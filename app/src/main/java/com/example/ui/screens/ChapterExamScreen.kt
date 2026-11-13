package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.LiveExamQuestionItem
import com.example.api.McqOptionItem
import com.example.course.ChapterExamUiState
import com.example.course.ChapterExamViewModel
import com.example.course.ExamStage
import com.example.ui.components.QuizRichContent
import com.example.ui.theme.isAppInDarkTheme
import com.example.utils.toBengaliDigits
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterExamScreen(
    sessionId: String,
    lessonId: String,
    examTitle: String,
    chapterName: String,
    chapterId: String = "",
    subjectCode: String = "",
    subjectTitle: String = "",
    viewModel: ChapterExamViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(sessionId, lessonId) {
        viewModel.loadExamInfo(
            sessionId = sessionId,
            lessonId = lessonId,
            chapterId = chapterId,
            chapterName = chapterName,
            subjectCode = subjectCode,
            subjectTitle = subjectTitle,
            examTitle = examTitle
        )
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var activeSolutionPdfUrl by remember { mutableStateOf<String?>(null) }

    if (!activeSolutionPdfUrl.isNullOrBlank()) {
        com.example.ui.components.SlideViewerDialog(
            slideUrl = activeSolutionPdfUrl!!,
            title = "${uiState.examInfo?.title ?: examTitle} - সমাধান শিট",
            onDismiss = { activeSolutionPdfUrl = null }
        )
    }

    androidx.activity.compose.BackHandler {
        when (uiState.stage) {
            ExamStage.SOLUTIONS -> viewModel.backToPerformance()
            ExamStage.RULES -> viewModel.backToIntro()
            ExamStage.QUESTIONS -> showExitConfirmDialog = true
            else -> onBack()
        }
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = {
                Text("পরীক্ষা ত্যাগ করতে চান?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("আপনি কি নিশ্চিত যে পরীক্ষা শেষ না করেই বেরিয়ে যেতে চান?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("হ্যাঁ, বের হব", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitConfirmDialog = false }) {
                    Text("পরীক্ষা চালিয়ে যান")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (uiState.stage) {
            ExamStage.INTRO -> {
                ExamIntroBottomSheet(
                    uiState = uiState,
                    defaultTitle = examTitle,
                    defaultChapter = chapterName,
                    onStartClick = { viewModel.proceedToRules() },
                    onClose = onBack
                )
            }

            ExamStage.RULES -> {
                ExamRulesContent(
                    uiState = uiState,
                    defaultTitle = examTitle,
                    onBack = { viewModel.backToIntro() },
                    onStartExam = { viewModel.startExamQuestions() }
                )
            }

            ExamStage.QUESTIONS -> {
                ExamQuestionsContent(
                    uiState = uiState,
                    defaultTitle = examTitle,
                    onSelectOption = { viewModel.selectOption(it) },
                    onNext = { viewModel.nextQuestion() },
                    onPrevious = { viewModel.previousQuestion() },
                    onRefresh = { viewModel.startExamQuestions() },
                    onBack = { showExitConfirmDialog = true }
                )
            }

            ExamStage.SUBMITTING -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF0072EC))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "পরীক্ষা জমা দেওয়া হচ্ছে...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            ExamStage.PERFORMANCE -> {
                ExamPerformanceContent(
                    uiState = uiState,
                    defaultTitle = examTitle,
                    onShowSolutions = { viewModel.loadSolutions() },
                    onOpenPdf = { url ->
                        if (url.isNotBlank()) {
                            activeSolutionPdfUrl = url
                        }
                    },
                    onBack = onBack
                )
            }

            ExamStage.SOLUTIONS -> {
                ExamSolutionsContent(
                    uiState = uiState,
                    onNext = { viewModel.nextSolution() },
                    onPrevious = { viewModel.previousSolution() },
                    onJumpToQuestion = { idx -> viewModel.goToSolutionIndex(idx) },
                    onBack = { viewModel.backToPerformance() }
                )
            }
        }

        if (uiState.isLoading && uiState.stage != ExamStage.SUBMITTING && uiState.stage != ExamStage.QUESTIONS) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF0072EC))
            }
        }
    }
}

// ==========================================
// 1. INTRO SHEET (ধাপ ২ — এক্সাম Intro)
// ==========================================
@Composable
fun ExamIntroBottomSheet(
    uiState: ChapterExamUiState,
    defaultTitle: String,
    defaultChapter: String,
    onStartClick: () -> Unit,
    onClose: () -> Unit
) {
    val info = uiState.examInfo
    val displayTitle = info?.title ?: defaultTitle
    val displaySubject = info?.subject?.display?.takeIf { it.isNotBlank() }
        ?: uiState.subjectTitle.ifBlank { "বিষয়" }
    val chapterText = info?.chapters?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: defaultChapter.ifBlank { displayTitle }
    val totalQ = info?.total_number_of_question ?: 25

    val startCal = remember(info?.start_time) { com.example.ui.components.parseIsoToDhakaCalendar(info?.start_time) }
    val endCal = remember(info?.end_time) { com.example.ui.components.parseIsoToDhakaCalendar(info?.end_time) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val startMs = startCal?.timeInMillis ?: 0L
    val endMs = endCal?.timeInMillis ?: Long.MAX_VALUE

    val isUpcoming = startMs > 0L && nowMs < startMs
    val isOngoing = (startMs == 0L || nowMs >= startMs) && nowMs <= endMs
    val isEnded = endMs != Long.MAX_VALUE && nowMs > endMs

    val remainingDiff = if (isUpcoming) {
        (startMs - nowMs).coerceAtLeast(0L)
    } else if (isOngoing && endMs != Long.MAX_VALUE) {
        (endMs - nowMs).coerceAtLeast(0L)
    } else {
        0L
    }

    val days = (remainingDiff / (1000 * 60 * 60 * 24)).toInt()
    val hours = ((remainingDiff / (1000 * 60 * 60)) % 24).toInt()
    val minutes = ((remainingDiff / (1000 * 60)) % 60).toInt()
    val seconds = ((remainingDiff / 1000) % 60).toInt()

    val daysStr = toBengaliDigits(String.format(Locale.US, "%02d", days))
    val hoursStr = toBengaliDigits(String.format(Locale.US, "%02d", hours))
    val minutesStr = toBengaliDigits(String.format(Locale.US, "%02d", minutes))
    val secondsStr = toBengaliDigits(String.format(Locale.US, "%02d", seconds))

    val isDark = isAppInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(enabled = false) { }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isOngoing -> if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                        isUpcoming -> if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE)
                        else -> if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.45f) else Color(0xFFFEE2E2)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isOngoing -> "🔴 পরীক্ষা চলছে! দ্রুত অংশ নিন"
                            isUpcoming -> "⏳ পরীক্ষা শুরু হতে বাকি"
                            else -> "টেস্ট শেষ হয়ে গেছে (অনুশীলন করুন)"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isOngoing -> if (isDark) Color(0xFF6EE7B7) else Color(0xFF059669)
                            isUpcoming -> if (isDark) Color(0xFF7DD3FC) else Color(0xFF0284C7)
                            else -> if (isDark) Color(0xFFFCA5A5) else Color(0xFFEF4444)
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Countdown Box Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ExamCountdownBox(daysStr, "দিন")
                    ExamCountdownBox(hoursStr, "ঘণ্টা")
                    ExamCountdownBox(minutesStr, "মিনিট")
                    ExamCountdownBox(secondsStr, "সেকেন্ড")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info Cards
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = displaySubject,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF60A5FA) else Color(0xFF0072EC)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "অধ্যায়: $chapterText",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val examDurationMinutes = (uiState.examInfo?.total_number_of_question ?: totalQ).coerceAtLeast(1)
                        val examDurationText = "${toBengaliDigits(examDurationMinutes.coerceAtLeast(1).toString())} মিনিট"
                        Text(
                            text = "মোট প্রশ্ন: ${toBengaliDigits(totalQ.toString())} টি | সময়: $examDurationText",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Full-width Button
                Button(
                    onClick = onStartClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = if (isOngoing) "টেস্ট শুরু করো" else if (isEnded) "অনুশীলন শুরু করো" else "টেস্ট শুরু করো",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ExamCountdownBox(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(54.dp, 44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ==========================================
// 2. RULES CONTENT (ধাপ ৩ — নিয়মাবলী স্ক্রিন)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamRulesContent(
    uiState: ChapterExamUiState,
    defaultTitle: String,
    onBack: () -> Unit,
    onStartExam: () -> Unit
) {
    val info = uiState.examInfo
    val displayTitle = info?.title ?: defaultTitle
    val totalQ = info?.total_number_of_question ?: 25

    val isDark = isAppInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Metric cards row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.25f) else Color(0xFFEFF6FF)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF0072EC))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("প্রশ্ন", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${toBengaliDigits(totalQ.toString())} টি",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF78350F).copy(alpha = 0.28f) else Color(0xFFFEF3C7)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFD97706))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val examDurationMinutes = (uiState.examInfo?.total_number_of_question ?: totalQ).coerceAtLeast(1)
                                val examDurationText = "${toBengaliDigits(examDurationMinutes.coerceAtLeast(1).toString())} মিনিট"
                                Text("সময়", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(examDurationText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Rules Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "টেস্টের নিয়মাবলী",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        RuleItem(
                            iconBg = if (isDark) Color(0xFFD97706).copy(alpha = 0.2f) else Color(0xFFFEF3C7),
                            icon = Icons.Default.AccessTime,
                            iconTint = Color(0xFFD97706),
                            text = "প্রতিটি প্রশ্নের জন্য ৪টি করে বিকল্প অপশন থাকবে।"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        RuleItem(
                            iconBg = if (isDark) Color(0xFF059669).copy(alpha = 0.2f) else Color(0xDCD1FADF),
                            icon = Icons.Default.Check,
                            iconTint = Color(0xFF059669),
                            text = "নির্দিষ্ট সময়ের মধ্যে সকল প্রশ্নের উত্তর সাবমিট করতে হবে।"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        RuleItem(
                            iconBg = if (isDark) Color(0xFFEA580C).copy(alpha = 0.2f) else Color(0xFFFFEDD5),
                            icon = Icons.Default.BarChart,
                            iconTint = Color(0xFFEA580C),
                            text = "টেস্ট শেষে তোমার পারফরম্যান্স এবং সঠিক সমাধান দেখতে পারবে।"
                        )
                    }
                }

                if (!uiState.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartExam,
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "প্রশ্ন লোড হচ্ছে...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "টেস্ট শুরু করো",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun RuleItem(iconBg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = iconBg,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.weight(1f)
        )
    }
}

// ==========================================
// 3. QUESTIONS CONTENT (ধাপ ৫ — প্রশ্ন স্ক্রিন)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamQuestionsContent(
    uiState: ChapterExamUiState,
    defaultTitle: String,
    onSelectOption: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit
) {
    val questions = uiState.questions
    val index = uiState.currentQuestionIndex
    val currentQuestion = questions.getOrNull(index)

    val formatSeconds = remember(uiState.remainingSeconds) {
        val h = uiState.remainingSeconds / 3600
        val m = (uiState.remainingSeconds % 3600) / 60
        val s = uiState.remainingSeconds % 60
        val timeStr = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        toBengaliDigits(timeStr)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = defaultTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    val isDark = isAppInDarkTheme()
                    // Timer pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF78350F).copy(alpha = 0.45f) else Color(0xFFFEF3C7),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("⏰ ", fontSize = 12.sp)
                            Text(
                                text = formatSeconds,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal progress bar
            val progress = if (questions.isNotEmpty()) (index + 1).toFloat() / questions.size else 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF10B981))
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRefresh() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("রিফ্রেশ দাও", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (currentQuestion != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Question Counter
                        Text(
                            text = "${toBengaliDigits((index + 1).toString())}/${toBengaliDigits(questions.size.toString())}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEC4899)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Question Title & Diagram
                        QuizRichContent(
                            text = currentQuestion.title ?: "",
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                            textColor = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            maxImageHeight = 220.dp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // MCQ Options
                        val selectedAns = uiState.userAnswers[currentQuestion.id] ?: ""
                        val options = currentQuestion.mcq_options ?: emptyList()

                        options.forEachIndexed { optIdx, option ->
                            val optionNo = option.no ?: ""
                            val isSelected = selectedAns.isNotBlank() && (
                                selectedAns.equals(optionNo, ignoreCase = true) ||
                                com.example.utils.McqAnswerUtils.isAnswerCorrect(selectedAns, optionNo) ||
                                com.example.utils.McqAnswerUtils.normalizeToOptionIndex(selectedAns) == optIdx
                            )

                            McqOptionCard(
                                index = optIdx,
                                option = option,
                                isSelected = isSelected,
                                onClick = { onSelectOption(optionNo.ifBlank { ('A' + optIdx).toString() }) }
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Bottom Navigation Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (index > 0) {
                            OutlinedButton(
                                onClick = onPrevious,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("পেছনে", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (index == questions.size - 1) "সাবমিট করো" else "এগিয়ে যাও",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun McqOptionCard(
    index: Int,
    option: McqOptionItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val optionLetter = com.example.utils.McqAnswerUtils.toBengaliLetter(option.no).ifBlank {
        com.example.utils.McqAnswerUtils.indexToBengaliLetter(index)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF0072EC) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color(0xFFEFF6FF)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) Color(0xFF0072EC) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = optionLetter,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            QuizRichContent(
                text = com.example.utils.McqAnswerUtils.cleanOptionText(option.description),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                textColor = if (isSelected) {
                    if (isDark) Color(0xFF93C5FD) else Color(0xFF0072EC)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxImageHeight = 140.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==========================================
// 4. PERFORMANCE CONTENT (ধাপ ৭ — পারফরম্যান্স স্ক্রিন)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamPerformanceContent(
    uiState: ChapterExamUiState,
    defaultTitle: String,
    onShowSolutions: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onBack: () -> Unit
) {
    val summary = uiState.performanceSummary
    val correctCount = summary?.correct_ans?.toIntOrNull() ?: 0
    val incorrectCount = summary?.incorrect_ans?.toIntOrNull() ?: 0
    val unansweredCount = summary?.unanswered?.toIntOrNull() ?: 0
    val totalCount = summary?.total_question?.toIntOrNull() ?: 25

    val percentage = if (totalCount > 0) (correctCount.toFloat() / totalCount * 100).toInt() else 0

    val isDark = isAppInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "চ্যাপ্টার এক্সামের পারফরমেন্স",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // PDF Banner if URL exists
            if (!uiState.pdfAttachmentUrl.isNullOrBlank()) {
                Surface(
                    color = if (isDark) Color(0xFF78350F).copy(alpha = 0.45f) else Color(0xFFFEF3C7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPdf(uiState.pdfAttachmentUrl) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "📄 পিডিএফ দেখতে এখানে ট্যাপ করো ⬇",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = defaultTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "নিচের ড্যাশবোর্ড দেখে তোমার পারফরম্যান্সের ধারণা নাও, আর তোমার দেওয়া উত্তরগুলো মিলিয়ে নাও",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dashboard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut Chart
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(90.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 10.dp.toPx()
                                    drawArc(
                                        color = trackColor,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth)
                                    )
                                    drawArc(
                                        color = Color(0xFFEC4899),
                                        startAngle = -90f,
                                        sweepAngle = (percentage / 100f) * 360f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${toBengaliDigits(percentage.toString())}%",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${toBengaliDigits(correctCount.toString())}/${toBengaliDigits(totalCount.toString())}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Breakdown list
                            Column {
                                PerformanceLine(dotColor = Color(0xFF10B981), label = "সঠিক উত্তর: ${toBengaliDigits(correctCount.toString())}")
                                Spacer(modifier = Modifier.height(6.dp))
                                PerformanceLine(dotColor = Color(0xFFEF4444), label = "ভুল উত্তর: ${toBengaliDigits(incorrectCount.toString())}")
                                Spacer(modifier = Modifier.height(6.dp))
                                PerformanceLine(dotColor = Color(0xFF94A3B8), label = "উত্তর দাওনি: ${toBengaliDigits(unansweredCount.toString())}")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Solution Button
                        val accentBlue = if (isDark) Color(0xFF60A5FA) else Color(0xFF0072EC)
                        OutlinedButton(
                            onClick = onShowSolutions,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, accentBlue)
                        ) {
                            Text("সমাধান", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentBlue)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2 Metric cards
                val totalSec = uiState.timeSpentPerQuestion.values.sum()
                val totalMinutes = (totalSec / 60).toInt()
                val remSeconds = (totalSec % 60).toInt()
                val totalTimeSpentText = when {
                    totalMinutes > 0 && remSeconds > 0 -> "${toBengaliDigits(totalMinutes.toString())} মিনিট ${toBengaliDigits(remSeconds.toString())} সে."
                    totalMinutes > 0 -> "${toBengaliDigits(totalMinutes.toString())} মিনিট"
                    else -> "${toBengaliDigits(totalSec.toInt().coerceAtLeast(1).toString())} সেকেন্ড"
                }
                val avgSecPerQ = if (totalCount > 0) totalSec / totalCount else 0.0
                val avgTimeText = "${toBengaliDigits(String.format(java.util.Locale.US, "%.1f", avgSecPerQ))} সেকেন্ড"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("মোট সময় ব্যয়", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(totalTimeSpentText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("প্রতি প্রশ্নে সময়", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(avgTimeText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("ফিরে যাও", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PerformanceLine(dotColor: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==========================================
// 5. SOLUTIONS CONTENT (ধাপ ৮ — সমাধান স্ক্রিন)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSolutionsContent(
    uiState: ChapterExamUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onJumpToQuestion: (Int) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val solutions = uiState.solutions
    val index = uiState.solutionIndex
    val currentSolution = solutions.getOrNull(index)
    var showGridDialog by remember { mutableStateOf(false) }

    if (showGridDialog) {
        AlertDialog(
            onDismissRequest = { showGridDialog = false },
            title = {
                Text("প্রশ্ন নির্বাচন করুন", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    solutions.chunked(5).forEachIndexed { rowIdx, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEachIndexed { colIdx, _ ->
                                val qIdx = rowIdx * 5 + colIdx
                                val isCur = qIdx == index
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCur) Color(0xFF0072EC) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onJumpToQuestion(qIdx)
                                            showGridDialog = false
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = toBengaliDigits((qIdx + 1).toString()),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCur) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            repeat(5 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridDialog = false }) {
                    Text("বন্ধ করুন", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "সমাধান",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showGridDialog = true }) {
                        Icon(Icons.Default.GridView, contentDescription = "Grid", tint = Color(0xFF0072EC))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (currentSolution != null) {
            val q = currentSolution.question
            val givenAns = currentSolution.given_ans ?: ""
            val options = q?.mcq_options ?: emptyList()
            val optionDescs = options.map { it.description }
            val rawCorrectOpt = q?.correct_option
            val userGivenIdx = com.example.utils.McqAnswerUtils.normalizeToOptionIndex(givenAns)
            val resolvedCorrectBengali = com.example.utils.McqAnswerUtils.resolveCorrectOptionBengali(
                directCorrectOption = rawCorrectOpt,
                ansCorrectOption = null,
                solutionText = q?.solution,
                optionsDescriptions = optionDescs,
                userSelectedOptionIndex = userGivenIdx,
                isUserMarkedCorrectByServer = null
            )
            val givenAnsBengali = com.example.utils.McqAnswerUtils.toBengaliLetter(givenAns)
            val isUserCorrect = com.example.utils.McqAnswerUtils.isAnswerCorrect(givenAns, resolvedCorrectBengali)
            val isUserUnanswered = givenAns.isBlank()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Question Number & Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0072EC).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "প্রশ্ন ${toBengaliDigits((index + 1).toString())}/${toBengaliDigits(solutions.size.toString())}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0072EC),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        val isDark = isAppInDarkTheme()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isUserUnanswered -> MaterialTheme.colorScheme.surfaceVariant
                                isUserCorrect -> if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7)
                                else -> if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.45f) else Color(0xFFFEE2E2)
                            }
                        ) {
                            Text(
                                text = when {
                                    isUserUnanswered -> "উত্তরহীন"
                                    isUserCorrect -> "সঠিক উত্তর"
                                    else -> "ভুল উত্তর"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isUserUnanswered -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isUserCorrect -> if (isDark) Color(0xFF6EE7B7) else Color(0xFF059669)
                                    else -> if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val isDark = isAppInDarkTheme()

                    // Prominent Question Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "প্রশ্ন:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF60A5FA) else Color(0xFF0072EC)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            QuizRichContent(
                                text = q?.title?.takeIf { it.isNotBlank() } ?: "প্রশ্ন নং ${toBengaliDigits((index + 1).toString())}",
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                                textColor = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxImageHeight = 220.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // User Answer Status Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isUserUnanswered -> MaterialTheme.colorScheme.surfaceVariant
                            isUserCorrect -> if (isDark) Color(0xFF064E3B).copy(alpha = 0.65f) else Color(0xDCD1FADF)
                            else -> if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.45f) else Color(0xFFFEE2E2)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isUserUnanswered -> MaterialTheme.colorScheme.outlineVariant
                                isUserCorrect -> Color(0xFF10B981)
                                else -> Color(0xFFEF4444)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isUserUnanswered -> "তুমি কোনো উত্তর দেওনি • সঠিক উত্তর: ($resolvedCorrectBengali)"
                                    isUserCorrect -> "তোমার উত্তর সঠিক: ($givenAnsBengali)"
                                    else -> "তোমার উত্তর: ($givenAnsBengali) • সঠিক উত্তর: ($resolvedCorrectBengali)"
                                },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isUserUnanswered -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isUserCorrect -> if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                                    else -> if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = when {
                                    isUserUnanswered -> Icons.Default.Block
                                    isUserCorrect -> Icons.Default.CheckCircle
                                    else -> Icons.Default.Cancel
                                },
                                contentDescription = null,
                                tint = when {
                                    isUserUnanswered -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isUserCorrect -> Color(0xFF10B981)
                                    else -> Color(0xFFEF4444)
                                }
                            )
                        }
                    }

                    // Options List in Solution
                    if (options.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        options.forEachIndexed { optIdx, opt ->
                            val optLetter = com.example.utils.McqAnswerUtils.toBengaliLetter(opt.no).ifBlank {
                                com.example.utils.McqAnswerUtils.indexToBengaliLetter(optIdx)
                            }
                            val isThisCorrect = com.example.utils.McqAnswerUtils.isAnswerCorrect(opt.no ?: optLetter, resolvedCorrectBengali) ||
                                    com.example.utils.McqAnswerUtils.normalizeToOptionIndex(opt.no) == com.example.utils.McqAnswerUtils.normalizeToOptionIndex(resolvedCorrectBengali) ||
                                    com.example.utils.McqAnswerUtils.normalizeToOptionIndex(resolvedCorrectBengali) == optIdx
                            val isThisUserAns = !isUserUnanswered && (
                                com.example.utils.McqAnswerUtils.isAnswerCorrect(givenAns, opt.no ?: optLetter) ||
                                com.example.utils.McqAnswerUtils.normalizeToOptionIndex(givenAns) == optIdx
                            )

                            val (bgColor, borderColor, badgeColor) = when {
                                isThisCorrect -> Triple(
                                    if (isDark) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFFDCFCE7),
                                    Color(0xFF10B981),
                                    if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                )
                                isThisUserAns && !isThisCorrect -> Triple(
                                    if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.45f) else Color(0xFFFEE2E2),
                                    Color(0xFFEF4444),
                                    if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)
                                )
                                else -> Triple(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.4f else 0.55f),
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bgColor,
                                border = BorderStroke(if (isThisCorrect || isThisUserAns) 1.5.dp else 1.dp, borderColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = badgeColor.copy(alpha = 0.15f),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = optLetter,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeColor
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    QuizRichContent(
                                        text = com.example.utils.McqAnswerUtils.cleanOptionText(opt.description),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        textColor = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isThisCorrect || isThisUserAns) FontWeight.SemiBold else FontWeight.Normal,
                                        maxImageHeight = 120.dp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isThisCorrect) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    } else if (isThisUserAns) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Cancel, contentDescription = "Wrong", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Explanation Box
                    val correctIdx = com.example.utils.McqAnswerUtils.normalizeToOptionIndex(resolvedCorrectBengali) ?: 0
                    val correctOptText = com.example.utils.McqAnswerUtils.cleanOptionText(options.getOrNull(correctIdx)?.description)
                    val fallbackSolText = if (correctOptText.isNotBlank()) {
                        "সঠিক উত্তর: ($resolvedCorrectBengali) — $correctOptText"
                    } else {
                        "সঠিক উত্তর: ($resolvedCorrectBengali)"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ব্যাখ্যা ও সমাধান",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            QuizRichContent(
                                text = q?.solution?.takeIf { it.isNotBlank() } ?: fallbackSolText,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                maxImageHeight = 220.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "প্রশ্নটি রিপোর্ট করো",
                        fontSize = 13.sp,
                        color = if (isDark) Color(0xFF60A5FA) else Color(0xFF0072EC),
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                android.widget.Toast.makeText(
                                    context,
                                    "প্রশ্নটির রিপোর্ট সফলভাবে পাঠানো হয়েছে। ধন্যবাদ!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (index > 0) {
                        OutlinedButton(
                            onClick = onPrevious,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পেছনে")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (index < solutions.size - 1) {
                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("এগিয়ে যাও")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
