package com.videocollect.app.api

data class CacheStatusResponse(
    val finished: Boolean?,
    val total: Int?,
    val cached: Int?,
    val cachedBytes: Long?,
    val cachedMb: Double?,
    val error: String?
) {
    val progress: Float
        get() = if ((total ?: 0) > 0) ((cached ?: 0).toFloat() / (total ?: 1)) else 0f
}

data class CacheStartResponse(
    val started: Boolean?
)

data class CheckProgressResponse(
    val total: Int?,
    val successCount: Int?,
    val failCount: Int?,
    val details: List<String>?
)

data class DeleteBatchRequest(val ids: List<Long>)
