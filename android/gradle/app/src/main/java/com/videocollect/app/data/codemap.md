# android/gradle/app/src/main/java/com/videocollect/app/data/

## Responsibility
本地数据持久化层。使用 Jetpack DataStore Preferences 存储服务器配置。

## SettingsRepository.kt
- 基于 `Context` 扩展属性 `dataStore`（name="settings"）
- **Keys**：
  - `KEY_HOST` = "server_host"（默认 "192.168.1.100"）
  - `KEY_PORT` = "server_port"（默认 8080）
  - `KEY_CONFIGURED` = "server_configured"
- **Flows**：`serverHost`、`serverPort`、`isConfigured`
- `suspend fun saveServerConfig(host, port)` — 保存并标记已配置
