package com.example.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Clean, elegant Notice Slider/Banner rendered on the HomeScreen.
 * If multiple active notices exist, it smoothly auto-cycles or can be swiped by students.
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

    // Auto-scroll effect every 5 seconds if multiple notices exist
    if (notices.size > 1) {
        LaunchedEffect(pagerState.currentPage) {
            delay(5000L)
            val nextPage = (pagerState.currentPage + 1) % notices.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Title / Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "গুরুত্বপূর্ণ নোটিশ ও আপডেট",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (notices.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(notices.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 6.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f)
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val notice = notices[page]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNoticeClick(notice) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF1E293B))
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Image Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 8.5f)
                            .background(Color(0xFF020617))
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(notice.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = notice.title,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "নোটিশ ব্যানার",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        )

                        // Priority Tag if high priority
                        if (notice.priority > 0) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 0.dp, bottomEnd = 12.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.9f),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "বিশেষ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Content bar under image
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = notice.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!notice.description.isNullOrBlank()) {
                                Text(
                                    text = notice.description,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!notice.actionUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.3f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = notice.actionButtonText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(12.dp)
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
