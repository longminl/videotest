package com.videocollect.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 网页抓取器：通过 Jsoup 拉取页面 HTML
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

    @Value("${video.fetch.user-agent}")
    private String userAgent;

    @Value("${video.fetch.timeout}")
    private int timeout;

    /**
     * 抓取网页并解析为 Document 对象（使用配置的 UA）
     */
    public Document fetch(String url) {
        return fetch(url, userAgent);
    }

    /**
     * 抓取网页并解析为 Document 对象（指定 User-Agent）
     */
    public Document fetch(String url, String ua) {
        try {
            log.info("正在抓取页面: {} (UA: {})", url, ua.length() > 40 ? ua.substring(0, 40) + "..." : ua);
            Document doc = Jsoup.connect(url)
                    .userAgent(ua)
                    .timeout(timeout)
                    .followRedirects(true)
                    .get();
            log.info("页面抓取成功: {} ({} bytes)", url, doc.html().length());
            return doc;
        } catch (Exception e) {
            log.error("页面抓取失败: {}, 原因: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 抓取非 HTML 响应（JSON / m3u8 等），返回原始响应体字符串
     */
    public String fetchRaw(String url) {
        try {
            log.info("正在请求原始数据: {}", url);
            String body = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeout)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .execute()
                    .body();
            log.info("原始数据请求成功: {} ({} bytes)", url, body.length());
            return body;
        } catch (Exception e) {
            log.warn("原始数据请求失败: {}, 原因: {}", url, e.getMessage());
            return null;
        }
    }
}
