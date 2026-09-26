package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.home.HomeViewModel
import com.example.ui.components.SubjectFilterDialog
import com.example.ui.components.routine.MonthYearPickerDialog
import com.example.ui.components.routine.TimelineRoutineCard
import com.example.utils.ClassTypeUtils
import com.example.utils.RoutineDateUtils
import com.example.utils.toBengaliDigits
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

private val bengaliDayNames = arrayOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি")
private val bengaliMonthNames = arrayOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
)

enum class RoutineTabFilter(val label: String) {
    ALL("সব"),
    LIVE("লাইভ ক্লাস"),
    EXAM("লাইভ এক্সাম")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullRoutineScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenLessonDetail: ((lesson: StudentLessonItem) -> Unit)? = null,
    onNavigateToExam: ((sessionId: String, lessonId: String, title: String, chapter: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val dhakaZone = remember { TimeZone.getTimeZone("Asia/Dhaka") }
    val coroutineScope = rememberCoroutineScope()

    val initialPage = 1000
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2000 })
    val currentWeekOffset = pagerState.currentPage - initialPage

    val weekDays = remember(currentWeekOffset) {
        val cal = Calendar.getInstance(dhakaZone)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.add(Calendar.WEEK_OF_YEAR, currentWeekOffset)

        val days = mutableListOf<Calendar>()
        for (i in 0..6) {
            val d = cal.clone() as Calendar
            d.add(Calendar.DAY_OF_YEAR, i)
            days.add(d)
        }
        days
    }

    val todayCal = remember {
        Calendar.getInstance(dhakaZone).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }

    var selectedDayIndex by remember(currentWeekOffset) {
        val todayIdx = weekDays.indexOfFirst {
            it.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    it.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        }
        mutableIntStateOf(if (todayIdx != -1) todayIdx else 0)
    }

    val selectedDate = weekDays.getOrNull(selectedDayIndex) ?: weekDays[0]
    val displayedYear = selectedDate.get(Calendar.YEAR)
    val displayedMonth = selectedDate.get(Calendar.MONTH)

    LaunchedEffect(displayedYear, displayedMonth, uiState.activeProgram?.id) {
        viewModel.fetchMonthlyRoutine(displayedYear, displayedMonth)
    }

    val monthYearText = remember(selectedDate) {
        val monthStr = bengaliMonthNames[selectedDate.get(Calendar.MONTH)]
        val yearStr = selectedDate.get(Calendar.YEAR).toString().toBengaliDigits()
        "$monthStr $yearStr"
    }

    val fullDateText = remember(selectedDate) {
        val dayOfWeek = selectedDate.get(Calendar.DAY_OF_WEEK)
        val dayName = when (dayOfWeek) {
            Calendar.SATURDAY -> "শনিবার"
            Calendar.SUNDAY -> "রবিবার"
            Calendar.MONDAY -> "সোমবার"
            Calendar.TUESDAY -> "মঙ্গলবার"
            Calendar.WEDNESDAY -> "বুধবার"
            Calendar.THURSDAY -> "বৃহস্পতিবার"
            Calendar.FRIDAY -> "শুক্রবার"
            else -> ""
        }
        val dayNum = String.format(Locale.US, "%02d", selectedDate.get(Calendar.DAY_OF_MONTH)).toBengaliDigits()
        val monthNum = String.format(Locale.US, "%02d", selectedDate.get(Calendar.MONTH) + 1).toBengaliDigits()
        val yearNum = selectedDate.get(Calendar.YEAR).toString().toBengaliDigits()
        "$dayName, $dayNum/$monthNum/$yearNum"
    }

    val selectedDayLessons = remember(uiState.filteredWeeklyRoutine, selectedDate) {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }
        val targetDateStr = sdfDate.format(selectedDate.time)

        val raw = uiState.filteredWeeklyRoutine.filter { lesson ->
            val timeStr = lesson.start_time ?: lesson.live_class?.start_time
            if (timeStr.isNullOrBlank()) false
            else {
                val lessonCal = RoutineDateUtils.parseIsoToDhakaCalendar(timeStr)
                if (lessonCal != null) {
                    val lessonDateStr = sdfDate.format(lessonCal.time)
                    lessonDateStr == targetDateStr
                } else false
            }
        }
        RoutineDateUtils.sortRoutineLessons(raw)
    }

    val classCount = selectedDayLessons.count { !it.isExam }
    val examCount = selectedDayLessons.count { it.isExam }

    var selectedTabFilter by remember { mutableStateOf(RoutineTabFilter.ALL) }
    var showMonthYearPicker by remember { mutableStateOf(false) }

    val displayedLessons = remember(selectedDayLessons, selectedTabFilter) {
        when (selectedTabFilter) {
            RoutineTabFilter.ALL -> selectedDayLessons
            RoutineTabFilter.LIVE -> selectedDayLessons.filter { !it.isExam }
            RoutineTabFilter.EXAM -> selectedDayLessons.filter { it.isExam }
        }
    }

    if (uiState.showSubjectFilterDialog) {
        SubjectFilterDialog(
            courseTitle = uiState.activeProgram?.title_bn ?: "",
            subjects = uiState.courseSubjects,
            selectedSubjectCodes = uiState.selectedSubjectCodes,
            isLoading = uiState.isCourseSubjectsLoading,
            isSaving = uiState.isSavingSubjectFilter,
            onToggleSubject = { code -> viewModel.toggleSubjectSelection(code) },
            onSelectAll = { viewModel.selectAllSubjects() },
            onClearAll = { viewModel.clearAllSubjectSelection() },
            onSave = { viewModel.saveSubjectFilter() },
            onDismiss = { viewModel.dismissSubjectFilterDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("ক্লাস ও পরীক্ষার রুটিন", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "ফিরে যান", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    val filterInteraction = remember { MutableInteractionSource() }
                    val isFilterPressed by filterInteraction.collectIsPressedAsState()
                    val filterScale by animateFloatAsState(
                        targetValue = if (isFilterPressed) 0.9f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "filterScale"
                    )

                    Surface(
                        modifier = Modifier
                            .graphicsLayer { scaleX = filterScale; scaleY = filterScale }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(interactionSource = filterInteraction, indication = null) {
                                viewModel.openSubjectFilterDialog()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tune, "কাস্টমাইজ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("ফিল্টার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Month & Year Selector Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showMonthYearPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(monthYearText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "পূর্ববর্তী সপ্তাহ", modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "পরবর্তী সপ্তাহ", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Calendar Days Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val offset = page - initialPage
                val daysForPage = remember(offset) {
                    val cal = Calendar.getInstance(dhakaZone).apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        while (get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) add(Calendar.DAY_OF_YEAR, -1)
                        add(Calendar.WEEK_OF_YEAR, offset)
                    }
                    (0..6).map { i -> (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) } }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysForPage.forEachIndexed { index, day ->
                        val isSelected = (page == pagerState.currentPage && index == selectedDayIndex)
                        val isToday = day.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                day.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                        val dayLessons = remember(uiState.filteredWeeklyRoutine, day) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }
                            val target = sdf.format(day.time)
                            uiState.filteredWeeklyRoutine.filter {
                                val t = it.start_time ?: it.live_class?.start_time
                                if (t.isNullOrBlank()) false
                                else {
                                    val c = RoutineDateUtils.parseIsoToDhakaCalendar(t)
                                    c != null && sdf.format(c.time) == target
                                }
                            }
                        }

                        val hasClasses = dayLessons.any { !it.isExam }
                        val hasExams = dayLessons.any { it.isExam }

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.92f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "dayScale"
                        )

                        val dayBgColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else -> MaterialTheme.colorScheme.surface
                            },
                            animationSpec = tween(150),
                            label = "dayBg"
                        )

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    selectedDayIndex = index
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = dayBgColor),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else if (isToday) 1.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(if (isSelected) 3.dp else 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val dayOfWeek = day.get(Calendar.DAY_OF_WEEK)
                                val dayNameBn = bengaliDayNames[dayOfWeek - 1]

                                Text(
                                    text = dayNameBn,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = day.get(Calendar.DAY_OF_MONTH).toString().toBengaliDigits(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(5.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasClasses) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else Color(0xFF0284C7))
                                        )
                                    }
                                    if (hasExams) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFFFEF3C7) else Color(0xFFF59E0B))
                                        )
                                    }
                                    if (!hasClasses && !hasExams) {
                                        Spacer(modifier = Modifier.height(5.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Date Summary & Separate Filter Tabs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fullDateText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.12f)
                        ) {
                            Text(
                                "ক্লাস ${classCount.toString().toBengaliDigits()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.12f)
                        ) {
                            Text(
                                "এক্সাম ${examCount.toString().toBengaliDigits()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Separate Filter Tabs: সব, লাইভ ক্লাস, লাইভ এক্সাম
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoutineFilterChip(
                        text = "সব (${toBengaliDigits(selectedDayLessons.size)})",
                        isSelected = selectedTabFilter == RoutineTabFilter.ALL,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTabFilter = RoutineTabFilter.ALL }
                    )

                    RoutineFilterChip(
                        text = "🔴 লাইভ ক্লাস (${toBengaliDigits(classCount)})",
                        isSelected = selectedTabFilter == RoutineTabFilter.LIVE,
                        activeColor = Color(0xFFEF4444),
                        modifier = Modifier.weight(1.1f),
                        onClick = { selectedTabFilter = RoutineTabFilter.LIVE }
                    )

                    RoutineFilterChip(
                        text = "✍️ পরীক্ষা (${toBengaliDigits(examCount)})",
                        isSelected = selectedTabFilter == RoutineTabFilter.EXAM,
                        activeColor = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTabFilter = RoutineTabFilter.EXAM }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lesson List / Empty State / Shimmer
            when {
                uiState.isRoutineLoading -> {
                    ShimmerRoutineList()
                }

                displayedLessons.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EventBusy, null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (selectedTabFilter) {
                                    RoutineTabFilter.LIVE -> "এই দিনে কোনো লাইভ ক্লাস নেই"
                                    RoutineTabFilter.EXAM -> "এই দিনে কোনো পরীক্ষা নেই"
                                    RoutineTabFilter.ALL -> "এই দিনের জন্য কোনো ক্লাস বা পরীক্ষার রুটিন পাওয়া যায়নি"
                                },
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(displayedLessons, key = { it.id }) { lesson ->
                            TimelineRoutineCard(
                                lesson = lesson,
                                onClick = {
                                    if (lesson.isModelTest) {
                                        onOpenLessonDetail?.invoke(lesson)
                                    } else if (lesson.isExam && onNavigateToExam != null) {
                                        val sessionId = lesson.session_id?.takeIf { it.isNotBlank() }
                                            ?: lesson.live_class?.session_id?.takeIf { it.isNotBlank() }
                                            ?: lesson.content_id?.takeIf { it.isNotBlank() }
                                            ?: lesson.id
                                        val formattedTitle = ClassTypeUtils.formatLessonTitle(lesson.title ?: "অধ্যায় পরীক্ষা")
                                        val chapterName = lesson.subject_name ?: ""
                                        onNavigateToExam(sessionId, lesson.id, formattedTitle, chapterName)
                                    } else {
                                        onOpenLessonDetail?.invoke(lesson)
                                    }
                                },
                                onNavigateToExam = onNavigateToExam
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            initialYear = selectedDate.get(Calendar.YEAR),
            initialMonth = selectedDate.get(Calendar.MONTH),
            onDismiss = { showMonthYearPicker = false },
            onSelect = { year, month ->
                showMonthYearPicker = false
                val newCal = Calendar.getInstance(dhakaZone).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                while (newCal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                    newCal.add(Calendar.DAY_OF_YEAR, -1)
                }
                val diffWeeks = ((newCal.timeInMillis - todayCal.timeInMillis) / (7L * 24 * 60 * 60 * 1000L)).toInt()
                coroutineScope.launch {
                    pagerState.scrollToPage(initialPage + diffWeeks)
                    selectedDayIndex = 0
                }
            }
        )
    }
}

@Composable
private fun RoutineFilterChip(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ShimmerRoutineList() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    val skeletonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(4) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(skeletonColor.copy(alpha = shimmerAlpha))
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(64.dp, 18.dp).clip(RoundedCornerShape(8.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                        Box(Modifier.size(52.dp, 18.dp).clip(RoundedCornerShape(8.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                    }
                    Box(Modifier.fillMaxWidth(0.85f).height(15.dp).clip(RoundedCornerShape(5.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                    Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(RoundedCornerShape(5.dp)).background(skeletonColor.copy(alpha = shimmerAlpha)))
                }
            }
        }
    }
}
