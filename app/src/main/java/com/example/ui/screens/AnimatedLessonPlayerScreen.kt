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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.LocalPictureInPictureMode
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.download.DownloadQualityOption
import com.example.player.VideoProgressManager
import com.example.ui.components.VideoDownloadQualityDialog
import com.example.util.PipHelper
import com.example.util.SetupPipController
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

/**
 * Premium, feature-packed Animated Lesson Video Player.
 * Enhanced with:
 * - In-app Video Download with real quality selector and live progress
 * - Offline auto-playback when file is stored locally
 * - Double-tap to seek (10s back & forward with visual animated ripples)
 * - Press & hold 2X turbo speed with glowing badge
 * - Vertical drag gestures for Volume and Brightness with HUD overlay
 * - Pinch-to-zoom & Pan support for detailed illustrations
 * - Picture-in-Picture (PiP) multitasking mode
 * - Screen Lock (পর্দা লক) to prevent accidental taps
 * - Aspect ratio mode toggle (Fit, Zoom, Fill)
 * - Mute / Unmute one-tap toggle
 * - Safe system bars & display cutout insets
 * - Auto-resume playback reminder
 */
@OptIn(UnstableApi::class)
@Composable
fun AnimatedLessonPlayerScreen(
    videoUrl: String,
    title: String,
    subjectName: String = "",
    chapterName: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val isPipMode = LocalPictureInPictureMode.current

    // Audio & Screen Brightness Managers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    // -------------------------------------------------------------
    // Download Manager & Offline Resolution
    // -------------------------------------------------------------
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val downloadId = remember(videoUrl, title) {
        "anim_vid_" + (videoUrl.hashCode().toString() + "_" + title.hashCode().toString()).replace("-", "n")
    }
    val downloadedItem by downloadManager.getDownloadedItemById(downloadId).collectAsState(initial = null)
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showDeleteDownloadDialog by remember { mutableStateOf(false) }

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

    // -------------------------------------------------------------
    // UI & Player State
    // -------------------------------------------------------------
    var isFullscreen by remember {
        mutableStateOf(configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
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
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Mute State
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(1f) }

    // Gestures HUD (Brightness, Volume, Seek feedback)
    var gestureBrightness by remember { mutableFloatStateOf(-1f) }
    var gestureVolume by remember { mutableIntStateOf(-1) }
    var isGestureHudVisible by remember { mutableStateOf(false) }
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) } // "LEFT" or "RIGHT"
    var doubleTapTriggerKey by remember { mutableIntStateOf(0) }

    // Pinch-to-zoom & Pan
    var videoScale by remember { mutableFloatStateOf(1.0f) }
    var videoPan by remember { mutableStateOf(Offset.Zero) }

    // Resume video playback notification
    var resumeNotificationText by remember { mutableStateOf<String?>(null) }
    var hasAutoResumed by remember { mutableStateOf(false) }

    // -------------------------------------------------------------
    // ExoPlayer Instance
    // -------------------------------------------------------------
    val exoPlayer = remember(context, effectivePlaybackUrl) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            if (effectivePlaybackUrl.isNotBlank()) {
                val mediaItem = MediaItem.fromUri(effectivePlaybackUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        }
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
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        playbackError = null
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                        playbackError = null

                        // Check & Auto-resume from previous progress if available
                        if (!hasAutoResumed && totalDuration > 10000L) {
                            hasAutoResumed = true
                            coroutineScope.launch {
                                val progressMgr = VideoProgressManager.getInstance(context)
                                val key = progressMgr.generateVideoKey(null, videoUrl, title)
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

    // Save Video Progress periodically
    LaunchedEffect(exoPlayer, isPlaying) {
        val progressMgr = VideoProgressManager.getInstance(context)
        val key = progressMgr.generateVideoKey(null, videoUrl, title)

        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)

                if (currentPosition > 2000L) {
                    progressMgr.saveProgress(
                        videoKey = key,
                        title = title,
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
    LaunchedEffect(areControlsVisible, isPlaying, isScreenLocked) {
        if (areControlsVisible && isPlaying && !isScreenLocked) {
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
        if (isScreenLocked) {
            showUnlockPill = true
        } else if (isFullscreen) {
            setFullscreenMode(false)
        } else {
            onBack()
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

    // -------------------------------------------------------------
    // Main Player Screen Body
    // -------------------------------------------------------------
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("animated_lesson_player_screen")
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
                                delay(400) // Hold 400ms to activate 2X Speed
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
                                    // Rewind 10s
                                    val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    doubleTapFeedbackSide = "LEFT"
                                    doubleTapTriggerKey++
                                } else if (offset.x > screenWidth * 0.65f) {
                                    // Forward 10s
                                    val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(totalDuration)
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                    doubleTapFeedbackSide = "RIGHT"
                                    doubleTapTriggerKey++
                                } else {
                                    // Center double tap resets zoom or toggles play/pause
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
        )

        // 3. Double-Tap Seek Visual Feedback Arcs
        if (doubleTapFeedbackSide != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                contentAlignment = if (doubleTapFeedbackSide == "LEFT") Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (doubleTapFeedbackSide == "LEFT") {
                            Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Text("১০ সে. পেছনে", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text("১০ সে. সামনে", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        // 4. 2X Turbo Speed Pill Indicator
        AnimatedVisibility(
            visible = is2xHoldActive,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
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
                    Text("২X স্পিডে চলছে...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 5. Screen Lock Status / Unlock Button Overlay
        if (isScreenLocked) {
            AnimatedVisibility(
                visible = showUnlockPill,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp)
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
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Text("পর্দা লক করা আছে • আনলক করতে ট্যাপ করুন", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 6. Resume Video Playback Alert Banner
        AnimatedVisibility(
            visible = resumeNotificationText != null && areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 76.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.7f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                    Text(
                        text = resumeNotificationText ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            exoPlayer.seekTo(0L)
                            currentPosition = 0L
                            resumeNotificationText = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("শুরু থেকে", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 7. Loading / Buffering Indicator
        if (isBuffering && playbackError == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF8B5CF6),
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isPlayingOffline) "অফলাইন অ্যানিমেশন প্রস্তুত হচ্ছে..." else "লেসন লোড হচ্ছে...",
                            color = Color.White,
                            fontSize = 13.sp,
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
                    modifier = Modifier.padding(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = playbackError ?: "ভিডিও প্লে করা যাচ্ছে না",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            playbackError = null
                            exoPlayer.setMediaItem(MediaItem.fromUri(effectivePlaybackUrl))
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("পুনরায় চেষ্টা করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 9. Controls Overlay (Gradient Top & Bottom with Clean Touch Actions)
        AnimatedVisibility(
            visible = areControlsVisible && !isScreenLocked,
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
                                Color.Black.copy(alpha = 0.75f),
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
                        .statusBarsPadding()
                        .displayCutoutPadding()
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
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Lesson Title & Subject/Chapter Subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title.ifBlank { "অ্যানিমেটেড লেসন" },
                                color = Color.White,
                                fontSize = 15.sp,
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
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        val subInfo = listOfNotNull(
                            subjectName.takeIf { it.isNotBlank() },
                            chapterName.takeIf { it.isNotBlank() },
                            "অ্যানিমেটেড ক্লাস"
                        ).joinToString(" • ")

                        Text(
                            text = subInfo,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 1. Picture-in-Picture Button
                    IconButton(
                        onClick = {
                            val act = activity ?: (context as? Activity)
                            PipHelper.enterPipMode(act, isPlaying, Rational(16, 9))
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture-in-Picture",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 2. Aspect Ratio Toggle (Fit, Zoom, Fill)
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
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Playback Speed Selector
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSpeedDialog = true }
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 4. SMART VIDEO DOWNLOAD BUTTON WITH LIVE STATUS
                    when (downloadedItem?.status) {
                        DownloadedItemEntity.STATUS_DOWNLOADING -> {
                            val item = downloadedItem!!
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.35f),
                                border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                modifier = Modifier.clickable {
                                    Toast.makeText(context, "ডাউনলোড চলছে: ${item.progressPercent}%", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { item.progressFraction },
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${item.progressPercent}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                        DownloadedItemEntity.STATUS_COMPLETED -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.35f),
                                border = BorderStroke(0.8.dp, Color(0xFF34D399).copy(alpha = 0.7f)),
                                modifier = Modifier.clickable {
                                    showDeleteDownloadDialog = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadDone,
                                        contentDescription = "ডাউনলোড সম্পন্ন",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "সেভড",
                                        color = Color(0xFF34D399),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.35f),
                                border = BorderStroke(0.8.dp, Color(0xFFA78BFA).copy(alpha = 0.6f)),
                                modifier = Modifier.clickable {
                                    showDownloadQualityDialog = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "ডাউনলোড",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "ডাউনলোড",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
                        .padding(end = 16.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .displayCutoutPadding()
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "পর্দা লক করুন",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // ==================== CENTER PLAY/PAUSE & SEEK ====================
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Replay 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Main Play / Pause Button with Glow
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF8B5CF6),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .size(70.dp)
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
                                modifier = Modifier.size(42.dp)
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // ==================== BOTTOM TIMELINE CONTROLS ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Time Row & Quick Mute
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "/",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatTime(totalDuration),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
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
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isMuted) "আনমিউট করুন" else "মিউট করুন",
                                    tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Fullscreen Toggle
                            IconButton(
                                onClick = {
                                    setFullscreenMode(!isFullscreen)
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Progress Slider with Buffer Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Buffered progress bar
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

                        // Interactive Scrubber Slider
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

        // ==================== 10. DOWNLOAD QUALITY CHOOSER DIALOG ====================
        if (showDownloadQualityDialog) {
            VideoDownloadQualityDialog(
                videoUrl = effectivePlaybackUrl,
                title = title.ifBlank { "অ্যানিমেটেড লেসন" },
                downloadedItem = downloadedItem,
                onDismiss = { showDownloadQualityDialog = false },
                onConfirmDownload = { selectedQuality ->
                    showDownloadQualityDialog = false
                    val subtitleText = listOfNotNull(subjectName.takeIf { it.isNotBlank() }, chapterName.takeIf { it.isNotBlank() }).joinToString(" • ")
                    downloadManager.downloadFile(
                        id = downloadId,
                        title = title.ifBlank { "অ্যানিমেটেড লেসন" },
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

        // ==================== 11. DOWNLOAD INFO & DELETE DIALOG ====================
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

        // ==================== 12. SPEED SELECTION DIALOG ====================
        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("প্লেব্যাক স্পিড নির্বাচন করুন", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                            val isSelected = (playbackSpeed == speed)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedDialog = false
                                    }
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (speed == 1.0f) "স্বাভাবিক (1.0x)" else "${speed}x",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text("বন্ধ করুন")
                    }
                }
            )
        }
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
