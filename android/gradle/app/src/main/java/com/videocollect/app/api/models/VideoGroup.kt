package com.videocollect.app.api.models

/**
 * 视频合集数据模型
 */
data class VideoGroup(
    val id: Long?,
    val name: String?,
    val sourceId: Long?,
    val sourceSeriesUrl: String?,
    val totalEpisodes: Int?,
    val description: String?,
    val sortOrder: Int?,
    val videoCount: Int?,
    val sourceName: String?,
    val createdAt: String?,
    val updatedAt: String?
)
