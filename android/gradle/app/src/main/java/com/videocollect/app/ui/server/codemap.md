# android/gradle/app/src/main/java/com/videocollect/app/ui/server/

## Responsibility
首次启动服务器配置向导。在未配置服务器时强制展示。

## Files

### ServerConfigScreen.kt
- `@Composable fun ServerConfigScreen()` — 全屏渐变背景 + 居中卡片
- 与 SettingsScreen 类似的 IP/端口表单
- 首次启动时显示，配置完成后跳转到列表页

### ServerConfigViewModel.kt
- `AndroidViewModel` — 需要 Application Context
- 功能与 SettingsViewModel 相同：加载配置、测试连接、保存配置
