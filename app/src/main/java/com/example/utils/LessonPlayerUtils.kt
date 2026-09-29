package com.example.utils

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Utility functions for Lesson Date/Time formatting, file downloads, and Context helpers.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun formatBanglaDateTime(isoDateStr: String?): String {
    if (isoDateStr.isNullOrBlank()) return "১ সেপ্টেম্বর ২০২৬ • ১৭:০০ pm"
    return try {
        val cleanStr = if (isoDateStr.length >= 19) isoDateStr.substring(0, 19) else isoDateStr
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(cleanStr) ?: return isoDateStr

        val dayFormat = SimpleDateFormat("d", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Dhaka") }
        val monthFormat = SimpleDateFormat("MMMM", Locale.forLanguageTag("bn")).apply { timeZone = TimeZone.getTimeZone("Asia/Dhaka") }
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Dhaka") }
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Dhaka") }

        val day = toBengaliDigits(dayFormat.format(date))
        val month = monthFormat.format(date)
        val year = toBengaliDigits(yearFormat.format(date))
        val time = toBengaliDigits(timeFormat.format(date).lowercase())

        "$day $month $year • $time"
    } catch (e: Exception) {
        isoDateStr
    }
}

fun toBengaliDigits(input: Any?): String {
    if (input == null) return "০"
    val str = input.toString()
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val sb = StringBuilder()
    for (char in str) {
        if (char in '0'..'9') {
            sb.append(banglaDigits[char - '0'])
        } else {
            sb.append(char)
        }
    }
    return sb.toString()
}

@JvmName("toBengaliDigitsExt")
fun Any?.toBengaliDigits(): String = toBengaliDigits(this)


fun calculateTimeDifference(startTimeStr: String?): Long {
    if (startTimeStr.isNullOrBlank()) return 0L
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(startTimeStr) ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(startTimeStr)
        if (date != null) {
            val diff = date.time - System.currentTimeMillis()
            if (diff > 0) diff else 0L
        } else 0L
    } catch (_: Exception) {
        0L
    }
}

fun formatLessonDateDetailed(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return "শীঘ্রই আসছে"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(rawDate)
        if (date != null) {
            val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("bn-BD"))
            formatter.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
            formatter.format(date)
        } else {
            rawDate
        }
    } catch (_: Exception) {
        rawDate
    }
}

fun formatLessonTimeRange(startTimeStr: String?, endTimeStr: String?): String {
    if (startTimeStr.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val start = parser.parse(startTimeStr)
        val end = if (!endTimeStr.isNullOrBlank()) parser.parse(endTimeStr) else null

        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        timeFormatter.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        val startFormatted = start?.let { timeFormatter.format(it) } ?: ""
        val endFormatted = end?.let { timeFormatter.format(it) } ?: ""

        if (startFormatted.isNotBlank() && endFormatted.isNotBlank()) {
            "$startFormatted - $endFormatted"
        } else if (startFormatted.isNotBlank()) {
            startFormatted
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
}

fun downloadFile(context: Context, url: String, title: String) {
    try {
        if (url.isBlank()) {
            Toast.makeText(context, "ডাউনলোড লিঙ্ক উপলব্ধ নেই", Toast.LENGTH_SHORT).show()
            return
        }
        val downloadManager = com.example.download.AppFileDownloadManager.getInstance(context)
        val downloadId = "pdf_" + (url.hashCode().toString())
        downloadManager.downloadFile(
            id = downloadId,
            title = title,
            subtitle = "পিডিএফ নোট ও স্লাইড",
            fileType = com.example.database.DownloadedItemEntity.FILE_TYPE_PDF,
            remoteUrl = url
        )
        Toast.makeText(context, "ইন-অ্যাপ অফলাইন ডাউনলোড শুরু হয়েছে। 'ডাউনলোড' ট্যাবে দেখতে পাবেন।", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "ডাউনলোড ব্যর্থ হয়েছে: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openInExternalApp(context: Context, url: String) {
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "স্লাইড ওপেন করুন"))
    } catch (_: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "ব্রাউজার বা পিডিএফ রিডার পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
}

fun copyToClipboard(context: Context, url: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Lecture Slide URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "স্লাইড লিংক কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}
