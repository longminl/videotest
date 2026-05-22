package com.videocollect.app.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videocollect.app.api.models.VideoRecord
import com.videocollect.app.api.CacheStatusResponse
import com.videocollect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    videoId: Long,
    onBack: () -> Unit,
    onPlay: (VideoRecord) -> Unit,
    onDeleted: () -> Unit,
    viewModel: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(videoId) {
        viewModel.load(videoId)
    }

    val record = uiState.record

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        record?.title ?: "详情",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (record != null) {
                        IconButton(onClick = { viewModel.delete(onDeleted) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = SurfaceWhite)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = SurfaceWhite,
                    navigationIconContentColor = SurfaceWhite
                ),
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                )
            )
        },
        containerColor = CoolBg
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue600)
                }
            }

            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "加载失败", color = TextSecondary)
                }
            }

            record != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Video info card
                    VideoInfoCard(record)

                    // Action buttons
                    ActionButtons(
                        record = record,
                        isRechecking = uiState.isRechecking,
                        onPlay = { onPlay(record) },
                        onRecheck = { viewModel.recheck() },
                        onCache = { viewModel.startCache() }
                    )

                    // Cache progress
                    if (record.isM3u8) {
                        CacheProgressCard(
                            cacheStatus = uiState.cacheStatus,
                            isPolling = uiState.isPollingCache,
                            cacheError = uiState.cacheError
                        )
                    }

                    // Remark
                    RemarkCard(
                        text = uiState.remarkText,
                        isSaving = uiState.isSavingRemark,
                        saved = uiState.remarkSaved,
                        onTextChange = { viewModel.updateRemark(it) },
                        onSave = { viewModel.saveRemark() }
                    )

                    // Source info
                    SourceInfoCard(record)
                }
            }
        }
    }
}

@Composable
private fun VideoInfoCard(record: VideoRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Title
            Text(
                text = record.title ?: "无标题",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Status row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = when (record.status) {
                    1 -> StatusGreen; 2 -> StatusRed; 3 -> StatusOrange; else -> StatusGray
                }
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(record.statusText, color = statusColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                if (record.latencyMs != null && record.status == 1) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.Speed, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = TextTertiary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${record.latencyMs}ms", color = TextTertiary, fontSize = 13.sp)
                }
                if (record.cacheSize != null && record.cacheSize != "-"
                    && record.cacheSize != "0 B") {
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.Storage, contentDescription = null,
                        modifier = Modifier.size(16.dp), tint = TextTertiary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(record.cacheSize, color = TextTertiary, fontSize = 13.sp)
                }
                if (record.isM3u8) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = Blue100) {
                        Text(
                            "HLS",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = Blue700,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    record: VideoRecord,
    isRechecking: Boolean,
    onPlay: () -> Unit,
    onRecheck: () -> Unit,
    onCache: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (record.isPlayable) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("播放", fontWeight = FontWeight.SemiBold)
            }
        }
        if (record.isM3u8 && record.isPlayable) {
            OutlinedButton(
                onClick = onCache,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue600),
                border = BorderStroke(1.dp, Blue600)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("缓存", fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedButton(
            onClick = onRecheck,
            enabled = !isRechecking,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            border = BorderStroke(1.dp, TextTertiary)
        ) {
            if (isRechecking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TextSecondary)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("重检", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CacheProgressCard(cacheStatus: CacheStatusResponse?, isPolling: Boolean, cacheError: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Blue600
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("缓存状态", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                if (isPolling) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Blue400)
                }
            }

            if (cacheError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(cacheError, color = StatusRed, fontSize = 13.sp)
            }

            if (cacheStatus != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val total = cacheStatus.total ?: 0
                val cached = cacheStatus.cached ?: 0
                val progress = if (total > 0) cached.toFloat() / total else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (progress >= 1f) StatusGreen else Blue500,
                    trackColor = Blue100
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("$cached / $total TS", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        if (progress >= 1f) "已完成" else "${(progress * 100).toInt()}%",
                        color = if (progress >= 1f) StatusGreen else Blue600,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (cacheError == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (isPolling) "等待中…" else "点击上方「缓存」按钮开始下载",
                    color = TextTertiary, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun RemarkCard(
    text: String,
    isSaving: Boolean,
    saved: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Blue600
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("备注", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("添加备注...", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue600,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SurfaceWhite)
                    } else {
                        Text("保存", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceInfoCard(record: VideoRecord) {
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = TextTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("来源信息", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            CopyableInfoRow("来源", record.sourceUrl ?: "-", clipboardManager, context)
            CopyableInfoRow("视频", record.videoUrl ?: "-", clipboardManager, context)
            InfoRow("收录", record.createdAt ?: "-")
            if (record.pageTitle != null) {
                InfoRow("页面", record.pageTitle)
            }
        }
    }
}

@Composable
private fun CopyableInfoRow(label: String, value: String, clipboardManager: androidx.compose.ui.platform.ClipboardManager, context: android.content.Context) {
    Column(modifier = Modifier
        .padding(vertical = 4.dp)
        .clickable {
            if (value.isNotEmpty() && value != "-") {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(value))
                android.widget.Toast.makeText(context, "已复制: $value", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Text(label, color = TextTertiary, fontSize = 12.sp)
        Text(value, color = Blue600, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = TextTertiary, fontSize = 12.sp)
        Text(value, color = TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
