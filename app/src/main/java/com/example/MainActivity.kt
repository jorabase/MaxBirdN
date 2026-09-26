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
import com.example.auth.SessionManager
import com.example.notification.ClassAlarmScheduler
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

        // 1. Immediately create notification channel on startup
        ClassAlarmScheduler.createNotificationChannel(this)

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
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    android.util.Log.d("MainActivity", "Fetched FCM Token: $token")
                    val sessionManager = SessionManager(applicationContext)
                    sessionManager.setFcmToken(token)
                } else {
                    android.util.Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error fetching FCM token: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeState.value = isInPictureInPictureMode
    }
}
