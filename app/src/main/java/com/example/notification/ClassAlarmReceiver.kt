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

class ClassAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subjectName = intent.getStringExtra("subject_name") ?: "কোর্স"
        val lessonTitle = intent.getStringExtra("lesson_title") ?: "লাইভ ক্লাস"
        val lessonId = intent.getStringExtra("lesson_id") ?: ""
        val classStartTimeStr = intent.getStringExtra("class_start_time_str") ?: "নির্দিষ্ট সময়ে"
        val leadTimeMinutes = intent.getIntExtra("lead_time_minutes", 25)

        Log.d("ClassAlarmReceiver", "🚨 Alarm triggered for $subjectName: $lessonTitle")

        val customTitle = intent.getStringExtra("custom_title")
        val customBody = intent.getStringExtra("custom_body")

        val title = customTitle ?: "⏰ $subjectName ক্লাস রিমাইন্ডার"
        val body = customBody ?: "আপনার $subjectName ($lessonTitle) ক্লাস $classStartTimeStr এ শুরু হবে, রেডি হন! 🚀"

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

        val sessionManager = com.example.auth.SessionManager(context)
        if (!sessionManager.isAllNotificationsEnabled()) {
            Log.d("ClassAlarmReceiver", "Notification disabled globally by user.")
            return
        }

        val soundUri = if (sessionManager.isNotificationSoundEnabled()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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

        if (sessionManager.isNotificationVibrateEnabled()) {
            builder.setVibrate(longArrayOf(0, 300, 200, 300))
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e("ClassAlarmReceiver", "SecurityException: ${e.message}")
        }
    }
}
