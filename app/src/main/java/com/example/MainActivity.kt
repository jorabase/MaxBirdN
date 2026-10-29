package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.auth.SessionManager
import com.example.notification.ClassAlarmScheduler
import com.example.notification.NotificationDeepLinkDispatcher
import com.example.notification.ShikhoFirebaseMessagingService
import com.example.notification.ShikhoNotificationManager
import com.example.security.AntiTamperSecurity
import com.example.security.SecurityViolation
import com.example.ui.dialogs.UpdateDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LocalPictureInPictureMode = compositionLocalOf { false }

class MainActivity : ComponentActivity() {

    val isPipModeState = mutableStateOf(false)
    private val activeSecurityViolationState = mutableStateOf<SecurityViolation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 0. Apply Screenshot & Screen Recording Protection (FLAG_SECURE) if enabled in BuildConfig
        AntiTamperSecurity.applyScreenshotProtection(this)

        super.onCreate(savedInstanceState)

        // 0.1 Perform initial Anti-Capture (Reqable/HttpCanary/Proxy) & Anti-Tamper inspection
        AntiTamperSecurity.exitIfTampered(this)
        activeSecurityViolationState.value = AntiTamperSecurity.inspectSecurityViolation(this)

        enableEdgeToEdge()

        // 1. Immediately create notification channels on startup
        ClassAlarmScheduler.createNotificationChannel(this)
        ShikhoNotificationManager.createNotificationChannels(this)

        // 1.1 Purge any bloated cache on background IO thread to prevent startup UI freeze
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.example.utils.AppCacheManager.autoTrimExcessiveCache(applicationContext)
            } catch (_: Throwable) {}
        }

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

            var startupUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
            val startupScope = rememberCoroutineScope()
            val currentViolation = activeSecurityViolationState.value

            // Continuous background watchdog for Reqable / HttpCanary / Proxy / Debugger activation while app is running
            LaunchedEffect(Unit) {
                while (true) {
                    val detected = withContext(Dispatchers.IO) {
                        AntiTamperSecurity.inspectSecurityViolation(applicationContext)
                    }
                    activeSecurityViolationState.value = detected
                    delay(4000L)
                }
            }

            LaunchedEffect(Unit) {
                startupScope.launch(Dispatchers.IO) {
                    val info = AppUpdateRepository.fetchLatestUpdate()
                    if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                        withContext(Dispatchers.Main) {
                            startupUpdateInfo = info
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalPictureInPictureMode provides isPipModeState.value) {
                MyApplicationTheme(darkTheme = isDarkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (currentViolation != null) {
                                SecurityViolationShieldScreen(
                                    violation = currentViolation,
                                    onRecheck = {
                                        AntiTamperSecurity.clearTransientCaptureAlert()
                                        activeSecurityViolationState.value =
                                            AntiTamperSecurity.inspectSecurityViolation(this@MainActivity)
                                    },
                                    onExitApp = {
                                        finishAffinity()
                                    }
                                )
                            } else {
                                AppNavigation()

                                startupUpdateInfo?.let { updateInfo ->
                                    UpdateDialog(
                                        updateInfo = updateInfo,
                                        onDismiss = {
                                            if (!updateInfo.isForce) {
                                                startupUpdateInfo = null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        AntiTamperSecurity.applyScreenshotProtection(this)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            val restoreIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(restoreIntent)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Handle downloads notification tap
        if (intent.getStringExtra("OPEN_SCREEN") == "DOWNLOADS") {
            NotificationDeepLinkDispatcher.setPendingDeepLink("downloads")
            return
        }

        // 2. Handle class alarm notification tap
        val lessonId = intent.getStringExtra("lesson_id")
        if (!lessonId.isNullOrBlank()) {
            NotificationDeepLinkDispatcher.setPendingDeepLink("lesson_player?lesson_id=$lessonId")
            return
        }

        // 3. Handle push notification deep links
        val url = intent.getStringExtra(ShikhoFirebaseMessagingService.EXTRA_NOTIFICATION_URL)
            ?: intent.getStringExtra(ShikhoFirebaseMessagingService.EXTRA_NOTIFICATION_DEEPLINK)
            ?: intent.dataString

        if (!url.isNullOrBlank()) {
            android.util.Log.d("MainActivity", "Handling notification intent URL: $url")
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                } catch (e: Throwable) {
                    android.util.Log.e("MainActivity", "Failed to open external URL: ${e.message}", e)
                }
            } else {
                NotificationDeepLinkDispatcher.setPendingDeepLink(url)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AntiTamperSecurity.applyScreenshotProtection(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isPipModeState.value = isInPictureInPictureMode
        }
        // Anti-tamper & Anti-Reqable verification on every foreground resume
        AntiTamperSecurity.exitIfTampered(this)
        activeSecurityViolationState.value = AntiTamperSecurity.inspectSecurityViolation(this)

        // Silent heartbeat verification with Supabase
        try {
            if (com.example.security.DeviceActivationRepository.isConfigured() &&
                com.example.security.DeviceActivationRepository.isDeviceActivated(this)
            ) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val result = com.example.security.DeviceActivationRepository.verifyHeartbeat(applicationContext)
                        if (result is com.example.security.HeartbeatResult.Revoked) {
                            com.example.security.DeviceActivationRepository.clearActivation(applicationContext)
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                recreate()
                            }
                        }
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

@Composable
private fun SecurityViolationShieldScreen(
    violation: SecurityViolation,
    onRecheck: () -> Unit,
    onExitApp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0F172A))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security Shield",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = violation.titleBn,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = violation.messageBn,
                    fontSize = 14.sp,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "শনাক্তকৃত কারণ: ${violation.detectedDetail}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFCA5A5),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onRecheck,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("security_recheck_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("পুনরায় যাচাই করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onExitApp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("security_exit_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("অ্যাপ বন্ধ করুন", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
