package com.example.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.utils.AppLang
import java.util.Locale

object AppDownloadNotificationHelper {
    const val CHANNEL_DOWNLOADS = "shikho_video_downloads"
    private const val NOTIFICATION_BASE_ID = 20000

    @Volatile
    private var cachedAppLogoBitmap: Bitmap? = null

    @Volatile
    private var channelCreated = false

    fun createNotificationChannel(context: Context) {
        if (channelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val existing = notificationManager.getNotificationChannel(CHANNEL_DOWNLOADS)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_DOWNLOADS,
                        AppLang.tr("Video & Note Downloads", "ভিডিও ও নোট ডাউনলোড নোটিফিকেশন"),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = AppLang.tr("Offline video and lecture slide download progress", "অফলাইন ভিডিও এবং লেকচার শিট ডাউনলোডের অগ্রগতি ও স্ট্যাটাস")
                        enableLights(false)
                        enableVibration(false)
                        setShowBadge(false)
                        setSound(null, null)
                    }
                    notificationManager.createNotificationChannel(channel)
                }
                channelCreated = true
            } catch (_: Exception) {
            }
        } else {
            channelCreated = true
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getAppLogoBitmap(context: Context): Bitmap? {
        return com.example.notification.NotificationLogoHelper.getCircularAppLogo(context)
    }

    private fun getNotificationId(id: String): Int {
        return NOTIFICATION_BASE_ID + ((id.hashCode() and 0x7FFFFFFF) % 500000)
    }

    private fun createDownloadsPendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "DOWNLOADS")
            putExtra("DOWNLOAD_ID", id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, getNotificationId(id), intent, flags)
    }

    private fun formatSpeedBangla(speedMBs: Float): String {
        if (speedMBs <= 0.001f) return AppLang.tr("Measuring speed...", "গতি নির্ণয় হচ্ছে...")
        return if (speedMBs >= 1.0f) {
            AppLang.tr(
                String.format(Locale.US, "%.1f MB/s", speedMBs),
                String.format(Locale.US, "%.1f মেগাবাইট/সে.", speedMBs).toBengaliDigits()
            )
        } else {
            val speedKBs = (speedMBs * 1024f).coerceAtLeast(1f)
            AppLang.tr(
                String.format(Locale.US, "%.0f KB/s", speedKBs),
                String.format(Locale.US, "%.0f কেবি/সে.", speedKBs).toBengaliDigits()
            )
        }
    }

    private fun formatEtaBangla(downloadedBytes: Long, totalBytes: Long, speedMBs: Float): String? {
        if (totalBytes <= downloadedBytes || speedMBs <= 0.005f) return null
        val remainingBytes = totalBytes - downloadedBytes
        val bytesPerSec = speedMBs * 1024f * 1024f
        val remainingSec = (remainingBytes / bytesPerSec).toLong().coerceAtLeast(1L)
        return when {
            remainingSec >= 3600L -> {
                val hrs = remainingSec / 3600L
                val mins = (remainingSec % 3600L) / 60L
                AppLang.tr(
                    "~${hrs}h ${mins}m left",
                    "~${hrs.toString().toBengaliDigits()} ঘণ্টা ${mins.toString().toBengaliDigits()} মি. বাকি"
                )
            }
            remainingSec >= 60L -> {
                val mins = remainingSec / 60L
                val secs = remainingSec % 60L
                AppLang.tr(
                    "~${mins}m ${secs}s left",
                    "~${mins.toString().toBengaliDigits()} মি. ${secs.toString().toBengaliDigits()} সে. বাকি"
                )
            }
            else -> AppLang.tr(
                "~${remainingSec}s left",
                "~${remainingSec.toString().toBengaliDigits()} সেকেন্ড বাকি"
            )
        }
    }

    fun showDownloadProgressNotification(
        context: Context,
        id: String,
        title: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedMBs: Float,
        isPaused: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pendingIntent = createDownloadsPendingIntent(context, id)

            val downloadManager = AppFileDownloadManager.getInstance(context)
            val isBn = AppLang.isBn()
            val downloadedFormatted = downloadManager.formatFileSize(downloadedBytes, inBengali = isBn)
            val totalFormatted = if (totalBytes > 0L) {
                downloadManager.formatFileSize(totalBytes, inBengali = isBn)
            } else {
                AppLang.tr("Calculating", "গণনা করা হচ্ছে")
            }

            val safePercent = progressPercent.coerceIn(0, 100)
            val bnPercent = safePercent.toString().toBengaliDigits()
            val speedStr = formatSpeedBangla(speedMBs)
            val etaStr = formatEtaBangla(downloadedBytes, totalBytes, speedMBs)

            val contentTitle = if (isPaused) {
                AppLang.tr("Download paused: $title", "ডাউনলোড পজ করা হয়েছে: $title")
            } else {
                AppLang.tr("Downloading: $title", "ডাউনলোড হচ্ছে: $title")
            }

            val statusText = if (isPaused) {
                if (totalBytes > 0L) {
                    AppLang.tr("Paused • $downloadedFormatted / $totalFormatted", "পজ করা আছে • $downloadedFormatted / $totalFormatted")
                } else {
                    AppLang.tr("Paused • $downloadedFormatted", "পজ করা আছে • $downloadedFormatted")
                }
            } else {
                val baseProgress = if (totalBytes > 0L) {
                    "$downloadedFormatted / $totalFormatted • $speedStr"
                } else {
                    "$downloadedFormatted • $speedStr"
                }
                if (etaStr != null) "$baseProgress ($etaStr)" else baseProgress
            }

            val subText = if (isPaused) {
                AppLang.tr("Paused ($safePercent%)", "পজকৃত ($bnPercent%)")
            } else {
                AppLang.tr("$safePercent% complete", "$bnPercent% সম্পন্ন")
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(com.example.R.drawable.ic_shikho_notification_small)
                .setContentTitle(contentTitle)
                .setContentText(statusText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
                .setSubText(subText)
                .setOngoing(!isPaused)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setProgress(100, safePercent, !isPaused && totalBytes <= 0L && safePercent == 0)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            getAppLogoBitmap(context)?.let { builder.setLargeIcon(it) }

            notificationManager.notify(getNotificationId(id), builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun showDownloadCompleteNotification(
        context: Context,
        id: String,
        title: String,
        totalBytes: Long
    ) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pendingIntent = createDownloadsPendingIntent(context, id)
            val totalFormatted = AppFileDownloadManager.getInstance(context).formatFileSize(totalBytes, inBengali = AppLang.isBn())
            val message = AppLang.tr(
                "\"$title\" downloaded for offline viewing ($totalFormatted)",
                "\"$title\" অফলাইনে দেখার জন্য সফলভাবে ডাউনলোড হয়েছে ($totalFormatted)"
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(com.example.R.drawable.ic_shikho_notification_small)
                .setContentTitle(AppLang.tr("Download Complete ✓", "ডাউনলোড সম্পন্ন হয়েছে ✓"))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSubText(AppLang.tr("100% complete", "১০০% সম্পন্ন"))
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            getAppLogoBitmap(context)?.let { builder.setLargeIcon(it) }

            notificationManager.notify(getNotificationId(id), builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun showDownloadFailedNotification(
        context: Context,
        id: String,
        title: String
    ) {
        if (!canPostNotifications(context)) return
        createNotificationChannel(context)

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pendingIntent = createDownloadsPendingIntent(context, id)
            val message = AppLang.tr(
                "\"$title\" download interrupted. Tap to retry.",
                "\"$title\" ডাউনলোড বিঘ্নিত হয়েছে। পুনরায় চেষ্টা করতে ট্যাপ করুন।"
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
                .setSmallIcon(com.example.R.drawable.ic_shikho_notification_small)
                .setContentTitle(AppLang.tr("Download Failed", "ডাউনলোড ব্যর্থ হয়েছে"))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            getAppLogoBitmap(context)?.let { builder.setLargeIcon(it) }

            notificationManager.notify(getNotificationId(id), builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun cancelNotification(context: Context, id: String) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            notificationManager.cancel(getNotificationId(id))
        } catch (_: Exception) {
        }
    }

    private fun String.toBengaliDigits(): String = com.example.utils.toBengaliDigits(this)
}
