# Repository Atlas: videotest

## Project Responsibility
Spring Boot 2.7.18 + MyBatis 视频收藏工具。用户提交网页链接或视频直链，系统自动解析视频地址、检测可播放性并存入 MySQL。包含 Android 客户端（Kotlin + Jetpack Compose）。

## System Entry Points
- `src/main/java/com/videocollect/VideoCollectApplication.java` — Spring Boot 启动入口（监听 8080）
- `pom.xml` — Maven 构建（Java 1.8, Spring Boot 2.7.18, MyBatis 2.3.0）
- `sql/init.sql` — 数据库初始化脚本（MySQL 8.0, 端口 3307）
- `android/` — 独立 Android 项目（Kotlin + Compose + ExoPlayer）
- `yt-dlp.exe` — 可选：用于解析 B站/YouTube 等 JS 渲染站点

## Integration Points
- **数据库**：MySQL 8.0 端口 3307（`application-mysql.yml`），也可切换 SQLite（`application-sqlite.yml`）
- **前端**：Thymeleaf 模板（浏览器） + Retrofit 客户端（Android）
- **外部工具**：yt-dlp（可选视频解析）、Jsoup（HTML 抓取）、OkHttp（视频检测）

## Repository Directory Map

| Directory | Responsibility Summary | Detailed Map |
|-----------|----------------------|--------------|
| `src/main/java/com/videocollect/` | 后端 Java 源码根包 | [View Map](src/main/java/com/videocollect/codemap.md) |
| `src/main/java/com/videocollect/config/` | Spring 配置 + MyBatis 拦截器 | [View Map](src/main/java/com/videocollect/config/codemap.md) |
| `src/main/java/com/videocollect/controller/` | REST API + 页面路由 + HLS 代理 | [View Map](src/main/java/com/videocollect/controller/codemap.md) |
| `src/main/java/com/videocollect/service/` | 核心业务层：视频解析流水线 | [View Map](src/main/java/com/videocollect/service/codemap.md) |
| `src/main/java/com/videocollect/dao/` | MyBatis 数据访问 | [View Map](src/main/java/com/videocollect/dao/codemap.md) |
| `src/main/java/com/videocollect/model/` | 数据库实体 | [View Map](src/main/java/com/videocollect/model/codemap.md) |
| `src/main/java/com/videocollect/dto/` | API 请求/响应 DTO | [View Map](src/main/java/com/videocollect/dto/codemap.md) |
| `src/main/resources/` | 应用配置（YAML） | [View Map](src/main/resources/codemap.md) |
| `src/main/resources/mapper/` | MyBatis SQL 映射 | [View Map](src/main/resources/mapper/codemap.md) |
| `src/main/resources/templates/` | Thymeleaf 模板页面 | [View Map](src/main/resources/templates/codemap.md) |
| `sql/` | 数据库初始化脚本 | [View Map](sql/codemap.md) |
| `android/.../app/` | Android 客户端入口 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/codemap.md) |
| `android/.../app/api/` | Android Retrofit API 层 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/api/codemap.md) |
| `android/.../app/api/models/` | Android API 数据模型 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/api/models/codemap.md) |
| `android/.../app/data/` | Android DataStore 持久化 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/data/codemap.md) |
| `android/.../app/ui/` | Android UI 层总览 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/codemap.md) |
| `android/.../app/ui/list/` | 视频列表页 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/list/codemap.md) |
| `android/.../app/ui/detail/` | 视频详情页 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/detail/codemap.md) |
| `android/.../app/ui/player/` | 全屏 ExoPlayer 播放器 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/player/codemap.md) |
| `android/.../app/ui/add/` | 添加视频页 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/add/codemap.md) |
| `android/.../app/ui/settings/` | 设置页 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/settings/codemap.md) |
| `android/.../app/ui/server/` | 首次启动配置向导 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/server/codemap.md) |
| `android/.../app/ui/theme/` | Material3 主题+颜色 | [View Map](android/gradle/app/src/main/java/com/videocollect/app/ui/theme/codemap.md) |
| `android/.../app/src/main/res/` | Android 资源文件 | [View Map](android/gradle/app/src/main/res/codemap.md) |
