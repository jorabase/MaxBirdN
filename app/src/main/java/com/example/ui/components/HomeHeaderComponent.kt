package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.ui.theme.HeaderWallpaperConfig
import com.example.ui.theme.LiveHeaderBackground
import com.example.utils.AvatarUtils
import java.util.Calendar
import java.util.TimeZone

/**
 * Animated Clean Greeting Text Composable
 */
@Composable
fun AnimatedRainbowGreetingText(
    greetingPrefix: String = com.example.utils.tr("Hello,", "হ্যালো,"),
    userName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GreetingTransition")
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 188f,
        targetValue = 222f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HueShift"
    )

    val textColor = remember(hueShift) {
        Color.hsv(hue = hueShift, saturation = 0.65f, value = 1f)
    }

    val waveAngle by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveAngle"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$greetingPrefix $userName",
            color = textColor,
            fontSize = 17.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "👋",
            fontSize = 16.sp,
            modifier = Modifier.graphicsLayer {
                rotationZ = waveAngle
                transformOrigin = TransformOrigin(0.7f, 0.8f)
            }
        )
    }
}

/**
 * High-Density Compact Home Header with Interactive Live Wallpaper Support
 */
@Composable
fun HomeHeader(
    userName: String,
    subtitle: String,
    avatarUrl: String,
    isPremium: Boolean,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeCourseTitle: String? = null,
    onOpenCourseSwitcher: (() -> Unit)? = null,
    unreadNotificationCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    wallpaperConfig: HeaderWallpaperConfig? = null
) {
    val context = LocalContext.current
    val activeWallpaper = wallpaperConfig ?: remember { HeaderWallpaperConfig() }
    val isBn = com.example.utils.isBangla()

    // Dynamic time-of-day bilingual greeting
    val dynamicGreeting = remember(isBn) {
        val dhakaCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
        when (dhakaCal.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> if (isBn) "শুভ সকাল," else "Good Morning,"
            in 12..15 -> if (isBn) "শুভ দুপুর," else "Good Afternoon,"
            in 16..17 -> if (isBn) "শুভ অপরাহ্ন," else "Good Afternoon,"
            in 18..20 -> if (isBn) "শুভ সন্ধ্যা," else "Good Evening,"
            else -> if (isBn) "হ্যালো," else "Hello,"
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. Live Interactive Dynamic Wallpaper Background
        LiveHeaderBackground(
            config = activeWallpaper,
            modifier = Modifier.matchParentSize(),
            interactive = true
        )

        // Subtle bottom vignette for seamless blend with rounded surface
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.28f)
                        )
                    )
                )
        )

        // 2. Header Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fallbackInitial = remember(userName) {
                    userName.trim().firstOrNull()?.toString()?.uppercase() ?: "U"
                }

                val imageRequest = remember(avatarUrl, userName, context) {
                    AvatarUtils.buildImageRequest(context, avatarUrl, userName)
                }

                AnimatedProfileAvatar(
                    imageRequest = imageRequest,
                    fallbackInitial = fallbackInitial,
                    isPremium = isPremium,
                    onClick = onAvatarClick
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    AnimatedRainbowGreetingText(
                        greetingPrefix = dynamicGreeting,
                        userName = userName
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Notification Bell Button (Compact, softly frosted glass over live wallpaper)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onNotificationClick),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = com.example.utils.tr("Notifications", "নোটিফিকেশন"),
                                tint = Color.White.copy(alpha = 0.82f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (unreadNotificationCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEF4444).copy(alpha = 0.92f),
                            border = BorderStroke(1.2.dp, Color(0xFF07152B).copy(alpha = 0.85f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-4).dp, y = 4.dp)
                                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 3.5.dp)
                            ) {
                                Text(
                                    text = if (unreadNotificationCount > 9) {
                                        com.example.utils.toBengaliDigits("9+")
                                    } else {
                                        com.example.utils.toBengaliDigits(unreadNotificationCount)
                                    },
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // Glassmorphic Course Switcher Pill
            if (!activeCourseTitle.isNullOrBlank() && onOpenCourseSwitcher != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedCourseSwitcherPill(
                    activeCourseTitle = activeCourseTitle,
                    onClick = onOpenCourseSwitcher
                )
            }
        }
    }
}

@Composable
private fun AnimatedProfileAvatar(
    imageRequest: Any,
    fallbackInitial: String,
    isPremium: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AvatarGlowTransition")
    val avatarGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "avatarGlow"
    )

    Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = avatarGlow }
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    spotColor = if (isPremium) Color(0xFFF59E0B) else Color(0xFF38BDF8)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (isPremium) {
                            listOf(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFF818CF8))
                        } else {
                            listOf(Color(0xFF38BDF8), Color(0xFF6366F1), Color(0xFFA855F7))
                        }
                    )
                )
                .padding(2.4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF07152B))
            ) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = com.example.utils.tr("Profile photo", "প্রোফাইল ছবি"),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = { AvatarFallbackBadge(fallbackInitial) },
                    loading = { AvatarFallbackBadge(fallbackInitial) }
                )
            }
        }

        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(Color(0xFF07152B))
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (isPremium) Color(0xFFFBBF24) else Color(0xFF10B981))
        )
    }
}

@Composable
private fun AnimatedCourseSwitcherPill(
    activeCourseTitle: String,
    onClick: () -> Unit
) {
    val switcherInteraction = remember { MutableInteractionSource() }
    val isSwitcherPressed by switcherInteraction.collectIsPressedAsState()
    val switcherScale by animateFloatAsState(
        targetValue = if (isSwitcherPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switcherScale"
    )

    val borderGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF38BDF8).copy(alpha = 0.72f),
                Color(0xFF818CF8).copy(alpha = 0.85f),
                Color(0xFFC084FC).copy(alpha = 0.75f),
                Color(0xFF38BDF8).copy(alpha = 0.72f)
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { scaleX = switcherScale; scaleY = switcherScale }
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = switcherInteraction,
                indication = ripple(bounded = true, color = Color(0xFF38BDF8))
            ) {
                onClick()
            },
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF051226).copy(alpha = 0.72f),
        border = BorderStroke(1.2.dp, borderGradient),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activeCourseTitle,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF0284C7).copy(alpha = 0.35f),
                border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = com.example.utils.tr("Switch", "পরিবর্তন"),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFBAE6FD)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = com.example.utils.tr("Switch Course", "কোর্স পরিবর্তন"),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarFallbackBadge(initial: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(colors = listOf(Color(0xFF0284C7), Color(0xFF6366F1)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
