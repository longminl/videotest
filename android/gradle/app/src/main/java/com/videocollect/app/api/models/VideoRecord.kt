package com.videocollect.app.api.models

data class VideoRecord(
    val id: Long?,
    val title: String?,
    val sourceUrl: String?,
    val videoUrl: String?,
    val status: Int?,
    val latencyMs: Int?,
    val pageTitle: String?,
    val remark: String?,
    val groupId: Long?,
    val groupName: String?,
    val episodeNumber: Int?,
    val isCached: Boolean?,
    val cacheSize: String?,
    val createdAt: String?,
    val updatedAt: String?
) {
    val statusText: String
        get() = when (status) {
            0 -> "未检测"
            1 -> "可播放"
            2 -> "不可播放"
            3 -> "解析失败"
            else -> "未知"
        }

    val isPlayable: Boolean get() = status == 1

    val isM3u8: Boolean get() = videoUrl?.contains(".m3u8", ignoreCase = true) ?: false

    val latencyText: String
        get() = if (latencyMs != null) "${latencyMs}ms" else "-"

    val episodeText: String
        get() = if (episodeNumber != null) "第${episodeNumber}集" else "-"

    val groupDisplay: String
        get() = groupName ?: (if (groupId != null) "合集#$groupId" else "未分组")
}
