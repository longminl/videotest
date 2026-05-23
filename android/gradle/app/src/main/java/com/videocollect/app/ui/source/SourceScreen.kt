package com.videocollect.app.ui.source

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videocollect.app.api.models.VideoSource
import com.videocollect.app.ui.theme.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceScreen(
    onBack: () -> Unit,
    viewModel: SourceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频源管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportSources() }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreate() },
                containerColor = Blue600,
                contentColor = SurfaceWhite
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增视频源")
            }
        },
        containerColor = CoolBg
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            // Import section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusGreen)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusGreen)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导入", fontSize = 13.sp, color = StatusGreen)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Message
            if (uiState.importMessage != null) {
                Text(
                    uiState.importMessage ?: "",
                    color = StatusGreen,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (uiState.error != null) {
                Text(
                    uiState.error ?: "",
                    color = StatusRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Content
            when {
                uiState.isLoading && uiState.sources.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Blue600)
                    }
                }

                uiState.sources.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无视频源，点击 + 添加", color = TextTertiary)
                    }
                }

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.sources, key = { it.id ?: 0 }) { source ->
                            SourceCard(
                                source = source,
                                onEdit = { viewModel.openEdit(source) },
                                onDelete = { viewModel.deleteSource(source.id ?: return@SourceCard) },
                                onTestSearch = { keyword ->
                                    viewModel.setTestKeyword(keyword)
                                    source.id?.let { viewModel.testSearch(it) }
                                },
                                onTestParse = { url ->
                                    viewModel.setTestParseUrl(url)
                                    source.id?.let { viewModel.testParse(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit/Create dialog
    if (uiState.showEditDialog) {
        SourceEditDialog(
            source = uiState.editingSource,
            isEditing = uiState.isEditing,
            error = uiState.editError,
            onDismiss = { viewModel.closeEditDialog() },
            onSave = { viewModel.saveSource(it) },
            onSuggest = { url -> viewModel.setSuggestUrl(url); viewModel.suggestRegex() },
            suggestResult = uiState.suggestResult
        )
    }

    // Import dialog
    if (showImportDialog) {
        ImportJsonDialog(
            onDismiss = { showImportDialog = false },
            onImport = { sources ->
                viewModel.importSources(sources)
                showImportDialog = false
            }
        )
    }

    // Export data - copy to clipboard
    LaunchedEffect(uiState.exportData) {
        uiState.exportData?.let { data ->
            val json = Gson().toJson(data)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("video_sources_export", json)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "导出 ${data.size} 条视频源，JSON 已复制到剪贴板", Toast.LENGTH_LONG).show()
            viewModel.loadSources()
        }
    }
}

@Composable
private fun SourceCard(
    source: VideoSource,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestSearch: (String) -> Unit,
    onTestParse: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var parseUrl by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        source.name ?: "未命名",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        source.homeUrl ?: "",
                        fontSize = 12.sp,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Blue600, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = StatusRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Expand/collapse
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextTertiary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (expanded) "收起测试" else "测试搜索与解析",
                    fontSize = 13.sp,
                    color = TextTertiary
                )
            }

            // Test section
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(8.dp))

                // Test search
                Text("测试搜索", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchKeyword,
                        onValueChange = { searchKeyword = it },
                        placeholder = { Text("输入关键词", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onTestSearch(searchKeyword) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                    ) {
                        Text("搜索", fontSize = 13.sp)
                    }
                }

                // Test parse
                Spacer(modifier = Modifier.height(8.dp))
                Text("测试集数解析", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = parseUrl,
                        onValueChange = { parseUrl = it },
                        placeholder = { Text("输入剧集主页URL", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onTestParse(parseUrl) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                    ) {
                        Text("解析", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ===== Edit Dialog =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceEditDialog(
    source: VideoSource?,
    isEditing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (VideoSource) -> Unit,
    onSuggest: (String) -> Unit,
    suggestResult: Map<String, Any>?
) {
    var name by remember { mutableStateOf(source?.name ?: "") }
    var homeUrl by remember { mutableStateOf(source?.homeUrl ?: "") }
    var searchUrl by remember { mutableStateOf(source?.searchUrl ?: "/search?wd={keyword}") }
    var searchDataPath by remember { mutableStateOf(source?.searchDataPath ?: "$") }
    var searchTitleField by remember { mutableStateOf(source?.searchTitleField ?: "title") }
    var searchUrlField by remember { mutableStateOf(source?.searchUrlField ?: "url") }
    var searchCoverField by remember { mutableStateOf(source?.searchCoverField ?: "") }
    var episodePattern by remember { mutableStateOf(source?.episodePattern ?: "") }
    var episodeGroup by remember { mutableStateOf((source?.episodeGroup ?: 1).toString()) }
    var episodeSelector by remember { mutableStateOf(source?.episodeSelector ?: "") }
    var suggestSampleUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) "编辑视频源" else "新增视频源",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error != null) {
                    Text(error, color = StatusRed, fontSize = 13.sp)
                }

                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("名称 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = homeUrl, onValueChange = { homeUrl = it },
                    label = { Text("首页地址 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = searchUrl, onValueChange = { searchUrl = it },
                    label = { Text("搜索URL (用 {keyword} 占位)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Divider(color = Color(0xFFF1F5F9))
                Text("JSON 结果字段（部分站点用）", fontSize = 12.sp, color = TextTertiary)
                OutlinedTextField(value = searchDataPath, onValueChange = { searchDataPath = it },
                    label = { Text("数据路径") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = searchTitleField, onValueChange = { searchTitleField = it },
                        label = { Text("标题字段") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = searchUrlField, onValueChange = { searchUrlField = it },
                        label = { Text("URL字段") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = searchCoverField, onValueChange = { searchCoverField = it },
                    label = { Text("封面字段（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Divider(color = Color(0xFFF1F5F9))
                Text("集数解析", fontSize = 12.sp, color = TextTertiary)

                // Suggest Regex
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = suggestSampleUrl, onValueChange = { suggestSampleUrl = it },
                        placeholder = { Text("示例播放URL", fontSize = 12.sp) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { onSuggest(suggestSampleUrl) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("🤖", fontSize = 14.sp)
                    }
                }

                suggestResult?.let { result ->
                    val pattern = result["pattern"] as? String ?: ""
                    val suggestedGroup = result["group"] as? Int ?: 1
                    val suggestedHome = result["homeUrl"] as? String ?: ""
                    if (pattern.isNotEmpty()) {
                        Text("推荐正则: $pattern", fontSize = 12.sp, color = StatusGreen)
                        episodePattern = pattern
                        episodeGroup = suggestedGroup.toString()
                        if (homeUrl.isEmpty()) homeUrl = suggestedHome
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = episodePattern, onValueChange = { episodePattern = it },
                        label = { Text("集数正则") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = episodeGroup, onValueChange = { episodeGroup = it },
                        label = { Text("捕获组") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp))
                }
                OutlinedTextField(value = episodeSelector, onValueChange = { episodeSelector = it },
                    label = { Text("集数选择器（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(VideoSource(
                        id = source?.id,
                        name = name,
                        homeUrl = homeUrl,
                        searchUrl = searchUrl,
                        searchDataPath = searchDataPath.ifBlank { "$" },
                        searchTitleField = searchTitleField.ifBlank { "title" },
                        searchUrlField = searchUrlField.ifBlank { "url" },
                        searchCoverField = searchCoverField.ifBlank { null },
                        episodePattern = episodePattern.ifBlank { null },
                        episodeGroup = episodeGroup.toIntOrNull() ?: 1,
                        episodeSelector = episodeSelector.ifBlank { null },
                        encoding = "UTF-8",
                        sortOrder = source?.sortOrder ?: 0,
                        videoCount = source?.videoCount,
                        createdAt = source?.createdAt,
                        updatedAt = source?.updatedAt
                    ))
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
            ) {
                Text(if (isEditing) "保存" else "创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ===== Import Dialog =====

@Composable
fun ImportJsonDialog(
    onDismiss: () -> Unit,
    onImport: (List<VideoSource>) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入视频源（JSON）", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("粘贴从其他设备导出的 JSON 配置：", fontSize = 13.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it; error = null },
                    placeholder = { Text("[\n  {\n    \"name\": \"...\",\n    ...\n  }\n]", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error ?: "", color = StatusRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val type = object : TypeToken<List<VideoSource>>() {}.type
                        val sources: List<VideoSource> = Gson().fromJson(jsonText, type)
                        if (sources.isEmpty()) {
                            error = "JSON 数组为空"
                        } else {
                            onImport(sources)
                            onDismiss()
                        }
                    } catch (e: Exception) {
                        error = "JSON 解析失败: ${e.message}"
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
