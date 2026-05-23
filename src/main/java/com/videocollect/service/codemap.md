# src/main/java/com/videocollect/service/

## Responsibility
核心业务层，实现视频解析流水线编排。包含页面抓取、视频地址提取、可播放性检测、yt-dlp 降级解析和 HLS 缓存管理。

## 视频解析流水线 (Pipeline)

```
用户提交 URL → CollectService.collect()
  ├── 去重检测（findBySourceUrl）
  ├── 视频直链？→ isDirectVideoUrl()
  │   └── 是：collectDirectVideo() → VideoChecker.check() → DAO insert
  └── 网页解析：collectFromPage()
      ├── 1. PageFetcher.fetch() → Jsoup 抓取 HTML
      ├── 2. VideoParser.parse() → 7 步解析策略
      │   ├── ① <script> JS 变量（var player_xxx = {"url":"..."}）
      │   ├── ② <video src> 标签
      │   ├── ③ <video> <source> 子标签
      │   ├── ④ <iframe src>（递归 MAX_IFRAME_DEPTH=1 层）
      │   ├── ⑤ <a href> 直链
      │   ├── ⑥ embed 标签
      │   └── ⑦ AJAX API（ArtPlayer + api.php 模式）
      ├── 3. 桌面 UA 失败 → 手机 UA 重抓（部分影视站仅手机版嵌入视频）
      ├── 4. Jsoup 失败 → VideoDlpResolver.resolve()（yt-dlp 降级）
      └── 5. 全失败 → 标记 status=3（解析失败）
```

## Files

### CollectService.java
- `@Service` — 业务流程编排入口
- `collect(url)` — 主入口：去重 → 直链判断 → collectDirectVideo / collectFromPage
- `recheck(id)` — 单条重新检测，video_url==source_url 时尝试重新解析页面
- `recheckAll()` — 全量重新检测（同步执行，返回 CheckProgress）
- 内部类 `CollectResult` — success/failure 结果封装
- 常量 `MOBILE_UA` — Android 13 移动端 UA，用于反反爬
- 常量 `VIDEO_EXTENSIONS` — 12 种视频扩展名

### PageFetcher.java
- `@Component` — 基于 Jsoup 的页面抓取器
- `fetch(url)` / `fetch(url, ua)` — 抓取 HTML 返回 Document（支持自定义 UA）
- `fetchRaw(url)` — 获取非 HTML 响应（JSON/m3u8），返回原始字符串
- 超时和 UA 从 `application.yml` 的 `video.fetch.*` 配置注入

### VideoParser.java
- `@Component` — 7 步多策略视频地址提取器
- `parse(doc)` → `parse(doc, depth)` — 从 Jsoup Document 中提取视频链接，返回 List<String>
- **第 0 步**：正则匹配 `<script>` 中 `"url":"https://..."` 和 `"url_next":"..."`（常见于中文影视站）
- **第 1-5 步**：标准 HTML 标签解析（video, source, iframe, a, embed）
- **第 6 步**：二次 JS 扫描，匹配 `"link":"..."` 和裸视频 URL
- **第 7 步**：`fetchVideoUrlViaApi()` — 当使用 ArtPlayer 等 AJAX 播放器时，从 JS 中提取 API 参数（Url, Sign, t）并直接调用
- iframe 递归解析深度限制为 1 层
- `extractPageTitle(doc)` — 提取 `<title>` 标签

### VideoDlpResolver.java
- `@Component` — yt-dlp 命令行降级解析器
- `resolve(url)` — 执行 `yt-dlp --dump-json --no-warnings --no-download --format "best[ext=mp4]/best" <url>`
- 解析 JSON 输出，提取 title、videoUrl、pageUrl、allFormats
- `isAvailable()` — 检查 yt-dlp 可执行文件是否存在
- 异常处理：CreateProcess error=2 → "yt-dlp 未安装"

### VideoChecker.java
- `@Component` — 基于 OkHttp HEAD 请求的视频可播放性检测
- `check(videoUrl)` — HEAD 请求 + 超时测量
- 可播放条件：HTTP 200/206 + Content-Type 以 video/ 或 media/ 开头 或 URL 以视频扩展名结尾
- `sniffIsM3u8()` — 短 GET 嗅探内容前 20 字节是否为 `#EXTM3U` 开头
- 双 OkHttpClient：主 client（5s timeout）+ sniffClient（3s timeout）
- 自动跟随重定向和 SSL 重定向

### HlsCacheService.java
- `@Service` — HLS 片段本地磁盘缓存管理
- **缓存目录结构**：`{cache-dir}/{sanitized_title}/{md5(url)}`（ts）+ `.../m3u8/{md5(url)}`（m3u8）
- **ts 缓存**：`cacheFile()` / `getCachedFile()` — MD5 文件名，按 title 分目录
- **m3u8 缓存**：`cacheM3u8()` / `getCachedM3u8()` + `cacheOriginalM3u8()`（原始未改写副本）
- **并发控制**：`ConcurrentHashMap<String, ReentrantLock>` 双检锁（防止同一 ts 并发下载）
- **预下载**：
  - `prefetchTs()` — 批量提交 ts 到固定线程池（prefetch-threads）
  - `prefetchNextTs()` — 播放时预取后续 N 个 ts（playback-prefetch-count）
- **ts URL 列表缓存**：`cacheTsUrlList()` / `getTsUrlList()` — `ConcurrentHashMap<String, List<String>>`
- **缓存大小**：`getCacheSizeBytes()` / `getCacheSizeText()` — Files.walk 统计，30s TTL 缓存
- **清理**：`clearVideoCache(title)` — 递归删除目录 + 清除内存缓存
- `doDownloadTs()` / `downloadBytes()` — HTTP GET 下载（带 Referer 防盗链）
- `sanitizeTitle()` — 清理 Windows 非法文件名字符，截断 100 字

## Integration
- 被 `CollectController`、`VideoController`、`HlsProxyController` 调用
- 调用 `VideoRecordDao` 进行数据库读写
- 依赖 Jsoup（页面抓取）、OkHttp（视频检测）、yt-dlp（可选降级）
- HlsCacheService 同时被 VideoController（删除清缓存）和 HlsProxyController（代理缓存）使用
