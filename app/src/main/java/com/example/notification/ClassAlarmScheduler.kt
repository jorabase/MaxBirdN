package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.example.api.StudentLessonItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ClassAlarmScheduler {
    private const val TAG = "ClassAlarmScheduler"
    private const val PREFS_NAME = "shikho_alarm_scheduled_prefs"
    const val CHANNEL_ID = "shikho_class_alarms_channel"
    const val CHANNEL_NAME = "ক্লাস রিমাইন্ডার ও এলার্ম"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "নির্দিষ্ট সময়ে লাইভ ক্লাস শুরুর নোটিফিকেশন"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun schedule7DayClassAlarms(context: Context, programId: String, lessons: List<StudentLessonItem>) {
        createNotificationChannel(context)
        // 1. Cancel previous alarms first
        cancelAllAlarms(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val scheduledIds = mutableSetOf<Int>()

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentTime = System.currentTimeMillis()

        val sessionManager = com.example.auth.SessionManager(context)
        if (!sessionManager.isAllNotificationsEnabled()) {
            Log.d(TAG, "All notifications are turned off by user. Skipping alarms.")
            return
        }

        val leadTimeMinutes = sessionManager.getClassNotificationLeadTimeMinutes()
        val leadTimeMillis = leadTimeMinutes * 60 * 1000L
        val disabledAlarmIds = sessionManager.getDisabledAlarmIds()
        val isLiveEnabled = sessionManager.isLiveClassNotificationEnabled()
        val isExamEnabled = sessionManager.isExamNotificationEnabled()

        val timeFormatBn = SimpleDateFormat("hh:mm a", Locale("bn", "BD"))

        for ((index, lesson) in lessons.withIndex()) {
            val startTimeStr = lesson.start_time ?: lesson.live_class?.start_time ?: continue
            val lessonTitle = lesson.title ?: lesson.live_class?.chapter_name ?: "লাইভ ক্লাস"
            val subjectName = lesson.subject_name ?: lesson.live_class?.subject_name ?: "কোর্স"
            val lessonId = lesson.id.ifBlank { lesson.live_class?.id ?: "lesson_$index" }

            val isExam = lesson.isExam || lesson.content_type?.contains("Exam", ignoreCase = true) == true
            if (isExam && !isExamEnabled) continue
            if (!isExam && !isLiveEnabled) continue
            if (disabledAlarmIds.contains(lessonId)) continue

            try {
                val date = isoFormat.parse(startTimeStr) ?: continue
                val classStartTimeMillis = date.time
                val alarmTriggerTime = classStartTimeMillis - leadTimeMillis

                if (alarmTriggerTime > currentTime) {
                    val requestCode = (lessonId.hashCode() + index) % 1000000
                    scheduledIds.add(requestCode)

                    val classStartTimeDisplay = try {
                        val formatted = timeFormatBn.format(date)
                        formatted.replace("AM", "সকাল").replace("PM", "সন্ধ্যা/রাত")
                    } catch (_: Exception) {
                        "নির্দিষ্ট সময়ে"
                    }

                    val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
                        putExtra("subject_name", subjectName)
                        putExtra("lesson_title", lessonTitle)
                        putExtra("lesson_id", lessonId)
                        putExtra("class_start_time_str", classStartTimeDisplay)
                        putExtra("lead_time_minutes", leadTimeMinutes)
                    }

                    val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        pendingIntentFlags
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTriggerTime, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTriggerTime, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTriggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTriggerTime, pendingIntent)
                    }

                    Log.d(TAG, "⏰ Scheduled alarm ($leadTimeMinutes mins before) for $subjectName: $lessonTitle at ${Date(alarmTriggerTime)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing start time '$startTimeStr': ${e.message}")
            }
        }

        editor.putStringSet("scheduled_alarm_ids", scheduledIds.map { it.toString() }.toSet())
        editor.apply()
        Log.d(TAG, "✅ Scheduled ${scheduledIds.size} class alarms for program $programId")
    }

    fun scheduleCustomAlarm(context: Context, alarmId: String, title: String, body: String, triggerTimeMs: Long) {
        createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
            putExtra("custom_title", title)
            putExtra("custom_body", body)
            putExtra("lesson_id", alarmId)
        }

        val requestCode = alarmId.hashCode()
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }

        Log.d(TAG, "⏰ Scheduled custom alarm '$title' at ${Date(triggerTimeMs)}")
    }

    fun cancelCustomAlarm(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ClassAlarmReceiver::class.java)
        val requestCode = alarmId.hashCode()
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)
        try {
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }

    fun triggerInstantTestNotification(context: Context, title: String, body: String) {
        createNotificationChannel(context)
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)

        val mainIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setColor(0xFF0072EC.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending instant test notification: ${e.message}")
        }
    }

    fun cancelAllAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idsStr = prefs.getStringSet("scheduled_alarm_ids", emptySet()) ?: emptySet()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (idStr in idsStr) {
            val requestCode = idStr.toIntOrNull() ?: continue
            val intent = Intent(context, ClassAlarmReceiver::class.java)
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags)
            try {
                alarmManager.cancel(pendingIntent)
            } catch (_: Exception) {}
        }

        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ Cancelled all previous class alarms")
    }
}
