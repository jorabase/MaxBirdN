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
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("ClassAlarmReceiver", "🔄 Device rebooted: rescheduling all active custom & routine class alarms")
            CustomAlarmScheduler.rescheduleAllActive(context)
            ClassAlarmScheduler.rescheduleSavedRoutineAlarms(context)
            return
        }

        com.example.utils.AppLang.init(context)
        val subjectName = intent.getStringExtra("subject_name") ?: com.example.utils.AppLang.tr("Course", "কোর্স")
        val lessonTitle = intent.getStringExtra("lesson_title") ?: com.example.utils.AppLang.tr("Live Class", "লাইভ ক্লাস")
        val lessonId = intent.getStringExtra("lesson_id") ?: ""
        val classStartTimeStr = intent.getStringExtra("class_start_time_str") ?: com.example.utils.AppLang.tr("scheduled time", "নির্দিষ্ট সময়ে")
        val mentorName = intent.getStringExtra("mentor_name") ?: ""
        val mentorAvatar = intent.getStringExtra("mentor_avatar") ?: ""
        val isStartNow = intent.getBooleanExtra("is_start_now", false)

        Log.d("ClassAlarmReceiver", "🚨 Alarm triggered: $subjectName ($mentorName), startNow: $isStartNow")

        val customTitle = intent.getStringExtra("custom_title")
        val customBody = intent.getStringExtra("custom_body") ?: intent.getStringExtra("custom_note")

        val title = customTitle ?: if (isStartNow) {
            com.example.utils.AppLang.tr(
                "🔴 Live Class Started: $subjectName",
                "🔴 লাইভ ক্লাস শুরু হয়েছে: $subjectName"
            )
        } else {
            com.example.utils.AppLang.tr(
                "⏰ $subjectName at $classStartTimeStr",
                "⏰ $classStartTimeStr -এ $subjectName"
            )
        }

        val body = customBody ?: if (isStartNow) {
            com.example.utils.AppLang.tr(
                "Your $subjectName ($lessonTitle) class has started! Join now! 🚀",
                "আপনার $subjectName ($lessonTitle) ক্লাস এখন শুরু হয়ে গেছে, এখনই ক্লাসে জয়েন করো! 🚀"
            )
        } else {
            if (mentorName.isNotBlank()) {
                com.example.utils.AppLang.tr(
                    "Hey! Today's $subjectName class with mentor $mentorName starts at $classStartTimeStr! 🔥",
                    "হ্যালো! আজকে $subjectName মেন্টর $mentorName-এর ক্লাস ঠিক $classStartTimeStr -এ! 🔥"
                )
            } else {
                com.example.utils.AppLang.tr(
                    "Hey! Today's $subjectName class starts at $classStartTimeStr! 🔥",
                    "হ্যালো! আজকে $subjectName এর ক্লাস ঠিক $classStartTimeStr -এ! 🔥"
                )
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
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        ).launch {
            try {
                val largeIconBitmap = if (!mentorAvatar.isNullOrBlank()) {
                    var conn: java.net.HttpURLConnection? = null
                    try {
                        val url = java.net.URL(mentorAvatar)
                        conn = (url.openConnection() as? java.net.HttpURLConnection)?.apply {
                            doInput = true
                            connectTimeout = 3000
                            readTimeout = 3000
                            connect()
                        }
                        conn?.inputStream?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Exception) {
                        null
                    } finally {
                        try {
                            conn?.disconnect()
                        } catch (_: Exception) {
                        }
                    }
                } else null

                val appLogoBitmap = NotificationLogoHelper.getCircularAppLogo(context)

                val builder = NotificationCompat.Builder(context, ClassAlarmScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_shikho_notification_small)
                    .setColor(0xFF0072EC.toInt())
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setSound(soundUri)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)

                val logoToUse = largeIconBitmap ?: appLogoBitmap
                if (logoToUse != null) {
                    builder.setLargeIcon(logoToUse)
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
            } catch (e: Throwable) {
                Log.e("ClassAlarmReceiver", "Error posting class alarm notification: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
