package com.videocollect.app.api.models

data class ApiResult<T>(
    val code: Int,
    val message: String,
    val data: T?
) {
    val isSuccess: Boolean get() = code == 200
}
