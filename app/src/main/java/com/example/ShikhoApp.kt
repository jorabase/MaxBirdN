package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.notification.ClassAlarmScheduler
import com.example.security.AntiTamperSecurity
import com.google.firebase.FirebaseApp

class ShikhoApp : Application() {
    companion object {
        private const val TAG = "ShikhoApp"
        lateinit var instance: ShikhoApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AntiTamperSecurity.init(this)
        Log.d(TAG, "🚀 ShikhoApp onCreate initiated")

        // Automatically enforce FLAG_SECURE (Screenshot & Screen Record Protection) across all Activities
        // when BuildConfig.ENABLE_SCREENSHOT_PROTECTION is enabled at build time.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                AntiTamperSecurity.applyScreenshotProtection(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                AntiTamperSecurity.applyScreenshotProtection(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                AntiTamperSecurity.applyScreenshotProtection(activity)
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // 1. Ensure FirebaseApp is initialized immediately on app startup
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "✅ FirebaseApp initialized in Application.onCreate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize FirebaseApp in ShikhoApp: ${e.message}", e)
        }

        // 2. Ensure Notification Channels are created right when app launches
        ClassAlarmScheduler.createNotificationChannel(this)
        com.example.notification.ShikhoNotificationManager.createNotificationChannels(this)
        com.example.download.AppDownloadNotificationHelper.createNotificationChannel(this)
    }
}
