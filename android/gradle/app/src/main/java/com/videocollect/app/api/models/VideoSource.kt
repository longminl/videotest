package com.videocollect.app.api.models

/**
 * 视频源配置数据模型
 */
data class VideoSource(
    val id: Long?,
    val name: String?,
    val homeUrl: String?,
    val searchUrl: String?,
    val searchDataPath: String?,
    val searchTitleField: String?,
    val searchUrlField: String?,
    val searchCoverField: String?,
    val episodePattern: String?,
    val episodeGroup: Int?,
    val episodeSelector: String?,
    val encoding: String?,
    val sortOrder: Int?,
    val videoCount: Int?,
    val createdAt: String?,
    val updatedAt: String?
)
