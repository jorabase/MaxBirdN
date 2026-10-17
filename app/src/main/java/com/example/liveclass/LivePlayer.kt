package com.example.liveclass

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "LivePlayer"

@OptIn(UnstableApi::class)
@Composable
fun LivePlayer(
    masterUrl: String,
    candidateUrls: List<String> = emptyList(),
    selectedResolution: String = "auto",
    isPipActive: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    videoScale: Float = 1.0f,
    videoPan: Offset = Offset.Zero,
    syncToLiveTrigger: Long = 0L,
    onLiveDelayChanged: ((seconds: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    isMuted: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeStreamUrl by remember(masterUrl) { mutableStateOf(masterUrl) }
    var currentUrlIndex by remember(masterUrl) { mutableIntStateOf(0) }
    val allUrls = remember(masterUrl, candidateUrls) {
        listOf(masterUrl).plus(candidateUrls).distinct().filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    var isEnded by remember(activeStreamUrl) { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember(activeStreamUrl) { mutableStateOf(true) }
    var isPlayerError by remember(activeStreamUrl) { mutableStateOf(false) }
    var playerErrorMessage by remember(activeStreamUrl) { mutableStateOf("") }
    var retryCount by remember(activeStreamUrl) { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Initialize ExoPlayer with resilient load control
    val exoPlayer = remember(activeStreamUrl) {
        com.example.player.ShikhoPlayerManager.buildExoPlayer(
            context = context,
            classType = com.example.player.PlayerClassType.LIVE
        ).apply {
            val isLikelyLive = activeStreamUrl.contains(".m3u8", ignoreCase = true) &&
                !activeStreamUrl.contains("recording", ignoreCase = true)

            val mediaSource = com.example.player.ShikhoPlayerManager.createMediaSource(
                url = activeStreamUrl,
                isLive = isLikelyLive,
                classType = com.example.player.PlayerClassType.LIVE,
                context = context
            )

            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
            volume = if (isMuted) 0f else 1f
            setPlaybackSpeed(playbackSpeed)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Stream playback reached STATE_ENDED.")
                        isEnded = true
                        isBuffering = false
                        isPlayerError = false
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        isPlayerError = false
                        retryCount = 0
                    }
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error: ${error.errorCodeName} (${error.errorCode}): ${error.message}", error)
                isBuffering = false

                // 1. Auto-recover from BehindLiveWindowException
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    error.cause is BehindLiveWindowException
                ) {
                    Log.d(TAG, "BehindLiveWindowException encountered; syncing to live window...")
                    if (retryCount < 2) {
                        retryCount++
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                        exoPlayer.play()
                        return
                    } else {
                        if (currentUrlIndex + 1 < allUrls.size) {
                            currentUrlIndex++
                            activeStreamUrl = allUrls[currentUrlIndex]
                            retryCount = 0
                            return
                        }
                        isEnded = true
                        isPlayerError = false
                        return
                    }
                }

                // 2. Natural completion check
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                val isNearVodEnd = dur > 0 && pos >= (dur - 3000L)
                val isLiveBroadcastEnded = pos > 2000L && (
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                        error.message?.contains("404", ignoreCase = true) == true
                    )

                if (isNearVodEnd || isLiveBroadcastEnded) {
                    if (currentUrlIndex + 1 < allUrls.size) {
                        currentUrlIndex++
                        activeStreamUrl = allUrls[currentUrlIndex]
                        retryCount = 0
                        return
                    }
                    isEnded = true
                    isPlayerError = false
                    return
                }

                // 3. Transient errors auto-retry
                if (retryCount < 3) {
                    retryCount++
                    coroutineScope.launch {
                        delay(1200L * retryCount)
                        try {
                            exoPlayer.seekToDefaultPosition()
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } catch (_: Exception) {
                        }
                    }
                    return
                }

                // 4. Switch candidate URL if available
                if (currentUrlIndex + 1 < allUrls.size) {
                    currentUrlIndex++
                    activeStreamUrl = allUrls[currentUrlIndex]
                    retryCount = 0
                    return
                }

                // 5. In-player error overlay
                isPlayerError = true
                val msg = when {
                    error.message?.contains("Source error", ignoreCase = true) == true ->
                        "লাইভ সম্প্রচারের সংযোগে সাময়িক বিলম্ব হচ্ছে।"
                    error.message?.contains("BehindLiveWindow", ignoreCase = true) == true ->
                        "লাইভ সম্প্রচার সমাপ্ত বা রিফ্রেশ প্রয়োজন।"
                    else -> "নেটওয়ার্ক বা স্ট্রিম সংযোগে সমস্যা হয়েছে।"
                }
                playerErrorMessage = msg
                onError?.invoke(msg)
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            try {
                exoPlayer.removeListener(listener)
                com.example.player.EqualizerManager.unbindPlayer(context)
                exoPlayer.stop()
                exoPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    // Apply mute state
    LaunchedEffect(isMuted, exoPlayer) {
        try {
            exoPlayer.volume = if (isMuted) 0f else 1f
        } catch (_: Exception) {
        }
    }

    // Apply playback speed changes
    LaunchedEffect(playbackSpeed, exoPlayer) {
        try {
            exoPlayer.setPlaybackSpeed(playbackSpeed)
        } catch (_: Exception) {
        }
    }

    // Apply resolution constraints
    LaunchedEffect(selectedResolution, exoPlayer) {
        try {
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            when (selectedResolution.lowercase()) {
                "1080p" -> builder.setMaxVideoSize(1920, 1080)
                "720p" -> builder.setMaxVideoSize(1280, 720)
                "480p" -> builder.setMaxVideoSize(854, 480)
                "360p" -> builder.setMaxVideoSize(640, 360)
                "240p" -> builder.setMaxVideoSize(426, 240)
                else -> builder.clearVideoSizeConstraints()
            }
            exoPlayer.trackSelectionParameters = builder.build()
        } catch (_: Exception) {
        }
    }

    // Connect PiP Controller
    com.example.util.SetupPipController(player = exoPlayer, isPlaying = isPlaying)

    // Sync to live edge trigger
    LaunchedEffect(syncToLiveTrigger) {
        if (syncToLiveTrigger > 0L) {
            try {
                if (exoPlayer.isCurrentMediaItemLive) {
                    val duration = exoPlayer.duration
                    if (duration != C.TIME_UNSET && duration > 0L) {
                        exoPlayer.seekTo(duration)
                    } else {
                        exoPlayer.seekToDefaultPosition()
                    }
                } else {
                    exoPlayer.seekToDefaultPosition()
                }
                exoPlayer.setPlaybackSpeed(1.0f)
                exoPlayer.playWhenReady = true
                exoPlayer.play()
                Toast.makeText(context, "লাইভ স্ট্রিমে সিঙ্ক করা হয়েছে ✨", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w(TAG, "Error seeking to live: ${e.message}")
            }
        }
    }

    // Monitor live delay offset periodically
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            try {
                val offsetMs = exoPlayer.currentLiveOffset
                val delaySec = if (offsetMs != C.TIME_UNSET && offsetMs > 0L) {
                    (offsetMs / 1000L).toInt().coerceAtLeast(0)
                } else {
                    val dur = exoPlayer.duration
                    val pos = exoPlayer.currentPosition
                    if (dur != C.TIME_UNSET && dur > pos && exoPlayer.isCurrentMediaItemLive) {
                        ((dur - pos) / 1000L).toInt().coerceAtLeast(0)
                    } else {
                        0
                    }
                }
                onLiveDelayChanged?.invoke(delaySec)
            } catch (_: Exception) {
            }
            delay(1000L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Core Video Surface with Zoom, Pan & Aspect Ratio
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = videoScale
                    scaleY = videoScale
                    translationX = videoPan.x
                    translationY = videoPan.y
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        this.resizeMode = resizeMode
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.useController = false
                    playerView.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Smooth Buffering Indicator Overlay
        if (isBuffering && !isEnded && !isPlayerError && !isPipActive) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            strokeWidth = 2.8.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Overlay 1: Broadcast Ended (Compact & Responsive for both 16:9 Portrait and Fullscreen Landscape)
        AnimatedVisibility(
            visible = isEnded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEB0B1120))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "লাইভ ক্লাস সম্প্রচার সমাপ্ত হয়েছে",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "আপনি ক্লাসটি পুনরায় প্রথম থেকে দেখতে পারেন",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                isEnded = false
                                isPlayerError = false
                                exoPlayer.seekTo(0)
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Replay, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পুনরায় দেখুন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        if (allUrls.size > 1 && currentUrlIndex + 1 < allUrls.size) {
                            Button(
                                onClick = {
                                    isEnded = false
                                    isPlayerError = false
                                    currentUrlIndex = (currentUrlIndex + 1) % allUrls.size
                                    activeStreamUrl = allUrls[currentUrlIndex]
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("রেকর্ডিং দেখুন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        if (onBack != null) {
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("ফিরে যান", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // Overlay 2: In-Player Error Recovery (Compact & Responsive)
        AnimatedVisibility(
            visible = isPlayerError && !isEnded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEB0B1120))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ভিডিও স্ট্রিম লোড হতে বিঘ্ন ঘটেছে",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playerErrorMessage,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                isPlayerError = false
                                retryCount = 0
                                exoPlayer.seekToDefaultPosition()
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পুনরায় চেষ্টা করুন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        if (allUrls.size > 1) {
                            OutlinedButton(
                                onClick = {
                                    isPlayerError = false
                                    currentUrlIndex = (currentUrlIndex + 1) % allUrls.size
                                    activeStreamUrl = allUrls[currentUrlIndex]
                                    retryCount = 0
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("বিকল্প স্ট্রিম", fontSize = 11.5.sp, color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }
            }
        }
    }
}
