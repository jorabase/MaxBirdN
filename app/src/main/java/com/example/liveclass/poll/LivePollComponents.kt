package com.example.liveclass.poll

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formats seconds exactly like the original app screenshots:
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
 * Matches Screenshots 2, 4, and 5 (1001468505.jpg, 1001468507.jpg, 1001468508.jpg) 100%.
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

    val correctIndex = question?.correctOptionIndex
    val isCorrect = when {
        poll.userResponse != null && correctIndex != null -> poll.userResponse.selectedOptionIndex == correctIndex
        poll.userResponse != null -> poll.userResponse.isCorrect
        correctIndex != null && selectedOptionIndex != null -> selectedOptionIndex == correctIndex
        else -> false
    }

    // Exact border color from screenshots: Green (#22C55E / #10B981) for correct, Red (#EF4444) for wrong
    val cardBorderColor = when {
        poll.isEnded && hasSubmitted && isCorrect -> Color(0xFF10B981)
        poll.isEnded && hasSubmitted && !isCorrect -> Color(0xFFEF4444)
        poll.isEnded && !hasSubmitted -> Color(0xFFEF4444)
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
                .background(Color(0xFF202124)),
            color = Color(0xFF202124)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 1. Top Bar: < [Title] Quiz [ENDED / LIVE]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE2E8F0)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${poll.title} $quizOrPollLabel",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (poll.isEnded) Color(0xFF3C4353) else Color(0xFFDC2626)
                    ) {
                        Text(
                            text = if (poll.isEnded) "ENDED" else "LIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Teacher Announcement Line (e.g. "Md. Sayedur Rahman started a Quiz")
                if (resolvedTeacher.isNotBlank()) {
                    Text(
                        text = "$resolvedTeacher started a $quizOrPollLabel",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFD1D5DB)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (question == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF2563EB),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    // 2. Question Card (Exact dark gray #292A2D with colored border)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF292A2D)),
                        border = BorderStroke(1.dp, cardBorderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            // Header row inside card: Outlined Check/Cross icon + QUESTION 1 OF 1 : SINGLE CHOICE
                            val headerAccentColor = when {
                                poll.isEnded && isCorrect -> Color(0xFF10B981)
                                poll.isEnded && !isCorrect -> Color(0xFFEF4444)
                                else -> Color(0xFF9CA3AF)
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
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.8.sp,
                                    color = headerAccentColor
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Question Text (e.g. "5?" or "3?")
                            Text(
                                text = question.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Options List (A, B, C, D) matching exact screenshot behavior
                            question.options.forEach { option ->
                                val isUserSelected = (poll.userResponse?.selectedOptionIndex ?: selectedOptionIndex) == option.index
                                val isOptionCorrect = correctIndex != null && correctIndex == option.index

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(enabled = !poll.isEnded && !hasSubmitted) {
                                            selectedOptionIndex = option.index
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (poll.isEnded) {
                                        // In ended state (Screenshots 2, 4, 5):
                                        // ONLY the correct option displays the white outlined check circle on the left.
                                        // Non-correct options (even if selected by the user) have NO circle icon on the left.
                                        if (isOptionCorrect) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = "Correct Option",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                    } else {
                                        // In live voting state: show radio button circle
                                        if (isUserSelected) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, Color(0xFF9CA3AF), CircleShape)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Text(
                                        text = option.text,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFFE5E7EB)
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    if (isUserSelected) {
                                        Text(
                                            text = "Your Answer",
                                            fontSize = 14.sp,
                                            color = Color(0xFF9CA3AF),
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Action Buttons aligned right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
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
                                    containerColor = Color(0xFF1967D2),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = "Answer",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (poll.isEnded) {
                            Button(
                                onClick = { onViewResults(poll) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1967D2),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = "View Results",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
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
 * Dialog displaying Participation Summary & Leaderboard.
 * Matches Screenshot 1 (1001468504.jpg) 100% with zero dummy data.
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
                    (currentUserName.isNotBlank() && entry.userName.startsWith(currentUserName.trim(), ignoreCase = true))
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
        myLeaderboardEntry != null && myLeaderboardEntry.position > 0 -> myLeaderboardEntry.position.toString()
        else -> "-"
    }

    val userPointsText = when {
        myLeaderboardEntry != null -> myLeaderboardEntry.score.toString()
        userResp != null -> (if (isCorrect) maxScore else 0).toString()
        else -> "0"
    }

    val timeTakenText = when {
        myLeaderboardEntry != null && myLeaderboardEntry.durationSeconds > 0 -> {
            "${formatDurationSeconds(myLeaderboardEntry.durationSeconds)} seconds"
        }
        userResp != null && userResp.durationMs > 0 -> {
            "${formatDurationSeconds(userResp.durationMs / 1000.0)} seconds"
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
                .background(Color(0xFF101318)),
            color = Color(0xFF101318)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top Bar: < [Title] [Ended]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE2E8F0)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = poll.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (poll.isEnded) Color(0xFF1E242E) else Color(0xFFDC2626)
                    ) {
                        Text(
                            text = if (poll.isEnded) "Ended" else "Live",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFD1D5DB),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Participation Summary Header
                Text(
                    text = "Participation Summary",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2x2 Grid of Summary Stat Cards matching Screenshot 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PollSummaryCard(
                        title = "YOUR RANK",
                        value = userRankText,
                        modifier = Modifier.weight(1f)
                    )
                    PollSummaryCard(
                        title = "POINTS",
                        value = userPointsText,
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
                        modifier = Modifier.weight(1f)
                    )
                    PollSummaryCard(
                        title = "CORRECT\nANSWERS",
                        value = correctAnswersText,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Leaderboard Section Header
                Text(
                    text = "Leaderboard",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Leaderboard List Container matching Screenshot 1
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181C22))
                ) {
                    if (leaderboard.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "লিডারবোর্ড ডেটা লোড হচ্ছে...",
                                fontSize = 13.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp)
                        ) {
                            items(leaderboard, key = { "${it.position}_${it.peerId}_${it.userId}" }) { entry ->
                                LeaderboardRowItem(entry = entry)
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181C22))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9CA3AF)
            )

            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun LeaderboardRowItem(
    entry: LivePollLeaderboardEntry
) {
    // Truncate long names in uppercase style like Screenshot 1 ("NUHA...", "ROKSANA...", "SADIA TA...")
    val formattedName = remember(entry.userName) {
        val raw = entry.userName.trim()
        if (raw.length > 9) {
            raw.take(8).uppercase() + "..."
        } else {
            raw.uppercase()
        }
    }

    val circleBg = when (entry.position) {
        1 -> Color(0xFFD99000) // Gold circle for 1st
        2 -> Color(0xFF4B525A) // Silver/Gray circle for 2nd
        3 -> Color(0xFF684314) // Bronze circle for 3rd
        else -> Color(0xFF3E4656) // Slate circle for 4th+
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Left column: Name and "10/10\npoints"
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = formattedName,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${entry.score}/${entry.totalScore}\npoints",
                fontSize = 12.5.sp,
                lineHeight = 15.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        // Right side: Trophy (for #1) + Check icon + "1/1" + Clock icon + "0.79 seconds"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (entry.position == 1) {
                Text(
                    text = "🏆",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(14.dp)
            )

            Text(
                text = "${entry.correctResponses}/${entry.totalResponses}",
                fontSize = 13.sp,
                color = Color(0xFFD1D5DB)
            )

            Spacer(modifier = Modifier.width(2.dp))

            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(14.dp)
            )

            Text(
                text = "${formatDurationSeconds(entry.durationSeconds)} seconds",
                fontSize = 13.sp,
                color = Color(0xFFD1D5DB)
            )
        }
    }
}

/**
 * Dialog displaying list of Previous Polls/Quizzes.
 * Matches Screenshot 3 (1001468506.jpg) 100% with zero dummy data.
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
                .background(Color(0xFF101318)),
            color = Color(0xFF101318)
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
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFE2E8F0)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Poll/Quiz",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF1E242E),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Previous Polls/Quizzes",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE5E7EB)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (polls.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "এখনো কোনো পোল বা কুইজ শুরু হয়নি",
                                fontSize = 14.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(polls, key = { it.pollId }) { pollItem ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF20242D))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = pollItem.title,
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (pollItem.isEnded) Color(0xFF3E4656) else Color(0xFFDC2626)
                                            ) {
                                                Text(
                                                    text = if (pollItem.isEnded) "ENDED" else "LIVE",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFE2E8F0),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = { onSelectPoll(pollItem) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF1967D2),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                                modifier = Modifier.height(38.dp)
                                            ) {
                                                Text(
                                                    text = "View",
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Medium
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
        color = Color(0xFF1E293B).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
        shadowElevation = 6.dp,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenPoll(activePoll) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                text = "নতুন পোল প্রশ্ন: ${activePoll.title}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
