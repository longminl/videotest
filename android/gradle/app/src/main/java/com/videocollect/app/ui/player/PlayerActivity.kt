package com.videocollect.app.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.videocollect.app.ui.theme.*

class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "video_url"
        private const val EXTRA_TITLE = "video_title"
        private const val EXTRA_IS_M3U8 = "is_m3u8"
        private const val EXTRA_BASE_URL = "base_url"

        fun intent(context: Context, videoUrl: String, title: String?, isM3u8: Boolean, baseUrl: String): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, videoUrl)
                putExtra(EXTRA_TITLE, title ?: "视频")
                putExtra(EXTRA_IS_M3U8, isM3u8)
                putExtra(EXTRA_BASE_URL, baseUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "视频"
        val isM3u8 = intent.getBooleanExtra(EXTRA_IS_M3U8, false)
        val baseUrl = intent.getStringExtra(EXTRA_BASE_URL) ?: ""

        val playUrl = if (isM3u8 && baseUrl.isNotBlank()) {
            val encodedUrl = Uri.encode(videoUrl)
            val encodedTitle = title?.let { "&title=" + Uri.encode(it) } ?: ""
            "${baseUrl}proxy/m3u8?url=$encodedUrl$encodedTitle"
        } else {
            videoUrl
        }

        setContent {
            VideoCollectTheme(darkTheme = true) {
                PlayerScreen(
                    playUrl = playUrl,
                    title = title,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun PlayerScreen(
    playUrl: String,
    title: String,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isShowingControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1f) }
    var playerError by remember { mutableStateOf(false) }

    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
    }

    val mediaSourceFactory = remember { HlsMediaSource.Factory(dataSourceFactory) }

    val player = remember(playUrl) {
        if (playUrl.isBlank()) {
            playerError = true
            null
        } else {
            playerError = false
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(playUrl)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title)
                            .build()
                    )
                    .build()
                val source: MediaSource = if (playUrl.contains(".m3u8", ignoreCase = true) || playUrl.contains("proxy/m3u8", ignoreCase = true)) {
                    mediaSourceFactory.createMediaSource(mediaItem)
                } else {
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                }
                setMediaSource(source)
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    // Track play state
    LaunchedEffect(player) {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playerError = true
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isShowingControls = !isShowingControls }
    ) {
        if (playerError || playUrl.isBlank()) {
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
            // Video view
            val mediaPlayer = player
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = mediaPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Controls overlay
            AnimatedVisibility(
                visible = isShowingControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top bar
                    Surface(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回",
                                    tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Center play/pause
                    val p = player
                    if (p != null) {
                        IconButton(
                            onClick = {
                                if (p.isPlaying) p.pause() else p.play()
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

                    // Bottom controls
                    Surface(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Speed button
                            Box {
                                IconButton(
                                    onClick = { showSpeedMenu = !showSpeedMenu },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Text(
                                        "${currentSpeed}x",
                                        color = Color.White,
                                        fontSize = 14.sp,
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
                                                p?.setPlaybackSpeed(speed)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Fullscreen toggle
                            IconButton(
                                onClick = { /* Already fullscreen */ },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.FullscreenExit,
                                    contentDescription = "退出全屏",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
