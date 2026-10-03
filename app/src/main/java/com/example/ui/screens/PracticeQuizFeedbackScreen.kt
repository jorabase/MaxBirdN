package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.PracticeQuizQuestionItem
import com.example.quiz.FeedbackFilter
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeQuizFeedbackScreen(
    sessionId: String,
    viewModel: PracticeQuizViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionId) {
        if (uiState.feedbackSession == null || uiState.sessionId != sessionId) {
            viewModel.loadQuizFeedback(sessionId)
        }
    }

    LaunchedEffect(uiState.bookmarkMessage) {
        uiState.bookmarkMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBookmarkMessage()
        }
    }

    val session = uiState.feedbackSession
    val questions = session?.questions ?: emptyList()
    val answersMap = remember(session) {
        session?.question_answer?.associateBy { it.id } ?: emptyMap()
    }

    // Counts
    val totalCount = questions.size
    var correctCount = 0
    var incorrectCount = 0
    var unansweredCount = 0

    questions.forEach { q ->
        val ans = answersMap[q.id]
        val givenAns = ans?.given_ans ?: q.given_ans
        val correctOpt = q.correct_option ?: ans?.correct_ans
        when {
            givenAns.isNullOrBlank() -> unansweredCount++
            givenAns.equals(correctOpt, ignoreCase = true) -> correctCount++
            else -> incorrectCount++
        }
    }

    // Filter questions according to selected filter
    val filteredQuestions = remember(questions, uiState.feedbackFilter, answersMap) {
        when (uiState.feedbackFilter) {
            FeedbackFilter.ALL -> questions
            FeedbackFilter.CORRECT -> questions.filter { q ->
                val ans = answersMap[q.id]
                val given = ans?.given_ans ?: q.given_ans
                val correct = q.correct_option ?: ans?.correct_ans
                !given.isNullOrBlank() && given.equals(correct, ignoreCase = true)
            }
            FeedbackFilter.INCORRECT -> questions.filter { q ->
                val ans = answersMap[q.id]
                val given = ans?.given_ans ?: q.given_ans
                val correct = q.correct_option ?: ans?.correct_ans
                !given.isNullOrBlank() && !given.equals(correct, ignoreCase = true)
            }
            FeedbackFilter.UNANSWERED -> questions.filter { q ->
                val ans = answersMap[q.id]
                val given = ans?.given_ans ?: q.given_ans
                given.isNullOrBlank()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("feedback_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "কুইজের ফিডব্যাক ও সলিউশন",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { viewModel.loadQuizFeedback(sessionId) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("feedback_done_button")
                    ) {
                        Text(
                            text = "হোমে ফিরে যান",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("practice_quiz_feedback_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isFeedbackLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF0072EC),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ফিডব্যাক ও সমাধান লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.feedbackError != null && session == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.feedbackError ?: "লোড করা যায়নি",
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadQuizFeedback(sessionId) }) {
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top Summary Card (Counters + Filter Chips)
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "সারসংক্ষেপ (${toBengaliDigits(totalCount)}টি প্রশ্ন)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 3 Status Pill Badges
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StatusCounterPill(
                                            icon = Icons.Default.CheckCircle,
                                            label = "${toBengaliDigits(correctCount)}টি সঠিক",
                                            bgColor = Color(0xFF10B981).copy(alpha = 0.12f),
                                            contentColor = Color(0xFF059669),
                                            modifier = Modifier.weight(1f)
                                        )
                                        StatusCounterPill(
                                            icon = Icons.Default.Cancel,
                                            label = "${toBengaliDigits(incorrectCount)}টি ভুল",
                                            bgColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                                            contentColor = Color(0xFFDC2626),
                                            modifier = Modifier.weight(1f)
                                        )
                                        StatusCounterPill(
                                            icon = Icons.Default.HelpOutline,
                                            label = "${toBengaliDigits(unansweredCount)}টি উত্তরহীন",
                                            bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Horizontal Filter Chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FeedbackFilterChip(
                                            title = "সব উত্তর (${toBengaliDigits(totalCount)})",
                                            isSelected = uiState.feedbackFilter == FeedbackFilter.ALL,
                                            onClick = { viewModel.setFeedbackFilter(FeedbackFilter.ALL) }
                                        )
                                        FeedbackFilterChip(
                                            title = "সঠিক (${toBengaliDigits(correctCount)})",
                                            isSelected = uiState.feedbackFilter == FeedbackFilter.CORRECT,
                                            onClick = { viewModel.setFeedbackFilter(FeedbackFilter.CORRECT) }
                                        )
                                        FeedbackFilterChip(
                                            title = "ভুল (${toBengaliDigits(incorrectCount)})",
                                            isSelected = uiState.feedbackFilter == FeedbackFilter.INCORRECT,
                                            onClick = { viewModel.setFeedbackFilter(FeedbackFilter.INCORRECT) }
                                        )
                                        FeedbackFilterChip(
                                            title = "উত্তরহীন (${toBengaliDigits(unansweredCount)})",
                                            isSelected = uiState.feedbackFilter == FeedbackFilter.UNANSWERED,
                                            onClick = { viewModel.setFeedbackFilter(FeedbackFilter.UNANSWERED) }
                                        )
                                    }
                                }
                            }
                        }

                        // Question cards
                        itemsIndexed(
                            items = filteredQuestions,
                            key = { _, q -> q.id }
                        ) { index, question ->
                            val ans = answersMap[question.id]
                            val userGivenAns = ans?.given_ans ?: question.given_ans
                            val correctOpt = question.correct_option ?: ans?.correct_ans
                            val isBookmarked = uiState.bookmarkedQuestionIds.contains(question.id)

                            QuestionFeedbackCard(
                                questionNumber = index + 1,
                                question = question,
                                userGivenAns = userGivenAns,
                                correctOpt = correctOpt,
                                isBookmarked = isBookmarked,
                                onBookmarkToggle = { viewModel.toggleBookmark(question.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCounterPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun FeedbackFilterChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF0072EC) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun QuestionFeedbackCard(
    questionNumber: Int,
    question: PracticeQuizQuestionItem,
    userGivenAns: String?,
    correctOpt: String?,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Question Number Badge + Bookmark Button
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
                        text = "প্রশ্ন ${toBengaliDigits(questionNumber)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0072EC),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark Question",
                        tint = if (isBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Question Title & Diagram
            QuizRichContent(
                text = question.title ?: "",
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                textColor = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxImageHeight = 220.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Options
            val options = question.mcq_options ?: emptyList()
            options.forEach { opt ->
                val isUserSelection = userGivenAns != null && userGivenAns.equals(opt.no, ignoreCase = true)
                val isCorrectOption = correctOpt != null && correctOpt.equals(opt.no, ignoreCase = true)

                FeedbackOptionRow(
                    option = opt,
                    isUserSelection = isUserSelection,
                    isCorrectOption = isCorrectOption
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Solution / Explanation Section
            val explanation = question.description?.takeIf { it.isNotBlank() }
                ?: (question as? com.example.api.LiveExamQuestionItem)?.solution?.takeIf { it.isNotBlank() }

            if (!explanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ব্যাখ্যা ও সমাধান",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        QuizRichContent(
                            text = explanation,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            textColor = MaterialTheme.colorScheme.onSurface,
                            maxImageHeight = 200.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackOptionRow(
    option: com.example.api.McqOptionItem,
    isUserSelection: Boolean,
    isCorrectOption: Boolean
) {
    val (bgColor, borderColor, badgeColor) = when {
        isCorrectOption -> Triple(
            Color(0xFFDCFCE7), // Light green
            Color(0xFF10B981),
            Color(0xFF059669)
        )
        isUserSelection && !isCorrectOption -> Triple(
            Color(0xFFFEE2E2), // Light red
            Color(0xFFEF4444),
            Color(0xFFDC2626)
        )
        else -> Triple(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isCorrectOption || isUserSelection) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = toBengaliOptionLetter(option.no),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            // Option text or image
            QuizRichContent(
                text = option.description ?: "",
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                textColor = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCorrectOption || isUserSelection) FontWeight.SemiBold else FontWeight.Normal,
                maxImageHeight = 140.dp,
                modifier = Modifier.weight(1f)
            )

            when {
                isCorrectOption && isUserSelection -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981)
                    ) {
                        Text(
                            text = "তোমার সঠিক উত্তর",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                isCorrectOption -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981)
                    ) {
                        Text(
                            text = "সঠিক উত্তর",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                isUserSelection -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEF4444)
                    ) {
                        Text(
                            text = "তোমার ভুল উত্তর",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
