package com.example.modeltest.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modeltest.data.ShikhoCqQuestionRaw
import com.example.modeltest.ui.ModelTestViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCqExamScreen(
    viewModel: ModelTestViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToUploadDashboard: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val totalSec = uiState.liveCqRemainingSeconds
    val min = totalSec / 60
    val sec = totalSec % 60
    val timerTextBn = formatBengaliTime(min, sec)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CQ",
                        fontSize = 18.sp,
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
                actions = {
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "⏰", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = timerTextBn,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "তোমার সবগুলো প্রশ্নের উত্তর খাতায় লেখার পর উত্তরপত্র আপলোড করো",
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onNavigateToUploadDashboard,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "উত্তরপত্র আপলোড করো",
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
            // 1. Unique Code Card (Screenshot 3)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "তোমার ইউনিক কোড : ",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = uiState.liveCqUCode.ifBlank { "৪২৮৭" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "উত্তর পত্রের প্রতি পৃষ্ঠার সাথে ইউনিক কোডটি খাতায় লিখ",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // 2. Test Rules Card (Screenshot 3)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "টেস্টের নিয়মাবলী",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val qCount = uiState.liveCqQuestions.size.let { if (it == 0) 2 else it }
                        RuleBulletItem(text = "তোমাকে ${formatToBengaliNumber(qCount)} টার মধ্যে ${formatToBengaliNumber(qCount)} টা আনসার করতে হবে।")
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleBulletItem(text = "প্রশ্নের উত্তর লেখার সময় ১ ঘণ্টা")
                        Spacer(modifier = Modifier.height(8.dp))
                        RuleBulletItem(text = "উত্তরপত্র আপলোড করার সময় ৪০ মিনিট")
                    }
                }
            }

            // 3. Question Cards
            val questions = if (uiState.liveCqQuestions.isNotEmpty()) {
                uiState.liveCqQuestions
            } else {
                getFallbackLiveCqQuestions()
            }

            itemsIndexed(questions) { index, q ->
                LiveCqQuestionCard(
                    questionNumber = index + 1,
                    question = q
                )
            }
        }
    }
}

@Composable
private fun RuleBulletItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFF0EA5E9),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF334155),
            lineHeight = 20.sp
        )
    }
}

@Composable
fun LiveCqQuestionCard(
    questionNumber: Int,
    question: ShikhoCqQuestionRaw
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Question No & Total Marks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatToBengaliNumber(questionNumber)}.  ${question.title?.replace("$", "") ?: ""}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatToBengaliNumber((question.total_marks ?: 10.0).toInt()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(12.dp))

            // Sub Questions
            val subQuestions = question.sub_questions ?: listOf(
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("ক) অ্যারে কী?", 1.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("খ) \"scanf(\"%f\", &a)\" —ব্যাখ্যা করো।", 2.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("গ) উদ্দীপকের ধারাটির যোগফল নির্ণয়ের জন্য অ্যালগরিদম তৈরি করো।", 3.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("ঘ) উদ্দীপকের ধারাটির ফলাফল প্রদর্শনের জন্য সি ভাষার একটি প্রোগ্রাম লেখো।", 4.0)
            )

            subQuestions.forEach { sub ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = sub.question?.replace("$", "") ?: "",
                        fontSize = 14.sp,
                        color = Color(0xFF334155),
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatToBengaliNumber((sub.marks ?: 1.0).toInt()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }
        }
    }
}

fun formatBengaliTime(minutes: Long, seconds: Long): String {
    val mStr = String.format(Locale.US, "%02d", minutes)
    val sStr = String.format(Locale.US, "%02d", seconds)
    return "${toBengaliDigits(mStr)} : ${toBengaliDigits(sStr)}"
}

fun formatToBengaliNumber(number: Int): String {
    return toBengaliDigits(number.toString())
}

fun toBengaliDigits(input: String): String {
    val bnDigits = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
    )
    return input.map { bnDigits[it] ?: it }.joinToString("")
}

fun getFallbackLiveCqQuestions(): List<ShikhoCqQuestionRaw> {
    return listOf(
        ShikhoCqQuestionRaw(
            id = "6ab5009a6a5f4a905b733653",
            question_no = "1",
            title = "5 + 10 + 15 +.......+ 200",
            total_marks = 10.0,
            sub_questions = listOf(
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("ক) অ্যারে কী?", 1.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("খ) \"scanf(\"%f\", &a)\" —ব্যাখ্যা করো।", 2.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("গ) উদ্দীপকের ধারাটির যোগফল নির্ণয়ের জন্য অ্যালগরিদম তৈরি করো।", 3.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("ঘ) উদ্দীপকের ধারাটির ফলাফল প্রদর্শনের জন্য সি ভাষার একটি প্রোগ্রাম লেখো।", 4.0)
            )
        ),
        ShikhoCqQuestionRaw(
            id = "6ab5009b70a9cc77f7f40691",
            question_no = "2",
            title = "#include<stdio.h>\nint main ( )\n{\nint K,S = 0;\nfor (K = 10; K <= 100; K = K + 10)\nS = S+K ;\nprintf(\"summation: % d\",S);\n}",
            total_marks = 10.0,
            sub_questions = listOf(
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("ক) অ্যাসেম্বলার কী?", 1.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("খ) C ভাষায় কেন Header file ব্যবহার করা হয়?", 2.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("গ) উদ্দীপকের প্রোগ্রামটির ফ্লোচার্ট অংকন করো।", 3.0),
                com.example.modeltest.data.ShikhoCqSubQuestionRaw("ঘ) উদ্দীপকের প্রোগ্রামটি do-while ব্যবহার করে লেখো।", 4.0)
            )
        )
    )
}
