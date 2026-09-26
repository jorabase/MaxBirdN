package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.SessionManager
import com.example.utils.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    sessionManager: SessionManager,
    onNavigateOnline: (isLoggedIn: Boolean) -> Unit,
    onNavigateOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // Animation states
    var startAnimation by remember { mutableStateOf(false) }
    var connectionStatusText by remember { mutableStateOf("সংযোগ পরীক্ষা করা হচ্ছে...") }
    var isCheckingNetwork by remember { mutableStateOf(true) }
    var detectedOnline by remember { mutableStateOf<Boolean?>(null) }

    // Logo scale & alpha animation
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.35f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "logo_alpha"
    )

    // Pulse animation for outer glow around logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // MaxBird title slide up & alpha
    val titleOffsetY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 60f,
        animationSpec = tween(durationMillis = 900, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "title_offset"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 850, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "title_alpha"
    )

    // Subtitle fade in
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 900, delayMillis = 450, easing = FastOutSlowInEasing),
        label = "subtitle_alpha"
    )

    // Main Startup Logic: runs animation + deep network check concurrently
    LaunchedEffect(Unit) {
        startAnimation = true

        val startTime = System.currentTimeMillis()

        // 1. Check real internet reachability
        val isReachable = NetworkUtils.isInternetReachable(context, timeoutMs = 2500)
        detectedOnline = isReachable

        // 2. Ensure user experiences the full delightful animation (minimum 1900ms)
        val elapsed = System.currentTimeMillis() - startTime
        val remainingWait = (1900L - elapsed).coerceAtLeast(0L)
        if (remainingWait > 0) {
            delay(remainingWait)
        }

        isCheckingNetwork = false

        if (isReachable) {
            connectionStatusText = "স্বাগতম! লোড হচ্ছে..."
            delay(350)
            val isLoggedIn = sessionManager.getAccessToken() != null
            onNavigateOnline(isLoggedIn)
        } else {
            connectionStatusText = "অফলাইন মোডে প্রবেশ করা হচ্ছে..."
            delay(600)
            onNavigateOffline()
        }
    }

    // Background Gradient: Deep luxury navy/sapphire look
    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF07152B),
                Color(0xFF0F1E36),
                Color(0xFF050D1A)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B2146),
                Color(0xFF0072EC),
                Color(0xFF004CB3)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background radial circles
        Box(
            modifier = Modifier
                .size(340.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(Color(0xFF38BDF8).copy(alpha = 0.25f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // Animated Logo Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
            ) {
                // Outer subtle ring
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // White circular elevated badge
                Surface(
                    modifier = Modifier
                        .size(116.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            spotColor = Color(0xFF0072EC).copy(alpha = 0.6f)
                        ),
                    shape = CircleShape,
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.maxbird_logo),
                            contentDescription = "MaxBird Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Text: "MaxBird" sliding up
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = titleOffsetY.dp)
                    .alpha(titleAlpha)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Max",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Bird",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD166),
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tagline / Subtitle
                Text(
                    text = "স্মার্ট ডিজিটাল ক্লাসরুম ও প্রস্তুতি",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.alpha(subtitleAlpha)
                )
            }
        }

        // Bottom connection status bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(subtitleAlpha),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.28f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    if (isCheckingNetwork) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color(0xFFFFD166),
                            modifier = Modifier.size(15.dp)
                        )
                    } else if (detectedOnline == true) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = connectionStatusText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
