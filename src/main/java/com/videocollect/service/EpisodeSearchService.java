package com.videocollect.service;

import com.videocollect.dao.VideoGroupDao;
import com.videocollect.dao.VideoRecordDao;
import com.videocollect.dao.VideoSourceDao;
import com.videocollect.model.VideoGroup;
import com.videocollect.model.VideoRecord;
import com.videocollect.model.VideoSource;
import com.videocollect.service.VideoChecker.CheckResult;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 剧集搜索与批量导入服务
 */
@Service
public class EpisodeSearchService {

    private static final Logger log = LoggerFactory.getLogger(EpisodeSearchService.class);

    @Autowired
    private VideoSourceDao videoSourceDao;

    @Autowired
    private VideoSourceService videoSourceService;

    @Autowired
    private VideoGroupDao videoGroupDao;

    @Autowired
    private VideoRecordDao videoRecordDao;

    @Autowired
    private VideoChecker videoChecker;

    @Autowired
    private PageFetcher pageFetcher;

    @Autowired
    private VideoParser videoParser;

    // ====== 全源搜索 ======

    /**
     * 全源搜索：在指定视频源（或全部）中搜索关键词
     *
     * @param keyword   关键词
     * @param sourceIds 要搜索的视频源 ID 列表，null 或空集合表示搜索全部
     * @return Map<sourceId, {source, results}>
     */
    public Map<Long, SourceSearchResult> searchAll(String keyword, List<Long> sourceIds) {
        List<VideoSource> sources;
        if (sourceIds != null && !sourceIds.isEmpty()) {
            sources = videoSourceDao.findByIds(sourceIds);
        } else {
            sources = videoSourceDao.findAll();
        }
        log.info("searchAll sourceIds={}, filterActive={}, sourcesCount={}", sourceIds, sourceIds != null && !sourceIds.isEmpty(), sources.size());
        Map<Long, SourceSearchResult> grouped = new LinkedHashMap<>();

        for (VideoSource source : sources) {
            try {
                List<Map<String, String>> results = videoSourceService.testSearch(source, keyword);
                grouped.put(source.getId(), new SourceSearchResult(source, results));
            } catch (Exception e) {
                log.warn("视频源 {} 搜索失败: {}", source.getName(), e.getMessage());
                grouped.put(source.getId(), new SourceSearchResult(source, Collections.emptyList()));
            }
        }

        return grouped;
    }

    /**
     * 单源搜索
     */
    public List<Map<String, String>> searchSource(Long sourceId, String keyword) {
        VideoSource source = videoSourceDao.findById(sourceId);
        if (source == null) return Collections.emptyList();
        return videoSourceService.testSearch(source, keyword);
    }

    // ====== 解析剧集列表 ======

    /**
     * 解析剧集主页，返回集数列表
     */
    public List<Map<String, Object>> parseEpisodes(Long sourceId, String seriesUrl) {
        VideoSource source = videoSourceDao.findById(sourceId);
        if (source == null) return Collections.emptyList();
        return videoSourceService.parseEpisodes(source, seriesUrl);
    }

    // ====== 批量导入 ======

    /**
     * 批量导入集数
     *
     * @param groupId  目标合集ID
     * @param episodes 要导入的集数列表 [{episodeNumber, url}]
     * @return 导入结果
     */
    public BatchImportResult batchImport(Long groupId, List<EpisodeItem> episodes) {
        VideoGroup group = videoGroupDao.findById(groupId);
        if (group == null) {
            return new BatchImportResult(false, "合集不存在", 0, 0, 0, Collections.emptyList());
        }

        int success = 0, fail = 0, skipped = 0;
        List<Map<String, Object>> detail = new ArrayList<>();

        for (EpisodeItem item : episodes) {
            try {
                Map<String, Object> itemResult = new LinkedHashMap<>();
                itemResult.put("episodeNumber", item.getEpisodeNumber());
                itemResult.put("url", item.getUrl());

                // 去重：同一 source_url 不重复导入
                VideoRecord exist = videoRecordDao.findBySourceUrl(item.getUrl());
                if (exist != null) {
                    itemResult.put("status", "skipped");
                    itemResult.put("message", "已存在，ID=" + exist.getId());
                    skipped++;
                    detail.add(itemResult);
                    continue;
                }

                // 构造标题
                String title = group.getName() + " 第" + item.getEpisodeNumber() + "集";

                // 检测可播放性
                CheckResult checkResult = videoChecker.check(item.getUrl());
                int status = checkResult.isPlayable() ? 1 : 2;

                // 入库
                VideoRecord record = new VideoRecord(title, item.getUrl(), item.getUrl(),
                        status, checkResult.getLatencyMs(), null);
                record.setGroupId(groupId);
                record.setEpisodeNumber(item.getEpisodeNumber());
                record.setRemark("批量导入");

                if (checkResult.getErrorMsg() != null) {
                    record.setRemark("批量导入；检测结果: " + checkResult.getErrorMsg());
                }

                videoRecordDao.insert(record);

                // 如果 video_url 不是视频直链（是网页），尝试解析真正的视频地址
                if (!isDirectVideoUrl(item.getUrl())) {
                    tryReparseVideoUrl(record);
                    // tryReparseVideoUrl 可能更新了 video_url，重新检测新地址
                    VideoRecord updated = videoRecordDao.findById(record.getId());
                    if (updated != null && updated.getVideoUrl() != null
                            && !updated.getVideoUrl().equals(item.getUrl())) {
                        CheckResult reCheck = videoChecker.check(updated.getVideoUrl());
                        int newStatus = reCheck.isPlayable() ? 1 : 2;
                        videoRecordDao.updateStatus(record.getId(), newStatus, reCheck.getLatencyMs(), reCheck.getErrorMsg());
                        status = newStatus;
                    }
                }

                itemResult.put("status", status == 1 ? "success" : "unplayable");
                itemResult.put("id", record.getId());
                success++;
                detail.add(itemResult);

            } catch (Exception e) {
                log.warn("导入集数 {} 失败: {}", item.getEpisodeNumber(), e.getMessage());
                Map<String, Object> itemResult = new LinkedHashMap<>();
                itemResult.put("episodeNumber", item.getEpisodeNumber());
                itemResult.put("url", item.getUrl());
                itemResult.put("status", "failed");
                itemResult.put("message", e.getMessage());
                fail++;
                detail.add(itemResult);
            }
        }

        // 更新合集的 known total_episodes
        Integer maxEp = videoRecordDao.findMaxEpisodeNumber(groupId);
        if (maxEp != null) {
            group.setTotalEpisodes(maxEp);
            videoGroupDao.update(group);
        }

        log.info("批量导入完成: 成功={}, 跳过={}, 失败={}", success, skipped, fail);
        return new BatchImportResult(true, "导入完成", success, skipped, fail, detail);
    }

    /**
     * 尝试重新解析视频地址（用 Jsoup + VideoParser，与 CollectService.tryReparse 一致）
     */
    private void tryReparseVideoUrl(VideoRecord record) {
        try {
            String url = record.getVideoUrl();
            Document doc = pageFetcher.fetch(url);
            List<String> videoUrls = null;
            if (doc != null) {
                videoUrls = videoParser.parse(doc);
            }
            // 用移动端 UA 重试
            if (videoUrls == null || videoUrls.isEmpty()) {
                Document mobileDoc = pageFetcher.fetch(url,
                        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36");
                if (mobileDoc != null) {
                    videoUrls = videoParser.parse(mobileDoc);
                }
            }
            if (videoUrls != null && !videoUrls.isEmpty()) {
                String videoUrl = videoUrls.get(0);
                videoRecordDao.updateVideoUrl(record.getId(), videoUrl);
                log.info("批量导入时解析到视频直链: {}", videoUrl);
            } else {
                log.debug("批量导入时页面中未发现视频链接: {}", url);
            }
        } catch (Exception e) {
            log.debug("批量导入时视频地址解析失败: {}", e.getMessage());
        }
    }

    private boolean isDirectVideoUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains(".mp4") || lower.contains(".m3u8")
                || lower.contains(".webm") || lower.contains(".flv")
                || lower.contains(".ts") || lower.contains(".mkv");
    }

    // ====== 下一集检测 ======

    /**
     * 检测某视频的下一集是否已更新/已收录
     */
    public NextEpisodeResult checkNextEpisode(Long videoId) {
        VideoRecord record = videoRecordDao.findById(videoId);
        if (record == null) {
            return new NextEpisodeResult(false, "记录不存在", null, null);
        }

        Long groupId = record.getGroupId();
        Integer episodeNumber = record.getEpisodeNumber();

        if (groupId == null || episodeNumber == null) {
            return new NextEpisodeResult(false, "该视频不在合集中或无集数信息", null, null);
        }

        int nextEpisode = episodeNumber + 1;

        // 检查是否已收录
        VideoRecord existing = videoRecordDao.findByGroupAndEpisode(groupId, nextEpisode);
        if (existing != null) {
            return new NextEpisodeResult(true, "已收录", nextEpisode, existing.getSourceUrl());
        }

        // 尝试从当前 URL 推断下一集 URL
        String currentUrl = record.getSourceUrl();
        String nextUrl = inferNextUrl(currentUrl, nextEpisode);

        if (nextUrl != null) {
            return new NextEpisodeResult(true, "未收录，可导入", nextEpisode, nextUrl);
        }

        return new NextEpisodeResult(false, "无法推断下一集地址", nextEpisode, null);
    }

    /**
     * 根据当前 URL 推断下一集 URL（将 URL 中最后一个数字替换为下一集数）
     */
    private String inferNextUrl(String currentUrl, int nextEpisode) {
        if (currentUrl == null || currentUrl.isEmpty()) return null;

        // 尝试匹配 URL 中最后一个数字段并替换
        Pattern p = Pattern.compile("(.*?)(\\d+)(\\.[a-zA-Z]+(?:\\?.*)?)$");
        Matcher m = p.matcher(currentUrl);
        if (m.find()) {
            String prefix = m.group(1);
            String suffix = m.group(3);
            return prefix + nextEpisode + suffix;
        }

        // 尝试匹配路径中的数字（如 /2-265.html → /2-266.html）
        p = Pattern.compile("(.*?[\\-/])(\\d+)([\\-/]?.*)");
        m = p.matcher(currentUrl);
        if (m.find()) {
            String before = m.group(1);
            String after = m.group(3);
            return before + nextEpisode + after;
        }

        return null;
    }

    // ====== 内部类 ======

    public static class SourceSearchResult {
        private VideoSource source;
        private List<Map<String, String>> results;

        public SourceSearchResult(VideoSource source, List<Map<String, String>> results) {
            this.source = source;
            this.results = results;
        }

        public VideoSource getSource() { return source; }
        public List<Map<String, String>> getResults() { return results; }
    }

    public static class EpisodeItem {
        private int episodeNumber;
        private String url;

        public EpisodeItem() {}

        public EpisodeItem(int episodeNumber, String url) {
            this.episodeNumber = episodeNumber;
            this.url = url;
        }

        public int getEpisodeNumber() { return episodeNumber; }
        public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class BatchImportResult {
        private boolean success;
        private String message;
        private int successCount;
        private int skippedCount;
        private int failCount;
        private List<Map<String, Object>> detail;

        public BatchImportResult(boolean success, String message, int successCount,
                                 int skippedCount, int failCount, List<Map<String, Object>> detail) {
            this.success = success;
            this.message = message;
            this.successCount = successCount;
            this.skippedCount = skippedCount;
            this.failCount = failCount;
            this.detail = detail;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getSuccessCount() { return successCount; }
        public int getSkippedCount() { return skippedCount; }
        public int getFailCount() { return failCount; }
        public List<Map<String, Object>> getDetail() { return detail; }
    }

    public static class NextEpisodeResult {
        private boolean available;
        private String message;
        private Integer nextEpisodeNumber;
        private String nextUrl;

        public NextEpisodeResult(boolean available, String message, Integer nextEpisodeNumber, String nextUrl) {
            this.available = available;
            this.message = message;
            this.nextEpisodeNumber = nextEpisodeNumber;
            this.nextUrl = nextUrl;
        }

        public boolean isAvailable() { return available; }
        public String getMessage() { return message; }
        public Integer getNextEpisodeNumber() { return nextEpisodeNumber; }
        public String getNextUrl() { return nextUrl; }
    }
}
