package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.McqOptionItem
import com.example.api.PracticeQuizQuestionItem
import com.example.quiz.FeedbackFilter
import com.example.quiz.PracticeQuizViewModel
import com.example.ui.components.QuizImageCard
import com.example.ui.components.QuizRichContent
import com.example.utils.McqAnswerUtils
import kotlinx.coroutines.launch

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

private data class EvaluatedFeedbackQuestion(
    val originalNumber: Int,
    val totalCount: Int,
    val question: PracticeQuizQuestionItem,
    val questionTitle: String,
    val options: List<McqOptionItem>,
    val userGivenAns: String?,
    val userOptionIndex: Int?,
    val correctOptionIndex: Int,
    val userBengaliLetter: String,
    val correctBengaliLetter: String,
    val correctOptionText: String?,
    val isAnswered: Boolean,
    val isCorrect: Boolean,
    val explanationText: String?,
    val solutionImageUrl: String?,
    val chapterName: String?,
    val sourceTag: String?
)

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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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

    val session = uiState.feedbackSession ?: uiState.session
    val rawQuestions = session?.questions ?: emptyList()
    val inMemoryQuestionsById = remember(uiState.session) {
        uiState.session?.questions?.associateBy { it.id }.orEmpty()
    }
    val answersMap = remember(session) {
        session?.question_answer?.associateBy { it.id } ?: emptyMap()
    }

    // Build unified evaluated questions list so counters, filters, and cards use identical logic
    val evaluatedQuestions = remember(rawQuestions, answersMap, uiState.userAnswers, inMemoryQuestionsById) {
        val total = rawQuestions.size
        rawQuestions.mapIndexed { idx, q ->
            val origQ = inMemoryQuestionsById[q.id] ?: uiState.session?.questions?.getOrNull(idx)
            val ans = answersMap[q.id]

            val resolvedTitle = q.title?.takeIf { it.isNotBlank() }
                ?: origQ?.title?.takeIf { it.isNotBlank() }
                ?: ""

            val resolvedOptions = q.mcq_options?.takeIf { it.isNotEmpty() }
                ?: origQ?.mcq_options?.takeIf { it.isNotEmpty() }
                ?: emptyList()

            val givenAns = ans?.given_ans?.takeIf { it.isNotBlank() }
                ?: q.given_ans?.takeIf { it.isNotBlank() }
                ?: uiState.userAnswers[q.id]?.takeIf { it.isNotBlank() }
                ?: origQ?.given_ans?.takeIf { it.isNotBlank() }

            val explanation = q.solution?.takeIf { it.isNotBlank() }
                ?: q.description?.takeIf { it.isNotBlank() }
                ?: origQ?.solution?.takeIf { it.isNotBlank() }
                ?: origQ?.description?.takeIf { it.isNotBlank() }

            val descs = resolvedOptions.map { it.description }
            val rawCorrect = q.correct_option?.takeIf { it.isNotBlank() }
                ?: ans?.correct_ans?.takeIf { it.isNotBlank() }
                ?: origQ?.correct_option?.takeIf { it.isNotBlank() }

            val userIdx = if (!givenAns.isNullOrBlank()) {
                McqAnswerUtils.normalizeToOptionIndex(givenAns)
                    ?: resolvedOptions.indexOfFirst { it.no.equals(givenAns, ignoreCase = true) }.takeIf { it >= 0 }
            } else null

            val correctIdx = McqAnswerUtils.resolveCorrectOptionIndex(
                directCorrectOption = rawCorrect,
                ansCorrectOption = ans?.correct_ans,
                solutionText = explanation,
                optionsDescriptions = descs,
                userSelectedOptionIndex = userIdx,
                isUserMarkedCorrectByServer = ans?.is_correct
            )

            val correctBengali = McqAnswerUtils.indexToBengaliLetter(correctIdx)
            val userBengali = if (userIdx != null) {
                McqAnswerUtils.indexToBengaliLetter(userIdx)
            } else {
                McqAnswerUtils.toBengaliLetter(givenAns)
            }

            val isAnswered = !givenAns.isNullOrBlank()
            val isCorrect = isAnswered && (
                ans?.is_correct == true ||
                    (ans?.is_correct != false && (
                        (userIdx != null && userIdx == correctIdx) ||
                            McqAnswerUtils.isAnswerCorrect(givenAns, correctBengali)
                        ))
                )

            val correctOptionRawText = resolvedOptions.getOrNull(correctIdx)?.description
            val cleanedCorrectOptionText = McqAnswerUtils.cleanOptionText(correctOptionRawText).takeIf { it.isNotBlank() }

            val qNumber = q.question_no?.toIntOrNull()?.takeIf { it in 1..(total + 5) } ?: (idx + 1)

            EvaluatedFeedbackQuestion(
                originalNumber = qNumber,
                totalCount = total,
                question = q,
                questionTitle = resolvedTitle,
                options = resolvedOptions,
                userGivenAns = givenAns,
                userOptionIndex = userIdx,
                correctOptionIndex = correctIdx,
                userBengaliLetter = userBengali,
                correctBengaliLetter = correctBengali,
                correctOptionText = cleanedCorrectOptionText,
                isAnswered = isAnswered,
                isCorrect = isCorrect,
                explanationText = explanation,
                solutionImageUrl = q.solution_img?.takeIf { it.isNotBlank() } ?: origQ?.solution_img?.takeIf { it.isNotBlank() },
                chapterName = q.chapter?.name?.takeIf { it.isNotBlank() } ?: origQ?.chapter?.name?.takeIf { it.isNotBlank() },
                sourceTag = q.source?.takeIf { it.isNotBlank() } ?: origQ?.source?.takeIf { it.isNotBlank() }
            )
        }
    }

    val totalCount = evaluatedQuestions.size
    val correctCount = evaluatedQuestions.count { it.isCorrect }
    val incorrectCount = evaluatedQuestions.count { it.isAnswered && !it.isCorrect }
    val unansweredCount = evaluatedQuestions.count { !it.isAnswered }

    val filteredQuestions = remember(evaluatedQuestions, uiState.feedbackFilter) {
        when (uiState.feedbackFilter) {
            FeedbackFilter.ALL -> evaluatedQuestions
            FeedbackFilter.CORRECT -> evaluatedQuestions.filter { it.isCorrect }
            FeedbackFilter.INCORRECT -> evaluatedQuestions.filter { it.isAnswered && !it.isCorrect }
            FeedbackFilter.UNANSWERED -> evaluatedQuestions.filter { !it.isAnswered }
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

                uiState.feedbackError != null && evaluatedQuestions.isEmpty() -> {
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
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadQuizFeedback(sessionId) }) {
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top Summary Card (Counters + Filter Chips + Quick Jump Strip)
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "সারসংক্ষেপ (${toBengaliDigits(totalCount)}টি প্রশ্ন)",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (totalCount > 0) {
                                            val accuracyPct = ((correctCount.toFloat() / totalCount.toFloat()) * 100).toInt()
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF0072EC).copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = "সঠিকতা: ${toBengaliDigits(accuracyPct)}%",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0072EC),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

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

                                    // Quick Jump Number Strip for filtered questions
                                    if (filteredQuestions.size > 1) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            filteredQuestions.forEachIndexed { listIdx, item ->
                                                val (chipBg, chipBorder, chipText) = when {
                                                    item.isCorrect -> Triple(
                                                        Color(0xFFDCFCE7),
                                                        Color(0xFF10B981),
                                                        Color(0xFF047857)
                                                    )
                                                    item.isAnswered -> Triple(
                                                        Color(0xFFFEE2E2),
                                                        Color(0xFFEF4444),
                                                        Color(0xFFB91C1C)
                                                    )
                                                    else -> Triple(
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = chipBg,
                                                    border = BorderStroke(1.dp, chipBorder),
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            coroutineScope.launch {
                                                                listState.animateScrollToItem(listIdx + 1)
                                                            }
                                                        }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = toBengaliDigits(item.originalNumber),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = chipText
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (filteredQuestions.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "এই ক্যাটাগরিতে কোনো প্রশ্ন নেই",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            // Question cards
                            itemsIndexed(
                                items = filteredQuestions,
                                key = { _, item -> item.question.id }
                            ) { _, evaluatedItem ->
                                val isBookmarked = uiState.bookmarkedQuestionIds.contains(evaluatedItem.question.id)

                                QuestionFeedbackCard(
                                    evaluated = evaluatedItem,
                                    isBookmarked = isBookmarked,
                                    onBookmarkToggle = { viewModel.toggleBookmark(evaluatedItem.question.id) }
                                )
                            }
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
    evaluated: EvaluatedFeedbackQuestion,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit
) {
    val statusColor = when {
        !evaluated.isAnswered -> Color(0xFF64748B)
        evaluated.isCorrect -> Color(0xFF059669)
        else -> Color(0xFFDC2626)
    }
    val statusBgColor = when {
        !evaluated.isAnswered -> Color(0xFFF1F5F9)
        evaluated.isCorrect -> Color(0xFFDCFCE7)
        else -> Color(0xFFFEE2E2)
    }
    val statusLabel = when {
        !evaluated.isAnswered -> "উত্তরহীন"
        evaluated.isCorrect -> "সঠিক উত্তর"
        else -> "ভুল উত্তর"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                evaluated.isCorrect -> Color(0xFF10B981).copy(alpha = 0.35f)
                evaluated.isAnswered -> Color(0xFFEF4444).copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        shadowElevation = 1.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Header: Question Number Badge + Status Badge + Bookmark Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0072EC).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "প্রশ্ন ${toBengaliDigits(evaluated.originalNumber)}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0072EC),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBgColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    !evaluated.isAnswered -> Icons.Default.HelpOutline
                                    evaluated.isCorrect -> Icons.Default.CheckCircle
                                    else -> Icons.Default.Cancel
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = statusLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    if (!evaluated.sourceTag.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = evaluated.sourceTag,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
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

            // Optional Chapter Subtitle
            if (!evaluated.chapterName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = evaluated.chapterName,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Prominent Question Statement Box (প্রশ্নটি স্পষ্টভাবে দেখানোর বক্স)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0072EC).copy(alpha = 0.14f),
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.QuestionMark,
                                    contentDescription = null,
                                    tint = Color(0xFF0072EC),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Text(
                            text = "প্রশ্ন:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0072EC)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (evaluated.questionTitle.isNotBlank()) {
                        QuizRichContent(
                            text = evaluated.questionTitle,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.5.sp,
                                lineHeight = 23.sp
                            ),
                            textColor = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxImageHeight = 230.dp
                        )
                    } else {
                        Text(
                            text = "প্রশ্ন নং ${toBengaliDigits(evaluated.originalNumber)} (নিচের অপশন ও সমাধান দেখুন)",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Answer Status Summary Banner (তোমার উত্তর বনাম সঠিক উত্তর)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusBgColor,
                border = BorderStroke(
                    1.dp,
                    when {
                        !evaluated.isAnswered -> Color(0xFFCBD5E1)
                        evaluated.isCorrect -> Color(0xFF10B981)
                        else -> Color(0xFFEF4444)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when {
                            !evaluated.isAnswered -> "তুমি কোনো উত্তর দেওনি • সঠিক উত্তর: (${evaluated.correctBengaliLetter})"
                            evaluated.isCorrect -> "তোমার উত্তর সঠিক: (${evaluated.userBengaliLetter})"
                            else -> "তোমার উত্তর: (${evaluated.userBengaliLetter}) • সঠিক উত্তর: (${evaluated.correctBengaliLetter})"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !evaluated.isAnswered -> Color(0xFF475569)
                            evaluated.isCorrect -> Color(0xFF047857)
                            else -> Color(0xFFB91C1C)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. All 4 MCQ Options
            evaluated.options.forEachIndexed { optIdx, opt ->
                val isUserSelection = evaluated.isAnswered && (
                    evaluated.userOptionIndex == optIdx ||
                        McqAnswerUtils.isAnswerCorrect(evaluated.userGivenAns, opt.no)
                    )
                val isCorrectOption = evaluated.correctOptionIndex == optIdx ||
                    McqAnswerUtils.isAnswerCorrect(evaluated.correctBengaliLetter, opt.no)

                FeedbackOptionRow(
                    optionIndex = optIdx,
                    option = opt,
                    isUserSelection = isUserSelection,
                    isCorrectOption = isCorrectOption
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 5. Solution / Explanation Section (ব্যাখ্যা ও সমাধান)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF59E0B).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
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

                    val fallbackExplanation = buildString {
                        append("সঠিক উত্তর: (${evaluated.correctBengaliLetter})")
                        if (!evaluated.correctOptionText.isNullOrBlank()) {
                            append(" — ")
                            append(evaluated.correctOptionText)
                        }
                    }

                    QuizRichContent(
                        text = evaluated.explanationText?.takeIf { it.isNotBlank() } ?: fallbackExplanation,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        ),
                        textColor = MaterialTheme.colorScheme.onSurface,
                        maxImageHeight = 210.dp
                    )

                    if (!evaluated.solutionImageUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        QuizImageCard(
                            imageUrl = evaluated.solutionImageUrl,
                            altText = "সমাধান চিত্র",
                            maxHeight = 220.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackOptionRow(
    optionIndex: Int,
    option: McqOptionItem,
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

    val displayLetter = McqAnswerUtils.toBengaliLetter(option.no).ifBlank {
        McqAnswerUtils.indexToBengaliLetter(optionIndex)
    }
    val cleanedOptionDescription = remember(option.description) {
        McqAnswerUtils.cleanOptionText(option.description)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (isCorrectOption || isUserSelection) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.16f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayLetter,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Option text or image (cleaned of duplicate leading A./B./C./D.)
            QuizRichContent(
                text = cleanedOptionDescription,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                textColor = if (isCorrectOption || isUserSelection) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCorrectOption || isUserSelection) FontWeight.SemiBold else FontWeight.Normal,
                maxImageHeight = 140.dp,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
