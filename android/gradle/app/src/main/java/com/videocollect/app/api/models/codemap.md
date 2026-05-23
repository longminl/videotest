# android/gradle/app/src/main/java/com/videocollect/app/api/models/

## Responsibility
API 数据模型。与后端 JSON 响应结构一一对应的 Kotlin data class。

## Files

### CollectRequest.kt
- `data class CollectRequest(val url: String)` — 提交收藏请求体

### VideoRecord.kt
- `data class VideoRecord` — 视频记录，与后端 VideoRecord 实体对应
- 字段：id, title, sourceUrl, videoUrl, status, latencyMs, pageTitle, remark, isCached, cacheSize, createdAt, updatedAt
- 计算属性：`statusText`（0→"未检测"等）、`isPlayable`（status==1）、`isM3u8`（URL 包含 .m3u8）、`latencyText`（延迟+"ms"）

### PageResult.kt
- `data class PageResult<T>(val list, val page, val pageSize, val total, val totalPages)` — 分页响应

### ApiResult.kt
- `data class ApiResult<T>(val code, val message, val data)` — 通用 API 响应
- `val isSuccess: Boolean` — `code == 200`
