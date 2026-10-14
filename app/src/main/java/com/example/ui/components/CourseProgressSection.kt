package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.PhaseItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun CourseProgressSection(
    phases: List<PhaseItem>,
    onPhaseClick: (PhaseItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayPhases = phases
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (displayPhases.isNotEmpty()) {
            val completedCount = remember(displayPhases) {
                displayPhases.count {
                    it.status.equals("COMPLETED", ignoreCase = true) ||
                            ((it.course_progress_percentage ?: 0.0) > 0.0 && it.is_current != true && !it.status.equals("UPCOMING", ignoreCase = true))
                }
            }

            // Section Title with overall phase counter pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFF10B981))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF34D399)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "কোর্স প্রগ্রেস",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ধাপে ধাপে তোমার সিলেবাস অগ্রগতি",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF10B981).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "${completedCount.toString().toBengaliDigits()}/${displayPhases.size.toString().toBengaliDigits()} সম্পন্ন",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Timeline Items — LOGIC: status determination হুবহু সেম
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                displayPhases.forEachIndexed { index, phase ->
                    val isLast = index == displayPhases.size - 1

                    val isCompleted = phase.status.equals("COMPLETED", ignoreCase = true) ||
                            ((phase.course_progress_percentage ?: 0.0) > 0.0 && phase.is_current != true && !phase.status.equals("UPCOMING", ignoreCase = true))
                    val isActive = phase.is_current == true || phase.status.equals("ACTIVE", ignoreCase = true)
                    val isUpcoming = phase.status.equals("UPCOMING", ignoreCase = true) || (!isCompleted && !isActive)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Timeline Node & Connecting Line
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(42.dp)
                                .fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                                if (isActive) {
                                    val pulse = rememberInfiniteTransition(label = "nodePulse")
                                    val pulseScale by pulse.animateFloat(
                                        initialValue = 1f, targetValue = 1.32f,
                                        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                        label = "pulseScale"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                                            .border(3.dp, Color(0xFF3B82F6).copy(alpha = 0.35f), CircleShape)
                                    )
                                }

                                when {
                                    isCompleted -> {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF22C55E),
                                            shadowElevation = 3.dp,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Check, "Completed", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    isActive -> {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .border(3.dp, Color(0xFF3B82F6), CircleShape)
                                                .background(Color.White, CircleShape)
                                        ) {
                                            Box(Modifier.size(16.dp).background(Color(0xFF3B82F6), CircleShape))
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color.Transparent),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = "Locked",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (!isLast) {
                                if (isCompleted) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .weight(1f)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF22C55E), Color(0xFF10B981))
                                                )
                                            )
                                    )
                                } else {
                                    val dashColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                                    Canvas(modifier = Modifier.width(3.dp).weight(1f)) {
                                        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                        drawLine(
                                            color = dashColor,
                                            start = Offset(size.width / 2, 0f),
                                            end = Offset(size.width / 2, size.height),
                                            strokeWidth = 3.dp.toPx(),
                                            pathEffect = pathEffect
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (isLast) 0.dp else 16.dp)
                        ) {
                            QuarterCard(
                                phase = phase,
                                index = index,
                                isCompleted = isCompleted,
                                isActive = isActive,
                                isUpcoming = isUpcoming,
                                onClick = { onPhaseClick(phase) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuarterCard(
    phase: PhaseItem,
    index: Int = -1,
    isCompleted: Boolean,
    isActive: Boolean,
    isUpcoming: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // LOGIC: badge text/colors হুবহু সেম
    val (badgeText, badgeBg, badgeTextColor) = when {
        isCompleted -> Triple("পড়ানো শেষ", Color(0xFFDCFCE7), Color(0xFF15803D))
        isActive -> Triple("চলছে", Color(0xFFDBEAFE), Color(0xFF1E40AF))
        else -> Triple("ভর্তি হয়েছো", Color(0xFFDCFCE7), Color(0xFF15803D))
    }

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed && !isUpcoming) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "quarterScale"
    )

    val cardBrush = when {
        isUpcoming -> if (isDark) {
            Brush.verticalGradient(listOf(Color(0xFF131F33), Color(0xFF0F172A)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFF1F5F9), Color(0xFFE8EEF5)))
        }
        isActive -> Brush.linearGradient(listOf(Color(0xFF0B3B8C), Color(0xFF1D4ED8), Color(0xFF2563EB)))
        else -> Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF475569)))
    }

    val upcomingTitleColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val upcomingSubColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUpcoming) 0.dp else 5.dp),
        border = if (isUpcoming) {
            BorderStroke(1.dp, if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1).copy(alpha = 0.6f))
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(cardBrush)) {
            // Left accent bar
            if (!isUpcoming) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(88.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            color = if (isActive) Color(0xFF38BDF8) else Color(0xFF4ADE80),
                            shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = phase.title ?: "কোয়ার্টার",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isUpcoming) upcomingTitleColor else Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Surface(shape = RoundedCornerShape(20.dp), color = badgeBg) {
                            Text(
                                text = badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val dateRangeText = remember(phase.start_date, phase.end_date, phase.title, index) {
                        formatQuarterDateRange(phase.start_date, phase.end_date, phase.title, index)
                    }
                    if (dateRangeText.isNotBlank()) {
                        Text(
                            text = dateRangeText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isUpcoming) upcomingSubColor else Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Safe clamped & animated progress bar for active/completed
                    if (!isUpcoming) {
                        val rawPct = phase.course_progress_percentage ?: 0.0
                        val pct = rawPct.roundToInt().coerceIn(0, 100)
                        val animatedFraction by animateFloatAsState(
                            targetValue = (pct / 100f).coerceIn(0f, 1f),
                            animationSpec = tween(600, easing = FastOutSlowInEasing),
                            label = "phasePctAnim"
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.22f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedFraction)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            Brush.horizontalGradient(
                                                if (isActive) listOf(Color(0xFF93C5FD), Color(0xFF38BDF8))
                                                else listOf(Color(0xFF86EFAC), Color(0xFF4ADE80))
                                            )
                                        )
                                )
                            }

                            Text(
                                text = "${pct.toString().toBengaliDigits()}%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isUpcoming && isDark) Color(0xFF1E293B) else Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Open",
                            tint = if (isUpcoming && isDark) Color(0xFF94A3B8) else Color(0xFF334155),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== UTILITIES ====================

private val bengaliMonths = arrayOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
)

private fun parseDateSafe(dateStr: String?): java.util.Date? {
    if (dateStr.isNullOrBlank()) return null
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"
    )
    for (fmt in formats) {
        try {
            val parser = SimpleDateFormat(fmt, Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(dateStr)
            if (date != null) return date
        } catch (_: Exception) {}
    }
    try {
        val ts = dateStr.toLongOrNull()
        if (ts != null) {
            val millis = if (ts < 10000000000L) ts * 1000 else ts
            return java.util.Date(millis)
        }
    } catch (_: Exception) {}
    return null
}

private fun formatQuarterDateRange(
    startDateStr: String?,
    endDateStr: String?,
    phaseTitle: String? = null,
    index: Int = -1
): String {
    val start = parseDateSafe(startDateStr)
    val end = parseDateSafe(endDateStr)
    val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
    val cal = Calendar.getInstance(dhakaZone)

    if (start != null || end != null) {
        fun formatSingle(date: java.util.Date): String {
            cal.time = date
            val monthIdx = cal.get(Calendar.MONTH)
            val monthName = bengaliMonths.getOrElse(monthIdx) { "" }
            val year = cal.get(Calendar.YEAR) % 100
            val yearBn = String.format(Locale.US, "%02d", year).toBengaliDigits()
            return "$monthName'$yearBn"
        }

        val startBn = start?.let { formatSingle(it) } ?: ""
        val endBn = end?.let { formatSingle(it) } ?: ""

        if (startBn.isNotBlank() && endBn.isNotBlank()) {
            return "$startBn - $endBn"
        } else if (startBn.isNotBlank()) {
            return "শুরু: $startBn"
        } else if (endBn.isNotBlank()) {
            return "পর্যন্ত: $endBn"
        }
    }

    // Dynamic year calculation instead of static hardcoded year
    val currentYearShort = cal.get(Calendar.YEAR) % 100
    val prevYearBn = String.format(Locale.US, "%02d", (currentYearShort - 1).coerceAtLeast(0)).toBengaliDigits()
    val currYearBn = String.format(Locale.US, "%02d", currentYearShort).toBengaliDigits()

    return when {
        phaseTitle?.contains("১") == true || phaseTitle?.contains("1") == true || index == 0 -> "সেপ্টেম্বর'$prevYearBn - ডিসেম্বর'$prevYearBn"
        phaseTitle?.contains("২") == true || phaseTitle?.contains("2") == true || index == 1 -> "জানুয়ারি'$currYearBn - মার্চ'$currYearBn"
        phaseTitle?.contains("৩") == true || phaseTitle?.contains("3") == true || index == 2 -> "এপ্রিল'$currYearBn - জুন'$currYearBn"
        phaseTitle?.contains("৪") == true || phaseTitle?.contains("4") == true || index == 3 -> "জুলাই'$currYearBn - সেপ্টেম্বর'$currYearBn"
        phaseTitle?.contains("৫") == true || phaseTitle?.contains("5") == true || index == 4 -> "অক্টোবর'$currYearBn - ডিসেম্বর'$currYearBn"
        else -> "সেপ্টেম্বর'$prevYearBn - ডিসেম্বর'$prevYearBn"
    }
}

val defaultCoursePhases = listOf(
    PhaseItem(
        id = "6864d4e806800acba2e270e1",
        academic_program_id = "6864d3a806800acba2e27099",
        title = "কোয়ার্টার ১",
        status = "COMPLETED",
        is_current = false,
        has_enrolment = true,
        has_free_trial_enrolment = false,
        course_progress_percentage = 0.53,
        start_date = "2025-08-31T18:00:00Z",
        end_date = "2025-12-31T17:59:59Z"
    ),
    PhaseItem(
        id = "6864d4e806800acba2e270e2",
        academic_program_id = "6864d3a806800acba2e27099",
        title = "কোয়ার্টার ২",
        status = "COMPLETED",
        is_current = false,
        has_enrolment = true,
        has_free_trial_enrolment = false,
        course_progress_percentage = 5.90,
        start_date = "2025-12-31T18:00:00Z",
        end_date = "2026-03-31T17:59:59Z"
    ),
    PhaseItem(
        id = "6864d4e806800acba2e270e3",
        academic_program_id = "6864d3a806800acba2e27099",
        title = "কোয়ার্টার ৩",
        status = "COMPLETED",
        is_current = false,
        has_enrolment = true,
        has_free_trial_enrolment = false,
        course_progress_percentage = 4.51,
        start_date = "2026-03-31T18:00:00Z",
        end_date = "2026-06-30T17:59:59Z"
    ),
    PhaseItem(
        id = "6864d4e806800acba2e270e4",
        academic_program_id = "6864d3a806800acba2e27099",
        title = "কোয়ার্টার ৪",
        status = "ACTIVE",
        is_current = true,
        has_enrolment = true,
        has_free_trial_enrolment = false,
        course_progress_percentage = 8.24,
        start_date = "2026-06-30T18:00:00Z",
        end_date = "2026-09-30T17:59:59Z"
    ),
    PhaseItem(
        id = "6864d4e806800acba2e270e5",
        academic_program_id = "6864d3a806800acba2e27099",
        title = "কোয়ার্টার ৫",
        status = "UPCOMING",
        is_current = false,
        has_enrolment = true,
        has_free_trial_enrolment = false,
        course_progress_percentage = 0.0,
        start_date = "2026-09-30T18:00:00Z",
        end_date = "2026-12-31T17:59:59Z"
    )
)
