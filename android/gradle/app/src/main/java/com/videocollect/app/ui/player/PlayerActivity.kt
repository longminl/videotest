package com.videocollect.app.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import com.videocollect.app.api.models.VideoRecord
import com.videocollect.app.ui.theme.*

class PlayerActivity : ComponentActivity() {

    companion object {
        const val RESULT_GO_TO_LIST = 1001
        private const val EXTRA_URL = "video_url"
        private const val EXTRA_TITLE = "video_title"
        private const val EXTRA_IS_M3U8 = "is_m3u8"
        private const val EXTRA_BASE_URL = "base_url"
        private const val EXTRA_VIDEO_ITEMS = "video_items"
        private const val EXTRA_CURRENT_INDEX = "current_index"

        fun intent(
            context: Context,
            videoUrl: String,
            title: String?,
            isM3u8: Boolean,
            baseUrl: String,
            itemsJson: String = "",
            currentIndex: Int = 0
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, videoUrl)
                putExtra(EXTRA_TITLE, title ?: "视频")
                putExtra(EXTRA_IS_M3U8, isM3u8)
                putExtra(EXTRA_BASE_URL, baseUrl)
                putExtra(EXTRA_VIDEO_ITEMS, itemsJson)
                putExtra(EXTRA_CURRENT_INDEX, currentIndex)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val videoUrl = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "视频"
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL) ?: ""
        val itemsJson = intent.getStringExtra(EXTRA_VIDEO_ITEMS) ?: ""
        val currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)

        val singleItem = VideoRecord(
            id = -1, title = title, videoUrl = videoUrl,
            sourceUrl = null, status = 1, latencyMs = null,
            pageTitle = null, remark = null,
            groupId = null, groupName = null, episodeNumber = null,
            isCached = null, cacheSize = null,
            createdAt = null, updatedAt = null
        )

        val items = if (itemsJson.isNotBlank()) {
            try {
                Gson().fromJson<List<VideoRecord>>(
                    itemsJson,
                    object : TypeToken<List<VideoRecord>>() {}.type
                ) ?: listOf(singleItem)
            } catch (_: Exception) { listOf(singleItem) }
        } else {
            listOf(singleItem)
        }

        setContent {
            VideoCollectTheme(darkTheme = true) {
                PlayerScreen(
                    items = items,
                    initialIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                    baseUrl = baseUrl,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(
    items: List<VideoRecord>,
    initialIndex: Int,
    baseUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // ── navigation state ──
    var currentVideoIndex by remember { mutableIntStateOf(initialIndex) }
    val currentRecord = remember(currentVideoIndex) { items.getOrNull(currentVideoIndex) }
    val hasPrev = currentVideoIndex > 0
    val hasNext = currentVideoIndex < items.size - 1
    val totalCount = items.size

    // ── playback state ──
    var isShowingControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf(false) }
    var isSwitchingVideo by remember { mutableStateOf(false) }

    // ── seekbar state ──
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableLongStateOf(0L) }

    // ── pinch-zoom state ──
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // ── cache state ──
    var cacheStarted by remember { mutableStateOf(false) }
    var cacheFinished by remember { mutableStateOf(false) }
    var cacheCached by remember { mutableIntStateOf(0) }
    var cacheTotal by remember { mutableIntStateOf(0) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var cachePollError by remember { mutableStateOf<String?>(null) }

    // ── orientation state ──
    var orientationMode by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── compute current playUrl ──
    val currentPlayUrl = remember(currentVideoIndex, currentRecord) {
        currentRecord?.let { record ->
            val url = record.videoUrl ?: ""
            if (record.isM3u8 && baseUrl.isNotBlank()) {
                val encodedUrl = Uri.encode(url)
                val encodedTitle = record.title?.let { "&title=" + Uri.encode(it) } ?: ""
                "${baseUrl}proxy/m3u8?url=$encodedUrl$encodedTitle"
            } else {
                url
            }
        } ?: ""
    }

    val currentTitle = currentRecord?.title ?: "视频"

    // ── DataSource + factories (stable) ──
    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
    }
    val mediaSourceFactory = remember { HlsMediaSource.Factory(dataSourceFactory) }

    // ── Player created once, reused across video switches ──
    val player = remember {
        if (currentPlayUrl.isBlank()) {
            playerError = true
            null
        } else {
            playerError = false
            ExoPlayer.Builder(context).build()
        }
    }

    // ── set initial media source ──
    LaunchedEffect(player, currentVideoIndex) {
        val p = player ?: run { playerError = true; return@LaunchedEffect }
        val url = currentPlayUrl
        if (url.isBlank()) { playerError = true; return@LaunchedEffect }

        isSwitchingVideo = true
        playerError = false
        currentPositionMs = 0
        bufferedPositionMs = 0
        isPlaying = false

        p.stop()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(currentTitle)
                    .build()
            )
            .build()
        val source = if (url.contains(".m3u8", ignoreCase = true) || url.contains("proxy/m3u8", ignoreCase = true)) {
            mediaSourceFactory.createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        isSwitchingVideo = false
    }

    // ── Player listener ──
    LaunchedEffect(player) {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: PlaybackException) {
                playerError = true
            }
        })
    }

    // ── Position polling ──
    LaunchedEffect(player) {
        while (true) {
            kotlinx.coroutines.delay(200)
            player?.let { p ->
                if (!isDragging && !isSwitchingVideo) {
                    currentPositionMs = p.currentPosition
                }
                bufferedPositionMs = p.bufferedPosition
                durationMs = p.duration
            }
        }
    }

    // ── Immersive mode on landscape ──
    LaunchedEffect(isLandscape) {
        val window = (context as? ComponentActivity)?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isLandscape) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── cache polling (5s) ──
    val currentRecordForCache = currentRecord
    LaunchedEffect(cacheStarted, currentVideoIndex) {
        if (!cacheStarted || currentRecordForCache == null) return@LaunchedEffect
        cachePollError = null
        while (true) {
            kotlinx.coroutines.delay(5000)
            try {
                val res = com.videocollect.app.api.RetrofitClient.getApiService().cacheStatus(
                    videoUrl = currentRecordForCache.videoUrl ?: "",
                    title = currentRecordForCache.title,
                    id = currentRecordForCache.id
                )
                if (res.isSuccess && res.data != null) {
                    val d = res.data
                    cacheTotal = d.total ?: 0
                    cacheCached = d.cached ?: 0
                    cacheBytes = d.cachedBytes ?: 0L
                    cacheFinished = d.finished == true
                    cachePollError = d.error
                    if (cacheFinished) break
                }
            } catch (_: Exception) {
                cachePollError = "轮询失败"
                break
            }
        }
    }

    // ── cleanup ──
    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    // ── helpers ──
    val cacheScope = rememberCoroutineScope()
    fun startCache(record: VideoRecord) {
        if (cacheStarted) return
        cacheScope.launch {
            try {
                com.videocollect.app.api.RetrofitClient.getApiService().cacheStart(
                    videoUrl = record.videoUrl ?: "",
                    title = record.title,
                    id = record.id
                )
                cacheStarted = true
            } catch (_: Exception) {
                cachePollError = "启动缓存失败"
            }
        }
    }

    fun seekTo(deltaMs: Long) {
        player?.let { p ->
            val target = (p.currentPosition + deltaMs).coerceIn(0L, p.duration.coerceAtLeast(0L))
            p.seekTo(target)
        }
    }

    fun goToIndex(index: Int) {
        if (index < 0 || index >= items.size) return
        scale = 1f; offsetX = 0f; offsetY = 0f
        cacheStarted = false; cacheFinished = false
        cacheCached = 0; cacheTotal = 0; cacheBytes = 0L; cachePollError = null
        currentVideoIndex = index
    }

    fun formatCacheSize(bytes: Long): String {
        if (bytes <= 0) return ""
        return if (bytes < 1024 * 1024) String.format("%.1f KB", bytes / 1024.0)
        else String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    fun cycleOrientation(activity: ComponentActivity) {
        orientationMode = when (orientationMode) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        activity.requestedOrientation = orientationMode
    }

    fun orientationLabel(): String = when (orientationMode) {
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT -> "竖屏"
        ActivityInfo.SCREEN_ORIENTATION_SENSOR -> "自动"
        else -> "横屏"
    }

    // ── display position (freezes while dragging) ──
    val displayPositionMs = if (isDragging) dragPositionMs else currentPositionMs
    val playedFraction = if (durationMs > 0) (displayPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // ── UI ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (playerError || currentPlayUrl.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("无法播放此视频", color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            // ── Video with pinch-to-zoom ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            val maxX = (size.width * (scale - 1)) / 2
                            val maxY = (size.height * (scale - 1)) / 2
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        }
                    }
            ) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            keepScreenOn = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── single-tap toggle controls + double-tap reset zoom ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { scale = 1f; offsetX = 0f; offsetY = 0f },
                            onTap = { isShowingControls = !isShowingControls }
                        )
                    }
            )

            // ── Controls overlay ──
            AnimatedVisibility(
                visible = isShowingControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background tap to hide controls
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { isShowingControls = false })
                            }
                    )

                    // ── Top bar with prev/next/list ──
                    Surface(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回",
                                    tint = Color.White, modifier = Modifier.size(26.dp))
                            }

                            if (totalCount > 1) {
                                IconButton(
                                    onClick = { goToIndex(currentVideoIndex - 1) },
                                    enabled = hasPrev,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一集",
                                        tint = if (hasPrev) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(22.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = currentTitle,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (totalCount > 1) {
                                    Text(
                                        text = "${currentVideoIndex + 1} / $totalCount",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (totalCount > 1) {
                                IconButton(
                                    onClick = { goToIndex(currentVideoIndex + 1) },
                                    enabled = hasNext,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "下一集",
                                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(22.dp))
                                }

                                TextButton(
                                    onClick = {
                                        (context as? ComponentActivity)?.setResult(PlayerActivity.RESULT_GO_TO_LIST)
                                        (context as? ComponentActivity)?.finish()
                                    },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("列表", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // ── Center play/pause ──
                    val centerPlayer = player
                    if (centerPlayer != null) {
                        IconButton(
                            onClick = {
                                if (centerPlayer.isPlaying) centerPlayer.pause() else centerPlayer.play()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // ── Bottom controls ──
                    Surface(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // ── Seek bar row ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration(displayPositionMs),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(42.dp)
                                )

                                Box(modifier = Modifier.weight(1f).height(32.dp)) {
                                    // Buffer background
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .align(Alignment.Center)
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                    )
                                    if (bufferedFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(bufferedFraction)
                                                .height(4.dp)
                                                .align(Alignment.CenterStart)
                                                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                                        )
                                    }
                                    // Seek slider
                                    Slider(
                                        value = playedFraction,
                                        onValueChange = { f ->
                                            isDragging = true
                                            dragPositionMs = (f * durationMs.coerceAtLeast(1)).toLong()
                                        },
                                        onValueChangeFinished = {
                                            isDragging = false
                                            player?.seekTo(dragPositionMs)
                                        },
                                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Blue500,
                                            inactiveTrackColor = Color.Transparent
                                        )
                                    )
                                }

                                Text(
                                    text = formatDuration(durationMs),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(42.dp)
                                )
                            }

                            // ── Cache progress row ──
                            val isM3u8 = currentRecord?.isM3u8 == true
                            if (isM3u8 && cacheStarted) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (cacheFinished) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = if (cacheFinished) StatusGreen else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (cacheFinished) "缓存完成" else "缓存中…",
                                        color = if (cacheFinished) StatusGreen else Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = formatCacheSize(cacheBytes),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                    if (cacheTotal > 0 && !cacheFinished) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$cacheCached/$cacheTotal 段",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (cachePollError != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(cachePollError ?: "", color = StatusRed, fontSize = 11.sp)
                                    }
                                }
                            }

                            // ── Action buttons row ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // Speed
                                Box {
                                    TextButton(
                                        onClick = { showSpeedMenu = !showSpeedMenu },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text(
                                            "${currentSpeed}x",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSpeedMenu,
                                        onDismissRequest = { showSpeedMenu = false },
                                        modifier = Modifier.background(SurfaceDark)
                                    ) {
                                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f).forEach { speed ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "${speed}x",
                                                        color = if (speed == currentSpeed) Blue400 else Color.White
                                                    )
                                                },
                                                onClick = {
                                                    currentSpeed = speed
                                                    player?.setPlaybackSpeed(speed)
                                                    showSpeedMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Skip back 10s
                                IconButton(
                                    onClick = { seekTo(-10000) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.Replay10, contentDescription = "后退10秒",
                                        tint = Color.White, modifier = Modifier.size(26.dp))
                                }

                                // Play/Pause
                                IconButton(
                                    onClick = {
                                        if (player?.isPlaying == true) player?.pause() else player?.play()
                                    },
                                    modifier = Modifier.size(52.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "暂停" else "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                // Skip forward 10s
                                IconButton(
                                    onClick = { seekTo(10000) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.Forward10, contentDescription = "快进10秒",
                                        tint = Color.White, modifier = Modifier.size(26.dp))
                                }

                                // Cache (m3u8 only)
                                if (isM3u8) {
                                    IconButton(
                                        onClick = {
                                            val r = currentRecord
                                            if (r != null) startCache(r)
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (cacheFinished) Icons.Default.CheckCircle
                                                else if (cacheStarted) Icons.Default.CloudDownload
                                                else Icons.Default.CloudDownload,
                                            contentDescription = "缓存",
                                            tint = if (cacheFinished) StatusGreen
                                                else if (cachePollError != null) StatusRed
                                                else Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Orientation toggle
                                Box {
                                    IconButton(
                                        onClick = { cycleOrientation(context as ComponentActivity) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.ScreenRotation, contentDescription = "旋转",
                                            tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Text(
                                        text = orientationLabel(),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-2).dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
