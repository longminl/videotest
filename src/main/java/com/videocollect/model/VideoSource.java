package com.videocollect.model;

import java.time.LocalDateTime;

/**
 * 视频源配置实体类
 */
public class VideoSource {

    private Long id;
    /** 视频源名称 */
    private String name;
    /** 网站首页地址 */
    private String homeUrl;
    /** 搜索API地址，用 {keyword} 占位 */
    private String searchUrl;
    /** JSON数据路径 */
    private String searchDataPath;
    /** 结果标题字段 */
    private String searchTitleField;
    /** 结果链接字段 */
    private String searchUrlField;
    /** 结果封面字段（可选） */
    private String searchCoverField;
    /** 从URL提取集数的正则 */
    private String episodePattern;
    /** 正则捕获组索引 */
    private Integer episodeGroup;
    /** 集数链接CSS选择器（可选） */
    private String episodeSelector;
    /** 页面编码 */
    private String encoding;
    /** 排序 */
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 视频数量（非DB字段，运行时计算） */
    private Integer videoCount;

    public VideoSource() {}

    // ====== Getters & Setters ======

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHomeUrl() { return homeUrl; }
    public void setHomeUrl(String homeUrl) { this.homeUrl = homeUrl; }

    public String getSearchUrl() { return searchUrl; }
    public void setSearchUrl(String searchUrl) { this.searchUrl = searchUrl; }

    public String getSearchDataPath() { return searchDataPath; }
    public void setSearchDataPath(String searchDataPath) { this.searchDataPath = searchDataPath; }

    public String getSearchTitleField() { return searchTitleField; }
    public void setSearchTitleField(String searchTitleField) { this.searchTitleField = searchTitleField; }

    public String getSearchUrlField() { return searchUrlField; }
    public void setSearchUrlField(String searchUrlField) { this.searchUrlField = searchUrlField; }

    public String getSearchCoverField() { return searchCoverField; }
    public void setSearchCoverField(String searchCoverField) { this.searchCoverField = searchCoverField; }

    public String getEpisodePattern() { return episodePattern; }
    public void setEpisodePattern(String episodePattern) { this.episodePattern = episodePattern; }

    public Integer getEpisodeGroup() { return episodeGroup; }
    public void setEpisodeGroup(Integer episodeGroup) { this.episodeGroup = episodeGroup; }

    public String getEpisodeSelector() { return episodeSelector; }
    public void setEpisodeSelector(String episodeSelector) { this.episodeSelector = episodeSelector; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getVideoCount() { return videoCount; }
    public void setVideoCount(Integer videoCount) { this.videoCount = videoCount; }
}
