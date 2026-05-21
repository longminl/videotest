package com.videocollect.service;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频解析器：从 HTML Document 中提取视频链接
 * 支持标签解析 + JS 变量解析（常见于影视站/资源站）
 */
@Component
public class VideoParser {

    private static final Logger log = LoggerFactory.getLogger(VideoParser.class);

    /** iframe 递归解析最大深度（1=只解析一层） */
    private static final int MAX_IFRAME_DEPTH = 1;

    @Autowired
    private PageFetcher pageFetcher;

    /** 常见的视频文件扩展名 */
    private static final List<String> VIDEO_EXTENSIONS = new ArrayList<String>() {{
        add(".mp4"); add(".webm"); add(".avi"); add(".mov");
        add(".mkv"); add(".flv"); add(".wmv"); add(".m3u8");
        add(".ts"); add(".mpg"); add(".mpeg"); add(".3gp");
    }};

    /**
     * 匹配 JS 中 "url":"https://...xxx.m3u8" 或 "url":"https://...xxx.mp4"
     * 常见于 var player_aaaa = {"url":"https://...", ...}
     * 也匹配 "url_next" 备用线路
     */
    private static final Pattern JS_URL_PATTERN = Pattern.compile(
            "\"(?:url|url_next)\"\\s*:\\s*\"(https?://[^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 匹配 JS 中 "link":"https://..."（一些站点的播放页跳转链接）
     */
    private static final Pattern JS_LINK_PATTERN = Pattern.compile(
            "\"link\"\\s*:\\s*\"(https?://[^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 从页面中提取视频链接（按优先级从高到低）
     *
     * @param doc Jsoup 解析的 Document
     * @return 找到的视频URL列表
     */
    public List<String> parse(Document doc) {
        return parse(doc, 0);
    }

    private List<String> parse(Document doc, int depth) {
        List<String> results = new ArrayList<>();

        // ====== 第0步：从 <script> 标签中提取 JS 变量里的视频地址 ======
        // 中文影视站常用模式：var player_xxxx = {"url":"https://...m3u8",...}
        // 注意 JS 中可能用 \/ 转义正斜杠，需要先标准化
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String raw = script.html();
            if (raw == null || raw.isEmpty()) continue;

            // 标准化：去掉 JS 中的反斜杠转义（\/ → /）
            String content = raw.replace("\\/", "/");

            // 查找 "url":"https://xxx" 或 "url_next":"https://xxx"
            Matcher matcher = JS_URL_PATTERN.matcher(content);
            while (matcher.find()) {
                String url = matcher.group(1).trim();
                // 简单过滤掉非视频URL（比如接口地址等）
                if (isLikelyVideoUrl(url) && !results.contains(url)) {
                    results.add(url);
                    log.debug("从JS变量中提取到视频地址: {}", url);
                }
            }
        }

        // ====== 第1步：从 <video> 标签的 src 属性提取 ======
        Elements videos = doc.select("video[src]");
        for (Element video : videos) {
            String src = video.attr("src").trim();
            if (isValidVideoUrl(src) && !results.contains(src)) {
                results.add(src);
            }
        }

        // ====== 第2步：从 <video> 标签内的 <source> 子标签提取 ======
        Elements sources = doc.select("video source[src]");
        for (Element source : sources) {
            String src = source.attr("src").trim();
            if (isValidVideoUrl(src) && !results.contains(src)) {
                results.add(src);
            }
        }

        // ====== 第3步：从 <iframe> 标签的 src 提取（嵌入视频） ======
        Elements iframes = doc.select("iframe[src]");
        for (Element iframe : iframes) {
            String src = iframe.attr("src").trim();
            if (!isValidVideoUrl(src) || results.contains(src)) continue;

            if (isVideoFileUrl(src) || isEmbedPlatform(src)) {
                // 视频直链或已知平台 → 直接收录
                results.add(src);
            } else if (depth < MAX_IFRAME_DEPTH && pageFetcher != null) {
                // 未知 iframe（PHP 播放页等）→ 递归抓取解析
                log.info("iframe 指向非视频页面，递归解析: {}", src);
                Document iframeDoc = pageFetcher.fetch(src);
                if (iframeDoc != null) {
                    List<String> nested = parse(iframeDoc, depth + 1);
                    for (String url : nested) {
                        if (!results.contains(url)) {
                            results.add(url);
                            log.debug("iframe 递归解析到视频: {}", url);
                        }
                    }
                    if (nested.isEmpty()) {
                        log.info("iframe 递归未找到视频，跳过: {}", src);
                    }
                } else {
                    log.warn("iframe 页面抓取失败: {}", src);
                }
            } else {
                // 超过递归深度或无 Fetcher → 保底收录
                results.add(src);
            }
        }

        // ====== 第4步：从 <a> 标签的 href 中提取直接视频链接 ======
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("href").trim();
            if (isVideoExtension(href) && !results.contains(href)) {
                results.add(href);
            }
        }

        // ====== 第5步：从 embed 标签提取 ======
        Elements embeds = doc.select("embed[src]");
        for (Element embed : embeds) {
            String src = embed.attr("src").trim();
            if (isValidVideoUrl(src) && !results.contains(src)) {
                results.add(src);
            }
        }

        // ====== 第6步：从所有 <script> 标签中二次扫描 ======
        // 这次找 link 字段（播放页跳转）或者任何包含视频扩展名的URL
        if (results.isEmpty()) {
            for (Element script : scripts) {
                String raw = script.html();
                if (raw == null || raw.isEmpty()) continue;

                // 标准化 JS 转义
                String content = raw.replace("\\/", "/");

                // 查找 "link":"https://..." 跳转链接
                Matcher linkMatcher = JS_LINK_PATTERN.matcher(content);
                while (linkMatcher.find()) {
                    String url = linkMatcher.group(1).trim();
                    if (isValidVideoUrl(url) && !results.contains(url)) {
                        results.add(url);
                        log.debug("从JS中提取到播放页链接: {}", url);
                    }
                }

                // 兜底：直接扫描文本中所有带视频扩展名的URL
                Matcher urlMatcher = Pattern.compile(
                        "(https?://[^\\s\"'<>]+(?:\\.(?:m3u8|mp4|ts|flv|webm))[^\\s\"'<>]*)",
                        Pattern.CASE_INSENSITIVE
                ).matcher(content);
                while (urlMatcher.find()) {
                    String url = urlMatcher.group(1).trim();
                    if (!results.contains(url)) {
                        results.add(url);
                        log.debug("从JS文本中提取到视频直链: {}", url);
                    }
                }
            }
        }

        log.info("页面解析完成，共找到 {} 个视频链接 (标签+JS)", results.size());
        return results;
    }

    /**
     * 提取页面标题
     */
    public String extractPageTitle(Document doc) {
        String title = doc.title();
        return (title != null && !title.isEmpty()) ? title : null;
    }

    /**
     * 判断 URL 是否指向直接的视频文件（通过路径扩展名判断，忽略查询参数）
     */
    private boolean isVideoFileUrl(String url) {
        try {
            String path = new URL(url).getPath().toLowerCase();
            for (String ext : VIDEO_EXTENSIONS) {
                if (path.endsWith(ext)) return true;
            }
        } catch (Exception e) {
            // URL 格式异常，回退到简单匹配
            String lower = url.toLowerCase();
            for (String ext : VIDEO_EXTENSIONS) {
                if (lower.contains(ext)) return true;
            }
        }
        return false;
    }

    /**
     * 判断是否可能是视频URL
     */
    private boolean isLikelyVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        // 包含常见视频扩展名
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.contains(ext)) return true;
        }
        // 包含 video/stream 等关键词
        return lower.contains("/video/") || lower.contains("/stream/")
                || lower.contains("/play/") || lower.contains("video");
    }

    /**
     * 是否是有效的视频URL
     */
    private boolean isValidVideoUrl(String url) {
        return url != null && !url.isEmpty()
                && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//"));
    }

    /**
     * 是否是已知的嵌入视频平台
     */
    private boolean isEmbedPlatform(String url) {
        return url != null && (url.contains("youtube.com/embed/")
                || url.contains("player.bilibili.com")
                || url.contains("youku.com/embed/"));
    }

    /**
     * 判断URL是否以视频文件扩展名结尾
     */
    private boolean isVideoExtension(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
