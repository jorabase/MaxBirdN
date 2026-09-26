package com.example.ui.screens

import com.example.R
import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import com.example.util.PipHelper
import com.example.util.SetupPipController
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Rational
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.api.LessonAttachmentItem
import com.example.api.ShikhoApiService
import com.example.api.StudentLessonItem
import com.example.api.TopicFullItem
import com.example.course.CourseRepository
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.player.ShikhoPlayerManager
import com.example.player.PlayerClassType
import com.example.player.VideoTrackQuality
import com.example.ui.components.*
import com.example.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow

@OptIn(UnstableApi::class)
@Composable
fun LessonDetailPlayerScreen(
    lesson: StudentLessonItem?,
    subjectName: String,
    subjectColorHex: String?,
    isLessonLoading: Boolean = false,
    onRefreshLesson: (() -> Unit)? = null,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null,
    onPlayAnimatedLesson: ((videoUrl: String, title: String) -> Unit)? = null,
    onOpenChapterResources: (() -> Unit)? = null,
    onOpenSubjectResources: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val isExamLesson = lesson?.isExam == true ||
            lesson?.content_type?.contains("EXAM", ignoreCase = true) == true ||
            lesson?.class_type?.contains("EXAM", ignoreCase = true) == true

    if (isExamLesson) {
        val sessionId = lesson?.session_id?.takeIf { it.isNotBlank() }
            ?: lesson?.live_class?.session_id?.takeIf { it.isNotBlank() }
            ?: lesson?.content_id?.takeIf { it.isNotBlank() }
            ?: lesson?.id ?: ""
        val formattedTitle = ClassTypeUtils.formatLessonTitle(lesson?.title ?: "পরীক্ষা")
        val chapterName = lesson?.subject_name ?: subjectName

        LaunchedEffect(sessionId) {
            if (onNavigateToExam != null && sessionId.isNotBlank()) {
                onNavigateToExam(sessionId, lesson?.id ?: "", formattedTitle, chapterName)
            }
        }

        ExamRedirectScreen(
            title = formattedTitle,
            subjectName = chapterName,
            onStartExam = {
                if (onNavigateToExam != null && sessionId.isNotBlank()) {
                    onNavigateToExam(sessionId, lesson?.id ?: "", formattedTitle, chapterName)
                }
            },
            onBack = onBack
        )
        return
    }

    if (lesson?.isUpcoming == true) {
        UpcomingCountdownScreen(
            lesson = lesson,
            subjectName = subjectName,
            subjectColorHex = subjectColorHex,
            onBack = onBack
        )
        return
    }

    val context = LocalContext.current
    val sessionManager = remember(context) { com.example.auth.SessionManager(context) }
    val courseRepository = remember(sessionManager) { CourseRepository(ShikhoApiService.create(sessionManager)) }


    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var livePlayerMode by remember { mutableStateOf("STREAM") }

    // Parse Subject Color
    val subjectThemeColor = remember(subjectColorHex) {
        if (!subjectColorHex.isNullOrBlank()) {
            try {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } catch (e: Exception) {
                Color(0xFF2563EB)
            }
        } else {
            Color(0xFF2563EB)
        }
    }

    val candidateStreams = remember(lesson, lesson?.live_class?.recording_url, lesson?.live_class?.playback_url) {
        val raw = lesson?.candidateStreamUrls?.filter { it.isNotBlank() && it != "null" } ?: emptyList()
        raw.distinct()
    }
    var currentStreamIndex by remember(lesson?.id) { mutableIntStateOf(0) }
    var activeStreamUrl by remember(candidateStreams, currentStreamIndex) {
        mutableStateOf(
            candidateStreams.getOrNull(currentStreamIndex)
                ?: candidateStreams.firstOrNull()
                ?: lesson?.resolvedVideoUrl
                ?: ""
        )
    }

    // Resolve class type: Animated vs Recorded Lecture
    val classType = remember(lesson, activeStreamUrl) {
        PlayerClassType.resolve(
            isLive = false,
            contentType = lesson?.content_type,
            classType = lesson?.class_type ?: lesson?.live_class?.class_type,
            title = lesson?.title,
            url = activeStreamUrl
        )
    }

    LaunchedEffect(candidateStreams) {
        if (candidateStreams.isNotEmpty()) {
            if (activeStreamUrl.isBlank() || !candidateStreams.contains(activeStreamUrl)) {
                currentStreamIndex = 0
                activeStreamUrl = candidateStreams.first()
            }
        }
    }

    // Diagnostics & Dialog States
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playbackErrorDetails by remember { mutableStateOf<String?>(null) }

    var showCustomUrlDialog = false

    // Player States
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var availableQualities by remember { mutableStateOf<List<VideoTrackQuality>>(emptyList()) }
    var selectedQualityLabel by remember { mutableStateOf("অটো") }

    // Video Playback Progress & Resume Management
    val videoProgressManager = remember { com.example.player.VideoProgressManager.getInstance(context) }
    val videoKey = remember(lesson, activeStreamUrl) {
        videoProgressManager.generateVideoKey(
            lessonId = lesson?.id,
            contentId = lesson?.content_id,
            remoteUrl = activeStreamUrl,
            title = lesson?.title
        )
    }

    var hasAutoResumed by remember(videoKey) { mutableStateOf(false) }
    var resumeNotificationText by remember { mutableStateOf<String?>(null) }

    // Quick One-Tap Mute / Unmute State
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(1f) }

    // Audio-Only Listening Mode State ("শোনার বাটন" / Screen-off Audio)
    var isAudioOnlyMode by remember { mutableStateOf(false) }

    // ExoPlayer Instance with Shikho CDN headers & DefaultTrackSelector for HLS quality selection
    val trackSelector = remember { DefaultTrackSelector(context) }
    val exoPlayer = remember(classType) {
        ShikhoPlayerManager.buildExoPlayer(context, trackSelector, classType).apply {
            repeatMode = if (classType == PlayerClassType.ANIMATED) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
        val act = context as? Activity
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
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ভিডিও সাইজ: ফিট স্ক্রিন (১৬:৯)"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ভিডিও সাইজ: জুম ও ফিল স্ক্রিন"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ভিডিও সাইজ: ফুল স্ক্রিন স্ট্রেচ"
            else -> "ফিট স্ক্রিন"
        }
        Toast.makeText(context, modeName, Toast.LENGTH_SHORT).show()
    }

    // Slide viewing state
    var viewingSlideItem by remember { mutableStateOf<LessonAttachmentItem?>(null) }
    var isRefreshingSlide by remember { mutableStateOf(false) }

    // Expandable Accordion State for Topics
    var isTopicsExpanded by remember { mutableStateOf(true) }

    // Download Manager & Video Offline Caching
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    val videoDownloadId = remember(lesson?.id, activeStreamUrl) {
        "vid_" + ((lesson?.id ?: activeStreamUrl.ifBlank { "lesson" }).hashCode().toString() + "_" + (lesson?.title ?: "").hashCode().toString()).replace("-", "n")
    }
    val videoDownloadedItem by downloadManager.getDownloadedItemById(videoDownloadId).collectAsState(initial = null)

    val handleDownloadVideo: () -> Unit = {
        when (videoDownloadedItem?.status) {
            DownloadedItemEntity.STATUS_DOWNLOADING -> {
                downloadManager.cancelDownload(videoDownloadId)
                Toast.makeText(context, "ভিডিও ডাউনলোড বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
            DownloadedItemEntity.STATUS_COMPLETED -> {
                Toast.makeText(context, "এই ভিডিওটি ইতিমধ্যে অফলাইনে ডাউনলোড করা আছে। 'ডাউনলোড' ট্যাবে দেখতে পাবেন।", Toast.LENGTH_LONG).show()
            }
            else -> {
                val downloadUrl = activeStreamUrl.ifBlank {
                    lesson?.resolvedVideoUrl
                        ?: candidateStreams.firstOrNull()
                        ?: ""
                }
                if (downloadUrl.isNotBlank() && downloadUrl != "null") {
                    showDownloadQualityDialog = true
                } else {
                    Toast.makeText(context, "ভিডিও ডাউনলোড লিংক পাওয়া যায়নি বা লাইভ ক্লাস এখনও চলছে।", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDownloadQualityDialog) {
        val downloadSourceUrl = activeStreamUrl.ifBlank {
            lesson?.resolvedVideoUrl
                ?: candidateStreams.firstOrNull()
                ?: ""
        }
        VideoDownloadQualityDialog(
            videoUrl = downloadSourceUrl,
            title = lesson?.title ?: "ক্লাস ভিডিও",
            downloadedItem = videoDownloadedItem,
            onDismiss = { showDownloadQualityDialog = false },
            onConfirmDownload = { selectedQuality ->
                showDownloadQualityDialog = false
                downloadManager.downloadFile(
                    id = videoDownloadId,
                    title = lesson?.title ?: "ক্লাস ভিডিও লেকচার",
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

    // Initialize media source when activeStreamUrl or mode changes
    LaunchedEffect(activeStreamUrl, livePlayerMode) {
        android.util.Log.d("LectureDebug", "calling player with URL: $activeStreamUrl")
        playbackError = null
        playbackErrorDetails = null
        if (livePlayerMode == "WEB_PLAYER") {
            // When in WebView mode, stop ExoPlayer to prevent background stream fetching
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (_: Exception) {}
            isBuffering = false
            return@LaunchedEffect
        }
        if (activeStreamUrl.isNotBlank()) {
            val urlToPlay = activeStreamUrl

            // If activeStreamUrl is a master.m3u8, ExoPlayer's onTracksChanged will parse tracks dynamically.
            // If it's a direct stream_X URL, fallback to pre-populating availableQualities.
            if (urlToPlay.contains("/stream_")) {
                val baseUrl = urlToPlay
                val s0 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_0/stream.m3u8")
                val s1 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_1/stream.m3u8")
                val s2 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_2/stream.m3u8")
                val s3 = baseUrl.replace(Regex("/stream_\\d+/stream\\.m3u8"), "/stream_3/stream.m3u8")

                availableQualities = listOf(
                    VideoTrackQuality("auto", "অটো (Auto)", 0, 0, null, 0, s0),
                    VideoTrackQuality("1080p", "1080p (উচ্চ মান)", 1080, 0, null, 0, s0),
                    VideoTrackQuality("720p", "720p (এইচডি)", 720, 0, null, 0, s1),
                    VideoTrackQuality("480p", "480p (মাঝারি)", 480, 0, null, 0, s2),
                    VideoTrackQuality("360p", "360p (সাধারণ)", 360, 0, null, 0, s3)
                )
            }

            isBuffering = true
            try {
                val mediaSource = ShikhoPlayerManager.createMediaSource(urlToPlay, isLive = false)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                isBuffering = false
                playbackError = "প্লেয়ার প্রস্তুত করতে ব্যর্থ"
                playbackErrorDetails = e.localizedMessage ?: "অজানা ত্রুটি"
            }
        } else {
            isBuffering = false
        }
    }

    // Player Event Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        playbackError = null
                        playbackErrorDetails = null
                        totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (livePlayerMode == "MEETING") {
                    // Do not process or display ExoPlayer errors when user is in 100ms MEETING WebView
                    isBuffering = false
                    isPlaying = false
                    return
                }

                if (currentStreamIndex + 1 < candidateStreams.size) {
                    currentStreamIndex += 1
                    // Updating currentStreamIndex automatically changes activeStreamUrl, which triggers LaunchedEffect
                    return
                }

                isBuffering = false
                isPlaying = false
                val cause = error.cause
                val httpEx = cause as? HttpDataSource.InvalidResponseCodeException
                    ?: (cause?.cause as? HttpDataSource.InvalidResponseCodeException)
                val httpCode = httpEx?.responseCode

                val detail = when {
                    httpCode == 404 -> "ভিডিও ফাইলটি সার্ভারে পাওয়া যায়নি (HTTP 404)"
                    httpCode == 403 -> "CDN অ্যাক্সেস রিজেক্টেড (HTTP 403)"
                    httpCode != null -> "CDN নেটওয়ার্ক রেসপন্স ত্রুটি (HTTP $httpCode)"
                    cause is java.net.UnknownHostException -> "ইন্টারনেট সংযোগ নেই বা CDN সার্ভারে পৌঁছানো যাচ্ছে না"
                    cause is java.net.SocketTimeoutException -> "সার্ভার সংযোগ সময়োত্তীর্ণ (Connection Timeout)"
                    else -> error.localizedMessage ?: "অজানা প্লেব্যাক ত্রুটি"
                }
                playbackError = "প্লেব্যাক এরর"
                playbackErrorDetails = detail
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    val saveCurrentProgress: () -> Unit = {
        try {
            val pos = if (currentPosition > 0L) currentPosition else exoPlayer.currentPosition
            val dur = if (totalDuration > 0L) totalDuration else exoPlayer.duration
            if (pos > 2000L) {
                videoProgressManager.saveProgress(
                    videoKey = videoKey,
                    lessonId = lesson?.id,
                    title = lesson?.title ?: "ক্লাস লেকচার",
                    subjectName = subjectName,
                    courseId = lesson?.phase_id,
                    positionMs = pos,
                    durationMs = dur
                )
            }
        } catch (_: Exception) {}
    }

    // Auto-Resume from Last Saved Playback Position (Even across app restarts & syllabus changes)
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

    // Continuous Progress Tracking & Persistence Loop
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
                // Persist progress every 2 seconds while actively playing
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

    // Controls Auto-Hide Timer (Resets cleanly on state changes)
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isSeeking) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Lifecycle Observer (Pause on background UNLESS in Audio Mode, Resume on foreground)
    val mediaSession = remember(exoPlayer) {
        try {
            MediaSession.Builder(context, exoPlayer)
                .setId("session_ldp_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
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
                        val activity = context as? Activity
                        if (activity?.isInPictureInPictureMode != true) {
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

    val isPipMode = com.example.LocalPictureInPictureMode.current
    val configuration = LocalConfiguration.current

    // Handle device physical orientation rotation dynamically
    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !isFullscreen) {
            isFullscreen = true
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && isFullscreen) {
            isFullscreen = false
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

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

    // Fullscreen Screen Orientation & Immersive Sticky System Bars
    fun toggleFullscreen() {
        val newFullscreen = !isFullscreen
        isFullscreen = newFullscreen
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (newFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Back Handler to exit fullscreen first if active
    BackHandler {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            exoPlayer.stop()
            onBack()
        }
    }

    // Clean up screen orientation on leaving
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                if (act.isFinishing) {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val window = act.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // Picture In Picture View Mode
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
                update = { pv ->
                    pv.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // UI Structure
    if (isFullscreen) {
        // FULLSCREEN VIDEO PLAYER
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (activeStreamUrl.isNotBlank() || candidateStreams.isNotEmpty()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
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

                // Ambient Audio Only Mode Screen
                if (isAudioOnlyMode) {
                    AmbientAudioVisualizerOverlay(
                        title = lesson?.title ?: "রেকর্ডকৃত ক্লাস",
                        subjectName = subjectName,
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

                // Fullscreen Player Controls Overlay
                val seekStep = if (classType == PlayerClassType.ANIMATED) 5000L else 10000L
                PlayerControlsOverlay(
                    title = lesson?.title ?: "রেকর্ডকৃত ক্লাস",
                    subjectName = subjectName,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    currentPosition = if (isSeeking) seekPosition else currentPosition,
                    bufferedPosition = bufferedPosition,
                    totalDuration = totalDuration,
                    areControlsVisible = areControlsVisible,
                    isFullscreen = true,
                    playbackSpeed = playbackSpeed,
                    classType = classType,
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
                    onToggleFullscreen = { toggleFullscreen() },
                    onToggleControls = { areControlsVisible = !areControlsVisible },
                    onSpeedClick = { showSpeedDialog = true },
                    onQualityClick = { showQualityDialog = true },
                    selectedQualityLabel = selectedQualityLabel,
                    downloadedItem = videoDownloadedItem,
                    onDownloadClick = handleDownloadVideo,
                    resizeMode = resizeMode,
                    onToggleResizeMode = toggleResizeMode,
                    isMuted = isMuted,
                    onToggleMute = onToggleMute,
                    isAudioOnlyMode = isAudioOnlyMode,
                    onToggleAudioOnlyMode = onToggleAudioOnlyMode,
                    resumeNotificationText = resumeNotificationText,
                    onRestartFromBeginning = onRestartFromBeginning,
                    onPipClick = { enterPipMode() },
                    onBack = { toggleFullscreen() }
                )
            } else if (isLessonLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ক্লাস লোড হচ্ছে...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    } else {
        // PORTRAIT DETAIL SCREEN
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Top Video Player (16:9 Aspect Ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (livePlayerMode == "WEB_PLAYER") {
                        val webStreamUrl = activeStreamUrl
                        if (webStreamUrl.isNotBlank()) {
                            HlsWebPlayerView(
                                streamUrl = webStreamUrl,
                                onBackToStream = { livePlayerMode = "STREAM" },
                                onOpenExternal = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webStreamUrl)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (activeStreamUrl.isNotBlank() || candidateStreams.isNotEmpty()) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
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

                        // Ambient Audio Mode Visualizer Screen in Portrait
                        if (isAudioOnlyMode) {
                            AmbientAudioVisualizerOverlay(
                                title = lesson?.title ?: "ক্লাস",
                                subjectName = subjectName,
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

                        // If playbackError is present, show a sleek diagnostic overlay on top of the player!
                        if (playbackError != null) {
                            val slideUrlForError = lesson?.resolvedSlideUrl
                                ?: lesson?.live_class?.lectureSlideUrl
                            PlayerErrorOverlay(
                                playbackError = playbackError ?: "ক্লাস লোড ব্যর্থ হয়েছে",
                                playbackErrorDetails = playbackErrorDetails,
                                slideUrl = slideUrlForError,
                                onRefreshLesson = onRefreshLesson,
                                onRetryPlayback = {
                                    val curr = activeStreamUrl
                                    activeStreamUrl = ""
                                    coroutineScope.launch {
                                        delay(150)
                                        activeStreamUrl = curr
                                    }
                                },
                                onLaunchWebPlayer = { livePlayerMode = "WEB_PLAYER" },
                                onViewSlide = { item -> viewingSlideItem = item },
                                onBack = onBack
                            )
                        } else {
                            // Player Controls Overlay
                            val seekStep = if (classType == PlayerClassType.ANIMATED) 5000L else 10000L
                            PlayerControlsOverlay(
                                title = lesson?.title ?: "ক্লাস",
                                subjectName = subjectName,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                currentPosition = if (isSeeking) seekPosition else currentPosition,
                                bufferedPosition = bufferedPosition,
                                totalDuration = totalDuration,
                                areControlsVisible = areControlsVisible,
                                isFullscreen = false,
                                playbackSpeed = playbackSpeed,
                                classType = classType,
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
                                onToggleFullscreen = { toggleFullscreen() },
                                onToggleControls = { areControlsVisible = !areControlsVisible },
                                onSpeedClick = { showSpeedDialog = true },
                                onQualityClick = { showQualityDialog = true },
                                selectedQualityLabel = selectedQualityLabel,
                                downloadedItem = videoDownloadedItem,
                                onDownloadClick = handleDownloadVideo,
                                resizeMode = resizeMode,
                                onToggleResizeMode = toggleResizeMode,
                                isMuted = isMuted,
                                onToggleMute = onToggleMute,
                                isAudioOnlyMode = isAudioOnlyMode,
                                onToggleAudioOnlyMode = onToggleAudioOnlyMode,
                                resumeNotificationText = resumeNotificationText,
                                onRestartFromBeginning = onRestartFromBeginning,
                                onPipClick = { enterPipMode() },
                                onBack = {
                                    exoPlayer.stop()
                                    onBack()
                                }
                            )
                        }
                    } else if (isLessonLoading) {
                        // Actively fetching stream and materials from server/database
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.5.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "ভিডিও ও স্টাডি মেটেরিয়াল লোড করা হচ্ছে...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করুন",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Empty / No Direct Stream State Placeholder with Diagnostics
                        val slideUrlForEmpty = lesson?.resolvedSlideUrl
                            ?: lesson?.live_class?.lectureSlideUrl
                        LessonStreamPlaceholder(
                            lesson = lesson,
                            slideUrl = slideUrlForEmpty,
                            onRefreshLesson = onRefreshLesson,
                            onViewSlide = { item -> viewingSlideItem = item },
                            onBack = onBack
                        )
                    }
                }

                // 2. Class Header & Info Section
                LessonDetailHeader(lesson = lesson)

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. "ক্লাসের বিষয়বস্তু" (Expandable Accordion)
                LessonTopicsAccordion(
                    lesson = lesson,
                    subjectThemeColor = subjectThemeColor,
                    isExpanded = isTopicsExpanded,
                    onToggleExpand = { isTopicsExpanded = !isTopicsExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4. "ক্লাস রিসোর্সেস"
                LessonDocumentsSection(
                    lesson = lesson,
                    context = context,
                    coroutineScope = coroutineScope,
                    isLoading = isLessonLoading,
                    onRefreshLesson = onRefreshLesson,
                    onViewAttachment = { attachment ->
                        viewingSlideItem = attachment
                    },
                    onOpenChapterResources = onOpenChapterResources,
                    onOpenSubjectResources = onOpenSubjectResources
                )

                // 5. "🎬 অ্যানিমেটেড লেসন" (Horizontal Scroll)
                val topicIdsForAnimated = remember(lesson) {
                    val tList = lesson?.topics?.takeIf { it.isNotEmpty() }
                        ?: lesson?.live_class?.topics
                        ?: emptyList()
                    tList.mapNotNull { it.id }.filter { it.isNotBlank() }
                }
                val chapterIdForAnimated = remember(lesson) {
                    lesson?.chapter_id?.takeIf { it.isNotBlank() }
                        ?: lesson?.live_class?.chapter_id
                        ?: ""
                }

                LaunchedEffect(lesson) {
                    android.util.Log.d("AnimatedBug", "topics: ${lesson?.topics ?: lesson?.live_class?.topics}")
                    android.util.Log.d("AnimatedBug", "topicIds: $topicIdsForAnimated")
                }

                LessonAnimatedLessonsSection(
                    chapterId = chapterIdForAnimated,
                    topicIds = topicIdsForAnimated,
                    repository = courseRepository,
                    themeColor = subjectThemeColor,
                    onPlayAnimatedLesson = { vUrl, vTitle ->
                        onPlayAnimatedLesson?.invoke(vUrl, vTitle)
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Playback Speed Selection Dialog
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            playbackSpeed = playbackSpeed,
            onSpeedChange = { newSpeed ->
                playbackSpeed = newSpeed
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    // Video Quality Selection Dialog
    if (showQualityDialog) {
        VideoQualityDialog(
            availableQualities = availableQualities,
            selectedQualityLabel = selectedQualityLabel,
            onSelectQuality = { quality ->
                selectedQualityLabel = quality.label
                val parametersBuilder = trackSelector.buildUponParameters()
                if (quality.id == "auto") {
                    parametersBuilder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    trackSelector.setParameters(parametersBuilder)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                } else if (quality.trackGroup != null) {
                    val override = TrackSelectionOverride(
                        quality.trackGroup.mediaTrackGroup,
                        listOf(quality.trackIndex)
                    )
                    parametersBuilder.setOverrideForType(override)
                    trackSelector.setParameters(parametersBuilder)
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(override)
                        .build()
                } else if (quality.targetStreamUrl != null && quality.targetStreamUrl != activeStreamUrl) {
                    activeStreamUrl = quality.targetStreamUrl
                }
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    // In-App Slide Viewer Dialog
    if (viewingSlideItem != null) {
        val slide = viewingSlideItem!!
        val url = slide.downloadUrl ?: ""
        SlideViewerDialog(
            slideUrl = url,
            title = slide.displayTitle,
            onDismiss = { viewingSlideItem = null }
        )
    }
}

/**
 * Horizontal scrolling "🎬 অ্যানিমেটেড লেসন" section.
 * Automatically fetches topics corresponding to the lecture class's topics array,
 * skips items without videos, hides if topic_ids is empty.
 */
@Composable
fun LessonAnimatedLessonsSection(
    chapterId: String,
    topicIds: List<String>,
    repository: CourseRepository,
    themeColor: Color,
    onPlayAnimatedLesson: (videoUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (topicIds.isEmpty()) {
        return
    }

    var animatedTopics by remember(chapterId, topicIds) { mutableStateOf<List<TopicFullItem>>(emptyList()) }
    var isLoading by remember(chapterId, topicIds) { mutableStateOf(true) }

    LaunchedEffect(chapterId, topicIds) {
        isLoading = true
        try {
            val res = repository.getTopics(chapterId, topicIds)
            android.util.Log.d("AnimatedBug", "topics fetched: ${res.size}")
            android.util.Log.d("AnimatedBug", "first thumbnail: ${res.firstOrNull()?.videos?.data?.firstOrNull()?.video_thumbnail_url}")
            // Filter out topics with empty videos or blank playback URLs
            animatedTopics = res.filter { topic ->
                val vList = topic.videos?.data
                !vList.isNullOrEmpty() && vList.any { !it.playback_url.isNullOrBlank() }
            }
        } catch (e: Exception) {
            android.util.Log.e("AnimatedBug", "Error fetching animated lessons", e)
            animatedTopics = emptyList()
        } finally {
            isLoading = false
            android.util.Log.d("AnimatedBug", "rendering section: ${topicIds.isNotEmpty() && animatedTopics.isNotEmpty()}")
        }
    }

    // Hide completely if no animated lessons found and not loading
    if (!isLoading && animatedTopics.isEmpty()) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "অ্যানিমেটেড লেসন",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (animatedTopics.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = themeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${toBengaliDigits(animatedTopics.size)}টি",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    color = themeColor
                )
                Text(
                    text = "অ্যানিমেটেড লেসন লোড হচ্ছে...",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(animatedTopics) { topic ->
                    val video = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }
                    val playbackUrl = video?.playback_url ?: ""
                    val thumbnail = video?.video_thumbnail_url?.firstOrNull()
                    val topicTitle = topic.name ?: "অ্যানিমেটেড লেসন"
                    val serialText = topic.no?.let { toBengaliDigits(it) } ?: ""

                    AnimatedLessonMiniCard(
                        serialNo = serialText,
                        title = topicTitle,
                        thumbnailUrl = thumbnail,
                        themeColor = themeColor,
                        onClick = {
                            if (playbackUrl.isNotBlank()) {
                                onPlayAnimatedLesson(playbackUrl, topicTitle)
                            }
                        }
                    )
                }
            }

            // Dot indicators below the LazyRow
            if (animatedTopics.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstVisible = listState.firstVisibleItemIndex
                    val displayCount = animatedTopics.size.coerceAtMost(8)
                    repeat(displayCount) { index ->
                        val isSelected = index == (firstVisible % displayCount)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.5.dp)
                                .height(4.dp)
                                .width(if (isSelected) 14.dp else 5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected) themeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedLessonMiniCard(
    serialNo: String,
    title: String,
    thumbnailUrl: String?,
    themeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .width(230.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl?.takeIf { it.isNotBlank() } ?: R.drawable.placeholder_animated)
                    .crossfade(true)
                    .error(R.drawable.placeholder_animated)
                    .placeholder(R.drawable.placeholder_animated)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark gradient overlay covering the whole card with heavy darkness at the bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Serial badge (Top-Left)
            if (serialNo.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 10.dp, topStart = 16.dp),
                    color = themeColor,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = serialNo,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Center Play Icon (Circular white background with dark play arrow)
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "প্লে করুন",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Title positioned at the bottom over the dark gradient
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}



