package com.videocollect.app.api

import com.videocollect.app.api.models.*
import kotlin.jvm.JvmSuppressWildcards
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
        @Query("sortOrder") sortOrder: String? = null,
        @Query("groupId") groupId: Long? = null
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

    // ===== Video Source CRUD =====

    @GET("/api/source/list")
    suspend fun getSourceList(): ApiResult<List<VideoSource>>

    @GET("/api/source/{id}")
    suspend fun getSource(@Path("id") id: Long): ApiResult<VideoSource>

    @POST("/api/source")
    suspend fun createSource(@Body source: VideoSource): ApiResult<VideoSource>

    @PUT("/api/source/{id}")
    suspend fun updateSource(
        @Path("id") id: Long,
        @Body source: VideoSource
    ): ApiResult<VideoSource>

    @DELETE("/api/source/{id}")
    suspend fun deleteSource(@Path("id") id: Long): ApiResult<Any>

    // ===== Source Export/Import =====

    @GET("/api/source/export")
    suspend fun exportSources(): ApiResult<List<VideoSource>>

    @POST("/api/source/import")
    suspend fun importSources(@Body sources: List<VideoSource>): ApiResult<Any>

    // ===== Source Test =====

    @POST("/api/source/test-search")
    suspend fun testSearch(@Body body: Map<String, String>): ApiResult<List<SearchResultItem>>

    @POST("/api/source/test-parse")
    suspend fun testParse(@Body body: Map<String, String>): ApiResult<List<EpisodeItem>>

    @POST("/api/source/test-search-no-id")
    suspend fun testSearchInline(@Body body: Map<String, Any>): ApiResult<List<SearchResultItem>>

    @POST("/api/source/test-parse-no-id")
    suspend fun testParseInline(@Body body: Map<String, Any>): ApiResult<List<EpisodeItem>>

    // ===== Suggest Regex =====

    @POST("/api/source/suggest-regex")
    suspend fun suggestRegex(@Body body: Map<String, String>): ApiResult<Map<String, Any>>

    // ===== Video Group CRUD =====

    @GET("/api/group/list")
    suspend fun getGroupList(): ApiResult<List<VideoGroup>>

    @GET("/api/group/{id}")
    suspend fun getGroup(@Path("id") id: Long): ApiResult<VideoGroup>

    @GET("/api/group/{id}/videos")
    suspend fun getGroupVideos(
        @Path("id") id: Long,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null
    ): ApiResult<List<VideoRecord>>

    @POST("/api/group")
    suspend fun createGroup(@Body group: VideoGroup): ApiResult<VideoGroup>

    @PUT("/api/group/{id}")
    suspend fun updateGroup(
        @Path("id") id: Long,
        @Body group: VideoGroup
    ): ApiResult<VideoGroup>

    @DELETE("/api/group/{id}")
    suspend fun deleteGroup(
        @Path("id") id: Long,
        @Query("deleteVideos") deleteVideos: Boolean = false
    ): ApiResult<Any>

    // ===== Group Move =====

    @PUT("/api/group/move-video")
    suspend fun moveVideoToGroup(@Body body: Map<String, Long>): ApiResult<Any>

    @PUT("/api/group/batch-move-video")
    suspend fun batchMoveToGroup(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResult<Any>

    @PUT("/api/group/unlink-video/{videoId}")
    suspend fun unlinkVideoFromGroup(@Path("videoId") videoId: Long): ApiResult<Any>

    // ===== Episode Search =====

    @POST("/api/episode/search-all")
    suspend fun searchAllEpisodes(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResult<Map<Long, SourceSearchResult>>

    @POST("/api/episode/search-source")
    suspend fun searchSourceEpisodes(@Body body: Map<String, String>): ApiResult<List<SearchResultItem>>

    @POST("/api/episode/parse")
    suspend fun parseEpisodes(@Body body: Map<String, String>): ApiResult<List<EpisodeItem>>

    @POST("/api/episode/import")
    suspend fun batchImportEpisodes(@Body body: Map<String, @JvmSuppressWildcards Any>): ApiResult<BatchImportResult>

    @GET("/api/episode/next/{videoId}")
    suspend fun checkNextEpisode(@Path("videoId") videoId: Long): ApiResult<NextEpisodeResult>
}
