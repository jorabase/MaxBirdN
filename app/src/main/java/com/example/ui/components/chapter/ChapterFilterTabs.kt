package com.example.ui.components.chapter

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.toBengaliDigits

/**
 * Filter tab types to cleanly separate Live Classes (চলছে), Upcoming (আপকামিং), Recorded (রেকর্ড), and Exams (পরীক্ষা).
 */
enum class ChapterContentFilter(val titleBn: String, val icon: ImageVector) {
    ALL("সব", Icons.Default.Dashboard),
    LIVE("চলছে", Icons.Default.LiveTv),
    UPCOMING("আপকামিং", Icons.Default.AccessTime),
    RECORDED("রেকর্ড", Icons.Default.PlayCircleFilled),
    EXAM("পরীক্ষা", Icons.Default.Assignment)
}

@Composable
fun ChapterFilterTabs(
    selectedFilter: ChapterContentFilter,
    onFilterSelected: (ChapterContentFilter) -> Unit,
    totalCount: Int,
    liveCount: Int,
    upcomingCount: Int,
    recordedCount: Int,
    examCount: Int,
    subjectColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChapterFilterTabItem(
            filter = ChapterContentFilter.ALL,
            isSelected = selectedFilter == ChapterContentFilter.ALL,
            count = totalCount,
            accentColor = subjectColor,
            onClick = { onFilterSelected(ChapterContentFilter.ALL) }
        )

        ChapterFilterTabItem(
            filter = ChapterContentFilter.LIVE,
            isSelected = selectedFilter == ChapterContentFilter.LIVE,
            count = liveCount,
            accentColor = Color(0xFFEF4444),
            onClick = { onFilterSelected(ChapterContentFilter.LIVE) }
        )

        ChapterFilterTabItem(
            filter = ChapterContentFilter.UPCOMING,
            isSelected = selectedFilter == ChapterContentFilter.UPCOMING,
            count = upcomingCount,
            accentColor = Color(0xFF0284C7),
            onClick = { onFilterSelected(ChapterContentFilter.UPCOMING) }
        )

        ChapterFilterTabItem(
            filter = ChapterContentFilter.RECORDED,
            isSelected = selectedFilter == ChapterContentFilter.RECORDED,
            count = recordedCount,
            accentColor = Color(0xFF3B82F6),
            onClick = { onFilterSelected(ChapterContentFilter.RECORDED) }
        )

        ChapterFilterTabItem(
            filter = ChapterContentFilter.EXAM,
            isSelected = selectedFilter == ChapterContentFilter.EXAM,
            count = examCount,
            accentColor = Color(0xFFF59E0B),
            onClick = { onFilterSelected(ChapterContentFilter.EXAM) }
        )
    }
}

@Composable
private fun ChapterFilterTabItem(
    filter: ChapterContentFilter,
    isSelected: Boolean,
    count: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "tabBg"
    )
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val badgeBg = if (isSelected) Color.White.copy(alpha = 0.25f) else accentColor.copy(alpha = 0.12f)
    val badgeTextColor = if (isSelected) Color.White else accentColor

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = filter.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )

            Text(
                text = filter.titleBn,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )

            // Count Badge
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = toBengaliDigits(count),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor
                )
            }
        }
    }
}
