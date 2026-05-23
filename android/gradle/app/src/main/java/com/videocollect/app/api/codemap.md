# android/gradle/app/src/main/java/com/videocollect/app/api/

## Responsibility
Retrofit HTTP 客户端层。封装后端 REST API 的调用接口和网络配置。

## Files

### ApiService.kt
Retrofit 接口，定义所有后端 API 调用：
- **视频 CRUD**：`getVideoList()`（分页+筛选+排序）、`getVideoDetail()`、`deleteVideo()`、`deleteBatch()`、`updateRemark()`
- **收集/检测**：`collectVideo()`、`recheckVideo()`、`recheckAll()`
- **缓存操作**：`cacheStart()`、`cacheStatus()`
- 全部为 `suspend` 协程方法

### RetrofitClient.kt
- 单例对象（`object RetrofitClient`）
- 使用 Gson + OkHttp（含 logging 拦截器）构建 Retrofit 实例
- `updateBaseUrl(host, port)` — 动态切换服务器地址，重置 Retrofit 实例
- `getBaseUrl()` — 获取当前基础 URL
- `getApiService()` — 懒加载 ApiService 实例
- `testConnection(host, port, callback)` — 异步测试连接（OkHttp enqueue）

### Models.kt
- `CacheStatusResponse` — 缓存状态响应（finished, total, cached, cachedBytes, cachedMb, error），含 `progress` 计算属性
- `CacheStartResponse` — 缓存启动响应（started）
- `CheckProgressResponse` — 批量检测进度
- `DeleteBatchRequest` — 批量删除请求体（ids）

### models/CacheStatusResponse.kt, etc.
各 API 数据模型，详见 models/ codemap。
