package com.example.modeltest.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.example.ui.components.SlideViewerDialog
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

    var activePdfUrl by remember { mutableStateOf<String?>(null) }
    var activePdfTitle by remember { mutableStateOf("") }
    var isSyllabusExpanded by remember { mutableStateOf(false) }

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
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("হোম এ ফিরে যাও", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                            Text(
                                text = "দুঃখিত!",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "তুমি টেস্টটি মিস করেছো",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B),
                                textAlign = TextAlign.Center
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

                // 2. Exam Rules & Instructions Section (shown when upcoming/active)
                if (!isMissed) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            val defaultRules = listOf(
                                "পরীক্ষা শুধুমাত্র একবারই দেওয়া যাবে।",
                                "CQ পরীক্ষার উত্তরের ছবি আপলোড করার জন্য নির্ধারিত অতিরিক্ত সময় পাওয়া যাবে।"
                            )
                            val rulesToDisplay = info?.instructions?.ifEmpty { defaultRules } ?: defaultRules

                            rulesToDisplay.forEach { rule ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = rule,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. MCQ & CQ Info Cards with Distinct Master Solution Buttons
                val mcqMinutes = info?.mcq_duration_minutes ?: 30
                val cqMinutes = info?.cq_duration_minutes ?: 100
                val cqDurationFormatted = if (cqMinutes >= 60) {
                    val hours = cqMinutes / 60
                    val mins = cqMinutes % 60
                    if (mins > 0) "${toBengaliDigits(hours.toString())} ঘণ্টা ${toBengaliDigits(mins.toString())} মিনিট"
                    else "${toBengaliDigits(hours.toString())} ঘণ্টা"
                } else {
                    "${toBengaliDigits(cqMinutes.toString())} মিনিট"
                }

                // MCQ Card
                ExamPartBlockCard(
                    title = "MCQ",
                    count = "${toBengaliDigits((info?.mcq_count ?: 30).toString())}টি প্রশ্ন",
                    duration = "সময় ${toBengaliDigits(mcqMinutes.toString())} মিনিট",
                    icon = Icons.Default.FactCheck,
                    iconBg = Color(0xFFE0F2FE),
                    iconTint = Color(0xFF0284C7),
                    showMasterSolution = true,
                    onMasterSolutionClick = {
                        viewModel.loadMasterSolution(modelTestId, "mcq") { url ->
                            activePdfUrl = url
                            activePdfTitle = "${info?.title ?: "মডেল টেস্ট"} - বহুনির্বাচনি সমাধান"
                        }
                    }
                )

                // CQ Card
                ExamPartBlockCard(
                    title = "CQ",
                    count = "${toBengaliDigits((info?.cq_count ?: 2).toString())}টি প্রশ্ন",
                    duration = "সময় $cqDurationFormatted",
                    icon = Icons.Default.EditNote,
                    iconBg = Color(0xFFFEF3C7),
                    iconTint = Color(0xFFD97706),
                    showMasterSolution = true,
                    onMasterSolutionClick = {
                        viewModel.loadMasterSolution(modelTestId, "cq") { url ->
                            activePdfUrl = url
                            activePdfTitle = "${info?.title ?: "মডেল টেস্ট"} - সৃজনশীল সমাধান"
                        }
                    }
                )

                // 4. Subject & Chapter (বিষয় ও অধ্যায়) Expandable Card
                val subjectName = info?.subject_name 
                    ?: info?.subjects?.firstOrNull()?.display_bn 
                    ?: (info?.title?.split(" ")?.firstOrNull() ?: "বিষয়")
                
                val chapterItems = info?.hierarchy?.firstOrNull()?.chapters ?: emptyList()
                val chapterCount = if (chapterItems.isNotEmpty()) chapterItems.size else 1
                val subjectCount = if (!info?.subjects.isNullOrEmpty()) info?.subjects!!.size else 1

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSyllabusExpanded = !isSyllabusExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Book 3D-styled Icon
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Subject Count
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "বিষয়",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${toBengaliDigits(subjectCount.toString())}টি",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Vertical Divider
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Chapter Count
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "অধ্যায়",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${toBengaliDigits(chapterCount.toString())}টি",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Expand Arrow
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSyllabusExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isSyllabusExpanded) "Collapse" else "Expand",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Expanded Subject & Chapter details
                        AnimatedVisibility(
                            visible = isSyllabusExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "অন্তর্ভুক্ত সিলেবাস ও বিষয়:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "📖 $subjectName",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )

                                        if (chapterItems.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            chapterItems.forEach { chap ->
                                                Text(
                                                    text = "• ${chap.name ?: "অধ্যায় ${chap.no}"}",
                                                    fontSize = 12.5.sp,
                                                    color = Color(0xFF475569),
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "• অধ্যায়ভিত্তিক পূর্ণাঙ্গ মডেল টেস্ট",
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Practice Test Section (প্র্যাকটিস টেস্ট)
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
                                text = "প্র্যাকটিস টেস্ট দিয়ে নিজেকে যাচাই করে নাও",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val remainingTries = uiState.remainingPracticeCount
                        val triesBengali = toBengaliDigits(remainingTries.toString())
                        Text(
                            text = "আর মাত্র $triesBengali বার প্র্যাকটিস টেস্ট দিতে পারবে",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDC2626)
                        )

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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
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
                                Text("প্র্যাকটিস টেস্ট শুরু করো", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Notice Card (Yellow highlight)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "i",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "প্র্যাকটিস টেস্টের ক্ষেত্রে শুধু MCQ টেস্ট দিতে পারবে এবং ফিডব্যাক পাবে। CQ উত্তরপত্র সাবমিট ও রিভিঊয়ের জন্য তোমাকে নির্ধারিত সময়ে মডেল টেস্ট দিতে হবে।",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Practice Test History & "ফিডব্যাক দেখো" action buttons (Screenshot 4)
                        val practiceSessions = uiState.retakeContainer?.sessions ?: emptyList()
                        val effectiveSessions = if (practiceSessions.isNotEmpty()) {
                            practiceSessions
                        } else if (uiState.attemptedPracticeCount > 0) {
                            (1..uiState.attemptedPracticeCount).map { 
                                com.example.modeltest.data.RetakeSessionItem(
                                    session_id = "${modelTestId}_practice_$it",
                                    attempt_number = it,
                                    is_completed = true
                                )
                            }
                        } else emptyList()

                        if (effectiveSessions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(14.dp))

                            effectiveSessions.forEachIndexed { index, session ->
                                val attemptNum = session.attempt_number ?: (index + 1)
                                val sid = session.session_id ?: "${modelTestId}_practice_${attemptNum}"

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "প্র্যাকটিস টেস্ট ${toBengaliDigits(attemptNum.toString())}",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Full-width "ফিডব্যাক দেখো" button matching Screenshot 4
                                    Button(
                                        onClick = { onViewFeedback(sid) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFEFF6FF),
                                            contentColor = Color(0xFF1D4ED8)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        Text(
                                            text = "ফিডব্যাক দেখো",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
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

    // In-app Master Solution PDF Viewer Dialog
    activePdfUrl?.let { pdfUrl ->
        SlideViewerDialog(
            slideUrl = pdfUrl,
            title = activePdfTitle,
            onDismiss = { activePdfUrl = null }
        )
    }
}

@Composable
private fun ExamPartBlockCard(
    title: String,
    count: String,
    duration: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    showMasterSolution: Boolean = true,
    onMasterSolutionClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconBg,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = count, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1.2f)) {
                    Text(text = duration, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (showMasterSolution) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onMasterSolutionClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEFF6FF),
                        contentColor = Color(0xFF1D4ED8)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text("মাস্টার সল্যুশন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
