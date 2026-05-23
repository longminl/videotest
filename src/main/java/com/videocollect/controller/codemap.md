# src/main/java/com/videocollect/controller/

## Responsibility
REST API 控制器 + Thymeleaf 页面路由。包含三个 Controller，分别处理视频收集、页面/数据查询和 HLS 代理缓存。

## Files

### CollectController.java
- `@RestController` `/api/`
- **POST `/api/collect`** — 提交 URL，触发解析流水线（JsonBody: `{url: string}`）
- **PUT `/api/check/{id}`** — 单条重新检测
- **PUT `/api/check-all`** — 一键全量重新检测（同步执行，阻塞等待全部完成）
- 内部调用 `CollectService` 编排流水线

### VideoController.java
- `@Controller` `/` — 混合页面 + REST 数据接口
- **页面路由**：
  - `GET /` → `index`（收藏列表页）
  - `GET /detail/{id}` → `detail`（详情页，含 Plyr 播放器）
- **REST 数据接口**（`@ResponseBody`）：
  - `GET /api/list` — 分页查询，支持 `page`、`pageSize`、`status`、`keyword`、`sortBy`、`sortOrder`
  - `GET /api/detail/{id}` — 单条详情 JSON（含缓存大小）
  - `DELETE /api/delete/{id}` — 单条删除（先清本地缓存再删 DB）
  - `DELETE /api/delete-batch` — 批量删除（JsonBody: `{ids: [long]}`）
  - `PUT /api/remark/{id}` — 更新备注（`?remark=xxx`）
- 注入 `HlsCacheService` 用于删除时清理缓存

### HlsProxyController.java
- `@RestController` — HLS 代理和缓存管理
- **代理端点**：
  - `GET /proxy/m3u8` — 代理 m3u8 索引文件，改写 ts URL 为本地代理地址，触发后台 ts 预下载
  - `GET /proxy/ts` — 代理 ts 片段（带磁盘缓存 + 双检锁并发控制）
  - `GET /proxy/key` — 代理 AES-128 加密密钥
- **缓存管理**：
  - `POST /api/cache/start` — 异步启动缓存（后台线程拉取 m3u8 + 预下载 ts）
  - `POST /api/cache/retry` — 重试缓存（清空 m3u8 缓存后同步执行）
  - `GET /api/cache/status` — 查询缓存进度（递归解析 m3u8，统计 ts 总数和已缓存数）
  - `GET /api/cache/clear-m3u8` — 清除 m3u8 缓存
- **关键实现**：
  - OkHttp 设置 `maxRequestsPerHost=20`，避免单域名下并发请求阻塞
  - `Dispatcher` 独立配置，不与系统默认共享
  - 双检锁（Double-checked locking）避免同一 ts 片段重复下载
  - `resolveAbsoluteUrl()` 支持绝对、协议相对 `//`、绝对路径 `/`、相对路径四种格式
  - `rewriteExtXKeyUri()` 改写 AES-128 密钥 URI 为代理地址

## Integration
- 调用 `CollectService` 进行视频解析和检测
- 调用 `VideoRecordDao` 进行数据存取
- 调用 `HlsCacheService` 进行缓存读写
- 被前端（浏览器 Thymeleaf + Android Retrofit）消费
