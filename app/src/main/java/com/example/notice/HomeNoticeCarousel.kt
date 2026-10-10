package com.example.notice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/**
 * Ultra-sleek, flat bKash-style Notice Banner Carousel rendered on the HomeScreen.
 * Shows strictly ONE banner at a time across full container width and auto-cycles smoothly every 6 seconds.
 */
@Composable
fun HomeNoticeCarousel(
    notices: List<AppNotice>,
    onNoticeClick: (notice: AppNotice) -> Unit,
    modifier: Modifier = Modifier
) {
    if (notices.isEmpty()) return

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { notices.size })

    // Auto-scroll effect: smoothly transitions to the next page every 6 seconds without getting stuck
    if (notices.size > 1) {
        LaunchedEffect(notices.size) {
            while (true) {
                delay(6000L)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % notices.size
                    try {
                        pagerState.animateScrollToPage(
                            page = nextPage,
                            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                        )
                    } catch (e: Exception) {
                        // Ignore cancellation/interruption
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sleek Flat Horizontal Pager: strictly ONE item per page, full container width (bKash banner style)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            pageSize = PageSize.Fill,
            beyondViewportPageCount = 0,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
            pageSpacing = 0.dp
        ) { page ->
            val notice = notices[page]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 4.8f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNoticeClick(notice) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1224)),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.35f), Color(0xFF1E293B))
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (notice.imageUrl.isNotBlank()) {
                        // Full-bleed Banner Image
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(notice.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = notice.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            },
                            error = {
                                NoticeFallbackBanner(notice)
                            }
                        )

                        // Subtle bottom gradient scrim overlay for readability if title or action is present
                        if (notice.title.isNotBlank() || !notice.actionUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color(0x99030B17),
                                                Color(0xE6030B17)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (notice.title.isNotBlank()) {
                                        Text(
                                            text = notice.title,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }

                                    if (!notice.actionUrl.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF0284C7).copy(alpha = 0.55f),
                                            border = BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = notice.actionButtonText.ifBlank { "দেখুন" },
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Notice with no image URL
                        NoticeFallbackBanner(notice)
                    }

                    // Priority Tag badge if priority > 0
                    if (notice.priority > 0) {
                        Surface(
                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.95f),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "বিশেষ",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }
            }
        }

        // bKash-style animated smooth pagination indicator dots
        if (notices.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                notices.indices.forEach { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 20.dp else 6.dp,
                        animationSpec = tween(durationMillis = 300),
                        label = "dotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(5.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFF0284C7) else Color(0xFF94A3B8).copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Fallback banner for text notices or when image loading fails.
 */
@Composable
private fun NoticeFallbackBanner(notice: AppNotice) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF0284C7))
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notice.title.ifBlank { "গুরুত্বপূর্ণ নোটিশ" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!notice.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = notice.description,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!notice.actionUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notice.actionButtonText.ifBlank { "দেখুন" },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}
