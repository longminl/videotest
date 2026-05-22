package com.videocollect.app.api

import com.videocollect.app.api.models.*
import retrofit2.http.*

interface ApiService {

    // ===== Video CRUD =====

    @GET("/api/list")
    suspend fun getVideoList(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("status") status: Int? = null,
        @Query("keyword") keyword: String? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null
    ): ApiResult<PageResult<VideoRecord>>

    @GET("/api/detail/{id}")
    suspend fun getVideoDetail(@Path("id") id: Long): ApiResult<VideoRecord>

    @DELETE("/api/delete/{id}")
    suspend fun deleteVideo(@Path("id") id: Long): ApiResult<Any>

    @HTTP(method = "DELETE", path = "/api/delete-batch", hasBody = true)
    suspend fun deleteBatch(@Body body: DeleteBatchRequest): ApiResult<Any>

    @PUT("/api/remark/{id}")
    suspend fun updateRemark(
        @Path("id") id: Long,
        @Query("remark") remark: String
    ): ApiResult<Any>

    // ===== Collect / Check =====

    @POST("/api/collect")
    suspend fun collectVideo(@Body request: CollectRequest): ApiResult<VideoRecord>

    @PUT("/api/check/{id}")
    suspend fun recheckVideo(@Path("id") id: Long): ApiResult<VideoRecord>

    @PUT("/api/check-all")
    suspend fun recheckAll(): ApiResult<CheckProgressResponse>

    // ===== Cache =====

    @POST("/api/cache/start")
    suspend fun cacheStart(
        @Query("videoUrl") videoUrl: String,
        @Query("title") title: String?,
        @Query("id") id: Long?
    ): ApiResult<CacheStartResponse>

    @GET("/api/cache/status")
    suspend fun cacheStatus(
        @Query("videoUrl") videoUrl: String,
        @Query("title") title: String?,
        @Query("id") id: Long?
    ): ApiResult<CacheStatusResponse>
}
