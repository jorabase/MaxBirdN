package com.example.modeltest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.modeltest.data.FeedbackQuestionItem
import com.example.modeltest.ui.FeedbackFilter
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.components.ModelTestDetailSkeleton
import com.example.modeltest.ui.components.RetryErrorView
import com.example.utils.toBengaliDigits

/**
 * Analytics Feedback Screen (analytics.shikho.com):
 * - Summary: Total, Correct (Green), Wrong (Red), Unattempted
 * - Filter Dropdown: "সব উত্তর", "সঠিক উত্তর", "ভুল উত্তর", "অনুত্তরিত প্রশ্ন"
 * - Solution explanations for each question
 * - Error state & Retry fallback if analytics API fails
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCQFeedbackScreen(
    sessionId: String,
    viewModel: ModelTestViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var filterMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadFeedback(sessionId)
    }

    val feedback = uiState.feedbackDetails
    val filter = uiState.feedbackFilter

    val allQuestions = feedback?.questions ?: emptyList()
    val filteredQuestions = remember(allQuestions, filter) {
        when (filter) {
            FeedbackFilter.ALL -> allQuestions
            FeedbackFilter.CORRECT -> allQuestions.filter { it.is_correct == true }
            FeedbackFilter.WRONG -> allQuestions.filter { it.is_correct == false && it.user_selected_option != null }
            FeedbackFilter.UNATTEMPTED -> allQuestions.filter { it.user_selected_option == null }
        }
    }

    val filterLabel = when (filter) {
        FeedbackFilter.ALL -> "সব উত্তর"
        FeedbackFilter.CORRECT -> "সঠিক উত্তর"
        FeedbackFilter.WRONG -> "ভুল উত্তর"
        FeedbackFilter.UNATTEMPTED -> "অনুত্তরিত প্রশ্ন"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "টেস্ট ফিডব্যাক ও সমাধান",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
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
        modifier = modifier
    ) { innerPadding ->
        when {
            uiState.isFeedbackLoading && feedback == null -> {
                Box(modifier = Modifier.padding(innerPadding)) {
                    ModelTestDetailSkeleton()
                }
            }
            uiState.feedbackError != null && feedback == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    RetryErrorView(
                        message = uiState.feedbackError ?: "ফিডব্যাক লোড করা যায়নি",
                        onRetry = { viewModel.loadFeedback(sessionId) }
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Summary Metric Row
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ফলাফল সারসংক্ষেপ",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricPill(
                                        label = "সঠিক",
                                        value = toBengaliDigits((feedback?.correct_count ?: 0).toString()),
                                        color = Color(0xFF10B981)
                                    )
                                    MetricPill(
                                        label = "ভুল",
                                        value = toBengaliDigits((feedback?.wrong_count ?: 0).toString()),
                                        color = Color(0xFFEF4444)
                                    )
                                    MetricPill(
                                        label = "অনুত্তরিত",
                                        value = toBengaliDigits((feedback?.unattempted_count ?: 0).toString()),
                                        color = Color(0xFF64748B)
                                    )
                                    MetricPill(
                                        label = "মোট",
                                        value = toBengaliDigits((feedback?.total_questions ?: allQuestions.size).toString()),
                                        color = Color(0xFF3B82F6)
                                    )
                                }
                            }
                        }
                    }

                    // Filter Dropdown Bar
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "প্রশ্ন তালিকা (${toBengaliDigits(filteredQuestions.size.toString())})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { filterMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(text = filterLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }

                                DropdownMenu(
                                    expanded = filterMenuExpanded,
                                    onDismissRequest = { filterMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("সব উত্তর") },
                                        onClick = {
                                            viewModel.setFeedbackFilter(FeedbackFilter.ALL)
                                            filterMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("সঠিক উত্তর") },
                                        onClick = {
                                            viewModel.setFeedbackFilter(FeedbackFilter.CORRECT)
                                            filterMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("ভুল উত্তর") },
                                        onClick = {
                                            viewModel.setFeedbackFilter(FeedbackFilter.WRONG)
                                            filterMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("অনুত্তরিত প্রশ্ন") },
                                        onClick = {
                                            viewModel.setFeedbackFilter(FeedbackFilter.UNATTEMPTED)
                                            filterMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Question Solutions List
                    itemsIndexed(
                        items = filteredQuestions,
                        key = { idx, item -> item.id.ifBlank { "$idx" } }
                    ) { index, item ->
                        FeedbackQuestionCard(
                            index = index + 1,
                            item = item
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedbackQuestionCard(
    index: Int,
    item: FeedbackQuestionItem
) {
    val bengaliLetters = listOf("ক", "খ", "গ", "ঘ")

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "প্রশ্ন ${toBengaliDigits(index.toString())}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                val (badgeText, badgeBg, badgeColor) = when {
                    item.is_correct == true -> Triple("সঠিক", Color(0xFF10B981).copy(alpha = 0.12f), Color(0xFF059669))
                    item.user_selected_option != null -> Triple("ভুল", Color(0xFFEF4444).copy(alpha = 0.12f), Color(0xFFDC2626))
                    else -> Triple("অনুত্তরিত", Color(0xFF64748B).copy(alpha = 0.12f), Color(0xFF475569))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.question_text ?: "",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 21.sp
            )

            if (!item.question_image.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = item.question_image,
                    contentDescription = "Question Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Options
            val options = item.options ?: emptyList()
            options.forEachIndexed { optIdx, opt ->
                val optIndex = opt.index ?: optIdx
                val isCorrectAnswer = optIndex == item.correct_option
                val isUserSelection = optIndex == item.user_selected_option

                val optBg = when {
                    isCorrectAnswer -> Color(0xFF10B981).copy(alpha = 0.12f)
                    isUserSelection && !isCorrectAnswer -> Color(0xFFEF4444).copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
                val optBorder = when {
                    isCorrectAnswer -> Color(0xFF10B981)
                    isUserSelection && !isCorrectAnswer -> Color(0xFFEF4444)
                    else -> Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(optBg)
                        .border(1.dp, optBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "(${bengaliLetters.getOrElse(optIdx) { "${optIdx + 1}" }})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = opt.text ?: "",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (isCorrectAnswer) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    } else if (isUserSelection) {
                        Icon(Icons.Default.Cancel, contentDescription = "Wrong", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Solution & Explanation Section
            if (!item.solution.isNullOrBlank() || !item.solution_image.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "ব্যাখ্যা ও সমাধান:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        if (!item.solution.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = item.solution, fontSize = 13.sp, color = Color(0xFF334155), lineHeight = 18.sp)
                        }
                        if (!item.solution_image.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = item.solution_image,
                                contentDescription = "Solution",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
