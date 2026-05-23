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
        if (source.getSortOrder() == null) {
            source.setSortOrder(0);
        }
        if (source.getEpisodeGroup() == null) {
            source.setEpisodeGroup(1);
        }
        if (source.getSearchDataPath() == null) {
            source.setSearchDataPath("$");
        }
        if (source.getSearchTitleField() == null) {
            source.setSearchTitleField("title");
        }
        if (source.getSearchUrlField() == null) {
            source.setSearchUrlField("url");
        }
        if (source.getEncoding() == null) {
            source.setEncoding("UTF-8");
        }
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

    /**
     * 批量导入视频源
     * 清除 ID 和时间戳以使用数据库自增和默认时间
     */
    public int importSources(List<VideoSource> sources) {
        for (VideoSource s : sources) {
            s.setId(null);
            s.setCreatedAt(null);
            s.setUpdatedAt(null);
            if (s.getSortOrder() == null) s.setSortOrder(0);
            if (s.getEpisodeGroup() == null) s.setEpisodeGroup(1);
            if (s.getSearchDataPath() == null) s.setSearchDataPath("$");
            if (s.getSearchTitleField() == null) s.setSearchTitleField("title");
            if (s.getSearchUrlField() == null) s.setSearchUrlField("url");
            if (s.getEncoding() == null) s.setEncoding("UTF-8");
        }
        return videoSourceDao.insertBatch(sources);
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

        // 检测响应类型：JSON 还是 HTML
        String trimmed = response.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // JSON 格式
            try {
                return parseSearchResults(source, response);
            } catch (Exception e) {
                log.warn("JSON 搜索结果解析失败: {}", e.getMessage());
                return Collections.emptyList();
            }
        } else if (trimmed.startsWith("<")) {
            // HTML 格式 → 用 Jsoup 解析
            log.info("搜索返回 HTML，尝试 HTML 解析");
            return parseHtmlSearchResults(source, searchUrl, response);
        } else {
            log.warn("搜索结果格式未知，尝试 JSON 解析: {}", trimmed.substring(0, Math.min(50, trimmed.length())));
            try {
                return parseSearchResults(source, response);
            } catch (Exception e) {
                return Collections.emptyList();
            }
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
     * 解析 HTML 格式的搜索结果
     * 通用策略：先找容器，再全页扫描白名单模式，最后兜底去噪
     */
    private List<Map<String, String>> parseHtmlSearchResults(VideoSource source, String searchUrl, String html) {
        List<Map<String, String>> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, searchUrl);
            org.jsoup.nodes.Element searchList = doc.selectFirst("ul.search-list");

            // 策略0：专门解析 <ul class="search-list">（maccms 系站点通用）
            if (searchList != null) {
                org.jsoup.select.Elements items = searchList.select("> li");
                for (org.jsoup.nodes.Element item : items) {
                    org.jsoup.nodes.Element titleLink = item.selectFirst("h5.subject a[href], .subject a[href], a[href]");
                    if (titleLink == null) continue;
                    String href = titleLink.attr("href").trim();
                    String text = titleLink.text().trim();
                    if (text.isEmpty() || href.isEmpty()) continue;
                    if (isNoiseLink(href, text)) continue;
                    // 取 <b> 内的核心标题（去掉 douban_score 等后缀噪声）
                    org.jsoup.nodes.Element b = titleLink.selectFirst("b");
                    if (b != null) {
                        String bText = b.text().trim();
                        if (!bText.isEmpty() && bText.length() < 50) text = bText;
                    }
                    String fullUrl = resolveUrl(href, source.getHomeUrl());
                    if (fullUrl == null || seenUrls.contains(fullUrl)) continue;
                    seenUrls.add(fullUrl);
                    java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
                    result.put("title", text);
                    result.put("url", fullUrl);
                    // 取封面
                    org.jsoup.nodes.Element img = item.selectFirst("img[src]");
                    if (img != null) {
                        String imgSrc = img.attr("src").trim();
                        if (!imgSrc.isEmpty() && !imgSrc.startsWith("data:")) {
                            result.put("cover", resolveUrl(imgSrc, source.getHomeUrl()));
                        }
                    }
                    results.add(result);
                }
                if (!results.isEmpty()) {
                    log.info("HTML 搜索结果解析完成，共 {} 条 [策略0-search-list]", results.size());
                    return results;
                }
            }

            // 策略1：查找常见搜索结果容器中的链接
            String[] selectors = {
                ".search-result a[href]", ".vod-list a[href]", ".module-list a[href]",
                "ul.vodlist a[href]", "ul.video-list a[href]", ".stui-vodlist a[href]",
                ".search-list a[href]", ".xing-vodlist a[href]", ".fed-vod-list a[href]"
            };

            for (String sel : selectors) {
                org.jsoup.select.Elements links = doc.select(sel);
                if (!links.isEmpty()) {
                    for (org.jsoup.nodes.Element link : links) {
                        addHtmlResult(source, results, seenUrls, link);
                    }
                    if (!results.isEmpty()) break;
                }
            }

            // 策略2：全页扫描，按白名单 URL 模式匹配剧集详情页
            if (results.isEmpty()) {
                org.jsoup.select.Elements allLinks = doc.select("a[href]");
                for (org.jsoup.nodes.Element link : allLinks) {
                    String href = link.attr("href").trim();
                    String text = link.text().trim();
                    if (text.isEmpty() || text.length() < 2) continue;
                    if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript")) continue;
                    if (text.equals("首页") || text.equals("上一页") || text.equals("下一页")
                        || text.contains("登录") || text.contains("注册") || text.contains("关于")) continue;
                    // 剧集详情页常见 URL 模式（含字母数字混合路径）
                    String hrefLower = href.toLowerCase();
                    if (hrefLower.matches(".*/lty/\\d+.*") || hrefLower.matches(".*/nlu/\\d+.*")
                        || hrefLower.matches(".*/vod/\\d+.*") || hrefLower.matches(".*/detail/\\d+.*")
                        || hrefLower.matches(".*/show/\\d+.*") || hrefLower.matches(".*/mip/\\d+.*")
                        || hrefLower.matches(".*/play/\\w+.*") || hrefLower.matches(".*/content/\\w+.*")) {
                        addHtmlResult(source, results, seenUrls, link);
                    }
                }
            }

            // 策略3：兜底——所有链接中去噪
            if (results.isEmpty()) {
                org.jsoup.select.Elements allLinks = doc.select("a[href]");
                for (org.jsoup.nodes.Element link : allLinks) {
                    String href = link.attr("href").trim();
                    String text = link.text().trim();
                    if (text.isEmpty() || text.length() < 2) continue;
                    if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript")) continue;
                    if (isNoiseLink(href, text)) continue;
                    // 要求 URL 至少包含数字（区分详情页与普通页面）
                    if (href.matches(".*\\d+.*") || text.length() >= 4) {
                        addHtmlResult(source, results, seenUrls, link);
                    }
                }
            }

            log.info("HTML 搜索结果解析完成，共 {} 条", results.size());
            return results;

        } catch (Exception e) {
            log.warn("HTML 搜索结果解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 判断是否为噪音链接（导航、地图、搜索翻页等）
     */
    private boolean isNoiseLink(String href, String text) {
        // 排除 RSS/地图/标签/搜索翻页等
        if (href.contains("/rss/") || href.contains("/label/") || href.contains("/search/")
            || href.contains("/tag/") || href.contains("/page/") || href.contains("/sitemap")
            || href.contains("/xml/") || href.contains(".xml") || href.contains("baidu")) {
            return true;
        }
        // 排除噪音文本
        String[] noiseTexts = {"网站地图", "百度地图", "360地图", "神马地图", "搜狗地图", "头条地图",
            "热播榜", "首页", "上一页", "下一页", "登录", "注册", "关于我们", "友情链接",
            "网站首页", "网站导航", "最近更新"};
        for (String nt : noiseTexts) {
            if (text.contains(nt)) return true;
        }
        return false;
    }

    /**
     * 将 <a> 标签加入搜索结果（补全 URL、去重、去噪）
     */
    private void addHtmlResult(VideoSource source, List<Map<String, String>> results,
                                Set<String> seenUrls, org.jsoup.nodes.Element link) {
        String href = link.attr("href").trim();
        String text = link.text().trim();
        if (text.isEmpty() || href.isEmpty()) return;
        if (isNoiseLink(href, text)) return;

        // 补全相对路径
        String fullUrl = resolveUrl(href, source.getHomeUrl());
        if (fullUrl == null) return;

        // 去重
        String key = fullUrl;
        if (seenUrls.contains(key)) return;
        seenUrls.add(key);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("title", text);
        result.put("url", fullUrl);

        // 查找同级别的 <img> 作为封面
        org.jsoup.nodes.Element img = link.select("img[src]").first();
        if (img == null && link.parent() != null) {
            img = link.parent().select("img[src]").first();
        }
        if (img != null) {
            String imgSrc = img.attr("src").trim();
            if (!imgSrc.isEmpty() && !imgSrc.startsWith("data:")) {
                result.put("cover", resolveUrl(imgSrc, source.getHomeUrl()));
            }
        }

        results.add(result);
    }

    /**
     * 补全相对 URL
     */
    private String resolveUrl(String href, String baseUrl) {
        if (href == null || href.isEmpty()) return null;
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) {
            if (baseUrl == null || baseUrl.isEmpty()) return href;
            String base = baseUrl;
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + href;
        }
        return href;
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

    // ====== 正则智能建议 ======

    /**
     * 根据示例播放 URL 自动推测集数正则和首页地址
     * 输入: https://www.xigua9.com/play/RcVn-1-1.html
     * 输出: { homeUrl, pattern, group, sampleUrls }
     */
    public Map<String, Object> suggestRegex(String seriesUrl) {
        Map<String, Object> result = new HashMap<>();
        result.put("pattern", "");
        result.put("group", 1);
        result.put("homeUrl", "");
        result.put("sampleUrls", Collections.emptyList());

        if (seriesUrl == null || seriesUrl.trim().isEmpty()) {
            return result;
        }

        try {
            java.net.URL urlObj = new java.net.URL(seriesUrl);
            String homeUrl = urlObj.getProtocol() + "://" + urlObj.getHost();
            String path = urlObj.getPath();

            // 按 / 分割，找到第一个含数字的段，取它及之后的部分
            String[] segments = path.split("/");
            int startIdx = -1;
            for (int i = 0; i < segments.length; i++) {
                if (segments[i].matches(".*\\d+.*")) {
                    startIdx = i;
                    break;
                }
            }
            if (startIdx < 0) {
                result.put("homeUrl", homeUrl);
                return result;
            }

            // 拼接从 startIdx 开始的部分，去掉首个数字段前的固定前缀
            StringBuilder relevantPart = new StringBuilder();
            for (int i = startIdx; i < segments.length; i++) {
                if (i > startIdx) relevantPart.append('/');
                String seg = segments[i];
                if (i == startIdx) {
                    // 跳过第一个数字段中的非数字前缀（如 RcVn-1-1.html → 1-1.html）
                    int digitPos = -1;
                    for (int j = 0; j < seg.length(); j++) {
                        if (Character.isDigit(seg.charAt(j))) {
                            digitPos = j;
                            break;
                        }
                    }
                    if (digitPos > 0) seg = seg.substring(digitPos);
                }
                relevantPart.append(seg);
            }
            String subPath = relevantPart.toString();

            // 转义正则特殊字符（数字之外的字符）
            String escaped = subPath.replaceAll("([.+*?^${}()|\\[\\]\\\\])", "\\\\$1");
            // 将数字序列替换为捕获组 (\d+)
            String regexPattern = escaped.replaceAll("\\d+", "(\\\\d+)");

            // 统计捕获组数量
            int groupCount = 0;
            int idx = 0;
            while ((idx = regexPattern.indexOf("(\\d+)", idx)) != -1) {
                groupCount++;
                idx += 6;
            }
            int suggestedGroup = groupCount >= 2 ? groupCount : 1;

            result.put("pattern", regexPattern);
            result.put("group", suggestedGroup);
            result.put("homeUrl", homeUrl);
            result.put("sampleUrls", Collections.singletonList(seriesUrl));
            return result;

        } catch (Exception e) {
            log.warn("suggestRegex 解析URL失败: {}", e.getMessage());
            return result;
        }
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
            Document doc = pageFetcher.fetch(seriesUrl);
            if (doc == null) {
                log.warn("剧集主页抓取失败: {}", seriesUrl);
                return Collections.emptyList();
            }

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
