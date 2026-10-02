package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.DownloadedItemEntity
import com.example.player.PlayerClassType
import com.example.player.ShikhoPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControlsOverlay(
    title: String,
    subjectName: String = "",
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    bufferedPosition: Long,
    totalDuration: Long,
    areControlsVisible: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    isTemporaryFastForwarding: Boolean = false,
    onStartTemporaryFastForward: () -> Unit = {},
    onStopTemporaryFastForward: () -> Unit = {},
    isLive: Boolean = false,
    viewerCount: Int? = null,
    classType: PlayerClassType? = null,
    hasMeeting: Boolean = false,
    downloadedItem: DownloadedItemEntity? = null,
    onSwitchToMeeting: (() -> Unit)? = null,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekStarted: (Long) -> Unit,
    onSeekChanged: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleControls: () -> Unit,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit = {},
    selectedQualityLabel: String = "অটো",
    onPipClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onToggleResizeMode: () -> Unit = {},
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    isAudioOnlyMode: Boolean = false,
    onToggleAudioOnlyMode: () -> Unit = {},
    resumeNotificationText: String? = null,
    onRestartFromBeginning: (() -> Unit)? = null,
    manualZoomScale: Float = 1.0f,
    onResetManualZoom: () -> Unit = {},
    standaloneFullScreen: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen Touch Lock State
    var isScreenLocked by remember { mutableStateOf(false) }
    var showUnlockPill by remember { mutableStateOf(false) }

    // Double tap feedback state
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) }
    var doubleTapTriggerKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(doubleTapTriggerKey) {
        if (doubleTapFeedbackSide != null) {
            delay(650)
            doubleTapFeedbackSide = null
        }
    }

    LaunchedEffect(isScreenLocked, showUnlockPill) {
        if (isScreenLocked && showUnlockPill) {
            delay(3500)
            showUnlockPill = false
        }
    }

    val shouldApplySystemInsets = standaloneFullScreen || isFullscreen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isScreenLocked, isLive, isPlaying, manualZoomScale) {
                detectTapGestures(
                    onPress = {
                        var is2xActive = false
                        val holdJob = coroutineScope.launch {
                            delay(400) // Press & hold for 400ms to trigger 2X speed
                            if (isPlaying && !isLive && !isScreenLocked) {
                                is2xActive = true
                                onStartTemporaryFastForward()
                            }
                        }
                        tryAwaitRelease()
                        holdJob.cancel()
                        if (is2xActive) {
                            onStopTemporaryFastForward()
                        }
                    },
                    onTap = {
                        if (isScreenLocked) {
                            showUnlockPill = true
                        } else {
                            onToggleControls()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isLive && !isScreenLocked) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth * 0.38f) {
                                // Left double tap -> Rewind 10s
                                onSeekBack()
                                doubleTapFeedbackSide = "LEFT"
                                doubleTapTriggerKey++
                            } else if (offset.x > screenWidth * 0.62f) {
                                // Right double tap -> Fast Forward 10s
                                onSeekForward()
                                doubleTapFeedbackSide = "RIGHT"
                                doubleTapTriggerKey++
                            } else {
                                if (manualZoomScale > 1.05f) {
                                    onResetManualZoom()
                                } else {
                                    onTogglePlayPause()
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // -------------------------------------------------------------
        // 1. SCREEN LOCK OVERLAY (Unobtrusive & Elegant)
        // -------------------------------------------------------------
        if (isScreenLocked) {
            AnimatedVisibility(
                visible = showUnlockPill,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.90f),
                    border = BorderStroke(1.2.dp, Color(0xFFF59E0B)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable {
                            isScreenLocked = false
                            showUnlockPill = false
                            Toast.makeText(context, "স্ক্রিন আনলক হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "আনলক করুন",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "আনলক করতে ট্যাপ করুন",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            return@Box
        }

        // -------------------------------------------------------------
        // 2. TEMPORARY 2X SPEED FLOATING BADGE (On Press & Hold)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = isTemporaryFastForwarding,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (shouldApplySystemInsets) Modifier.statusBarsPadding() else Modifier)
                .padding(top = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.8f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "2X স্পিড",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 3. DOUBLE TAP RIPPLE VISUAL FEEDBACK
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = doubleTapFeedbackSide == "LEFT",
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = if (isFullscreen) 48.dp else 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "১০ সেকেন্ড পেছনে",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "-১০ সেকেন্ড",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = doubleTapFeedbackSide == "RIGHT",
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (isFullscreen) 48.dp else 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "১০ সেকেন্ড সামনে",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+১০ সেকেন্ড",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 4. OVERLAY CONTROLS (Top Bar + Center Play + Bottom Seekbar)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = areControlsVisible || isBuffering,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.84f),
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            ) {
                // ---------------------------------------------------------
                // TOP BAR (Back Button + Lesson Title ONLY + Action Strip)
                // ---------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .then(
                            if (shouldApplySystemInsets) {
                                Modifier.statusBarsPadding().displayCutoutPadding()
                            } else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = if (isFullscreen) 10.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back Button + Lesson Title ONLY (Subject Name Removed)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                            modifier = Modifier
                                .size(if (isFullscreen) 38.dp else 34.dp)
                                .clip(CircleShape)
                                .clickable { onBack() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "ফিরে যান",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = title.ifBlank { "ক্লাস লেকচার" },
                            color = Color.White,
                            fontSize = if (isFullscreen) 14.5.sp else 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Modern Action Pills Strip (No duplicate sound/audio buttons!)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Screen Touch Lock Button (In fullscreen to prevent accidental touches)
                        if (isFullscreen) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable {
                                    isScreenLocked = true
                                    showUnlockPill = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "স্ক্রিন লক",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "লক",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 2. Aspect Ratio Resize Toggle (Fit, Zoom, Fill)
                        val resizeModeLabel = when (resizeMode) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> "ফিট"
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "জুম"
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "ফুল"
                            else -> "ফিট"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onToggleResizeMode() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "ভিডিও সাইজ / ফিট",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = resizeModeLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 3. Quality Selection Tag
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onQualityClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "কোয়ালিটি",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = selectedQualityLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 4. Playback Speed Selector
                        if (!isLive) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { onSpeedClick() }
                            ) {
                                val displaySpeed = String.format(Locale.US, "%.2f", playbackSpeed).removeSuffix(".00")
                                Text(
                                    text = "${displaySpeed}x",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // 5. Download Button with live status
                        when (downloadedItem?.status) {
                            DownloadedItemEntity.STATUS_DOWNLOADING -> {
                                val item = downloadedItem
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.4f),
                                    border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable { onDownloadClick() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
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
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            DownloadedItemEntity.STATUS_COMPLETED -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.35f),
                                    border = BorderStroke(0.8.dp, Color(0xFF34D399).copy(alpha = 0.6f)),
                                    modifier = Modifier.clickable { onDownloadClick() }
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
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier.clickable { onDownloadClick() }
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

                        // 6. Picture-in-Picture
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onPipClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "PiP",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------------
                // CENTER CONTROLS (Hero Play/Pause + Fast 10s Rewind/Forward)
                // ---------------------------------------------------------
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 36.dp else 22.dp)
                ) {
                    if (!isLive) {
                        // Quick 10s Rewind
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .size(if (isFullscreen) 52.dp else 42.dp)
                                .clip(CircleShape)
                                .clickable { onSeekBack() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "১০ সেকেন্ড পেছনে",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isFullscreen) 28.dp else 22.dp)
                                )
                            }
                        }
                    }

                    if (isBuffering) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier.size(if (isFullscreen) 68.dp else 56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    } else {
                        // Clean Hero Play / Pause Button
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(if (isFullscreen) 68.dp else 56.dp)
                                .clip(CircleShape)
                                .clickable { onTogglePlayPause() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "পজ" else "প্লে",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(if (isFullscreen) 38.dp else 32.dp)
                                )
                            }
                        }
                    }

                    if (!isLive) {
                        // Quick 10s Forward
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .size(if (isFullscreen) 52.dp else 42.dp)
                                .clip(CircleShape)
                                .clickable { onSeekForward() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "১০ সেকেন্ড সামনে",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isFullscreen) 28.dp else 22.dp)
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------------
                // BOTTOM BAR (Resume Banner + Seekbar + Bengali time + Quick Actions)
                // ---------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .then(
                            if (shouldApplySystemInsets) {
                                Modifier.navigationBarsPadding().displayCutoutPadding()
                            } else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = if (isFullscreen) 10.dp else 6.dp)
                ) {
                    // Resume Playback Banner
                    AnimatedVisibility(
                        visible = !resumeNotificationText.isNullOrBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF07152B).copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.8f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = resumeNotificationText.orEmpty(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (onRestartFromBeginning != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.35f),
                                        border = BorderStroke(0.8.dp, Color(0xFF38BDF8)),
                                        modifier = Modifier.clickable { onRestartFromBeginning() }
                                    ) {
                                        Text(
                                            text = "↺ শুরু থেকে দেখুন",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isLive) {
                        var isDraggingSlider by remember { mutableStateOf(false) }
                        var dragProgressFraction by remember { mutableFloatStateOf(0f) }

                        val displayPosition = if (isDraggingSlider) {
                            (dragProgressFraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                        } else {
                            currentPosition
                        }

                        val sliderValue = if (isDraggingSlider) {
                            dragProgressFraction
                        } else if (totalDuration > 0) {
                            (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val bufferedFraction = if (totalDuration > 0) {
                            (bufferedPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        // Floating Seek Preview Tooltip when dragging
                        if (isDraggingSlider) {
                            val currentFormatted = ShikhoPlayerManager.formatTime(displayPosition, true)
                            val totalFormatted = ShikhoPlayerManager.formatTime(totalDuration, true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                                border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
                                shadowElevation = 10.dp,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "$currentFormatted / $totalFormatted",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Interactive Progress Slider with generous 56dp touch area & smooth dragging
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Secondary Track for 2-3 minutes pre-buffer visualization
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.20f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bufferedFraction)
                                        .fillMaxHeight()
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.55f))
                                )
                            }

                            // Interactive M3 Slider with smooth thumb & responsive seeking
                            Slider(
                                value = sliderValue,
                                onValueChange = { fraction ->
                                    if (!isDraggingSlider) {
                                        val target = (fraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                                        onSeekStarted(target)
                                    }
                                    isDraggingSlider = true
                                    dragProgressFraction = fraction
                                },
                                onValueChangeFinished = {
                                    val target = (dragProgressFraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                                    onSeekFinished(target)
                                    isDraggingSlider = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFE11D48),
                                    inactiveTrackColor = Color.Transparent
                                ),
                                thumb = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        shadowElevation = 8.dp,
                                        border = BorderStroke(2.dp, Color(0xFFE11D48)),
                                        modifier = Modifier.size(if (isDraggingSlider) 22.dp else 16.dp)
                                    ) {}
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Time string in Bengali digits
                            val timeString = "${ShikhoPlayerManager.formatTime(displayPosition, true)} / ${ShikhoPlayerManager.formatTime(totalDuration, true)}"
                            Text(
                                text = timeString,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Quick Action Control Icons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Manual Zoom Reset Indicator (if user pinched to zoom)
                                if (manualZoomScale > 1.05f) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.4f),
                                        border = BorderStroke(0.8.dp, Color(0xFF38BDF8)),
                                        modifier = Modifier.clickable { onResetManualZoom() }
                                    ) {
                                        Text(
                                            text = "জুম ${(manualZoomScale * 100).toInt()}% (রিসেট)",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Restart from Beginning Quick Action (if watched past 15 seconds)
                                if (currentPosition > 15_000L && onRestartFromBeginning != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.16f),
                                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                        modifier = Modifier.clickable { onRestartFromBeginning() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = "শুরু থেকে দেখুন",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "শুরু থেকে",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // One-Tap Mute / Unmute Button
                                Surface(
                                    shape = CircleShape,
                                    color = if (isMuted) Color(0xFFEF4444).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(0.8.dp, if (isMuted) Color(0xFFF87171) else Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleMute() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = if (isMuted) "সাউন্ড অন করুন" else "সাউন্ড অফ করুন",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                // Audio-Only Listening Mode Button
                                Surface(
                                    shape = CircleShape,
                                    color = if (isAudioOnlyMode) Color(0xFF0284C7).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(0.8.dp, if (isAudioOnlyMode) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleAudioOnlyMode() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Headphones,
                                            contentDescription = if (isAudioOnlyMode) "ভিডিও মোডে ফিরুন" else "শুধুমাত্র অডিও শুনুন",
                                            tint = if (isAudioOnlyMode) Color(0xFF38BDF8) else Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                // Fullscreen Toggle Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleFullscreen() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (isFullscreen) "ছোট স্ক্রিন" else "বড় স্ক্রিন",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
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
