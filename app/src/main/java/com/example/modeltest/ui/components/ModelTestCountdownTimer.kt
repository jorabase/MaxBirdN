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
 * Parses ISO date string to Epoch Millis safely.
 * If isEndOfDay is true and only a date (or zero time) is provided,
 * sets the time to 23:59:59.999 in Dhaka time so that the exam window
 * does not prematurely expire during the day of the exam.
 */
fun parseIsoDateToMillis(dateStr: String?, isEndOfDay: Boolean = false): Long? {
    if (dateStr.isNullOrBlank()) return null
    val clean = dateStr.trim()
    if (clean.startsWith("0000-00-00") || clean.startsWith("1970-01-01")) return null

    val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")

    // Case 1: Time-only format like "10:00:00" or "23:59:59" or "10:00"
    if (!clean.contains("-") && clean.contains(":")) {
        try {
            val dhakaCal = java.util.Calendar.getInstance(dhakaZone)
            val parts = clean.split(":")
            val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val min = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val sec = parts.getOrNull(2)?.trim()?.take(2)?.toIntOrNull() ?: 0
            dhakaCal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            dhakaCal.set(java.util.Calendar.MINUTE, min)
            dhakaCal.set(java.util.Calendar.SECOND, sec)
            dhakaCal.set(java.util.Calendar.MILLISECOND, if (isEndOfDay && hour == 23 && min == 59) 999 else 0)
            return dhakaCal.timeInMillis
        } catch (_: Exception) {}
    }

    // Case 2: Date-only format like "2026-09-30" or ends with "00:00:00"
    val isDateOnly = (clean.length == 10 && clean.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    val isZeroTime = clean.contains("T00:00:00") || clean.contains(" 00:00:00")

    if (isDateOnly || (isZeroTime && isEndOfDay)) {
        try {
            val datePart = clean.take(10)
            val parts = datePart.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1
            val day = parts[2].toInt()

            val dhakaCal = java.util.Calendar.getInstance(dhakaZone)
            dhakaCal.set(year, month, day, if (isEndOfDay) 23 else 0, if (isEndOfDay) 59 else 0, if (isEndOfDay) 59 else 0)
            dhakaCal.set(java.util.Calendar.MILLISECOND, if (isEndOfDay) 999 else 0)
            return dhakaCal.timeInMillis
        } catch (_: Exception) {}
    }

    // Standard ISO parse
    val dhakaParsed = com.example.api.parseIsoToDhakaMillis(dateStr)
    if (dhakaParsed != null) {
        if (isEndOfDay) {
            val dhakaCal = java.util.Calendar.getInstance(dhakaZone).apply { timeInMillis = dhakaParsed }
            if (dhakaCal.get(java.util.Calendar.HOUR_OF_DAY) == 0 &&
                dhakaCal.get(java.util.Calendar.MINUTE) == 0 &&
                dhakaCal.get(java.util.Calendar.SECOND) == 0
            ) {
                dhakaCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                dhakaCal.set(java.util.Calendar.MINUTE, 59)
                dhakaCal.set(java.util.Calendar.SECOND, 59)
                dhakaCal.set(java.util.Calendar.MILLISECOND, 999)
                return dhakaCal.timeInMillis
            }
        }
        return dhakaParsed
    }

    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleanIso = clean.replace("Z", "").take(19)
        parser.parse(cleanIso)?.time
    } catch (_: Exception) {
        null
    }
}
