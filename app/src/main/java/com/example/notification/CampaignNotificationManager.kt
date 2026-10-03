package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.auth.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Manages personalized campaign, orientation live, routine, and guideline push notifications
 * matching the authentic Shikho rich BigPicture notification formats.
 */
object CampaignNotificationManager {

    /**
     * Triggers the rich "HSC '27 ৫ম কোয়ার্টার Orientation LIVE" notification
     * with BigPicture banner and batch-specific time reminder.
     */
    fun triggerOrientationLiveNotification(
        context: Context,
        batchName: String = "HSC '27",
        quarter: String = "৫ম কোয়ার্টার",
        date: String = "৪ অক্টোবর",
        time: String = "রাত ৯টা"
    ) {
        val sessionManager = SessionManager(context)
        if (!sessionManager.isAllNotificationsEnabled()) return

        val title = "$batchName ব্যাচ, রেডি তো? 🤩"
        val subtitle = "$quarter-এর Orientation LIVE- $date! চোখ রাখো Shikho-র সব চ্যানেলে!"
        val bannerBitmap = createOrientationBannerBitmap(batchName, quarter, date, time)

        displayCampaignNotification(
            context = context,
            title = title,
            body = subtitle,
            bannerBitmap = bannerBitmap,
            deeplink = "shikho://orientation/live"
        )
    }

    /**
     * Triggers personalized "আজকের ক্লাস রুটিন দেখেছো?" notification with the student's name
     * and explore app CTA.
     */
    fun triggerDailyRoutineNotification(
        context: Context,
        studentName: String? = null
    ) {
        val sessionManager = SessionManager(context)
        if (!sessionManager.isAllNotificationsEnabled()) return

        val name = studentName ?: sessionManager.getUserFullName() ?: "শিক্ষার্থী"
        val title = "আজকের ক্লাস রুটিন দেখেছো? 🤩"
        val body = "$name, আজ ক্লাসে কী পড়ানো হচ্ছে তার রুটিন দেখতে ৩ দিন ফ্রি অ্যাপ এক্সপ্লোর করো!"
        val bannerBitmap = createRoutineBannerBitmap(name)

        displayCampaignNotification(
            context = context,
            title = title,
            body = body,
            bannerBitmap = bannerBitmap,
            deeplink = "shikho://routine"
        )
    }

    /**
     * Triggers the "বই নির্বাচনের সিক্রেট 🤫" guideline notification with mentor banner.
     */
    fun triggerBookSelectionGuidelineNotification(context: Context) {
        val sessionManager = SessionManager(context)
        if (!sessionManager.isAllNotificationsEnabled()) return

        val title = "বই নির্বাচনের সিক্রেট 🤫"
        val body = "কলেজের কোন গ্রুপের জন্য কোন বই বেস্ট? সঠিক গাইডলাইন ও সিক্রেট ট্রিকস জানতে ট্যাপ করো!"
        val bannerBitmap = createBookGuidelineBannerBitmap()

        displayCampaignNotification(
            context = context,
            title = title,
            body = body,
            bannerBitmap = bannerBitmap,
            deeplink = "shikho://smartnotes"
        )
    }

    /**
     * Displays a rich notification with BigPictureStyle banner, app icon, sound, vibration, and deeplink.
     */
    fun displayCampaignNotification(
        context: Context,
        title: String,
        body: String,
        bannerBitmap: Bitmap?,
        deeplink: String = ""
    ) {
        ShikhoNotificationManager.createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sessionManager = SessionManager(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ShikhoFirebaseMessagingService.EXTRA_NOTIFICATION_DEEPLINK, deeplink)
            putExtra(ShikhoFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE, "campaign")
            if (deeplink.isNotBlank()) {
                data = Uri.parse(deeplink)
            }
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 100000).toInt(),
            intent,
            pendingIntentFlags
        )

        val soundUri = if (sessionManager.isNotificationSoundEnabled()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else null

        val appLogoBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, ShikhoNotificationManager.CHANNEL_PUSH_NOTIFICATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0072EC.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)

        if (appLogoBitmap != null) {
            builder.setLargeIcon(appLogoBitmap)
        }

        if (sessionManager.isNotificationVibrateEnabled()) {
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
        }

        if (bannerBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bannerBitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
            )
        }

        val notificationId = Random.nextInt(10000, 999999)
        notificationManager.notify(notificationId, builder.build())
        Toast.makeText(context, "ক্যাম্পেইন নোটিফিকেশন পাঠানো হয়েছে! 🔔", Toast.LENGTH_SHORT).show()
    }

    /**
     * Generates a high-quality Orientation LIVE BigPicture Banner matching the Shikho style.
     */
    private fun createOrientationBannerBitmap(
        batchName: String,
        quarter: String,
        date: String,
        time: String
    ): Bitmap {
        val width = 900
        val height = 450
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient Background (Soft Sky Blue to Emerald Mint)
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFFF0FDF4.toInt(), 0xFFE0F2FE.toInt(), 0xFFEFF6FF.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Decorative Card Paper
        val cardPaint = Paint().apply {
            color = Color.WHITE
            setShadowLayer(16f, 0f, 6f, 0x22000000)
        }
        val cardRect = RectF(40f, 35f, 580f, 415f)
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)

        // Batch Title Text
        val batchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF003399.toInt()
            textSize = 42f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$batchName", 310f, 105f, batchPaint)

        // Quarter & Orientation Live Badge
        val quarterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFDC2626.toInt()
            textSize = 40f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$quarter", 310f, 160f, quarterPaint)

        val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE11D48.toInt()
            textSize = 46f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Orientation Live 🔴", 310f, 220f, livePaint)

        // Time Pill Badge
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEFF6FF.toInt()
        }
        val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3B82F6.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val pillRect = RectF(90f, 290f, 530f, 375f)
        canvas.drawRoundRect(pillRect, 20f, 20f, pillPaint)
        canvas.drawRoundRect(pillRect, 20f, 20f, pillBorder)

        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E3A8A.toInt()
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("📅 $date | ⏰ $time", 310f, 345f, pillTextPaint)

        // Right-side decorative circle with Student Avatar/Illustration
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFDBEAFE.toInt()
        }
        canvas.drawCircle(730f, 225f, 135f, circlePaint)

        val avatarEmojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("👩‍🎓", 730f, 260f, avatarEmojiPaint)

        return bitmap
    }

    /**
     * Generates a Routine Announcement BigPicture Banner.
     */
    private fun createRoutineBannerBitmap(name: String): Bitmap {
        val width = 900
        val height = 450
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Purple Gradient Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFFFDF4FF.toInt(), 0xFFFAF5FF.toInt(), 0xFFF3E8FF.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // White Card
        val cardPaint = Paint().apply {
            color = Color.WHITE
        }
        val cardRect = RectF(40f, 35f, 580f, 415f)
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF86198F.toInt()
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("আজ ক্লাসে কী পড়ানো হচ্ছে", 70f, 110f, titlePaint)
        canvas.drawText("জানতে চাও? 📚", 70f, 160f, titlePaint)

        // Pink Pill CTA
        val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF43F5E.toInt()
        }
        val ctaRect = RectF(70f, 220f, 540f, 340f)
        canvas.drawRoundRect(ctaRect, 18f, 18f, ctaPaint)

        val ctaTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ক্লাসের রুটিন দেখতে আর শিখতে", 305f, 265f, ctaTextPaint)
        canvas.drawText("৩ দিন ফ্রি অ্যাপ এক্সপ্লোর করো! ✨", 305f, 305f, ctaTextPaint)

        // Right-side Mentor Illustration Avatar
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFCE7F3.toInt()
        }
        canvas.drawCircle(730f, 225f, 135f, circlePaint)

        val avatarEmojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("👨‍🏫", 730f, 260f, avatarEmojiPaint)

        return bitmap
    }

    /**
     * Generates a Book Guideline BigPicture Banner.
     */
    private fun createBookGuidelineBannerBitmap(): Bitmap {
        val width = 900
        val height = 450
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Amber/Cyan Gradient Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFFFEF9C3.toInt(), 0xFFE0F2FE.toInt(), 0xFFCCFBF1.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // White Card
        val cardPaint = Paint().apply {
            color = Color.WHITE
        }
        val cardRect = RectF(40f, 35f, 580f, 415f)
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F766E.toInt()
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("গ্রুপভিত্তিক সঠিক বই নির্বাচনের", 70f, 110f, titlePaint)

        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE11D48.toInt()
            textSize = 38f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("পারফেক্ট গাইডলাইন ও সিক্রেট 📖", 70f, 175f, highlightPaint)

        // Action CTA
        val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFDC2626.toInt()
        }
        val ctaRect = RectF(70f, 240f, 540f, 330f)
        canvas.drawRoundRect(ctaRect, 18f, 18f, ctaPaint)

        val ctaTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("রিভিল করতে ট্যাপ করো 👆", 305f, 295f, ctaTextPaint)

        // Right-side Mentor Illustration Avatar
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E7FF.toInt()
        }
        canvas.drawCircle(730f, 225f, 135f, circlePaint)

        val avatarEmojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🧑‍🏫", 730f, 260f, avatarEmojiPaint)

        return bitmap
    }
}
