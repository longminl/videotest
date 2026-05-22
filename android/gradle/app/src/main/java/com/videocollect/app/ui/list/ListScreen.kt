package com.videocollect.app.ui.list

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videocollect.app.api.models.VideoRecord
import com.videocollect.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onAddClick: () -> Unit,
    onDetailClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Load more on scroll to bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingMore && uiState.hasMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectMode) {
                // Selection mode bar
                TopAppBar(
                    title = { Text("已选 ${uiState.selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSelectMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (uiState.selectedIds.size == uiState.items.size) {
                                viewModel.deselectAll()
                            } else {
                                viewModel.selectAll()
                            }
                        }) {
                            Text(
                                if (uiState.selectedIds.size == uiState.items.size) "取消全选" else "全选",
                                fontSize = 14.sp
                            )
                        }
                        IconButton(onClick = { viewModel.deleteSelected { } }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = StatusRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceWhite,
                        titleContentColor = TextPrimary
                    )
                )
            } else {
                // Normal top bar with gradient
                TopAppBar(
                    title = {
                        Text(
                            "视频收藏",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        if (uiState.items.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleSelectMode() }) {
                                Icon(Icons.Default.Checklist, contentDescription = "批量操作")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = SurfaceWhite,
                        actionIconContentColor = SurfaceWhite
                    ),
                    modifier = Modifier.background(
                        Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                    )
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectMode) {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = Blue600,
                    contentColor = SurfaceWhite,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "收藏视频", modifier = Modifier.size(28.dp))
                }
            }
        },
        containerColor = CoolBg
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar
            if (!uiState.isSelectMode) {
                OutlinedTextField(
                    value = uiState.keyword,
                    onValueChange = { viewModel.setKeyword(it) },
                    placeholder = { Text("搜索标题或URL...", color = TextTertiary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary)
                    },
                    trailingIcon = {
                        if (uiState.keyword.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setKeyword(""); viewModel.loadData() }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除", tint = TextTertiary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue600,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Status filter chips
            if (!uiState.isSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.statusFilter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("全部", fontSize = 13.sp) },
                        colors = chipColors(uiState.statusFilter == null),
                        border = null
                    )
                    FilterChip(
                        selected = uiState.statusFilter == 1,
                        onClick = { viewModel.setStatusFilter(1) },
                        label = { Text("可播放", fontSize = 13.sp) },
                        colors = chipColors(uiState.statusFilter == 1),
                        border = null
                    )
                    FilterChip(
                        selected = uiState.statusFilter == 3,
                        onClick = { viewModel.setStatusFilter(3) },
                        label = { Text("失败", fontSize = 13.sp) },
                        colors = chipColors(uiState.statusFilter == 3),
                        border = null
                    )
                }
            }

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Blue600)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("加载中...", color = TextSecondary, fontSize = 14.sp)
                        }
                    }

                    uiState.error != null && uiState.items.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null,
                                modifier = Modifier.size(48.dp), tint = TextTertiary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(uiState.error ?: "加载失败", color = TextSecondary)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.loadData() }) {
                                Text("重试")
                            }
                        }
                    }

                    uiState.items.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null,
                                modifier = Modifier.size(64.dp), tint = TextTertiary.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("还没有收藏的视频", color = TextTertiary, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("点击 + 按钮添加", color = TextTertiary.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.items, key = { it.id ?: 0 }) { record ->
                                VideoCard(
                                    record = record,
                                    isSelectMode = uiState.isSelectMode,
                                    isSelected = record.id?.let { it in uiState.selectedIds } ?: false,
                                    onToggleSelect = { record.id?.let { viewModel.toggleSelection(it) } },
                                    onClick = {
                                        if (uiState.isSelectMode) {
                                            record.id?.let { viewModel.toggleSelection(it) }
                                        } else {
                                            record.id?.let { onDetailClick(it) }
                                        }
                                    },
                                    onCacheClick = {
                                        viewModel.startCache(
                                            record.id ?: 0,
                                            record.videoUrl ?: "",
                                            record.title
                                        )
                                    }
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = Blue600
                                        )
                                    }
                                }
                            }
                        }

                        // Pull-to-refresh indicator
                        if (uiState.isRefreshing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                color = Blue400
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    record: VideoRecord,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onCacheClick: () -> Unit
) {
    val statusColor = when (record.status) {
        1 -> StatusGreen
        2 -> StatusRed
        3 -> StatusOrange
        else -> StatusGray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for select mode
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = Blue600),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title ?: "无标题",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.statusText,
                        fontSize = 12.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                    if (record.status == 1 && record.latencyMs != null) {
                        Text(
                            text = " · ${record.latencyMs}ms",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                    if (record.isM3u8) {
                        Text(
                            text = " · HLS",
                            fontSize = 12.sp,
                            color = StatusBlue
                        )
                    }
                }
            }

            // Actions
            if (!isSelectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Cache button for m3u8
                    if (record.isM3u8 && record.isPlayable) {
                        IconButton(
                            onClick = onCacheClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (record.isCached == true) Icons.Default.CheckCircle
                                    else Icons.Default.CloudDownload,
                                contentDescription = "缓存",
                                tint = if (record.isCached == true) StatusGreen else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Play button
                    if (record.isPlayable) {
                        FilledIconButton(
                            onClick = onClick,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Blue600.copy(alpha = 0.1f),
                                contentColor = Blue600
                            )
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun chipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Blue100,
    selectedLabelColor = Blue700,
    containerColor = SurfaceWhite,
    labelColor = TextSecondary
)
