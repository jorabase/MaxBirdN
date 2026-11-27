package com.example.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.theme.isAppInDarkTheme
import com.example.update.ApkUpdateDownloadState
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateRepository
import com.example.update.InAppApkUpdater
import com.example.utils.tr

/**
 * Full-Screen Global Kill Switch / Maintenance Mode Shield.
 * When `updateInfo.isKillSwitchActive == true`, the app is completely locked for all users
 * and displays an "অ্যাপ আপডেটের কাজ চলছে" screen until the Admin turns the kill switch OFF.
 */
@Composable
fun KillSwitchMaintenanceScreen(
    updateInfo: AppUpdateInfo,
    isRechecking: Boolean = false,
    onRecheck: () -> Unit,
    onExitApp: () -> Unit
) {
    BackHandler(enabled = true) {
        // Cannot bypass global kill switch
    }

    val infiniteTransition = rememberInfiniteTransition(label = "MaintenancePulse")
    val iconRotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IconRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070D19),
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF070D19)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2D)),
            border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0284C7), Color(0xFF2563EB), Color(0xFF7C3AED))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Maintenance Mode",
                        tint = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer { rotationZ = iconRotation }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
                ) {
                    Text(
                        text = tr("System Maintenance", "সিস্টেম মেইনটেন্যান্স চলছে"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = updateInfo.killSwitchTitleBn.ifBlank { tr("Maintenance in Progress", "অ্যাপ আপডেটের কাজ চলছে") },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = updateInfo.killSwitchMessageBn,
                    fontSize = 14.sp,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )

                if (!updateInfo.estimatedBackTimeBn.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr(
                                    "Estimated return: ${updateInfo.estimatedBackTimeBn}",
                                    "সম্ভাব্য চালুর সময়: ${updateInfo.estimatedBackTimeBn}"
                                ),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onRecheck,
                    enabled = !isRechecking,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("kill_switch_recheck_button")
                ) {
                    if (isRechecking) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(tr("Checking server...", "সার্ভার চেক করা হচ্ছে..."), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr("Try Again", "পুনরায় চেষ্টা"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onExitApp,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF475569)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("kill_switch_exit_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("Exit App", "অ্যাপ বন্ধ করুন"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * In-App Direct APK Auto-Update Dialog.
 * Downloads the APK directly inside the app with live progress (%, MB, speed)
 * and automatically triggers the Android Package Installer upon completion.
 */
@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = isAppInDarkTheme()
    val isDowngrade = AppUpdateRepository.isDowngradeRequired(updateInfo, BuildConfig.VERSION_CODE)
    val downloadState by InAppApkUpdater.downloadState.collectAsStateWithLifecycle()

    // Ensure state is synchronized with the current target version & URL and verify cached APK still exists
    LaunchedEffect(updateInfo.versionCode, updateInfo.versionName, updateInfo.downloadUrl) {
        InAppApkUpdater.syncDialogForTarget(context, updateInfo)
    }

    // Resume installation automatically when returning from Android "Install Unknown Apps" settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = InAppApkUpdater.downloadState.value
                if (current is ApkUpdateDownloadState.PermissionRequired && InAppApkUpdater.canInstallPackages(context)) {
                    InAppApkUpdater.triggerInstallOrRequestPermission(context, current.apkFile)
                } else {
                    InAppApkUpdater.syncDialogForTarget(context, updateInfo)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Strict version enforcement: intercept back button unconditionally so old versions cannot be used
    BackHandler(enabled = true) {
        // Mandatory version enforcement cannot be bypassed
    }

    val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
    val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)

    Dialog(
        onDismissRequest = {
            // Never allow dismissing when version does not match database
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = dialogBg,
            tonalElevation = 10.dp,
            border = BorderStroke(
                1.dp,
                if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Gradient Icon Badge
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0284C7), Color(0xFF2563EB), Color(0xFF4F46E5))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update Icon",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = if (isDowngrade) {
                        tr("Required Version", "নির্ধারিত ভার্সন")
                    } else {
                        updateInfo.titleBn.ifBlank { tr("App Update", "অ্যাপ আপডেট") }
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center,
                    color = primaryText
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = tr(
                        "The new version downloads directly inside the app and cleans up the APK cache automatically.",
                        "নতুন সংস্করণ অ্যাপের ভেতরেই ডাউনলোড হয়ে আপডেট হবে এবং শেষে APK ক্যাশ নিজে থেকেই ক্লিয়ার হবে।"
                    ),
                    fontSize = 12.sp,
                    color = secondaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Current vs Required Database Version Comparison Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tr("Current", "বর্তমান"),
                                fontSize = 10.5.sp,
                                color = secondaryText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tr("Required", "নির্ধারিত"),
                                fontSize = 10.5.sp,
                                color = secondaryText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v${updateInfo.versionName} (${updateInfo.versionCode})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Changelog Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("What's New:", "নতুন পরিবর্তনসমূহ:"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0284C7)
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = updateInfo.changelogBn,
                            fontSize = 12.5.sp,
                            color = primaryText.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live In-App Download & Auto-Install State Area
                when (val state = downloadState) {
                    is ApkUpdateDownloadState.Idle -> {
                        Button(
                            onClick = {
                                InAppApkUpdater.startDownloadAndInstall(context, updateInfo, forceRedownload = false)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("in_app_update_start_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = updateInfo.buttonText.ifBlank {
                                    tr(
                                        "Update Now (v${updateInfo.versionName})",
                                        "আপডেট করুন (v${updateInfo.versionName})"
                                    )
                                },
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    is ApkUpdateDownloadState.Downloading -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tr("Downloading update...", "আপডেট ডাউনলোড হচ্ছে..."),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Text(
                                        text = if (state.progressPercent >= 0) "${state.progressPercent}%" else "...",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0284C7)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (state.progressPercent >= 0) {
                                    LinearProgressIndicator(
                                        progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                                        color = Color(0xFF0284C7),
                                        trackColor = Color(0xFF0284C7).copy(alpha = 0.18f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(9.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = Color(0xFF0284C7),
                                        trackColor = Color(0xFF0284C7).copy(alpha = 0.18f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(9.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val downloadedText = InAppApkUpdater.formatMegabytes(state.downloadedBytes)
                                    val totalText = if (state.totalBytes > 0L) {
                                        " / ${InAppApkUpdater.formatMegabytes(state.totalBytes)}"
                                    } else ""
                                    Text(
                                        text = "$downloadedText$totalText",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    val speedText = InAppApkUpdater.formatSpeed(state.speedBytesPerSec)
                                    if (speedText.isNotBlank()) {
                                        Text(
                                            text = speedText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is ApkUpdateDownloadState.Verifying -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0284C7),
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = tr("Verifying APK & preparing installer...", "APK যাচাই ও ইনস্টলার প্রস্তুত হচ্ছে..."),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                            }
                        }
                    }

                    is ApkUpdateDownloadState.PermissionRequired -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tr(
                                    "Please enable 'Install Unknown Apps' permission once to install the update:",
                                    "আপডেট ইনস্টল করতে একবার 'Install Unknown Apps' পারমিশন অন করুন:"
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    InAppApkUpdater.openInstallPermissionSettings(context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(19.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Allow & Install", "পারমিশন দিয়ে ইনস্টল করুন"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    is ApkUpdateDownloadState.ReadyToInstall,
                    is ApkUpdateDownloadState.Installing -> {
                        val apkFile = when (state) {
                            is ApkUpdateDownloadState.ReadyToInstall -> state.apkFile
                            is ApkUpdateDownloadState.Installing -> state.apkFile
                            else -> null
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr("Download complete! Tap to install:", "ডাউনলোড সম্পন্ন! ইনস্টল করতে ট্যাপ করুন:"),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (apkFile != null && apkFile.exists()) {
                                        InAppApkUpdater.triggerInstallOrRequestPermission(context, apkFile)
                                    } else {
                                        InAppApkUpdater.startDownloadAndInstall(context, updateInfo, forceRedownload = true)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Install Now", "এখনই ইনস্টল করুন"),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    InAppApkUpdater.startDownloadAndInstall(context, updateInfo, forceRedownload = true)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr("Download Again", "পুনরায় ডাউনলোড"),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        }
                    }

                    is ApkUpdateDownloadState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = state.messageBn,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    InAppApkUpdater.startDownloadAndInstall(context, updateInfo, forceRedownload = true)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Retry Download", "পুনরায় চেষ্টা করুন"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            if (state.fallbackUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        AppUpdateRepository.openDownloadUrl(context, state.fallbackUrl)
                                    }
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tr("Open Mirror Link", "বিকল্প লিংক খুলুন"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = tr(
                        "Version v${updateInfo.versionName} (${updateInfo.versionCode}) is required to continue.",
                        "অ্যাপ ব্যবহার চালিয়ে যেতে v${updateInfo.versionName} (${updateInfo.versionCode}) ইনস্টল করতে হবে।"
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEF4444),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
