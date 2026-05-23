package com.videocollect.model;

import java.time.LocalDateTime;

/**
 * 视频合集实体类（一级分类，如"狂飙"）
 */
public class VideoGroup {

    private Long id;
    /** 合集名称 */
    private String name;
    /** 关联视频源ID */
    private Long sourceId;
    /** 剧集主页URL */
    private String sourceSeriesUrl;
    /** 已知总集数 */
    private Integer totalEpisodes;
    /** 描述 */
    private String description;
    /** 排序 */
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 视频数量（非DB字段，运行时计算） */
    private Integer videoCount;
    /** 视频源名称（非DB字段，关联查询用） */
    private String sourceName;

    public VideoGroup() {}

    // ====== Getters & Setters ======

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }

    public String getSourceSeriesUrl() { return sourceSeriesUrl; }
    public void setSourceSeriesUrl(String sourceSeriesUrl) { this.sourceSeriesUrl = sourceSeriesUrl; }

    public Integer getTotalEpisodes() { return totalEpisodes; }
    public void setTotalEpisodes(Integer totalEpisodes) { this.totalEpisodes = totalEpisodes; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getVideoCount() { return videoCount; }
    public void setVideoCount(Integer videoCount) { this.videoCount = videoCount; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
}
