# src/main/java/com/videocollect/dto/

## Responsibility
数据传输对象，用于 Controller 与客户端之间的 JSON 序列化/反序列化。

## Files

### ApiResult.java
- 泛型响应封装 `ApiResult<T>`
- 工厂方法：
  - `success(data)` → code=200, message="操作成功"
  - `success(message, data)` → code=200
  - `error(message)` → code=500
  - `error(code, message)` → 自定义 code

### PageResult.java
- 分页响应封装 `PageResult<T>`
- 字段：`list`（数据行）、`page`（当前页）、`pageSize`（每页大小）、`total`（总数）、`totalPages`（总页数）
- `totalPages` 在构造时通过 `Math.ceil(total / pageSize)` 计算

### CollectRequest.java
- 提交收藏请求体
- 字段：`url`（网页链接或视频直链）

### CheckProgress.java
- 批量检测进度，原设计用于 SSE 推送
- 字段：`total`、`completed`、`successCount`、`failCount`、`currentUrl`、`finished`
- `getPercent()` — 计算完成百分比（`completed * 100 / total`）
