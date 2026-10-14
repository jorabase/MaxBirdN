package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.utils.ClassTypeUtils
import com.example.utils.SubjectColorUtils
import java.text.SimpleDateFormat
import java.util.*

// ==================== UNCHANGED UTILITIES ====================

fun String.toBengaliDigits(): String = com.example.utils.toBengaliDigits(this)

data class RoutineDayItem(
    val dayIndex: Int,
    val shortDayName: String,
    val fullDayName: String,
    val dateNumBn: String,
    val fullDateBn: String,
    val dateKey: String,
    val isToday: Boolean,
    val lessons: List<StudentLessonItem>,
    val classCount: Int,
    val examCount: Int
)

data class DayColorScheme(
    val selectedGradient: List<Color>,
    val selectedDotColor: Color,
    val unselectedBg: Color,
    val unselectedText: Color,
    val accentBorder: Color
)

fun getDayColorScheme(dayIndex: Int): DayColorScheme {
    return when (dayIndex % 7) {
        0 -> DayColorScheme(listOf(Color(0xFF4F46E5), Color(0xFF6366F1)), Color.White, Color(0xFFEEF2FF), Color(0xFF4338CA), Color(0xFF818CF8))
        1 -> DayColorScheme(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)), Color.White, Color(0xFFE0F2FE), Color(0xFF0369A1), Color(0xFF38BDF8))
        2 -> DayColorScheme(listOf(Color(0xFF059669), Color(0xFF10B981)), Color.White, Color(0xFFD1FAE5), Color(0xFF047857), Color(0xFF34D399))
        3 -> DayColorScheme(listOf(Color(0xFFEA580C), Color(0xFFF97316)), Color.White, Color(0xFFFFEDD5), Color(0xFFC2410C), Color(0xFFFB923C))
        4 -> DayColorScheme(listOf(Color(0xFF9333EA), Color(0xFFC084FC)), Color.White, Color(0xFFF3E8FF), Color(0xFF7E22CE), Color(0xFFC084FC))
        5 -> DayColorScheme(listOf(Color(0xFFE11D48), Color(0xFFFB7185)), Color.White, Color(0xFFFFE4E6), Color(0xFFBE123C), Color(0xFFFB7185))
        else -> DayColorScheme(listOf(Color(0xFF0D9488), Color(0xFF2DD4BF)), Color.White, Color(0xFFCCFBF1), Color(0xFF0F766E), Color(0xFF2DD4BF))
    }
}

// ==================== IMPROVED UI ====================

@Composable
fun WeeklyRoutineSection(
    lessons: List<StudentLessonItem>,
    isLoading: Boolean,
    onSeeAllClick: () -> Unit = {},
    onCustomizeSubjectsClick: () -> Unit = {},
    selectedSubjectsCount: Int = 0,
    totalSubjectsCount: Int = 0,
    selectedSubjectNames: List<String> = emptyList(),
    onOpenLessonDetail: (StudentLessonItem) -> Unit = {},
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val dhakaZone = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val todayCal = Calendar.getInstance(dhakaZone)
    val todayDateKey = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }.format(todayCal.time)
    }

    // ===== LOGIC: daysList calculation (হুবহু সেম) =====
    val daysList = remember(lessons, todayDateKey) {
        val satCal = todayCal.clone() as Calendar
        satCal.set(Calendar.HOUR_OF_DAY, 0)
        satCal.set(Calendar.MINUTE, 0)
        satCal.set(Calendar.SECOND, 0)
        satCal.set(Calendar.MILLISECOND, 0)

        while (satCal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            satCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        val keyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }
        val dayNumFormatter = SimpleDateFormat("dd", Locale.US).apply { timeZone = dhakaZone }
        val fullDateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = dhakaZone }

        (0..6).map { offset ->
            val dayCal = satCal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, offset)

            val dateKey = keyFormatter.format(dayCal.time)
            val dateNum = dayNumFormatter.format(dayCal.time).toBengaliDigits()

            val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
            val (shortName, fullName) = when (dayOfWeek) {
                Calendar.SATURDAY -> "শনি" to "শনিবার"
                Calendar.SUNDAY -> "রবি" to "রবিবার"
                Calendar.MONDAY -> "সোম" to "সোমবার"
                Calendar.TUESDAY -> "মঙ্গল" to "মঙ্গলবার"
                Calendar.WEDNESDAY -> "বুধ" to "বুধবার"
                Calendar.THURSDAY -> "বৃহ" to "বৃহস্পতিবার"
                Calendar.FRIDAY -> "শুক্র" to "শুক্রবার"
                else -> "শনি" to "শনিবার"
            }

            val fullDateBn = "$fullName, ${fullDateFormatter.format(dayCal.time).toBengaliDigits()}"

            val rawDayLessons = lessons.filter { lesson ->
                val startTime = lesson.start_time ?: lesson.live_class?.start_time
                if (startTime.isNullOrBlank()) false
                else {
                    val lessonCal = parseIsoToDhakaCalendar(startTime)
                    if (lessonCal != null) {
                        val lessonDateKey = keyFormatter.format(lessonCal.time)
                        lessonDateKey == dateKey
                    } else false
                }
            }
            val dayLessons = sortRoutineLessons(rawDayLessons)

            val classCount = dayLessons.count {
                it.content_type == "LiveClass" || it.content_type == "RecordedClass" ||
                        it.content_type == "Lesson" || it.content_type == null
            }
            val examCount = dayLessons.count {
                it.content_type == "LiveExam" || it.content_type == "ModelTest" || it.content_type == "Quiz"
            }

            RoutineDayItem(
                offset, shortName, fullName, dateNum, fullDateBn,
                dateKey, dateKey == todayDateKey, dayLessons, classCount, examCount
            )
        }
    }

    // ===== LOGIC: selectedDayIndex (সেম) =====
    var selectedDayIndex by remember(daysList) {
        val todayIdx = daysList.indexOfFirst { it.isToday }
        mutableIntStateOf(if (todayIdx >= 0) todayIdx else 0)
    }

    val selectedDay = daysList.getOrNull(selectedDayIndex) ?: daysList.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---------- 1. Header Row ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFF0284C7))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "সাপ্তাহিক রুটিন",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "আজকের ক্লাস ও পরীক্ষার সময়সূচি",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Gradient "সব দেখুন" pill
            Surface(
                onClick = onSeeAllClick,
                shape = RoundedCornerShape(50),
                color = Color.Transparent,
                shadowElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier
                        .background(Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF2563EB))))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text("সব দেখুন", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------- 2. 7-Day Strip — প্রতিদিন আলাদা রঙ + spring bounce ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysList.forEach { day ->
                val isSelected = day.dayIndex == selectedDayIndex
                val scheme = getDayColorScheme(day.dayIndex)
                val hasEvents = (day.classCount + day.examCount) > 0

                val pillScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.06f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "pillScale${day.dayIndex}"
                )

                val unselectedBrush = if (isDark) {
                    Brush.verticalGradient(listOf(Color(0xFF0F1B2D), Color(0xFF16263E)))
                } else {
                    Brush.verticalGradient(listOf(scheme.unselectedBg, Color.White))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.5.dp)
                        .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                        .shadow(
                            elevation = if (isSelected) 6.dp else 0.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = scheme.accentBorder
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) Brush.verticalGradient(scheme.selectedGradient)
                            else unselectedBrush
                        )
                        .border(
                            width = when {
                                isSelected -> 1.5.dp
                                day.isToday -> 1.2.dp
                                else -> 1.dp
                            },
                            color = when {
                                isSelected -> scheme.accentBorder
                                day.isToday -> Color(0xFF38BDF8).copy(alpha = 0.65f)
                                isDark -> Color(0xFF1E293B)
                                else -> scheme.accentBorder.copy(alpha = 0.25f)
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedDayIndex = day.dayIndex }
                        .padding(vertical = 11.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.shortDayName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> Color.White
                            isDark -> Color(0xFF94A3B8)
                            else -> scheme.unselectedText
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = day.dateNumBn,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            isSelected -> Color.White
                            isDark -> Color(0xFFE2E8F0)
                            else -> Color(0xFF0F172A)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Event indicator dots
                    if (isSelected) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(Color.White))
                            if (hasEvents) {
                                Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(if (day.isToday) 5.5.dp else 4.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasEvents) scheme.accentBorder
                                    else scheme.accentBorder.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------- 3. Summary Row ----------
        if (selectedDay != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedDay.fullDateBn,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF0284C7)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ক্লাস ${selectedDay.classCount.toString().toBengaliDigits()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("|", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "এক্সাম ${selectedDay.examCount.toString().toBengaliDigits()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---------- 4. Lessons / Empty / Loading ----------
            AnimatedContent(
                targetState = Triple(isLoading, selectedDay.dateKey, selectedDay.lessons),
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "routineDayTransition"
            ) { (loading, _, dayLessons) ->
                when {
                    loading -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.2.dp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "রুটিন লোড হচ্ছে...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    dayLessons.isEmpty() -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF131F33).copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "আজকে কোন ক্লাস বা পরীক্ষা নেই ✨",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    else -> {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(end = 16.dp)
                        ) {
                            itemsIndexed(
                                items = dayLessons,
                                key = { idx, item -> "${item.id ?: idx}_${item.start_time ?: idx}" }
                            ) { _, lesson ->
                                ShikhoRoutineCard(
                                    lesson = lesson,
                                    onClick = { onOpenLessonDetail(lesson) },
                                    onNavigateToExam = onNavigateToExam
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- 5. সাবজেক্ট সাজাও Bento Card ----------
        val bentoInteraction = remember { MutableInteractionSource() }
        val isBentoPressed by bentoInteraction.collectIsPressedAsState()
        val bentoScale by animateFloatAsState(
            targetValue = if (isBentoPressed) 0.98f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "bentoScale"
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = bentoScale; scaleY = bentoScale }
                .clip(RoundedCornerShape(22.dp))
                .clickable(interactionSource = bentoInteraction, indication = null) { onCustomizeSubjectsClick() },
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF071731),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF1E3A5F))
                )
            ),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF071731), Color(0xFF0E2952))))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.22f))
                                .border(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "সাবজেক্ট সাজাও",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(3.5.dp)) {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF10B981)))
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = if (selectedSubjectsCount > 0 && totalSubjectsCount > 0)
                                    "${selectedSubjectsCount.toString().toBengaliDigits()}/${totalSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                                else if (selectedSubjectsCount > 0)
                                    "${selectedSubjectsCount.toString().toBengaliDigits()} টি বিষয় নির্বাচন করা আছে"
                                else
                                    "তোমার প্রয়োজনীয় সাবজেক্টগুলো বেছে নাও",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF0284C7),
                        border = BorderStroke(1.dp, Color(0xFF7DD3FC).copy(alpha = 0.4f)),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FilterList, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("ফিল্টার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (selectedSubjectNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(selectedSubjectNames) { index, subName ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F2547),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF38BDF8)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${(index + 1).toString().toBengaliDigits()}. $subName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF1F5F9)
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

/**
 * Routine Card — Compact, premium M3 design with an adaptive (screen-relative) width
 * and full dark/light mode contrast support.
 */
@Composable
fun ShikhoRoutineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val startTime = lesson.start_time ?: lesson.live_class?.start_time
    val endTime = lesson.end_time ?: lesson.live_class?.end_time
    val startCal = remember(startTime) { parseIsoToDhakaCalendar(startTime, isEndOfDay = false) }
    val endCal = remember(endTime) { parseIsoToDhakaCalendar(endTime, isEndOfDay = true) }
    val timeString = remember(startCal, endCal) { formatTimeRange(startCal, endCal) }
    val durationString = remember(startCal, endCal) { calculateDurationText(startCal, endCal) }

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

    val subjectName = lesson.subject_name ?: "বিষয়"
    val titleText = ClassTypeUtils.formatLessonTitle(lesson.title ?: lesson.live_class?.chapter_name ?: "অনলাইন ক্লাস")
    val subjectColors = SubjectColorUtils.getColorScheme(subjectName)

    val handleCardClick = { onClick() }

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "routineCardScale"
    )

    val liveTransition = rememberInfiniteTransition(label = "livePulse")
    val liveDotScale by liveTransition.animateFloat(
        initialValue = 0.75f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liveDotScale"
    )

    val cardBorderColor = when {
        isLiveNow -> Color(0xFFEF4444)
        isModelTestNow -> Color(0xFF7C3AED)
        isExamNow -> Color(0xFFF59E0B)
        isModelTest -> Color(0xFF7C3AED).copy(alpha = 0.7f)
        isExam -> Color(0xFFFBBF24).copy(alpha = 0.75f)
        lesson.isRecordingProcessing -> Color(0xFFF59E0B).copy(alpha = 0.75f)
        lesson.isUpcoming -> Color(0xFF0284C7).copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    val cardBackgroundGradient = if (isDark) {
        when {
            isLiveNow -> Brush.linearGradient(listOf(Color(0xFF2D1219), Color(0xFF1F0D12)))
            isModelTestNow || isModelTest -> Brush.linearGradient(listOf(Color(0xFF23153C), Color(0xFF160F29)))
            isExamNow || isExam -> Brush.linearGradient(listOf(Color(0xFF2B2111), Color(0xFF1C160B)))
            lesson.isUpcoming -> Brush.linearGradient(listOf(Color(0xFF0F2238), Color(0xFF0B1829)))
            else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)))
        }
    } else {
        when {
            isLiveNow -> Brush.linearGradient(listOf(Color(0xFFFFF1F2), Color(0xFFFFE4E6)))
            isModelTestNow || isModelTest -> Brush.linearGradient(listOf(Color(0xFFFAF5FF), Color(0xFFF3E8FF).copy(alpha = 0.6f)))
            isExamNow -> Brush.linearGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)))
            isExam -> Brush.linearGradient(listOf(Color(0xFFFFFDF5), Color(0xFFFEF9C3).copy(alpha = 0.45f)))
            lesson.isRecordingProcessing -> Brush.linearGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7).copy(alpha = 0.6f)))
            lesson.isUpcoming -> Brush.linearGradient(listOf(Color(0xFFF0F9FF), Color(0xFFE0F2FE).copy(alpha = 0.6f)))
            else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)))
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (screenWidthDp * 0.62f).coerceIn(194.dp, 244.dp)

    Surface(
        modifier = modifier
            .width(cardWidth)
            .height(132.dp)
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = handleCardClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isLiveNow || isExamNow) 1.5.dp else 1.dp, cardBorderColor),
        shadowElevation = if (isLiveNow || isExamNow) 6.dp else 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBackgroundGradient)
        ) {
            // Top accent strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.5.dp)
                    .background(
                        when {
                            isLiveNow -> Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFF43F5E)))
                            isModelTestNow || isModelTest -> Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFA855F7)))
                            isExamNow -> Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                            isExam -> Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFFCD34D)))
                            lesson.isUpcoming -> Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF38BDF8)))
                            else -> Brush.horizontalGradient(listOf(subjectColors.textColor, subjectColors.textColor.copy(alpha = 0.4f)))
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Subject Tag
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = subjectColors.backgroundColor.copy(alpha = 0.92f),
                        border = BorderStroke(0.8.dp, subjectColors.textColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = subjectName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = subjectColors.textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Status / Exam / Live Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isLiveNow -> Color(0xFFFEE2E2)
                            isModelTestNow || isModelTest -> Color(0xFFEDE9FE)
                            isExamNow -> Color(0xFFFEF3C7)
                            isExam -> Color(0xFFFFF7ED)
                            lesson.isUpcoming -> Color(0xFFE0F2FE)
                            else -> Color(0xFFF1F5F9)
                        },
                        border = BorderStroke(
                            0.8.dp,
                            when {
                                isLiveNow -> Color(0xFFEF4444).copy(alpha = 0.4f)
                                isModelTestNow || isModelTest -> Color(0xFF7C3AED).copy(alpha = 0.35f)
                                isExamNow || isExam -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                                lesson.isUpcoming -> Color(0xFF0284C7).copy(alpha = 0.3f)
                                else -> Color(0xFF94A3B8).copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        when {
                            isLiveNow -> {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .graphicsLayer { scaleX = liveDotScale; scaleY = liveDotScale }
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                    Text(
                                        "লাইভ ক্লাস",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                            isModelTestNow || isExamNow -> {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .graphicsLayer { scaleX = liveDotScale; scaleY = liveDotScale }
                                            .clip(CircleShape)
                                            .background(Color(0xFFEA580C))
                                    )
                                    Text(
                                        "লাইভ এক্সাম",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC2410C)
                                    )
                                }
                            }
                            isModelTest || isExam || isLiveExam -> {
                                if (lesson.isExamUpcoming) {
                                    Text(
                                        text = "আপকামিং এক্সাম",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C3AED),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                } else {
                                    Text(
                                        text = "রেকর্ড এক্সাম",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            lesson.isUpcoming -> {
                                Text(
                                    text = "আপকামিং ক্লাস",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            else -> {
                                Text(
                                    text = "রেকর্ড ক্লাস",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Title
                Text(
                    text = titleText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.5.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                // Footer with Time & Interactive Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (isLiveNow || isExamNow) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.5.dp)
                        )
                        Text(
                            text = if (durationString.isNotBlank()) "$timeString • $durationString" else timeString,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Subtle action pill
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isLiveNow -> Color(0xFFEF4444)
                            isExamNow -> Color(0xFFF59E0B)
                            isExam -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go",
                                tint = if (isLiveNow || isExamNow) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineCard(
    lesson: StudentLessonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShikhoRoutineCard(lesson = lesson, onClick = onClick, modifier = modifier)
}

// ==================== ROUTINE UTILITIES DELEGATION ====================

fun parseIsoToDhakaCalendar(isoString: String?, isEndOfDay: Boolean = false): Calendar? =
    com.example.utils.RoutineDateUtils.parseIsoToDhakaCalendar(isoString, isEndOfDay)

fun formatTimeRange(startCal: Calendar?, endCal: Calendar?): String =
    com.example.utils.RoutineDateUtils.formatTimeRange(startCal, endCal)

fun calculateDurationText(startCal: Calendar?, endCal: Calendar?): String =
    com.example.utils.RoutineDateUtils.calculateDurationText(startCal, endCal)

fun sortRoutineLessons(lessons: List<StudentLessonItem>): List<StudentLessonItem> =
    com.example.utils.RoutineDateUtils.sortRoutineLessons(lessons)
