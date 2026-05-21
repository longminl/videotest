package com.videocollect.controller;

import com.videocollect.dao.VideoRecordDao;
import com.videocollect.service.HlsCacheService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HLS 视频代理控制器
 * 代理 m3u8 索引和 ts 片段，提供本地磁盘缓存加速
 */
@RestController
public class HlsProxyController {

    private static final Logger log = LoggerFactory.getLogger(HlsProxyController.class);

    @Autowired
    private HlsCacheService cacheService;

    @Autowired
    private VideoRecordDao videoRecordDao;

    private final OkHttpClient httpClient;
    private final ExecutorService cacheExecutor;

    public HlsProxyController() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
        this.cacheExecutor = Executors.newCachedThreadPool();
    }

    /**
     * 代理 m3u8 索引文件
     * 优先返回本地缓存的 m3u8（零网络），否则从源站拉取并触发后台预下载 ts
     */
    @GetMapping("/proxy/m3u8")
    public ResponseEntity<String> proxyM3u8(@RequestParam String url,
                                            @RequestParam(required = false) String title) {
        log.info("代理 m3u8: {}", url);

        // 1. 检查本地缓存的 m3u8（全缓存态下零网络）
        String cachedContent = cacheService.getCachedM3u8(url, title);
        if (cachedContent != null) {
            log.info("m3u8 缓存命中，直接返回");
            return buildM3u8Response(cachedContent);
        }

        // 2. 从源站拉取（并缓存原始内容供后续 collectTsUrls 使用）
        String content = fetchUrl(url);
        if (content == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("# 获取 m3u8 失败\n");
        }
        // 缓存原始（未改写）内容
        cacheService.cacheOriginalM3u8(url, content, title);

        // 3. 逐行处理：收集 ts URL + 生成代理改写内容
        String baseUrl = resolveBaseUrl(url);
        List<String> tsUrls = new ArrayList<>();
        StringBuilder sb = new StringBuilder(content.length() + 512);
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                sb.append(line).append("\n");
            } else {
                String absoluteUrl = resolveAbsoluteUrl(trimmed, baseUrl);
                try {
                    // 收集 ts 文件 URL（非子 m3u8）
                    if (!absoluteUrl.toLowerCase().contains(".m3u8")) {
                        tsUrls.add(absoluteUrl);
                    }
                    // 生成代理 URL
                    String encoded = URLEncoder.encode(absoluteUrl, "UTF-8");
                    String encodedTitle = (title != null && !title.isEmpty())
                            ? "&title=" + URLEncoder.encode(title, "UTF-8") : "";
                    if (absoluteUrl.toLowerCase().contains(".m3u8")) {
                        sb.append("/proxy/m3u8?url=").append(encoded).append(encodedTitle).append("\n");
                    } else {
                        sb.append("/proxy/ts?url=").append(encoded).append(encodedTitle).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("URL 编码失败: {}", absoluteUrl, e);
                    sb.append(line).append("\n");
                }
            }
        }

        String rewritten = sb.toString();

        // 4. 缓存改写后的 m3u8 索引 + ts URL 列表
        cacheService.cacheM3u8(url, rewritten, title);
        if (!tsUrls.isEmpty() && title != null) {
            cacheService.cacheTsUrlList(title, tsUrls);
        }

        // 5. 预取前 10 个 ts（边播边缓存），其余懒加载
        if (!tsUrls.isEmpty()) {
            int prefetchCount = Math.min(tsUrls.size(), 10);
            cacheService.prefetchTs(tsUrls.subList(0, prefetchCount), title);
        }

        return buildM3u8Response(rewritten);
    }

    /** 构建 m3u8 响应头 */
    private ResponseEntity<String> buildM3u8Response(String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/vnd.apple.mpegurl"));
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    /**
     * 代理 ts 文件（带本地磁盘缓存）
     */
    @GetMapping("/proxy/ts")
    public ResponseEntity<Resource> proxyTs(@RequestParam String url,
                                            @RequestParam(required = false) String title) {
        // 1. 检查缓存
        File cached = cacheService.getCachedFile(url, title);
        if (cached != null) {
            log.debug("ts 缓存命中");
            return buildTsResponse(cached);
        }

        // 2. 加锁下载（防止同一片段并发重复下载）
        ReentrantLock lock = cacheService.getDownloadLock(url);
        lock.lock();
        try {
            // 双检锁：再次检查缓存
            cached = cacheService.getCachedFile(url, title);
            if (cached != null) {
                return buildTsResponse(cached);
            }

            // 3. 从源站下载
            log.info("下载 ts: {}", url);
            byte[] data = downloadBytes(url);
            if (data == null) {
                log.warn("ts 下载失败: {}", url);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            // 4. 缓存到磁盘（按 title 分目录）
            File cachedFile = cacheService.cacheFile(url, data, title);
            // 播放预取：当前 ts 下载成功后，后台预取后续 N 个 ts
            cacheService.prefetchNextTs(url, title);
            if (cachedFile != null) {
                return buildTsResponse(cachedFile);
            }

            // 缓存写入失败，直接返回内存数据
            ByteArrayResource resource = new ByteArrayResource(data) {
                @Override
                public String getFilename() {
                    return "segment.ts";
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("video/mp2t"))
                    .contentLength(data.length)
                    .body(resource);
        } finally {
            lock.unlock();
        }
    }

    // ==================== 缓存操作 & 状态查询 ====================

    /**
     * 启动缓存：异步执行 m3u8 拉取 + ts 预下载，立即返回
     */
    @PostMapping("/api/cache/start")
    public ResponseEntity<Map<String, Object>> cacheStart(@RequestParam String videoUrl,
                                                           @RequestParam(required = false) String title,
                                                           @RequestParam(required = false) Long id) {
        log.info("启动缓存: videoUrl={}, title={}", videoUrl, title);

        if (!videoUrl.toLowerCase().contains(".m3u8")) {
            return ResponseEntity.ok(createStatusResult(false, 0, 0, 0, "非 HLS 视频无需缓存"));
        }

        // 提交后台任务：拉取 m3u8 + 改写 + 缓存 + 预下载 ts
        final String fVideoUrl = videoUrl;
        final String fTitle = title;
        final Long fId = id;
        cacheExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    doCacheWork(fVideoUrl, fTitle, fId);
                } catch (Exception e) {
                    log.error("后台缓存任务异常: {}", fVideoUrl, e);
                }
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("started", true);
        return ResponseEntity.ok(result);
    }

    /** 后台执行：获取 m3u8 → 改写 → 缓存 m3u8 → 预下载 ts */
    private void doCacheWork(String videoUrl, String title, Long id) {
        String content = getM3u8Content(videoUrl, title);
        if (content == null) {
            log.warn("后台缓存: 获取 m3u8 失败, url={}", videoUrl);
            return;
        }

        String baseUrl = resolveBaseUrl(videoUrl);
        List<String> tsUrls = new ArrayList<>();
        StringBuilder sb = new StringBuilder(content.length() + 512);
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                sb.append(line).append("\n");
            } else {
                String absoluteUrl = resolveAbsoluteUrl(trimmed, baseUrl);
                try {
                    if (!absoluteUrl.toLowerCase().contains(".m3u8")) {
                        tsUrls.add(absoluteUrl);
                    }
                    String encoded = URLEncoder.encode(absoluteUrl, "UTF-8");
                    String encodedTitle = (title != null && !title.isEmpty())
                            ? "&title=" + URLEncoder.encode(title, "UTF-8") : "";
                    if (absoluteUrl.toLowerCase().contains(".m3u8")) {
                        sb.append("/proxy/m3u8?url=").append(encoded).append(encodedTitle).append("\n");
                    } else {
                        sb.append("/proxy/ts?url=").append(encoded).append(encodedTitle).append("\n");
                    }
                } catch (Exception e) {
                    log.warn("URL 编码失败: {}", absoluteUrl, e);
                    sb.append(line).append("\n");
                }
            }
        }

        String rewritten = sb.toString();
        cacheService.cacheM3u8(videoUrl, rewritten, title);

        // 递归收集所有 ts URL（含子 m3u8 中的），然后预下载
        List<String> allTsUrls = new ArrayList<>();
        collectTsUrls(videoUrl, allTsUrls, title);
        if (!allTsUrls.isEmpty()) {
            cacheService.prefetchTs(allTsUrls, title);
        }
        log.info("后台缓存任务完成: videoUrl={}, tsCount={}", videoUrl, allTsUrls.size());
    }

    /**
     * 查询视频缓存进度
     * 递归解析 master → media → ts，统计已缓存片段
     */
    @GetMapping("/api/cache/status")
    public ResponseEntity<Map<String, Object>> cacheStatus(@RequestParam String videoUrl,
                                                           @RequestParam(required = false) String title,
                                                           @RequestParam(required = false) Long id) {
        List<String> tsUrls = new ArrayList<>();
        collectTsUrls(videoUrl, tsUrls, title);

        int total = tsUrls.size();
        int cached = 0;
        long cachedBytes = 0;

        for (String url : tsUrls) {
            File file = cacheService.getCachedFile(url, title);
            if (file != null) {
                cached++;
                cachedBytes += file.length();
            }
        }

        // 全部缓存完成 → 更新数据库标记
        boolean allCached = total > 0 && cached == total;
        if (allCached && id != null) {
            videoRecordDao.updateIsCached(id, true);
        }

        Map<String, Object> result = createStatusResult(allCached, total, cached, cachedBytes, null);
        return ResponseEntity.ok(result);
    }

    /** 构建缓存状态响应 */
    private Map<String, Object> createStatusResult(Boolean finished, int total, int cached,
                                                    long cachedBytes, String error) {
        Map<String, Object> result = new HashMap<>();
        result.put("finished", finished);
        result.put("total", total);
        result.put("cached", cached);
        result.put("cachedBytes", cachedBytes);
        result.put("cachedMb", Math.round(cachedBytes / 1048576.0 * 100) / 100.0);
        if (error != null) {
            result.put("error", error);
        }
        return result;
    }

    /**
     * 清除 m3u8 缓存（重新检测时调用，强制下次走网络拉取）
     */
    @GetMapping("/api/cache/clear-m3u8")
    public ResponseEntity<Void> clearM3u8Cache(@RequestParam String videoUrl,
                                               @RequestParam(required = false) String title) {
        cacheService.clearM3u8Cache(videoUrl, title);
        return ResponseEntity.ok().build();
    }

    /** 递归收集 m3u8 中所有 ts 片段 URL（优先从缓存读取，避免重复请求源站） */
    private void collectTsUrls(String url, List<String> result, String title) {
        String content = getM3u8Content(url, title);
        if (content == null) return;

        String baseUrl = resolveBaseUrl(url);
        String[] lines = content.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            String absoluteUrl = resolveAbsoluteUrl(trimmed, baseUrl);
            if (absoluteUrl.toLowerCase().contains(".m3u8")) {
                // 子 m3u8 → 递归
                collectTsUrls(absoluteUrl, result, title);
            } else {
                // ts 文件
                result.add(absoluteUrl);
            }
        }
    }

    // ==================== 响应构建 ====================

    private ResponseEntity<Resource> buildTsResponse(File file) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp2t"))
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }

    // ==================== HTTP 请求工具 ====================

    /**
     * 获取 m3u8 原始内容：优先使用缓存，缓存未命中则从源站拉取并缓存
     */
    private String getM3u8Content(String url, String title) {
        String cached = cacheService.getOriginalM3u8(url, title);
        if (cached != null) {
            return cached;
        }
        String content = fetchUrl(url);
        if (content != null) {
            cacheService.cacheOriginalM3u8(url, content, title);
        }
        return content;
    }

    /** 构建带 User-Agent + Referer 的请求（Referer 取自 URL 的源站域名） */
    private Request buildRequest(String url) {
        String referer = getOrigin(url);
        return new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", referer)
                .build();
    }

    private String fetchUrl(String url) {
        try {
            try (Response response = httpClient.newCall(buildRequest(url)).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("获取 m3u8 返回非成功状态: {} -> {}", url, response.code());
                    return null;
                }
                return response.body().string();
            }
        } catch (IOException e) {
            log.warn("获取 m3u8 失败: {}", url, e);
            return null;
        }
    }

    private byte[] downloadBytes(String url) {
        try {
            try (Response response = httpClient.newCall(buildRequest(url)).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("下载 ts 返回非成功状态: {} -> {}", url, response.code());
                    return null;
                }
                return response.body().bytes();
            }
        } catch (IOException e) {
            log.warn("下载 ts 失败: {}", url, e);
            return null;
        }
    }

    /** 提取 URL 的源站（scheme + host），用于 Referer 防盗链 */
    private String getOrigin(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    // ==================== URL 解析工具 ====================

    /**
     * 计算 m3u8 所在目录（作为相对路径的基准）
     */
    private String resolveBaseUrl(String m3u8Url) {
        // 去掉 querystring 再取最后一个 / 前的部分
        int qsIdx = m3u8Url.indexOf('?');
        String clean = qsIdx > 0 ? m3u8Url.substring(0, qsIdx) : m3u8Url;
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash > 0) {
            return clean.substring(0, lastSlash + 1);
        }
        return m3u8Url + "/";
    }

    /**
     * 将 ts 地址解析为绝对 URL
     * 支持：绝对、协议相对 //、绝对路径 /、相对路径
     */
    private String resolveAbsoluteUrl(String tsUrl, String baseUrl) {
        if (tsUrl.startsWith("http://") || tsUrl.startsWith("https://")) {
            return tsUrl;
        }
        if (tsUrl.startsWith("//")) {
            return "https:" + tsUrl;
        }
        if (tsUrl.startsWith("/")) {
            try {
                URL url = new URL(baseUrl);
                return url.getProtocol() + "://" + url.getHost() + tsUrl;
            } catch (Exception e) {
                // 回退：去掉 base 末尾部分
                int idx = baseUrl.indexOf("//");
                if (idx > 0) {
                    int slash = baseUrl.indexOf('/', idx + 2);
                    if (slash > 0) {
                        return baseUrl.substring(0, slash) + tsUrl;
                    }
                }
                return baseUrl + tsUrl.substring(1);
            }
        }
        // 相对路径
        return baseUrl + tsUrl;
    }
}
