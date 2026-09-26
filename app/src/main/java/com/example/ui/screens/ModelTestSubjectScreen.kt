package com.example.modeltest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.api.StudentLessonItem
import com.example.utils.toBengaliDigits
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Format ISO datetime string to user-friendly Bengali format (e.g. 16 Nov 2025)
 */
fun formatModelTestDate(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleanDate = rawDate.replace("Z", "").take(19)
        val date = parser.parse(cleanDate)
        if (date != null) {
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale("bn", "BD"))
            formatter.format(date)
        } else {
            rawDate
        }
    } catch (_: Exception) {
        try {
            val simple = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = simple.parse(rawDate.take(10))
            if (d != null) {
                SimpleDateFormat("dd MMM yyyy", Locale("bn", "BD")).format(d)
            } else rawDate
        } catch (_: Exception) {
            rawDate
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTestSubjectScreen(
    subjectCode: String,
    subjectTitle: String,
    subjectColorHex: String?,
    viewModel: ModelTestViewModel,
    onBack: () -> Unit,
    onOpenExam: (lesson: StudentLessonItem) -> Unit,
    onOpenClass: (lesson: StudentLessonItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = subjectTitle.ifBlank { "বিষয়" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "মডেল টেস্ট ও রিভিশন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = subjectColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("model_test_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshCurrentTab() },
                        modifier = Modifier.testTag("model_test_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "রিফ্রেশ করুন",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Elegant Two-Tab Segment Control
            ModelTestTabsHeader(
                selectedTab = uiState.selectedTab,
                modelTestCount = uiState.modelTests.size,
                classCount = uiState.liveClasses.size,
                activeColor = subjectColor,
                onSelectTab = { viewModel.selectTab(it) }
            )

            // Content Area based on selected Tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState.selectedTab) {
                    ModelTestTab.MODEL_TEST -> {
                        ModelTestListView(
                            items = uiState.modelTests,
                            isLoading = uiState.isModelTestsLoading,
                            errorMessage = uiState.modelTestsError,
                            subjectColor = subjectColor,
                            onRetry = { viewModel.loadModelTests(forceRefresh = true) },
                            onItemClick = onOpenExam
                        )
                    }
                    ModelTestTab.CLASS -> {
                        ClassListView(
                            items = uiState.liveClasses,
                            isLoading = uiState.isClassesLoading,
                            errorMessage = uiState.classesError,
                            subjectColor = subjectColor,
                            onRetry = { viewModel.loadClasses(forceRefresh = true) },
                            onItemClick = onOpenClass
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Segment Tabs Bar
 */
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
        shadowElevation = 2.dp,
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
            // Tab 1: Model Test
            val isTestSelected = selectedTab == ModelTestTab.MODEL_TEST
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (isTestSelected) activeColor else Color.Transparent,
                shadowElevation = if (isTestSelected) 2.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onSelectTab(ModelTestTab.MODEL_TEST) }
                    .testTag("tab_model_test")
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
                        text = if (modelTestCount > 0) "মডেল টেস্ট (${toBengaliDigits(modelTestCount)})" else "মডেল টেস্ট",
                        fontSize = 13.5.sp,
                        fontWeight = if (isTestSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTestSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab 2: Class
            val isClassSelected = selectedTab == ModelTestTab.CLASS
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (isClassSelected) activeColor else Color.Transparent,
                shadowElevation = if (isClassSelected) 2.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onSelectTab(ModelTestTab.CLASS) }
                    .testTag("tab_live_class")
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = if (isClassSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (classCount > 0) "ক্লাস (${toBengaliDigits(classCount)})" else "ক্লাস",
                        fontSize = 13.5.sp,
                        fontWeight = if (isClassSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isClassSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Model Test List Tab View
 */
@Composable
private fun ModelTestListView(
    items: List<StudentLessonItem>,
    isLoading: Boolean,
    errorMessage: String?,
    subjectColor: Color,
    onRetry: () -> Unit,
    onItemClick: (StudentLessonItem) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = subjectColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "মডেল টেস্ট লোড হচ্ছে...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            errorMessage != null && items.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRetry) {
                        Text("পুনরায় চেষ্টা করুন")
                    }
                }
            }
            items.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "এই মুহূর্তে কোনো মডেল টেস্ট নেই",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id.ifBlank { it.content_id ?: it.title ?: it.hashCode().toString() } }) { item ->
                        ModelTestCard(
                            item = item,
                            accentColor = subjectColor,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Class List Tab View
 */
@Composable
private fun ClassListView(
    items: List<StudentLessonItem>,
    isLoading: Boolean,
    errorMessage: String?,
    subjectColor: Color,
    onRetry: () -> Unit,
    onItemClick: (StudentLessonItem) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = subjectColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ক্লাস লোড হচ্ছে...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            errorMessage != null && items.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRetry) {
                        Text("পুনরায় চেষ্টা করুন")
                    }
                }
            }
            items.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "এই মুহূর্তে কোনো ক্লাস নেই",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id.ifBlank { it.content_id ?: it.title ?: it.hashCode().toString() } }) { item ->
                        LiveClassCard(
                            item = item,
                            accentColor = subjectColor,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Model Test Card with exam icon, title, start date, and status badge (Missed/Completed/Upcoming)
 */
@Composable
fun ModelTestCard(
    item: StudentLessonItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateText = remember(item.start_time) {
        formatModelTestDate(item.start_time)
    }

    val state = (item.user_activity_state ?: "").uppercase()
    val (statusLabel, statusBg, statusTextColor) = when (state) {
        "MISSED" -> Triple("মিসড", Color(0xFFFEE2E2), Color(0xFFDC2626))
        "COMPLETED", "ATTENDED" -> Triple("সম্পন্ন", Color(0xFFDCFCE7), Color(0xFF16A34A))
        "UPCOMING" -> Triple("আসন্ন", Color(0xFFFEF3C7), Color(0xFFD97706))
        "LIVE" -> Triple("লাইভ", Color(0xFFFEE2E2), Color(0xFFEF4444))
        else -> Triple(null, Color.Transparent, Color.Transparent)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("model_test_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exam Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Quiz,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: "মডেল টেস্ট",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                if (dateText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = dateText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status Badge
            if (statusLabel != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusTextColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/**
 * Live / Recorded Class Card with video icon, title, start date, and status badge
 */
@Composable
fun LiveClassCard(
    item: StudentLessonItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateText = remember(item.start_time, item.live_class?.start_time) {
        formatModelTestDate(item.start_time ?: item.live_class?.start_time)
    }

    val state = (item.user_activity_state ?: "").uppercase()
    val isOngoing = item.live_class?.is_on_going == true || item.isLiveNow

    val (statusLabel, statusBg, statusTextColor) = when {
        isOngoing -> Triple("লাইভ চলছে", Color(0xFFFEE2E2), Color(0xFFEF4444))
        state == "MISSED" -> Triple("মিসড", Color(0xFFFEE2E2), Color(0xFFDC2626))
        state == "COMPLETED" || state == "ATTENDED" -> Triple("সম্পন্ন", Color(0xFFDCFCE7), Color(0xFF16A34A))
        state == "UPCOMING" -> Triple("আসন্ন", Color(0xFFFEF3C7), Color(0xFFD97706))
        else -> Triple(null, Color.Transparent, Color.Transparent)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("live_class_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Class Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isOngoing) Color(0xFFEF4444).copy(alpha = 0.14f) else accentColor.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOngoing) Icons.Default.PlayArrow else Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = if (isOngoing) Color(0xFFEF4444) else accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: "ক্লাস লেকচার",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                if (dateText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = dateText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status Badge
            if (statusLabel != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusTextColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
