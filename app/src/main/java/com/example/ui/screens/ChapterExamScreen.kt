package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.utils.toBengaliDigits
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterExamScreen(
    sessionId: String,
    lessonId: String,
    examTitle: String,
    chapterName: String,
    viewModel: ChapterExamViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(sessionId) {
        viewModel.loadExamInfo(sessionId, lessonId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
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
                    onBack = { onBack() },
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
                    onBack = { onBack() }
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
                            color = Color(0xFF1E293B)
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    onBack = onBack
                )
            }

            ExamStage.SOLUTIONS -> {
                ExamSolutionsContent(
                    uiState = uiState,
                    onNext = { viewModel.nextSolution() },
                    onPrevious = { viewModel.previousSolution() },
                    onBack = { onBack() }
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
    val displaySubject = info?.subject?.display ?: "Bangla 1st Paper"
    val chapterText = info?.chapters?.firstOrNull()?.name ?: defaultChapter
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isOngoing -> Color(0xFFD1FAE5)
                        isUpcoming -> Color(0xFFE0F2FE)
                        else -> Color(0xFFFEE2E2)
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
                            isOngoing -> Color(0xFF059669)
                            isUpcoming -> Color(0xFF0284C7)
                            else -> Color(0xFFEF4444)
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = displaySubject,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0072EC)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "অধ্যায়: $chapterText",
                            fontSize = 13.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "মোট প্রশ্ন: ${toBengaliDigits(totalQ.toString())} টি | সময়: ২৫ মিনিট",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
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
            color = Color(0xFFF1F5F9),
            modifier = Modifier.size(54.dp, 44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = Color(0xFF64748B))
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF0072EC))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("প্রশ্ন", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    "${toBengaliDigits(totalQ.toString())} টি",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFD97706))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("সময়", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("২৪ ঘণ্টা", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Rules Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "টেস্টের নিয়মাবলী",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        RuleItem(
                            iconBg = Color(0xFFFEF3C7),
                            icon = Icons.Default.AccessTime,
                            iconTint = Color(0xFFD97706),
                            text = "প্রতিটি প্রশ্নের জন্য ৪টি করে বিকল্প অপশন থাকবে।"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        RuleItem(
                            iconBg = Color(0xDCD1FADF),
                            icon = Icons.Default.Check,
                            iconTint = Color(0xFF059669),
                            text = "নির্দিষ্ট সময়ের মধ্যে সকল প্রশ্নের উত্তর সাবমিট করতে হবে।"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        RuleItem(
                            iconBg = Color(0xFFFFEDD5),
                            icon = Icons.Default.BarChart,
                            iconTint = Color(0xFFEA580C),
                            text = "টেস্ট শেষে তোমার পারফরম্যান্স এবং সঠিক সমাধান দেখতে পারবে।"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartExam,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
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
            color = Color(0xFF334155),
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
                        color = Color(0xFF0F172A),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
                    }
                },
                actions = {
                    // Timer pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFEF3C7),
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
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
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
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { /* Refresh action */ }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("রিফ্রেশ দাও", fontSize = 12.sp, color = Color(0xFF64748B))
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
                            textColor = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            maxImageHeight = 220.dp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // MCQ Options
                        val selectedAns = uiState.userAnswers[currentQuestion.id] ?: ""
                        val options = currentQuestion.mcq_options ?: emptyList()

                        options.forEach { option ->
                            val optionNo = option.no ?: ""
                            val isSelected = selectedAns.equals(optionNo, ignoreCase = true)

                            McqOptionCard(
                                option = option,
                                isSelected = isSelected,
                                onClick = { onSelectOption(optionNo) }
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
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFCBD5E1)))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("পেছনে", color = Color(0xFF475569))
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
    option: McqOptionItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF0072EC) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuizRichContent(
                text = option.description ?: "",
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                textColor = if (isSelected) Color(0xFF0072EC) else Color(0xFF1E293B),
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "চ্যাপ্টার এক্সামের পারফরমেন্স",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
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
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPdf(uiState.pdfAttachmentUrl) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("📄 পিডিএফ দেখতে এখানে ট্যাপ করো ⬇", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = defaultTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "নিচের ড্যাশবোর্ড দেখে তোমার পারফরম্যান্সের ধারণা নাও, আর তোমার দেওয়া উত্তরগুলো মিলিয়ে নাও",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dashboard Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut Chart
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(90.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 10.dp.toPx()
                                    drawArc(
                                        color = Color(0xFFE2E8F0),
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
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "${toBengaliDigits(correctCount.toString())}/${toBengaliDigits(totalCount.toString())}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
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
                        OutlinedButton(
                            onClick = onShowSolutions,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF0072EC)))
                        ) {
                            Text("সমাধান", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0072EC))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2 Metric cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("মোট সময় ব্যয়", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("১ মিনিট", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("প্রতি প্রশ্নে সময়", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("২.৫ সেকেন্ড", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
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
        Text(text = label, fontSize = 13.sp, color = Color(0xFF334155))
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
    onBack: () -> Unit
) {
    val solutions = uiState.solutions
    val index = uiState.solutionIndex
    val currentSolution = solutions.getOrNull(index)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "সমাধান",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
                    }
                },
                actions = {
                    IconButton(onClick = { /* Grid overview */ }) {
                        Icon(Icons.Default.GridView, contentDescription = "Grid", tint = Color(0xFF64748B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (currentSolution != null) {
            val q = currentSolution.question
            val givenAns = currentSolution.given_ans ?: ""
            val correctOpt = q?.correct_option ?: "B"

            val isUserCorrect = givenAns.equals(correctOpt, ignoreCase = true)
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
                    // Question Number
                    Text(
                        text = "${toBengaliDigits((index + 1).toString())}/${toBengaliDigits(solutions.size.toString())}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEC4899)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title & Diagrams
                    QuizRichContent(
                        text = q?.title ?: "",
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                        textColor = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        maxImageHeight = 220.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Answer Status Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isUserUnanswered -> Color(0xFFF1F5F9)
                            isUserCorrect -> Color(0xDCD1FADF)
                            else -> Color(0xFFFEE2E2)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isUserUnanswered -> Color(0xFFCBD5E1)
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
                                    isUserUnanswered -> "তুমি কোনো উত্তর দেওনি!"
                                    isUserCorrect -> "তোমার উত্তর সঠিক: $givenAns"
                                    else -> "তোমার উত্তর: $givenAns"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isUserUnanswered -> Color(0xFF64748B)
                                    isUserCorrect -> Color(0xFF047857)
                                    else -> Color(0xFFB91C1C)
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
                                    isUserUnanswered -> Color(0xFF64748B)
                                    isUserCorrect -> Color(0xFF10B981)
                                    else -> Color(0xFFEF4444)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Correct Option Banner
                    Text(
                        text = "সঠিক উত্তর: $correctOpt",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Explanation Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            QuizRichContent(
                                text = q?.solution ?: "সঠিক উত্তর: $correctOpt",
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                                textColor = Color(0xFF334155),
                                maxImageHeight = 220.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "প্রশ্নটি রিপোর্ট করো",
                        fontSize = 13.sp,
                        color = Color(0xFF0072EC),
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { /* Report question */ }
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
