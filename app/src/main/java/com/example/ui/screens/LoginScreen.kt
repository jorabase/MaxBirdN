package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthState
import com.example.auth.AuthViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    authState: AuthState,
    onNavigateToPin: (String) -> Unit,
    onNavigateToOtp: (String, String) -> Unit,
    sessionManager: com.example.auth.SessionManager? = null,
    onLoginSuccess: (() -> Unit)? = null,
    onNavigateToActivation: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var phoneInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var showQrLoginSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = isSystemInDarkTheme()

    val textFieldInteractionSource = remember { MutableInteractionSource() }
    val isTextFieldFocused by textFieldInteractionSource.collectIsFocusedAsState()

    LaunchedEffect(Unit) {
        visible = true
        // Zero-Trust Check: verify device activation even if someone bypassed Splash directly into Login
        try {
            if (!com.example.security.DeviceActivationRepository.isDeviceActivated(context)) {
                onNavigateToActivation?.invoke()
            }
        } catch (_: Throwable) {}
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.NavigateToPin -> {
                onNavigateToPin(authState.phone)
                viewModel.resetState()
            }
            is AuthState.NavigateToOtp -> {
                onNavigateToOtp(authState.phone, authState.authType)
                viewModel.resetState()
            }
            is AuthState.Error -> errorMessage = authState.message
            else -> {}
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val successColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)

    // ---------- Ambient animations ----------
    val ambient = rememberInfiniteTransition(label = "ambient")
    val blob1X by ambient.animateFloat(-35f, 45f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "b1x")
    val blob1Y by ambient.animateFloat(-25f, 35f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "b1y")
    val blob2X by ambient.animateFloat(30f, -40f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse), label = "b2x")
    val blob2Y by ambient.animateFloat(25f, -30f, infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "b2y")
    val glowAlpha by ambient.animateFloat(0.45f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val floatY by ambient.animateFloat(-6f, 6f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatY")
    val ringAngle by ambient.animateFloat(0f, 360f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "ring")

    // ---------- Animated gradient border (rotating) ----------
    val density = LocalDensity.current
    val logoBoxPx = with(density) { 84.dp.toPx() }
    val center = logoBoxPx / 2f
    val rad = Math.toRadians(ringAngle.toDouble())
    val dx = (cos(rad) * logoBoxPx).toFloat()
    val dy = (sin(rad) * logoBoxPx).toFloat()
    val ringBrush = Brush.linearGradient(
        colors = listOf(primary, secondary, primary.copy(alpha = 0.3f), secondary.copy(alpha = 0.6f), primary),
        start = Offset(center - dx, center - dy),
        end = Offset(center + dx, center + dy)
    )

    val backgroundGradient = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(listOf(Color(0xFF060B19), Color(0xFF020617), Color(0xFF0B132B)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFEEF2FF), Color(0xFFE2E8F0)))
        }
    }

    fun submitPhoneLogin() {
        if (authState == AuthState.Loading) return
        try {
            keyboardController?.hide()
        } catch (_: Throwable) {}

        if (com.example.security.DeviceActivationRepository.isConfigured() &&
            !com.example.security.DeviceActivationRepository.isDeviceActivated(context)
        ) {
            errorMessage = "অননুমোদিত ডিভাইস! আগে ডিভাইস এক্সেস সক্রিয় করতে হবে।"
            onNavigateToActivation?.invoke()
        } else if (phoneInput.length >= 10) {
            viewModel.checkUser(phoneInput)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Moving aurora blobs
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.TopStart)
                .graphicsLayer { translationX = blob1X; translationY = blob1Y }
                .offset(x = (-100).dp, y = (-80).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(primary.copy(alpha = if (isDark) 0.24f else 0.15f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.BottomEnd)
                .graphicsLayer { translationX = blob2X; translationY = blob2Y }
                .offset(x = 95.dp, y = 95.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(secondary.copy(alpha = if (isDark) 0.22f else 0.13f), Color.Transparent)
                    )
                )
        )
        // Soft center glow behind card
        Box(
            modifier = Modifier
                .size(440.dp)
                .align(Alignment.Center)
                .graphicsLayer { alpha = glowAlpha }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(primary.copy(alpha = if (isDark) 0.09f else 0.06f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // ---------- Logo & Brand Header (floating + rotating gradient ring) ----------
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(700)) + slideInVertically(
                    initialOffsetY = { -40 },
                    animationSpec = tween(700, easing = FastOutSlowInEasing)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(106.dp)
                            .graphicsLayer { translationY = floatY }
                    ) {
                        // Outer rotating orbital arc
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(ringAngle)
                        ) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        primary.copy(alpha = 0.65f),
                                        Color.Transparent,
                                        secondary.copy(alpha = 0.65f),
                                        Color.Transparent
                                    )
                                ),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Soft radial glow behind logo
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            primary.copy(alpha = if (isDark) 0.3f else 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Main 3D Squircle Logo Box
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .shadow(
                                    elevation = if (isDark) 18.dp else 12.dp,
                                    shape = RoundedCornerShape(24.dp),
                                    spotColor = primary,
                                    ambientColor = secondary
                                )
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (isDark) Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                                    else Brush.linearGradient(listOf(Color.White, Color(0xFFF1F5F9)))
                                )
                                .border(2.2.dp, ringBrush, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground_asset),
                                contentDescription = "MaxBird Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Verified Academy Pill Badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = primary.copy(alpha = if (isDark) 0.15f else 0.1f),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "স্মার্ট লার্নিং প্ল্যাটফর্ম",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gradient brand name
                    Text(
                        text = "MaxBird",
                        style = TextStyle(
                            brush = Brush.linearGradient(listOf(primary, secondary)),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "সহজ ও আনন্দদায়ক শেখার ডিজিটাল একাডেমি",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ---------- Phone Input Card ----------
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(800, delayMillis = 150)) + slideInVertically(
                    initialOffsetY = { 60 },
                    animationSpec = tween(800, delayMillis = 150, easing = FastOutSlowInEasing)
                )
            ) {
                val isComplete = phoneInput.length == 11

                val borderColor by animateColorAsState(
                    targetValue = when {
                        errorMessage != null -> MaterialTheme.colorScheme.error
                        isComplete -> successColor
                        isTextFieldFocused || phoneInput.isNotEmpty() -> primary
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                    animationSpec = tween(300),
                    label = "borderColor"
                )

                val animatedProgress by animateFloatAsState(
                    targetValue = (phoneInput.length / 11f).coerceIn(0f, 1f),
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "progressAnim"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = if (isDark) 20.dp else 12.dp,
                            shape = RoundedCornerShape(30.dp),
                            spotColor = primary.copy(alpha = 0.35f)
                        ),
                    shape = RoundedCornerShape(30.dp),
                    color = if (isDark) Color(0xFF0F172A).copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primary.copy(alpha = if (isDark) 0.4f else 0.25f),
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                secondary.copy(alpha = if (isDark) 0.35f else 0.2f)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Card Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "মোবাইল নম্বর লিখুন",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "লগইন বা নতুন অ্যাকাউন্ট খুলতে ১১ ডিজিটের নম্বর দিন",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, primary.copy(alpha = 0.25f)),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Input container with animated border & subtle focus glow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .shadow(
                                    elevation = if (isTextFieldFocused) 6.dp else 0.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    spotColor = borderColor.copy(alpha = 0.4f)
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isDark) Color(0xFF070D19)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                                .border(
                                    width = if (isTextFieldFocused || phoneInput.isNotEmpty() || errorMessage != null) 1.8.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(18.dp)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Country code badge
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(
                                        if (isDark) Color(0xFF131E33)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🇧🇩",
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "+880",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight(0.6f)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )

                            TextField(
                                value = phoneInput,
                                onValueChange = { rawValue ->
                                    val sanitized = sanitizePhoneInput(rawValue)
                                    if (sanitized.length <= 11) {
                                        phoneInput = sanitized
                                        errorMessage = null
                                    }
                                },
                                interactionSource = textFieldInteractionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                textStyle = TextStyle(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    errorContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = primary
                                ),
                                placeholder = {
                                    Text(
                                        text = "০১XXXXXXXXX",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.5.sp
                                    )
                                },
                                trailingIcon = {
                                    AnimatedContent(
                                        targetState = when {
                                            isComplete -> 2
                                            phoneInput.isNotEmpty() -> 1
                                            else -> 0
                                        },
                                        transitionSpec = {
                                            (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
                                        },
                                        label = "trailingIconState"
                                    ) { state ->
                                        when (state) {
                                            2 -> Surface(
                                                shape = CircleShape,
                                                color = successColor.copy(alpha = 0.15f),
                                                modifier = Modifier
                                                    .padding(end = 10.dp)
                                                    .size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Complete",
                                                        tint = successColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            1 -> IconButton(onClick = {
                                                phoneInput = ""
                                                errorMessage = null
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            else -> Spacer(modifier = Modifier.size(1.dp))
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitPhoneLogin() }
                                ),
                                singleLine = true
                            )
                        }

                        // Progress bar + Status & Bengali digit counter
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            color = borderColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isComplete -> "✓ নম্বরটি সম্পূর্ণ হয়েছে"
                                    phoneInput.isNotEmpty() -> "আরও ${(11 - phoneInput.length).toBengaliDigits()} ডিজিট বাকি"
                                    else -> "বাংলাদেশি মোবাইল নম্বর"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isComplete) successColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "${phoneInput.length.toBengaliDigits()}/১১",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = borderColor
                            )
                        }

                        // Error message banner
                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.35f else 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = errorMessage ?: "",
                                        color = if (isDark) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.error,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Helper banner
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.25f else 0.45f),
                            border = BorderStroke(1.dp, primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "নতুন অ্যাকাউন্ট হলে ওটিপি ভেরিফিকেশনের পর আপনার নাম ও শিক্ষা প্রতিষ্ঠানের তথ্য দিয়ে অ্যাকাউন্ট কমপ্লিট করার সুযোগ থাকবে।",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Gradient CTA button with glow + press animation
                        val isEnabled = authState != AuthState.Loading && phoneInput.length >= 10
                        val interaction = remember { MutableInteractionSource() }
                        val isPressed by interaction.collectIsPressedAsState()
                        val pressScale by animateFloatAsState(
                            targetValue = when {
                                isPressed -> 0.96f
                                isEnabled -> 1f
                                else -> 0.99f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "pressScale"
                        )

                        val btnBrush = if (isEnabled) {
                            Brush.linearGradient(listOf(primary, secondary))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                                .shadow(
                                    elevation = if (isEnabled) (8.dp + 6.dp * glowAlpha) else 0.dp,
                                    shape = RoundedCornerShape(18.dp),
                                    spotColor = primary,
                                    ambientColor = secondary
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(btnBrush)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    enabled = isEnabled
                                ) {
                                    submitPhoneLogin()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (authState == AuthState.Loading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = "যাচাই করা হচ্ছে...",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "এগিয়ে যান",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Modern "অথবা" Divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "অথবা",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // QR / Session Code Login Button
                        OutlinedButton(
                            onClick = { showQrLoginSheet = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = primary.copy(alpha = if (isDark) 0.08f else 0.05f)
                            ),
                            border = BorderStroke(1.2.dp, primary.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "কিউআর / সেশন কোড দিয়ে লগইন",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primary
                            )
                        }
                    }
                }
            }

            if (showQrLoginSheet && sessionManager != null) {
                com.example.ui.dialogs.QrSessionLoginSheet(
                    sessionManager = sessionManager,
                    onLoginSuccess = {
                        showQrLoginSheet = false
                        onLoginSuccess?.invoke()
                    },
                    onDismiss = { showQrLoginSheet = false }
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ---------- Terms footer ----------
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(800, delayMillis = 300))
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.25f else 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "এগিয়ে যাওয়ার মাধ্যমে আপনি MaxBird এর শর্তাবলী ও গোপনীয়তা নীতি মেনে নিচ্ছেন।",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---------- Helper: English ↔ Bengali digits & Input Sanitizer ----------
private val bnDigits = mapOf(
    '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
    '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
)

private val bnToEnDigits = mapOf(
    '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
    '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
)

private fun Int.toBengaliDigits(): String =
    toString().map { bnDigits[it] ?: it }.joinToString("")

/**
 * Converts Bengali digits to English digits, strips spaces/hyphens/+88 prefixes on paste,
 * and keeps only numeric characters.
 */
private fun sanitizePhoneInput(raw: String): String {
    val normalized = raw.map { bnToEnDigits[it] ?: it }
        .filter { it in '0'..'9' }
        .joinToString("")

    return when {
        normalized.startsWith("8801") && normalized.length > 11 -> normalized.removePrefix("88")
        else -> normalized
    }
}
