# src/main/resources/

## Responsibility
Spring Boot 应用配置文件。管理数据源、MyBatis、视频检测、HLS 缓存和 yt-dlp 等全部配置。

## Files

### application.yml（主配置）
- **server.port**: 8080
- **spring.profiles.active**: mysql（默认使用 MySQL）
- **spring.thymeleaf**: cache=false, suffix=.html, prefix=classpath:/templates/
- **MyBatis**: mapper-locations=classpath:mapper/*.xml, type-aliases=com.videocollect.model, map-underscore-to-camel-case=true, log-impl=StdOutImpl
- **video.check**: connect-timeout=5000, read-timeout=5000
- **video.fetch**: user-agent=Chrome 125 UA, timeout=8000
- **video.ytdlp**: path=yt-dlp, timeout=30
- **video.hls**: cache-dir=${user.dir}/hls-cache, prefetch-threads=5, playback-prefetch-count=10

### application-mysql.yml（MySQL 配置）
- JDBC URL: `jdbc:mysql://127.0.0.1:3307/video_collect`（端口 3307，非标准）
- 用户名/密码: root / 123456
- 驱动: com.mysql.cj.jdbc.Driver

### application-sqlite.yml（SQLite 配置）
- JDBC URL: `jdbc:sqlite:${user.dir}/video-collect.db`
- 驱动: org.sqlite.JDBC
- HikariCP: maximum-pool-size=1
- 自动初始化: schema-locations=classpath:sql/init-sqlite.sql
