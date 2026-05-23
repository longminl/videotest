package com.videocollect.service;

import com.videocollect.dao.VideoRecordDao;
import com.videocollect.dto.CheckProgress;
import com.videocollect.model.VideoRecord;
import com.videocollect.service.VideoChecker.CheckResult;
import com.videocollect.service.VideoDlpResolver.DlResult;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 视频收藏业务编排服务
 */
@Service
public class CollectService {

    private static final Logger log = LoggerFactory.getLogger(CollectService.class);

    @Autowired
    private PageFetcher pageFetcher;

    @Autowired
    private VideoParser videoParser;

    @Autowired
    private VideoChecker videoChecker;

    @Autowired
    private VideoRecordDao videoRecordDao;

    @Autowired
    private VideoDlpResolver videoDlpResolver;

    @Autowired
    private EpisodeExtractor episodeExtractor;

    /** 移动端 User-Agent（用于反反爬 && 某些仅手机版才嵌入视频地址的影视站） */
    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; SM-S908E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36";
    private static final String[] VIDEO_EXTENSIONS = {
        ".mp4", ".webm", ".avi", ".mov", ".mkv", ".flv",
        ".wmv", ".m3u8", ".ts", ".mpg", ".mpeg", ".3gp"
    };

    /**
     * 收集视频：入口方法
     *
     * @param url 用户输入的URL（网页链接或视频直链）
     * @return 收集结果描述
     */
    public CollectResult collect(String url) {
        if (url == null || url.trim().isEmpty()) {
            return CollectResult.failure("URL 不能为空");
        }

        url = url.trim();

        // 去重检测
        VideoRecord exist = videoRecordDao.findBySourceUrl(url);
        if (exist != null) {
            return CollectResult.failure("该链接已收录，ID: " + exist.getId());
        }

        // 判断是否为视频直链
        if (isDirectVideoUrl(url)) {
            return collectDirectVideo(url);
        }

        // 否则当作网页处理，抓取 + 解析
        return collectFromPage(url);
    }

    /**
     * 收集视频直链
     */
    private CollectResult collectDirectVideo(String url) {
        log.info("检测到视频直链: {}", url);

        // 提取文件名作为标题
        String title = extractFileName(url);

        // 检测可播放性和延迟
        CheckResult checkResult = videoChecker.check(url);
        int status = checkResult.isPlayable() ? 1 : 2;
        String remark = checkResult.getErrorMsg();

        // 入库
        VideoRecord record = new VideoRecord(title, url, url, status,
                checkResult.getLatencyMs(), null);
        record.setRemark(remark);
        record.setEpisodeNumber(episodeExtractor.extractFromUrl(url));
        videoRecordDao.insert(record);

        log.info("视频直链收录完成, ID={}, title={}, status={}", record.getId(), title, status);
        return CollectResult.success("收录成功（直链）", record);
    }

    /**
     * 从网页中解析视频
     */
    private CollectResult collectFromPage(String url) {
        log.info("开始从网页解析视频: {}", url);

        // 1. 抓取页面
        Document doc = pageFetcher.fetch(url);
        if (doc == null) {
            return CollectResult.failure("页面抓取失败，请检查URL是否可访问");
        }

        // 2. 提取页面标题
        String pageTitle = videoParser.extractPageTitle(doc);

        // 3. 解析视频链接（优先 Jsoup 静态解析）
        List<String> videoUrls = videoParser.parse(doc);
        if (videoUrls.isEmpty()) {
            // 3a. 桌面 UA 未找到 → 尝试手机 UA 重抓（部分影视站仅手机版嵌入视频地址）
            log.info("桌面 UA 未找到视频，尝试手机 UA 重抓: {}", url);
            Document mobileDoc = pageFetcher.fetch(url, MOBILE_UA);
            if (mobileDoc != null) {
                videoUrls = videoParser.parse(mobileDoc);
                if (!videoUrls.isEmpty()) {
                    log.info("手机 UA 解析成功，共 {} 个视频链接", videoUrls.size());
                }
            }
        }
        if (!videoUrls.isEmpty()) {
            // Jsoup 成功，取第一个视频检测并入库
            String videoUrl = videoUrls.get(0);
            String title = pageTitle != null ? pageTitle : extractFileName(videoUrl);
            String source = "Jsoup 解析（共 " + videoUrls.size() + " 个视频链接）";
            return saveAndCheck(title, url, videoUrl, pageTitle, source);
        }

        // 4. Jsoup 未找到，降级到 yt-dlp 解析
        log.info("Jsoup 未找到视频链接，尝试 yt-dlp 解析: {}", url);
        DlResult dlResult = videoDlpResolver.resolve(url);
        if (dlResult.isSuccess()) {
            log.info("yt-dlp 解析成功: {}", dlResult.getTitle());
            String title = dlResult.getTitle() != null ? dlResult.getTitle() : "未命名";
            return saveAndCheck(title, url, dlResult.getVideoUrl(), pageTitle, "yt-dlp 解析");
        }

        // 5. 两者都失败，记录为"解析失败"
        String failReason = dlResult.getErrorMsg();
        log.warn("yt-dlp 也解析失败: {}", failReason);
        VideoRecord record = new VideoRecord(pageTitle != null ? pageTitle : "未命名",
                url, url, 3, null, pageTitle);
        record.setRemark("页面中未发现视频链接" + (failReason != null ? "（" + failReason + "）" : ""));
        record.setEpisodeNumber(episodeExtractor.extract(url, pageTitle));
        videoRecordDao.insert(record);
        return CollectResult.failure("视频解析失败: " + (failReason != null ? failReason : "页面中未发现视频链接"));
    }

    /**
     * 保存视频记录并检测可播放性
     */
    private CollectResult saveAndCheck(String title, String sourceUrl, String videoUrl,
                                       String pageTitle, String sourceInfo) {
        CheckResult checkResult = videoChecker.check(videoUrl);
        int status = checkResult.isPlayable() ? 1 : 2;
        String remark = sourceInfo + (checkResult.getErrorMsg() != null
                ? "；检测结果: " + checkResult.getErrorMsg() : "");

        VideoRecord record = new VideoRecord(title, sourceUrl, videoUrl, status,
                checkResult.getLatencyMs(), pageTitle);
        record.setRemark(remark);
        record.setEpisodeNumber(episodeExtractor.extract(sourceUrl, pageTitle));
        videoRecordDao.insert(record);

        log.info("收录完成, ID={}, title={}, status={}, source={}",
                record.getId(), title, status, sourceInfo);

        String msg = "收录成功（" + sourceInfo + "）"
                + (status == 1 ? "，延迟 " + checkResult.getLatencyMs() + "ms" : "，不可播放");
        return CollectResult.success(msg, record);
    }

    /**
     * 重新检测单条记录（video_url==source_url 时重新解析页面）
     */
    public CollectResult recheck(Long id) {
        VideoRecord record = videoRecordDao.findById(id);
        if (record == null) {
            return CollectResult.failure("记录不存在");
        }

        // 如果 video_url 与 source_url 相同（之前解析失败），重新解析页面
        if (record.getVideoUrl() != null && record.getVideoUrl().equals(record.getSourceUrl())) {
            CollectResult parsed = tryReparse(record);
            if (parsed.isSuccess()) return parsed;
        }

        // 原有逻辑：直接检测 stored video_url
        CheckResult result = videoChecker.check(record.getVideoUrl());
        int status = result.isPlayable() ? 1 : 2;
        videoRecordDao.updateStatus(id, status, result.getLatencyMs(), result.getErrorMsg());
        videoRecordDao.updateIsCached(id, false);

        String msg = result.isPlayable() ? "检测成功，延迟 " + result.getLatencyMs() + "ms"
                : "检测失败: " + result.getErrorMsg();
        return CollectResult.success(msg, id);
    }

    /**
     * 尝试重新解析 source_url，如成功则更新记录并返回 success
     */
    private CollectResult tryReparse(VideoRecord record) {
        Long id = record.getId();
        String sourceUrl = record.getSourceUrl();
        log.info("video_url == source_url，重新解析页面: {}", sourceUrl);

        // 直链解析
        if (isDirectVideoUrl(sourceUrl)) {
            CheckResult checkResult = videoChecker.check(sourceUrl);
            int status = checkResult.isPlayable() ? 1 : 2;
            videoRecordDao.updateVideoUrl(id, sourceUrl);
            videoRecordDao.updateStatus(id, status, checkResult.getLatencyMs(), checkResult.getErrorMsg());
            videoRecordDao.updateIsCached(id, false);
            String msg = status == 1 ? "重新解析成功（直链），延迟 " + checkResult.getLatencyMs() + "ms"
                    : "重新解析失败: " + checkResult.getErrorMsg();
            return CollectResult.success(msg, id);
        }

        // Jsoup 抓取 + 解析
        Document doc = pageFetcher.fetch(sourceUrl);
        if (doc == null) return CollectResult.failure("页面抓取失败");

        String pageTitle = videoParser.extractPageTitle(doc);
        List<String> videoUrls = videoParser.parse(doc);
        if (videoUrls.isEmpty()) {
            Document mobileDoc = pageFetcher.fetch(sourceUrl, MOBILE_UA);
            if (mobileDoc != null) {
                videoUrls = videoParser.parse(mobileDoc);
            }
        }

        if (!videoUrls.isEmpty()) {
            return saveReparseResult(id, videoUrls.get(0), videoUrls.size(),
                    pageTitle, "Jsoup 重新解析");
        }

        // yt-dlp 降级
        DlResult dlResult = videoDlpResolver.resolve(sourceUrl);
        if (dlResult.isSuccess()) {
            return saveReparseResult(id, dlResult.getVideoUrl(), 1,
                    dlResult.getTitle(), "yt-dlp 重新解析");
        }

        log.warn("重新解析也失败: {}", dlResult.getErrorMsg());
        return CollectResult.failure("重新解析失败: " + dlResult.getErrorMsg());
    }

    /**
     * 保存重新解析结果到已有记录
     */
    private CollectResult saveReparseResult(Long id, String newVideoUrl, int urlCount,
                                            String newPageTitle, String source) {
        CheckResult checkResult = videoChecker.check(newVideoUrl);
        int status = checkResult.isPlayable() ? 1 : 2;
        String remark = source + "（共 " + urlCount + " 个视频链接）"
                + (checkResult.getErrorMsg() != null ? "；检测结果: " + checkResult.getErrorMsg() : "");

        videoRecordDao.updateVideoUrl(id, newVideoUrl);
        videoRecordDao.updateStatus(id, status, checkResult.getLatencyMs(), remark);
        if (newPageTitle != null) {
            videoRecordDao.updatePageTitle(id, newPageTitle);
        }
        videoRecordDao.updateIsCached(id, false);
        // 重新提取集数（URL 或新标题可能含集数信息）
        Integer ep = episodeExtractor.extract(newVideoUrl, newPageTitle);
        if (ep != null) {
            videoRecordDao.updateEpisodeNumber(id, ep);
        }

        log.info("重新解析成功, ID={}, new video_url={}, status={}", id, newVideoUrl, status);
        String msg = source + "成功"
                + (status == 1 ? "，延迟 " + checkResult.getLatencyMs() + "ms" : "，不可播放");
        return CollectResult.success(msg, id);
    }

    /**
     * 批量重新检测（同步执行，返回进度对象）
     */
    public CheckProgress recheckAll() {
        List<VideoRecord> list = videoRecordDao.findNeedCheck();
        CheckProgress progress = new CheckProgress(list.size());

        for (VideoRecord record : list) {
            progress.setCurrentUrl(record.getTitle());

            if (record.getVideoUrl() != null && record.getVideoUrl().equals(record.getSourceUrl())) {
                // 尝试重新解析
                Document doc = pageFetcher.fetch(record.getSourceUrl());
                List<String> videoUrls = new java.util.ArrayList<>();
                if (doc != null) {
                    videoUrls = videoParser.parse(doc);
                    if (videoUrls.isEmpty()) {
                        Document mobileDoc = pageFetcher.fetch(record.getSourceUrl(), MOBILE_UA);
                        if (mobileDoc != null) {
                            videoUrls = videoParser.parse(mobileDoc);
                        }
                    }
                }

                if (!videoUrls.isEmpty()) {
                    String newVideoUrl = videoUrls.get(0);
                    CheckResult checkResult = videoChecker.check(newVideoUrl);
                    int status = checkResult.isPlayable() ? 1 : 2;
                    String remark = "批量重新解析（共 " + videoUrls.size() + " 个视频链接）"
                            + (checkResult.getErrorMsg() != null ? "；检测结果: " + checkResult.getErrorMsg() : "");
                    videoRecordDao.updateVideoUrl(record.getId(), newVideoUrl);
                    videoRecordDao.updateStatus(record.getId(), status, checkResult.getLatencyMs(), remark);

                    if (status == 1) progress.setSuccessCount(progress.getSuccessCount() + 1);
                    else progress.setFailCount(progress.getFailCount() + 1);
                    progress.setCompleted(progress.getCompleted() + 1);
                    continue;
                }
            }

            // 原有逻辑：直接检测 stored video_url
            CheckResult result = videoChecker.check(record.getVideoUrl());
            int status = result.isPlayable() ? 1 : 2;
            videoRecordDao.updateStatus(record.getId(), status,
                    result.getLatencyMs(), result.getErrorMsg());

            progress.setCompleted(progress.getCompleted() + 1);
            if (result.isPlayable()) {
                progress.setSuccessCount(progress.getSuccessCount() + 1);
            } else {
                progress.setFailCount(progress.getFailCount() + 1);
            }
        }

        progress.setFinished(true);
        return progress;
    }

    /**
     * 判断是否为直接视频链接
     */
    private boolean isDirectVideoUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.contains(ext)) {
                return true;
            }
        }
        // 也支持 m3u8 链接
        return lower.contains(".m3u8");
    }

    /**
     * 从URL中提取文件名
     */
    private String extractFileName(String url) {
        if (url == null || url.isEmpty()) return "未命名";
        // 取最后一个 / 之后的内容
        int lastSlash = url.lastIndexOf('/');
        String fileName = (lastSlash >= 0) ? url.substring(lastSlash + 1) : url;
        // 去掉查询参数
        int queryIndex = fileName.indexOf('?');
        if (queryIndex > 0) {
            fileName = fileName.substring(0, queryIndex);
        }
        return fileName.isEmpty() ? "未命名" : fileName;
    }

    /**
     * 收集结果
     */
    public static class CollectResult {
        private boolean success;
        private String message;
        private Object data;

        private CollectResult(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static CollectResult success(String message, Object data) {
            return new CollectResult(true, message, data);
        }

        public static CollectResult failure(String message) {
            return new CollectResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
    }
}
