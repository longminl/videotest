# video-collect 仓库指南

## 项目概述

Spring Boot 2.7.18 + MyBatis 视频收藏工具。用户提交网页链接或视频直链，系统自动解析视频地址、检测可播放性并存入 MySQL。

## 快速命令

```bash
mvn clean package                  # 打包（无测试，无需 -DskipTests）
java -jar target/video-collect-1.0.0.jar  # 启动，监听 8080
```

## 前置条件

- **Java 1.8**（pom.xml 中 `<java.version>1.8</java.version>`）
- **MySQL 8.0**，端口 **3307**（非默认 3306），用户名 `root` 密码 `123456`
- 首次部署执行 `sql/init.sql` 创建库表
- **yt-dlp**（可选）：下载 `yt-dlp.exe` 放到项目根目录，用于解析 B站/YouTube 等 JS 渲染站点

## 项目结构

```
videotest/
├── pom.xml                           # Maven + Spring Boot 2.7.18 + MyBatis 2.3.0
├── sql/init.sql                      # 建库建表脚本
├── debug_parser.py                   # Python 调试脚本（独立工具，非构建部分）
├── yt-dlp.exe                        # yt-dlp 可执行文件（可选）
├── src/main/
│   ├── java/com/videocollect/
│   │   ├── VideoCollectApplication.java   # 启动入口
│   │   ├── config/WebConfig.java          # 静态资源配置
│   │   ├── controller/
│   │   │   ├── CollectController.java     # REST: POST /api/collect, PUT /api/check
│   │   │   └── VideoController.java       # 页面+API: GET /, /api/list, /api/detail
│   │   ├── service/
│   │   │   ├── CollectService.java        # 核心编排：URL→解析→检测→入库
│   │   │   ├── PageFetcher.java           # Jsoup 抓取页面
│   │   │   ├── VideoParser.java           # HTML/JS 视频地址提取
│   │   │   ├── VideoDlpResolver.java      # yt-dlp 降级解析
│   │   │   └── VideoChecker.java          # OkHttp HEAD 检测可播放性+延迟
│   │   ├── dao/VideoRecordDao.java        # MyBatis Mapper
│   │   ├── model/VideoRecord.java         # 实体
│   │   └── dto/                           # ApiResult, PageResult, CollectRequest, CheckProgress
│   └── resources/
│       ├── application.yml                # 配置（数据库、视频检测、yt-dlp）
│       ├── mapper/VideoRecordMapper.xml   # SQL 映射
│       └── templates/                     # Thymeleaf 页面
└── target/                               # 构建产物（已存在）
```

## 架构要点

- **视频解析流水线**：`提交URL → 去重检测 → 视频直链？→ 是：直链入库 → 否：Jsoup抓取页面 → Jsoup解析HTML标签/JS变量 → 成功？→ 是：检测入库 → 否：yt-dlp降级 → 全部失败标记 status=3`
- **状态码**：`0=未检测` `1=可播放` `2=不可播放` `3=解析失败`
- **Jsoup 解析器**：支持 `<video>` 标签、`<iframe>`、`<a>` 直链以及中文影视站常见的 `var player_xxxx = {"url":"https://...m3u8"}` 模式
- `recheckAll()` 当前**同步执行**（可能阻塞），非异步

## 数据库

- 库名 `video_collect`，表名 `video_collection`
- 字段：`title, source_url, video_url, status, latency_ms, page_title, remark`
- MyBatis mapper-locations: `classpath:mapper/*.xml`
- `map-underscore-to-camel-case: true`

## 关键约定

- 所有注释**用中文写**（见 `AI.md` 约束）
- 代码中无 Lombok 注解（手动 getter/setter），添加新实体时保持一致
- MySQL 端口 **3307**（非标准），修改需同时更新 `application.yml`
- `target/` 目录已检入版本控制

---

## 会话摘要 (2026-05-21)

### 已完成
1. **缓存按钮卡死修复** — OkHttp Dispatcher maxRequestsPerHost(20) 解决单域名并发竞争；pollCacheStatus total===0 显示"等待中…"而非静默退出；startCache 成功显示"缓存中…"；20s 超时自动重试（/api/cache/retry）。
2. **前端移动端性能优化** — 移动端检测（UA + 宽度），缓存按钮 fire-and-forget 无轮询，CSS 禁用 backdrop-filter/shimer/过渡动画，touch-action:manipulation。
3. **表格列重排** — # / 标题 / 状态 / 延迟 / 缓存 / 操作 / 来源 / 收录时间；sticky 操作列仅限移动端(≤768px)；修复 400px 断点隐藏错列 bug。
4. **蓝白色调配色改造** — index.html 和 detail.html 均由暗黑主题改为蓝白主题（`#f0f5ff` 背景，`#ffffff` 卡片，`#3b82f6` 主色）；Plyr 播放器保持暗色；更新所有 inline 颜色、JS 图标颜色、Toast 样式、滤镜圆点颜色。
5. **修复蓝白迁移中误删的 HTML 结构** — 恢复表格 loadingRow 骨架屏、分页组件、进度弹窗（之前被合并到 table 内部导致页面结构损坏→网络错误）。
6. **删除视频同步清除本地缓存** — HlsCacheService.clearVideoCache() 删除 `{cache-dir}/{title}/`（含 ts + m3u8 子目录）+ 清除 tsUrlListCache 内存条目；VideoController.delete() 在删 DB 前调用。

### 阻塞
- yt-dlp.exe 下载受限（GitHub 国内慢，SourceForge 文件截断），需用户自行获取。

### 关键文件
- `HlsProxyController.java` — maxRequestsPerHost=20 + /api/cache/retry
- `HlsCacheService.java` — +clearVideoCache(title)
- `VideoController.java` — delete() 注入 HlsCacheService，删除时清缓存
- `index.html` — 蓝白色调 + 列重排 + sticky 操作列 + 移动端跳过轮询 + 20s 自动重试
- `detail.html` — 蓝白色调 + 移动端跳过缓存轮询 + 修复 downloadVideo 语法错误
