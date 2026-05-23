package com.videocollect.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videocollect.dao.VideoSourceDao;
import com.videocollect.model.VideoSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 视频源管理服务：CRUD + 搜索测试 + 集数解析测试
 */
@Service
public class VideoSourceService {

    private static final Logger log = LoggerFactory.getLogger(VideoSourceService.class);

    @Autowired
    private VideoSourceDao videoSourceDao;

    @Autowired
    private PageFetcher pageFetcher;

    @Value("${video.fetch.user-agent}")
    private String userAgent;

    @Value("${video.fetch.timeout}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====== CRUD ======

    public List<VideoSource> findAll() {
        return videoSourceDao.findAll();
    }

    public VideoSource findById(Long id) {
        return videoSourceDao.findById(id);
    }

    public VideoSource save(VideoSource source) {
        if (source.getId() != null) {
            videoSourceDao.update(source);
            return videoSourceDao.findById(source.getId());
        } else {
            videoSourceDao.insert(source);
            return source;
        }
    }

    public void delete(Long id) {
        videoSourceDao.deleteById(id);
    }

    // ====== 搜索测试 ======

    /**
     * 搜索测试：调用视频源的搜索API，返回解析后的结果列表
     */
    public List<Map<String, String>> testSearch(VideoSource source, String keyword) {
        if (source == null || keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String searchUrl = buildSearchUrl(source, keyword.trim());
        log.info("测试搜索: {}", searchUrl);

        String response = pageFetcher.fetchRaw(searchUrl);
        if (response == null || response.isEmpty()) {
            log.warn("搜索响应为空: {}", searchUrl);
            return Collections.emptyList();
        }

        try {
            return parseSearchResults(source, response);
        } catch (Exception e) {
            log.warn("搜索结果解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建搜索URL（替换 {keyword} 占位符）
     */
    private String buildSearchUrl(VideoSource source, String keyword) {
        String baseUrl = source.getHomeUrl();
        if (baseUrl == null) baseUrl = "";
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        String searchPath = source.getSearchUrl();
        if (searchPath == null) searchPath = "/search?wd={keyword}";

        // 替换占位符
        String fullUrl = baseUrl + (searchPath.startsWith("/") ? "" : "/") + searchPath;
        try {
            fullUrl = fullUrl.replace("{keyword}", java.net.URLEncoder.encode(keyword, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 不可能不被支持
            fullUrl = fullUrl.replace("{keyword}", keyword);
        }
        return fullUrl;
    }

    /**
     * 解析搜索结果 JSON
     */
    private List<Map<String, String>> parseSearchResults(VideoSource source, String json) throws Exception {
        List<Map<String, String>> results = new ArrayList<>();

        String dataPath = source.getSearchDataPath();
        if (dataPath == null) dataPath = "$";

        JsonNode root = objectMapper.readTree(json);
        JsonNode array = root;

        // 按路径导航到数组
        if (!"$".equals(dataPath)) {
            String path = dataPath;
            if (path.startsWith("$.")) path = path.substring(2);
            String[] parts = path.split("\\.");
            for (String part : parts) {
                if (array != null) array = array.get(part);
            }
        }

        if (array == null || !array.isArray()) {
            // 尝试直接当作数组
            if (root.isArray()) array = root;
        }

        if (array == null || !array.isArray()) {
            log.warn("搜索结果JSON路径无效，dataPath={}", dataPath);
            return results;
        }

        String titleField = source.getSearchTitleField() != null ? source.getSearchTitleField() : "title";
        String urlField = source.getSearchUrlField() != null ? source.getSearchUrlField() : "url";
        String coverField = source.getSearchCoverField();

        for (JsonNode item : array) {
            Map<String, String> result = new LinkedHashMap<>();
            JsonNode titleNode = item.get(titleField);
            JsonNode urlNode = item.get(urlField);

            String title = titleNode != null ? titleNode.asText("") : "";
            String url = urlNode != null ? urlNode.asText("") : "";

            if (title.isEmpty() && url.isEmpty()) continue;

            result.put("title", title);
            result.put("url", url);

            if (coverField != null) {
                JsonNode coverNode = item.get(coverField);
                result.put("cover", coverNode != null ? coverNode.asText("") : "");
            }

            results.add(result);
        }

        return results;
    }

    // ====== 集数解析测试 ======

    /**
     * 解析剧集主页，提取所有集数链接
     * 返回 List<{episodeNumber, url, text}>
     */
    public List<Map<String, Object>> parseEpisodes(VideoSource source, String seriesUrl) {
        if (source == null || seriesUrl == null || seriesUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }

        log.info("开始解析剧集主页: {}", seriesUrl);

        try {
            Document doc = Jsoup.connect(seriesUrl)
                    .userAgent(userAgent)
                    .timeout(timeout)
                    .followRedirects(true)
                    .get();

            List<Map<String, Object>> episodes = new ArrayList<>();
            String pattern = source.getEpisodePattern();
            Integer groupIdx = source.getEpisodeGroup() != null ? source.getEpisodeGroup() : 1;
            String selector = source.getEpisodeSelector();

            List<Element> links;
            if (selector != null && !selector.isEmpty()) {
                // 限定区域查找
                links = doc.select(selector);
            } else {
                // 全页扫描 <a> 标签
                links = doc.select("a[href]");
            }

            Pattern episodeRegex = (pattern != null && !pattern.isEmpty())
                    ? Pattern.compile(pattern)
                    : Pattern.compile("(\\d+)-(\\d+)\\.html");

            Set<String> seenUrls = new HashSet<>();

            for (Element link : links) {
                String href = link.attr("href").trim();
                String text = link.text().trim();

                if (href.isEmpty()) continue;

                // 补全相对路径
                String fullUrl = href;
                if (!href.startsWith("http://") && !href.startsWith("https://")) {
                    if (href.startsWith("//")) {
                        fullUrl = "https:" + href;
                    } else if (href.startsWith("/")) {
                        String base = source.getHomeUrl();
                        if (base != null && !base.isEmpty()) {
                            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                            fullUrl = base + href;
                        }
                    } else {
                        // 相对路径，从 seriesUrl 推断
                        int lastSlash = seriesUrl.lastIndexOf('/');
                        if (lastSlash > 0) {
                            fullUrl = seriesUrl.substring(0, lastSlash + 1) + href;
                        }
                    }
                }

                // 去重
                String urlKey = fullUrl;
                if (seenUrls.contains(urlKey)) continue;

                // 用正则匹配集数
                Matcher matcher = episodeRegex.matcher(fullUrl);
                if (matcher.find()) {
                    String episodeStr;
                    try {
                        episodeStr = matcher.group(groupIdx);
                    } catch (Exception e) {
                        episodeStr = matcher.group(1);
                    }

                    int episodeNum;
                    try {
                        episodeNum = Integer.parseInt(episodeStr);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    seenUrls.add(urlKey);
                    Map<String, Object> ep = new LinkedHashMap<>();
                    ep.put("episodeNumber", episodeNum);
                    ep.put("url", fullUrl);
                    ep.put("text", text.isEmpty() ? "第" + episodeNum + "集" : text);
                    episodes.add(ep);
                }
            }

            // 按集数排序
            episodes.sort(Comparator.comparingInt(e -> (int) e.get("episodeNumber")));

            log.info("剧集主页解析完成，共 {} 集", episodes.size());
            return episodes;

        } catch (Exception e) {
            log.warn("剧集主页解析失败: {} - {}", seriesUrl, e.getMessage());
            return Collections.emptyList();
        }
    }
}
