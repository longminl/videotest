# android/gradle/app/src/main/java/com/videocollect/app/ui/player/

## Responsibility
全屏 ExoPlayer 播放器。支持 prev/next 导航、进度条、捏合缩放、倍速、方向切换、缓存轮询。

## PlayerActivity.kt
- `ComponentActivity`，默认横屏（`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`）
- **intent()** — 静态工厂方法，传入 videoUrl、title、isM3u8、baseUrl、itemsJson（列表数据用于上下集导航）、currentIndex
- 解析 items JSON 为 `List<VideoRecord>`

## PlayerScreen Composable
- **播放器**：`ExoPlayer` 通过 `remember {}` 创建一次，`LaunchedEffect(currentVideoIndex)` 切换 MediaSource
  - HLS 流 → `HlsMediaSource.Factory` + `/proxy/m3u8?url=...` 代理地址
  - 直链 → `ProgressiveMediaSource.Factory`
  - 自定义 `DefaultHttpDataSource`（User-Agent + 跨协议重定向）
- **进度条**：200ms 轮询播放位置 + `Slider` 可拖动 + 缓冲区指示
- **捏合缩放**：`graphicsLayer` + `detectTransformGestures`，1x~4x，双击重置
- **控制栏**：AnimatedVisibility 淡入淡出
  - 顶部：返回 / 上一集 / 标题 + 序号 / 下一集 / 列表按钮
  - 底部：倍速菜单（0.5x~4x）/ 后退10s / 播放暂停 / 快进10s / 缓存按钮 / 方向切换
- **方向**：循环切换 `SENSOR_LANDSCAPE → USER_PORTRAIT → SENSOR`
- **横屏沉浸**：`WindowInsetsControllerCompat` 隐藏系统栏
- **缓存轮询**：`LaunchedEffect` 每 5s 查询 cacheStatus，显示进度/大小
- **缓存状态**：切换视频自动重置缓存状态
- **Player.Listener**：监听播放状态变化和错误
