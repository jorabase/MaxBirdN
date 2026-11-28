package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.DownloadedItemEntity
import com.example.player.PlayerClassType
import com.example.player.ShikhoPlayerManager
import com.example.utils.AppLang
import com.example.utils.isBangla
import com.example.utils.toBengaliDigits
import com.example.utils.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

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
    onEqualizerClick: () -> Unit = {},
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
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isBn = isBangla()
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Screen Touch Lock State
    var isScreenLocked by remember { mutableStateOf(false) }
    var showUnlockPill by remember { mutableStateOf(false) }

    // Accumulative Double-Tap Seek Feedback State (-10s, -20s, -30s ...)
    var doubleTapFeedbackSide by remember { mutableStateOf<String?>(null) }
    var doubleTapAccumulatedSeconds by remember { mutableIntStateOf(10) }
    var doubleTapTriggerKey by remember { mutableIntStateOf(0) }

    // Fullscreen Brightness (left) / Volume (right) Vertical Swipe Gesture State
    var gestureHudMode by remember { mutableStateOf<String?>(null) } // "BRIGHTNESS" or "VOLUME"
    var gestureHudLevel by remember { mutableFloatStateOf(0.5f) }
    var gestureHudDismissKey by remember { mutableIntStateOf(0) }
    var isVerticalGesturing by remember { mutableStateOf(false) }

    LaunchedEffect(doubleTapTriggerKey) {
        if (doubleTapFeedbackSide != null) {
            delay(750)
            doubleTapFeedbackSide = null
            doubleTapAccumulatedSeconds = 10
        }
    }

    LaunchedEffect(gestureHudDismissKey, isVerticalGesturing) {
        if (gestureHudMode != null && !isVerticalGesturing) {
            delay(900)
            gestureHudMode = null
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
                            if (isPlaying && !isLive && !isScreenLocked && !isVerticalGesturing) {
                                is2xActive = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                // Left double tap -> Rewind 10s (accumulates on rapid taps)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeekBack()
                                doubleTapAccumulatedSeconds = if (doubleTapFeedbackSide == "LEFT") {
                                    (doubleTapAccumulatedSeconds + 10).coerceAtMost(120)
                                } else 10
                                doubleTapFeedbackSide = "LEFT"
                                doubleTapTriggerKey++
                            } else if (offset.x > screenWidth * 0.62f) {
                                // Right double tap -> Fast Forward 10s (accumulates on rapid taps)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeekForward()
                                doubleTapAccumulatedSeconds = if (doubleTapFeedbackSide == "RIGHT") {
                                    (doubleTapAccumulatedSeconds + 10).coerceAtMost(120)
                                } else 10
                                doubleTapFeedbackSide = "RIGHT"
                                doubleTapTriggerKey++
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            .pointerInput(isFullscreen, isScreenLocked, manualZoomScale) {
                if (!isFullscreen || isScreenLocked || manualZoomScale > 1.05f) return@pointerInput
                var activeSide: String? = null
                var runningLevel = 0.5f
                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        val w = size.width.coerceAtLeast(1)
                        val h = size.height.coerceAtLeast(1)
                        // Ignore top/bottom system edge zones so notification shade & nav bar work normally
                        if (startOffset.y < h * 0.12f || startOffset.y > h * 0.88f) {
                            activeSide = null
                            return@detectVerticalDragGestures
                        }
                        isVerticalGesturing = true
                        if (startOffset.x < w * 0.5f) {
                            activeSide = "BRIGHTNESS"
                            val win = (context as? Activity)?.window
                            val currentWinBrightness = win?.attributes?.screenBrightness ?: -1f
                            runningLevel = if (currentWinBrightness in 0f..1f) {
                                currentWinBrightness
                            } else {
                                val sysVal = runCatching {
                                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                }.getOrDefault(128)
                                (sysVal / 255f).coerceIn(0.05f, 1f)
                            }
                            gestureHudMode = "BRIGHTNESS"
                            gestureHudLevel = runningLevel
                        } else {
                            activeSide = "VOLUME"
                            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
                            val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVol / 2)
                            runningLevel = (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                            gestureHudMode = "VOLUME"
                            gestureHudLevel = runningLevel
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        val side = activeSide ?: return@detectVerticalDragGestures
                        val sensitivityPx = (size.height * 0.65f).coerceAtLeast(220f)
                        val delta = -dragAmount / sensitivityPx
                        if (side == "BRIGHTNESS") {
                            runningLevel = (runningLevel + delta).coerceIn(0.02f, 1f)
                            (context as? Activity)?.window?.let { win ->
                                val lp = win.attributes
                                lp.screenBrightness = runningLevel
                                win.attributes = lp
                            }
                            gestureHudMode = "BRIGHTNESS"
                            gestureHudLevel = runningLevel
                        } else if (side == "VOLUME") {
                            runningLevel = (runningLevel + delta).coerceIn(0f, 1f)
                            audioManager?.let { am ->
                                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val targetVol = (runningLevel * maxVol).roundToInt().coerceIn(0, maxVol)
                                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                if (targetVol != currentVol) {
                                    runCatching {
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    }
                                }
                            }
                            gestureHudMode = "VOLUME"
                            gestureHudLevel = runningLevel
                        }
                    },
                    onDragEnd = {
                        activeSide = null
                        isVerticalGesturing = false
                        gestureHudDismissKey++
                    },
                    onDragCancel = {
                        activeSide = null
                        isVerticalGesturing = false
                        gestureHudDismissKey++
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
                            Toast.makeText(context, AppLang.tr("Screen unlocked", "স্ক্রিন আনলক হয়েছে"), Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = tr("Unlock", "আনলক করুন"),
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tr("Tap to unlock", "আনলক করতে ট্যাপ করুন"),
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
                        text = tr("2X Speed", "2X স্পিড"),
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
                        contentDescription = tr("Rewind 10 seconds", "১০ সেকেন্ড পেছনে"),
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr(
                            "-${doubleTapAccumulatedSeconds}s",
                            "-${toBengaliDigits(doubleTapAccumulatedSeconds)} সেকেন্ড"
                        ),
                        color = Color.White,
                        fontSize = 12.5.sp,
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
                color = Color.Black.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = tr("Forward 10 seconds", "১০ সেকেন্ড সামনে"),
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr(
                            "+${doubleTapAccumulatedSeconds}s",
                            "+${toBengaliDigits(doubleTapAccumulatedSeconds)} সেকেন্ড"
                        ),
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 3B. FULLSCREEN BRIGHTNESS / VOLUME GESTURE INDICATOR PILL
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = gestureHudMode != null,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (shouldApplySystemInsets) Modifier.statusBarsPadding() else Modifier)
                .padding(top = 18.dp)
        ) {
            val isBrightness = gestureHudMode == "BRIGHTNESS"
            val pct = (gestureHudLevel * 100f).roundToInt().coerceIn(0, 100)
            val accentColor = if (isBrightness) Color(0xFFFBBF24) else Color(0xFF38BDF8)
            val hudIcon = when {
                isBrightness -> Icons.Default.Brightness6
                pct == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                pct < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF071120).copy(alpha = 0.88f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = hudIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(gestureHudLevel.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(50))
                                .background(accentColor)
                        )
                    }
                    Text(
                        text = "${toBengaliDigits(pct)}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 3C. CALM STANDALONE BUFFERING SPINNER (When Controls Are Hidden)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = isBuffering && !areControlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                modifier = Modifier.size(if (isFullscreen) 62.dp else 52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(if (isFullscreen) 30.dp else 26.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 4. OVERLAY CONTROLS (Top Bar + Center Play + Bottom Seekbar)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = areControlsVisible,
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
                                Color.Black.copy(alpha = 0.80f),
                                Color.Black.copy(alpha = 0.16f),
                                Color.Black.copy(alpha = 0.88f)
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
                        modifier = Modifier.weight(if (isFullscreen) 0.45f else 0.40f)
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
                                    contentDescription = tr("Back", "ফিরে যান"),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = title.ifBlank { tr("Class Lecture", "ক্লাস লেকচার") },
                            color = Color.White,
                            fontSize = if (isFullscreen) 14.5.sp else 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Modern Action Pills Strip (No duplicate sound/audio buttons!)
                    Row(
                        modifier = Modifier
                            .weight(if (isFullscreen) 0.55f else 0.60f, fill = false)
                            .horizontalScroll(rememberScrollState()),
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
                                        contentDescription = tr("Lock screen", "স্ক্রিন লক"),
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = tr("Lock", "লক"),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // 2. Aspect Ratio Resize Toggle (Fit, Zoom, Fill)
                        val resizeModeLabel = when (resizeMode) {
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> tr("Fit", "ফিট")
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> tr("Zoom", "জুম")
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> tr("Fill", "ফুল")
                            else -> tr("Fit", "ফিট")
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
                                    contentDescription = tr("Aspect ratio", "ভিডিও সাইজ"),
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = resizeModeLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // 3. Quality Selection Tag
                        val displayQualityLabel = if (selectedQualityLabel.isBlank() || selectedQualityLabel == "অটো" || selectedQualityLabel.equals("Auto", ignoreCase = true)) {
                            tr("Auto", "অটো")
                        } else {
                            selectedQualityLabel
                        }
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
                                    contentDescription = tr("Quality", "কোয়ালিটি"),
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = displayQualityLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
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
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Equalizer Connector & Audio Control
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.clickable { onEqualizerClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = tr("Equalizer", "ইকুলাইজার"),
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = tr("EQ", "ইকুলাইজার"),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
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
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
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
                                            contentDescription = tr("Downloaded", "ডাউনলোড সম্পন্ন"),
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = tr("Saved", "সেভড"),
                                            color = Color(0xFF34D399),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
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
                                            contentDescription = tr("Download", "ডাউনলোড"),
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = tr("Download", "ডাউনলোড"),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
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
                    horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 38.dp else 24.dp)
                ) {
                    if (!isLive) {
                        val backInteraction = remember { MutableInteractionSource() }
                        val backPressed by backInteraction.collectIsPressedAsState()
                        val backScale by animateFloatAsState(
                            targetValue = if (backPressed) 0.88f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "seekBackScale"
                        )
                        // Quick 10s Rewind (minimum 48dp accessible touch target)
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.52f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .size(if (isFullscreen) 54.dp else 48.dp)
                                .graphicsLayer { scaleX = backScale; scaleY = backScale }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = backInteraction,
                                    indication = ripple(color = Color.White)
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSeekBack()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = tr("Rewind 10 seconds", "১০ সেকেন্ড পেছনে"),
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isFullscreen) 28.dp else 24.dp)
                                )
                            }
                        }
                    }

                    if (isBuffering) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.62f),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier.size(if (isFullscreen) 68.dp else 58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        val playInteraction = remember { MutableInteractionSource() }
                        val playPressed by playInteraction.collectIsPressedAsState()
                        val playScale by animateFloatAsState(
                            targetValue = if (playPressed) 0.90f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "heroPlayScale"
                        )
                        // Clean Hero Play / Pause Button
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .size(if (isFullscreen) 68.dp else 58.dp)
                                .graphicsLayer { scaleX = playScale; scaleY = playScale }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = playInteraction,
                                    indication = ripple(color = Color(0xFF0284C7))
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTogglePlayPause()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) tr("Pause", "পজ") else tr("Play", "প্লে"),
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(if (isFullscreen) 38.dp else 32.dp)
                                )
                            }
                        }
                    }

                    if (!isLive) {
                        val fwdInteraction = remember { MutableInteractionSource() }
                        val fwdPressed by fwdInteraction.collectIsPressedAsState()
                        val fwdScale by animateFloatAsState(
                            targetValue = if (fwdPressed) 0.88f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "seekFwdScale"
                        )
                        // Quick 10s Forward (minimum 48dp accessible touch target)
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.52f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier
                                .size(if (isFullscreen) 54.dp else 48.dp)
                                .graphicsLayer { scaleX = fwdScale; scaleY = fwdScale }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = fwdInteraction,
                                    indication = ripple(color = Color.White)
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSeekForward()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = tr("Forward 10 seconds", "১০ সেকেন্ড সামনে"),
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isFullscreen) 28.dp else 24.dp)
                                )
                            }
                        }
                    }
                }

                // ---------------------------------------------------------
                // BOTTOM BAR (Resume Banner + Seekbar + Localized time + Quick Actions)
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
                                            text = tr("↺ Restart", "↺ শুরু থেকে"),
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
                            val currentFormatted = ShikhoPlayerManager.formatTime(displayPosition, isBn)
                            val totalFormatted = ShikhoPlayerManager.formatTime(totalDuration, isBn)
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
                                    .padding(horizontal = 8.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
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
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                    activeTrackColor = Color(0xFF38BDF8),
                                    inactiveTrackColor = Color.Transparent
                                ),
                                thumb = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        shadowElevation = 8.dp,
                                        border = BorderStroke(2.5.dp, Color(0xFF38BDF8)),
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
                            // Localized time string
                            val timeString = "${ShikhoPlayerManager.formatTime(displayPosition, isBn)} / ${ShikhoPlayerManager.formatTime(totalDuration, isBn)}"
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
                                            text = tr("Zoom ${(manualZoomScale * 100).toInt()}% (Reset)", "জুম ${(manualZoomScale * 100).toInt()}% (রিসেট)"),
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
                                                contentDescription = tr("Restart from beginning", "শুরু থেকে দেখুন"),
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = tr("Restart", "শুরু থেকে"),
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
                                            contentDescription = if (isMuted) tr("Unmute", "সাউন্ড অন করুন") else tr("Mute", "সাউন্ড অফ করুন"),
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
                                            contentDescription = if (isAudioOnlyMode) tr("Back to video mode", "ভিডিও মোডে ফিরুন") else tr("Audio-only mode", "শুধুমাত্র অডিও শুনুন"),
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
                                            contentDescription = if (isFullscreen) tr("Exit fullscreen", "ছোট স্ক্রিন") else tr("Fullscreen", "বড় স্ক্রিন"),
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
