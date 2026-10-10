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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * Modern popup modal dialog displayed when user opens the app or navigates to Home.
 * Supports multiple notices in a carousel with page indicators and direct action buttons.
 */
@Composable
fun NoticePopupDialog(
    notices: List<AppNotice>,
    onDismiss: (dontShowAgainToday: Boolean) -> Unit,
    onNoticeActionClick: (notice: AppNotice) -> Unit
) {
    if (notices.isEmpty()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dontShowAgainToday by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(pageCount = { notices.size })

    Dialog(
        onDismissRequest = { onDismiss(dontShowAgainToday) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.6f),
                            Color(0xFF818CF8).copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                // Header Bar: Badge + Indicator + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.22f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "বিশেষ নোটিশ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE0F2FE)
                            )
                        }
                    }

                    if (notices.size > 1) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${notices.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onDismiss(dontShowAgainToday) },
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pager Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val notice = notices[page]
                    val noticeScrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(noticeScrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Image Banner
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    if (!notice.actionUrl.isNullOrBlank()) {
                                        onNoticeActionClick(notice)
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp
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
                                            text = "ছবি লোড করা যায়নি",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Title
                        if (notice.title.isNotBlank()) {
                            Text(
                                text = notice.title,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Description
                        if (!notice.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = notice.description,
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Action Button
                        if (!notice.actionUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onNoticeActionClick(notice) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFF0284C7),
                                                    Color(0xFF2563EB)
                                                )
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = notice.actionButtonText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Multiple notices indicators & arrow controls
                if (notices.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (pagerState.currentPage > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "পূর্ববর্তী",
                                tint = if (pagerState.currentPage > 0) Color.White else Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Dots
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(notices.size) { index ->
                                val isSelected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (pagerState.currentPage < notices.size - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            enabled = pagerState.currentPage < notices.size - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "পরবর্তী",
                                tint = if (pagerState.currentPage < notices.size - 1) Color.White else Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Footer: "Don't show again today" checkbox & Dismiss button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom toggle for "আজকে আর দেখাবেন না"
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgainToday = !dontShowAgainToday }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (dontShowAgainToday) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.1f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (dontShowAgainToday) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dontShowAgainToday) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "আজকে আর দেখাবেন না",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Simple "বন্ধ করুন" text button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier.clickable { onDismiss(dontShowAgainToday) }
                    ) {
                        Text(
                            text = "বন্ধ করুন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}
