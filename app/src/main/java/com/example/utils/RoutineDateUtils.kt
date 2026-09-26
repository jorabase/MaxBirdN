package com.example.utils

import com.example.api.StudentLessonItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shared utility functions for formatting and sorting dates, calendars, and routines.
 */
object RoutineDateUtils {

    fun parseIsoToDhakaCalendar(isoString: String?): Calendar? {
        if (isoString.isNullOrBlank()) return null
        val clean = isoString.trim()
        val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")

        if (clean.startsWith("0000-00-00") || clean.startsWith("1970-01-01")) return null

        if (clean.endsWith("Z", ignoreCase = true)) {
            try {
                val formatStr = if (clean.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
                val sdfUtc = SimpleDateFormat(formatStr, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val date = sdfUtc.parse(clean.replace(" ", "T"))
                if (date != null) return Calendar.getInstance(dhakaZone).apply { time = date }
            } catch (_: Exception) {}

            try {
                val sdfUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val date = sdfUtc.parse(clean.replace(" ", "T").take(19))
                if (date != null) return Calendar.getInstance(dhakaZone).apply { time = date }
            } catch (_: Exception) {}
        }

        if (clean.contains("+") || (clean.contains("-") && clean.length > 10 && clean.lastIndexOf("-") > 10)) {
            try {
                val cleanT = clean.replace(" ", "T")
                val formatStr = if (cleanT.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
                val date = SimpleDateFormat(formatStr, Locale.US).parse(cleanT)
                if (date != null) return Calendar.getInstance(dhakaZone).apply { time = date }
            } catch (_: Exception) {}
        }

        try {
            val cleanT = clean.replace(" ", "T").take(19)
            val sdfLocal = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = dhakaZone }
            val date = sdfLocal.parse(cleanT)
            if (date != null) return Calendar.getInstance(dhakaZone).apply { time = date }
        } catch (_: Exception) {}

        try {
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = dhakaZone }
            val date = sdfDate.parse(clean.take(10))
            if (date != null) return Calendar.getInstance(dhakaZone).apply { time = date }
        } catch (_: Exception) {}

        try {
            val ms = clean.toLongOrNull()
            if (ms != null && ms > 1000000000L) {
                return Calendar.getInstance(dhakaZone).apply {
                    timeInMillis = if (ms < 100000000000L) ms * 1000L else ms
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun formatTimeRange(startCal: Calendar?, endCal: Calendar?): String {
        if (startCal == null) return ""
        val sdf = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Dhaka") }
        val startStr = sdf.format(startCal.time).toBengaliDigits()
        if (endCal == null || endCal.timeInMillis <= startCal.timeInMillis) return startStr

        val diffHours = (endCal.timeInMillis - startCal.timeInMillis) / (1000 * 60 * 60)
        if (diffHours <= 0 || diffHours > 24) return startStr

        val endStr = sdf.format(endCal.time).toBengaliDigits()
        return "$startStr - $endStr"
    }

    fun calculateDurationText(startCal: Calendar?, endCal: Calendar?): String {
        if (startCal == null || endCal == null) return ""
        val diffMs = endCal.timeInMillis - startCal.timeInMillis
        if (diffMs <= 0 || diffMs > 24 * 60 * 60 * 1000L) return ""
        val diffMins = (diffMs / (1000 * 60)).toInt()
        val hours = diffMins / 60
        val mins = diffMins % 60
        return when {
            hours > 0 && mins > 0 -> "${hours.toBengaliDigits()} ঘণ্টা ${mins.toBengaliDigits()} মিনিট"
            hours > 0 -> "${hours.toBengaliDigits()} ঘণ্টা"
            mins > 0 -> "${mins.toBengaliDigits()} মিনিট"
            else -> ""
        }
    }

    fun sortRoutineLessons(lessons: List<StudentLessonItem>): List<StudentLessonItem> {
        val nowMs = System.currentTimeMillis()

        fun getStartMs(lesson: StudentLessonItem): Long {
            val startTimeStr = lesson.start_time ?: lesson.live_class?.start_time ?: return Long.MAX_VALUE
            return parseIsoToDhakaCalendar(startTimeStr)?.timeInMillis ?: Long.MAX_VALUE
        }

        fun getEndMs(lesson: StudentLessonItem): Long {
            val endTimeStr = lesson.end_time ?: lesson.live_class?.end_time
            val startMs = getStartMs(lesson)
            if (!endTimeStr.isNullOrBlank()) {
                val endCal = parseIsoToDhakaCalendar(endTimeStr)
                if (endCal != null) return endCal.timeInMillis
            }
            return if (startMs != Long.MAX_VALUE) startMs + (90 * 60 * 1000L) else Long.MAX_VALUE
        }

        fun isLessonLiveNow(lesson: StudentLessonItem): Boolean {
            return lesson.isLiveNow
        }

        fun isLessonPassed(lesson: StudentLessonItem): Boolean {
            if (isLessonLiveNow(lesson)) return false
            val endMs = getEndMs(lesson)
            return endMs != Long.MAX_VALUE && nowMs > endMs
        }

        return lessons.sortedWith { a, b ->
            val aLive = isLessonLiveNow(a)
            val bLive = isLessonLiveNow(b)
            when {
                aLive && !bLive -> -1
                !aLive && bLive -> 1
                else -> {
                    val aPassed = isLessonPassed(a)
                    val bPassed = isLessonPassed(b)
                    when {
                        !aPassed && bPassed -> -1
                        aPassed && !bPassed -> 1
                        else -> getStartMs(a).compareTo(getStartMs(b))
                    }
                }
            }
        }
    }
}
