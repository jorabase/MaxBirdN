package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.ui.theme.HeaderWallpaperConfig
import com.example.ui.theme.LiveHeaderBackground
import com.example.utils.AvatarUtils

/**
 * Animated Clean Greeting Text Composable
 */
@Composable
fun AnimatedRainbowGreetingText(
    greetingPrefix: String = "হ্যালো,",
    userName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GreetingTransition")
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 190f,
        targetValue = 220f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HueShift"
    )

    val textColor = remember(hueShift) {
        Color.hsv(hue = hueShift, saturation = 0.75f, value = 0.98f)
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
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(5.dp))
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
 * High-Density Compact Home Header with Dynamic Live Wallpaper Support
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

    val infiniteTransition = rememberInfiniteTransition(label = "HeaderGlow")
    val borderGlowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BorderGlow"
    )

    val courseSwitcherBorderGradient = remember(borderGlowOffset) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF38BDF8).copy(alpha = 0.55f),
                Color(0xFF818CF8).copy(alpha = 0.75f),
                Color(0xFFC084FC).copy(alpha = 0.65f),
                Color(0xFF38BDF8).copy(alpha = 0.55f)
            ),
            startX = borderGlowOffset * 500f,
            endX = (borderGlowOffset + 1f) * 500f
        )
    }

    val switcherInteraction = remember { MutableInteractionSource() }
    val isSwitcherPressed by switcherInteraction.collectIsPressedAsState()
    val switcherScale by animateFloatAsState(
        targetValue = if (isSwitcherPressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switcherScale"
    )

    val avatarGlow by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "avatarGlow"
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. Live Dynamic Wallpaper Background
        LiveHeaderBackground(
            config = activeWallpaper,
            modifier = Modifier.matchParentSize()
        )

        // 2. Header Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Logo Badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = Color(0xFF38BDF8).copy(alpha = 0.4f)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8))),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.maxbird_logo),
                            contentDescription = "MaxBird Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedRainbowGreetingText(
                            greetingPrefix = "হ্যালো,",
                            userName = userName
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Notification Bell
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF051226).copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable { onNotificationClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "নোটিফিকেশন",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    if (unreadNotificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-3).dp)
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .border(1.5.dp, Color(0xFF07152B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadNotificationCount > 9) "9+" else unreadNotificationCount.toString(),
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Profile Avatar
                val fallbackInitial = remember(userName) {
                    userName.trim().firstOrNull()?.toString()?.uppercase() ?: "U"
                }

                val imageRequest = remember(avatarUrl, userName, context) {
                    AvatarUtils.buildImageRequest(context, avatarUrl, userName)
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { alpha = avatarGlow }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFF59E0B), Color(0xFFFB7185), Color(0xFF818CF8))
                            )
                        )
                        .padding(2.dp)
                        .clickable { onAvatarClick() },
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
                            contentDescription = "Profile Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = { AvatarFallbackBadge(fallbackInitial) },
                            loading = { AvatarFallbackBadge(fallbackInitial) }
                        )
                    }
                }
            }

            // Sleek, Compact Course Switcher Pill
            if (!activeCourseTitle.isNullOrBlank() && onOpenCourseSwitcher != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .graphicsLayer { scaleX = switcherScale; scaleY = switcherScale }
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(interactionSource = switcherInteraction, indication = null) {
                            onOpenCourseSwitcher()
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF051226).copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, courseSwitcherBorderGradient),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = activeCourseTitle,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7).copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "কোর্স পরিবর্তন",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
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
            fontWeight = FontWeight.Bold
        )
    }
}
