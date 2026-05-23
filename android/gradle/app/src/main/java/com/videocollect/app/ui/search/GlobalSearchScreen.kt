package com.videocollect.app.ui.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videocollect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onImportDone: () -> Unit,
    viewModel: GlobalSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全局搜索", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = CoolBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (uiState.step) {
            1 -> this.SearchStep(uiState, viewModel)
            2 -> ResultsStep(uiState, viewModel)
            3 -> this.EpisodesStep(uiState, viewModel)
            4 -> this.ImportStep(uiState, viewModel, onImportDone)
            }
        }
    }
}

// ===== Step 1: Search =====

@Composable
private fun ColumnScope.SearchStep(uiState: GlobalSearchUiState, viewModel: GlobalSearchViewModel) {
    // Search input
    OutlinedTextField(
        value = uiState.keyword,
        onValueChange = { viewModel.setKeyword(it) },
        placeholder = { Text("输入关键词搜索所有视频源...", color = TextTertiary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Blue600,
            unfocusedBorderColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )

    // Source selector
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择视频源", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                TextButton(onClick = { viewModel.toggleAllSources() }) {
                    Text(
                        if (uiState.selectedSourceIds.size == uiState.sources.size) "取消全选" else "全选",
                        fontSize = 12.sp
                    )
                }
            }
            if (uiState.sourcesLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Blue600)
                }
            } else if (uiState.sources.isEmpty()) {
                Text("暂无视频源，请先在视频源管理中添加", color = TextTertiary, fontSize = 13.sp)
            } else {
                Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    uiState.sources.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { source.id?.let { viewModel.toggleSource(it) } }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = source.id in uiState.selectedSourceIds,
                                onCheckedChange = { source.id?.let { viewModel.toggleSource(it) } },
                                colors = CheckboxDefaults.colors(checkedColor = Blue600)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(source.name ?: "未命名", fontSize = 14.sp, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.weight(1f))

    // Search button
    Button(
        onClick = { viewModel.search() },
        enabled = !uiState.isSearching && uiState.keyword.isNotBlank() && uiState.selectedSourceIds.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue600)
    ) {
        if (uiState.isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceWhite, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(Icons.Default.Search, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("搜索", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

// ===== Step 2: Results =====

@Composable
private fun ResultsStep(uiState: GlobalSearchUiState, viewModel: GlobalSearchViewModel) {
    // Back button
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.reset() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextSecondary)
        }
        Text("搜索结果: ${uiState.keyword}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
    }

    if (uiState.isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Blue600)
                Spacer(modifier = Modifier.height(12.dp))
                Text("搜索中...", color = TextSecondary)
            }
        }
    } else if (uiState.searchResults.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未找到结果", color = TextTertiary)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            uiState.searchResults.forEach { (sourceId, results) ->
                val sourceName = uiState.sources.find { it.id == sourceId }?.name ?: "源#$sourceId"
                if (results.isNotEmpty()) {
                    item {
                        Text(
                            sourceName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Blue700,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(results) { item ->
                        SearchResultCard(
                            title = item.title ?: "",
                            url = item.url ?: "",
                            sourceId = sourceId,
                            onClick = { viewModel.selectResult(sourceId, item.url ?: "", item.title ?: "") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    title: String,
    url: String,
    sourceId: Long,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    url,
                    fontSize = 12.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ===== Step 3: Episodes =====

@Composable
private fun ColumnScope.EpisodesStep(uiState: GlobalSearchUiState, viewModel: GlobalSearchViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.backToStep2() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextSecondary)
        }
        Column {
            Text("选择集数", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Text(
                uiState.selectedTitle ?: "",
                fontSize = 12.sp,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (uiState.isParsing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Blue600)
                Spacer(modifier = Modifier.height(12.dp))
                Text("解析剧集中...", color = TextSecondary)
            }
        }
    } else if (uiState.episodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("未解析到集数", color = TextTertiary)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { viewModel.goToImport() }) {
                    Text("跳过，直接导入")
                }
            }
        }
    } else {
        // Quick select bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("共 ${uiState.episodes.size} 集", fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { viewModel.selectAllEpisodes() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("全选", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { viewModel.clearEpisodeSelection() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("清除", fontSize = 12.sp) }
        }

        // Episode list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(uiState.episodes.take(500)) { ep ->
                val num = ep.episodeNumber ?: 0
                val isSelected = num in uiState.selectedEpisodes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleEpisode(num) }
                        .background(
                            if (isSelected) Blue100 else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { viewModel.toggleEpisode(num) },
                        colors = CheckboxDefaults.colors(checkedColor = Blue600),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        ep.text ?: "第${num}集",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "第${num}集",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.goToImport() },
            enabled = uiState.selectedEpisodes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue600)
        ) {
            Text("导入选中 (${uiState.selectedEpisodes.size})", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ===== Step 4: Import =====

@Composable
private fun ColumnScope.ImportStep(
    uiState: GlobalSearchUiState,
    viewModel: GlobalSearchViewModel,
    onDone: () -> Unit
) {
    if (uiState.importResult != null) {
        // Show success
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = StatusGreen
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("导入完成", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.importResult?.summary ?: "", color = TextSecondary)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                ) {
                    Text("完成")
                }
            }
        }
        return
    }

    // Import form
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.backToStep3() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextSecondary)
        }
        Text("导入到合集", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("即将导入 ${uiState.selectedEpisodes.size} 集到合集", fontSize = 14.sp, color = TextPrimary)

            // Select existing group
            if (uiState.groups.isNotEmpty()) {
                Text("选择已有合集", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary)
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(uiState.groups) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSelectedGroupId(group.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedGroupId == group.id,
                                onClick = { viewModel.setSelectedGroupId(group.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = Blue600)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(group.name ?: "未命名", fontSize = 14.sp, color = TextPrimary)
                                if (group.videoCount != null) {
                                    Text("${group.videoCount} 个视频", fontSize = 12.sp, color = TextTertiary)
                                }
                            }
                        }
                    }
                }
            }

            // Or create new
            Text("或新建合集", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary)
            OutlinedTextField(
                value = uiState.newGroupName,
                onValueChange = { viewModel.setNewGroupName(it) },
                placeholder = { Text("新合集名称", color = TextTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (uiState.importError != null) {
        Text(uiState.importError ?: "", color = StatusRed, fontSize = 13.sp)
    }

    Spacer(modifier = Modifier.weight(1f))

    Button(
        onClick = { viewModel.doImport() },
        enabled = !uiState.isImporting && (uiState.selectedGroupId != null || uiState.newGroupName.isNotBlank()),
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue600)
    ) {
        if (uiState.isImporting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceWhite, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("开始导入", fontWeight = FontWeight.SemiBold)
    }
}
