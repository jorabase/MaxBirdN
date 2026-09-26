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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modeltest.ui.ModelTestViewModel
import com.example.modeltest.ui.components.ModelTestCountdownTimer
import com.example.utils.toBengaliDigits

/**
 * Main Exam CQ Image Upload Screen:
 * - Real-time countdown timer tracking examination end_time (e.g., 8:10 PM)
 * - Upload button becomes permanently disabled once timer reaches 0:00
 * - Has // TODO: Implement CQ Upload API placeholder as required by prompt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CQExamUploadScreen(
    sessionId: String,
    viewModel: ModelTestViewModel,
    onNavigateToResult: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val endTimeMillis = uiState.cqEndTimeMillis ?: (System.currentTimeMillis() + 45 * 60 * 1000L)
    val isExpired = uiState.isCqUploadTimeExpired

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CQ উত্তরপত্র আপলোড",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigateToResult(sessionId) }) {
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
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = { onNavigateToResult(sessionId) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ফলাফল দেখুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Countdown Timer Banner
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isExpired) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isExpired) Color(0xFFFCA5A5) else Color(0xFFBFDBFE)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isExpired) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEF4444)
                        ) {
                            Text(
                                text = "আপলোডের সময় শেষ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "নির্ধারিত সময় শেষ হয়ে যাওয়ায় আর কোনো CQ উত্তরপত্র আপলোড করা সম্ভব নয়।",
                            fontSize = 13.sp,
                            color = Color(0xFF991B1B),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "CQ উত্তর আপলোড করার অবশিষ্ট সময়",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E40AF)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ModelTestCountdownTimer(
                            targetEpochMillis = endTimeMillis,
                            onTimerFinished = { viewModel.onCqUploadTimeExpired() }
                        )
                    }
                }
            }

            // Upload Box Placeholder
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "উত্তরপত্রের ছবি তুলুন বা গ্যালারি থেকে সিলেক্ট করুন",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "পরিষ্কার আলোতে পৃষ্ঠার নম্বর অনুযায়ী ছবি তুলে আপলোড করুন। ফাইল সাইজ ৫MB এর বেশি হতে পারবে না।",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // TODO: Implement CQ Upload API with camera/gallery picker
                    Button(
                        onClick = {
                            // TODO: Implement CQ Upload API integration when backend schema is provided
                        },
                        enabled = !isExpired,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!isExpired) "ছবি আপলোড করুন" else "আপলোড বন্ধ রয়েছে",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
