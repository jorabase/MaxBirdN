package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.LocalPictureInPictureMode
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.player.PlayerClassType
import com.example.player.ShikhoPlayerManager
import com.example.player.VideoProgressManager
import com.example.player.VideoTrackQuality
import com.example.ui.components.AmbientAudioVisualizerOverlay
import com.example.ui.components.PlaybackSpeedDialog
import com.example.ui.components.PlayerControlsOverlay
import com.example.ui.components.VideoDownloadQualityDialog
import com.example.ui.components.VideoQualityDialog
import com.example.util.PipHelper
import com.example.util.SetupPipController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUrl: String,
    title: String,
    subjectName: String?,
    subjectColorHex: String? = null,
    isLive: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Dialog state
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf<List<VideoTrackQuality>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("অটো") }

    // Controls visibility state
    var areControlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    // Download Manager & Offline Check
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val downloadId = remember(videoUrl, title) {
        "vid_" + (videoUrl.hashCode().toString() + "_" + title.hashCode().toString()).replace("-", "n")
    }
    val downloadedItem by downloadManager.getDownloadedItemById(downloadId).collectAsState(initial = null)
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }

    val sessionManager = remember { com.example.auth.SessionManager(context) }
    LaunchedEffect(videoUrl, title) {
        if (title.isNotBlank()) {
            sessionManager.markLessonCompleted(title)
            sessionManager.markLessonCompleted(downloadId)
        }
    }

    // Video Playback Progress & Resume Management
    val videoProgressManager = remember { VideoProgressManager.getInstance(context) }
    val videoKey = remember(videoUrl, title) {
        videoProgressManager.generateVideoKey(
            lessonId = null,
            remoteUrl = videoUrl,
            title = title
        )
    }

    var hasAutoResumed by remember(videoKey) { mutableStateOf(false) }
    var resumeNotificationText by remember { mutableStateOf<String?>(null) }

    // Quick One-Tap Mute / Unmute State
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(1f) }

    // Audio-Only Listening Mode State ("শোনার বাটন" / Screen-off Audio)
    var isAudioOnlyMode by remember { mutableStateOf(false) }

    // Fallback URL if passed URL is empty
    val effectivePlaybackUrl = remember(videoUrl, downloadedItem) {
        val completedLocal = if (downloadedItem?.status == DownloadedItemEntity.STATUS_COMPLETED) {
            val file = File(downloadedItem!!.localFilePath)
            if (file.exists() && file.length() > 0) file.absolutePath else null
        } else null

        when {
            completedLocal != null -> completedLocal
            videoUrl.isNotBlank() && videoUrl != "null" -> videoUrl
            else -> ""
        }
    }

    val isPlayingOffline = remember(effectivePlaybackUrl) {
        effectivePlaybackUrl.startsWith("/") || effectivePlaybackUrl.startsWith("file://")
    }

    // ExoPlayer instance configured with Shikho CDN headers or Local Offline source
    val exoPlayer = remember(context, effectivePlaybackUrl) {
        ShikhoPlayerManager.buildExoPlayer(context).apply {
            val mediaSource = ShikhoPlayerManager.createMediaSource(
                url = effectivePlaybackUrl,
                isLive = isLive && !isPlayingOffline,
                context = context
            )
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    val onToggleMute: () -> Unit = {
        if (isMuted) {
            exoPlayer.volume = if (previousVolume > 0f) previousVolume else 1f
            isMuted = false
            Toast.makeText(context, "🔊 সাউন্ড চালু করা হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            previousVolume = if (exoPlayer.volume > 0f) exoPlayer.volume else 1f
            exoPlayer.volume = 0f
            isMuted = true
            Toast.makeText(context, "🔇 সাউন্ড বন্ধ করা হয়েছে (Muted)", Toast.LENGTH_SHORT).show()
        }
    }

    val onToggleAudioOnlyMode: () -> Unit = {
        isAudioOnlyMode = !isAudioOnlyMode
        val act = activity ?: (context as? Activity)
        if (isAudioOnlyMode) {
            act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(context, "অডিও মোড চালু হয়েছে! এখন স্ক্রিন বন্ধ করলেও লেকচার শুনতে পারবেন। 🎧", Toast.LENGTH_SHORT).show()
        } else {
            act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(context, "ভিডিও মোডে ফিরে আসা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    val onRestartFromBeginning: () -> Unit = {
        exoPlayer.seekTo(0L)
        resumeNotificationText = null
        videoProgressManager.resetProgress(videoKey)
        Toast.makeText(context, "শুরু থেকে প্লে করা হচ্ছে", Toast.LENGTH_SHORT).show()
    }

    val toggleResizeMode: () -> Unit = {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val modeName = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ভিডিও সাইজ: ফিট স্ক্রিন"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ভিডিও সাইজ: জুম ও ফিল স্ক্রিন"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ভিডিও সাইজ: ফুল স্ক্রিন"
            else -> "ফিট স্ক্রিন"
        }
        Toast.makeText(context, modeName, Toast.LENGTH_SHORT).show()
    }

    val saveCurrentProgress: () -> Unit = {
        try {
            val pos = if (currentPosition > 0L) currentPosition else exoPlayer.currentPosition
            val dur = if (totalDuration > 0L) totalDuration else exoPlayer.duration
            if (pos > 2000L) {
                videoProgressManager.saveProgress(
                    videoKey = videoKey,
                    lessonId = null,
                    title = title,
                    subjectName = subjectName,
                    courseId = null,
                    positionMs = pos,
                    durationMs = dur
                )
            }
        } catch (_: Exception) {}
    }

    // Auto-detect physical rotation
    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !isFullscreen) {
            isFullscreen = true
        } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && isFullscreen) {
            isFullscreen = false
        }
    }

    // Handle Fullscreen system bars & orientation
    DisposableEffect(isFullscreen, activity) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Handle hardware back button to exit fullscreen first
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            saveCurrentProgress()
            exoPlayer.stop()
            onBack()
        }
    }

    // Player listener & periodic time tracker
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    saveCurrentProgress()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val qualities = mutableListOf<VideoTrackQuality>()
                qualities.add(VideoTrackQuality(id = "auto", label = "অটো (Auto)", height = 0, bitrate = 0))

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val height = format.height
                            val bitrate = format.bitrate
                            if (height > 0) {
                                val label = "${height}p" + if (bitrate > 0) " (${bitrate / 1000} kbps)" else ""
                                qualities.add(
                                    VideoTrackQuality(
                                        id = "${height}_${bitrate}",
                                        label = label,
                                        height = height,
                                        bitrate = bitrate,
                                        trackGroup = group,
                                        trackIndex = i
                                    )
                                )
                            }
                        }
                    }
                }
                availableQualities = qualities.distinctBy { it.label }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            saveCurrentProgress()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto-Resume from Last Saved Playback Position (Even after days or app restarts)
    LaunchedEffect(exoPlayer, videoKey, isBuffering) {
        if (!isBuffering && !hasAutoResumed) {
            val savedProgress = videoProgressManager.getProgress(videoKey)
            if (savedProgress != null && savedProgress.isEligibleForResume) {
                hasAutoResumed = true
                exoPlayer.seekTo(savedProgress.positionMs)
                val timeFormatted = ShikhoPlayerManager.formatTime(savedProgress.positionMs, true)
                resumeNotificationText = "পূর্বের $timeFormatted মিনিট থেকে চলছে"
                coroutineScope.launch {
                    delay(8000)
                    resumeNotificationText = null
                }
            }
        }
    }

    // Continuous progress tracking loop
    LaunchedEffect(exoPlayer, videoKey, isPlaying) {
        var tick = 0
        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                if (!isSeeking) {
                    currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)

                tick++
                if (tick % 4 == 0 && currentPosition > 2000L && isPlaying) {
                    saveCurrentProgress()
                }
            }
            delay(500)
        }
    }

    // Save Progress on screen exit / dispose
    DisposableEffect(videoKey) {
        onDispose {
            saveCurrentProgress()
        }
    }

    // Auto-hide controls timer (4 seconds)
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isSeeking) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Handle App Lifecycle (Keep playing in background when Audio Mode is active)
    val mediaSession = remember(exoPlayer) {
        try {
            MediaSession.Builder(context, exoPlayer)
                .setId("session_vp_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
                .build()
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(mediaSession) {
        onDispose {
            try {
                mediaSession?.release()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(lifecycleOwner, isAudioOnlyMode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    saveCurrentProgress()
                    if (isAudioOnlyMode) {
                        // Keep playing audio uninterrupted when screen is turned off or app backgrounded!
                    } else {
                        val act = context as? Activity
                        if (act?.isInPictureInPictureMode != true) {
                            exoPlayer.pause()
                        }
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying && !isAudioOnlyMode) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            saveCurrentProgress()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isPipMode = LocalPictureInPictureMode.current

    SetupPipController(
        player = exoPlayer,
        isPlaying = isPlaying,
        aspectRatio = Rational(16, 9),
        onPipEntered = {
            if (isFullscreen) {
                isFullscreen = false
            }
        }
    )

    fun enterPipMode() {
        val act = activity ?: (context as? Activity)
        PipHelper.enterPipMode(act, isPlaying, Rational(16, 9))
    }

    // Picture In Picture View Mode (Compact floating window with gestures)
    if (isPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(totalDuration) {
                    val componentWidth = size.width
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (offset.x < componentWidth * 0.4f) {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            } else if (offset.x > componentWidth * 0.6f) {
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                                exoPlayer.seekTo(target)
                            } else {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. ExoPlayer Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Ambient Audio Visualizer Overlay (Battery-saving screen for screen-off listen mode)
        if (isAudioOnlyMode) {
            AmbientAudioVisualizerOverlay(
                title = title.ifBlank { "ক্লাস ভিডিও লেকচার" },
                subjectName = subjectName ?: "",
                isPlaying = isPlaying,
                isMuted = isMuted,
                onTogglePlayPause = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onToggleMute = onToggleMute,
                onExitAudioMode = { onToggleAudioOnlyMode() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Unified Adaptive Player Controls Overlay
        val seekStep = 10000L
        PlayerControlsOverlay(
            title = title.ifBlank { "ক্লাস ভিডিও লেকচার" },
            subjectName = subjectName ?: "",
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            currentPosition = if (isSeeking) seekPosition else currentPosition,
            bufferedPosition = bufferedPosition,
            totalDuration = totalDuration,
            areControlsVisible = areControlsVisible,
            isFullscreen = isFullscreen,
            playbackSpeed = playbackSpeed,
            classType = if (isLive) PlayerClassType.LIVE else PlayerClassType.RECORDED_LECTURE,
            isLive = isLive,
            downloadedItem = downloadedItem,
            onTogglePlayPause = {
                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            onSeekBack = {
                val target = (exoPlayer.currentPosition - seekStep).coerceAtLeast(0L)
                exoPlayer.seekTo(target)
            },
            onSeekForward = {
                val target = (exoPlayer.currentPosition + seekStep).coerceAtMost(totalDuration)
                exoPlayer.seekTo(target)
            },
            onSeekStarted = {
                isSeeking = true
                seekPosition = it
            },
            onSeekChanged = {
                seekPosition = it
            },
            onSeekFinished = { targetPos ->
                currentPosition = targetPos
                seekPosition = targetPos
                exoPlayer.seekTo(targetPos)
                coroutineScope.launch {
                    delay(350)
                    isSeeking = false
                }
            },
            onToggleFullscreen = { isFullscreen = !isFullscreen },
            onToggleControls = { areControlsVisible = !areControlsVisible },
            onSpeedClick = { showSpeedDialog = true },
            onQualityClick = { showQualityDialog = true },
            selectedQualityLabel = selectedQualityLabel,
            onPipClick = { enterPipMode() },
            onDownloadClick = {
                when (downloadedItem?.status) {
                    DownloadedItemEntity.STATUS_DOWNLOADING -> {
                        downloadManager.cancelDownload(downloadId)
                        Toast.makeText(context, "ভিডিও ডাউনলোড বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                    DownloadedItemEntity.STATUS_COMPLETED -> {
                        showDeleteDownloadDialog = true
                    }
                    else -> {
                        if (effectivePlaybackUrl.isNotBlank()) {
                            showDownloadQualityDialog = true
                        } else {
                            Toast.makeText(context, "ভিডিও ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            resizeMode = resizeMode,
            onToggleResizeMode = toggleResizeMode,
            isMuted = isMuted,
            onToggleMute = onToggleMute,
            isAudioOnlyMode = isAudioOnlyMode,
            onToggleAudioOnlyMode = onToggleAudioOnlyMode,
            resumeNotificationText = resumeNotificationText,
            onRestartFromBeginning = onRestartFromBeginning,
            onBack = {
                if (isFullscreen) {
                    isFullscreen = false
                } else {
                    saveCurrentProgress()
                    exoPlayer.stop()
                    onBack()
                }
            }
        )

        // Speed Selector Dialog
        if (showSpeedDialog) {
            PlaybackSpeedDialog(
                playbackSpeed = playbackSpeed,
                onSpeedChange = { speed ->
                    playbackSpeed = speed
                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        // Quality Selector Dialog
        if (showQualityDialog) {
            VideoQualityDialog(
                availableQualities = availableQualities,
                selectedQualityLabel = selectedQualityLabel,
                onSelectQuality = { quality ->
                    selectedQualityLabel = quality.label
                    if (quality.id == "auto") {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .build()
                    } else if (quality.trackGroup != null) {
                        val override = TrackSelectionOverride(
                            quality.trackGroup.mediaTrackGroup,
                            listOf(quality.trackIndex)
                        )
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(override)
                            .build()
                    }
                },
                onDismiss = { showQualityDialog = false }
            )
        }

        // Download Quality Selection Dialog
        if (showDownloadQualityDialog) {
            VideoDownloadQualityDialog(
                videoUrl = effectivePlaybackUrl,
                title = title.ifBlank { "ক্লাস ভিডিও লেকচার" },
                downloadedItem = downloadedItem,
                onDismiss = { showDownloadQualityDialog = false },
                onConfirmDownload = { selectedQuality ->
                    showDownloadQualityDialog = false
                    downloadManager.downloadFile(
                        id = downloadId,
                        title = title.ifBlank { "ক্লাস ভিডিও লেকচার" },
                        subtitle = subjectName,
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

        // Downloaded Video Info & Deletion Dialog
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
                        Text("অফলাইন ডাউনলোড", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "এই ভিডিওটি আপনার ডিভাইসের সুরক্ষিত অ্যাপ স্টোরেজে অফলাইনে সংরক্ষিত রয়েছে।",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (downloadedItem != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "সাইজ: ${downloadManager.formatFileSize(downloadedItem!!.totalBytes)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            Toast.makeText(context, "ডাউনলোড করা ফাইল ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("ডিলিট করুন", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// Extension to find Activity from Context
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
