package com.videocollect.model;

import java.time.LocalDateTime;

/**
 * 视频收藏记录实体类
 */
public class VideoRecord {

    private Long id;
    /** 视频标题 */
    private String title;
    /** 来源网页URL */
    private String sourceUrl;
    /** 视频流地址 */
    private String videoUrl;
    /** 状态: 0=未检测, 1=可播放, 2=不可播放, 3=解析失败 */
    private Integer status;
    /** 延迟（毫秒） */
    private Integer latencyMs;
    /** 网页标题 */
    private String pageTitle;
    /** 备注 */
    private String remark;
    /** 所属合集ID */
    private Long groupId;
    /** 集数（如第265集） */
    private Integer episodeNumber;
    /** HLS ts 是否已全部缓存 */
    private Boolean isCached;
    /** 缓存文件总大小（非 DB 字段，运行时计算） */
    private String cacheSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VideoRecord() {}

    public VideoRecord(String title, String sourceUrl, String videoUrl,
                       Integer status, Integer latencyMs, String pageTitle) {
        this.title = title;
        this.sourceUrl = sourceUrl;
        this.videoUrl = videoUrl;
        this.status = status;
        this.latencyMs = latencyMs;
        this.pageTitle = pageTitle;
    }

    public boolean getIsCached() { return isCached != null && isCached; }
    public void setIsCached(Boolean isCached) { this.isCached = isCached; }

    public String getCacheSize() { return cacheSize; }
    public void setCacheSize(String cacheSize) { this.cacheSize = cacheSize; }

    public String getStatusText() {
        if (status == null) return "未检测";
        switch (status) {
            case 0: return "未检测";
            case 1: return "可播放";
            case 2: return "不可播放";
            case 3: return "解析失败";
            default: return "未知";
        }
    }

    public String getLatencyText() {
        if (latencyMs == null) return "-";
        return latencyMs + "ms";
    }

    // ====== Getters & Setters ======

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Integer getEpisodeNumber() { return episodeNumber; }
    public void setEpisodeNumber(Integer episodeNumber) { this.episodeNumber = episodeNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
