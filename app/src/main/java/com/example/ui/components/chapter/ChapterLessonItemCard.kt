package com.example.ui.components.chapter

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.utils.ClassTypeUtils
import com.example.utils.toBengaliDigits
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Formats ISO date string to Bengali readable date & time.
 */
fun formatChapterLessonDate(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(rawDate)
        if (date != null) {
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("Asia/Dhaka")
            }
            toBengaliDigits(formatter.format(date))
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

/**
 * Distinctly styled card separating Live Classes, Recorded Classes, and Exams.
 */
@Composable
fun ChapterLessonItemCard(
    lesson: StudentLessonItem,
    isCompleted: Boolean,
    subjectColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isModelTest = lesson.isModelTest
    val isLiveExam = lesson.isLiveExam
    val isExam = lesson.isExam
    val isLiveNow = lesson.isLiveNow
    val isUpcoming = lesson.isUpcoming
    val isLive = isLiveNow || lesson.isLive
    val isRecorded = lesson.isRecorded

    val state = lesson.user_activity_state?.uppercase() ?: ""
    val isCompletedEffective = isCompleted || state == "COMPLETED" || state == "ATTENDED"

    // Live Pulse Animation
    val liveTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by liveTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Distinct Theme Colors based on Type
    val (typeTitle, typeTagBg, typeTagColor, cardBorderColor, cardIcon, actionButtonText, actionButtonColor) = when {
        isCompletedEffective -> Tuple7(
            "সম্পন্ন ক্লাস",
            Color(0xFF10B981).copy(alpha = 0.15f),
            Color(0xFF059669),
            Color(0xFF10B981).copy(alpha = 0.35f),
            Icons.Default.CheckCircle,
            "পুনরায় দেখুন",
            Color(0xFF059669)
        )
        isModelTest -> Tuple7(
            "মডেল টেস্ট",
            Color(0xFF7C3AED).copy(alpha = 0.15f),
            Color(0xFF7C3AED),
            Color(0xFF7C3AED).copy(alpha = 0.45f),
            Icons.Default.Quiz,
            "মডেল টেস্ট দিন",
            Color(0xFF7C3AED)
        )
        isLiveExam || isExam -> Tuple7(
            "লাইভ চ্যাপ্টার পরীক্ষা",
            Color(0xFFF59E0B).copy(alpha = 0.15f),
            Color(0xFFD97706),
            Color(0xFFF59E0B).copy(alpha = 0.45f),
            Icons.Default.Assignment,
            "পরীক্ষা দিন",
            Color(0xFFD97706)
        )
        isLiveNow -> Tuple7(
            "🔴 লাইভ ক্লাস চলছে",
            Color(0xFFEF4444).copy(alpha = 0.18f),
            Color(0xFFDC2626),
            Color(0xFFEF4444).copy(alpha = 0.6f),
            Icons.Default.LiveTv,
            "লাইভে যোগ দিন",
            Color(0xFFDC2626)
        )
        isUpcoming -> Tuple7(
            "আসন্ন লাইভ ক্লাস",
            Color(0xFF0284C7).copy(alpha = 0.15f),
            Color(0xFF0284C7),
            Color(0xFF0284C7).copy(alpha = 0.35f),
            Icons.Default.Schedule,
            "ক্লাস রুটিন",
            Color(0xFF0284C7)
        )
        else -> Tuple7(
            "রেকর্ড ক্লাস / ভিডিও লেকচার",
            Color(0xFF3B82F6).copy(alpha = 0.15f),
            Color(0xFF2563EB),
            Color(0xFF3B82F6).copy(alpha = 0.3f),
            Icons.Default.PlayCircleFilled,
            "ভিডিও দেখুন",
            Color(0xFF2563EB)
        )
    }

    val startTimeFormatted = remember(lesson.live_class?.start_time ?: lesson.start_time) {
        formatChapterLessonDate(lesson.live_class?.start_time ?: lesson.start_time)
    }

    val formattedTitle = remember(lesson.title) {
        ClassTypeUtils.formatLessonTitle(lesson.title)
    }

    val classTypeBadge = remember(lesson) {
        ClassTypeUtils.getClassTypeBadgeStyle(lesson)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLiveNow) 4.dp else 1.5.dp),
        border = BorderStroke(
            width = if (isLiveNow) 1.5.dp else 1.dp,
            color = cardBorderColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Type Badge + Additional Class Tag + Live Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Type Badge (লাইভ ক্লাস / রেকর্ড ক্লাস / লাইভ পরীক্ষা)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = typeTagBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isLiveNow) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(typeTagColor.copy(alpha = pulseAlpha))
                                )
                            }
                            Text(
                                text = typeTitle,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = typeTagColor
                            )
                        }
                    }

                    // Secondary Specific Badge (লেকচার ক্লাস, ডাউট ক্লাস, সলভিং ক্লাস)
                    if (classTypeBadge.label != typeTitle) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = classTypeBadge.backgroundColor
                        ) {
                            Text(
                                text = classTypeBadge.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = classTypeBadge.textColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // If Completed, show green checkmark
                if (isCompletedEffective) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle: Icon + Title + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = typeTagBg,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = cardIcon,
                            contentDescription = null,
                            tint = typeTagColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formattedTitle,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (startTimeFormatted.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = startTimeFormatted,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeTagBg.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isModelTest -> if (!lesson.model_test?.type.isNullOrBlank()) "${lesson.model_test?.type} মডেল টেস্ট • পূর্ণাঙ্গ মূল্যায়ন" else "পূর্ণাঙ্গ মডেল টেস্ট ও জাতীয় র‍্যাংকিং"
                        isLiveExam || isExam -> "অধ্যায় পরীক্ষা এবং মূল্যায়ন"
                        isLiveNow -> "সরাসরি শিক্ষক ও সহপাঠীদের সাথে"
                        isUpcoming -> "নির্ধারিত সময়ে ক্লাস শুরু হবে"
                        isRecorded -> "রেকর্ড ভিডিও ও লেকচার শিট"
                        else -> "ক্লাস ও স্টাডি মেটেরিয়াল"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = actionButtonText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = actionButtonColor
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = actionButtonColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private data class Tuple7<A, B, C, D, E, F, G>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
)
