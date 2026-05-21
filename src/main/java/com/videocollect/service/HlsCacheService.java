package com.videocollect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HLS 代理缓存服务
 * 缓存 ts 片段到本地磁盘，避免重复下载
 */
@Service
public class HlsCacheService {

    private static final Logger log = LoggerFactory.getLogger(HlsCacheService.class);

    @Value("${video.hls.cache-dir:./hls-cache}")
    private String cacheDir;

    private Path cachePath;

    /** 预下载并发线程数 */
    @Value("${video.hls.prefetch-threads:5}")
    private int prefetchThreads;

    /** 播放时额外预取 ts 个数 */
    @Value("${video.hls.playback-prefetch-count:10}")
    private int playbackPrefetchCount;

    /** 后台预下载线程池 */
    private ExecutorService prefetchExecutor;

    /** 防止同一 ts 并发下载 */
    private final ConcurrentHashMap<String, ReentrantLock> downloadLocks = new ConcurrentHashMap<>();

    /** 每个视频的 ts URL 列表缓存（key = sanitized title） */
    private final ConcurrentHashMap<String, List<String>> tsUrlListCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        cachePath = Paths.get(cacheDir).toAbsolutePath();
        try {
            Files.createDirectories(cachePath);
            log.info("HLS 缓存目录: {}", cachePath);
        } catch (IOException e) {
            log.error("创建 HLS 缓存目录失败: {}", cachePath, e);
        }
        prefetchExecutor = Executors.newFixedThreadPool(prefetchThreads);
        log.info("HLS 预下载线程池: {} 线程", prefetchThreads);
    }

    @PreDestroy
    public void destroy() {
        if (prefetchExecutor != null) {
            prefetchExecutor.shutdown();
        }
    }

    /**
     * 根据 ts URL 计算缓存文件名（MD5 十六进制）
     */
    public String getFileName(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            // 回退：用 hashCode
            return Integer.toHexString(url.hashCode());
        }
    }

    /**
     * 获取缓存文件（不存在返回 null）
     */
    public File getCachedFile(String url) {
        return getCachedFile(url, null);
    }

    /**
     * 获取缓存文件（按视频标题分目录）
     */
    public File getCachedFile(String url, String title) {
        String name = getFileName(url);
        Path dir = resolveDir(title);
        File file = dir.resolve(name).toFile();
        return file.exists() ? file : null;
    }

    /**
     * 将 ts 数据写入缓存
     */
    public File cacheFile(String url, byte[] data) {
        return cacheFile(url, data, null);
    }

    /**
     * 将 ts 数据写入缓存（按视频标题分目录）
     */
    public File cacheFile(String url, byte[] data, String title) {
        String name = getFileName(url);
        Path dir = resolveDir(title);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            Files.write(target, data);
            log.debug("已缓存: {} -> {}", url, target);
            return target.toFile();
        } catch (IOException e) {
            log.error("缓存写入失败: {}", dir.resolve(name), e);
            return null;
        }
    }

    /**
     * 根据 title 解析子目录路径
     */
    private Path resolveDir(String title) {
        if (title != null && !title.trim().isEmpty()) {
            String safe = sanitizeTitle(title);
            return cachePath.resolve(safe);
        }
        return cachePath;
    }

    /**
     * 清理标题中的非法文件名字符
     */
    private String sanitizeTitle(String title) {
        // Windows 路径非法字符 & 截断
        String safe = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.length() > 100) {
            safe = safe.substring(0, 100);
        }
        return safe.trim();
    }

    // ==================== m3u8 索引缓存 ====================

    /**
     * 缓存改写后的 m3u8 索引内容（含代理 URL）
     */
    public void cacheM3u8(String url, String content, String title) {
        String name = getFileName(url);
        Path dir = resolveM3u8Dir(title);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), content.getBytes("UTF-8"));
            log.debug("m3u8 已缓存: {}", url);
        } catch (IOException e) {
            log.warn("m3u8 缓存写入失败: {}", url, e);
        }
    }

    /**
     * 获取缓存的 m3u8 索引（不存在返回 null）
     */
    public String getCachedM3u8(String url, String title) {
        String name = getFileName(url);
        Path file = resolveM3u8Dir(title).resolve(name);
        if (Files.exists(file)) {
            try {
                return new String(Files.readAllBytes(file), "UTF-8");
            } catch (IOException e) {
                log.warn("读取 m3u8 缓存失败: {}", file, e);
            }
        }
        return null;
    }

    /**
     * 缓存原始（未改写）m3u8 内容，专供 collectTsUrls 解析原始 ts 链接
     */
    public void cacheOriginalM3u8(String url, String content, String title) {
        String name = getFileName(url) + "_original";
        Path dir = resolveM3u8Dir(title);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), content.getBytes("UTF-8"));
        } catch (IOException e) {
            log.warn("原始 m3u8 缓存写入失败: {}", url, e);
        }
    }

    /**
     * 获取缓存的原始 m3u8 内容
     */
    public String getOriginalM3u8(String url, String title) {
        String name = getFileName(url) + "_original";
        Path file = resolveM3u8Dir(title).resolve(name);
        if (Files.exists(file)) {
            try {
                return new String(Files.readAllBytes(file), "UTF-8");
            } catch (IOException e) {
                log.warn("读取原始 m3u8 缓存失败: {}", file, e);
            }
        }
        return null;
    }

    /**
     * 清除 m3u8 缓存（用于重新检测时强制刷新）
     */
    public void clearM3u8Cache(String url, String title) {
        String name = getFileName(url);
        Path dir = resolveM3u8Dir(title);
        try {
            Files.deleteIfExists(dir.resolve(name));
            Files.deleteIfExists(dir.resolve(name + "_original"));
            log.info("m3u8 缓存已清除: {}", url);
        } catch (IOException e) {
            log.warn("清除 m3u8 缓存失败: {}", url, e);
        }
    }

    /** m3u8 缓存子目录：{title}/m3u8/ */
    private Path resolveM3u8Dir(String title) {
        return resolveDir(title).resolve("m3u8");
    }

    // ==================== 并发预下载 ====================

    /**
     * 后台并发预下载 ts 片段（跳过已缓存）
     */
    public void prefetchTs(List<String> tsUrls, String title) {
        if (tsUrls == null || tsUrls.isEmpty()) return;
        int submitted = 0;
        for (String tsUrl : tsUrls) {
            if (getCachedFile(tsUrl, title) != null) continue;
            prefetchExecutor.submit(() -> doDownloadTs(tsUrl, title));
            submitted++;
        }
        if (submitted > 0) {
            log.info("提交 {} 个 ts 预下载任务", submitted);
        }
    }

    /** 单个 ts 下载任务 */
    private void doDownloadTs(String tsUrl, String title) {
        try {
            log.debug("预下载 ts: {}", tsUrl);
            byte[] data = downloadBytes(tsUrl);
            if (data != null) {
                cacheFile(tsUrl, data, title);
                log.debug("预下载完成: {}", tsUrl);
            }
        } catch (Exception e) {
            log.warn("预下载异常: {}", tsUrl, e);
        }
    }

    // ==================== 播放时预取（边播边缓存后续 N 段） ====================

    /**
     * 缓存该视频的 ts URL 列表（由 proxyM3u8 写入）
     */
    public void cacheTsUrlList(String title, List<String> tsUrls) {
        if (title == null || title.trim().isEmpty()) return;
        String key = sanitizeTitle(title);
        tsUrlListCache.put(key, new ArrayList<>(tsUrls));
        log.debug("已缓存 ts URL 列表: title={}, count={}", title, tsUrls.size());
    }

    /**
     * 获取该视频的 ts URL 列表
     */
    public List<String> getTsUrlList(String title) {
        if (title == null || title.trim().isEmpty()) return null;
        return tsUrlListCache.get(sanitizeTitle(title));
    }

    /**
     * 播放时预取当前 ts 后续 N 个片段（跳过已缓存）
     */
    public void prefetchNextTs(String currentTsUrl, String title) {
        if (title == null || title.trim().isEmpty() || playbackPrefetchCount <= 0) return;
        List<String> allTs = tsUrlListCache.get(sanitizeTitle(title));
        if (allTs == null || allTs.isEmpty()) return;

        int index = allTs.indexOf(currentTsUrl);
        if (index < 0) return;

        int end = Math.min(index + 1 + playbackPrefetchCount, allTs.size());
        if (end <= index + 1) return;

        List<String> nextTs = allTs.subList(index + 1, end);
        int submitted = 0;
        for (String tsUrl : nextTs) {
            if (getCachedFile(tsUrl, title) != null) continue;
            prefetchExecutor.submit(() -> doDownloadTs(tsUrl, title));
            submitted++;
        }
        if (submitted > 0) {
            log.debug("播放预取: 当前 {}，后续预取 {} 个", currentTsUrl, submitted);
        }
    }

    /**
     * 清除指定视频的本地缓存（ts + m3u8 + tsUrlListCache）
     */
    public void clearVideoCache(String title) {
        if (title == null || title.trim().isEmpty()) return;
        String key = sanitizeTitle(title);
        Path dir = resolveDir(title);
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException e) { log.warn("删除缓存文件失败: {}", p, e); }
                        });
                log.info("已清除视频缓存: {}", key);
            }
            tsUrlListCache.remove(key);
        } catch (IOException e) {
            log.warn("清除视频缓存异常: {}", key, e);
        }
    }

    /** HTTP GET 下载文件字节（带 User-Agent + Referer 防盗链） */
    private byte[] downloadBytes(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", extractOrigin(url));
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("预下载 HTTP {}: {}", code, url);
                return null;
            }
            int length = conn.getContentLength();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(length > 0 ? length : 8192);
            byte[] buf = new byte[8192];
            try (InputStream in = conn.getInputStream()) {
                int len;
                while ((len = in.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("预下载失败: {}", url, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 提取 URL 的源站（用于 Referer） */
    private String extractOrigin(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 获取（或创建）该 URL 对应的下载锁
     */
    public ReentrantLock getDownloadLock(String url) {
        return downloadLocks.computeIfAbsent(getFileName(url), k -> new ReentrantLock());
    }
}
