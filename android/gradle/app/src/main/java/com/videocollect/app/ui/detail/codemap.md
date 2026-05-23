# android/gradle/app/src/main/java/com/videocollect/app/ui/detail/

## Responsibility
视频详情页面。显示视频信息、播放/缓存/重检操作、缓存进度、备注编辑、来源信息。

## Files

### DetailScreen.kt
- `@Composable fun DetailScreen()` — 主屏幕
- 组件：
  - **VideoInfoCard** — 标题、状态（圆点+文字）、延迟、缓存大小、HLS 标签
  - **ActionButtons** — 播放按钮（蓝色）、缓存按钮（仅 HLS）、重检按钮
  - **CacheProgressCard** — 缓存进度条（LinearProgressIndicator）+ 状态文字（"等待中…"/"已完成"/百分比）
  - **RemarkCard** — 备注编辑（OutlinedTextField + 保存按钮）
  - **SourceInfoCard** — 来源信息（"来源"和"视频"URL 可点击复制到剪贴板）

### DetailViewModel.kt
- `DetailUiState`：record, isLoading, error, remarkText, isSavingRemark, remarkSaved, isRechecking, cacheStatus, isPollingCache, cacheError, isDeleting
- `load(id)` — 加载详情
- `saveRemark()` — 保存备注到后端
- `recheck()` — 重新检测
- `startCache()` — 启动缓存 + 开始轮询
- `pollCacheStatus()` — 每 3s 轮询 cacheStatus，最多 60 次（3 分钟超时）
- `delete(onDeleted)` — 删除并回调
