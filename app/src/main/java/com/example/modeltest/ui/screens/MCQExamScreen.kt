package com.example.modeltest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.components.ExitExamConfirmDialog
import com.example.modeltest.ui.components.ModelTestListSkeleton
import com.example.modeltest.ui.components.QuestionPaletteBottomSheet
import com.example.modeltest.ui.components.ScorePopupDialog
import com.example.utils.toBengaliDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCQExamScreen(
    sessionId: String,
    viewModel: ModelTestViewModel,
    onNavigateToCqReadOnly: (sessionId: String) -> Unit,
    onNavigateToCqUpload: (sessionId: String) -> Unit,
    onExitExam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPaletteSheet by remember { mutableStateOf(false) }

    // Preserve lifecycle state (onPause / onStop)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.saveLifecycleState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Intercept hardware / gesture back press with confirmation dialog
    BackHandler {
        viewModel.setExitDialogVisible(true)
    }

    if (uiState.showExitDialog) {
        ExitExamConfirmDialog(
            onConfirmExit = {
                viewModel.setExitDialogVisible(false)
                onExitExam()
            },
            onDismiss = {
                viewModel.setExitDialogVisible(false)
            }
        )
    }

    // Score Popup Dialog
    if (uiState.showScorePopup) {
        val score = uiState.minimalScoreResult?.obtained_score ?: 0.0
        val total = uiState.minimalScoreResult?.total_marks ?: (uiState.mcqQuestions.size.toDouble())
        ScorePopupDialog(
            score = score,
            totalMarks = total,
            isPractice = uiState.isPracticeSession,
            onProceed = {
                viewModel.dismissScorePopup()
                if (uiState.isPracticeSession) {
                    onNavigateToCqReadOnly(sessionId)
                } else {
                    onNavigateToCqUpload(sessionId)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Progress
                        val currentNum = uiState.currentQuestionIndex + 1
                        val totalNum = uiState.mcqQuestions.size
                        Text(
                            text = "${toBengaliDigits(currentNum.toString())}/${toBengaliDigits(totalNum.toString())}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Countdown Timer Box
                        val totalSec = uiState.remainingTimeSeconds
                        val mins = totalSec / 60
                        val secs = totalSec % 60
                        val formattedTime = "%02d:%02d".format(mins, secs)
                        val isUrgent = totalSec <= 120

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isUrgent) Color(0xFFFEF2F2) else Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, if (isUrgent) Color(0xFFFCA5A5) else Color(0xFFBFDBFE))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (isUrgent) Color(0xFFDC2626) else Color(0xFF1D4ED8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = toBengaliDigits(formattedTime),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgent) Color(0xFFDC2626) else Color(0xFF1D4ED8)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setExitDialogVisible(true) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit")
                    }
                },
                actions = {
                    TextButton(onClick = { showPaletteSheet = true }) {
                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সবগুলো দেখো", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFirst = uiState.currentQuestionIndex == 0
                    val isLast = uiState.currentQuestionIndex >= uiState.mcqQuestions.size - 1

                    OutlinedButton(
                        onClick = { viewModel.previousQuestion() },
                        enabled = !isFirst,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("পূর্ববর্তী")
                    }

                    if (isLast) {
                        Button(
                            onClick = { viewModel.submitFinalMcq(isTimeout = false) },
                            enabled = !uiState.isSubmittingMcq,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            if (uiState.isSubmittingMcq) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("জমা হচ্ছে...")
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("সাবমিট করো", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.nextQuestion() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("এগিয়ে যাও")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isMcqLoading && uiState.mcqQuestions.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding)) {
                ModelTestListSkeleton()
            }
        } else if (uiState.mcqQuestions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("কোনো প্রশ্ন পাওয়া যায়নি")
            }
        } else {
            val question = uiState.mcqQuestions.getOrNull(uiState.currentQuestionIndex)
            val selectedOption = question?.let { uiState.selectedAnswers[it.id] }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress Bar Indicator
                val progress = remember(uiState.currentQuestionIndex, uiState.mcqQuestions.size) {
                    if (uiState.mcqQuestions.isNotEmpty()) {
                        (uiState.currentQuestionIndex + 1).toFloat() / uiState.mcqQuestions.size.toFloat()
                    } else 0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (question != null) {
                    // Question Box
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "প্রশ্ন ${toBengaliDigits((uiState.currentQuestionIndex + 1).toString())}:",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = question.question ?: "",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )

                            if (!question.question_image.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                AsyncImage(
                                    model = question.question_image,
                                    contentDescription = "Question Image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }

                    // Options List
                    val options = question.options ?: emptyList()
                    val bengaliLetters = listOf("ক", "খ", "গ", "ঘ")

                    options.forEachIndexed { optIdx, opt ->
                        val optIndex = opt.index ?: optIdx
                        val isSelected = selectedOption == optIndex
                        val optionPrefix = bengaliLetters.getOrElse(optIdx) { "${optIdx + 1}" }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.selectOption(question.id, optIndex)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = optionPrefix,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = opt.text ?: "",
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF1D4ED8) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Question Palette Bottom Sheet
    if (showPaletteSheet) {
        val answeredIndices = remember(uiState.selectedAnswers, uiState.mcqQuestions) {
            uiState.mcqQuestions.mapIndexedNotNull { index, question ->
                if (uiState.selectedAnswers.containsKey(question.id)) index else null
            }.toSet()
        }

        QuestionPaletteBottomSheet(
            totalQuestions = uiState.mcqQuestions.size,
            currentIndex = uiState.currentQuestionIndex,
            answeredIndices = answeredIndices,
            onSelectQuestion = { viewModel.navigateToQuestion(it) },
            onDismiss = { showPaletteSheet = false }
        )
    }
}
