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
