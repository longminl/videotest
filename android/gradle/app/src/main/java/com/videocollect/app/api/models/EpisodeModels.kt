package com.videocollect.app.api.models

/**
 * 搜索结果项（来自 testSearch 返回的 title+url+cover）
 */
data class SearchResultItem(
    val title: String?,
    val url: String?,
    val cover: String?
)

/**
 * 单源搜索结果（包含源信息 + 结果列表）
 */
data class SourceSearchResult(
    val source: VideoSource?,
    val results: List<SearchResultItem>?
)

/**
 * 剧集解析结果项
 */
data class EpisodeItem(
    val episodeNumber: Int?,
    val url: String?,
    val text: String?
)

/**
 * 批量导入结果
 */
data class BatchImportResult(
    val success: Boolean?,
    val message: String?,
    val successCount: Int?,
    val skippedCount: Int?,
    val failCount: Int?,
    val detail: List<Map<String, Any>>?
) {
    val summary: String
        get() = "成功${successCount ?: 0}，跳过${skippedCount ?: 0}，失败${failCount ?: 0}"
}

/**
 * 下一集检测结果
 */
data class NextEpisodeResult(
    val available: Boolean?,
    val message: String?,
    val nextEpisodeNumber: Int?,
    val nextUrl: String?
)

/**
 * 批量移动到合集请求体
 */
data class BatchMoveRequest(
    val videoIds: List<Long>,
    val groupId: Long
)

/**
 * 批量导入请求体
 */
data class BatchImportRequest(
    val groupId: Long,
    val episodes: List<EpisodeImportItem>
)

data class EpisodeImportItem(
    val episodeNumber: Int,
    val url: String
)
