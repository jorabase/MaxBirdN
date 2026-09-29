package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.StudentLessonItem
import com.example.utils.ClassTypeUtils
import com.example.utils.calculateTimeDifference
import com.example.utils.formatLessonDateDetailed
import com.example.utils.formatLessonTimeRange
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun CountdownBox(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0D9488),
            modifier = Modifier.size(width = 64.dp, height = 64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingCountdownScreen(
    lesson: StudentLessonItem,
    subjectName: String,
    subjectColorHex: String?,
    onBack: () -> Unit
) {
    val startTimeStr = lesson.live_class?.start_time ?: lesson.start_time
    val endTimeStr = lesson.live_class?.end_time ?: lesson.end_time

    var timeDifferenceMs by remember(startTimeStr) {
        mutableLongStateOf(calculateTimeDifference(startTimeStr))
    }

    LaunchedEffect(startTimeStr) {
        while (true) {
            delay(1000L)
            timeDifferenceMs = calculateTimeDifference(startTimeStr)
        }
    }

    val secondsTotal = (timeDifferenceMs / 1000).coerceAtLeast(0L)
    val days = secondsTotal / (24 * 3600)
    val hours = (secondsTotal % (24 * 3600)) / 3600
    val minutes = (secondsTotal % 3600) / 60
    val seconds = secondsTotal % 60

    val isExam = lesson.isExam
    val headingText = if (isExam) "টেস্ট শুরু হতে সময় বাকি" else "ক্লাস শুরু হতে সময় বাকি"

    val formattedDate = remember(startTimeStr) {
        formatLessonDateDetailed(startTimeStr)
    }

    val formattedTimeRange = remember(startTimeStr, endTimeStr) {
        formatLessonTimeRange(startTimeStr, endTimeStr)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ClassTypeUtils.formatLessonTitle(lesson.title ?: subjectName),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Main Countdown Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = headingText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4 Timer Boxes Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", days), label = "দিন")
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", hours), label = "ঘণ্টা")
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", minutes), label = "মিনিট")
                        CountdownBox(value = String.format(Locale.getDefault(), "%02d", seconds), label = "সেকেন্ড")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Lesson & Subject Info Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = subjectName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2563EB)
                        )
                        val classTypeBadge = ClassTypeUtils.getClassTypeBadgeStyle(lesson)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = classTypeBadge.backgroundColor
                        ) {
                            Text(
                                text = classTypeBadge.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = classTypeBadge.textColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ClassTypeUtils.formatLessonTitle(lesson.title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "তারিখ ও সময়:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )
                    if (formattedTimeRange.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formattedTimeRange,
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}
