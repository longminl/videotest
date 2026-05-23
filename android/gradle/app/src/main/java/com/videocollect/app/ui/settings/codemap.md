# android/gradle/app/src/main/java/com/videocollect/app/ui/settings/

## Responsibility
设置页面。重新配置服务器 IP/端口。

## Files

### SettingsScreen.kt
- `@Composable fun SettingsScreen()` — 服务器配置表单
- 服务器配置卡片：IP 输入 + 端口输入 + 测试连接按钮 + 保存按钮
- 关于卡片：显示版本信息

### SettingsViewModel.kt
- `AndroidViewModel` — 需要 Application Context 访问 SettingsRepository
- `SettingsUiState`：host, port, isLoading, testResult, isSuccess, isSaving
- `init` 块从 DataStore 加载当前配置
- `testConnection()` — 调用 `RetrofitClient.testConnection()`
- `saveConfig(onSaved)` — 保存到 DataStore + 更新 RetrofitClient baseUrl
