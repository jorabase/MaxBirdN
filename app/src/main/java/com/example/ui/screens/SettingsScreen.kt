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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.example.BuildConfig
import com.example.auth.SessionManager
import com.example.player.EqualizerManager
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.dialogs.UpdateDialog
import com.example.ui.theme.HeaderWallpaperConfig
import com.example.ui.theme.HeaderWallpaperPresets
import com.example.ui.theme.isAppInDarkTheme
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateRepository
import com.example.utils.AppCacheManager
import com.example.utils.AppLang
import com.example.utils.AppLanguage
import com.example.utils.AvatarUtils
import com.example.utils.CacheBreakdown
import com.example.utils.isBangla
import com.example.utils.toBengaliDigits
import com.example.utils.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    sessionManager: SessionManager = SessionManager.getInstance(LocalContext.current),
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
    val isBn = isBangla()

    val currentThemeMode by sessionManager.themeModeFlow.collectAsStateWithLifecycle()
    val currentLanguage by sessionManager.appLanguageFlow.collectAsStateWithLifecycle()
    val currentLeadTime by sessionManager.classNotificationLeadTimeFlow.collectAsStateWithLifecycle()
    val currentWallpaper by sessionManager.headerWallpaperFlow.collectAsStateWithLifecycle()

    var showLanguageDialog by remember { mutableStateOf(false) }
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

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var fetchedUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpToDateDialog by remember { mutableStateOf(false) }
    var verifiedDatabaseVersionText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cacheBreakdown = AppCacheManager.getCacheBreakdown(context)
        }
    }

    val profileUpdateTick by sessionManager.userProfileUpdateFlow.collectAsStateWithLifecycle()
    val avatarFromFlow by sessionManager.userAvatarFlow.collectAsStateWithLifecycle()

    val fullName = remember(profileUpdateTick, isBn) {
        sessionManager.getUserFullName()
            ?: sessionManager.getUserFirstName()?.takeIf { it.isNotBlank() }
            ?: AppLang.tr("Student", "শিক্ষার্থী")
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
    val groupDisplay = remember(profileUpdateTick, isBn) {
        val raw = sessionManager.getUserGroup() ?: ""
        when (raw.lowercase()) {
            "science" -> AppLang.tr("Science", "বিজ্ঞান")
            "humanities", "arts", "hum" -> AppLang.tr("Humanities", "মানবিক")
            "business", "commerce", "business studies" -> AppLang.tr("Business", "ব্যবসায়")
            else -> raw.ifBlank { AppLang.tr("Science", "বিজ্ঞান") }
        }
    }
    val activeCourseTitle = remember(profileUpdateTick, isBn) {
        sessionManager.getActiveProgramTitleBn() ?: AppLang.tr("Academic Program", "একাডেমিক প্রোগ্রাম")
    }
    val avatarUrl = remember(avatarFromFlow, fullName) {
        val candidate = avatarFromFlow ?: sessionManager.getUserAvatar()
        AvatarUtils.formatAvatarUrl(candidate, fullName)
    }

    val activeWallpaperBadge = remember(currentWallpaper, isBn) {
        when (currentWallpaper.type) {
            HeaderWallpaperConfig.PRESET_AUTO_DAILY -> AppLang.tr("Auto 7D", "৭ দিন অটো")
            HeaderWallpaperConfig.CUSTOM_CODE -> currentWallpaper.customTitle ?: AppLang.tr("Custom", "কাস্টম")
            HeaderWallpaperConfig.CUSTOM_IMAGE -> AppLang.tr("Photo", "ছবি")
            else -> HeaderWallpaperPresets.findById(currentWallpaper.type).displayName.split(" ").firstOrNull()
                ?: AppLang.tr("Theme", "থিম")
        }
    }

    val activeEqualizerBadge = remember(showEqualizerSheet, isBn) {
        EqualizerManager.getActiveStatusLabel(context)
    }

    val pageBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant
    val primaryText = MaterialTheme.colorScheme.onSurface
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = tr("Settings", "সেটিংস"),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText,
                                    maxLines = 1
                                )
                                Text(
                                    text = tr("Account & preferences", "অ্যাকাউন্ট ও পছন্দ"),
                                    fontSize = 11.5.sp,
                                    color = secondaryText,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = cardBg
                    )
                )
                HorizontalDivider(color = cardBorder.copy(alpha = 0.65f), thickness = 1.dp)
            }
        },
        containerColor = pageBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Student Profile Hero Card
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
                                    Color(0xFF0B1528),
                                    Color(0xFF1A367A),
                                    Color(0xFF0284C7)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF38BDF8), CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = avatarUrl,
                                    contentDescription = tr("Profile Photo", "প্রোফাইল ছবি"),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    error = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = fullName,
                                        fontSize = 17.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = tr("Verified", "যাচাইকৃত"),
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                if (phone.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = phone,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.84f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                            maxLines = 1,
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
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            FilledTonalIconButton(
                                onClick = onNavigateToEditProfile,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.18f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = tr("Edit Profile", "প্রোফাইল এডিট"),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.24f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                        modifier = Modifier.size(17.dp)
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
                                    text = if (schoolName.isNotBlank()) schoolName else tr("Active", "সক্রিয়"),
                                    fontSize = 11.sp,
                                    color = Color(0xFF7DD3FC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 116.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Academic Section (Clean, Crisp 1-Line Titles)
            SettingsSectionGroup(
                title = tr("Academic", "একাডেমিক"),
                icon = Icons.Default.AutoStories,
                isDark = isDark
            ) {
                SettingsActionRow(
                    icon = Icons.Default.PersonOutline,
                    iconTint = Color(0xFF0284C7),
                    title = tr("Profile", "প্রোফাইল"),
                    subtitle = tr("Personal & academic info", "ব্যক্তিগত ও একাডেমিক তথ্য"),
                    isDark = isDark,
                    onClick = onNavigateToProfile,
                    testTag = "settings_row_profile"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.WorkspacePremium,
                    iconTint = Color(0xFF7C3AED),
                    title = tr("Batch & Syllabus", "ব্যাচ ও সিলেবাস"),
                    subtitle = tr("Switch active program", "সক্রিয় প্রোগ্রাম পরিবর্তন"),
                    isDark = isDark,
                    onClick = onNavigateToChangeSyllabus,
                    testTag = "settings_row_my_courses"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Explore,
                    iconTint = Color(0xFF059669),
                    title = tr("Course Catalog", "কোর্স ক্যাটালগ"),
                    subtitle = tr("Explore courses & trials", "নতুন কোর্স ও ফ্রি ট্রায়াল"),
                    isDark = isDark,
                    onClick = onNavigateToCourseEnrollment,
                    testTag = "settings_row_course_catalog"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.BookmarkBorder,
                    iconTint = Color(0xFFD97706),
                    title = tr("Saved Items", "সংরক্ষিত আইটেম"),
                    subtitle = tr("Bookmarked classes & notes", "সেভ করা লেকচার ও নোট"),
                    isDark = isDark,
                    onClick = onNavigateToSavedItems,
                    testTag = "settings_row_saved_items"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.Analytics,
                    iconTint = Color(0xFFEA580C),
                    title = tr("Report Card", "রিপোর্ট কার্ড"),
                    subtitle = tr("Results & progress", "ফলাফল ও অগ্রগতি"),
                    isDark = isDark,
                    onClick = onNavigateToReportCard,
                    testTag = "settings_row_report_card"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.DownloadForOffline,
                    iconTint = Color(0xFF2563EB),
                    title = tr("Downloads", "ডাউনলোড"),
                    subtitle = tr("Offline videos & notes", "অফলাইন ভিডিও ও নোট"),
                    isDark = isDark,
                    onClick = onNavigateToDownloads,
                    testTag = "settings_row_downloads"
                )
            }

            // 3. Preferences Section (Includes App Language + 7 Interactive Banner Themes)
            SettingsSectionGroup(
                title = tr("Preferences", "পছন্দ ও থিম"),
                icon = Icons.Default.Tune,
                isDark = isDark
            ) {
                // App Language (English Default + Bangla)
                SettingsActionRow(
                    icon = Icons.Default.Translate,
                    iconTint = Color(0xFF0EA5E9),
                    title = tr("App Language", "অ্যাপ ভাষা"),
                    subtitle = if (currentLanguage == AppLanguage.BANGLA) "বাংলা" else "English",
                    badgeText = if (currentLanguage == AppLanguage.BANGLA) "বাংলা" else "EN",
                    isDark = isDark,
                    onClick = { showLanguageDialog = true },
                    testTag = "settings_row_language"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)

                // Banner Themes (7 Interactive + Auto Daily)
                SettingsActionRow(
                    icon = Icons.Default.Wallpaper,
                    iconTint = Color(0xFFEC4899),
                    title = tr("Banner Themes", "ব্যানার থিম"),
                    subtitle = tr("7 interactive & daily auto", "৭টি ইন্টারঅ্যাক্টিভ ও অটো থিম"),
                    badgeText = activeWallpaperBadge,
                    isDark = isDark,
                    onClick = onNavigateToHeaderWallpaper,
                    testTag = "settings_row_wallpaper"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)

                // Notifications
                SettingsActionRow(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = Color(0xFFD97706),
                    title = tr("Notifications", "নোটিফিকেশন"),
                    subtitle = tr(
                        "Class alert (${currentLeadTime}m before)",
                        "ক্লাস অ্যালার্ম (${toBengaliDigits(currentLeadTime)} মি. আগে)"
                    ),
                    badgeText = "${toBengaliDigits(currentLeadTime)}m",
                    isDark = isDark,
                    onClick = onNavigateToNotification,
                    testTag = "settings_row_notifications"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)

                // Appearance
                val themeLabel = when (currentThemeMode) {
                    "light" -> tr("Light mode", "লাইট মোড")
                    "dark" -> tr("Dark mode", "ডার্ক মোড")
                    else -> tr("System default", "সিস্টেম ডিফল্ট")
                }
                val themeBadge = when (currentThemeMode) {
                    "light" -> tr("Light", "লাইট")
                    "dark" -> tr("Dark", "ডার্ক")
                    else -> tr("Auto", "অটো")
                }
                SettingsActionRow(
                    icon = Icons.Default.Brightness6,
                    iconTint = Color(0xFF4F46E5),
                    title = tr("Appearance", "অ্যাপ থিম"),
                    subtitle = themeLabel,
                    badgeText = themeBadge,
                    isDark = isDark,
                    onClick = { showThemeDialog = true },
                    testTag = "settings_row_theme"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)

                // Audio Equalizer
                SettingsActionRow(
                    icon = Icons.Default.Equalizer,
                    iconTint = Color(0xFF059669),
                    title = tr("Audio Equalizer", "ইকুলাইজার"),
                    subtitle = tr("Sound boost & presets", "সাউন্ড ও প্রিসেট কন্ট্রোল"),
                    badgeText = activeEqualizerBadge,
                    isDark = isDark,
                    onClick = { showEqualizerSheet = true },
                    testTag = "settings_row_equalizer"
                )
            }

            // 4. Storage & Updates Section
            SettingsSectionGroup(
                title = tr("System", "সিস্টেম"),
                icon = Icons.Default.Storage,
                isDark = isDark
            ) {
                val formattedTotal = remember(cacheBreakdown) {
                    AppCacheManager.formatBytes(cacheBreakdown.totalBytes)
                }
                SettingsActionRow(
                    icon = Icons.Default.CleaningServices,
                    iconTint = Color(0xFF0284C7),
                    title = tr("Clear Cache", "ক্যাশ পরিষ্কার"),
                    subtitle = if (isClearingCache) {
                        tr("Clearing cache...", "পরিষ্কার হচ্ছে...")
                    } else {
                        tr("Temporary app files", "অস্থায়ী ক্যাশ ফাইল")
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
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = cardBorder)
                SettingsActionRow(
                    icon = Icons.Default.SystemUpdate,
                    iconTint = Color(0xFF10B981),
                    title = tr("App Update", "অ্যাপ আপডেট"),
                    subtitle = if (isCheckingUpdate) {
                        tr("Checking version...", "যাচাই হচ্ছে...")
                    } else {
                        tr("Current v${BuildConfig.VERSION_NAME}", "বর্তমান v${BuildConfig.VERSION_NAME}")
                    },
                    badgeText = if (isCheckingUpdate) "..." else "v${BuildConfig.VERSION_NAME}",
                    isDark = isDark,
                    onClick = {
                        if (!isCheckingUpdate) {
                            isCheckingUpdate = true
                            scope.launch {
                                val info = AppUpdateRepository.fetchLatestUpdate(forceRefresh = true)
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

            // 5. Account Section
            SettingsSectionGroup(
                title = tr("Account", "অ্যাকাউন্ট"),
                icon = Icons.Default.Security,
                isDark = isDark
            ) {
                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = Color(0xFFDC2626),
                    title = tr("Log Out", "লগআউট"),
                    subtitle = if (isLoggingOut) {
                        tr("Signing out...", "লগআউট হচ্ছে...")
                    } else {
                        tr("Sign out & clear local data", "অ্যাকাউন্ট থেকে প্রস্থান")
                    },
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

            // Version Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 4.dp, bottom = 116.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = tr(
                                "Version v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                "ভার্সন v${BuildConfig.VERSION_NAME} (${toBengaliDigits(BuildConfig.VERSION_CODE)})"
                            ),
                            fontSize = 12.sp,
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

    // Language Selection Dialog (English Default + Bangla)
    if (showLanguageDialog) {
        val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
        val itemUnselectedBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
        val itemBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
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
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = Color(0xFF0EA5E9)
                        )
                    }
                    Column {
                        Text(
                            text = tr("App Language", "অ্যাপ ভাষা"),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = tr("Applies instantly across the app", "সম্পূর্ণ অ্যাপে সাথে সাথেই পরিবর্তিত হবে"),
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                }
            },
            text = {
                val langOptions = listOf(
                    Triple(AppLanguage.ENGLISH, "English", "Default interface language"),
                    Triple(AppLanguage.BANGLA, "বাংলা", "সম্পূর্ণ বাংলা ইন্টারফেস")
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    langOptions.forEach { (lang, title, desc) ->
                        val isSelected = currentLanguage == lang
                        val accentCol = if (lang == AppLanguage.ENGLISH) Color(0xFF0284C7) else Color(0xFF10B981)

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
                                    sessionManager.setAppLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .testTag("language_option_${lang.code}")
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
                                    Text(
                                        text = if (lang == AppLanguage.ENGLISH) "EN" else "বাং",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = accentCol
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontSize = 15.sp,
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
                                        sessionManager.setAppLanguage(lang)
                                        showLanguageDialog = false
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
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(tr("Close", "বন্ধ করুন"), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // App Theme Selection Dialog
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
                            .background(Color(0xFF4F46E5).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5)
                        )
                    }
                    Column {
                        Text(
                            text = tr("Appearance", "অ্যাপ থিম"),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = tr("Choose light or dark theme", "পছন্দের থিম নির্বাচন করুন"),
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                }
            },
            text = {
                val options = listOf(
                    Triple("system", tr("System Default", "সিস্টেম ডিফল্ট"), tr("Follows device theme", "ডিভাইস অনুযায়ী")) to Icons.Default.BrightnessAuto,
                    Triple("light", tr("Light Mode", "লাইট মোড"), tr("Bright & crisp display", "উজ্জ্বল ও পরিষ্কার")) to Icons.Default.LightMode,
                    Triple("dark", tr("Dark Mode", "ডার্ক মোড"), tr("Easy on the eyes at night", "রাতের জন্য আরামদায়ক")) to Icons.Default.DarkMode
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
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(accentCol.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = modeIcon,
                                        contentDescription = null,
                                        tint = accentCol,
                                        modifier = Modifier.size(20.dp)
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
                    Text(tr("Close", "বন্ধ করুন"), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(onDismiss = { showEqualizerSheet = false })
    }

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
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = tr("Clear Cache", "ক্যাশ পরিষ্কার"),
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
                                label = tr("Media & stream cache", "ভিডিও ও মিডিয়া ক্যাশ"),
                                sizeText = AppCacheManager.formatBytes(cacheBreakdown.mediaStreamBytes),
                                color = Color(0xFF0284C7),
                                primaryText = primaryText
                            )
                            CacheCategoryRow(
                                label = tr("PDF & slide cache", "পিডিএফ ও স্লাইড ক্যাশ"),
                                sizeText = AppCacheManager.formatBytes(cacheBreakdown.pdfPreviewBytes),
                                color = Color(0xFF8B5CF6),
                                primaryText = primaryText
                            )
                            CacheCategoryRow(
                                label = tr("Image & network cache", "ইমেজ ও নেটওয়ার্ক ক্যাশ"),
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
                                    text = tr("Total cache", "মোট অস্থায়ী ক্যাশ"),
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
                                text = tr(
                                    "Your offline downloads remain completely safe.",
                                    "আপনার ডাউনলোড করা অফলাইন ফাইল সম্পূর্ণ সুরক্ষিত থাকবে।"
                                ),
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
                                AppLang.tr("Cache cleared successfully", "ক্যাশ সফলভাবে পরিষ্কার হয়েছে"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Clear", "পরিষ্কার করুন"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(tr("Cancel", "বাতিল"), color = secondaryText)
                }
            }
        )
    }

    fetchedUpdateInfo?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { fetchedUpdateInfo = null }
        )
    }

    if (showUpToDateDialog) {
        val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
        AlertDialog(
            onDismissRequest = { showUpToDateDialog = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(30.dp)
                    )
                }
            },
            title = {
                Text(
                    text = tr("Up to Date", "অ্যাপ আপডেট আছে"),
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
                        text = tr(
                            "You are running the latest official version.",
                            "আপনি সর্বশেষ অফিশিয়াল ভার্সন ব্যবহার করছেন।"
                        ),
                        fontSize = 13.sp,
                        color = secondaryText,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = verifiedDatabaseVersionText ?: "v${BuildConfig.VERSION_NAME}",
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
                    Text(tr("OK", "ঠিক আছে"), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

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
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFDC2626).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = tr("Log Out?", "লগআউট করবেন?"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(
                            "Are you sure you want to sign out of this account?",
                            "আপনি কি বর্তমান অ্যাকাউন্ট থেকে লগআউট করতে চান?"
                        ),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
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
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = tr("This will clear:", "যা পরিষ্কার হবে:"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEF4444)
                            )
                            Text(
                                text = tr(
                                    "• Active login session & profile data\n• Cached courses, routine & lessons\n• Temporary media & PDF cache",
                                    "• বর্তমান লগইন ও প্রোফাইল ডাটা\n• লোকাল রুটিন, কোর্স ও লেসন ক্যাশ\n• অস্থায়ী মিডিয়া ও পিডিএফ ক্যাশ"
                                ),
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
                                    AppLang.tr("Logged out successfully", "লগআউট সম্পন্ন হয়েছে"),
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
                    Text(tr("Log Out", "লগআউট"), fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirmDialog = false },
                    enabled = !isLoggingOut
                ) {
                    Text(tr("Cancel", "বাতিল"), color = secondaryText, fontWeight = FontWeight.Bold)
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
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
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
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.13f))
                .border(1.dp, iconTint.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDestructive) Color(0xFFEF4444) else primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.5.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = iconTint.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, iconTint.copy(alpha = 0.26f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 88.dp)
                        .padding(horizontal = 9.dp, vertical = 3.5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = secondaryText.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}
