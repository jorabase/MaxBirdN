package com.example.modeltest.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.components.ModelTestCountdownTimer
import com.example.modeltest.ui.components.ModelTestDetailSkeleton
import com.example.modeltest.ui.components.parseIsoDateToMillis
import com.example.utils.toBengaliDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelTestDetailScreen(
    modelTestId: String,
    lessonTitle: String?,
    lessonStartTime: String?,
    lessonEndTime: String?,
    viewModel: ModelTestViewModel,
    onBack: () -> Unit,
    onStartExam: (sessionId: String) -> Unit,
    onViewFeedback: (sessionId: String) -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(modelTestId) {
        viewModel.loadModelTestDetails(modelTestId, lessonStartTime, lessonEndTime)
    }

    val info = uiState.modelTestInfo
    val startTimeMillis = remember(info?.start_time, lessonStartTime) {
        parseIsoDateToMillis(info?.start_time ?: lessonStartTime)
    }
    val endTimeMillis = remember(info?.end_time, lessonEndTime) {
        parseIsoDateToMillis(info?.end_time ?: lessonEndTime)
    }

    val isMissed = remember(info?.is_missed, endTimeMillis) {
        info?.is_missed == true || (endTimeMillis != null && System.currentTimeMillis() > endTimeMillis)
    }

    val isUpcoming = remember(startTimeMillis) {
        startTimeMillis != null && System.currentTimeMillis() < startTimeMillis
    }

    val canStartMainExam = !isMissed && (!isUpcoming || uiState.isMainExamCountdownFinished)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = info?.title ?: lessonTitle ?: "মডেল টেস্টের বিবরণ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
        if (uiState.isInfoLoading && info == null) {
            Box(modifier = Modifier.padding(innerPadding)) {
                ModelTestDetailSkeleton()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Live Countdown or Missed Banner
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMissed) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isMissed) Color(0xFFFCA5A5) else Color(0xFFBFDBFE)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isMissed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444)
                            ) {
                                Text(
                                    text = "পরীক্ষা মিস হয়েছে",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "পরীক্ষার নির্ধারিত সময় পার হয়ে গেছে। মূল পরীক্ষায় অংশগ্রহণের সুযোগ নেই, তবে তুমি প্র্যাকটিস টেস্ট দিয়ে নিজেকে যাচাই করতে পারো।",
                                fontSize = 13.sp,
                                color = Color(0xFF991B1B),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        } else {
                            Text(
                                text = "MCQ এক্সাম শুরু হতে বাকি সময়",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E40AF)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (startTimeMillis != null) {
                                ModelTestCountdownTimer(
                                    targetEpochMillis = startTimeMillis,
                                    onTimerFinished = { viewModel.onMainExamCountdownFinished() }
                                )
                            } else {
                                Text(
                                    text = "পরীক্ষার সময়সূচি শীঘ্রই জানানো হবে",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    viewModel.startExamSession(
                                        modelTestId = modelTestId,
                                        isPractice = false,
                                        onSessionReady = onStartExam
                                    )
                                },
                                enabled = canStartMainExam && !uiState.isCreatingSession,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                if (uiState.isCreatingSession && !uiState.isPracticeSession) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("সেশন প্রস্তুত হচ্ছে...")
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (canStartMainExam) "টেস্ট শুরু করো" else "পরীক্ষা শুরুর জন্য অপেক্ষা করো",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Exam Rules & Instructions Section
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Rule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "টেস্টের নিয়মাবলী",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val defaultRules = listOf(
                            "পরীক্ষা শুরুর আগে ইন্টারনেট কানেকশন স্থিতিশীল রাখুন।",
                            "MCQ-এর জন্য নির্দিষ্ট সময় থাকবে এবং প্রতিটি প্রশ্নের সঠিক উত্তরের জন্য নম্বর বরাদ্দ থাকবে।",
                            "পরীক্ষা শুধুমাত্র একবারই দেওয়া যাবে।",
                            "CQ পরীক্ষার উত্তরের ছবি আপলোড করার জন্য নির্ধারিত অতিরিক্ত সময় পাওয়া যাবে।"
                        )
                        val rulesToDisplay = info?.instructions?.ifEmpty { defaultRules } ?: defaultRules

                        rulesToDisplay.forEachIndexed { idx, rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = rule,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // 3. MCQ & CQ Info Cards with Master Solution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExamPartCard(
                        title = "MCQ অংশ",
                        count = "${toBengaliDigits((info?.mcq_count ?: 30).toString())} টি প্রশ্ন",
                        duration = "${toBengaliDigits((info?.mcq_duration_minutes ?: 30).toString())} মিনিট",
                        icon = Icons.Default.Quiz,
                        modifier = Modifier.weight(1f)
                    )
                    ExamPartCard(
                        title = "CQ অংশ",
                        count = "${toBengaliDigits((info?.cq_count ?: 2).toString())} টি প্রশ্ন",
                        duration = "${toBengaliDigits((info?.cq_duration_minutes ?: 100).toString())} মিনিট",
                        icon = Icons.Default.Description,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Master Solution Button
                OutlinedButton(
                    onClick = {
                        viewModel.loadMasterSolution(modelTestId) { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    if (uiState.isMasterSolutionLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("মাস্টার সলুশন লোড হচ্ছে...")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("মাস্টার সলুশন (PDF)", fontWeight = FontWeight.Bold)
                    }
                }

                // 4. Practice Test Section (Dynamic Limit & History)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "প্র্যাকটিস টেস্ট",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Dynamic limit indicator
                            val limitText = if (uiState.attemptedPracticeCount > 0) {
                                "আর মাত্র ${toBengaliDigits(uiState.remainingPracticeCount.toString())} বার"
                            } else {
                                "সর্বমোট ${toBengaliDigits(uiState.totalPracticeAllowed.toString())} বার"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.isPracticeLimitReached) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = limitText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isPracticeLimitReached) Color(0xFFDC2626) else Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "প্র্যাকটিস টেস্ট দিয়ে নিজেকে যাচাই করে নাও। প্র্যাকটিস টেস্টের ক্ষেত্রে শুধু MCQ টেস্ট দিতে পারবে এবং তাৎক্ষণিক ফিডব্যাক পাবে। CQ অংশটি শুধুমাত্র পড়ার জন্য থাকবে।",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )

                        if (uiState.isPracticeLimitReached) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF2F2),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "তোমার প্র্যাকটিস টেস্টের লিমিট শেষ হয়ে গেছে!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Button to Start Practice Test
                        Button(
                            onClick = {
                                viewModel.startExamSession(
                                    modelTestId = modelTestId,
                                    isPractice = true,
                                    onSessionReady = onStartExam
                                )
                            },
                            enabled = !uiState.isPracticeLimitReached && !uiState.isCreatingSession,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (uiState.isCreatingSession && uiState.isPracticeSession) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("প্র্যাকটিস সেশন তৈরি হচ্ছে...")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("প্র্যাকটিস টেস্ট শুরু করো", fontWeight = FontWeight.Bold)
                            }
                        }

                        // List of Previous Practice Sessions
                        val practiceSessions = uiState.retakeContainer?.sessions ?: emptyList()
                        if (practiceSessions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "তোমার পূর্ববর্তী প্র্যাকটিস সেশনসমূহ",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            practiceSessions.forEachIndexed { index, session ->
                                val attemptNum = session.attempt_number ?: (index + 1)
                                val isCompleted = session.is_completed == true

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "প্র্যাকটিস টেস্ট ${toBengaliDigits(attemptNum.toString())}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (session.score != null) {
                                                Text(
                                                    text = "স্কোর: ${toBengaliDigits(session.score.toInt().toString())} / ${toBengaliDigits((session.total_marks?.toInt() ?: 30).toString())}",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        if (isCompleted && !session.session_id.isNullOrBlank()) {
                                            TextButton(
                                                onClick = { onViewFeedback(session.session_id) }
                                            ) {
                                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ফিডব্যাক দেখো", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                            }
                                        } else {
                                            Text(
                                                text = "অসম্পূর্ণ",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun ExamPartCard(
    title: String,
    count: String,
    duration: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = duration, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
