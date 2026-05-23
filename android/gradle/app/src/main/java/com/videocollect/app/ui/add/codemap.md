# android/gradle/app/src/main/java/com/videocollect/app/ui/add/

## Responsibility
添加视频页面。用户输入 URL 提交收藏。

## Files

### AddVideoScreen.kt
- `@Composable fun AddVideoScreen()` — 输入 URL + 提交按钮
- OutlinedTextField（URL 输入，KeyboardType.Uri）
- 支持文字："网页链接（自动解析视频地址）、MP4/M3U8 直链"
- 提交时按钮显示 "解析中…" + CircularProgressIndicator
- 错误/成功结果以 AnimatedVisibility 卡片展示

### AddVideoViewModel.kt
- `AddVideoUiState`：url, isLoading, result, error, isSuccess
- `submit(onSuccess)` — 调用 `collectVideo()` API，成功后回调 `onSuccess`
