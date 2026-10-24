package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.BuildConfig
import com.example.ai.GeminiAiStudyRepository
import com.example.auth.SessionManager
import com.example.ui.dialogs.FcmLiveTerminalDialog
import com.example.ui.dialogs.GeminiApiSettingsDialog
import com.example.ui.dialogs.UpdateDialog
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateRepository
import com.example.utils.AppCacheManager
import com.example.utils.AvatarUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()

    val currentThemeMode by sessionManager.themeModeFlow.collectAsState()
    val currentLeadTime by sessionManager.classNotificationLeadTimeFlow.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showEqualizerBottomSheet by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showGeminiApiDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    val geminiConfig by GeminiAiStudyRepository.configState.collectAsState()

    var formattedCacheSize by remember { mutableStateOf("হিসাব করা হচ্ছে...") }
    var isClearingCache by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var manualUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

    // অফ-থ্রেডে (IO) ক্যাশ মেমোরি সাইজ লোড ও ক্লাউড থেকে Gemini API সিঙ্ক করা হচ্ছে যাতে UI ল্যাগ না করে
    LaunchedEffect(Unit) {
        GeminiAiStudyRepository.initialize(context, sessionManager)
        launch(Dispatchers.IO) {
            runCatching {
                GeminiAiStudyRepository.syncApiKeyFromCloud(context, sessionManager)
            }
        }
        formattedCacheSize = withContext(Dispatchers.IO) {
            runCatching { AppCacheManager.getFormattedCacheSize(context) }
                .getOrDefault("0.0 MB")
        }
    }

    val profileUpdated by sessionManager.userProfileUpdateFlow.collectAsState()
    val userAvatarFlowValue by sessionManager.userAvatarFlow.collectAsState()
    val userAvatar = userAvatarFlowValue ?: sessionManager.getUserAvatar()

    val userName = remember(profileUpdated) {
        sessionManager.getUserFullName()?.takeIf { it.isNotBlank() } ?: "শিক্ষার্থী"
    }
    val userClass = remember(profileUpdated) {
        sessionManager.getUserClassDisplay()?.takeIf { it.isNotBlank() }
            ?: sessionManager.getUserClassName()?.takeIf { it.isNotBlank() }
            ?: "একাদশ শ্রেণি"
    }
    val userGroup = remember(profileUpdated) {
        sessionManager.getUserGroup()?.takeIf { it.isNotBlank() } ?: "মানবিক"
    }
    val userBatch = remember(profileUpdated) {
        sessionManager.getUserBatchId()?.trim().orEmpty()
    }

    val classDisplay = remember(userClass) {
        when (userClass.trim().uppercase()) {
            "C11", "CLASS 11", "11", "XI" -> "একাদশ শ্রেণি"
            "C12", "CLASS 12", "12", "XII" -> "দ্বাদশ শ্রেণি"
            "C10", "CLASS 10", "10", "X" -> "দশম শ্রেণি"
            "C9", "CLASS 9", "9", "IX" -> "নবম শ্রেণি"
            else -> userClass.ifBlank { "একাদশ শ্রেণি" }
        }
    }

    val groupDisplay = remember(userGroup) {
        when (userGroup.trim().lowercase()) {
            "humanities", "humanities_group", "hum", "arts" -> "মানবিক"
            "science", "science_group", "sci" -> "বিজ্ঞান"
            "business", "business_studies", "commerce", "bs", "bba" -> "ব্যবসায় শিক্ষা"
            else -> userGroup
        }
    }

    val themeSubtitle = remember(currentThemeMode) {
        when (currentThemeMode) {
            "light" -> "লাইট মোড (উজ্জ্বল সাদা থিম)"
            "dark" -> "ডার্ক মোড (চোখের জন্য আরামদায়ক)"
            else -> "সিস্টেম ডিফল্ট (ডিভাইস অনুযায়ী)"
        }
    }

    val themeBadge = remember(currentThemeMode) {
        when (currentThemeMode) {
            "light" -> "Light"
            "dark" -> "Dark"
            else -> "System"
        }
    }

    val appVersionText = remember {
        val versionName = runCatching { BuildConfig.VERSION_NAME }.getOrDefault("1.0.0")
        val versionCode = runCatching { BuildConfig.VERSION_CODE }.getOrDefault(1)
        "ভার্সন v$versionName (Build $versionCode)"
    }

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
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "সেটিংস ও প্রেফারেন্স",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "অ্যাকাউন্ট ও অ্যাপ কাস্টমাইজেশন",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { showTerminalDialog = true },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "FCM টার্মিনাল",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ১. প্রিমিয়াম প্রোফাইল হিরো কার্ড
            ProfileHeroCard(
                userName = userName,
                userAvatar = userAvatar,
                classDisplay = classDisplay,
                groupDisplay = groupDisplay,
                userBatch = userBatch,
                onEditProfile = onNavigateToEditProfile,
                onViewProfile = onNavigateToProfile
            )

            // ২. কুইক অ্যাক্সেস বেন্টো গ্রিড (দ্রুত নেভিগেশনের জন্য)
            QuickAccessGrid(
                onNavigateToChangeSyllabus = onNavigateToChangeSyllabus,
                onNavigateToSavedItems = onNavigateToSavedItems,
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToReportCard = onNavigateToReportCard
            )

            // ২.৫ AI পড়াশোনা ও Gemini API সেকশন (ক্লাউড সিঙ্কড)
            SettingsSection(
                title = "AI দিয়ে পড়াশোনা (NotebookLM স্টাইল)",
                sectionIcon = Icons.Default.AutoAwesome
            ) {
                val hasUserKey = geminiConfig.apiKey.isNotBlank()
                val maskedKey = remember(geminiConfig.apiKey) {
                    val k = geminiConfig.apiKey.trim()
                    if (k.length > 10) "${k.take(6)}••••${k.takeLast(4)}" else "সক্রিয়"
                }
                SettingsRowItem(
                    icon = Icons.Default.AutoAwesome,
                    iconTint = Color(0xFF4F46E5),
                    title = "Gemini API",
                    subtitle = when {
                        geminiConfig.isSyncing -> "ক্লাউড ডাটাবেজের সাথে API Key সিঙ্ক হচ্ছে..."
                        hasUserKey && geminiConfig.isCloudSynced ->
                            "সক্রিয় ($maskedKey) • ক্লাউডে সংরক্ষিত • Model: 3.8 Flash"
                        hasUserKey ->
                            "সক্রিয় ($maskedKey) • লেকচার স্লাইড AI টিউটর (3.8 Flash)"
                        else ->
                            "লেকচার স্লাইডের যেকোনো কঠিন টপিক AI দিয়ে বুঝতে আপনার Gemini API Key সেট করুন"
                    },
                    badge = if (hasUserKey) "3.8 Flash ✓" else "সেট করুন",
                    isLoading = geminiConfig.isSyncing,
                    onClick = { showGeminiApiDialog = true }
                )
            }

            // ৩. একাডেমিক সেটিংস সেকশন
            SettingsSection(
                title = "একাডেমিক ও স্টাডি সেন্টার",
                sectionIcon = Icons.Default.School
            ) {
                SettingsRowItem(
                    icon = Icons.Default.Edit,
                    iconTint = Color(0xFF7C3AED),
                    title = "প্রোফাইল এডিট",
                    subtitle = "ব্যক্তিগত ও শিক্ষাপ্রতিষ্ঠানের তথ্য পরিবর্তন করো",
                    badge = "এডিট",
                    onClick = onNavigateToEditProfile
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.SwapCalls,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "সিলেবাস পরিবর্তন",
                    subtitle = listOf(classDisplay, groupDisplay, userBatch)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    badge = "পরিবর্তন",
                    onClick = onNavigateToChangeSyllabus
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.Person,
                    iconTint = Color(0xFF0284C7),
                    title = "প্রোফাইল বিবরণ",
                    subtitle = "নাম, প্রতিষ্ঠান ও অ্যাকাউন্টের বিস্তারিত তথ্য",
                    onClick = onNavigateToProfile
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.CardMembership,
                    iconTint = Color(0xFF16A34A),
                    title = "কোর্সে ভর্তি",
                    subtitle = "ভর্তি হওয়া কোর্স, কোয়ার্টার ও মেয়াদের বিবরণ",
                    badge = "বিস্তারিত",
                    onClick = onNavigateToCourseEnrollment
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.Bookmark,
                    iconTint = Color(0xFFF59E0B),
                    title = "সংরক্ষিত আইটেম",
                    subtitle = "বুকমার্ক করা প্রশ্ন ও গুরুত্বপূর্ণ স্টাডি ম্যাটেরিয়াল",
                    badge = "সংরক্ষিত",
                    onClick = onNavigateToSavedItems
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.DownloadDone,
                    iconTint = Color(0xFF10B981),
                    title = "অফলাইন ডাউনলোড",
                    subtitle = "ইন্টারনেট ছাড়া সংরক্ষিত ভিডিও ও পিডিএফ লেকচার",
                    badge = "অফলাইন",
                    onClick = onNavigateToDownloads
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.Assessment,
                    iconTint = Color(0xFF2563EB),
                    title = "রিপোর্ট কার্ড ও লিডারবোর্ড",
                    subtitle = "কোয়ার্টার পারফরম্যান্স ট্রেন্ড ও বিষয়ভিত্তিক মেধা তালিকা",
                    badge = "নতুন",
                    onClick = onNavigateToReportCard
                )
            }

            // ৪. অ্যাপ প্রেফারেন্স ও নোটিফিকেশন সেকশন
            SettingsSection(
                title = "অ্যাপ প্রেফারেন্স ও কাস্টমাইজেশন",
                sectionIcon = Icons.Default.Palette
            ) {
                SettingsRowItem(
                    icon = Icons.Default.Wallpaper,
                    iconTint = Color(0xFFEC4899),
                    title = "হেডার ও লাইভ ওয়ালপেপার",
                    subtitle = "হোম পেজের ব্যাকগ্রাউন্ড থিম ও নিজস্ব ওয়ালপেপার পরিবর্তন",
                    badge = "থিম",
                    onClick = onNavigateToHeaderWallpaper
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = Color(0xFFD97706),
                    title = "নোটিফিকেশন ও ক্লাস অ্যালার্ম",
                    subtitle = "তারিখ অনুযায়ী ক্লাস অ্যালার্ম ও $currentLeadTime মি. আগের রিমাইন্ডার",
                    badge = "${currentLeadTime}m",
                    onClick = onNavigateToNotification
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.Terminal,
                    iconTint = Color(0xFF0284C7),
                    title = "FCM লাইভ টার্মিনাল (মনিটর)",
                    subtitle = "ক্লাউড নোটিফিকেশন টপিক সাবস্ক্রিপশন ও লাইভ ইভেন্ট লগ",
                    badge = "রিয়েলটাইম",
                    onClick = { showTerminalDialog = true }
                )

                SectionDivider()

                SettingsRowItem(
                    icon = when (currentThemeMode) {
                        "light" -> Icons.Default.LightMode
                        "dark" -> Icons.Default.DarkMode
                        else -> Icons.Default.BrightnessAuto
                    },
                    iconTint = Color(0xFF6366F1),
                    title = "অ্যাপ থিম (Dark & Light Mode)",
                    subtitle = themeSubtitle,
                    badge = themeBadge,
                    onClick = { showThemeDialog = true }
                )

                SectionDivider()

                SettingsRowItem(
                    icon = Icons.Default.Tune,
                    iconTint = Color(0xFF10B981),
                    title = "অডিও ইকুলাইজার ও সাউন্ড কন্ট্রোল",
                    subtitle = "মোবাইলে থাকা ইকুলাইজার কানেক্ট ও সাউন্ড কাস্টমাইজ করুন",
                    badge = "ইকুলাইজার",
                    onClick = { showEqualizerBottomSheet = true }
                )
            }

            // ৫. স্টোরেজ ও ক্যাশ মেমোরি সেকশন
            SettingsSection(
                title = "স্টোরেজ ও ক্যাশ মেমোরি",
                sectionIcon = Icons.Default.Storage
            ) {
                val cacheEmpty = isCacheEmpty(formattedCacheSize)
                SettingsRowItem(
                    icon = Icons.Default.DeleteSweep,
                    iconTint = Color(0xFF0284C7),
                    title = "ক্যাশ মেমোরি পরিষ্কার করুন",
                    subtitle = when {
                        isClearingCache -> "ক্যাশ মেমোরি পরিষ্কার করা হচ্ছে..."
                        !cacheEmpty -> "$formattedCacheSize ক্যাশ জমা আছে • খালি করতে ট্যাপ করুন"
                        else -> "ক্যাশ মেমোরি সম্পূর্ণ পরিষ্কার আছে ($formattedCacheSize)"
                    },
                    badge = formattedCacheSize,
                    isLoading = isClearingCache,
                    onClick = {
                        if (!isClearingCache) {
                            scope.launch {
                                isClearingCache = true
                                val updatedSize = withContext(Dispatchers.IO) {
                                    runCatching {
                                        AppCacheManager.clearAllAppCache(context)
                                        AppCacheManager.getFormattedCacheSize(context)
                                    }.getOrDefault("0.0 MB")
                                }
                                formattedCacheSize = updatedSize
                                isClearingCache = false
                                Toast.makeText(
                                    context,
                                    "অ্যাপের সমস্ত ক্যাশ মেমোরি সম্পূর্ণ খালি করা হয়েছে!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            // ৬. অ্যাপ আপডেট ও সংস্করণ সেকশন
            SettingsSection(
                title = "অ্যাপ আপডেট ও সংস্করণ",
                sectionIcon = Icons.Default.SystemUpdate
            ) {
                SettingsRowItem(
                    icon = Icons.Default.SystemUpdate,
                    iconTint = Color(0xFF10B981),
                    title = "অ্যাপ আপডেট চেক করুন",
                    subtitle = if (isCheckingUpdate) {
                        "সার্ভার থেকে সর্বশেষ আপডেট যাচাই করা হচ্ছে..."
                    } else {
                        "সর্বশেষ সংস্করণে আছেন কিনা যাচাই করুন ($appVersionText)"
                    },
                    badge = if (isCheckingUpdate) "চেক হচ্ছে..." else "v${BuildConfig.VERSION_NAME}",
                    isLoading = isCheckingUpdate,
                    onClick = {
                        if (!isCheckingUpdate) {
                            scope.launch {
                                isCheckingUpdate = true
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { AppUpdateRepository.fetchLatestUpdate() }
                                }
                                isCheckingUpdate = false

                                result.onSuccess { info ->
                                    if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                                        manualUpdateInfo = info
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "আপনার অ্যাপটি ইতিমধ্যে সর্বশেষ সংস্করণে আছে!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        "আপডেট যাচাই করা যায়নি! ইন্টারনেট সংযোগ চেক করুন।",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }

            // ৭. অ্যাকাউন্ট ও নিরাপত্তা সেকশন
            SettingsSection(
                title = "অ্যাকাউন্ট ও নিরাপত্তা",
                sectionIcon = Icons.Default.Security
            ) {
                SettingsRowItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "লগআউট",
                    subtitle = "আপনার বর্তমান অ্যাকাউন্ট থেকে নিরাপদে লগআউট করুন",
                    badge = "প্রস্থান",
                    isDestructive = true,
                    onClick = { showLogoutConfirmDialog = true }
                )
            }

            // ৮. ডায়নামিক অ্যাপ ভার্সন ফুটার কার্ড
            AppFooterInfo(appVersionText = appVersionText)

            Spacer(modifier = Modifier.height(92.dp))
        }
    }

    // আপডেট ডায়ালগ
    manualUpdateInfo?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { manualUpdateInfo = null }
        )
    }

    // থিম সিলেকশন ডায়ালগ
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentThemeMode = currentThemeMode,
            onSelectTheme = { modeKey ->
                sessionManager.setThemeMode(modeKey)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // লগআউট কনফার্মেশন ডায়ালগ
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "আপনি কি লগআউট করতে চান?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "লগআউট করলে পুনরায় আপনার অ্যাকাউন্টে প্রবেশ করতে লগইন তথ্যের প্রয়োজন হবে।",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("হ্যাঁ, লগআউট করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("বাতিল")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // FCM টার্মিনাল ডায়ালগ
    if (showTerminalDialog) {
        FcmLiveTerminalDialog(
            sessionManager = sessionManager,
            onDismissRequest = { showTerminalDialog = false }
        )
    }

    // অডিও ইকুলাইজার বটম শিট
    if (showEqualizerBottomSheet) {
        com.example.ui.components.EqualizerBottomSheet(
            onDismiss = { showEqualizerBottomSheet = false }
        )
    }

    // Gemini API সেটিংস ও ক্লাউড সিঙ্ক ডায়ালগ
    if (showGeminiApiDialog) {
        GeminiApiSettingsDialog(
            sessionManager = sessionManager,
            onDismiss = { showGeminiApiDialog = false }
        )
    }
}

@Composable
private fun ProfileHeroCard(
    userName: String,
    userAvatar: String?,
    classDisplay: String,
    groupDisplay: String,
    userBatch: String,
    onEditProfile: () -> Unit,
    onViewProfile: () -> Unit
) {
    val context = LocalContext.current
    val fallbackInitial = remember(userName) {
        userName.trim().firstOrNull()?.toString()?.uppercase() ?: "U"
    }
    val imageRequest = remember(userAvatar, userName, context) {
        AvatarUtils.buildImageRequest(context, userAvatar, userName)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onViewProfile() },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            )
        ),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // অবতার বক্স + অনলাইন ব্যাজ
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                            .padding(2.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            SubcomposeAsyncImage(
                                model = imageRequest,
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                loading = {
                                    AvatarFallbackBox(fallbackInitial = fallbackInitial)
                                },
                                error = {
                                    AvatarFallbackBox(fallbackInitial = fallbackInitial)
                                }
                            )
                        }
                    }

                    // ভেরিফাইড/অ্যাক্টিভ ব্যাজ
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // ইউজার নাম ও একাডেমিক চিপস
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = userName,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (classDisplay.isNotBlank()) {
                            ProfileTagChip(
                                text = classDisplay,
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (groupDisplay.isNotBlank()) {
                            ProfileTagChip(
                                text = groupDisplay,
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (userBatch.isNotBlank()) {
                            ProfileTagChip(
                                text = userBatch,
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // কুইক অ্যাকশন বাটন রো (প্রোফাইল দেখুন & প্রোফাইল এডিট)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onViewProfile,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "প্রোফাইল বিবরণ",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onEditProfile,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "প্রোফাইল এডিট",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarFallbackBox(fallbackInitial: String) {
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
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProfileTagChip(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun QuickAccessGrid(
    onNavigateToChangeSyllabus: () -> Unit,
    onNavigateToSavedItems: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToReportCard: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.SwapCalls,
            title = "সিলেবাস",
            accentColor = Color(0xFF7C3AED),
            onClick = onNavigateToChangeSyllabus
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Bookmark,
            title = "সংরক্ষিত",
            accentColor = Color(0xFFF59E0B),
            onClick = onNavigateToSavedItems
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.DownloadDone,
            title = "ডাউনলোড",
            accentColor = Color(0xFF10B981),
            onClick = onNavigateToDownloads
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Assessment,
            title = "ফলাফল",
            accentColor = Color(0xFF2563EB),
            onClick = onNavigateToReportCard
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor.copy(alpha = 0.05f))
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    sectionIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sectionIcon != null) {
                Icon(
                    imageVector = sectionIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.2.sp
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
            ),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    )
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val effectiveTint = if (isDestructive) MaterialTheme.colorScheme.error else iconTint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // অ্যাডাপ্টিভ আইকন কন্টেইনার (লাইট ও ডার্ক উভয় মোডে সুন্দর দেখাবে)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(effectiveTint.copy(alpha = 0.13f))
                .border(
                    width = 1.dp,
                    color = effectiveTint.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.2.dp,
                    color = effectiveTint
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = effectiveTint.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, effectiveTint.copy(alpha = 0.3f))
            ) {
                Text(
                    text = badge,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = effectiveTint,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentThemeMode: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themeOptions = remember {
        listOf(
            ThemeOptionItem(
                key = "system",
                title = "সিস্টেম ডিফল্ট (মোবাইল অনুযায়ী)",
                description = "মোবাইলের সিস্টেম থিম সেটিংস অনুযায়ী স্বয়ংক্রিয়ভাবে পরিবর্তিত হবে",
                icon = Icons.Default.BrightnessAuto,
                accentColor = Color(0xFF6366F1)
            ),
            ThemeOptionItem(
                key = "light",
                title = "লাইট মোড",
                description = "দিনের বেলা পড়ার জন্য উজ্জ্বল ও পরিষ্কার সাদা থিম",
                icon = Icons.Default.LightMode,
                accentColor = Color(0xFFF59E0B)
            ),
            ThemeOptionItem(
                key = "dark",
                title = "ডার্ক মোড",
                description = "রাতের বেলা ও চোখের আরামের জন্য ডার্ক থিম",
                icon = Icons.Default.DarkMode,
                accentColor = Color(0xFF8B5CF6)
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "অ্যাপ থিম নির্বাচন করুন",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                themeOptions.forEach { option ->
                    val isSelected = currentThemeMode == option.key
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        animationSpec = tween(250),
                        label = "themeContainerColor"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTheme(option.key) },
                        color = containerColor,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(option.accentColor.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = option.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.description,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectTheme(option.key) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AppFooterInfo(appVersionText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                0.8.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = appVersionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ThemeOptionItem(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

private fun isCacheEmpty(formattedSize: String): Boolean {
    val cleaned = formattedSize.trim().uppercase()
    return cleaned == "0.0 MB" ||
        cleaned == "0 MB" ||
        cleaned == "0.00 MB" ||
        cleaned == "0 KB" ||
        cleaned == "0.0 KB" ||
        cleaned == "0 B" ||
        cleaned == "০.০ MB"
}
