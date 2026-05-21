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
     * 抓取网页并解析为 Document 对象
     *
     * @param url 网页URL
     * @return Document 对象，抓取失败返回 null
     */
    public Document fetch(String url) {
        try {
            log.info("正在抓取页面: {}", url);
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
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
}
