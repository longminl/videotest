package com.videocollect.app.api.models

data class PageResult<T>(
    val list: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val totalPages: Int
)
