package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.McqOptionItem
import com.example.quiz.PracticeQuizViewModel
import com.example.ui.components.QuizRichContent

private fun toBengaliDigits(number: Any): String {
    val english = number.toString()
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val builder = StringBuilder()
    for (char in english) {
        if (char in '0'..'9') {
            builder.append(banglaDigits[char - '0'])
        } else {
            builder.append(char)
        }
    }
    return builder.toString()
}

private fun toBengaliOptionLetter(optionNo: String?): String {
    return when (optionNo?.trim()?.uppercase()) {
        "A", "1" -> "ক"
        "B", "2" -> "খ"
        "C", "3" -> "গ"
        "D", "4" -> "ঘ"
        else -> optionNo ?: ""
    }
}

@Composable
fun PracticeQuizPlayerScreen(
    sessionId: String,
    viewModel: PracticeQuizViewModel,
    onBack: () -> Unit,
    onNavigateToResult: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val subjectColor = remember(uiState.subjectColorHex) {
        try {
            if (!uiState.subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(uiState.subjectColorHex))
            } else {
                Color(0xFF0072EC)
            }
        } catch (_: Exception) {
            Color(0xFF0072EC)
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }

    BackHandler {
        showExitDialog = true
    }

    val questions = uiState.session?.questions ?: emptyList()
    val totalCount = questions.size.coerceAtLeast(uiState.selectedQuestionCount)
    val currentQuestion = uiState.currentQuestion

    val answeredCount = uiState.answeredQuestionsCount
    val remainingSec = uiState.remainingSeconds
    val isUrgent = uiState.isUrgentTimer

    val timerColor by animateColorAsState(
        targetValue = if (isUrgent) Color(0xFFEF4444) else Color(0xFF10B981),
        label = "timerColor"
    )

    // Pulse animation for urgent timer
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isUrgent) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    // Top Bar Row: Close Button + Countdown Timer + Submit Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { showExitDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .testTag("player_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Quiz",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Countdown Timer Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = timerColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.5.dp, timerColor.copy(alpha = 0.6f)),
                            modifier = Modifier.scale(pulseScale)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = timerColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = toBengaliDigits(uiState.timeRemainingFormatted),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = timerColor
                                )
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = { showSubmitDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("top_submit_button")
                        ) {
                            Text(
                                text = "সাবমিট",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Question Number Navigation Strip (1 .. N)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        questions.forEachIndexed { index, question ->
                            val isCurrent = index == uiState.currentQuestionIndex
                            val isAnswered = uiState.userAnswers.containsKey(question.id)

                            val chipBg = when {
                                isCurrent -> subjectColor
                                isAnswered -> Color(0xFF10B981)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                            val chipText = when {
                                isCurrent || isAnswered -> Color.White
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = chipBg,
                                border = if (!isCurrent && !isAnswered) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                } else null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.goToQuestionIndex(index) }
                                    .testTag("nav_question_$index")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = toBengaliDigits(index + 1),
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent || isAnswered) FontWeight.Bold else FontWeight.Medium,
                                        color = chipText
                                    )
                                }
                            }
                        }
                    }

                    // Sub Header: Answered Counter + Color Legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "উত্তর সিলেক্ট করেছো: ${toBengaliDigits(answeredCount)}/${toBengaliDigits(totalCount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = subjectColor
                        )

                        // Legend dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendItem(color = Color(0xFF10B981), label = "পূরণ")
                            LegendItem(color = subjectColor, label = "বর্তমান")
                            LegendItem(color = MaterialTheme.colorScheme.outlineVariant, label = "পূরণ না")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFirst = uiState.currentQuestionIndex == 0
                    val isLast = uiState.currentQuestionIndex >= (questions.size - 1)

                    // Previous Button
                    OutlinedButton(
                        onClick = { viewModel.goToPreviousQuestion() },
                        enabled = !isFirst,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("prev_question_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "পূর্ববর্তী",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Next or Submit Button
                    if (isLast) {
                        Button(
                            onClick = { showSubmitDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("final_submit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "জমা দিন",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.goToNextQuestion() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("next_question_button")
                        ) {
                            Text(
                                text = "পরবর্তী",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("practice_quiz_player_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentQuestion != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Question Header Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Badge: Question Number
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = subjectColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "প্রশ্ন ${toBengaliDigits(uiState.currentQuestionIndex + 1)}/${toBengaliDigits(totalCount)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = subjectColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                val marks = currentQuestion.allocated_marks?.toDoubleOrNull()?.toInt()
                                    ?: currentQuestion.allocated_marks?.toIntOrNull()
                                if (marks != null) {
                                    Text(
                                        text = "মান: ${toBengaliDigits(marks)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Question Title & Diagrams
                            QuizRichContent(
                                text = currentQuestion.title ?: "",
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
                                textColor = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxImageHeight = 220.dp
                            )
                        }
                    }

                    // Section Title
                    Text(
                        text = "সঠিক উত্তরটি নির্বাচন করো:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 4 MCQ Options
                    val selectedAnswer = uiState.userAnswers[currentQuestion.id]
                    val options = currentQuestion.mcq_options ?: emptyList()

                    options.forEach { option ->
                        val isOptionSelected = selectedAnswer == option.no
                        McqOptionCard(
                            option = option,
                            isSelected = isOptionSelected,
                            subjectColor = subjectColor,
                            onClick = {
                                viewModel.selectOption(currentQuestion.id, option.no ?: "")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = subjectColor)
                }
            }
        }
    }

    // Exit Confirmation Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = "কুইজ ছেড়ে যেতে চান?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "আপনি কুইজ ছেড়ে গেলে আপনার বর্তমান অগ্রগতি সংরক্ষিত থাকবে, তবে কুইজটির ফলাফল হিসাব করা হবে না। আপনি কি নিশ্চিত?",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.resetQuizFlow()
                        onBack()
                    }
                ) {
                    Text("বেরিয়ে যান", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("কুইজে থাকুন")
                }
            }
        )
    }

    // Submit Confirmation Dialog
    if (showSubmitDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isSubmitting) showSubmitDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = subjectColor,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "কুইজ সাবমিট করবেন?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "আপনার মোট ${toBengaliDigits(totalCount)}টি প্রশ্নের মধ্যে ${toBengaliDigits(answeredCount)}টি প্রশ্নের উত্তর দেওয়া হয়েছে।",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    if (answeredCount < totalCount) {
                        Text(
                            text = "বাকি ${toBengaliDigits(totalCount - answeredCount)}টি প্রশ্ন এখনও উত্তরহীন আছে!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (!uiState.submitError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = uiState.submitError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        showSubmitDialog = false
                                        val targetSid = sessionId.ifBlank { uiState.sessionId }
                                        onNavigateToResult(targetSid)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = subjectColor)
                                ) {
                                    Text("সরাসরি ফলাফল দেখুন", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetSid = sessionId.ifBlank { uiState.sessionId }
                        Log.d("PracticeQuizPlayerScreen", "Submit confirm button clicked! paramSessionId=$sessionId, uiStateSessionId=${uiState.sessionId}, targetSid=$targetSid")
                        viewModel.submitFinalQuiz(explicitSessionId = targetSid) { sid ->
                            Log.d("PracticeQuizPlayerScreen", "submitFinalQuiz onSuccess triggered: sid=$sid. Navigating to result...")
                            showSubmitDialog = false
                            onNavigateToResult(sid)
                        }
                    },
                    enabled = !uiState.isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
                    modifier = Modifier.testTag("submit_confirm_button")
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সাবমিট হচ্ছে...")
                    } else {
                        Text(if (!uiState.submitError.isNullOrBlank()) "পুনরায় চেষ্টা করুন" else "হ্যাঁ, সাবমিট করুন")
                    }
                }
            },
            dismissButton = {
                if (!uiState.isSubmitting) {
                    TextButton(onClick = { showSubmitDialog = false }) {
                        Text("আরও দেখুন")
                    }
                }
            }
        )
    }
}

@Composable
private fun McqOptionCard(
    option: McqOptionItem,
    isSelected: Boolean,
    subjectColor: Color,
    onClick: () -> Unit
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "optionScale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) subjectColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = if (isSelected) 2.dp else 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("option_${option.no}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Option Letter Badge (ক / খ / গ / ঘ)
            Surface(
                shape = CircleShape,
                color = if (isSelected) subjectColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .size(32.dp)
                    .scale(checkScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = toBengaliOptionLetter(option.no),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Option description / image content
            QuizRichContent(
                text = option.description ?: "",
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                textColor = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxImageHeight = 140.dp,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = subjectColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
