# video-collect 操作说明

基于 Spring Boot 2.7 + MyBatis 的自建视频收藏工具。输入网页链接或视频直链，自动解析视频地址、检测可播放性，支持 HLS 代理缓存加速和多设备局域网共享。

---

## 1. 快速开始

### 前置条件

| 软件 | 版本 | 说明 |
|------|------|------|
| Java | 1.8 | pom.xml 中 `<java.version>1.8</java.version>` |
| Maven | 3.6+ | 打包用 |
| MySQL | 5.5,5.7 | 端口 **3307**（非默认 3306） |

> 所有前端静态资源（Bootstrap、Bootstrap Icons、Plyr、hls.js）已内置在 `static/lib/` 中，**无需互联网连接**即可正常渲染页面和播放视频。

### 初始化数据库

```sql
source sql/init.sql
```

自动创建库 `video_collect` 和表 `video_collection`（含 `is_cached` 字段）。

### 打包 & 启动

```bash
mvn clean package -DskipTests
java -jar target/video-collect-1.0.0.jar
```

打开浏览器访问 `http://localhost:8080`。

---

## 2. 配置说明

`application.yml` 核心配置：

| 配置 | 说明 | 默认值 |
|------|------|--------|
| `spring.datasource.url` | MySQL 连接地址 | `jdbc:mysql://127.0.0.1:3307/video_collect?...` |
| `spring.datasource.username` | 数据库用户名 | `root` |
| `spring.datasource.password` | 数据库密码 | `123456` |
| `video.check.connect-timeout` | 视频检测连接超时 | `5000`（毫秒） |
| `video.check.read-timeout` | 视频检测读取超时 | `5000`（毫秒） |
| `video.fetch.timeout` | 页面抓取超时 | `8000`（毫秒） |
| `video.hls.cache-dir` | HLS 缓存根目录 | `./hls-cache/` |
| `video.hls.prefetch-threads` | 后台预下载 ts 的并发线程数 | `5` |
| `video.hls.playback-prefetch-count` | 播放时额外预取 ts 个数（边播边缓存后续 N 段） | `10` |
| `video.ytdlp.path` | yt-dlp 可执行文件路径 | `yt-dlp` |
| `video.ytdlp.timeout` | yt-dlp 执行超时 | `30`（秒） |

---

## 3. 使用指南

### 收藏视频

1. 首页输入框粘贴网页链接（如影视站详情页）或视频直链（.mp4 / .m3u8）
2. 点击保存，系统自动抓取页面 → 解析视频地址 → 检测可播放性
3. 解析成功 status 变为"可播放"；失败变为"解析失败"

### 首页列表

- **筛选栏**：按状态（全部/可播放/不可播放/未检测/解析失败）过滤，支持关键词搜索
- **缓存列**：显示每个视频的缓存状态
  - 非 m3u8 视频显示灰色"—"
  - m3u8 视频：点击"缓存"按钮启动全量预下载 → 进度条 5 秒轮询 → 全部完成显示绿勾"已缓存"
- **播放按钮**：可播放视频显示绿色"播放"按钮 → 点击弹出模态框，Plyr 播放器播放（m3u8 走 hls.js 代理，mp4 直链）
- **批量检测**：一键重新检测所有异常记录
- **分页**：每页 20 条

### 详情页

点击列表项进入详情页：

- **播放器**：顶部 Plyr 播放器
  - m3u8 视频：通过 `/proxy/m3u8` 代理播放，支持倍速 **0.5x ~ 4x**（齿轮菜单中切换）
  - mp4/ts 直链：直接播放
  - hls.js CDN 超时自动降级到原生播放器
- **缓存状态行**：仅 m3u8 视频显示，每 3 秒自动刷新进度、大小
- **下载按钮**：
  - m3u8 视频：触发后台缓存（`POST /api/cache/start`）+ 新标签打开代理 m3u8 下载
  - mp4/ts 直链：新标签打开视频（浏览器直接播放/下载）
- **重新检测**：重新解析并检测可播放性（同时复位 is_cached）
- **编辑备注**：输入后点击"保存"

---

## 4. 缓存系统

### 工作原理

两个缓存入口：

| 入口 | 触发方式 | 行为 |
|------|---------|------|
| **播放时** | 浏览器播放到某个 ts | `/proxy/ts` 从源站下载当前 ts → 写入磁盘缓存 → 自动预取后续 N 个（`playback-prefetch-count`） |
| **主动缓存** | 首页点击"缓存" / 详情页点击"下载" | `POST /api/cache/start` → 后台 `collectTsUrls` 递归收集所有 ts（含子 m3u8）→ 5 线程并发全量预下载 |

### 目录结构

```
hls-cache/
├── 视频标题1/                  ← 按数据库中的 title 字段分目录
│   ├── d41d8cd9...            ← ts 文件（文件名 = ts URL 的 MD5）
│   ├── e99a18c4...
│   └── m3u8/                  ← m3u8 索引缓存
│       ├── abc123...           ← 改写后的 m3u8（含代理 URL）
│       └── abc123..._original  ← 原始 m3u8（供 collectTsUrls 解析原始链接）
├── 视频标题2/
│   └── ...
└── （title 为空的视频存根目录）
```

### 速度对比

| 场景 | 终端速度 | 说明 |
|------|---------|------|
| 首次播放（未缓存 ts） | 取决于源站带宽 | 本机逐段下载到硬盘 |
| 后续播放（同机或局域网） | **局域网速度 ≈ 100MB/s+** | 直接从磁盘缓存读取，不需出网 |
| 跨视频同 ts 文件 | 也命中缓存 | MD5 去重，自动共享 |

### 缓存状态

- 数据库 `is_cached` 字段（`TINYINT(1)`）标记是否全部缓存
- 首页缓存列：绿勾"已缓存" / 进度条 / "缓存"按钮
- 详情页缓存行：进度百分比 + 大小（MB），自动更新
- 全部缓存完成后，`/api/cache/status` 自动更新 `is_cached=1`

### 清缓存

直接删除 `hls-cache/` 目录即可，下次播放自动重建。

---

## 5. 局域网访问

其他电脑（手机、电视等）通过局域网访问本机服务，同样享受缓存加速：

```bash
# 1. 在本机查看局域网 IP
ipconfig
# 无线局域网适配器 WiFi → IPv4 地址 → 192.168.x.x

# 2. 其他设备浏览器访问
http://192.168.x.x:8080
```

若无法访问，检查 Windows 防火墙是否放行了 8080 端口：

```powershell
# 添加入站规则（管理员 PowerShell）
New-NetFirewallRule -DisplayName "video-collect" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

---

## 6. API 参考

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/collect` | 提交 URL 收藏视频（iframe 递归 + AJAX API + yt-dlp 降级） |
| `GET` | `/api/list` | 分页列表 |
| `GET` | `/api/detail/{id}` | 详情 |
| `PUT` | `/api/check/{id}` | 重新单条检测 |
| `PUT` | `/api/check-all` | 批量重新检测 |
| `PUT` | `/api/remark/{id}` | 更新备注 |
| `DELETE` | `/api/delete/{id}` | 删除记录 |
| `GET` | `/proxy/m3u8` | 代理 m3u8（含改写 + 缓存） |
| `GET` | `/proxy/ts` | 代理 ts（含磁盘缓存 + 播放预取） |
| `GET` | `/proxy/key` | 代理 AES-128 加密密钥文件 |
| `POST` | `/api/cache/start` | 启动全量缓存（异步） |
| `GET` | `/api/cache/status` | 查询缓存进度 |
| `GET` | `/api/cache/clear-m3u8` | 清除 m3u8 缓存 |

---

## 7. 注意事项

### 缓存机制说明

- **首次使用"缓存"前，建议先播放一遍视频**：播放时 `/proxy/m3u8` 会自动从源站拉取并缓存原始 m3u8 结构。这样后续点击"缓存"时，`collectTsUrls` 可以从本地缓存直接解析所有 ts URL（含子 m3u8），避免再去请求源站导致 503。

- **`playback-prefetch-count` 不宜过大**：建议设为 10~50。播放时每播一个 ts 会尝试预取后续 N 个，设得太大会膨胀线程池队列，实际收益不大。

- **已缓存的 ts 文件不影响播放**：即使删除或重命名了 `hls-cache/` 下的文件，播放器会通过 `/proxy/ts` 自动从源站重新下载并缓存，不受影响。

- **下载按钮可能被浏览器弹窗拦截**：详情页"下载"按钮会调用 `window.open` 打开代理 m3u8 或视频直链。如果浏览器阻断了弹窗，允许当前站点弹窗即可。

### 源站防盗链

某些源站会检查 Referer 或限制请求频率，可能导致：

1. **播放正常但"缓存"按钮 503**：播放时 `/proxy/m3u8` 已缓存原始 m3u8，但 `collectTsUrls` 递归时需要拉取子 m3u8 文件，源站可能拒绝。解决方案：先播完一遍视频（播到末尾），让所有子 m3u8 都被缓存，再点"缓存"。

2. **ts 下载失败**：`/proxy/ts` 从源站拉取 ts 时设置了 User-Agent 和源站域名 Referer。如仍被拒绝，需联系源站或更换视频源。

### 数据库

- 首次部署执行 `sql/init.sql` 创建库表
- 已有数据的升级：手动执行 `ALTER TABLE video_collection ADD is_cached TINYINT(1) DEFAULT 0;`
- MySQL 端口为 **3307**（非标准），修改需同步更新 `application.yml`
- 所有注释用中文写（代码规范）

### 常见问题

#### 数据库连接失败
```
java.sql.SQLException: Access denied for user 'root'@'localhost'
```
- 检查 MySQL 是否在 **3307** 端口运行（不是 3306）
- 检查用户名密码是否与 `application.yml` 一致
- 首次使用先执行 `source sql/init.sql`

#### 视频解析失败 / status=3
- 检查本机是否能访问目标网站
- 某些 JS 渲染站点需要 yt-dlp 才能解析
- 下载 `yt-dlp.exe` 放到项目根目录或在配置中指定路径

#### iframe 嵌套站点播放在线正常，提交后 status=3
部分影视站将视频内嵌在 iframe 中，且 iframe 页面通过 JS 动态请求 API 获取视频地址（如 ArtPlayer + `api.php` 模式）。系统已支持三层降级解析：

1. **iframe 递归解析**：自动抓取 iframe 页面内容，提取其中的静态视频地址
2. **AJAX API 提取**：检测 ArtPlayer 等播放器的 `Url`/`Sign`/`From` 变量，直接调用后端 `api.php` 获取真实视频地址
3. **yt-dlp 降级**：前两者均失败后，自动调用 yt-dlp 通用提取器

如果该站点仍解析失败：
- 确认 `yt-dlp.exe` 在项目根目录且可执行（运行 `yt-dlp --version` 测试）
- 尝试提交 **手机版页面 URL**（部分站点仅移动端页面包含视频地址）
- 可以直接提交 **iframe 的 src 地址**（浏览器开发者工具 → 找到 iframe 标签 → 复制 src 属性值）

#### 重新检测不生效（已有记录解析失败）
之前解析失败的老记录（status=3），直接点"重新检测"只会检查旧地址。新版本已修复：**重检会重新解析页面**，自动更新 `video_url` 为正确地址。对已存在的失败记录直接点"重新检测"即可。如仍未解决，可删除记录后重新提交。**重启服务后重检才生效**（需 `mvn clean package` 并重启）。

#### 播放器不显示 / 白屏
- 按 `F12` 打开控制台，检查资源是否加载成功
- 所有静态资源已本地化（`static/lib/`），不依赖外网 CDN
- 如仍白屏，确认 `static/lib/` 目录完整存在

#### ts 缓存不生效
- 检查 `hls-cache/` 目录是否存在且有写入权限
- 检查启动日志中是否有 `HLS 缓存目录: ...` 的日志
- 删除 `hls-cache/` 后重启，让程序重新创建

#### AES-128 加密的 m3u8 播放黑屏
部分 m3u8 使用 AES-128 加密（包含 `#EXT-X-KEY` 标签），密钥文件通常以相对路径（如 `enc.key`）引用的。系统会自动将密钥 URI 改写为 `/proxy/key?url=...`，通过服务端代理拉取密钥，解决浏览器端相对路径解析失败的问题。

无需额外配置，自动生效。

#### 改了代码如何重启
```bash
mvn clean package -DskipTests
# 停止旧进程（Ctrl+C），然后
java -jar target/video-collect-1.0.0.jar
```
