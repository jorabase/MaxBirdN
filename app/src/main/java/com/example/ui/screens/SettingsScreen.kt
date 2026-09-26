package com.example.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import com.example.MainActivity
import com.example.auth.SessionManager
import com.example.utils.AvatarUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangeSyllabus: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCourseEnrollment: () -> Unit = {},
    onNavigateToSavedItems: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToReportCard: () -> Unit = {},
    onNavigateToNotification: () -> Unit = {},
    onNavigateToHeaderWallpaper: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentThemeMode by sessionManager.themeModeFlow.collectAsState()
    val currentLeadTime by sessionManager.classNotificationLeadTimeFlow.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val profileUpdated by sessionManager.userProfileUpdateFlow.collectAsState()
    val userAvatarFlowValue by sessionManager.userAvatarFlow.collectAsState()
    val userAvatar = userAvatarFlowValue ?: sessionManager.getUserAvatar()
    val userName = remember(profileUpdated) { sessionManager.getUserFullName() ?: "শিক্ষার্থী" }
    val userClass = remember(profileUpdated) { sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "একাদশ শ্রেণি" }
    val userGroup = remember(profileUpdated) { sessionManager.getUserGroup() ?: "মানবিক" }
    val userBatch = remember(profileUpdated) { sessionManager.getUserBatchId() ?: "" }

    val classDisplay = remember(userClass) {
        when (userClass) {
            "C11", "Class 11" -> "একাদশ শ্রেণি"
            "C12", "Class 12" -> "দ্বাদশ শ্রেণি"
            "C10", "Class 10" -> "দশম শ্রেণি"
            "C9", "Class 9" -> "নবম শ্রেণি"
            else -> userClass.ifBlank { "একাদশ শ্রেণি" }
        }
    }
    val groupDisplay = remember(userGroup) {
        when (userGroup.lowercase()) {
            "humanities", "humanities_group", "hum" -> "মানবিক"
            "science", "science_group", "sci" -> "বিজ্ঞান"
            "business", "business_studies", "commerce", "bs" -> "ব্যবসায় শিক্ষা"
            else -> userGroup
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "সেটিংস",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Profile Card Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigateToEditProfile() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    )
                ),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            val fallbackInitial = remember(userName) {
                                userName.trim().firstOrNull()?.toString()?.uppercase() ?: "U"
                            }
                            val imageRequest = remember(userAvatar, userName, context) {
                                AvatarUtils.buildImageRequest(context, userAvatar, userName)
                            }

                            SubcomposeAsyncImage(
                                model = imageRequest,
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = fallbackInitial,
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.secondary
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = fallbackInitial,
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = listOf(classDisplay, groupDisplay).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Academic Settings Group
            SettingsSection(title = "একাডেমিক সেটিংস") {
                // 1. প্রোফাইল এডিট (Edit Profile - above Change Syllabus)
                SettingsRowItem(
                    icon = Icons.Default.Edit,
                    iconTint = Color(0xFF7C3AED),
                    iconBg = Color(0xFFEDE9FE),
                    title = "প্রোফাইল এডিট",
                    subtitle = "ব্যক্তিগত ও শিক্ষাপ্রতিষ্ঠানের তথ্য পরিবর্তন করো",
                    badge = "এডিট",
                    onClick = onNavigateToEditProfile
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 2. সিলেবাস পরিবর্তন (Change Syllabus)
                SettingsRowItem(
                    icon = Icons.Default.SwapCalls,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    title = "সিলেবাস পরিবর্তন",
                    subtitle = listOf(classDisplay, groupDisplay, userBatch).filter { it.isNotBlank() }.joinToString(" • "),
                    badge = "পরিবর্তন",
                    onClick = onNavigateToChangeSyllabus
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 3. প্রোফাইল বিবরণ
                SettingsRowItem(
                    icon = Icons.Default.Person,
                    iconTint = Color(0xFF0284C7),
                    iconBg = Color(0xFFE0F2FE),
                    title = "প্রোফাইল বিবরণ",
                    subtitle = "নাম, প্রতিষ্ঠান ও অ্যাকাউন্টের তথ্য",
                    onClick = onNavigateToProfile
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 4. কোর্সে ভর্তি (Course Enrollment / Admission Details)
                SettingsRowItem(
                    icon = Icons.Default.CardMembership,
                    iconTint = Color(0xFF16A34A),
                    iconBg = Color(0xFFDCFCE7),
                    title = "কোর্সে ভর্তি",
                    subtitle = "ভর্তি হওয়া কোর্স, কোয়ার্টার ও মেয়াদের বিবরণ",
                    badge = "বিস্তারিত",
                    onClick = onNavigateToCourseEnrollment
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 5. সংরক্ষিত আইটেম (Saved Items)
                SettingsRowItem(
                    icon = Icons.Default.Bookmark,
                    iconTint = Color(0xFFF59E0B),
                    iconBg = Color(0xFFFEF3C7),
                    title = "সংরক্ষিত আইটেম",
                    subtitle = "বুকমার্ক করা প্রশ্ন ও গুরুত্বপূর্ণ স্টাডি ম্যাটেরিয়াল",
                    badge = "সংরক্ষিত",
                    onClick = onNavigateToSavedItems
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 6. অফলাইন ডাউনলোড (Offline Downloads)
                SettingsRowItem(
                    icon = Icons.Default.DownloadDone,
                    iconTint = Color(0xFF10B981),
                    iconBg = Color(0xFFD1FAE5),
                    title = "অফলাইন ডাউনলোড",
                    subtitle = "ইন্টারনেট ছাড়া সংরক্ষিত ভিডিও ও পিডিএফ লেকচার",
                    badge = "অফলাইন",
                    onClick = onNavigateToDownloads
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // 7. রিপোর্ট কার্ড ও লিডারবোর্ড (Report Card & Leaderboard)
                SettingsRowItem(
                    icon = Icons.Default.Assessment,
                    iconTint = Color(0xFF2563EB),
                    iconBg = Color(0xFFEFF6FF),
                    title = "রিপোর্ট কার্ড ও লিডারবোর্ড",
                    subtitle = "কোয়ার্টার পারফরম্যান্স ট্রেন্ড ও বিষয়ভিত্তিক মেধা তালিকা",
                    badge = "নতুন",
                    onClick = onNavigateToReportCard
                )
            }

            // App Preferences & Theme Section
            SettingsSection(title = "অ্যাপ প্রেফারেন্স ও নোটিফিকেশন") {
                // হেডার ও লাইভ ওয়ালপেপার
                SettingsRowItem(
                    icon = Icons.Default.Wallpaper,
                    iconTint = Color(0xFFEC4899),
                    iconBg = Color(0xFFFCE7F3),
                    title = "হেডার ও লাইভ ওয়ালপেপার",
                    subtitle = "হোম পেজের ব্যাকগ্রাউন্ড থিম ও নিজস্ব ওয়ালপেপার পরিবর্তন",
                    badge = "থিম",
                    onClick = onNavigateToHeaderWallpaper
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                SettingsRowItem(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = Color(0xFFD97706),
                    iconBg = Color(0xFFFEF3C7),
                    title = "নোটিফিকেশন ও ক্লাস অ্যালার্ম",
                    subtitle = "তারিখ অনুযায়ী সেট করা ক্লাস অ্যালার্ম ও $currentLeadTime মি. আগের রিমাইন্ডার",
                    badge = "নোটিফিকেশন",
                    onClick = onNavigateToNotification
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                SettingsRowItem(
                    icon = Icons.Default.DarkMode,
                    iconTint = Color(0xFF6366F1),
                    iconBg = Color(0xFFEEF2FF),
                    title = "অ্যাপ থিম (Dark & Light Mode)",
                    subtitle = when (currentThemeMode) {
                        "light" -> "লাইট মোড"
                        "dark" -> "ডার্ক মোড"
                        else -> "সিস্টেম ডিফল্ট (মোবাইল অনুযায়ী)"
                    },
                    badge = when (currentThemeMode) {
                        "light" -> "Light"
                        "dark" -> "Dark"
                        else -> "System"
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            // Account & Preferences Section
            SettingsSection(title = "অ্যাকাউন্ট ও নিরাপত্তা") {
                // Logout
                SettingsRowItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    title = "লগআউট",
                    subtitle = "অ্যাকাউন্ট থেকে লগআউট করো",
                    onClick = onLogout,
                    isDestructive = true
                )
            }

            // App Version Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MaxBird App v6.0.5",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "অ্যাপ থিম নির্বাচন করুন",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("system", "সিস্টেম ডিফল্ট (মোবাইল অনুযায়ী)", "মোবাইলের সিস্টেম থিম সেটিংস অনুযায়ী স্বয়ংক্রিয়ভাবে পরিবর্তিত হবে"),
                        Triple("light", "লাইট মোড", "উজ্জ্বল ও পরিষ্কার সাদা থিম"),
                        Triple("dark", "ডার্ক মোড", "চোখের জন্য আরামদায়ক ডার্ক থিম")
                    ).forEach { (modeKey, modeTitle, modeDesc) ->
                        val isSelected = currentThemeMode == modeKey
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    sessionManager.setThemeMode(modeKey)
                                    showThemeDialog = false
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        sessionManager.setThemeMode(modeKey)
                                        showThemeDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = modeTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = modeDesc,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("ঠিক আছে")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.3.sp
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}
