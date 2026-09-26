package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class CustomAlarmItem(
    val id: String,
    val title: String,
    val note: String = "",
    val triggerTimeMillis: Long,
    val isEnabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val isPassed: Boolean
        get() = System.currentTimeMillis() > triggerTimeMillis

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("note", note)
            put("triggerTimeMillis", triggerTimeMillis)
            put("isEnabled", isEnabled)
            put("createdAtMillis", createdAtMillis)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CustomAlarmItem? {
            return try {
                CustomAlarmItem(
                    id = json.getString("id"),
                    title = json.getString("title"),
                    note = json.optString("note", ""),
                    triggerTimeMillis = json.getLong("triggerTimeMillis"),
                    isEnabled = json.optBoolean("isEnabled", true),
                    createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

object CustomAlarmScheduler {
    private const val TAG = "CustomAlarmScheduler"
    private const val PREFS_NAME = "custom_alarms_prefs"
    private const val KEY_ALARMS = "saved_custom_alarms"

    fun getCustomAlarms(context: Context): List<CustomAlarmItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        val list = mutableListOf<CustomAlarmItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                CustomAlarmItem.fromJson(obj)?.let { list.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading custom alarms: ${e.message}")
        }
        return list.sortedBy { it.triggerTimeMillis }
    }

    fun saveCustomAlarms(context: Context, list: List<CustomAlarmItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    fun addCustomAlarm(context: Context, item: CustomAlarmItem) {
        val current = getCustomAlarms(context).toMutableList()
        current.removeAll { it.id == item.id }
        current.add(item)
        saveCustomAlarms(context, current)
        if (item.isEnabled && item.triggerTimeMillis > System.currentTimeMillis()) {
            scheduleAlarm(context, item)
        }
    }

    fun deleteCustomAlarm(context: Context, id: String) {
        cancelAlarm(context, id)
        val current = getCustomAlarms(context).toMutableList()
        current.removeAll { it.id == id }
        saveCustomAlarms(context, current)
    }

    fun toggleCustomAlarm(context: Context, id: String, isEnabled: Boolean) {
        val current = getCustomAlarms(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(isEnabled = isEnabled)
            current[index] = updated
            saveCustomAlarms(context, current)
            if (isEnabled && updated.triggerTimeMillis > System.currentTimeMillis()) {
                scheduleAlarm(context, updated)
            } else {
                cancelAlarm(context, id)
            }
        }
    }

    fun scheduleAlarm(context: Context, item: CustomAlarmItem) {
        ClassAlarmScheduler.createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val requestCode = (item.id.hashCode() and 0x7FFFFFFF) % 1000000

        val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
            putExtra("is_custom", true)
            putExtra("custom_id", item.id)
            putExtra("custom_title", item.title)
            putExtra("custom_note", item.note)
            putExtra("trigger_time", item.triggerTimeMillis)
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        item.triggerTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        item.triggerTimeMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    item.triggerTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    item.triggerTimeMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "⏰ Scheduled custom alarm '${item.title}' at ${item.triggerTimeMillis}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule custom alarm: ${e.message}")
        }
    }

    fun cancelAlarm(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = (id.hashCode() and 0x7FFFFFFF) % 1000000
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

    fun rescheduleAllActive(context: Context) {
        val alarms = getCustomAlarms(context)
        val now = System.currentTimeMillis()
        alarms.filter { it.isEnabled && it.triggerTimeMillis > now }.forEach {
            scheduleAlarm(context, it)
        }
    }
}
