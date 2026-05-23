package com.videocollect.app.ui.list

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
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
import com.videocollect.app.api.models.VideoGroup
import com.videocollect.app.api.models.VideoRecord
import com.videocollect.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onAddClick: () -> Unit,
    onDetailClick: (Long) -> Unit,
    onSettingsClick: () -> Unit = {},
    onSourcesClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
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
                        if (uiState.selectedIds.isNotEmpty()) {
                            IconButton(onClick = { viewModel.showMoveGroupDialog() }) {
                                Icon(Icons.Default.Folder, contentDescription = "移动到合集", tint = Blue600)
                            }
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
                        IconButton(onClick = onSourcesClick) {
                            Icon(Icons.Default.Extension, contentDescription = "视频源")
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "总搜索")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
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
                        focusedBorderColor = if (uiState.keyword.isNotEmpty()) Blue700 else Blue600,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Group filter chips
            if (!uiState.isSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = uiState.groupIdFilter == null,
                        onClick = { viewModel.setGroupFilter(null) },
                        label = { Text("全部视频", fontSize = 12.sp) },
                        colors = chipColors(uiState.groupIdFilter == null),
                        border = null
                    )
                    uiState.groups.forEach { group ->
                        FilterChip(
                            selected = uiState.groupIdFilter == group.id,
                            onClick = { viewModel.setGroupFilter(group.id) },
                            label = {
                                Text(
                                    (group.name ?: "未知") + if (group.videoCount != null) " (${group.videoCount})" else "",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = chipColors(uiState.groupIdFilter == group.id),
                            border = null
                        )
                    }
                    IconButton(
                        onClick = { viewModel.openGroupManagement() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "管理合集",
                            tint = Blue600,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                val groupErr = uiState.groupError
                if (groupErr != null) {
                    Text(
                        groupErr,
                        color = StatusRed, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Status filter + sort chips
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
                    Spacer(modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.cycleSort() },
                        label = { Text(viewModel.sortLabel(), fontSize = 13.sp) },
                        colors = chipColors(selected = false),
                        border = null,
                        leadingIcon = {
                            Icon(Icons.Default.SwapVert, contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = TextSecondary)
                        }
                    )
                }
            }

            // Content with pull-to-refresh
            val pullRefreshState = rememberPullRefreshState(
                refreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
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
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val hasFilter = uiState.keyword.isNotBlank() ||
                                uiState.statusFilter != null ||
                                uiState.groupIdFilter != null
                            Icon(
                                imageVector = if (hasFilter) Icons.Default.SearchOff else Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = TextTertiary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (hasFilter) "未找到匹配的视频" else "还没有收藏的视频",
                                color = TextTertiary, fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (hasFilter) "尝试修改搜索条件或筛选" else "点击 + 按钮添加",
                                color = TextTertiary.copy(alpha = 0.6f), fontSize = 13.sp
                            )
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
                    }
                }

                PullRefreshIndicator(
                    refreshing = uiState.isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    backgroundColor = SurfaceWhite,
                    contentColor = Blue600
                )
            }
        }
    }

    // Batch move to group dialog
    if (uiState.showMoveGroupDialog) {
        MoveToGroupDialog(
            groups = uiState.groups,
            selectedCount = uiState.selectedIds.size,
            onSelectGroup = { groupId -> viewModel.batchMoveToGroup(groupId) },
            onDismiss = { viewModel.dismissMoveGroupDialog() }
        )
    }

    // Group management dialog
    if (uiState.showGroupManagementDialog) {
        GroupManagementDialog(
            groups = uiState.groups,
            groupError = uiState.groupManagementError,
            onDismiss = { viewModel.dismissGroupManagement() },
            onCreateGroup = { name -> viewModel.createGroup(name) },
            onDeleteGroup = { id -> viewModel.deleteGroup(id) },
            onRenameGroup = { id, name -> viewModel.renameGroup(id, name) }
        )
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
                    if (record.cacheSize != null && record.cacheSize != "-") {
                        Text(
                            text = " · ${record.cacheSize}",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                }
                // Group name + episode number row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!record.groupDisplay.isNullOrBlank() && record.groupDisplay != "未分组") {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Blue400
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = record.groupDisplay,
                            fontSize = 11.sp,
                            color = Blue500,
                            maxLines = 1
                        )
                    }
                    if (record.episodeNumber != null) {
                        if (record.groupDisplay != "未分组") {
                            Text(" · ", fontSize = 11.sp, color = TextTertiary)
                        }
                        Text(
                            text = record.episodeText,
                            fontSize = 11.sp,
                            color = TextSecondary
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

// ====== 管理合集弹窗 ======

@Composable
private fun GroupManagementDialog(
    groups: List<VideoGroup>,
    groupError: String?,
    onDismiss: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onRenameGroup: (Long, String) -> Unit
) {
    var newGroupName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Long?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (deleteConfirmId == null && renameTarget == null) onDismiss()
        },
        title = { Text("管理合集") },
        text = {
            Column {
                if (groupError != null) {
                    Text(groupError, color = StatusRed, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (groups.isEmpty()) {
                    Text("还没有合集", color = TextTertiary, fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Blue500
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.name ?: "未命名", fontSize = 14.sp, color = TextPrimary)
                                Text(
                                    "${group.videoCount ?: 0} 个视频",
                                    fontSize = 11.sp, color = TextTertiary
                                )
                            }
                            IconButton(
                                onClick = {
                                    renameTarget = group.id
                                    renameName = group.name ?: ""
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "重命名",
                                    modifier = Modifier.size(18.dp), tint = Blue500)
                            }
                            IconButton(
                                onClick = { deleteConfirmId = group.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "删除",
                                    modifier = Modifier.size(18.dp), tint = StatusRed)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("新合集名称", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue600,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    )
                    Button(
                        onClick = {
                            if (newGroupName.isNotBlank()) {
                                onCreateGroup(newGroupName.trim())
                                newGroupName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                    ) {
                        Text("新建", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )

    // Rename sub-dialog
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null; renameName = "" },
            title = { Text("重命名合集") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    placeholder = { Text("输入新名称") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue600,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameName.isNotBlank()) {
                        renameTarget?.let { onRenameGroup(it, renameName.trim()) }
                        renameTarget = null
                        renameName = ""
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null; renameName = "" }) { Text("取消") }
            }
        )
    }

    // Delete confirmation sub-dialog
    if (deleteConfirmId != null) {
        val groupName = groups.find { it.id == deleteConfirmId }?.name ?: "此合集"
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("删除合集") },
            text = { Text("确定删除「$groupName」？视频不会被删除，仅移出合集。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmId?.let { onDeleteGroup(it) }
                    deleteConfirmId = null
                }) { Text("删除", color = StatusRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun chipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Blue100,
    selectedLabelColor = Blue700,
    containerColor = SurfaceWhite,
    labelColor = TextSecondary
)

// ====== 批量移动到合集弹窗 ======

@Composable
private fun MoveToGroupDialog(
    groups: List<VideoGroup>,
    selectedCount: Int,
    onSelectGroup: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (groups.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("移动到合集") },
            text = { Text("暂无合集，请先在详情页创建") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("确定") }
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到合集（已选 $selectedCount 个）") },
        text = {
            Column {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectGroup(group.id ?: return@clickable) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = group.name ?: "未命名",
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${group.videoCount ?: 0} 个视频",
                            fontSize = 12.sp,
                            color = TextTertiary
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
