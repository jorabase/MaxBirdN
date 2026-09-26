package com.example.ui.components

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.DownloadedItemEntity
import com.example.player.PlayerClassType
import com.example.player.ShikhoPlayerManager
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerControlsOverlay(
    title: String,
    subjectName: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    bufferedPosition: Long,
    totalDuration: Long,
    areControlsVisible: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
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
    onBack: () -> Unit
) {
    val effectiveClassType = remember(classType, isLive) {
        classType ?: if (isLive) PlayerClassType.LIVE else PlayerClassType.RECORDED_LECTURE
    }

    // Screen Touch Lock State (specifically designed for landscape/fullscreen comfort)
    var isScreenLocked by remember { mutableStateOf(false) }

    // Double tap feedback state
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) }
    var doubleTapTriggerKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(doubleTapTriggerKey) {
        if (doubleTapFeedbackSide != null) {
            delay(650)
            doubleTapFeedbackSide = null
        }
    }

    // Safe Insets Modifier for Rotated (Fullscreen) & Portrait
    val insetsModifier = if (isFullscreen) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isScreenLocked, isLive) {
                detectTapGestures(
                    onTap = {
                        if (isScreenLocked) {
                            // If screen is locked, a tap just shows the unlock button
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
                                // Center double tap -> Toggle play/pause
                                onTogglePlayPause()
                            }
                        }
                    }
                )
            }
    ) {
        // -------------------------------------------------------------
        // 1. SCREEN LOCK OVERLAY (Only visible when locked)
        // -------------------------------------------------------------
        if (isScreenLocked) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.78f),
                border = BorderStroke(1.2.dp, Color(0xFFF59E0B)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .padding(top = 16.dp)
                    .clickable { isScreenLocked = false }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "আনলক করুন",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "স্ক্রিন লক রয়েছে • আনলক করতে ট্যাপ করুন",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            return@Box
        }

        // -------------------------------------------------------------
        // 2. DOUBLE TAP RIPPLE VISUAL FEEDBACK
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
        // 3. OVERLAY CONTROLS (Fade in/out with smooth gradient background)
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
                    .then(insetsModifier)
            ) {
                // ---------------------------------------------------------
                // TOP BAR (Back Button + Title info + Action Strip Pills)
                // ---------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = if (isFullscreen) 10.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back Button + Title
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

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            if (subjectName.isNotBlank() && isFullscreen) {
                                Text(
                                    text = subjectName,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = title.ifBlank { "ক্লাস লেকচার" },
                                color = Color.White,
                                fontSize = if (isFullscreen) 14.sp else 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Modern Action Pills Strip
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. One-Tap Mute / Unmute Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMuted) Color(0xFFEF4444).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, if (isMuted) Color(0xFFF87171) else Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onToggleMute() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "সাউন্ড অফ/অন",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isMuted) "মিউট" else "সাউন্ড",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 2. Audio-Only Listening Mode Pill (শোনার বাটন / স্ক্রিন অফ অডিও)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAudioOnlyMode) Color(0xFF0284C7).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, if (isAudioOnlyMode) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onToggleAudioOnlyMode() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "অডিও মোড",
                                    tint = if (isAudioOnlyMode) Color(0xFF38BDF8) else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAudioOnlyMode) "শুনুন (অন)" else "শুনুন",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 3. Screen Touch Lock Button (In landscape mode to prevent accidental touches)
                        if (isFullscreen) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { isScreenLocked = true }
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

                        // 4. Aspect Ratio Resize Toggle
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

                        // 5. Quality Selection Tag
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

                        // 6. Playback Speed Selector
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

                        // 7. Download Button with live status
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

                        // 8. Picture-in-Picture
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
                // CENTER CONTROLS (-10s, Hero Play/Pause, +10s)
                // ---------------------------------------------------------
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 36.dp else 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBuffering) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier.size(if (isFullscreen) 64.dp else 52.dp)
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
                        // Rewind 10s Button
                        if (!isLive) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                                modifier = Modifier
                                    .size(if (isFullscreen) 50.dp else 42.dp)
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

                        // Hero Play / Pause Button
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

                        // Forward 10s Button
                        if (!isLive) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                                modifier = Modifier
                                    .size(if (isFullscreen) 50.dp else 42.dp)
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
                }

                // ---------------------------------------------------------
                // BOTTOM BAR (Resume Banner + Seekbar + Bengali time + Quick Actions)
                // ---------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = if (isFullscreen) 10.dp else 6.dp)
                ) {
                    // Resume Playback Banner (With direct "শুরু থেকে দেখুন" button)
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

                        // Interactive Progress Slider with secondary buffered track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Secondary Track for Buffering visualization
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bufferedFraction)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.48f))
                                )
                            }

                            // Interactive M3 Slider
                            Slider(
                                value = sliderValue,
                                onValueChange = { fraction ->
                                    isDraggingSlider = true
                                    dragProgressFraction = fraction
                                    val target = (fraction * totalDuration).toLong().coerceIn(0L, totalDuration)
                                    onSeekStarted(target)
                                    onSeekChanged(target)
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

                            // Quick Action Control Pills (Restart, Mute, Audio Mode, Fullscreen)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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

                                // Quick Audio Mode Button (শোনার বাটন)
                                Surface(
                                    shape = CircleShape,
                                    color = if (isAudioOnlyMode) Color(0xFF0284C7).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(0.8.dp, if (isAudioOnlyMode) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleAudioOnlyMode() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Headphones,
                                            contentDescription = "অডিও মোড (স্ক্রিন অফ করে শুনুন)",
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
                                            contentDescription = if (isFullscreen) "ছোট স্ক্রিন" else "ফুল স্ক্রিন",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // LIVE STATUS BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE11D48))
                                )
                                Text(
                                    text = "🔴 সরাসরি সম্প্রচার চলছে (Live)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // One-Tap Mute / Unmute Button for Live
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

                                // Audio Mode for Live
                                Surface(
                                    shape = CircleShape,
                                    color = if (isAudioOnlyMode) Color(0xFF0284C7).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(0.8.dp, if (isAudioOnlyMode) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onToggleAudioOnlyMode() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Headphones,
                                            contentDescription = "অডিও মোড",
                                            tint = if (isAudioOnlyMode) Color(0xFF38BDF8) else Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                // Fullscreen for Live
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
                                            contentDescription = if (isFullscreen) "ছোট স্ক্রিন" else "ফুল স্ক্রিন",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
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
