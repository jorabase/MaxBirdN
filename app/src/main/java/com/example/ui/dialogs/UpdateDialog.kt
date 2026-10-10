package com.example.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateRepository

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // If force update, intercept back button so user cannot bypass
    BackHandler(enabled = updateInfo.isForce) {
        // Do nothing, force update cannot be bypassed
    }

    Dialog(
        onDismissRequest = {
            if (!updateInfo.isForce) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.isForce,
            dismissOnClickOutside = !updateInfo.isForce
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = updateInfo.titleBn,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Version Tag
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "নতুন সংস্করণ: v${updateInfo.versionName} (Code: ${updateInfo.versionCode})",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Changelog Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "কী কী আপডেট করা হয়েছে:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateInfo.changelogBn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Button(
                    onClick = {
                        AppUpdateRepository.openDownloadUrl(context, updateInfo.downloadUrl)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "এখনই আপডেট করুন",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (!updateInfo.isForce) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "পরে করব",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "এটি একটি বাধ্যতামূলক আপডেট। চালিয়ে যেতে আপডেট করতে হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
