package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.LocalPictureInPictureMode
import com.example.R
import com.example.api.TopicFullItem
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.gamification.StudentGamificationManager
import com.example.player.EqualizerManager
import com.example.player.PlayerClassType
import com.example.player.ShikhoPlayerManager
import com.example.player.VideoProgressManager
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.VideoDownloadQualityDialog
import com.example.utils.PipHelper
import com.example.utils.SetupPipController
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

data class AnimatedPlaylistEntry(
    val id: String,
    val serialNo: String,
    val title: String,
    val description: String?,
    val videoUrl: String,
    val thumbnailUrl: String?
)

/**
 * Premium Gamified Animated Lesson Video Player with Seamless In-Player Playlist Switching:
 * - Instant in-place video switching from playlist (both while watching and after completion)
 * - Auto-Play Next with 5-second countdown & celebration XP overlay when video ends
 * - Split Portrait Mode (16:9 Player + Interactive Gamified Playlist below) & Fullscreen Landscape Mode
 * - Glassmorphic In-Player Playlist Drawer accessible anytime in Fullscreen or Portrait
 * - Previous / Next video quick-skip buttons in player controls
 * - In-app Video Download with real quality selector and live progress
 * - Offline auto-playback when file is stored locally
 * - Double-tap to seek (10s back & forward), Press & hold 2X turbo speed, Volume/Brightness swipe HUD
 * - Pinch-to-zoom & Pan, Picture-in-Picture (PiP), Screen Lock, Equalizer
 */
@OptIn(UnstableApi::class)
@Composable
fun AnimatedLessonPlayerScreen(
    videoUrl: String,
    title: String,
    subjectName: String = "",
    chapterName: String = "",
    playlist: List<TopicFullItem> = emptyList(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val isPipMode = LocalPictureInPictureMode.current

    // Active Video State (allows instant switching without navigating back!)
    var activeVideoUrl by remember(videoUrl) { mutableStateOf(videoUrl) }
    var activeTitle by remember(title) { mutableStateOf(title) }
    var watchedUrls by remember { mutableStateOf(StudentGamificationManager.getWatchedAnimatedUrls(context)) }

    // Build normalized playlist entries
    val playlistEntries = remember(playlist, activeVideoUrl, activeTitle) {
        val fromTopics = playlist.mapIndexedNotNull { index, topic ->
            val videoData = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }
            val pbUrl = videoData?.playback_url?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val thumb = videoData.video_thumbnail_url?.firstOrNull()
            val tName = topic.name ?: "অ্যানিমেটেড লেসন ${toBengaliDigits(index + 1)}"
            val sNo = topic.no?.let { toBengaliDigits(it) } ?: toBengaliDigits(index + 1)
            AnimatedPlaylistEntry(
                id = topic.id ?: "topic_$index",
                serialNo = sNo,
                title = tName,
                description = topic.description,
                videoUrl = pbUrl,
                thumbnailUrl = thumb
            )
        }
        if (fromTopics.isNotEmpty()) {
            fromTopics
        } else if (activeVideoUrl.isNotBlank()) {
            listOf(
                AnimatedPlaylistEntry(
                    id = "current_0",
                    serialNo = "১",
                    title = activeTitle.ifBlank { "অ্যানিমেটেড লেসন" },
                    description = null,
                    videoUrl = activeVideoUrl,
                    thumbnailUrl = null
                )
            )
        } else {
            emptyList()
        }
    }

    val currentPlaylistIndex = remember(playlistEntries, activeVideoUrl, activeTitle) {
        val byUrl = playlistEntries.indexOfFirst { it.videoUrl == activeVideoUrl }
        if (byUrl >= 0) byUrl else playlistEntries.indexOfFirst { it.title == activeTitle }.coerceAtLeast(0)
    }

    val prevEntry = remember(playlistEntries, currentPlaylistIndex) {
        if (currentPlaylistIndex > 0) playlistEntries.getOrNull(currentPlaylistIndex - 1) else null
    }
    val nextEntry = remember(playlistEntries, currentPlaylistIndex) {
        if (currentPlaylistIndex >= 0 && currentPlaylistIndex < playlistEntries.lastIndex) {
            playlistEntries.getOrNull(currentPlaylistIndex + 1)
        } else null
    }

    // Record Gamification XP when active video starts
    LaunchedEffect(activeVideoUrl) {
        if (activeVideoUrl.isNotBlank()) {
            StudentGamificationManager.recordAnimatedVideoWatched(context, activeVideoUrl)
            watchedUrls = StudentGamificationManager.getWatchedAnimatedUrls(context)
        }
    }

    // Audio & Screen Brightness Managers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    // -------------------------------------------------------------
    // Download Manager & Offline Resolution (tracks activeVideoUrl)
    // -------------------------------------------------------------
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val downloadId = remember(activeVideoUrl, activeTitle) {
        "anim_vid_" + (activeVideoUrl.hashCode().toString() + "_" + activeTitle.hashCode().toString()).replace("-", "n")
    }
    val downloadedItem by downloadManager.getDownloadedItemById(downloadId).collectAsState(initial = null)
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }

    val effectivePlaybackUrl = remember(activeVideoUrl, downloadedItem) {
        val completedLocal = if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) {
            val file = File(downloadedItem!!.localFilePath)
            if (file.exists() && file.length() > 0) file.absolutePath else null
        } else null

        when {
            completedLocal != null -> completedLocal
            activeVideoUrl.isNotBlank() && activeVideoUrl != "null" -> activeVideoUrl
            else -> ""
        }
    }

    val isPlayingOffline = remember(effectivePlaybackUrl) {
        effectivePlaybackUrl.startsWith("/") || effectivePlaybackUrl.startsWith("file://")
    }

    // -------------------------------------------------------------
    // UI & Player State
    // -------------------------------------------------------------
    var isFullscreen by remember {
        mutableStateOf(configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isVideoEnded by remember { mutableStateOf(false) }
    var isAutoPlayEnabled by remember { mutableStateOf(true) }
    var autoPlayCountdown by remember { mutableIntStateOf(-1) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showUnlockPill by remember { mutableStateOf(false) }

    // Speed & Aspect Ratio
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var originalSpeedBeforeHold by remember { mutableFloatStateOf(1.0f) }
    var is2xHoldActive by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEqualizerBottomSheet by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Mute State
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(1f) }

    // Gestures HUD (Brightness, Volume, Seek feedback)
    var gestureBrightness by remember { mutableFloatStateOf(-1f) }
    var gestureVolume by remember { mutableIntStateOf(-1) }
    var isGestureHudVisible by remember { mutableStateOf(false) }
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) }
    var doubleTapTriggerKey by remember { mutableIntStateOf(0) }

    // Pinch-to-zoom & Pan
    var videoScale by remember { mutableFloatStateOf(1.0f) }
    var videoPan by remember { mutableStateOf(Offset.Zero) }

    // Resume video playback notification
    var resumeNotificationText by remember { mutableStateOf<String?>(null) }
    var hasAutoResumed by remember { mutableStateOf(false) }
    var lastPlaybackPositionMs by remember { mutableLongStateOf(0L) }

    // -------------------------------------------------------------
    // Single Persistent ExoPlayer Instance for Seamless Video Switching
    // -------------------------------------------------------------
    val exoPlayer = remember(context) {
        ShikhoPlayerManager.buildExoPlayer(
            context = context,
            classType = PlayerClassType.ANIMATED
        )
    }

    // Load media source whenever effectivePlaybackUrl changes
    LaunchedEffect(effectivePlaybackUrl) {
        if (effectivePlaybackUrl.isNotBlank()) {
            isBuffering = true
            isVideoEnded = false
            autoPlayCountdown = -1
            playbackError = null
            videoScale = 1.0f
            videoPan = Offset.Zero
            val mediaSource = ShikhoPlayerManager.createMediaSource(
                url = effectivePlaybackUrl,
                isLive = false,
                classType = PlayerClassType.ANIMATED,
                context = context
            )
            exoPlayer.setMediaSource(mediaSource)
            if (lastPlaybackPositionMs > 1000L) {
                exoPlayer.seekTo(lastPlaybackPositionMs)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
        }
    }

    // Helper function to switch to another animated video in place
    fun switchToVideo(entry: AnimatedPlaylistEntry) {
        if (entry.videoUrl == activeVideoUrl && !isVideoEnded) {
            showPlaylistSheet = false
            return
        }
        lastPlaybackPositionMs = 0L
        currentPosition = 0L
        totalDuration = 0L
        bufferedPosition = 0L
        hasAutoResumed = false
        resumeNotificationText = null
        isVideoEnded = false
        autoPlayCountdown = -1
        showPlaylistSheet = false
        activeTitle = entry.title
        activeVideoUrl = entry.videoUrl
    }

    // Connect PiP Controller
    SetupPipController(player = exoPlayer, isPlaying = isPlaying)

    // Fullscreen Mode Controller
    fun setFullscreenMode(enable: Boolean) {
        isFullscreen = enable
        val act = activity ?: return
        val window = act.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (enable) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Auto sync orientation change
    LaunchedEffect(configuration.orientation) {
        isFullscreen = (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    // ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isVideoEnded = false
                    autoPlayCountdown = -1
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        playbackError = null
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        isVideoEnded = false
                        totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                        playbackError = null

                        // Check & Auto-resume from previous progress if available
                        if (!hasAutoResumed && totalDuration > 10000L) {
                            hasAutoResumed = true
                            coroutineScope.launch {
                                val progressMgr = VideoProgressManager.getInstance(context)
                                val key = progressMgr.generateVideoKey(null, activeVideoUrl, activeTitle)
                                val saved = progressMgr.getProgress(key)
                                if (saved != null && saved.positionMs > 3000L && saved.positionMs < totalDuration - 5000L) {
                                    exoPlayer.seekTo(saved.positionMs)
                                    currentPosition = saved.positionMs
                                    resumeNotificationText = "পূর্বের অবস্থান (${formatTime(saved.positionMs)}) থেকে শুরু হয়েছে"
                                }
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isPlaying = false
                        isVideoEnded = true
                        areControlsVisible = true
                        StudentGamificationManager.recordAnimatedVideoWatched(context, activeVideoUrl)
                        watchedUrls = StudentGamificationManager.getWatchedAnimatedUrls(context)
                        coroutineScope.launch {
                            val progressMgr = VideoProgressManager.getInstance(context)
                            val key = progressMgr.generateVideoKey(null, activeVideoUrl, activeTitle)
                            progressMgr.saveProgress(
                                videoKey = key,
                                title = activeTitle,
                                subjectName = subjectName,
                                positionMs = 0L,
                                durationMs = totalDuration
                            )
                        }
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = "ভিডিও লোড হতে সমস্যা হয়েছে: ${error.localizedMessage ?: "নেটওয়ার্ক কানেকশন পরীক্ষা করুন"}"
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            EqualizerManager.unbindPlayer(context)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = act.window
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-play countdown when video ends and nextEntry is available
    LaunchedEffect(isVideoEnded, isAutoPlayEnabled, nextEntry) {
        if (isVideoEnded && isAutoPlayEnabled && nextEntry != null) {
            autoPlayCountdown = 5
            while (autoPlayCountdown > 0 && isVideoEnded && isAutoPlayEnabled) {
                delay(1000)
                autoPlayCountdown--
            }
            if (autoPlayCountdown == 0 && isVideoEnded && isAutoPlayEnabled) {
                switchToVideo(nextEntry)
            }
        } else {
            autoPlayCountdown = -1
        }
    }

    // Keep screen awake during playback
    LaunchedEffect(isPlaying) {
        val act = activity ?: (context as? Activity)
        if (isPlaying) {
            act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-reconnect & resume on network restoration
    DisposableEffect(context, effectivePlaybackUrl) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                coroutineScope.launch {
                    if (playbackError != null || (!exoPlayer.isPlaying && isBuffering)) {
                        playbackError = null
                        isBuffering = true
                        try {
                            if (effectivePlaybackUrl.isNotBlank()) {
                                val mediaSource = ShikhoPlayerManager.createMediaSource(
                                    url = effectivePlaybackUrl,
                                    isLive = false,
                                    classType = PlayerClassType.ANIMATED,
                                    context = context
                                )
                                exoPlayer.setMediaSource(mediaSource)
                                if (lastPlaybackPositionMs > 1000L) {
                                    exoPlayer.seekTo(lastPlaybackPositionMs)
                                }
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                                Toast.makeText(context, "ইন্টারনেট পুনরায় সংযুক্ত হয়েছে, ক্লাস চালু হচ্ছে...", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (_: Throwable) {}

        onDispose {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (_: Throwable) {}
        }
    }

    // Save Video Progress periodically
    LaunchedEffect(exoPlayer, isPlaying, activeVideoUrl) {
        val progressMgr = VideoProgressManager.getInstance(context)
        val key = progressMgr.generateVideoKey(null, activeVideoUrl, activeTitle)

        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPosition = pos
                if (pos > 1000L) {
                    lastPlaybackPositionMs = pos
                }
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)

                if (currentPosition > 2000L && totalDuration > 0L && currentPosition < totalDuration - 4000L) {
                    progressMgr.saveProgress(
                        videoKey = key,
                        title = activeTitle,
                        subjectName = subjectName,
                        positionMs = currentPosition,
                        durationMs = totalDuration
                    )
                }
            }
            delay(1000)
        }
    }

    // Auto-hide controls
    LaunchedEffect(areControlsVisible, isPlaying, isScreenLocked, isVideoEnded) {
        if (areControlsVisible && isPlaying && !isScreenLocked && !isVideoEnded) {
            delay(4500)
            areControlsVisible = false
        }
    }

    // Auto-hide double tap feedback
    LaunchedEffect(doubleTapTriggerKey) {
        if (doubleTapFeedbackSide != null) {
            delay(850)
            doubleTapFeedbackSide = null
        }
    }

    // Auto-hide unlock pill
    LaunchedEffect(showUnlockPill) {
        if (showUnlockPill) {
            delay(2800)
            showUnlockPill = false
        }
    }

    // Auto-hide gesture HUD
    LaunchedEffect(isGestureHudVisible) {
        if (isGestureHudVisible) {
            delay(1500)
            isGestureHudVisible = false
        }
    }

    // Auto-dismiss resume banner
    LaunchedEffect(resumeNotificationText) {
        if (resumeNotificationText != null) {
            delay(4000)
            resumeNotificationText = null
        }
    }

    BackHandler {
        when {
            showPlaylistSheet -> showPlaylistSheet = false
            isScreenLocked -> showUnlockPill = true
            isFullscreen -> setFullscreenMode(false)
            else -> onBack()
        }
    }

    // -------------------------------------------------------------
    // Compact Picture-In-Picture View Mode
    // -------------------------------------------------------------
    if (isPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        this.useController = false
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val useSplitPortraitLayout = !isFullscreen && playlistEntries.size > 1

    // -------------------------------------------------------------
    // Main Screen Container (Supports Split Portrait + Fullscreen)
    // -------------------------------------------------------------
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070D19))
            .testTag("animated_lesson_player_screen")
    ) {
        // =============================================================
        // TOP / FULL VIDEO PLAYER SURFACE
        // =============================================================
        Box(
            modifier = if (useSplitPortraitLayout) {
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            } else {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            }
        ) {
            // 1. ExoPlayer Video Surface with Pinch-to-Zoom & Pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (videoScale * zoom).coerceIn(1.0f, 3.5f)
                            videoScale = newScale
                            if (newScale > 1.0f) {
                                videoPan += pan
                            } else {
                                videoPan = Offset.Zero
                            }
                        }
                    }
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
                            this.player = exoPlayer
                            this.useController = false
                            this.resizeMode = resizeMode
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { pv ->
                        pv.player = exoPlayer
                        pv.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Gesture Detector Layer (Tap, Double-tap, 2X Hold, Swipe Brightness & Volume)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isScreenLocked, isPlaying, totalDuration, videoScale) {
                        detectTapGestures(
                            onPress = {
                                var is2xTriggered = false
                                val holdJob = coroutineScope.launch {
                                    delay(400)
                                    if (isPlaying && !isScreenLocked) {
                                        is2xTriggered = true
                                        is2xHoldActive = true
                                        originalSpeedBeforeHold = playbackSpeed
                                        exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                                    }
                                }
                                tryAwaitRelease()
                                holdJob.cancel()
                                if (is2xTriggered) {
                                    is2xHoldActive = false
                                    exoPlayer.playbackParameters = PlaybackParameters(originalSpeedBeforeHold)
                                }
                            },
                            onTap = {
                                if (isScreenLocked) {
                                    showUnlockPill = true
                                } else {
                                    areControlsVisible = !areControlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                if (!isScreenLocked) {
                                    val screenWidth = size.width
                                    if (offset.x < screenWidth * 0.35f) {
                                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                        currentPosition = target
                                        doubleTapFeedbackSide = "LEFT"
                                        doubleTapTriggerKey++
                                    } else if (offset.x > screenWidth * 0.65f) {
                                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                                        exoPlayer.seekTo(target)
                                        currentPosition = target
                                        doubleTapFeedbackSide = "RIGHT"
                                        doubleTapTriggerKey++
                                    } else {
                                        if (videoScale > 1.05f) {
                                            videoScale = 1.0f
                                            videoPan = Offset.Zero
                                        } else {
                                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(isScreenLocked, maxVolume) {
                        if (isScreenLocked) return@pointerInput
                        val act = context as? Activity ?: (context as? ContextWrapper)?.baseContext as? Activity
                        var isDragOnLeft = false
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragOnLeft = offset.x < size.width * 0.45f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (isDragOnLeft) {
                                    val window = act?.window
                                    val currentAttr = window?.attributes
                                    var currentB = currentAttr?.screenBrightness ?: 0.5f
                                    if (currentB < 0f) currentB = 0.5f
                                    val delta = -dragAmount / 400f
                                    val newB = (currentB + delta).coerceIn(0.01f, 1.0f)
                                    currentAttr?.screenBrightness = newB
                                    window?.attributes = currentAttr
                                    gestureBrightness = newB
                                    gestureVolume = -1
                                    isGestureHudVisible = true
                                } else {
                                    val maxV = maxVolume.coerceAtLeast(1)
                                    val currentV = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxV / 2)
                                    val delta = if (dragAmount < -15f) 1 else if (dragAmount > 15f) -1 else 0
                                    if (delta != 0) {
                                        val newV = (currentV + delta).coerceIn(0, maxV)
                                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                        gestureVolume = newV
                                        gestureBrightness = -1f
                                        isGestureHudVisible = true
                                    }
                                }
                            },
                            onDragEnd = {
                                coroutineScope.launch {
                                    delay(1200)
                                    isGestureHudVisible = false
                                }
                            }
                        )
                    }
            )

            // 2.5 Gesture HUD Overlay (Brightness & Volume)
            androidx.compose.animation.AnimatedVisibility(
                visible = isGestureHudVisible && !isScreenLocked,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.90f),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.25f)),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        if (gestureBrightness >= 0f) {
                            val percent = (gestureBrightness * 100).toInt()
                            Icon(
                                imageVector = when {
                                    gestureBrightness > 0.6f -> Icons.Default.BrightnessHigh
                                    gestureBrightness > 0.25f -> Icons.Default.BrightnessMedium
                                    else -> Icons.Default.BrightnessLow
                                },
                                contentDescription = null,
                                tint = Color(0xFFFDE047),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "উজ্জ্বলতা ${toBengaliDigits(percent.toString())}%",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(gestureBrightness.coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFFDE047))
                                )
                            }
                        } else if (gestureVolume >= 0) {
                            val maxV = maxVolume.coerceAtLeast(1)
                            val fraction = (gestureVolume.toFloat() / maxV).coerceIn(0f, 1f)
                            val percent = (fraction * 100).toInt()
                            Icon(
                                imageVector = when {
                                    gestureVolume == 0 -> Icons.Default.VolumeMute
                                    gestureVolume > maxV / 2 -> Icons.Default.VolumeUp
                                    else -> Icons.Default.VolumeDown
                                },
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ভলিউম ${toBengaliDigits(percent.toString())}%",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF60A5FA))
                                )
                            }
                        }
                    }
                }
            }

            // 3. Double-Tap Seek Visual Feedback
            if (doubleTapFeedbackSide != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = if (doubleTapFeedbackSide == "LEFT") Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            if (doubleTapFeedbackSide == "LEFT") {
                                Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("১০ সে. পেছনে", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            } else {
                                Text("১০ সে. সামনে", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }

            // 4. 2X Turbo Speed Pill Indicator
            androidx.compose.animation.AnimatedVisibility(
                visible = is2xHoldActive,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF7C3AED).copy(alpha = 0.95f),
                    border = BorderStroke(1.2.dp, Color(0xFFA78BFA)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFDE047), modifier = Modifier.size(18.dp))
                        Text("২X স্পিডে চলছে...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }

            // 5. Screen Lock Status / Unlock Button Overlay
            if (isScreenLocked) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showUnlockPill,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.92f),
                        border = BorderStroke(1.2.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.clickable {
                            isScreenLocked = false
                            areControlsVisible = true
                            Toast.makeText(context, "স্ক্রিন আনলক হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                            Text("পর্দা লক করা আছে • আনলক করতে ট্যাপ করুন", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 6. Resume Video Playback Alert Banner
            androidx.compose.animation.AnimatedVisibility(
                visible = resumeNotificationText != null && areControlsVisible && !isVideoEnded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.7f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                        Text(
                            text = resumeNotificationText ?: "",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(
                            onClick = {
                                exoPlayer.seekTo(0L)
                                currentPosition = 0L
                                resumeNotificationText = null
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("শুরু থেকে", color = Color(0xFFA78BFA), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 7. Loading / Buffering Indicator
            if (isBuffering && playbackError == null && !isVideoEnded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF8B5CF6),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isPlayingOffline) "অফলাইন অ্যানিমেশন প্রস্তুত হচ্ছে..." else "লেসন লোড হচ্ছে...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 8. Playback Error Overlay
            if (playbackError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = playbackError ?: "ভিডিও প্লে করা যাচ্ছে না",
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                playbackError = null
                                val mediaSource = ShikhoPlayerManager.createMediaSource(
                                    url = effectivePlaybackUrl,
                                    isLive = false,
                                    classType = PlayerClassType.ANIMATED,
                                    context = context
                                )
                                exoPlayer.setMediaSource(mediaSource)
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুনরায় চেষ্টা করুন", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }

            // 9. Controls Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = areControlsVisible && !isScreenLocked && !isVideoEnded,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.78f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    // ==================== TOP BAR ====================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!useSplitPortraitLayout) Modifier.statusBarsPadding().displayCutoutPadding() else Modifier)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back Button
                        IconButton(
                            onClick = {
                                if (isFullscreen) {
                                    setFullscreenMode(false)
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Lesson Title & Subtitle
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activeTitle.ifBlank { "অ্যানিমেটেড লেসন" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isPlayingOffline) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.25f),
                                        border = BorderStroke(0.6.dp, Color(0xFF10B981))
                                    ) {
                                        Text(
                                            text = "অফলাইন",
                                            color = Color(0xFF34D399),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            val subInfo = listOfNotNull(
                                subjectName.takeIf { it.isNotBlank() },
                                chapterName.takeIf { it.isNotBlank() },
                                if (playlistEntries.size > 1) "লেসন ${toBengaliDigits(currentPlaylistIndex + 1)}/${toBengaliDigits(playlistEntries.size)}" else "অ্যানিমেটেড ক্লাস"
                            ).joinToString(" • ")

                            Text(
                                text = subInfo,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Playlist Drawer Toggle Button (Always accessible when playlist > 1)
                        if (playlistEntries.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.8f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showPlaylistSheet = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = "প্লেলিস্ট",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "ভিডিও (${toBengaliDigits(playlistEntries.size)})",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Picture-in-Picture Button
                        IconButton(
                            onClick = {
                                val act = activity ?: (context as? Activity)
                                PipHelper.enterPipMode(act, isPlaying, Rational(16, 9))
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Aspect Ratio Toggle
                        if (isFullscreen || !useSplitPortraitLayout) {
                            IconButton(
                                onClick = {
                                    val nextMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    resizeMode = nextMode
                                    val label = when (nextMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ফিট মোড (Fit)"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ক্রপ/জুম মোড (Zoom)"
                                        else -> "ফুলস্ক্রিন স্ট্রেচ (Fill)"
                                    }
                                    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Equalizer Button
                        IconButton(
                            onClick = { showEqualizerBottomSheet = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "ইকুলাইজার",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Download Status Button
                        when (downloadedItem?.status) {
                            DownloadedItemEntity.STATUS_DOWNLOADING -> {
                                val item = downloadedItem!!
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.35f),
                                    border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                    modifier = Modifier.clickable {
                                        Toast.makeText(context, "ডাউনলোড চলছে: ${item.progressPercent}%", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { item.progressFraction },
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${item.progressPercent}%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }
                            }
                            DownloadedItemEntity.STATUS_COMPLETED -> {
                                IconButton(
                                    onClick = { showDeleteDownloadDialog = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadDone,
                                        contentDescription = "সেভড",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            else -> {
                                IconButton(
                                    onClick = { showDownloadQualityDialog = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "ডাউনলোড",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ==================== SCREEN LOCK BUTTON ====================
                    IconButton(
                        onClick = {
                            isScreenLocked = true
                            areControlsVisible = false
                            Toast.makeText(context, "পর্দা লক করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "পর্দা লক করুন",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // ==================== CENTER PLAY/PAUSE, SEEK & PREV/NEXT ====================
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (useSplitPortraitLayout) 18.dp else 26.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Video in Playlist
                        if (playlistEntries.size > 1) {
                            IconButton(
                                onClick = { prevEntry?.let { switchToVideo(it) } },
                                enabled = prevEntry != null,
                                modifier = Modifier
                                    .size(if (useSplitPortraitLayout) 38.dp else 44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (prevEntry != null) 0.16f else 0.06f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "পূর্ববর্তী ভিডিও",
                                    tint = Color.White.copy(alpha = if (prevEntry != null) 1f else 0.35f),
                                    modifier = Modifier.size(if (useSplitPortraitLayout) 22.dp else 26.dp)
                                )
                            }
                        }

                        // Rewind 10s Button
                        IconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                doubleTapFeedbackSide = "LEFT"
                                doubleTapTriggerKey++
                            },
                            modifier = Modifier
                                .size(if (useSplitPortraitLayout) 44.dp else 50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Replay 10s",
                                tint = Color.White,
                                modifier = Modifier.size(if (useSplitPortraitLayout) 24.dp else 28.dp)
                            )
                        }

                        // Main Play / Pause Button with Glow
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF8B5CF6),
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .size(if (useSplitPortraitLayout) 58.dp else 68.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (useSplitPortraitLayout) 34.dp else 40.dp)
                                )
                            }
                        }

                        // Forward 10s Button
                        IconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                doubleTapFeedbackSide = "RIGHT"
                                doubleTapTriggerKey++
                            },
                            modifier = Modifier
                                .size(if (useSplitPortraitLayout) 44.dp else 50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(if (useSplitPortraitLayout) 24.dp else 28.dp)
                            )
                        }

                        // Next Video in Playlist
                        if (playlistEntries.size > 1) {
                            IconButton(
                                onClick = { nextEntry?.let { switchToVideo(it) } },
                                enabled = nextEntry != null,
                                modifier = Modifier
                                    .size(if (useSplitPortraitLayout) 38.dp else 44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (nextEntry != null) 0.16f else 0.06f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "পরবর্তী ভিডিও",
                                    tint = Color.White.copy(alpha = if (nextEntry != null) 1f else 0.35f),
                                    modifier = Modifier.size(if (useSplitPortraitLayout) 22.dp else 26.dp)
                                )
                            }
                        }
                    }

                    // ==================== BOTTOM TIMELINE CONTROLS ====================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .then(if (!useSplitPortraitLayout) Modifier.navigationBarsPadding().displayCutoutPadding() else Modifier)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "/",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = formatTime(totalDuration),
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Quick Mute / Unmute
                                IconButton(
                                    onClick = {
                                        if (isMuted) {
                                            exoPlayer.volume = previousVolume.coerceAtLeast(0.2f)
                                            isMuted = false
                                        } else {
                                            previousVolume = exoPlayer.volume
                                            exoPlayer.volume = 0f
                                            isMuted = true
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = if (isMuted) "আনমিউট করুন" else "মিউট করুন",
                                        tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Speed Chip
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.22f),
                                    border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showSpeedDialog = true }
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }

                                // Fullscreen Toggle
                                IconButton(
                                    onClick = { setFullscreenMode(!isFullscreen) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Progress Slider with Buffer Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val bufferedFraction = if (totalDuration > 0) {
                                (bufferedPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bufferedFraction)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.45f))
                                )
                            }

                            Slider(
                                value = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f,
                                onValueChange = { frac ->
                                    val target = (frac * totalDuration).toLong()
                                    currentPosition = target
                                    exoPlayer.seekTo(target)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFA78BFA),
                                    activeTrackColor = Color(0xFF8B5CF6),
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // =============================================================
            // 9.5 GAMIFIED VIDEO ENDED & UP-NEXT AUTO-PLAY OVERLAY
            // =============================================================
            androidx.compose.animation.AnimatedVisibility(
                visible = isVideoEnded,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.95f),
                exit = fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF070D19).copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Lesson Complete Celebration Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.7f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                Text(
                                    text = "লেসন সম্পন্ন হয়েছে! 🎉",
                                    color = Color(0xFFD1FAE5),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (nextEntry != null) {
                            Text(
                                text = if (autoPlayCountdown > 0 && isAutoPlayEnabled) {
                                    "পরবর্তী লেসন শুরু হচ্ছে ${toBengaliDigits(autoPlayCountdown)} সেকেন্ডে..."
                                } else {
                                    "পরবর্তী অ্যানিমেটেড লেসন:"
                                },
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = nextEntry.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Replay Button
                                OutlinedButton(
                                    onClick = {
                                        isVideoEnded = false
                                        autoPlayCountdown = -1
                                        exoPlayer.seekTo(0L)
                                        exoPlayer.playWhenReady = true
                                        exoPlayer.play()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("পুনরায় দেখুন", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Play Next Immediately Button
                                Button(
                                    onClick = { switchToVideo(nextEntry) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("পরবর্তী ভিডিও চালান", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Cancel Auto-play or Open Playlist
                                if (autoPlayCountdown > 0 && isAutoPlayEnabled) {
                                    TextButton(
                                        onClick = { autoPlayCountdown = -1; isAutoPlayEnabled = false }
                                    ) {
                                        Text("থামান", color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else if (playlistEntries.size > 1) {
                                    OutlinedButton(
                                        onClick = { showPlaylistSheet = true },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFA78BFA)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("সব ভিডিও", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = activeTitle.ifBlank { "অ্যানিমেটেড লেসন" },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        isVideoEnded = false
                                        exoPlayer.seekTo(0L)
                                        exoPlayer.playWhenReady = true
                                        exoPlayer.play()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("পুনরায় দেখুন", fontWeight = FontWeight.Bold)
                                }

                                if (playlistEntries.size > 1) {
                                    OutlinedButton(
                                        onClick = { showPlaylistSheet = true },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFA78BFA))
                                    ) {
                                        Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("অন্য ভিডিও বাছুন", color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // In Fullscreen mode, show horizontal quick-picker strip of other videos right inside the ended overlay
                        if (isFullscreen && playlistEntries.size > 1) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(playlistEntries) { idx, item ->
                                    val isCurrent = idx == currentPlaylistIndex
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCurrent) Color(0xFF8B5CF6).copy(alpha = 0.3f) else Color(0xFF1E293B).copy(alpha = 0.85f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) Color(0xFFA78BFA) else Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .width(200.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { switchToVideo(item) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isCurrent) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.12f),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = item.serialNo,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isCurrent) "বর্তমান ভিডিও" else "ট্যাপ করে দেখুন",
                                                    color = Color(0xFFC4B5FD),
                                                    fontSize = 10.sp
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

            // =============================================================
            // 9.8 IN-PLAYER GLASSMORPHIC PLAYLIST OVERLAY DRAWER
            // Works in both Fullscreen Landscape and Portrait!
            // =============================================================
            androidx.compose.animation.AnimatedVisibility(
                visible = showPlaylistSheet && playlistEntries.size > 1,
                enter = fadeIn(tween(180)) + slideInHorizontally(initialOffsetX = { it / 2 }),
                exit = fadeOut(tween(150)) + slideOutHorizontally(targetOffsetX = { it / 2 }),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { showPlaylistSheet = false },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.97f),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = 280.dp, max = 380.dp)
                            .fillMaxWidth(0.82f)
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (!useSplitPortraitLayout) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "অ্যানিমেটেড লেসন প্লেলিস্ট",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "মোট ${toBengaliDigits(playlistEntries.size)}টি ভিডিও • যেকোনোটিতে ট্যাপ করুন",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showPlaylistSheet = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন", tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(playlistEntries) { idx, entry ->
                                    val isCurrent = idx == currentPlaylistIndex
                                    val isWatched = watchedUrls.contains(entry.videoUrl)

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isCurrent) Color(0xFF8B5CF6).copy(alpha = 0.24f) else Color(0xFF1E293B),
                                        border = BorderStroke(
                                            width = if (isCurrent) 1.5.dp else 1.dp,
                                            color = if (isCurrent) Color(0xFFA78BFA) else Color.White.copy(alpha = 0.08f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { switchToVideo(entry) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(78.dp)
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0F172A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(entry.thumbnailUrl?.takeIf { it.isNotBlank() } ?: R.drawable.placeholder_animated)
                                                        .crossfade(true)
                                                        .error(R.drawable.placeholder_animated)
                                                        .placeholder(R.drawable.placeholder_animated)
                                                        .build(),
                                                    contentDescription = entry.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.35f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrent) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "লেসন ${entry.serialNo}",
                                                        color = Color(0xFFA78BFA),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (isCurrent) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = Color(0xFF8B5CF6)
                                                        ) {
                                                            Text(
                                                                text = "চলছে",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    } else if (isWatched) {
                                                        Text(
                                                            text = "✓ দেখা হয়েছে",
                                                            color = Color(0xFF34D399),
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = entry.title,
                                                    color = Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
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
        }

        // =============================================================
        // BOTTOM GAMIFIED PLAYLIST PANEL (IN PORTRAIT MODE)
        // Gives 1-tap access to all other animated videos without pressing Back!
        // =============================================================
        if (useSplitPortraitLayout) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0B1326), Color(0xFF070D19))
                        )
                    )
                    .navigationBarsPadding()
            ) {
                // Active Video Info & Gamified Controls Bar
                Surface(
                    color = Color(0xFF111C35),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "লেসন ${toBengaliDigits(currentPlaylistIndex + 1)}/${toBengaliDigits(playlistEntries.size)}",
                                        color = Color(0xFFC4B5FD),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }



                            // Auto-Play Toggle Chip
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isAutoPlayEnabled) Color(0xFF10B981).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isAutoPlayEnabled) Color(0xFF34D399).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { isAutoPlayEnabled = !isAutoPlayEnabled }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAutoPlayEnabled) Icons.Default.Autorenew else Icons.Default.PauseCircleOutline,
                                        contentDescription = null,
                                        tint = if (isAutoPlayEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isAutoPlayEnabled) "অটো-প্লে অন" else "অটো-প্লে অফ",
                                        color = if (isAutoPlayEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = activeTitle.ifBlank { "অ্যানিমেটেড লেসন" },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Previous / Next / Fullscreen Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { prevEntry?.let { switchToVideo(it) } },
                                enabled = prevEntry != null,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (prevEntry != null) Color(0xFFA78BFA).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = null,
                                    tint = if (prevEntry != null) Color(0xFFC4B5FD) else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "পূর্ববর্তী",
                                    color = if (prevEntry != null) Color(0xFFC4B5FD) else Color.White.copy(alpha = 0.3f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { nextEntry?.let { switchToVideo(it) } },
                                enabled = nextEntry != null,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8B5CF6),
                                    disabledContainerColor = Color.White.copy(alpha = 0.08f)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "পরবর্তী ভিডিও",
                                    color = if (nextEntry != null) Color.White else Color.White.copy(alpha = 0.35f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = null,
                                    tint = if (nextEntry != null) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = { setFullscreenMode(true) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "ফুলস্ক্রিন",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ফুলস্ক্রিন",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Playlist Section Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "অন্যান্য অ্যানিমেটেড ভিডিও সমূহ",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "মোট ${toBengaliDigits(playlistEntries.size)}টি",
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Scrollable List of All Animated Videos in the Chapter
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(playlistEntries, key = { idx, item -> "${item.id}_$idx" }) { idx, entry ->
                        val isCurrent = idx == currentPlaylistIndex
                        val isWatched = watchedUrls.contains(entry.videoUrl)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isCurrent) Color(0xFF2E1065).copy(alpha = 0.65f) else Color(0xFF131F38),
                            border = BorderStroke(
                                width = if (isCurrent) 1.6.dp else 1.dp,
                                color = if (isCurrent) Color(0xFFA78BFA) else Color.White.copy(alpha = 0.08f)
                            ),
                            shadowElevation = if (isCurrent) 6.dp else 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { switchToVideo(entry) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 16:9 Thumbnail with Play/Equalizer Badge
                                Box(
                                    modifier = Modifier
                                        .width(108.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(entry.thumbnailUrl?.takeIf { it.isNotBlank() } ?: R.drawable.placeholder_animated)
                                            .crossfade(true)
                                            .error(R.drawable.placeholder_animated)
                                            .placeholder(R.drawable.placeholder_animated)
                                            .build(),
                                        contentDescription = entry.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = if (isCurrent) 0.45f else 0.25f))
                                    )
                                    // Serial number top-left pill
                                    Surface(
                                        shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 10.dp),
                                        color = if (isCurrent) Color(0xFF8B5CF6) else Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = entry.serialNo,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    // Center Play/Playing icon
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCurrent) Color(0xFF8B5CF6) else Color.Black.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isCurrent && isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Video Details
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isCurrent) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF8B5CF6)
                                            ) {
                                                Text(
                                                    text = "এখন চলছে",
                                                    color = Color.White,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        } else if (isWatched) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = "দেখা হয়েছে ✓",
                                                    color = Color(0xFF34D399),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }


                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = entry.title,
                                        color = if (isCurrent) Color(0xFFDDD6FE) else Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!entry.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = entry.description,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

    // ==================== DOWNLOAD QUALITY CHOOSER DIALOG ====================
    if (showDownloadQualityDialog) {
        VideoDownloadQualityDialog(
            videoUrl = effectivePlaybackUrl,
            title = activeTitle.ifBlank { "অ্যানিমেটেড লেসন" },
            downloadedItem = downloadedItem,
            onDismiss = { showDownloadQualityDialog = false },
            onConfirmDownload = { selectedQuality ->
                showDownloadQualityDialog = false
                val subtitleText = listOfNotNull(subjectName.takeIf { it.isNotBlank() }, chapterName.takeIf { it.isNotBlank() }).joinToString(" • ")
                downloadManager.downloadFile(
                    id = downloadId,
                    title = activeTitle.ifBlank { "অ্যানিমেটেড লেসন" },
                    subtitle = subtitleText.ifBlank { "অ্যানিমেটেড ক্লাস" },
                    fileType = DownloadedItemEntity.FILE_TYPE_VIDEO,
                    remoteUrl = selectedQuality.targetM3u8Url
                )
                Toast.makeText(
                    context,
                    "ভিডিও ডাউনলোড শুরু হয়েছে (${selectedQuality.labelBangla})। 'ডাউনলোড' ট্যাবে দেখতে পাবেন।",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // ==================== DOWNLOAD INFO & DELETE DIALOG ====================
    if (showDeleteDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDownloadDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Text("অফলাইন ডাউনলোড তথ্য", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "এই অ্যানিমেটেড লেসনটি আপনার ডিভাইসে অফলাইনে সংরক্ষিত আছে। ইন্টারনেট ছাড়াও এটি দেখতে পারবেন।",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (downloadedItem != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ফাইল সাইজ:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = downloadManager.formatFileSize(downloadedItem!!.totalBytes),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDownloadDialog = false }) {
                    Text("ঠিক আছে", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        downloadManager.deleteDownloadedFile(downloadId)
                        showDeleteDownloadDialog = false
                        Toast.makeText(context, "ডাউনলোড ফাইল ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("ডিলিট করুন", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ==================== SPEED SELECTION DIALOG ====================
    if (showSpeedDialog) {
        com.example.ui.components.PlaybackSpeedDialog(
            playbackSpeed = playbackSpeed,
            onSpeedChange = { speed ->
                playbackSpeed = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // ==================== AUDIO EQUALIZER BOTTOM SHEET ====================
    if (showEqualizerBottomSheet) {
        EqualizerBottomSheet(
            onDismiss = { showEqualizerBottomSheet = false }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val mStr = if (minutes < 10) "0$minutes" else "$minutes"
    val sStr = if (seconds < 10) "0$seconds" else "$seconds"
    return "${toBengaliDigits(mStr)}:${toBengaliDigits(sStr)}"
}
