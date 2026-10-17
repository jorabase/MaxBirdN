package com.example.liveclass.poll

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats seconds cleanly:
 * e.g. 0.790 -> "0.79", 0.800 -> "0.8", 1.141 -> "1.14"
 */
private fun formatDurationSeconds(seconds: Double): String {
    return try {
        BigDecimal.valueOf(seconds)
            .setScale(2, RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
    } catch (_: Exception) {
        String.format(java.util.Locale.US, "%.2f", seconds)
    }
}

/**
 * Dialog displaying active/ended Poll Question where students can vote and review answers.
 * Responsive across both Portrait and Landscape mobile screens with zero layout overflow.
 */
@Composable
fun LivePollQuestionDialog(
    poll: LivePollItem,
    teacherName: String,
    onDismiss: () -> Unit,
    onSubmitAnswer: (pollId: String, questionIndex: Int, optionIndex: Int, durationMs: Long) -> Unit,
    onViewResults: (LivePollItem) -> Unit
) {
    val question = poll.questions.firstOrNull()

    var selectedOptionIndex by remember(poll.pollId, poll.userResponse) {
        mutableStateOf(poll.userResponse?.selectedOptionIndex)
    }

    val startTimeMs = remember(poll.pollId) { System.currentTimeMillis() }
    val hasSubmitted = poll.userResponse != null && poll.userResponse.selectedOptionIndex > 0

    // Live elapsed timer when poll is active and not yet answered
    var elapsedSeconds by remember(poll.pollId) { mutableIntStateOf(0) }
    LaunchedEffect(poll.pollId, poll.isLive, hasSubmitted) {
        if (poll.isLive && !hasSubmitted) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(0)
                delay(500L)
            }
        }
    }

    val correctIndex = question?.correctOptionIndex
    val isCorrect = when {
        poll.userResponse != null && correctIndex != null -> poll.userResponse.selectedOptionIndex == correctIndex
        poll.userResponse != null -> poll.userResponse.isCorrect
        correctIndex != null && selectedOptionIndex != null -> selectedOptionIndex == correctIndex
        else -> false
    }

    val cardBorderColor = when {
        poll.isEnded && hasSubmitted && isCorrect -> Color(0xFF10B981)
        poll.isEnded && hasSubmitted && !isCorrect -> Color(0xFFEF4444)
        poll.isEnded && !hasSubmitted -> Color(0xFFEF4444)
        hasSubmitted -> Color(0xFF38BDF8)
        else -> Color(0xFF334155)
    }

    val resolvedTeacher = poll.teacherName.ifBlank { teacherName }
    val quizOrPollLabel = if (poll.type.equals("quiz", ignoreCase = true)) "Quiz" else "Poll"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111827)),
            color = Color(0xFF111827)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 1. Top Bar: < [Title] [LIVE / ENDED] [Timer]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F2937))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "${poll.title} $quizOrPollLabel",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (poll.isLive && !hasSubmitted) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1F2937),
                            border = BorderStroke(1.dp, Color(0xFF374151)),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${elapsedSeconds}s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (poll.isEnded) Color(0xFF374151) else Color(0xFFDC2626)
                    ) {
                        Text(
                            text = if (poll.isEnded) "ENDED" else "LIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable body so options & buttons never clip on small or landscape mobile screens
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Teacher Announcement Line
                    if (resolvedTeacher.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1F2937),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$resolvedTeacher started a $quizOrPollLabel",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFD1D5DB),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (question == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "পোলের প্রশ্ন লোড হচ্ছে...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                        }
                    } else {
                        // 2. Question Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.2.dp, cardBorderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                val headerAccentColor = when {
                                    poll.isEnded && isCorrect -> Color(0xFF10B981)
                                    poll.isEnded && !isCorrect -> Color(0xFFEF4444)
                                    hasSubmitted -> Color(0xFF38BDF8)
                                    else -> Color(0xFF94A3B8)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (poll.isEnded) {
                                        Icon(
                                            imageVector = if (isCorrect) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                            contentDescription = null,
                                            tint = headerAccentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Text(
                                        text = "QUESTION ${question.index} OF ${poll.questionsCount.coerceAtLeast(1)} : ${question.type.uppercase().replace("-", " ")}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.6.sp,
                                        color = headerAccentColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Question Text
                                Text(
                                    text = question.text,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    lineHeight = 22.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Options List
                                val totalOptionVotes = remember(question.options, question.totalVotes) {
                                    val sum = question.options.sumOf { it.votesCount }
                                    if (question.totalVotes > sum) question.totalVotes else sum
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    question.options.forEach { option ->
                                        val isUserSelected =
                                            (poll.userResponse?.selectedOptionIndex ?: selectedOptionIndex) == option.index
                                        val isOptionCorrect = correctIndex != null && correctIndex == option.index

                                        val optionBgColor = when {
                                            poll.isEnded && isOptionCorrect -> Color(0xFF10B981).copy(alpha = 0.16f)
                                            poll.isEnded && isUserSelected && !isOptionCorrect -> Color(0xFFEF4444).copy(alpha = 0.16f)
                                            isUserSelected -> Color(0xFF0284C7).copy(alpha = 0.22f)
                                            else -> Color(0xFF0F172A).copy(alpha = 0.65f)
                                        }

                                        val optionBorderColor = when {
                                            poll.isEnded && isOptionCorrect -> Color(0xFF10B981)
                                            poll.isEnded && isUserSelected && !isOptionCorrect -> Color(0xFFEF4444)
                                            isUserSelected -> Color(0xFF38BDF8)
                                            else -> Color(0xFF334155)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = optionBgColor,
                                            border = BorderStroke(1.dp, optionBorderColor),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable(enabled = !poll.isEnded && !hasSubmitted) {
                                                    selectedOptionIndex = option.index
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (poll.isEnded) {
                                                    if (isOptionCorrect) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.CheckCircle,
                                                            contentDescription = "Correct Option",
                                                            tint = Color(0xFF10B981),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                    } else if (isUserSelected) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Cancel,
                                                            contentDescription = "Wrong Option",
                                                            tint = Color(0xFFEF4444),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(CircleShape)
                                                                .border(1.2.dp, Color(0xFF475569), CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                    }
                                                } else {
                                                    if (isUserSelected) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.CheckCircle,
                                                            contentDescription = "Selected",
                                                            tint = Color(0xFF38BDF8),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(20.dp)
                                                                .clip(CircleShape)
                                                                .border(1.5.dp, Color(0xFF94A3B8), CircleShape)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }

                                                Text(
                                                    text = option.text,
                                                    fontSize = 15.sp,
                                                    fontWeight = if (isUserSelected || isOptionCorrect) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = Color(0xFFF1F5F9),
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (isUserSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF334155)
                                                    ) {
                                                        Text(
                                                            text = "Your Answer",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFFE2E8F0),
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                if (poll.isEnded && totalOptionVotes > 0 && option.votesCount > 0) {
                                                    val pct = ((option.votesCount * 100.0) / totalOptionVotes).toInt()
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "$pct%",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Submitted confirmation banner when poll is still live
                        if (hasSubmitted && !poll.isEnded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF065F46).copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "আপনার উত্তর সফলভাবে জমা হয়েছে! পোল শেষ হলে চূড়ান্ত ফলাফল ও লিডারবোর্ড দেখতে পাবেন।",
                                        fontSize = 12.5.sp,
                                        color = Color(0xFFD1FAE5),
                                        lineHeight = 17.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Bottom Action Bar (Always visible, never clipped)
                if (question != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!poll.isEnded && !hasSubmitted) {
                            Button(
                                onClick = {
                                    val opt = selectedOptionIndex
                                    if (opt != null) {
                                        val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(100L)
                                        onSubmitAnswer(poll.pollId, question.index, opt, duration)
                                    }
                                },
                                enabled = selectedOptionIndex != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0284C7),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFF334155),
                                    disabledContentColor = Color(0xFF94A3B8)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = "Answer",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { onViewResults(poll) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0284C7),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "View Results",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog displaying Participation Summary & Leaderboard with zero dummy data.
 */
@Composable
fun LivePollResultDialog(
    poll: LivePollItem,
    currentPeerId: String = "",
    currentUserId: String = "",
    currentUserName: String = "",
    onDismiss: () -> Unit
) {
    val leaderboard = poll.leaderboard
    val totalQuestions = poll.questionsCount.coerceAtLeast(1)
    val maxScore = (poll.questions.firstOrNull()?.weight ?: 10) * totalQuestions

    // Locate the current student's entry in the real 100ms leaderboard
    val myLeaderboardEntry = remember(leaderboard, currentPeerId, currentUserId, currentUserName) {
        leaderboard.firstOrNull { entry ->
            (currentUserId.isNotBlank() && entry.userId.equals(currentUserId, ignoreCase = true)) ||
                (currentPeerId.isNotBlank() && entry.peerId.equals(currentPeerId, ignoreCase = true)) ||
                (currentUserName.isNotBlank() && entry.userName.equals(currentUserName.trim(), ignoreCase = true))
        }
    }

    val userResp = poll.userResponse
    val question = poll.questions.firstOrNull()
    val isCorrect = when {
        myLeaderboardEntry != null -> myLeaderboardEntry.correctResponses > 0
        userResp != null && question?.correctOptionIndex != null -> userResp.selectedOptionIndex == question.correctOptionIndex
        userResp != null -> userResp.isCorrect
        else -> false
    }

    val userRankText = when {
        myLeaderboardEntry != null && myLeaderboardEntry.position > 0 -> "#${myLeaderboardEntry.position}"
        else -> "-"
    }

    val userPointsText = when {
        myLeaderboardEntry != null -> "${myLeaderboardEntry.score}/$maxScore"
        userResp != null -> "${if (isCorrect) maxScore else 0}/$maxScore"
        else -> "0/$maxScore"
    }

    val timeTakenText = when {
        myLeaderboardEntry != null && myLeaderboardEntry.durationSeconds > 0 -> {
            "${formatDurationSeconds(myLeaderboardEntry.durationSeconds)}s"
        }
        userResp != null && userResp.durationMs > 0 -> {
            "${formatDurationSeconds(userResp.durationMs / 1000.0)}s"
        }
        else -> "-"
    }

    val correctCount = myLeaderboardEntry?.correctResponses ?: if (isCorrect) 1 else 0
    val correctAnswersText = "$correctCount/$totalQuestions"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top Bar: < [Title] [Ended / Live]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = poll.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (poll.isEnded) Color(0xFF1E293B) else Color(0xFFDC2626),
                        border = BorderStroke(1.dp, if (poll.isEnded) Color(0xFF334155) else Color(0xFFEF4444))
                    ) {
                        Text(
                            text = if (poll.isEnded) "Ended" else "Live",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Participation Summary Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Participation Summary",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (currentUserName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "👤 $currentUserName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(max = 150.dp)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2x2 Grid of Summary Stat Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PollSummaryCard(
                        title = "YOUR RANK",
                        value = userRankText,
                        accentColor = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1f)
                    )
                    PollSummaryCard(
                        title = "POINTS",
                        value = userPointsText,
                        accentColor = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PollSummaryCard(
                        title = "TIME TAKEN",
                        value = timeTakenText,
                        accentColor = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f)
                    )
                    PollSummaryCard(
                        title = "CORRECT ANSWERS",
                        value = correctAnswersText,
                        accentColor = Color(0xFF34D399),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Leaderboard Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Leaderboard",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (leaderboard.isNotEmpty()) {
                        Text(
                            text = "মোট ${leaderboard.size} জন",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Leaderboard List Container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    if (leaderboard.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Leaderboard,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "এখনো কোনো ফলাফল পাওয়া যায়নি",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 6.dp)
                        ) {
                            items(leaderboard, key = { "${it.position}_${it.peerId}_${it.userId}" }) { entry ->
                                val isMine = (currentUserId.isNotBlank() && entry.userId.equals(currentUserId, ignoreCase = true)) ||
                                    (currentPeerId.isNotBlank() && entry.peerId.equals(currentPeerId, ignoreCase = true)) ||
                                    (currentUserName.isNotBlank() && entry.userName.equals(currentUserName.trim(), ignoreCase = true))

                                LeaderboardRowItem(
                                    entry = entry,
                                    isCurrentUser = isMine,
                                    displayUserNameOverride = if (isMine && currentUserName.isNotBlank()) currentUserName else null
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
private fun PollSummaryCard(
    title: String,
    value: String,
    accentColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LeaderboardRowItem(
    entry: LivePollLeaderboardEntry,
    isCurrentUser: Boolean = false,
    displayUserNameOverride: String? = null
) {
    val resolvedName = (displayUserNameOverride ?: entry.userName).trim()
        .ifBlank { "শিক্ষার্থী #${entry.position}" }

    val circleBg = when (entry.position) {
        1 -> Color(0xFFD99000) // Gold circle for 1st
        2 -> Color(0xFF64748B) // Silver circle for 2nd
        3 -> Color(0xFF92400E) // Bronze circle for 3rd
        else -> if (isCurrentUser) Color(0xFF0284C7) else Color(0xFF334155)
    }

    Surface(
        color = if (isCurrentUser) Color(0xFF0284C7).copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = if (isCurrentUser) BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position Circle
            Surface(
                shape = CircleShape,
                color = circleBg,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.position.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Left column: Name and points
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = resolvedName,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrentUser) Color(0xFF38BDF8) else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentUser) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0284C7)
                        ) {
                            Text(
                                text = "আপনি",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${entry.score}/${entry.totalScore} points",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right side: Trophy (for #1) + Check icon + "1/1" + Clock icon + seconds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (entry.position == 1) {
                    Text(
                        text = "🏆",
                        fontSize = 13.sp
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = "${entry.correctResponses}/${entry.totalResponses}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.width(2.dp))

                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = "${formatDurationSeconds(entry.durationSeconds)}s",
                    fontSize = 12.5.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

/**
 * Dialog displaying list of Previous Polls/Quizzes with zero dummy data.
 */
@Composable
fun PreviousPollsDialog(
    polls: List<LivePollItem>,
    onDismiss: () -> Unit,
    onSelectPoll: (LivePollItem) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar: < Poll/Quiz
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "পোল ও কুইজ (Poll/Quiz)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    if (polls.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = "${polls.size}টি",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = Color(0xFF1E293B),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "সকল পোল ও কুইজ তালিকা",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (polls.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Quiz,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "এখনো কোনো পোল বা কুইজ শুরু হয়নি",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(polls, key = { it.pollId }) { pollItem ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPoll(pollItem) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(
                                        1.dp,
                                        if (pollItem.isLive) Color(0xFFEF4444) else Color(0xFF334155)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = pollItem.title,
                                                fontSize = 16.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (pollItem.isEnded) Color(0xFF334155) else Color(0xFFDC2626)
                                            ) {
                                                Text(
                                                    text = if (pollItem.isEnded) "ENDED" else "LIVE",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        val qText = pollItem.questions.firstOrNull()?.text.orEmpty()
                                        if (qText.isNotBlank() && qText != pollItem.title) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = qText,
                                                fontSize = 13.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            val hasVoted = pollItem.userResponse != null && pollItem.userResponse.selectedOptionIndex > 0
                                            Text(
                                                text = if (hasVoted) "✓ আপনি উত্তর দিয়েছেন" else "উত্তর দেওয়া হয়নি",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (hasVoted) Color(0xFF34D399) else Color(0xFF94A3B8)
                                            )

                                            Button(
                                                onClick = { onSelectPoll(pollItem) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (pollItem.isLive) Color(0xFFDC2626) else Color(0xFF0284C7),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text(
                                                    text = if (pollItem.isLive && !hasVoted) "উত্তর দিন" else "View",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
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
        }
    }
}

/**
 * Floating notification pill when teacher launches a live poll during video playback.
 */
@Composable
fun LivePollFloatingNotification(
    activePoll: LivePollItem,
    onOpenPoll: (LivePollItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.95f),
        border = BorderStroke(1.2.dp, Color(0xFF38BDF8)),
        shadowElevation = 8.dp,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenPoll(activePoll) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            )

            Text(
                text = "লাইভ পোল: ${activePoll.title}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0284C7)
            ) {
                Text(
                    text = "উত্তর দিন",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
