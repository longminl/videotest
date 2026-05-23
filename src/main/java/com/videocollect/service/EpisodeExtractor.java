package com.videocollect.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 URL 和页面标题中自动提取视频集数。
 * 支持常见影视站 URL 模式，提取后自动填充到 VideoRecord.episodeNumber。
 */
@Component
public class EpisodeExtractor {

    private static final Logger log = LoggerFactory.getLogger(EpisodeExtractor.class);

    /** URL 集数正则（按优先级从高到低） */
    private static final Pattern[] URL_PATTERNS = {
        // /12345/2-22.html → 22（sid-nid 模式，多线路站最常见）
        Pattern.compile("/(\\d+)-(\\d+)\\.html$"),
        // /12345/22.html → 22
        Pattern.compile("/(\\d+)\\.html$"),
        // /12345/ep-22.html 或 /12345/ep22.html → 22
        Pattern.compile("/(?:ep|episode)[-_]?(\\d+)\\.html$", Pattern.CASE_INSENSITIVE),
        // ?ep=22 或 &episode=22
        Pattern.compile("[?&](?:ep|episode|e)=(\\d+)", Pattern.CASE_INSENSITIVE),
        // URL 路径中的 第N集 / 第N话 / 第N期
        Pattern.compile("第(\\d+)[集话期]"),
        // /play/22 或 /vod/22
        Pattern.compile("/(?:play|vod|view|detail)/(\\d+)(?:\\.html)?$", Pattern.CASE_INSENSITIVE),
    };

    /** 页面标题集数正则 */
    private static final Pattern[] TITLE_PATTERNS = {
        // 第22集 / 第22话 / 第22期
        Pattern.compile("第(\\d+)[集话期]"),
        // episode 22 / EP22
        Pattern.compile("(?:episode|EP|ep)\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
    };

    /**
     * 从 URL 中提取集数
     */
    public Integer extractFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        for (Pattern p : URL_PATTERNS) {
            Matcher m = p.matcher(url);
            if (m.find()) {
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > 0 && n < 10000) {
                        log.debug("从URL提取到集数: {} → {}", m.group(1), url);
                        return n;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /**
     * 从页面标题中提取集数
     */
    public Integer extractFromTitle(String pageTitle) {
        if (pageTitle == null || pageTitle.isEmpty()) return null;
        for (Pattern p : TITLE_PATTERNS) {
            Matcher m = p.matcher(pageTitle);
            if (m.find()) {
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > 0 && n < 10000) {
                        log.debug("从标题提取到集数: {} → {}", m.group(1), pageTitle);
                        return n;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /**
     * 综合提取：优先 URL，URL 未匹配则尝试标题
     */
    public Integer extract(String url, String pageTitle) {
        Integer ep = extractFromUrl(url);
        if (ep != null) return ep;
        return extractFromTitle(pageTitle);
    }
}
