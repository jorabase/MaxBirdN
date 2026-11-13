package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.BuildConfig
import com.example.auth.SessionManager
import com.example.course.CourseViewModel
import com.example.home.HomeViewModel
import com.example.player.EqualizerManager
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.dialogs.UpdateDialog
import com.example.ui.theme.HeaderWallpaperConfig
import com.example.ui.theme.HeaderWallpaperPresets
import com.example.ui.theme.isAppInDarkTheme
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateRepository
import com.example.utils.AppCacheManager
import com.example.utils.AvatarUtils
import com.example.utils.CacheBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionManager: SessionManager = SessionManager(LocalContext.current),
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangeSyllabus: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToCourseEnrollment: () -> Unit = {},
    onNavigateToSavedItems: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToReportCard: () -> Unit = {},
    onNavigateToNotification: () -> Unit = {},
    onNavigateToHeaderWallpaper: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isAppInDarkTheme()

    val currentThemeMode by sessionManager.themeModeFlow.collectAsState()
    val currentLeadTime by sessionManager.classNotificationLeadTimeFlow.collectAsState()
    val currentWallpaper by sessionManager.headerWallpaperFlow.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    var cacheBreakdown by remember {
        mutableStateOf(
            CacheBreakdown(
                mediaStreamBytes = 0L,
                pdfPreviewBytes = 0L,
                imageAndNetworkBytes = 0L,
                totalBytes = 0L
            )
        )
    }
    var isClearingCache by remember { mutableStateOf(false) }

    // App Update Checker State
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var fetchedUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpToDateDialog by remember { mutableStateOf(false) }
    var verifiedDatabaseVersionText by remember { mutableStateOf<String?>(null) }

    // Refresh cache breakdown on entry
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cacheBreakdown = AppCacheManager.getCacheBreakdown(context)
        }
    }

    // Reactive profile fields
    val profileUpdateTick by sessionManager.userProfileUpdateFlow.collectAsState()
    val avatarFromFlow by sessionManager.userAvatarFlow.collectAsState()

    val fullName = remember(profileUpdateTick) {
        sessionManager.getUserFullName()
            ?: sessionManager.getUserFirstName()?.takeIf { it.isNotBlank() }
            ?: "শিক্ষার্থী"
    }
    val phone = remember(profileUpdateTick) {
        sessionManager.getUserPhone() ?: ""
    }
    val schoolName = remember(profileUpdateTick) {
        sessionManager.getUserSchoolName() ?: ""
    }
    val classDisplay = remember(profileUpdateTick) {
        sessionManager.getUserClassDisplay()
            ?: sessionManager.getUserClassName()
            ?: "HSC"
    }
    val groupDisplay = remember(profileUpdateTick) {
        val raw = sessionManager.getUserGroup() ?: ""
        when (raw.lowercase()) {
            "science" -> "বিজ্ঞান বিভাগ"
            "humanities", "arts" -> "মানবিক বিভাগ"
            "business", "commerce", "business studies" -> "ব্যবসায় শিক্ষা"
            else -> raw.ifBlank { "বিজ্ঞান বিভাগ" }
        }
    }
    val activeCourseTitle = remember(profileUpdateTick) {
        sessionManager.getActiveProgramTitleBn() ?: "অ্যাকাডেমিক প্রোগ্রাম"
    }
    val avatarUrl = remember(avatarFromFlow, fullName) {
        val candidate = avatarFromFlow ?: sessionManager.getUserAvatar()
        AvatarUtils.formatAvatarUrl(candidate, fullName)
    }

    val activeWallpaperBadge = remember(currentWallpaper) {
        when (currentWallpaper.type) {
            HeaderWallpaperConfig.CUSTOM_CODE -> currentWallpaper.customTitle ?: "কাস্টম কোড"
            HeaderWallpaperConfig.CUSTOM_IMAGE -> "গ্যালারি ছবি"
            else -> HeaderWallpaperPresets.findById(currentWallpaper.type).nameBn.split(" ").firstOrNull() ?: "থিম"
        }
    }

    val activeEqualizerBadge = remember(showEqualizerSheet) {
        EqualizerManager.getActiveStatusLabel(context)
    }

    val pageBg = if (isDark) Color(0xFF070D19) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val cardBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "সেটিংস ও প্রেফারেন্স",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                            Text(
                                text = "অ্যাকাউন্ট ও অ্যাপ কাস্টমাইজেশন",
                                fontSize = 11.5.sp,
                                color = secondaryText
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cardBg
                )
            )
        },
        containerColor = pageBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Premium Student Profile Hero Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfile() }
                    .testTag("settings_profile_hero_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E3A8A),
                                    Color(0xFF0284C7)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, Color(0xFF38BDF8), CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    error = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = fullName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (phone.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = phone,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = classDisplay,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFBAE6FD),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = groupDisplay,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFDE68A),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            FilledTonalIconButton(
                                onClick = onNavigateToEditProfile,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.18f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Active Program Pill inside Hero Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeCourseTitle,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (schoolName.isNotBlank()) schoolName else "সক্রিয় ব্যাচ",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7DD3FC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Academic & Learning Section
            SettingsSectionGroup(
                title = "অ্যাকাডেমিক ও লার্নিং পোর্টাল",
                icon = Icons.Default.AutoStories,
                isDark = isDark
            ) {
                SettingsActionRow(
                    icon = Icons.Default.PersonOutline,
                    iconTint = Color(0xFF0284C7),
                    title = "আমার প্রোফাইল ও একাডেমিক তথ্য",
                    subtitle = "নাম, প্রতিষ্ঠান, শ্রেণি ও প্রোফাইল তথ্য দেখুন",
                    badgeText = "প্রোফাইল",
                    isDark = isDark,
                    onClick = onNavigateToProfile,
                    testTag = "settings_row_profile"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                SettingsActionRow(
                    icon = Icons.Default.WorkspacePremium,
                    iconTint = Color(0xFF7C3AED),
                    title = "ব্যাচ ও সিলেবাস পরিবর্তন",
                    subtitle = "সক্রিয় প্রোগ্রাম পরিবর্তন ও বিষয়ভিত্তিক সিলেবাস",
                    badgeText = "ব্যাচ",
                    isDark = isDark,
                    onClick = onNavigateToChangeSyllabus,
                    testTag = "settings_row_my_courses"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                SettingsActionRow(
                    icon = Icons.Default.Explore,
                    iconTint = Color(0xFF059669),
                    title = "সকল কোর্স ও ক্যাটালগ দেখুন",
                    subtitle = "নতুন কোর্স, ফ্রি ট্রায়াল ও বিষয়ভিত্তিক লেকচার",
                    badgeText = "ক্যাটালগ",
                    isDark = isDark,
                    onClick = onNavigateToCourseEnrollment,
                    testTag = "settings_row_course_catalog"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                SettingsActionRow(
                    icon = Icons.Default.BookmarkBorder,
                    iconTint = Color(0xFFD97706),
                    title = "সংরক্ষিত আইটেম (বুকমার্কস)",
                    subtitle = "আপনার সেভ করা গুরুত্বপূর্ণ লেকচার ও পিডিএফ নোটস",
                    badgeText = "সেভড",
                    isDark = isDark,
                    onClick = onNavigateToSavedItems,
                    testTag = "settings_row_saved_items"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                SettingsActionRow(
                    icon = Icons.Default.Analytics,
                    iconTint = Color(0xFFEA580C),
                    title = "রিপোর্ট কার্ড ও লিডারবোর্ড",
                    subtitle = "উপস্থিতি, পরীক্ষার ফলাফল ও বিষয়ভিত্তিক অগ্রগতি",
                    badgeText = "প্রগ্রেস",
                    isDark = isDark,
                    onClick = onNavigateToReportCard,
                    testTag = "settings_row_report_card"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                SettingsActionRow(
                    icon = Icons.Default.DownloadForOffline,
                    iconTint = Color(0xFF2563EB),
                    title = "অফলাইন ডাউনলোড ম্যানেজার",
                    subtitle = "ডাউনলোড করা ভিডিও ক্লাস ও পিডিএফ নোটস",
                    badgeText = "অফলাইন",
                    isDark = isDark,
                    onClick = onNavigateToDownloads,
                    testTag = "settings_row_downloads"
                )
            }

            // 3. App Preferences & Customization Section (Options #1, #2, #3, #4)
            SettingsSectionGroup(
                title = "অ্যাপ প্রেফারেন্স ও কাস্টমাইজেশন",
                icon = Icons.Default.Palette,
                isDark = isDark
            ) {
                // Option #1: Header & Live Wallpaper
                SettingsActionRow(
                    icon = Icons.Default.Wallpaper,
                    iconTint = Color(0xFFEC4899),
                    title = "হেডার ও লাইভ ওয়ালপেপার",
                    subtitle = "${com.example.utils.toBengaliDigits(HeaderWallpaperPresets.presets.size)}টি প্রিমিয়াম সিমলেস অ্যানিমেটেড থিম, গ্যালারি ছবি ও থিম কোড ইমপোর্ট",
                    badgeText = activeWallpaperBadge,
                    isDark = isDark,
                    onClick = onNavigateToHeaderWallpaper,
                    testTag = "settings_row_wallpaper"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                // Option #2: Notification & Class Alarm
                SettingsActionRow(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = Color(0xFFD97706),
                    title = "নোটিফিকেশন ও ক্লাস অ্যালার্ম",
                    subtitle = "রুটিন অনুযায়ী ক্লাস অ্যালার্ম ও $currentLeadTime মি. আগের রিমাইন্ডার",
                    badgeText = "${currentLeadTime}m",
                    isDark = isDark,
                    onClick = onNavigateToNotification,
                    testTag = "settings_row_notifications"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                // Option #3: App Theme (Dark & Light Mode)
                val themeLabel = when (currentThemeMode) {
                    "light" -> "লাইট মোড সক্রিয়"
                    "dark" -> "ডার্ক মোড সক্রিয়"
                    else -> "সিস্টেম ডিফল্ট (ডিভাইস অনুযায়ী)"
                }
                val themeBadge = when (currentThemeMode) {
                    "light" -> "Light"
                    "dark" -> "Dark"
                    else -> "System"
                }
                SettingsActionRow(
                    icon = Icons.Default.Brightness6,
                    iconTint = Color(0xFF4F46E5),
                    title = "অ্যাপ থিম (Dark & Light Mode)",
                    subtitle = themeLabel,
                    badgeText = themeBadge,
                    isDark = isDark,
                    onClick = { showThemeDialog = true },
                    testTag = "settings_row_theme"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = cardBorder
                )
                // Option #4: Audio Equalizer & Sound Control
                SettingsActionRow(
                    icon = Icons.Default.Tune,
                    iconTint = Color(0xFF059669),
                    title = "অডিও ইকুলাইজার ও সাউন্ড কন্ট্রোল",
                    subtitle = "মোবাইলের ইকুলাইজার স্থায়ী কানেক্ট ও স্টুডিও সাউন্ড বুস্টার",
                    badgeText = activeEqualizerBadge,
                    isDark = isDark,
                    onClick = { showEqualizerSheet = true },
                    testTag = "settings_row_equalizer"
                )
            }

            // 4. Option #5: Storage & Cache Memory Management Section
            SettingsSectionGroup(
                title = "স্টোরেজ ও ক্যাশ মেমোরি",
                icon = Icons.Default.Storage,
                isDark = isDark
            ) {
                val formattedTotal = remember(cacheBreakdown) {
                    AppCacheManager.formatBytes(cacheBreakdown.totalBytes)
                }
                SettingsActionRow(
                    icon = Icons.Default.CleaningServices,
                    iconTint = Color(0xFF0284C7),
                    title = "ক্যাশ মেমোরি পরিষ্কার করুন",
                    subtitle = if (isClearingCache) {
                        "ক্যাশ মেমোরি পরিষ্কার করা হচ্ছে..."
                    } else {
                        "$formattedTotal অস্থায়ী ক্যাশ জমা আছে • বিস্তারিত দেখতে ও খালি করতে ট্যাপ করুন"
                    },
                    badgeText = formattedTotal,
                    isDark = isDark,
                    onClick = {
                        if (!isClearingCache) {
                            scope.launch {
                                cacheBreakdown = withContext(Dispatchers.IO) {
                                    AppCacheManager.getCacheBreakdown(context)
                                }
                                showClearCacheDialog = true
                            }
                        }
                    },
                    testTag = "settings_row_clear_cache"
                )
            }

            // 5. Option #6: App Update & Version Enforcement Section
            SettingsSectionGroup(
                title = "অ্যাপ আপডেট ও সংস্করণ যাচাই",
                icon = Icons.Default.SystemUpdate,
                isDark = isDark
            ) {
                SettingsActionRow(
                    icon = Icons.Default.SystemSecurityUpdateGood,
                    iconTint = Color(0xFF10B981),
                    title = "অ্যাপ আপডেট ও নির্ধারিত সংস্করণ চেক করুন",
                    subtitle = if (isCheckingUpdate) {
                        "সার্ভার ডাটাবেজ থেকে নির্ধারিত সংস্করণ যাচাই করা হচ্ছে..."
                    } else {
                        "ডাটাবেজে নির্ধারিত সংস্করণে আছেন কিনা যাচাই করুন (বর্তমান: v${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE})"
                    },
                    badgeText = if (isCheckingUpdate) "..." else "v${BuildConfig.VERSION_NAME}",
                    isDark = isDark,
                    onClick = {
                        if (!isCheckingUpdate) {
                            isCheckingUpdate = true
                            scope.launch {
                                val info = AppUpdateRepository.fetchLatestUpdate()
                                isCheckingUpdate = false
                                if (info != null && AppUpdateRepository.shouldBlockApp(
                                        info = info,
                                        installedVersionCode = BuildConfig.VERSION_CODE,
                                        installedVersionName = BuildConfig.VERSION_NAME
                                    )
                                ) {
                                    fetchedUpdateInfo = info
                                } else {
                                    verifiedDatabaseVersionText = if (info != null) {
                                        "v${info.versionName} (Build ${info.versionCode})"
                                    } else {
                                        "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                                    }
                                    showUpToDateDialog = true
                                }
                            }
                        }
                    },
                    testTag = "settings_row_check_update"
                )
            }

            // 6. Option #7: Account & Security (Full Account + Cache Wipe Logout)
            SettingsSectionGroup(
                title = "অ্যাকাউন্ট ও নিরাপত্তা",
                icon = Icons.Default.Security,
                isDark = isDark
            ) {
                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = Color(0xFFDC2626),
                    title = "লগআউট ও অ্যাকাউন্ট ডাটা ক্লিয়ার",
                    subtitle = if (isLoggingOut) {
                        "অ্যাকাউন্ট ডাটা ও ক্যাশ মুছে লগআউট করা হচ্ছে..."
                    } else {
                        "সকল অ্যাকাউন্ট তথ্য ও ক্যাশ মুছে নিরাপদে প্রস্থান করুন"
                    },
                    badgeText = "প্রস্থান",
                    isDestructive = true,
                    isDark = isDark,
                    onClick = {
                        if (!isLoggingOut) {
                            showLogoutConfirmDialog = true
                        }
                    },
                    testTag = "settings_row_logout"
                )
            }

            // App Version Footer — generous bottom clearance above floating dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ভার্সন v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS & SHEETS
    // =========================================================================

    // #3: App Theme Selection Dialog (Instant Reactive Update)
    if (showThemeDialog) {
        val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
        val itemUnselectedBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
        val itemBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color(0xFF0284C7)
                        )
                    }
                    Column {
                        Text(
                            text = "অ্যাপ থিম নির্বাচন করুন",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = "সিলেক্ট করার সাথে সাথেই সম্পূর্ণ অ্যাপের থিম পরিবর্তিত হবে",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                }
            },
            text = {
                val options = listOf(
                    Triple(
                        "system",
                        "সিস্টেম ডিফল্ট (মোবাইল অনুযায়ী)",
                        "মোবাইলের সিস্টেম সেটিংস অনুযায়ী স্বয়ংক্রিয়ভাবে পরিবর্তিত হবে"
                    ) to Icons.Default.BrightnessAuto,
                    Triple(
                        "light",
                        "লাইট মোড (Light Mode)",
                        "দিনের বেলা পড়ার জন্য উজ্জ্বল ও পরিষ্কার সাদা থিম"
                    ) to Icons.Default.LightMode,
                    Triple(
                        "dark",
                        "ডার্ক মোড (Dark Mode)",
                        "রাতের বেলা ও চোখের আরামের জন্য প্রিমিয়াম ডার্ক থিম"
                    ) to Icons.Default.DarkMode
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { (item, modeIcon) ->
                        val (modeKey, label, desc) = item
                        val isSelected = currentThemeMode == modeKey
                        val accentCol = when (modeKey) {
                            "light" -> Color(0xFFF59E0B)
                            "dark" -> Color(0xFF8B5CF6)
                            else -> Color(0xFF0284C7)
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) accentCol.copy(alpha = if (isDark) 0.18f else 0.10f) else itemUnselectedBg,
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) accentCol else itemBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    sessionManager.setThemeMode(modeKey)
                                    showThemeDialog = false
                                }
                                .testTag("theme_option_$modeKey")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accentCol.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = modeIcon,
                                        contentDescription = null,
                                        tint = accentCol,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = primaryText
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 11.5.sp,
                                        color = secondaryText
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        sessionManager.setThemeMode(modeKey)
                                        showThemeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = accentCol,
                                        unselectedColor = secondaryText
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("বন্ধ করুন", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // #4: Audio Equalizer Bottom Sheet
    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            onDismiss = { showEqualizerSheet = false }
        )
    }

    // #5: Upgraded Cache Memory Breakdown & Clean Dialog
    if (showClearCacheDialog) {
        val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
        val subCardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)

        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(26.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "ক্যাশ মেমোরি ও স্টোরেজ ক্লিনার",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = subCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CacheCategoryRow(
                                label = "ভিডিও স্ট্রিম ও মিডিয়া ক্যাশ",
                                sizeText = AppCacheManager.formatBytes(cacheBreakdown.mediaStreamBytes),
                                color = Color(0xFF0284C7),
                                primaryText = primaryText
                            )
                            CacheCategoryRow(
                                label = "পিডিএফ ও স্লাইড প্রিভিউ ক্যাশ",
                                sizeText = AppCacheManager.formatBytes(cacheBreakdown.pdfPreviewBytes),
                                color = Color(0xFF8B5CF6),
                                primaryText = primaryText
                            )
                            CacheCategoryRow(
                                label = "ইমেজ ও নেটওয়ার্ক টেম্পোরারি ফাইল",
                                sizeText = AppCacheManager.formatBytes(cacheBreakdown.imageAndNetworkBytes),
                                color = Color(0xFF10B981),
                                primaryText = primaryText
                            )
                            HorizontalDivider(color = cardBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "সর্বমোট অস্থায়ী ক্যাশ",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText
                                )
                                Text(
                                    text = AppCacheManager.formatBytes(cacheBreakdown.totalBytes),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "আপনার ডাউনলোড করা অফলাইন ভিডিও ও নোটস ১০০% অক্ষত থাকবে।",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        isClearingCache = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppCacheManager.clearAllAppCache(context)
                                cacheBreakdown = AppCacheManager.getCacheBreakdown(context)
                            }
                            isClearingCache = false
                            Toast.makeText(
                                context,
                                "সকল অস্থায়ী ক্যাশ মেমোরি সফলভাবে পরিষ্কার করা হয়েছে!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ক্যাশ পরিষ্কার করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("বাতিল", color = secondaryText)
                }
            }
        )
    }

    // #6: App Update Dialog when version differs from Database (`!=`)
    fetchedUpdateInfo?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { fetchedUpdateInfo = null }
        )
    }

    // #6: Up-to-date Verification Dialog when Installed Version == Database Version
    if (showUpToDateDialog) {
        val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
        AlertDialog(
            onDismissRequest = { showUpToDateDialog = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "সঠিক ও অনুমোদিত সংস্করণ!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "আপনি ডাটাবেজে নির্ধারিত অফিশিয়াল সংস্করণটিই ব্যবহার করছেন।",
                        fontSize = 13.sp,
                        color = secondaryText,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "নির্ধারিত ভার্সন: ${verifiedDatabaseVersionText ?: "v${BuildConfig.VERSION_NAME}"}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showUpToDateDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ঠিক আছে", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // #7: Upgraded Logout & Full Account/Cache Wipe Confirmation Dialog
    if (showLogoutConfirmDialog) {
        val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
        val subCardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFEF2F2)

        AlertDialog(
            onDismissRequest = {
                if (!isLoggingOut) showLogoutConfirmDialog = false
            },
            containerColor = dialogBg,
            shape = RoundedCornerShape(26.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFDC2626).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "লগআউট ও ডাটা ক্লিয়ার নিশ্চিত করুন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "আপনি কি বর্তমান অ্যাকাউন্ট থেকে লগআউট করতে চান?",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = subCardBg,
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "লগআউট করলে যা যা পরিষ্কার হবে:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEF4444)
                            )
                            Text(
                                text = "• ফোন থেকে বর্তমান অ্যাকাউন্টের সকল লগইন ও প্রোফাইল ডাটা\n• লোকাল ডাটাবেজে থাকা রুটিন, কোর্স ও লেসন ক্যাশ\n• সকল অস্থায়ী ভিডিও ও পিডিএফ ক্যাশ মেমোরি",
                                fontSize = 11.5.sp,
                                color = secondaryText,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isLoggingOut) {
                            isLoggingOut = true
                            showLogoutConfirmDialog = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppCacheManager.wipeAllAccountAndCacheOnLogout(context)
                                }
                                isLoggingOut = false
                                Toast.makeText(
                                    context,
                                    "সকল অ্যাকাউন্ট ডাটা ও ক্যাশ মুছে লগআউট সম্পন্ন হয়েছে",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onLogout()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_logout_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("হ্যাঁ, লগআউট করুন", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirmDialog = false },
                    enabled = !isLoggingOut
                ) {
                    Text("না, ফিরে যান", color = secondaryText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun CacheCategoryRow(
    label: String,
    sizeText: String,
    color: Color,
    primaryText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = primaryText.copy(alpha = 0.85f)
            )
        }
        Text(
            text = sizeText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = primaryText
        )
    }
}

@Composable
private fun SettingsSectionGroup(
    title: String,
    icon: ImageVector,
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val cardBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0284C7)
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    isDestructive: Boolean = false,
    isDark: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconTint.copy(alpha = 0.14f))
                .border(1.dp, iconTint.copy(alpha = 0.25f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDestructive) Color(0xFFEF4444) else primaryText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = iconTint.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, iconTint.copy(alpha = 0.28f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 92.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = secondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
