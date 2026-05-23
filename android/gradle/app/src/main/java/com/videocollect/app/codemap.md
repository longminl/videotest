# android/gradle/app/src/main/java/com/videocollect/app/

## Responsibility
Android 客户端入口。使用 Jetpack Compose + Navigation Compose 组织页面路由。

## Files

### VideoCollectApp.kt
- `Application` 子类，Android 应用入口

### MainActivity.kt
- `ComponentActivity` 使用 `setContent` 设置 Compose 内容
- 启动时通过 `SettingsRepository` 读取服务器配置，如果已配置则调用 `RetrofitClient.updateBaseUrl()` 初始化
- **`AppNavigation()`** — Navigation Compose 路由宿主
  - 共享 `ListViewModel` 在导航层级（避免列表数据在返回时丢失）
  - 起始路由：`server_config`（未配置）或 `list`（已配置）
  - 路由表：
    | 路由 | 页面 | 说明 |
    |-------|-------|--------|
    | `server_config` | ServerConfigScreen | 首次配置服务器 |
    | `list` | ListScreen | 视频列表 |
    | `settings` | SettingsScreen | 设置（IP/端口） |
    | `add` | AddVideoScreen | 添加视频 |
    | `detail/{id}` | DetailScreen | 详情 + 播放 |
  - PlayerActivity 通过 `ActivityResultLauncher` 启动，返回时如果 `RESULT_GO_TO_LIST` 则退回列表页
