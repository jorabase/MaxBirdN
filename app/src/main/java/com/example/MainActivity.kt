package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import com.example.auth.SessionManager
import com.example.notification.ClassAlarmScheduler
import com.example.notification.NotificationDeepLinkDispatcher
import com.example.notification.ShikhoFirebaseMessagingService
import com.example.notification.ShikhoNotificationManager
import com.example.security.AntiTamperSecurity
import com.example.ui.theme.MyApplicationTheme

val LocalPictureInPictureMode = compositionLocalOf { false }

class MainActivity : ComponentActivity() {

    val isPipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0. Anti-Tamper & Security Verification against MT Manager / Frida / Hooking
        AntiTamperSecurity.exitIfTampered(this)

        enableEdgeToEdge()

        // 1. Immediately create notification channels on startup
        ClassAlarmScheduler.createNotificationChannel(this)
        ShikhoNotificationManager.createNotificationChannels(this)

        // 1.1 Immediately purge any bloated cache to protect phone storage
        com.example.utils.AppCacheManager.autoTrimExcessiveCache(this)

        // 2. Verify Firebase is initialized correctly
        try {
            val apps = com.google.firebase.FirebaseApp.getApps(this)
            if (apps.isEmpty()) {
                android.util.Log.e("MainActivity", "❌ Firebase NOT initialized!")
            } else {
                val projectId = com.google.firebase.FirebaseApp.getInstance().options.projectId
                android.util.Log.d("MainActivity", "✅ Firebase Project: $projectId")
                if (projectId != "shikho-tech") {
                    android.util.Log.e("MainActivity", "❌ Wrong Firebase project! Expected: shikho-tech, Got: $projectId")
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Firebase check failed: ${e.message}", e)
        }

        requestNotificationPermissionOnStartup()
        fetchAndRegisterFcmToken()
        handleNotificationIntent(intent)

        setContent {
            val sessionManager = remember { SessionManager(applicationContext) }
            val themeMode by sessionManager.themeModeFlow.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemInDark
            }

            CompositionLocalProvider(LocalPictureInPictureMode provides isPipModeState.value) {
                MyApplicationTheme(darkTheme = isDarkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavigation()
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionOnStartup() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        101
                    )
                }
            }
        } catch (_: Throwable) {
            // Ignore runtime permission dispatch issues on startup
        }
    }

    private fun fetchAndRegisterFcmToken() {
        try {
            if (!com.example.notification.ShikhoNotificationManager.isGooglePlayServicesAvailable(this)) {
                android.util.Log.i("MainActivity", "Google Play Services unavailable on this environment. Skipping FCM registration.")
                return
            }

            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    android.util.Log.d("MainActivity", "Fetched FCM Token: $token")
                    val sessionManager = SessionManager(applicationContext)
                    sessionManager.setFcmToken(token)

                    // Immediately sync topic subscriptions (SHIKHO_ALL, user, program, phase)
                    ShikhoNotificationManager.syncAllTopicSubscriptions(applicationContext)
                } else {
                    android.util.Log.i("MainActivity", "FCM registration token deferred/unavailable in current environment")
                }
            }
        } catch (e: Throwable) {
            android.util.Log.i("MainActivity", "FCM token registration deferred: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)

        // Seamless PiP to Fullscreen Transition:
        // If the user clicks the app icon or re-enters the app from launcher / recents while PiP is active,
        // bring the Activity back out of PiP mode to the foreground immediately!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            val restoreIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(restoreIntent)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val url = intent.getStringExtra(ShikhoFirebaseMessagingService.EXTRA_NOTIFICATION_URL)
            ?: intent.getStringExtra(ShikhoFirebaseMessagingService.EXTRA_NOTIFICATION_DEEPLINK)
            ?: intent.dataString

        if (!url.isNullOrBlank()) {
            android.util.Log.d("MainActivity", "Handling notification intent URL: $url")
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                // External website (e.g. admission form or registration) - open in browser
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                } catch (e: Throwable) {
                    android.util.Log.e("MainActivity", "Failed to open external URL: ${e.message}", e)
                }
            } else {
                // In-app deep link
                NotificationDeepLinkDispatcher.setPendingDeepLink(url)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isPipModeState.value = isInPictureInPictureMode
        }
        // Anti-tamper verification on every foreground resume
        AntiTamperSecurity.exitIfTampered(this)

        // Silent heartbeat verification with Supabase
        try {
            if (com.example.security.DeviceActivationRepository.isConfigured() &&
                com.example.security.DeviceActivationRepository.isDeviceActivated(this)
            ) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        com.example.security.DeviceActivationRepository.verifyHeartbeat(applicationContext)
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        @Suppress("DEPRECATION")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isPipModeState.value = isInPictureInPictureMode
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeState.value = isInPictureInPictureMode
    }
}
