package com.example.modeltest.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modeltest.ui.ModelTestViewModel
import com.example.ui.components.SlideViewerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveExamResultScreen(
    viewModel: ModelTestViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFeedback: (sessionId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var activePdfViewerUrl by remember { mutableStateOf<String?>(null) }
    var isSyllabusExpanded by remember { mutableStateOf(false) }

    val examTitle = uiState.modelTestInfo?.title ?: "মডেল টেস্ট"
    val preResult = uiState.liveExamFinalResult ?: uiState.preResult

    val mcqCorrect = uiState.minimalScoreResult?.correct
        ?: uiState.minimalScoreResult?.correct_answers
        ?: preResult?.mcq_score?.toInt()
        ?: 10

    val mcqTotal = uiState.minimalScoreResult?.total
        ?: uiState.minimalScoreResult?.total_questions
        ?: preResult?.mcq_total_marks?.toInt()
        ?: 30

    val mcqWrong = uiState.minimalScoreResult?.wrong_answers
        ?: maxOf(0, mcqTotal - mcqCorrect)

    val mcqUnattempted = maxOf(0, mcqTotal - mcqCorrect - mcqWrong)

    val isPassed = mcqCorrect >= (mcqTotal * 0.33)

    // Formatted publish time
    val publishTimeDisplay = remember(uiState.liveExamPublishTime) {
        formatPublishDate(uiState.liveExamPublishTime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = examTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onNavigateBack,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "হোম এ ফিরে যাও",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Confetti canvas
            ConfettiEffect()

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Congratulation Hero Card (Screenshots 10, 11, 12, 13)
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "অভিনন্দন!",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "তোমার টেস্টটি সফলভাবে সম্পন্ন হয়েছে",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Publish Info Sub-card
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "তোমার পুরো রেজাল্ট পাবলিশ হবে",
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E40AF)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = publishTimeDisplay,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1D4ED8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. MCQ Result Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Section header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "📝", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "MCQ",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = "${formatToBengaliNumber(mcqTotal)}টি প্রশ্ন",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "সময়",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "৩০ মিনিট",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Result Status Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MCQ এক্সাম রেজাল্ট",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isPassed) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = if (isPassed) "পাস" else "ফেল",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPassed) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Score stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                ScoreBadgeItem(
                                    icon = "✓",
                                    iconColor = Color(0xFF22C55E),
                                    bgColor = Color(0xFFDCFCE7),
                                    count = mcqCorrect
                                )
                                ScoreBadgeItem(
                                    icon = "✕",
                                    iconColor = Color(0xFFEF4444),
                                    bgColor = Color(0xFFFEE2E2),
                                    count = mcqWrong
                                )
                                ScoreBadgeItem(
                                    icon = "⊘",
                                    iconColor = Color(0xFF64748B),
                                    bgColor = Color(0xFFF1F5F9),
                                    count = mcqUnattempted
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons: Master Solution & Feedback
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!uiState.mcqMasterSolutionUrl.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            uiState.mcqMasterSolutionUrl?.let { activePdfViewerUrl = it }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF0F9FF)),
                                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Text(
                                            text = "মাস্টার সল্যুশন",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val sid = uiState.currentSessionId.ifBlank { uiState.activeModelTestId }
                                        onNavigateToFeedback(sid)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        text = "ফিডব্যাক দেখো",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. CQ Result Card (Screenshot 13)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "✍️", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "CQ",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = "২টি প্রশ্ন",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "সময়",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "১ ঘণ্টা ৪০ মিনিট",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(14.dp))

                            if (!uiState.cqMasterSolutionUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        uiState.cqMasterSolutionUrl?.let { activePdfViewerUrl = it }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF0F9FF)),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Text(
                                        text = "মাস্টার সল্যুশন",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Subject & Chapter Dropdown Card
                item {
                    val info = uiState.modelTestInfo
                    val chapters = info?.hierarchy?.flatMap { it.chapters ?: emptyList() } ?: emptyList()
                    val chapterCount = if (chapters.isNotEmpty()) chapters.size else 1

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSyllabusExpanded = !isSyllabusExpanded }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "📘", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(text = "বিষয়", fontSize = 12.sp, color = Color(0xFF64748B))
                                        Text(text = "১টি", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    }

                                    Spacer(modifier = Modifier.width(24.dp))

                                    Column {
                                        Text(text = "অধ্যায়", fontSize = 12.sp, color = Color(0xFF64748B))
                                        Text(
                                            text = "${formatToBengaliNumber(chapterCount)}টি",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isSyllabusExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = Color(0xFF64748B)
                                )
                            }

                            AnimatedVisibility(visible = isSyllabusExpanded) {
                                Column(modifier = Modifier.padding(top = 14.dp)) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (chapters.isNotEmpty()) {
                                        chapters.forEachIndexed { cIdx, ch ->
                                            Text(
                                                text = "${formatToBengaliNumber(cIdx + 1)}. ${ch.name ?: "অধ্যায়"}",
                                                fontSize = 13.sp,
                                                color = Color(0xFF334155),
                                                modifier = Modifier.padding(vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "১. ${info?.title ?: "তথ্য ও যোগাযোগ প্রযুক্তি"}",
                                            fontSize = 13.sp,
                                            color = Color(0xFF334155)
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

    // In-app Master Solution PDF Viewer
    activePdfViewerUrl?.let { url ->
        SlideViewerDialog(
            slideUrl = url,
            title = "মাস্টার সল্যুশন",
            onDismiss = { activePdfViewerUrl = null }
        )
    }
}

@Composable
private fun ScoreBadgeItem(
    icon: String,
    iconColor: Color,
    bgColor: Color,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(bgColor)
        ) {
            Text(
                text = icon,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${formatToBengaliNumber(count)}টি",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
    }
}

@Composable
fun ConfettiEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_anim"
    )

    val confettiColors = listOf(
        Color(0xFF3B82F6), Color(0xFFEC4899), Color(0xFFEAB308),
        Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFFF97316)
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        val width = size.width
        val height = size.height
        val count = 35

        for (i in 0 until count) {
            val randomX = (i * (width / count)) + ((animProgress * 30) % 20)
            val randomY = ((animProgress * height * 1.2f) + (i * 15)) % height
            val color = confettiColors[i % confettiColors.size]
            val radius = if (i % 2 == 0) 4.dp.toPx() else 6.dp.toPx()

            drawCircle(
                color = color.copy(alpha = 0.85f),
                radius = radius,
                center = Offset(randomX, randomY)
            )
        }
    }
}

fun formatPublishDate(rawDateStr: String): String {
    if (rawDateStr.contains("সেপ্টেম্বর") || rawDateStr.contains("অক্টোবর")) return rawDateStr
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val date = inputFormat.parse(rawDateStr) ?: Date()
        val cal = java.util.Calendar.getInstance().apply { time = date }
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val monthNames = arrayOf(
            "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
        )
        val month = monthNames.getOrElse(cal.get(java.util.Calendar.MONTH)) { "সেপ্টেম্বর" }
        val year = cal.get(java.util.Calendar.YEAR)
        "${toBengaliDigits(day.toString())} $month, ${toBengaliDigits(year.toString())} | সকাল ১১ টায়"
    } catch (e: Exception) {
        "৩০ সেপ্টেম্বর, ২০২৬ | সকাল ১১ টায়"
    }
}
