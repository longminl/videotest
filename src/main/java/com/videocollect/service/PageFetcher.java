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
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 网页抓取器：三阶降级策略
 *   Tier 1: OkHttp + 完整浏览器头 + Cookie 持久化
 *   Tier 2: OkHttp 重试（1s 延迟）
 *   Tier 3: Edge/Chrome headless --dump-dom（绕过 Cloudflare 等 JA3 指纹封锁）
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

    private static final int BROWSER_TIMEOUT = 15000;

    @Value("${video.fetch.user-agent}")
    private String userAgent;

    @Value("${video.fetch.timeout}")
    private int timeout;

    /** 用户指定的浏览器路径（可选），为空则自动探测 */
    @Value("${video.fetch.browser-path:}")
    private String configBrowserPath;

    private OkHttpClient httpClient;

    /** 浏览器标准请求头 */
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

    /** 常见浏览器安装路径（探测顺序：Edge → Chrome） */
    private static final String[] BROWSER_CANDIDATES = {
        System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
        System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
        System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe",
        System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
    };

    @PostConstruct
    public void init() {
        this.httpClient = buildHttpClient();
    }

    // ====== 公共方法 ======

    public Document fetch(String url) {
        return doFetch(url, userAgent, true);
    }

    public Document fetch(String url, String ua) {
        return doFetch(url, ua, true);
    }

    public String fetchRaw(String url) {
        Document doc = doFetch(url, userAgent, false);
        return doc != null ? doc.html() : null;
    }

    // ====== 三阶降级核心 ======

    /**
     * 三阶降级获取页面内容：
     *   1. OkHttp + 重试
     *   2. 失败 → Edge/Chrome headless --dump-dom
     */
    private Document doFetch(String url, String ua, boolean parseHtml) {
        // Tier 1+2: OkHttp + 重试
        String body = executeWithRetry(url, ua);
        if (body != null) {
            Document doc = Jsoup.parse(body, url);
            log.info("页面抓取成功: {} ({} bytes) [OkHttp]", url, body.length());
            return doc;
        }

        // Tier 3: 浏览器降级
        log.warn("OkHttp 请求失败，降级到浏览器无头模式: {}", url);
        String browserBody = fetchWithBrowser(url);
        if (browserBody != null) {
            Document doc = Jsoup.parse(browserBody, url);
            log.info("页面抓取成功: {} ({} bytes) [Browser]", url, browserBody.length());
            return doc;
        }

        log.error("所有获取方式均失败: {}", url);
        return null;
    }

    // ====== OkHttp ======

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

    private String execute(String url, String ua) throws Exception {
        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("Referer", extractBaseUrl(url));

        for (Map.Entry<String, String> entry : BROWSER_HEADERS.entrySet()) {
            reqBuilder.header(entry.getKey(), entry.getValue());
        }

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

    private OkHttpClient buildHttpClient() {
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

        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectionPool(new okhttp3.ConnectionPool(10, 30, TimeUnit.SECONDS))
                .cookieJar(new MemCookieJar())
                .sslSocketFactory(sslContext.getSocketFactory(), trustAll)
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }

    // ====== 浏览器无头降级 ======

    /**
     * 用 Edge/Chrome headless --dump-dom 获取页面完整 HTML
     * 绕过 Cloudflare / 反爬 JA3 指纹检测 + 支持 JS 渲染
     */
    private String fetchWithBrowser(String url) {
        String browserPath = resolveBrowser();
        if (browserPath == null) {
            log.warn("未找到浏览器，跳过无头降级");
            return null;
        }

        log.info("浏览器无头模式启动: {} {}", browserPath, url);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    browserPath,
                    "--headless",
                    "--dump-dom",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-software-rasterizer",
                    "--disable-dev-shm-usage",
                    "--no-first-run",
                    "--disable-sync",
                    url
            );
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // 读取 stdout（页面 HTML）
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) != -1) {
                    stdout.append(buf, 0, n);
                    // 防止 OOM，限制最大 5MB
                    if (stdout.length() > 5 * 1024 * 1024) {
                        log.warn("浏览器输出超过 5MB，截断: {}", url);
                        break;
                    }
                }
            }

            // 读 stderr（仅用于日志）
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line);
                }
            }

            boolean finished = process.waitFor(BROWSER_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("浏览器超时 ({})，强制终止: {}", BROWSER_TIMEOUT, url);
                process.destroyForcibly();
                return null;
            }

            if (stdout.length() == 0) {
                log.warn("浏览器输出为空: {} stderr: {}", url, stderr.length() > 0 ? stderr.substring(0, 200) : "无");
                return null;
            }

            log.debug("浏览器渲染成功: {} ({} bytes)", url, stdout.length());
            return stdout.toString();

        } catch (Exception e) {
            log.warn("浏览器抓取异常: {}, 原因: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 解析浏览器可执行路径
     * 优先：配置路径  →  自动探测
     */
    private String resolveBrowser() {
        // 用户配置
        if (configBrowserPath != null && !configBrowserPath.isEmpty()) {
            File f = new File(configBrowserPath);
            if (f.isFile()) return f.getAbsolutePath();
            log.warn("配置的浏览器路径不存在: {}", configBrowserPath);
        }

        // 自动探测
        for (String candidate : BROWSER_CANDIDATES) {
            if (candidate == null) continue;
            File f = new File(candidate);
            if (f.isFile()) {
                log.info("自动探测到浏览器: {}", f.getAbsolutePath());
                return f.getAbsolutePath();
            }
        }

        return null;
    }

    // ====== 工具方法 ======

    private static String extractBaseUrl(String url) {
        if (url == null || url.isEmpty()) return "https://www.baidu.com";
        int idx = url.indexOf("/", url.indexOf("://") + 3);
        return idx > 0 ? url.substring(0, idx) + "/" : url + "/";
    }

    // ====== 内存 Cookie 存储 ======

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
            if (cookies == null || cookies.isEmpty()) return Collections.emptyList();
            List<Cookie> valid = new ArrayList<>();
            for (Cookie c : cookies) {
                if (c.expiresAt() > System.currentTimeMillis()) valid.add(c);
            }
            if (valid.size() != cookies.size()) store.put(host, valid);
            return valid;
        }
    }
}