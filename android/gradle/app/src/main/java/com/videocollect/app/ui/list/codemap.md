# android/gradle/app/src/main/java/com/videocollect/app/ui/list/

## Responsibility
视频列表页面。支持分页加载、下拉刷新、状态筛选、排序、多选删除。

## Files

### ListScreen.kt
- `@Composable fun ListScreen()` — 主屏幕
- **TopAppBar**：正常模式（标题 + 设置齿轮 + 批量按钮）+ 选择模式（已选数 + 全选 + 删除）
- **搜索栏**：OutlinedTextField + 回车搜索
- **筛选 Chip**：全部 / 可播放 / 失败 + 排序切换
- **下拉刷新**：Material1 `pullRefresh` + `PullRefreshIndicator`
- **分页**：`LazyColumn` + `LaunchedEffect` 检测底部（剩余 3 项触发加载更多）
- **VideoCard**：每张卡片显示状态圆点、标题、状态/延迟/HLS/缓存大小、播放/缓存按钮
- 选择模式下显示 Checkbox，支持多选批量删除

### ListViewModel.kt
- `ListUiState`：items, isLoading, isRefreshing, isLoadingMore, currentPage, hasMore, statusFilter, keyword, selectedIds, isSelectMode, error, sortBy, sortOrder
- `loadData()` — 首页加载（page=1）
- `loadMore()` — 追加加载（page++）
- `refresh()` — 下拉刷新
- `cycleSort()` — 循环切换排序：created_at desc → created_at asc → title asc → title desc
- `sortLabel()` — 返回中文排序标签："最新"/"最早"/"标题A-Z"/"标题Z-A"
- 多选方法：`toggleSelectMode()`、`toggleSelection()`、`selectAll()`、`deselectAll()`、`deleteSelected()`
- `startCache()` — fire-and-forget 启动缓存
