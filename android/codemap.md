# android/

## Responsibility
Android 客户端项目。独立 Gradle 项目，与后端代码目录无关。

## Tech Stack
- Kotlin + Jetpack Compose + Material3
- Retrofit + OkHttp (HTTP 客户端)
- ExoPlayer (视频播放)
- Navigation Compose (页面路由)
- DataStore Preferences (本地配置持久化)

## Build
- 最低 SDK：API 26（Android 8.0）
- 目标 SDK：API 35（Android 15）
- Gradle 8.6 + AGP 8.3.0
- 构建输出：`android/gradle/app/build/outputs/apk/debug/app-debug.apk`

## Pages
| 路由/Activity | 功能 |
|-------|---------|
| server_config | 首次配置服务器 IP:端口 |
| list | 视频列表（分页、筛选、排序、多选删除） |
| detail/{id} | 详情 + 播放 + 缓存进度 + 备注编辑 |
| PlayerActivity | 全屏 ExoPlayer（缩放、倍速、导航） |
| add | 添加视频 URL |
| settings | 服务器配置 |
