package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.SessionManager
import com.example.utils.AppLang
import com.example.utils.NetworkUtils
import com.example.utils.tr
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    sessionManager: SessionManager,
    onNavigateOnline: (isLoggedIn: Boolean) -> Unit,
    onNavigateOffline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var startAnimation by remember { mutableStateOf(false) }
    var statusState by remember { mutableStateOf(0) } // 0 = connecting, 1 = online, 2 = offline

    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.78f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "logo_alpha"
    )

    val titleOffsetY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 18f,
        animationSpec = tween(durationMillis = 700, delayMillis = 140, easing = FastOutSlowInEasing),
        label = "title_offset"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 650, delayMillis = 140, easing = FastOutSlowInEasing),
        label = "title_alpha"
    )

    val footerAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "footer_alpha"
    )

    // Serene, slow breathing aura & minimalist shimmer bar
    val infiniteTransition = rememberInfiniteTransition(label = "splash_serene")
    val auraBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_breath"
    )
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        val startTime = System.currentTimeMillis()
        val isReachable = NetworkUtils.isInternetReachable(context, timeoutMs = 2500)

        val elapsed = System.currentTimeMillis() - startTime
        val remainingWait = (850L - elapsed).coerceAtLeast(0L)
        if (remainingWait > 0) {
            delay(remainingWait)
        }

        if (isReachable) {
            statusState = 1
            delay(150)
            val isLoggedIn = sessionManager.getAccessToken() != null
            onNavigateOnline(isLoggedIn)
        } else {
            statusState = 2
            delay(300)
            onNavigateOffline()
        }
    }

    val statusText = when (statusState) {
        1 -> tr("Welcome back", "স্বাগতম")
        2 -> tr("Offline mode", "অফলাইন মোড")
        else -> tr("Connecting...", "সংযোগ হচ্ছে...")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF040B1A),
                        Color(0xFF0A1D3B),
                        Color(0xFF061126)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Serene, uncluttered ambient radial glow behind emblem
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.45f
            val baseRadius = size.minDimension * 0.46f * auraBreath

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.16f),
                        Color(0xFF6366F1).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = Offset(cx, cy)
            )

            // Delicate hairline orbital ring (single, understated luxury touch)
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = 74.dp.toPx() * auraBreath,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Centered Brand Composition
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-24).dp)
                .padding(horizontal = 32.dp)
        ) {
            // Emblem with soft frosted halo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF38BDF8).copy(alpha = 0.24f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Surface(
                    modifier = Modifier
                        .size(84.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = CircleShape,
                            spotColor = Color(0xFF38BDF8).copy(alpha = 0.45f)
                        ),
                    shape = CircleShape,
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground_asset),
                            contentDescription = "MaxBird",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Disciplined Wordmark & Short Tagline
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
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC),
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Bird",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF6C86E),
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = tr("Smart Digital Classroom", "স্মার্ট ডিজিটাল ক্লাসরুম"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFCBD5E1).copy(alpha = 0.82f),
                    letterSpacing = 0.6.sp
                )
            }
        }

        // Minimalist Bottom Shimmer Bar & Quiet Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 44.dp)
                .alpha(footerAlpha)
        ) {
            if (statusState == 0) {
                Canvas(
                    modifier = Modifier
                        .width(96.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                ) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.14f),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                    val barW = size.width * 0.42f
                    val startX = (size.width + barW) * shimmerProgress - barW
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF38BDF8),
                                Color(0xFFF6C86E),
                                Color.Transparent
                            ),
                            startX = startX,
                            endX = startX + barW
                        ),
                        topLeft = Offset(startX, 0f),
                        size = Size(barW, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
            } else {
                Icon(
                    imageVector = if (statusState == 1) Icons.Default.Wifi else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (statusState == 1) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.4.sp
            )
        }
    }
}
