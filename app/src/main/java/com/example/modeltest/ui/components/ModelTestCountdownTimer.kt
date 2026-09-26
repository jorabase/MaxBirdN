package com.example.modeltest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Real-time Countdown Timer component formatted in Bengali numerals:
 * e.g., "০২ দিন ০৪ ঘণ্টা ১২ মিনিট ৩০ সেকেন্ড"
 */
@Composable
fun ModelTestCountdownTimer(
    targetEpochMillis: Long,
    onTimerFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remainingMillis by remember(targetEpochMillis) {
        mutableStateOf(maxOf(0L, targetEpochMillis - System.currentTimeMillis()))
    }

    LaunchedEffect(targetEpochMillis) {
        while (true) {
            val now = System.currentTimeMillis()
            val diff = targetEpochMillis - now
            if (diff <= 0L) {
                remainingMillis = 0L
                onTimerFinished()
                break
            } else {
                remainingMillis = diff
            }
            delay(1000L)
        }
    }

    val totalSeconds = remainingMillis / 1000L
    val days = totalSeconds / (24 * 3600)
    val hours = (totalSeconds % (24 * 3600)) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    if (remainingMillis <= 0L) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "সময় শেষ",
                color = Color(0xFFDC2626),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (days > 0) {
                TimeUnitBox(value = toBengaliDigits(days.toString()), label = "দিন")
            }
            TimeUnitBox(value = toBengaliDigits("%02d".format(hours)), label = "ঘণ্টা")
            TimeUnitBox(value = toBengaliDigits("%02d".format(minutes)), label = "মিনিট")
            TimeUnitBox(value = toBengaliDigits("%02d".format(seconds)), label = "সেকেন্ড")
        }
    }
}

@Composable
private fun TimeUnitBox(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF3B82F6).copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(min = 44.dp)
    ) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D4ED8)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF64748B)
        )
    }
}

/**
 * Parses ISO date string to Epoch Millis safely
 */
fun parseIsoDateToMillis(dateStr: String?): Long? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val clean = dateStr.replace("Z", "").take(19)
        parser.parse(clean)?.time
    } catch (_: Exception) {
        try {
            val simple = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            simple.parse(dateStr.take(10))?.time
        } catch (_: Exception) {
            null
        }
    }
}
