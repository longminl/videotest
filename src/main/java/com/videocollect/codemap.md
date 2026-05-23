# src/main/java/com/videocollect/

## Responsibility
后端 Java 源码根包。采用经典分层架构（Controller → Service → DAO），实现视频 URL 提交、解析、检测、存储的完整流水线。

## 分层设计

| 层 | 包 | 职责 |
|---|-----|--------|
| Controller | `controller/` | REST API + Thymeleaf 页面路由 |
| Service | `service/` | 核心业务编排：页面抓取 → 视频地址提取 → 可播放性检测 → 入库 |
| DAO | `dao/` | MyBatis 数据访问接口 |
| Model | `model/` | 数据库实体（无 Lombok，手动 getter/setter） |
| DTO | `dto/` | API 请求/响应对象 |
| Config | `config/` | Spring 配置 + MyBatis 拦截器 |

## Entry Point
- `VideoCollectApplication.java` — `@SpringBootApplication`，启动监听 8080 端口

## 数据流
1. 用户提交 URL → `CollectController.collect()` → `CollectService.collect()`
2. 视频解析流水线：URL → 去重 → 直链？→ Jsoup 抓取 → HTML/JS 解析 → yt-dlp 降级 → 检测可播放性 → 入库
3. HLS 视频通过反向代理 `/proxy/m3u8` 和 `/proxy/ts` 提供本地缓存加速

## Integration
- 消费者：前端 Thymeleaf 页面 + Android App (Retrofit)
- 依赖：Spring Boot 2.7.18, MyBatis 2.3.0, Jsoup, OkHttp, yt-dlp (可选)
- 数据库：MySQL 8.0 (端口 3307)，表 `video_collection`
