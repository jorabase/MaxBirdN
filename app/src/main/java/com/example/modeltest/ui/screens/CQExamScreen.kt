package com.example.modeltest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.components.ModelTestDetailSkeleton
import com.example.utils.toBengaliDigits

/**
 * Practice Mode CQ Screen: 100% Read-Only!
 * No answer upload input. Shows CQ stimuli and sub-questions (ক, খ, গ, ঘ) with marks.
 * Clicking "টেস্ট শেষ করো" shows a thank you modal and transitions directly to MCQFeedbackScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CQExamScreen(
    sessionId: String,
    viewModel: ModelTestViewModel,
    onFinishPracticeExam: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showThankYouDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadCqQuestions(sessionId)
    }

    if (showThankYouDialog) {
        AlertDialog(
            onDismissRequest = { /* force button */ },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                }
            },
            title = {
                Text(
                    text = "প্র্যাকটিস টেস্ট সমাপ্ত!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "প্র্যাকটিস টেস্টে অংশগ্রহণ করার জন্য তোমাকে ধন্যবাদ! এখন তোমার MCQ পরীক্ষার উত্তর ও বিস্তারিত ব্যাখ্যা দেখে নাও।",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showThankYouDialog = false
                        onFinishPracticeExam(sessionId)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ফিডব্যাক দেখো", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CQ প্রশ্নাবলী (প্র্যাকটিস)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showThankYouDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = { showThankYouDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("টেস্ট শেষ করো", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isCqLoading && uiState.cqContainer == null) {
            Box(modifier = Modifier.padding(innerPadding)) {
                ModelTestDetailSkeleton()
            }
        } else {
            val questions = uiState.cqContainer?.questions ?: emptyList()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info Banner Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "এই টেস্টটি তুমি শুধু প্র্যাকটিসের জন্য দিতে পারবে এবং উত্তরপত্র সাবমিট করতে পারবে না। CQ উত্তরপত্র সাবমিট ও মূল্যায়নের জন্য নির্ধারিত সময়ে মূল মডেল টেস্ট দিতে হবে।",
                            fontSize = 12.5.sp,
                            color = Color(0xFF1E40AF),
                            lineHeight = 17.sp
                        )
                    }
                }

                if (questions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো CQ প্রশ্ন পাওয়া যায়নি",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    questions.forEachIndexed { qIdx, cq ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "সৃজনশীল প্রশ্ন ${toBengaliDigits((qIdx + 1).toString())}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (!cq.stimulus.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = cq.stimulus,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 22.sp
                                    )
                                }

                                if (!cq.stimulus_image.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AsyncImage(
                                        model = cq.stimulus_image,
                                        contentDescription = "Stimulus Image",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(12.dp))

                                // Sub questions (ক, খ, গ, ঘ)
                                val subList = cq.sub_questions ?: emptyList()
                                subList.forEach { sub ->
                                    val keyLabel = sub.key ?: ""
                                    val markText = if (sub.marks != null) "${toBengaliDigits(sub.marks.toInt().toString())} নম্বর" else ""

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "($keyLabel)",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(
                                                text = sub.question ?: "",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 20.sp
                                            )
                                        }

                                        if (markText.isNotBlank()) {
                                            Text(
                                                text = markText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp)
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
