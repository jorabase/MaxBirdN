package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthState
import com.example.auth.AuthViewModel
import com.example.ui.components.ResponsiveDigitInputField
import kotlinx.coroutines.delay

private fun toBengaliDigits(input: String): String {
    val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder(input.length)
    for (ch in input) {
        if (ch in '0'..'9') {
            sb.append(bengaliDigits[ch - '0'])
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

private fun formatBengaliOtpCountdown(totalSeconds: Int): String {
    val safeSec = totalSeconds.coerceAtLeast(0)
    val mins = (safeSec / 60).toString().padStart(2, '0')
    val secs = (safeSec % 60).toString().padStart(2, '0')
    val unit = if (mins == "00") "সেকেন্ড" else "মিনিট"
    return "${toBengaliDigits(mins)} : ${toBengaliDigits(secs)} $unit"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    phone: String,
    authType: String,
    viewModel: AuthViewModel,
    authState: AuthState,
    onNavigateToSetPin: (String) -> Unit,
    onBack: () -> Unit
) {
    var otpValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val serverOtpSeconds by viewModel.otpRemainingSeconds.collectAsState()
    val timerResetTrigger by viewModel.otpTimerResetTrigger.collectAsState()
    var timer by remember(serverOtpSeconds, timerResetTrigger) {
        mutableStateOf(serverOtpSeconds.coerceAtLeast(0))
    }

    LaunchedEffect(serverOtpSeconds, timerResetTrigger) {
        timer = serverOtpSeconds.coerceAtLeast(0)
        while (timer > 0) {
            delay(1000L)
            timer--
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.NavigateToSetPin -> {
                onNavigateToSetPin(authState.phone)
                viewModel.resetState()
            }
            is AuthState.Error -> {
                errorMessage = authState.message
            }
            else -> {}
        }
    }

    val backgroundGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A),
                Color(0xFF020617),
                Color(0xFF090D16)
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Email/OTP Icon Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA855F7).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFC084FC).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        tint = Color(0xFFC084FC),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                val title = if (authType == "login") "পাসওয়ার্ড রিসেট ভেরিফিকেশন" else "মোবাইল নম্বর ভেরিফাই"

                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "আপনার $phone নম্বরে One Time Password (OTP) পাঠানো হয়েছে",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "আপনার নম্বরে পাঠানো ৪ সংখ্যার OTP-টি লিখুন",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Responsive 4-digit OTP Input (Auto-focus enabled)
                        ResponsiveDigitInputField(
                            value = otpValue,
                            length = 4,
                            isPassword = false,
                            autoFocus = true,
                            onValueChange = { newValue ->
                                otpValue = newValue
                                errorMessage = null
                            },
                            onComplete = {
                                if (otpValue.length == 4 && authState != AuthState.Loading) {
                                    viewModel.submitOtp(phone, otpValue, authType)
                                }
                            }
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFF87171),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (otpValue.length == 4 && authState != AuthState.Loading) {
                                    viewModel.submitOtp(phone, otpValue, authType)
                                }
                            },
                            enabled = otpValue.length == 4 && authState != AuthState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6),
                                disabledContainerColor = Color(0xFF334155),
                                contentColor = Color.White,
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            if (authState == AuthState.Loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "ভেরিফাই করুন",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "কোনো OTP পাননি?",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )

                        if (timer > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "আবার OTP পাঠানোর অনুরোধ জানাতে অপেক্ষা করুন:",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatBengaliOtpCountdown(timer),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEC4899),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    errorMessage = null
                                    viewModel.triggerResendOtp(phone, authType)
                                },
                                enabled = authState != AuthState.Loading
                            ) {
                                Text(
                                    text = "পুনরায় ওটিপি পাঠান",
                                    color = Color(0xFFC084FC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "মোবাইল নম্বর ভুল হয়নি তো?",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = phone.removePrefix("88"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        TextButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "নম্বর পরিবর্তন",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF60A5FA)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
