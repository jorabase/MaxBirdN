package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.Locale

object AppDownloadNotificationHelper {
    const val CHANNEL_DOWNLOADS = "shikho_video_downloads"
    private const val NOTIFICATION_BASE_ID = 20000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_DOWNLOADS)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "ভিডিও ডাউনলোড নোটিফিকেশন",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "অফলাইন ভিডিও ডাউনলোড এর অগ্রগতি ও স্ট্যাটাস"
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun getNotificationId(id: String): Int {
        return NOTIFICATION_BASE_ID + (id.hashCode() and 0x7FFFF)
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
        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "DOWNLOADS")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            getNotificationId(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val downloadedFormatted = AppFileDownloadManager.getInstance(context).formatFileSize(downloadedBytes, inBengali = true)
        val totalFormatted = AppFileDownloadManager.getInstance(context).formatFileSize(totalBytes, inBengali = true)
        val speedStr = if (speedMBs > 0) String.format(Locale.US, "%.1f MB/s", speedMBs).replace("0", "০").replace("1", "১").replace("2", "২").replace("3", "৩").replace("4", "৪").replace("5", "৫").replace("6", "৬").replace("7", "৭").replace("8", "৮").replace("9", "৯") else "অপেক্ষারত..."

        val statusText = if (isPaused) {
            "পজ করা হয়েছে • $downloadedFormatted"
        } else {
            "$downloadedFormatted / $totalFormatted ($speedStr)"
        }

        val bnPercent = progressPercent.toString().toBengaliDigits()

        val appLogoBitmap = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(com.example.R.drawable.ic_shikho_notification_small)
            .setContentTitle("ডাউনলোড হচ্ছে: $title")
            .setContentText("$bnPercent% • $statusText")
            .setSubText("$bnPercent% সম্পন্ন")
            .setOngoing(!isPaused)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), totalBytes <= 0L)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (appLogoBitmap != null) {
            builder.setLargeIcon(appLogoBitmap)
        }

        notificationManager.notify(getNotificationId(id), builder.build())
    }

    fun showDownloadCompleteNotification(
        context: Context,
        id: String,
        title: String,
        totalBytes: Long
    ) {
        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "DOWNLOADS")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            getNotificationId(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val totalFormatted = AppFileDownloadManager.getInstance(context).formatFileSize(totalBytes, inBengali = true)

        val appLogoBitmap = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(com.example.R.drawable.ic_shikho_notification_small)
            .setContentTitle("ডাউনলোড সম্পন্ন হয়েছে")
            .setContentText("\"$title\" সফলভাবে ডাউনলোড হয়েছে ($totalFormatted)")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (appLogoBitmap != null) {
            builder.setLargeIcon(appLogoBitmap)
        }

        notificationManager.notify(getNotificationId(id), builder.build())
    }

    fun showDownloadFailedNotification(
        context: Context,
        id: String,
        title: String
    ) {
        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SCREEN", "DOWNLOADS")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            getNotificationId(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val appLogoBitmap = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(com.example.R.drawable.ic_shikho_notification_small)
            .setContentTitle("ডাউনলোড ব্যর্থ হয়েছে")
            .setContentText("\"$title\" ডাউনলোড করতে সমস্যা হয়েছে। আবার চেষ্টা করুন।")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (appLogoBitmap != null) {
            builder.setLargeIcon(appLogoBitmap)
        }

        notificationManager.notify(getNotificationId(id), builder.build())
    }

    fun cancelNotification(context: Context, id: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(getNotificationId(id))
    }

    private fun String.toBengaliDigits(): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        var res = this
        for (i in 0..9) {
            res = res.replace('0' + i, bnDigits[i])
        }
        return res
    }
}
