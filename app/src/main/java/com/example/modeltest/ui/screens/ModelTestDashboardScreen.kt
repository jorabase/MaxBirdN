package com.example.modeltest.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.StudentLessonItem
import com.example.modeltest.ui.ModelTestTab
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.components.ModelTestListSkeleton
import com.example.modeltest.ui.components.RetryErrorView
import com.example.utils.AppLang
import com.example.utils.isBangla
import com.example.utils.toBengaliDigits
import com.example.utils.tr
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun formatModelTestDate(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleanDate = rawDate.replace("Z", "").take(19)
        val date = parser.parse(cleanDate)
        if (date != null) {
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", AppLang.locale())
            formatter.format(date)
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTestDashboardScreen(
    subjectCode: String,
    subjectTitle: String,
    subjectColorHex: String?,
    viewModel: ModelTestViewModel,
    onBack: () -> Unit,
    onOpenModelTest: (lesson: StudentLessonItem) -> Unit,
    onOpenClass: (lesson: StudentLessonItem) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToAnimatedLessons: () -> Unit = {},
    onNavigateToPracticeQuiz: () -> Unit = {},
    onNavigateToSmartNotes: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val subjectColor = remember(subjectColorHex) {
        try {
            if (!subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    subjectColor.copy(alpha = 0.08f),
                                    subjectColor.copy(alpha = 0.14f)
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = subjectTitle.ifBlank { tr("Model Tests", "মডেল টেস্ট") },
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Decorative letters artwork on the right
                        val isBn = isBangla()
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(width = 56.dp, height = 42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isBn) "অ" else "A",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFACC15),
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                            Text(
                                text = if (isBn) "খ" else "B",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                            Text(
                                text = if (isBn) "ক" else "C",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr("Back to Home", "হোমে ফিরে যান"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top 3 Feature Cards
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Animated Lessons
                    ModelTestTopFeatureCard(
                        title = tr("Animated", "অ্যানিমেটেড"),
                        testTagName = "model_test_animated_lessons_card",
                        onClick = onNavigateToAnimatedLessons,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFEC4899), Color(0xFFBE185D))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Slideshow,
                                contentDescription = tr("Animated Lessons", "অ্যানিমেটেড লেসনস"),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 2. Practice Quiz
                    ModelTestTopFeatureCard(
                        title = tr("Quiz", "কুইজ"),
                        testTagName = "model_test_practice_quiz_card",
                        onClick = onNavigateToPracticeQuiz,
                        modifier = Modifier.weight(1f)
                    ) {
                        val isDark = com.example.ui.theme.isAppInDarkTheme()
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isDark) {
                                            listOf(Color(0xFF78350F).copy(alpha = 0.65f), Color(0xFF0C4A6E).copy(alpha = 0.65f))
                                        } else {
                                            listOf(Color(0xFFFEF3C7), Color(0xFFE0F2FE))
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = tr("Practice Quiz", "প্র্যাকটিস কুইজ"),
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 3. Smart Notes / E-Book
                    ModelTestTopFeatureCard(
                        title = tr("Smart Notes", "ই-বুক"),
                        testTagName = "model_test_ebook_card",
                        onClick = onNavigateToSmartNotes,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFDBEAFE), Color(0xFF93C5FD))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = tr("Smart Notes", "ই-বুক"),
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Segment Tabs Header
            ModelTestTabsHeader(
                selectedTab = uiState.selectedTab,
                modelTestCount = uiState.modelTests.size,
                classCount = uiState.liveClasses.size,
                activeColor = subjectColor,
                onSelectTab = { viewModel.selectTab(it) }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState.selectedTab) {
                    ModelTestTab.MODEL_TEST -> {
                        when {
                            uiState.isModelTestsLoading -> {
                                ModelTestListSkeleton()
                            }
                            uiState.modelTestsError != null && uiState.modelTests.isEmpty() -> {
                                RetryErrorView(
                                    message = uiState.modelTestsError ?: tr("Failed to load model tests", "মডেল টেস্ট লোড করা যায়নি"),
                                    onRetry = { viewModel.loadModelTests(forceRefresh = true) }
                                )
                            }
                            uiState.modelTests.isEmpty() -> {
                                EmptyStateView(
                                    icon = Icons.Default.Quiz,
                                    title = tr("No Model Tests", "কোনো মডেল টেস্ট নেই"),
                                    subtitle = tr("New model tests for this subject will appear here soon.", "এই বিষয়ের জন্য শীঘ্রই নতুন মডেল টেস্ট প্রকাশ করা হবে।")
                                )
                            }
                            else -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        items = uiState.modelTests,
                                        key = { it.id ?: it.content_id ?: "" }
                                    ) { item ->
                                        ModelTestCard(
                                            item = item,
                                            accentColor = subjectColor,
                                            onClick = { onOpenModelTest(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ModelTestTab.CLASS -> {
                        when {
                            uiState.isClassesLoading -> {
                                ModelTestListSkeleton()
                            }
                            uiState.classesError != null && uiState.liveClasses.isEmpty() -> {
                                RetryErrorView(
                                    message = uiState.classesError ?: tr("Failed to load classes", "ক্লাস লোড করা যায়নি"),
                                    onRetry = { viewModel.loadClasses(forceRefresh = true) }
                                )
                            }
                            uiState.liveClasses.isEmpty() -> {
                                EmptyStateView(
                                    icon = Icons.Default.VideoCameraFront,
                                    title = tr("No Classes Found", "কোনো ক্লাস পাওয়া যায়নি"),
                                    subtitle = tr("Live and recorded classes for this subject will appear here soon.", "এই বিষয়ের জন্য লাইভ বা রেকর্ডেড ক্লাস শীঘ্রই শুরু হবে।")
                                )
                            }
                            else -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        items = uiState.liveClasses,
                                        key = { it.id ?: it.content_id ?: "" }
                                    ) { item ->
                                        ClassCard(
                                            item = item,
                                            accentColor = subjectColor,
                                            onClick = { onOpenClass(item) }
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

@Composable
private fun ModelTestTabsHeader(
    selectedTab: ModelTestTab,
    modelTestCount: Int,
    classCount: Int,
    activeColor: Color,
    onSelectTab: (ModelTestTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isTestSelected = selectedTab == ModelTestTab.MODEL_TEST
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (isTestSelected) activeColor else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onSelectTab(ModelTestTab.MODEL_TEST) }
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        tint = if (isTestSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (modelTestCount > 0) {
                            tr("Model Tests ($modelTestCount)", "মডেল টেস্ট (${toBengaliDigits(modelTestCount)})")
                        } else {
                            tr("Model Tests", "মডেল টেস্ট")
                        },
                        fontSize = 13.5.sp,
                        fontWeight = if (isTestSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTestSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isClassSelected = selectedTab == ModelTestTab.CLASS
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (isClassSelected) activeColor else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onSelectTab(ModelTestTab.CLASS) }
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = if (isClassSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (classCount > 0) {
                            tr("Classes ($classCount)", "ক্লাস (${toBengaliDigits(classCount)})")
                        } else {
                            tr("Classes", "ক্লাস")
                        },
                        fontSize = 13.5.sp,
                        fontWeight = if (isClassSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isClassSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelTestCard(
    item: StudentLessonItem,
    accentColor: Color,
    onClick: () -> Unit
) {
    val title = item.title ?: tr("Model Test", "মডেল টেস্ট")
    val dateStr = formatModelTestDate(item.start_time)
    val state = item.user_activity_state?.uppercase() ?: ""
    val now = System.currentTimeMillis()
    val startMs = com.example.modeltest.ui.components.parseIsoDateToMillis(item.start_time, isEndOfDay = false)
    val endMs = com.example.modeltest.ui.components.parseIsoDateToMillis(item.end_time, isEndOfDay = true)

    val isCompleted = state == "COMPLETED" || state == "ATTENDED"
    val isLive = !isCompleted && (
        state == "LIVE" ||
        item.isLiveNow ||
        (startMs != null && now >= (startMs - 5 * 60 * 1000L) && (endMs == null || now <= endMs))
    )
    val isMissed = !isLive && !isCompleted && (
        (endMs != null && now > endMs) || state == "MISSED"
    )

    // Live, Completed, Ended, Upcoming detection
    val isDark = com.example.ui.theme.isAppInDarkTheme()
    val (statusLabel, statusBg, statusText) = when {
        isLive -> Triple(
            tr("🔴 Live Exam", "🔴 লাইভ এক্সাম"),
            if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.55f) else Color(0xFFEF4444).copy(alpha = 0.15f),
            if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)
        )
        isCompleted -> Triple(
            tr("Completed", "সম্পন্ন"),
            if (isDark) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFF10B981).copy(alpha = 0.12f),
            if (isDark) Color(0xFF6EE7B7) else Color(0xFF059669)
        )
        isMissed -> Triple(
            tr("Recorded Exam", "রেকর্ড এক্সাম"),
            if (isDark) Color(0xFF1E293B) else Color(0xFF64748B).copy(alpha = 0.12f),
            if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
        )
        else -> Triple(
            tr("Upcoming Exam", "আপকামিং এক্সাম"),
            if (isDark) Color(0xFF4C1D95).copy(alpha = 0.52f) else Color(0xFF7C3AED).copy(alpha = 0.12f),
            if (isDark) Color(0xFFC4B5FD) else Color(0xFF7C3AED)
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (dateStr.isNotBlank()) {
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("View Details & Rules", "বিস্তারিত দেখুন"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ClassCard(
    item: StudentLessonItem,
    accentColor: Color,
    onClick: () -> Unit
) {
    val title = item.title ?: tr("Lecture Class", "লেকচার ক্লাস")
    val dateStr = formatModelTestDate(item.start_time ?: item.live_class?.start_time)
    val isLive = item.isLiveNow
    val isUpcoming = item.isUpcoming
    val (statusLabel, statusBg, statusColor) = when {
        isLive -> Triple(tr("🔴 Live Class", "🔴 লাইভ ক্লাস"), Color(0xFFEF4444).copy(alpha = 0.12f), Color(0xFFDC2626))
        isUpcoming -> Triple(tr("Upcoming", "আপকামিং"), Color(0xFF0284C7).copy(alpha = 0.12f), Color(0xFF0284C7))
        else -> Triple(tr("Recorded", "রেকর্ড ক্লাস"), Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF1D4ED8))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (dateStr.isNotBlank()) {
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Open Class", "ক্লাস দেখুন"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ModelTestTopFeatureCard(
    title: String,
    testTagName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .height(92.dp)
            .testTag(testTagName)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            iconContent()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

