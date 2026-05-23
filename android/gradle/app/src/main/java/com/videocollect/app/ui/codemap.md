# android/gradle/app/src/main/java/com/videocollect/app/ui/

## Responsibility
Android UI 层。使用 Jetpack Compose + Material3 构建，MVVM 架构（每个页面有自己的 ViewModel）。

## Screens
| 页面 | 路由 | ViewModel | 功能 |
|-------|-------|-----------|---------|
| ListScreen | `list` | ListViewModel | 视频列表（下拉刷新、筛选、排序、多选删除） |
| DetailScreen | `detail/{id}` | DetailViewModel | 视频详情 + 内嵌播放器 + 备注编辑 + 缓存进度 |
| PlayerActivity | （独立 Activity） | — | 全屏 ExoPlayer（缩放、倍速、导航） |
| AddVideoScreen | `add` | AddVideoViewModel | 输入 URL 收藏新视频 |
| SettingsScreen | `settings` | SettingsViewModel | 服务器 IP/端口配置 |
| ServerConfigScreen | `server_config` | ServerConfigViewModel | 首次启动配置向导 |
| Theme | — | — | 蓝白 Material3 主题 + 暗黑模式 |

## Design Patterns
- **MVVM**：每个 Screen 对应一个 ViewModel，状态通过 StateFlow 驱动
- **Composition Local**：Navigation Compose 管理路由
- **Shared ViewModel**：ListViewModel 在 NavHost 层级共享，避免返回时重新加载
