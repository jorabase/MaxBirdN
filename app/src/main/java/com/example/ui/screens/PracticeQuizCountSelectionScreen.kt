package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.quiz.PracticeQuizViewModel

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeQuizCountSelectionScreen(
    viewModel: PracticeQuizViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: (sessionId: String) -> Unit,
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

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "প্র্যাকটিস কুইজ",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ধাপ ২/৩ • প্রশ্নের সংখ্যা নির্বাচন",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.66f)
                                .background(subjectColor)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("practice_quiz_count_selection_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header
            Text(
                text = "প্রশ্নের সংখ্যা সিলেক্ট করো",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "প্রতিটি প্রশ্নের জন্য ১ মিনিট সময় বরাদ্দ থাকবে। নিজের সুবিধাজনক প্রশ্নের সংখ্যা বেছে নাও।",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Option 1: 10 Questions - 10 Minutes
            QuestionCountOptionCard(
                count = 10,
                durationMinutes = 10,
                title = "১০ টি — ১০ মি.",
                subtitle = "দ্রুত রিভিশন ও প্র্যাকটিসের জন্য সেরা অপশন",
                isSelected = uiState.selectedQuestionCount == 10,
                subjectColor = subjectColor,
                onClick = { viewModel.selectQuestionCount(10) }
            )

            // Option 2: 20 Questions - 20 Minutes
            QuestionCountOptionCard(
                count = 20,
                durationMinutes = 20,
                title = "২০ টি — ২০ মি.",
                subtitle = "গভীর প্রস্তুতি ও পূর্ণাঙ্গ আত্মমূল্যায়নের জন্য",
                isSelected = uiState.selectedQuestionCount == 20,
                subjectColor = subjectColor,
                onClick = { viewModel.selectQuestionCount(20) }
            )

            if (uiState.startQuizError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.startQuizError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Summary Action Button
            Button(
                onClick = { viewModel.openSummarySheet() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("view_summary_button")
            ) {
                Text(
                    text = "কুইজের সামারি দেখুন",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    // Modal Bottom Sheet: Summary Bottom Sheet
    if (uiState.showSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissSummarySheet() },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.testTag("quiz_summary_bottom_sheet")
        ) {
            QuizSummaryBottomSheetContent(
                uiState = uiState,
                subjectColor = subjectColor,
                onDismiss = { viewModel.dismissSummarySheet() },
                onStartQuiz = {
                    viewModel.startQuiz { sessionId ->
                        onNavigateToPlayer(sessionId)
                    }
                }
            )
        }
    }
}

@Composable
private fun QuestionCountOptionCard(
    count: Int,
    durationMinutes: Int,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    subjectColor: Color,
    onClick: () -> Unit
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "countScale"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) subjectColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("count_option_$count")
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio circle indicator
            Surface(
                shape = CircleShape,
                color = if (isSelected) subjectColor else Color.Transparent,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isSelected) subjectColor else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.size(24.dp).scale(checkScale)
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) subjectColor else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun QuizSummaryBottomSheetContent(
    uiState: com.example.quiz.PracticeQuizUiState,
    subjectColor: Color,
    onDismiss: () -> Unit,
    onStartQuiz: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sheet Header: Bulb icon + Title + Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "কুইজের সামারি",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Two Stat Cards side by side (Questions count, Time)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Questions Count Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = subjectColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, subjectColor.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        tint = subjectColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${toBengaliDigits(uiState.selectedQuestionCount)} টি প্রশ্ন",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "মোট প্রশ্ন",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Duration Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF10B981).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${toBengaliDigits(uiState.selectedQuestionCount)} মিনিট",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "বরাদ্দকৃত সময়",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Subject & Selected Chapters Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = subjectColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!uiState.subjectIcon.isNullOrBlank()) {
                                AsyncImage(
                                    model = uiState.subjectIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = subjectColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.subjectTitle.ifBlank { "নির্বাচিত বিষয়" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "অধ্যায়: ${toBengaliDigits(uiState.selectedChaptersCount)}টি সিলেক্টেড",
                            fontSize = 12.sp,
                            color = subjectColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List selected chapter names (truncated gracefully)
                val selectedChapterNames = uiState.chapters
                    .filter { uiState.selectedChapterIds.contains(it.id) }
                    .mapNotNull { it.name }

                if (selectedChapterNames.isNotEmpty()) {
                    Text(
                        text = selectedChapterNames.take(4).joinToString(", ") + if (selectedChapterNames.size > 4) " এবং আরও ${toBengaliDigits(selectedChapterNames.size - 4)}টি" else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Daily Practice Limit Notice if present
        if (uiState.practiceLimits?.has_limit == true) {
            val used = uiState.practiceLimits?.used_today ?: 0
            val limit = uiState.practiceLimits?.limit_per_day ?: 3
            Text(
                text = "আজকের প্র্যাকটিস সম্পন্ন: ${toBengaliDigits(used)}/${toBengaliDigits(limit)} বার",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Start Quiz Button
        Button(
            onClick = onStartQuiz,
            enabled = !uiState.isStartingQuiz,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = subjectColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_quiz_button")
        ) {
            if (uiState.isStartingQuiz) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "কুইজ প্রস্তুত হচ্ছে...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "কুইজ শুরু করো",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
