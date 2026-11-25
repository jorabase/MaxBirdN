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
    if (isoDateStr.isNullOrBlank()) return AppLang.tr("1 Sep 2026 • 5:00 pm", "১ সেপ্টেম্বর ২০২৬ • ৫:০০ pm")
    return try {
        val cleanStr = if (isoDateStr.length >= 19) isoDateStr.substring(0, 19) else isoDateStr
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(cleanStr) ?: return isoDateStr

        val dhakaTz = TimeZone.getTimeZone("Asia/Dhaka")
        val dayFormat = SimpleDateFormat("d", Locale.US).apply { timeZone = dhakaTz }
        val monthFormat = SimpleDateFormat("MMMM", AppLang.locale()).apply { timeZone = dhakaTz }
        val yearFormat = SimpleDateFormat("yyyy", Locale.US).apply { timeZone = dhakaTz }
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US).apply { timeZone = dhakaTz }

        val day = toBengaliDigits(dayFormat.format(date))
        val month = monthFormat.format(date)
        val year = toBengaliDigits(yearFormat.format(date))
        val time = toBengaliDigits(timeFormat.format(date).lowercase(Locale.US))

        "$day $month $year • $time"
    } catch (_: Exception) {
        isoDateStr
    }
}

fun toBengaliDigits(input: Any?): String = formatLocalizedDigits(input)

@JvmName("toBengaliDigitsExt")
fun Any?.toBengaliDigits(): String = toBengaliDigits(this)


fun calculateTimeDifference(startTimeStr: String?): Long {
    if (startTimeStr.isNullOrBlank()) return 0L
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(startTimeStr) ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(startTimeStr)
        if (date != null) {
            val diff = date.time - System.currentTimeMillis()
            if (diff > 0) diff else 0L
        } else 0L
    } catch (_: Exception) {
        0L
    }
}

fun formatLessonDateDetailed(rawDate: String?): String {
    if (rawDate.isNullOrBlank()) return AppLang.tr("Coming soon", "শীঘ্রই আসছে")
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(rawDate)
        if (date != null) {
            val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy", AppLang.locale())
            formatter.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
            toBengaliDigits(formatter.format(date))
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
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val start = parser.parse(startTimeStr)
        val end = if (!endTimeStr.isNullOrBlank()) parser.parse(endTimeStr) else null

        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.US)
        timeFormatter.timeZone = TimeZone.getTimeZone("Asia/Dhaka")
        val startFormatted = start?.let { toBengaliDigits(timeFormatter.format(it)) } ?: ""
        val endFormatted = end?.let { toBengaliDigits(timeFormatter.format(it)) } ?: ""

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
            Toast.makeText(context, AppLang.tr("Download link unavailable", "ডাউনলোড লিংক উপলব্ধ নেই"), Toast.LENGTH_SHORT).show()
            return
        }
        val downloadManager = com.example.download.AppFileDownloadManager.getInstance(context)
        val downloadId = "pdf_" + (url.hashCode().toString())
        downloadManager.downloadFile(
            id = downloadId,
            title = title,
            subtitle = AppLang.tr("PDF Notes & Slides", "পিডিএফ নোট ও স্লাইড"),
            fileType = com.example.database.DownloadedItemEntity.FILE_TYPE_PDF,
            remoteUrl = url
        )
        Toast.makeText(
            context,
            AppLang.tr("Offline download started. View in Downloads.", "অফলাইন ডাউনলোড শুরু হয়েছে। ডাউনলোড ট্যাবে দেখুন।"),
            Toast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        Toast.makeText(
            context,
            AppLang.tr("Download failed: ${e.message}", "ডাউনলোড ব্যর্থ হয়েছে: ${e.message}"),
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun openInExternalApp(context: Context, url: String) {
    if (!com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context)) {
        Toast.makeText(
            context,
            AppLang.tr("Opening files externally is restricted for security", "নিরাপত্তার কারণে অ্যাপের বাইরে ফাইল ওপেন করা অনুমোদিত নয়"),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    try {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, AppLang.tr("Open Slide", "স্লাইড ওপেন করুন")))
    } catch (_: Exception) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                AppLang.tr("No browser or PDF viewer found", "ব্রাউজার বা পিডিএফ রিডার পাওয়া যায়নি"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

fun copyToClipboard(context: Context, url: String) {
    if (!com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context)) {
        Toast.makeText(
            context,
            AppLang.tr("Copying link is restricted for security", "নিরাপত্তার কারণে লিংক কপি করা অনুমোদিত নয়"),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Lecture Slide URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, AppLang.tr("Slide link copied", "স্লাইড লিংক কপি হয়েছে"), Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}
