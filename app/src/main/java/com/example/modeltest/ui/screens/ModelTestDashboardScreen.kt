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
import com.example.api.StudentLessonItem
import com.example.modeltest.ui.ModelTestTab
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.components.ModelTestListSkeleton
import com.example.modeltest.ui.components.RetryErrorView
import com.example.utils.toBengaliDigits
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
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("bn", "BD"))
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
                            text = subjectTitle.ifBlank { "মডেল টেস্ট" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "মডেল টেস্ট ও লাইভ ক্লাস",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                        Text("হোম এ ফিরে যাও", fontWeight = FontWeight.SemiBold)
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
                                    message = uiState.modelTestsError ?: "মডেল টেস্ট লোড করা যায়নি",
                                    onRetry = { viewModel.loadModelTests(forceRefresh = true) }
                                )
                            }
                            uiState.modelTests.isEmpty() -> {
                                EmptyStateView(
                                    icon = Icons.Default.Quiz,
                                    title = "কোনো মডেল টেস্ট পাওয়া যায়নি",
                                    subtitle = "এই বিষয়ের জন্য শীঘ্রই নতুন মডেল টেস্ট প্রকাশ করা হবে।"
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
                                    message = uiState.classesError ?: "ক্লাস লোড করা যায়নি",
                                    onRetry = { viewModel.loadClasses(forceRefresh = true) }
                                )
                            }
                            uiState.liveClasses.isEmpty() -> {
                                EmptyStateView(
                                    icon = Icons.Default.VideoCameraFront,
                                    title = "কোনো ক্লাস পাওয়া যায়নি",
                                    subtitle = "এই বিষয়ের জন্য লাইভ বা রেকর্ডেড ক্লাস শীঘ্রই শুরু হবে।"
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
                        text = if (modelTestCount > 0) "মডেল টেস্ট (${toBengaliDigits(modelTestCount)})" else "মডেল টেস্ট",
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

@Composable
private fun ModelTestCard(
    item: StudentLessonItem,
    accentColor: Color,
    onClick: () -> Unit
) {
    val title = item.title ?: "মডেল টেস্ট"
    val dateStr = formatModelTestDate(item.start_time)
    val state = item.user_activity_state?.uppercase() ?: ""

    // Missed, Completed, Upcoming detection
    val (statusLabel, statusBg, statusText) = when {
        state == "COMPLETED" -> Triple("সম্পন্ন", Color(0xFF10B981).copy(alpha = 0.12f), Color(0xFF059669))
        state == "MISSED" || (item.end_time != null && System.currentTimeMillis() > (com.example.modeltest.ui.components.parseIsoDateToMillis(item.end_time) ?: Long.MAX_VALUE)) ->
            Triple("মিসড", Color(0xFFEF4444).copy(alpha = 0.12f), Color(0xFFDC2626))
        else -> Triple("আসন্ন", Color(0xFF3B82F6).copy(alpha = 0.12f), Color(0xFF1D4ED8))
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
                    text = "বিস্তারিত ও রুলস দেখো",
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
    val title = item.title ?: "লেকচার ক্লাস"
    val dateStr = formatModelTestDate(item.start_time ?: item.live_class?.start_time)
    val isLive = item.live_class?.is_on_going == true

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
                    color = if (isLive) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF3B82F6).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (isLive) "লাইভ চলছে" else "ক্লাস",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) Color(0xFFDC2626) else Color(0xFF1D4ED8),
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
                    text = "ক্লাস প্লেয়ারে যাও",
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
