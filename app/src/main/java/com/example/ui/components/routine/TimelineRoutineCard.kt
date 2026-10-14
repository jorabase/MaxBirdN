package com.example.ui.components.routine

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.utils.ClassTypeUtils
import com.example.utils.RoutineDateUtils
import com.example.utils.SubjectColorUtils

@Composable
fun TimelineRoutineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val st = lesson.start_time ?: lesson.live_class?.start_time
    val et = lesson.end_time ?: lesson.live_class?.end_time

    val startCal = remember(st) { RoutineDateUtils.parseIsoToDhakaCalendar(st) }
    val endCal = remember(et) { RoutineDateUtils.parseIsoToDhakaCalendar(et, isEndOfDay = true) }

    val formattedTime = remember(startCal, endCal) {
        val timeRange = RoutineDateUtils.formatTimeRange(startCal, endCal)
        val duration = RoutineDateUtils.calculateDurationText(startCal, endCal)
        if (duration.isNotBlank()) "$timeRange • $duration" else timeRange
    }

    val isModelTest = lesson.isModelTest
    val isLiveExam = lesson.isLiveExam
    val isExam = lesson.isExam
    val isLive = lesson.isLive

    val nowMs = System.currentTimeMillis()
    val startMs = startCal?.timeInMillis ?: Long.MAX_VALUE
    val endMs = endCal?.timeInMillis ?: (if (startMs != Long.MAX_VALUE) startMs + (90 * 60 * 1000L) else Long.MAX_VALUE)

    val isLiveNow = lesson.isLiveNow
    val isExamNow = isExam && (startMs != Long.MAX_VALUE && nowMs in (startMs - 5 * 60 * 1000L)..endMs)
    val isModelTestNow = isModelTest && (startMs != Long.MAX_VALUE && nowMs in (startMs - 5 * 60 * 1000L)..endMs)

    val classTypeBadge = ClassTypeUtils.getClassTypeBadgeStyle(lesson)
    val typeText = when {
        isLiveNow -> "🔴 লাইভ চলছে"
        isModelTestNow -> "✍️ মডেল টেস্ট চলছে"
        isModelTest -> "✍️ মডেল টেস্ট (Model Test)"
        isExamNow -> "✍️ পরীক্ষা চলছে"
        isLiveExam || isExam -> "✍️ চ্যাপ্টার এক্সাম"
        else -> classTypeBadge.label
    }

    val subjectName = lesson.subject_name ?: "বিষয়"
    val titleText = ClassTypeUtils.formatLessonTitle(lesson.title ?: lesson.live_class?.chapter_name ?: "অনলাইন ক্লাস")
    val subjectColors = SubjectColorUtils.getColorScheme(subjectName)

    val handleCardClick = { onClick() }

    val titleFontSize = when {
        titleText.length > 50 -> 12.sp
        titleText.length > 30 -> 13.sp
        else -> 14.sp
    }
    val titleLineHeight = when {
        titleText.length > 50 -> 16.sp
        titleText.length > 30 -> 17.5.sp
        else -> 19.sp
    }

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tlCardScale"
    )

    val liveTransition = rememberInfiniteTransition(label = "tlLivePulse")
    val liveDotScale by liveTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tlLiveDotScale"
    )

    val primaryAccent = when {
        isLiveNow -> Color(0xFFEF4444)
        isModelTestNow || isModelTest -> Color(0xFF7C3AED)
        isExamNow || isExam -> Color(0xFFD97706)
        else -> Color(0xFF0284C7)
    }

    val cardContainerColor = if (isDark) {
        when {
            isLiveNow -> Color(0xFF281117)
            isModelTestNow || isModelTest -> Color(0xFF1F1435)
            isExamNow || isExam -> Color(0xFF261D10)
            else -> MaterialTheme.colorScheme.surface
        }
    } else {
        when {
            isLiveNow -> Color(0xFFFFF1F2)
            isModelTestNow || isModelTest -> Color(0xFFFAF5FF)
            isExamNow -> Color(0xFFFFFBEB)
            isExam -> Color(0xFFFEF3C7).copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surface
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline dot + Vertical track line
        Column(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(primaryAccent.copy(alpha = if (isDark) 0.25f else 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isLiveNow || isExamNow || isModelTestNow) 10.dp else 8.5.dp)
                        .graphicsLayer {
                            if (isLiveNow || isExamNow || isModelTestNow) {
                                scaleX = liveDotScale
                                scaleY = liveDotScale
                            }
                        }
                        .clip(CircleShape)
                        .background(primaryAccent)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                primaryAccent.copy(alpha = 0.45f),
                                primaryAccent.copy(alpha = 0.1f)
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Main Card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                .clickable(interactionSource = interaction, indication = null, onClick = handleCardClick),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardContainerColor),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isLiveNow || isExamNow || isModelTestNow) 4.dp else 2.dp
            ),
            border = when {
                isLiveNow -> BorderStroke(1.5.dp, Color(0xFFEF4444))
                isModelTestNow -> BorderStroke(1.5.dp, Color(0xFF7C3AED))
                isModelTest -> BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.5f))
                isExamNow -> BorderStroke(1.5.dp, Color(0xFFF59E0B))
                isExam -> BorderStroke(1.dp, Color(0xFFFCD34D).copy(alpha = 0.7f))
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Top Tag Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLiveNow) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEF4444)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .graphicsLayer { scaleX = liveDotScale; scaleY = liveDotScale }
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Text("লাইভ ক্লাস", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        } else if (isExamNow) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEA580C)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .graphicsLayer { scaleX = liveDotScale; scaleY = liveDotScale }
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Text("লাইভ এক্সাম", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = subjectColors.backgroundColor,
                            border = BorderStroke(0.8.dp, subjectColors.textColor.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = subjectName,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = subjectColors.textColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isLiveNow && !isExamNow) {
                            val (posLabel, posBg, posColor) = when {
                                isLiveExam || isExam || isModelTest -> when {
                                    lesson.isExamUpcoming -> Triple("আপকামিং এক্সাম", Color(0xFFEDE9FE), Color(0xFF7C3AED))
                                    else -> Triple("রেকর্ড এক্সাম", Color(0xFFFEF3C7), Color(0xFFD97706))
                                }
                                lesson.isUpcoming -> Triple("আপকামিং ক্লাস", Color(0xFFE0F2FE), Color(0xFF0284C7))
                                else -> Triple("রেকর্ড ক্লাস", Color(0xFFEFF6FF), Color(0xFF2563EB))
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = posBg
                            ) {
                                Text(
                                    text = posLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = posColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = titleText,
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (formattedTime.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = primaryAccent.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = formattedTime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = primaryAccent.copy(alpha = 0.14f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open",
                            tint = primaryAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
