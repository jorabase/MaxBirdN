package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.VideoPlaybackProgressEntity
import com.example.player.ShikhoPlayerManager
import com.example.ui.theme.isAppInDarkTheme
import com.example.utils.isBangla
import com.example.utils.toBengaliDigits
import com.example.utils.tr
import kotlin.math.roundToInt

/**
 * Flagship "Continue Learning" resume card for the Home screen.
 * Surfaces the student's most recently paused class with one-tap resume,
 * animated progress bar, remaining-time indicator, and optional dismiss action.
 */
@Composable
fun ContinueLearningCard(
    item: VideoPlaybackProgressEntity,
    onResumeClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val isBn = isBangla()
    val haptic = LocalHapticFeedback.current

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "continueCardScale"
    )

    val rawProgress = remember(item.lastPositionMs, item.totalDurationMs, item.percentage) {
        when {
            item.totalDurationMs > 0L -> (item.lastPositionMs.toFloat() / item.totalDurationMs.toFloat()).coerceIn(0.03f, 0.99f)
            item.percentage > 0f -> item.percentage.coerceIn(0.03f, 0.99f)
            else -> 0.12f
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "continueProgressBar"
    )
    val percentInt = (rawProgress * 100f).roundToInt().coerceIn(1, 99)

    val watchedFormatted = remember(item.lastPositionMs, isBn) {
        ShikhoPlayerManager.formatTime(item.lastPositionMs, isBn)
    }
    val remainingLabel = remember(item.lastPositionMs, item.totalDurationMs, isBn) {
        if (item.totalDurationMs > item.lastPositionMs) {
            val remainingMins = ((item.totalDurationMs - item.lastPositionMs) / 60_000L).coerceAtLeast(1L)
            if (isBn) "${toBengaliDigits(remainingMins)} মি. বাকি" else "${remainingMins}m left"
        } else {
            if (isBn) "$watchedFormatted থেকে শুরু" else "Resume at $watchedFormatted"
        }
    }

    val cardGradient = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0A1931),
                Color(0xFF102A54),
                Color(0xFF0C2244)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF0F9FF),
                Color(0xFFEFF6FF)
            )
        )
    }

    val borderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF38BDF8).copy(alpha = 0.45f),
                Color(0xFF6366F1).copy(alpha = 0.28f),
                Color(0xFF38BDF8).copy(alpha = 0.22f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0284C7).copy(alpha = 0.32f),
                Color(0xFF38BDF8).copy(alpha = 0.22f),
                Color(0xFF0284C7).copy(alpha = 0.16f)
            )
        )
    }

    val titleColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subtitleColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
    val accentCyan = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.2.dp, borderBrush),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 8.dp else 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .shadow(
                elevation = if (isDark) 14.dp else 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0xFF0284C7).copy(alpha = if (isDark) 0.36f else 0.18f)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = accentCyan),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onResumeClick()
                }
            )
            .testTag("home_continue_learning_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
        ) {
            // Subtle ambient top-right radial glow
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 34.dp, y = (-34).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentCyan.copy(alpha = if (isDark) 0.24f else 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header row: Continue badge + subject pill + subtle dismiss button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentCyan)
                        )
                        Text(
                            text = tr("Continue Learning", "পড়া চালিয়ে যান"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                            color = accentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.subjectName.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accentCyan.copy(alpha = if (isDark) 0.16f else 0.10f),
                                border = BorderStroke(0.8.dp, accentCyan.copy(alpha = 0.28f))
                            ) {
                                Text(
                                    text = item.subjectName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Compact accessible dismiss action
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDismissClick()
                                }
                            )
                            .testTag("home_continue_learning_dismiss")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("Dismiss", "সরিয়ে দিন"),
                            tint = subtitleColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Main content row: Title + Time metadata + Glowing Play Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = titleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 20.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = subtitleColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = remainingLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = subtitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = subtitleColor.copy(alpha = 0.55f)
                            )
                            Text(
                                text = "${toBengaliDigits(percentInt)}%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentCyan
                            )
                        }
                    }

                    // Glowing circular resume play button (48.dp touch target)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                spotColor = Color(0xFF0284C7).copy(alpha = 0.55f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0EA5E9), Color(0xFF2563EB))
                                )
                            )
                            .border(1.2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = tr("Resume Class", "ক্লাস চালু করুন"),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Sleek animated gradient progress track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.12f)
                            else Color(0xFF0284C7).copy(alpha = 0.12f)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF06B6D4),
                                        Color(0xFF3B82F6),
                                        Color(0xFF6366F1)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}
