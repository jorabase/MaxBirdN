package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.auth.SessionManager
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

private fun formatSecondsToBengaliTime(seconds: Double?): String {
    if (seconds == null || seconds <= 0) return "০০:০০ মি."
    val totalSec = seconds.toLong()
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${toBengaliDigits(String.format("%02d", min))}:${toBengaliDigits(String.format("%02d", sec))} মি."
}

@Composable
fun PracticeQuizResultScreen(
    sessionId: String,
    sessionManager: SessionManager,
    viewModel: PracticeQuizViewModel,
    onBackToChapters: () -> Unit,
    onNavigateToFeedback: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        if (uiState.resultSummary == null || uiState.sessionId != sessionId) {
            viewModel.loadQuizResult(sessionId)
        }
    }

    BackHandler {
        onBackToChapters()
    }

    val result = uiState.resultSummary
    val userName = remember { sessionManager.getUserFullName() ?: "শিক্ষার্থী" }
    val userClass = remember { sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "একাদশ শ্রেণি" }
    val userAvatar = remember { sessionManager.getUserAvatar() }

    // Dark Navy theme colors
    val darkNavyBg = Color(0xFF0B132B)
    val cardNavyBg = Color(0xFF1C2541)
    val accentNavy = Color(0xFF3A506B)
    val goldBadge = Color(0xFFF59E0B)

    Scaffold(
        topBar = {
            Surface(
                color = darkNavyBg,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToChapters,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("result_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "কুইজের ফলাফল",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = darkNavyBg,
        modifier = modifier.testTag("practice_quiz_result_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isResultLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ফলাফল প্রস্তুত হচ্ছে...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                uiState.resultError != null && result == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.resultError ?: "ফলাফল পাওয়া যায়নি",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadQuizResult(sessionId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                result != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // User Profile Info Strip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardNavyBg.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (!userAvatar.isNullOrBlank()) {
                                            AsyncImage(
                                                model = userAvatar,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = userClass,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF38BDF8).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "প্র্যাকটিস কুইজ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }
                            }
                        }

                        // Main Illustrated Badge Card (Centered with glowing gradient)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = cardNavyBg,
                            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8)))),
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Badge Image (Coil AsyncImage or fallback trophy)
                                val badgeImageUrl = result.fullBadgeImageUrl
                                if (!badgeImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = badgeImageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = goldBadge.copy(alpha = 0.15f),
                                        border = BorderStroke(2.dp, goldBadge),
                                        modifier = Modifier.size(90.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.EmojiEvents,
                                                contentDescription = null,
                                                tint = goldBadge,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Badge Title (e.g. "ঘষামাজা যোদ্ধা")
                                Text(
                                    text = result.badge ?: "কুইজ সম্পন্ন!",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Large Score Display (e.g. "৩/১০")
                                val totalCorrect = result.correctCount
                                val totalQuestions = if (result.questionsCount > 0) result.questionsCount else uiState.totalQuestionsCount
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "স্কোর: ${toBengaliDigits(totalCorrect)}/${toBengaliDigits(totalQuestions)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // 3-Column Stats Grid (সঠিক / ভুল / সময় লেগেছে)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardNavyBg,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Correct Answers
                                StatColumnItem(
                                    icon = Icons.Default.CheckCircle,
                                    iconTint = Color(0xFF10B981),
                                    title = "সঠিক",
                                    value = "${toBengaliDigits(result.correctCount)}টি"
                                )

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )

                                // Incorrect Answers
                                StatColumnItem(
                                    icon = Icons.Default.Cancel,
                                    iconTint = Color(0xFFEF4444),
                                    title = "ভুল",
                                    value = "${toBengaliDigits(result.incorrectCount)}টি"
                                )

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )

                                // Time Spent
                                StatColumnItem(
                                    icon = Icons.Default.AccessTime,
                                    iconTint = Color(0xFF38BDF8),
                                    title = "সময় লেগেছে",
                                    value = formatSecondsToBengaliTime(result.spentTimeSeconds.toDouble())
                                )
                            }
                        }

                        // Subject Proficiency Breakdown Card
                        val subjectDetail = result.subject_results?.firstOrNull()
                        if (subjectDetail != null) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = cardNavyBg,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = subjectDetail.title_bn ?: subjectDetail.title ?: uiState.subjectTitle,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )

                                        // Proficiency Level Pill (e.g. "কাঁচা")
                                        val proficiency = subjectDetail.proficiency ?: "অগ্রসরমান"
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "দক্ষতা: $proficiency",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF59E0B),
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    val correct = subjectDetail.correctCount
                                    val total = (if (subjectDetail.questionsCount > 0) subjectDetail.questionsCount else 1).coerceAtLeast(1)
                                    val pct = (correct.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.White.copy(alpha = 0.15f))
                                    ) {
                                        if (pct > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(pct.coerceAtLeast(0.04f))
                                                    .clip(RoundedCornerShape(50))
                                                    .background(Color(0xFF10B981))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "সঠিকতা: ${toBengaliDigits((pct * 100).toInt())}%",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom Action Buttons
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // View Solution Button (Primary)
                            Button(
                                onClick = { onNavigateToFeedback(sessionId) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0072EC)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("view_solution_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "সলিউশন দেখা",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Try Again Button (Outlined)
                            OutlinedButton(
                                onClick = {
                                    viewModel.resetQuizFlow()
                                    onBackToChapters()
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("try_again_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "আবার ট্রাই করো",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumnItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = title,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.65f)
        )
    }
}
