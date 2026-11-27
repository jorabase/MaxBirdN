package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.LocalPictureInPictureMode
import com.example.ui.theme.isAppInDarkTheme
import com.example.utils.NetworkUtils
import com.example.utils.tr
import kotlinx.coroutines.delay

/**
 * Lightweight, non-intrusive floating connectivity status pill.
 * Appears smoothly below the status bar when offline (dismissible for offline viewing)
 * and briefly confirms when internet connectivity is restored.
 */
@Composable
fun NetworkStatusBanner(
    modifier: Modifier = Modifier
) {
    val isPipMode = LocalPictureInPictureMode.current
    if (isPipMode) return

    val context = LocalContext.current
    val initialConnected = remember(context) { NetworkUtils.isNetworkConnected(context) }
    val connectivityFlow = remember(context) { NetworkUtils.observeNetworkConnectivity(context) }
    val isConnected by connectivityFlow.collectAsStateWithLifecycle(initialValue = initialConnected)

    var wasOffline by remember { mutableStateOf(!initialConnected) }
    var dismissedWhileOffline by remember { mutableStateOf(false) }
    var showBackOnlineBanner by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            wasOffline = true
            dismissedWhileOffline = false
            showBackOnlineBanner = false
        } else if (wasOffline) {
            wasOffline = false
            dismissedWhileOffline = false
            showBackOnlineBanner = true
            delay(2600L)
            showBackOnlineBanner = false
        }
    }

    val isVisible = (!isConnected && !dismissedWhileOffline) || showBackOnlineBanner
    val isDark = isAppInDarkTheme()

    val isOfflineState = !isConnected
    val accentColor = if (isOfflineState) {
        if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
    } else {
        if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    }
    val containerColor = if (isDark) {
        if (isOfflineState) Color(0xF0251218) else Color(0xF00F241D)
    } else {
        if (isOfflineState) Color(0xF7FEF2F2) else Color(0xF7ECFDF5)
    }
    val contentColor = if (isDark) {
        Color(0xFFF8FAFC)
    } else {
        if (isOfflineState) Color(0xFF7F1D1D) else Color(0xFF064E3B)
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
            .testTag("network_status_banner")
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            border = BorderStroke(1.dp, accentColor.copy(alpha = if (isDark) 0.45f else 0.35f)),
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = if (isOfflineState) 4.dp else 14.dp,
                    top = 6.dp,
                    bottom = 6.dp
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOfflineState) Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = if (isOfflineState) {
                        tr("No internet connection", "ইন্টারনেট সংযোগ বিচ্ছিন্ন")
                    } else {
                        tr("Back online", "ইন্টারনেট সংযোগ ফিরে এসেছে")
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isOfflineState) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { dismissedWhileOffline = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("network_status_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("Dismiss", "বন্ধ করুন"),
                            tint = contentColor.copy(alpha = 0.72f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
