package com.example

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.network.SharedHttpClient
import com.example.notification.ClassAlarmScheduler
import com.example.security.AntiTamperSecurity
import com.google.firebase.FirebaseApp

class ShikhoApp : Application(), ImageLoaderFactory {
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

        // 3. Initialize OneSignal + FCM Push Notification Registration & Sync
        com.example.notification.OneSignalPushManager.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { SharedHttpClient.baseClient }
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(60L * 1024L * 1024L)
                    .build()
            }
            .crossfade(200)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            runCatching {
                Coil.imageLoader(this).memoryCache?.clear()
            }
        }
    }
}
