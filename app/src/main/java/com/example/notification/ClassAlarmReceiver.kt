package com.example.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subjectName = intent.getStringExtra("subject_name") ?: "কোর্স"
        val lessonTitle = intent.getStringExtra("lesson_title") ?: "লাইভ ক্লাস"
        val lessonId = intent.getStringExtra("lesson_id") ?: ""
        val classStartTimeStr = intent.getStringExtra("class_start_time_str") ?: "নির্দিষ্ট সময়ে"
        val mentorName = intent.getStringExtra("mentor_name") ?: ""
        val mentorAvatar = intent.getStringExtra("mentor_avatar") ?: ""
        val isStartNow = intent.getBooleanExtra("is_start_now", false)

        Log.d("ClassAlarmReceiver", "🚨 Alarm triggered: $subjectName ($mentorName), startNow: $isStartNow")

        val customTitle = intent.getStringExtra("custom_title")
        val customBody = intent.getStringExtra("custom_body")

        // Exact Shikho notification formats
        val title = customTitle ?: if (isStartNow) {
            "🔴 লাইভ ক্লাস শুরু হয়েছে: $subjectName"
        } else {
            "⏰ $classStartTimeStr -এ $subjectName"
        }

        val body = customBody ?: if (isStartNow) {
            "আপনার $subjectName ($lessonTitle) ক্লাস এখন শুরু হয়ে গেছে, এখনই ক্লাসে জয়েন করো! 🚀"
        } else {
            if (mentorName.isNotBlank()) {
                "হ্যালো! আজকে $subjectName মেন্টর $mentorName-এর ক্লাস ঠিক $classStartTimeStr -এ! 🔥"
            } else {
                "হ্যালো! আজকে $subjectName এর ক্লাস ঠিক $classStartTimeStr -এ! 🔥"
            }
        }

        val sessionManager = com.example.auth.SessionManager(context)
        if (!sessionManager.isAllNotificationsEnabled()) {
            Log.d("ClassAlarmReceiver", "Notification disabled globally by user.")
            return
        }

        ClassAlarmScheduler.createNotificationChannel(context)
        val notificationManager = NotificationManagerCompat.from(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("lesson_id", lessonId)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            mainIntent,
            pendingIntentFlags
        )

        val soundUri = if (sessionManager.isNotificationSoundEnabled()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else null

        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val largeIconBitmap = if (!mentorAvatar.isNullOrBlank()) {
                    try {
                        val url = java.net.URL(mentorAvatar)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.doInput = true
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        conn.connect()
                        val stream = conn.inputStream
                        android.graphics.BitmapFactory.decodeStream(stream)
                    } catch (_: Exception) {
                        null
                    }
                } else null

                val builder = NotificationCompat.Builder(context, ClassAlarmScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(0xFF0072EC.toInt())
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setSound(soundUri)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)

                if (largeIconBitmap != null) {
                    builder.setLargeIcon(largeIconBitmap)
                }

                if (sessionManager.isNotificationVibrateEnabled()) {
                    builder.setVibrate(longArrayOf(0, 300, 200, 300))
                } else {
                    builder.setVibrate(longArrayOf(0))
                }

                val notificationId = (System.currentTimeMillis() % 100000).toInt()
                notificationManager.notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                Log.e("ClassAlarmReceiver", "SecurityException: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
