# src/main/java/com/videocollect/config/

## Responsibility
Spring Boot 配置和 MyBatis 插件。包含静态资源映射和自动时间戳注入。

## Files

### WebConfig.java
- `@Configuration` 实现 `WebMvcConfigurer`
- 将 `/static/**` 请求映射到 `classpath:/static/`，提供 Bootstrap、Plyr、HLS.js 等前端静态资源

### UpdateTimestampInterceptor.java
- `@Intercepts` MyBatis Executor.update 方法
- **作用**：在所有 UPDATE 操作执行前自动向参数 Map 注入 `updatedAt = LocalDateTime.now()`
- **兼容性**：兼容 MySQL（与 `ON UPDATE CURRENT_TIMESTAMP` 不冲突）和 SQLite（无 ON UPDATE 能力）
- **限制**：仅当参数是 `Map` 类型且不包含 `updatedAt` 键时生效

## Integration
- WebConfig：被 Spring Boot 自动加载，为模板页面提供 CSS/JS 资源
- UpdateTimestampInterceptor：由 MyBatis 在每次 UPDATE 时自动调用，需要 Mapper XML 中 SQL 末尾包含 `updated_at = #{updatedAt}`
