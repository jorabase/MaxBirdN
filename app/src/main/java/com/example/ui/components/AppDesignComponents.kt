package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ui.theme.isAppInDarkTheme

/**
 * Reusable smooth Material 3 Shimmer placeholder block that adapts to Light & Dark mode.
 */
@Composable
fun AppShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    val isDark = isAppInDarkTheme()
    val transition = rememberInfiniteTransition(label = "appShimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -320f,
        targetValue = 960f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "appShimmerOffset"
    )
    val baseColor = if (isDark) Color(0xFF162338) else Color(0xFFE8EEF5)
    val highlightColor = if (isDark) Color(0xFF243754) else Color(0xFFF8FAFC)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(baseColor, highlightColor, baseColor),
                    start = Offset(shimmerX, 0f),
                    end = Offset(shimmerX + 260f, 180f)
                )
            )
    )
}

/**
 * Polished loading skeleton for ProfileScreen instead of a raw spinning circle.
 */
@Composable
fun AppProfileLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero card skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppShimmerBox(modifier = Modifier.size(92.dp), shape = CircleShape)
                AppShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(20.dp), shape = RoundedCornerShape(8.dp))
                AppShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(14.dp), shape = RoundedCornerShape(8.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppShimmerBox(modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(14.dp))
                    AppShimmerBox(modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(14.dp))
                    AppShimmerBox(modifier = Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(14.dp))
                }
            }
        }

        // Info card skeleton
        AppShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
            shape = RoundedCornerShape(18.dp)
        )

        // Action rows skeleton
        repeat(3) {
            AppShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/**
 * Reusable shimmer skeleton for vertical list screens (Routine, Chapters, Lessons).
 */
@Composable
fun AppListLoadingSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
    showHeaderBanner: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHeaderBanner) {
            AppShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp),
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        repeat(itemCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppShimmerBox(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(15.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                    AppShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.46f)
                            .height(11.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                }
                AppShimmerBox(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape
                )
            }
        }
    }
}

/**
 * Reusable shimmer skeleton for 2-column grid screens (Course Subjects).
 */
@Composable
fun AppGridLoadingSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            shape = RoundedCornerShape(20.dp)
        )
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(2) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppShimmerBox(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                        AppShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(15.dp),
                            shape = RoundedCornerShape(6.dp)
                        )
                        AppShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(11.dp),
                            shape = RoundedCornerShape(6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable shimmer skeleton for ReportCardScreen analytics dashboard.
 */
@Composable
fun AppReportLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            shape = RoundedCornerShape(24.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp),
                shape = RoundedCornerShape(18.dp)
            )
            AppShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp),
                shape = RoundedCornerShape(18.dp)
            )
        }
        AppShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(20.dp)
        )
        repeat(2) {
            AppShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

