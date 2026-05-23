package com.videocollect.service;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 网页抓取器：基于 OkHttp 实现，模拟真实浏览器
 * - 完整浏览器请求头
 * - Cookie 自动持久化（跨请求维持会话）
 * - 宽松 SSL（兼容国内站点自签名证书）
 * - 失败自动重试 1 次
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

    @Value("${video.fetch.user-agent}")
    private String userAgent;

    @Value("${video.fetch.timeout}")
    private int timeout;

    private OkHttpClient httpClient;

    /** 浏览器标准请求头（除 UA 外固定部分） */
    private static final Map<String, String> BROWSER_HEADERS;

    static {
        java.util.LinkedHashMap<String, String> h = new java.util.LinkedHashMap<>();
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        h.put("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"99\", \"Google Chrome\";v=\"125\", \"Chromium\";v=\"125\"");
        h.put("Sec-Ch-Ua-Mobile", "?0");
        h.put("Sec-Ch-Ua-Platform", "\"Windows\"");
        h.put("Sec-Fetch-Dest", "document");
        h.put("Sec-Fetch-Mode", "navigate");
        h.put("Sec-Fetch-Site", "none");
        h.put("Sec-Fetch-User", "?1");
        h.put("Upgrade-Insecure-Requests", "1");
        h.put("Cache-Control", "max-age=0");
        BROWSER_HEADERS = Collections.unmodifiableMap(h);
    }

    @PostConstruct
    public void init() {
        this.httpClient = buildHttpClient();
    }

    // ====== 公共方法 ======

    /**
     * 抓取网页并解析为 Document 对象
     */
    public Document fetch(String url) {
        return doFetch(url, userAgent, true);
    }

    /**
     * 抓取网页并解析为 Document 对象（指定 UA）
     */
    public Document fetch(String url, String ua) {
        return doFetch(url, ua, true);
    }

    /**
     * 抓取非 HTML 响应（JSON / m3u8 等），返回原始响应体字符串
     */
    public String fetchRaw(String url) {
        Document doc = doFetch(url, userAgent, false);
        return doc != null ? doc.html() : null;
    }

    // ====== 核心 ======

    /**
     * OkHttp GET 请求，失败后重试 1 次
     *
     * @param url       目标 URL
     * @param ua        User-Agent
     * @param parseHtml true=返回 Jsoup Document, false=将响应体放在 Document.html() 中
     */
    private Document doFetch(String url, String ua, boolean parseHtml) {
        String body = executeWithRetry(url, ua);
        if (body == null) return null;

        if (parseHtml) {
            Document doc = Jsoup.parse(body, url);
            log.info("页面抓取成功: {} ({} bytes)", url, body.length());
            return doc;
        } else {
            // 将原始响应体伪装为 Document（用于 fetchRaw 兼容）
            Document doc = Jsoup.parse(body, url);
            log.info("原始数据请求成功: {} ({} bytes)", url, body.length());
            return doc;
        }
    }

    /**
     * 用 OkHttp 执行 GET 请求，失败后等待 0.5~1.5s 重试 1 次
     */
    private String executeWithRetry(String url, String ua) {
        try {
            return execute(url, ua);
        } catch (Exception e) {
            log.warn("请求失败: {}, 原因: {}, 重试中...", url, e.getMessage());
            try {
                Thread.sleep(500 + (long) (Math.random() * 1000));
                return execute(url, ua);
            } catch (Exception retryEx) {
                log.warn("重试也失败: {}, 原因: {}", url, retryEx.getMessage());
                return null;
            }
        }
    }

    /**
     * 单次 OkHttp GET 请求
     */
    private String execute(String url, String ua) throws Exception {
        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("Referer", extractBaseUrl(url));

        // 添加浏览器标准头
        for (Map.Entry<String, String> entry : BROWSER_HEADERS.entrySet()) {
            reqBuilder.header(entry.getKey(), entry.getValue());
        }

        log.debug("请求: {} (UA: {})", url, ua.length() > 40 ? ua.substring(0, 40) + "..." : ua);

        try (Response resp = httpClient.newCall(reqBuilder.build()).execute()) {
            ResponseBody body = resp.body();
            if (body == null) {
                log.warn("响应体为空: {} (HTTP {})", url, resp.code());
                return null;
            }
            String content = body.string();
            log.debug("响应成功: {} (HTTP {}, {} bytes)", url, resp.code(), content.length());
            return content;
        }
    }

    /**
     * 构建 OkHttpClient 实例
     */
    private OkHttpClient buildHttpClient() {
        // 宽松 TrustManager（信任所有证书，兼容国内站点）
        X509TrustManager trustAll = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAll}, new java.security.SecureRandom());
        } catch (Exception e) {
            throw new RuntimeException("SSL 初始化失败", e);
        }

        HostnameVerifier hostnameVerifier = (hostname, session) -> true;

        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectionPool(new okhttp3.ConnectionPool(10, 30, TimeUnit.SECONDS))
                .cookieJar(new MemCookieJar())
                .sslSocketFactory(sslContext.getSocketFactory(), trustAll)
                .hostnameVerifier(hostnameVerifier)
                .addInterceptor(chain -> {
                    // 自动重定向（OkHttp 默认处理），记录日志
                    Request request = chain.request();
                    okhttp3.Response response = chain.proceed(request);
                    log.debug("{} -> HTTP {}", request.url(), response.code());
                    return response;
                })
                .build();
    }

    /**
     * 从 URL 提取站点根作为 Referer
     */
    private static String extractBaseUrl(String url) {
        if (url == null || url.isEmpty()) return "https://www.baidu.com";
        int idx = url.indexOf("/", url.indexOf("://") + 3);
        return idx > 0 ? url.substring(0, idx) + "/" : url + "/";
    }

    // ====== 内存 Cookie 存储 ======

    /**
     * 简易内存 CookieJar：跨请求自动保持 Cookie
     */
    private static class MemCookieJar implements CookieJar {
        private final ConcurrentHashMap<String, List<Cookie>> store = new ConcurrentHashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            if (cookies == null || cookies.isEmpty()) return;
            String host = url.host();
            store.put(host, cookies);
            log.debug("Cookie 已保存 [{}]: {} 个", host, cookies.size());
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            String host = url.host();
            List<Cookie> cookies = store.get(host);
            if (cookies == null || cookies.isEmpty()) {
                return Collections.emptyList();
            }
            // 过滤过期 Cookie
            List<Cookie> valid = new ArrayList<>();
            for (Cookie c : cookies) {
                if (c.expiresAt() > System.currentTimeMillis()) {
                    valid.add(c);
                }
            }
            if (valid.size() != cookies.size()) {
                store.put(host, valid);
            }
            return valid;
        }
    }
}