package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.api.UserProfile
import com.example.auth.AuthState
import com.example.auth.AuthViewModel
import com.example.gamification.rememberGamificationStats
import com.example.utils.AppLang
import com.example.utils.AvatarUtils
import com.example.utils.isBangla
import com.example.utils.toBengaliDigits
import com.example.utils.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    authState: AuthState,
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToSavedItems: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    sessionManager: com.example.auth.SessionManager? = null,
    onBack: (() -> Unit)? = null,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var showSessionShareDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var canViewProfile by remember {
        mutableStateOf(com.example.security.DeviceActivationRepository.isProfileViewPermissionCached(context))
    }

    LaunchedEffect(Unit) {
        viewModel.fetchProfile()
        canViewProfile = com.example.security.DeviceActivationRepository.checkProfileViewPermission(
            context = context,
            forceRefresh = true
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Back", "ফিরে যান"),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = tr("Profile", "প্রোফাইল"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = tr("Downloads", "ডাউনলোড"),
                            tint = Color(0xFF10B981)
                        )
                    }
                    IconButton(onClick = onNavigateToSavedItems) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = tr("Saved", "সংরক্ষিত"),
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    IconButton(onClick = onNavigateToEditProfile) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = tr("Edit Profile", "প্রোফাইল এডিট"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showLogoutConfirmDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = tr("Log Out", "লগআউট"),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (authState) {
                is AuthState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is AuthState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = authState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = { viewModel.fetchProfile() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Retry", "পুনরায় চেষ্টা করুন"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is AuthState.ProfileLoaded -> {
                    val profile = authState.profile
                    ProfileContent(
                        profile = profile,
                        sessionManager = sessionManager,
                        canViewProfile = canViewProfile,
                        onNavigateToSavedItems = onNavigateToSavedItems,
                        onNavigateToDownloads = onNavigateToDownloads,
                        onOpenSessionShare = { if (canViewProfile) showSessionShareDialog = true }
                    )
                }
                else -> {
                    val isBn = isBangla()
                    // Fallback profile display from sessionManager if AuthState is idle
                    val fallbackProfile = remember(sessionManager, isBn) {
                        UserProfile(
                            id = sessionManager?.getUserId(),
                            first_name = sessionManager?.getUserFullName() ?: AppLang.tr("Student", "শিক্ষার্থী"),
                            last_name = "",
                            avatar = sessionManager?.getUserAvatar(),
                            study_group = sessionManager?.getUserGroup()
                        )
                    }
                    ProfileContent(
                        profile = fallbackProfile,
                        sessionManager = sessionManager,
                        canViewProfile = canViewProfile,
                        onNavigateToSavedItems = onNavigateToSavedItems,
                        onNavigateToDownloads = onNavigateToDownloads,
                        onOpenSessionShare = { if (canViewProfile) showSessionShareDialog = true }
                    )
                }
            }

            if (showSessionShareDialog && canViewProfile && sessionManager != null) {
                com.example.ui.dialogs.SessionShareDialog(
                    sessionManager = sessionManager,
                    onDismiss = { showSessionShareDialog = false }
                )
            }

            if (showLogoutConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutConfirmDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = { Text(tr("Log Out?", "লগআউট করতে চান?"), fontWeight = FontWeight.Bold) },
                    text = { Text(tr("Are you sure you want to log out of your account?", "আপনি কি নিশ্চিত যে আপনার অ্যাকাউন্ট থেকে লগআউট করতে চান?")) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLogoutConfirmDialog = false
                                onLogout()
                            }
                        ) {
                            Text(
                                tr("Log Out", "লগআউট"),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutConfirmDialog = false }) {
                            Text(tr("Cancel", "না, থাক"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileContent(
    profile: UserProfile,
    sessionManager: com.example.auth.SessionManager? = null,
    canViewProfile: Boolean = false,
    onNavigateToSavedItems: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onOpenSessionShare: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val stats = rememberGamificationStats(context)
    var rawCacheSize by remember {
        mutableStateOf(com.example.utils.AppCacheManager.getFormattedCacheSize(context))
    }
    var isClearingCache by remember { mutableStateOf(false) }
    val imageRequest = remember(profile.avatar, profile.first_name, context) {
        AvatarUtils.buildImageRequest(context, profile.avatar, profile.first_name)
    }
    val initial = remember(profile.first_name) {
        profile.first_name?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "S"
    }

    val isBn = isBangla()
    val fullName = remember(profile.first_name, profile.last_name, isBn) {
        "${profile.first_name ?: ""} ${profile.last_name ?: ""}".trim().ifBlank { AppLang.tr("Student", "শিক্ষার্থী") }
    }

    val classDisplayBn = remember(profile.`class`?.display, sessionManager, isBn) {
        val raw = profile.`class`?.display ?: sessionManager?.getUserClassDisplay() ?: sessionManager?.getUserClassName() ?: ""
        when (raw.trim().uppercase()) {
            "C11", "CLASS 11", "11", "XI" -> AppLang.tr("Class 11", "একাদশ শ্রেণি")
            "C12", "CLASS 12", "12", "XII" -> AppLang.tr("Class 12", "দ্বাদশ শ্রেণি")
            "C10", "CLASS 10", "10", "X" -> AppLang.tr("Class 10", "দশম শ্রেণি")
            "C9", "CLASS 9", "9", "IX" -> AppLang.tr("Class 9", "নবম শ্রেণি")
            else -> raw.ifBlank { AppLang.tr("HSC", "এইচএসসি") }
        }
    }

    val groupDisplayBn = remember(profile.study_group, sessionManager, isBn) {
        val raw = profile.study_group ?: sessionManager?.getUserGroup() ?: ""
        when (raw.trim().lowercase()) {
            "humanities", "humanities_group", "hum", "arts" -> AppLang.tr("Humanities", "মানবিক বিভাগ")
            "science", "science_group", "sci" -> AppLang.tr("Science", "বিজ্ঞান বিভাগ")
            "business", "business_studies", "commerce", "bs", "bba" -> AppLang.tr("Business Studies", "ব্যবসায় শিক্ষা বিভাগ")
            else -> raw.ifBlank { AppLang.tr("Not Set", "নির্ধারিত নয়") }
        }
    }

    val schoolDisplayBn = remember(profile.school?.name, isBn) {
        profile.school?.name?.takeIf { it.isNotBlank() } ?: AppLang.tr("Add Institution", "শিক্ষাপ্রতিষ্ঠান যুক্ত করুন")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Gamified Hero Avatar & Level Banner Card
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.2.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(26.dp), spotColor = Color(0xFF6366F1))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E1B4B),
                                Color(0xFF311068)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFF38BDF8))
                                    )
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = imageRequest,
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Text(
                                        text = initial,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            )
                        }

                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = fullName,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!profile.user?.phone.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profile.user?.phone ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Student Status Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = tr("Active Student", "সক্রিয় শিক্ষার্থী"),
                                color = Color(0xFFD1FAE5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))



                    // 3 Gamified Stat Mini-Pill Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GamifiedStatMiniBox(
                            value = tr("${stats.streakDays}d", "${toBengaliDigits(stats.streakDays)} দিন"),
                            label = tr("🔥 Streak", "🔥 স্ট্রিক"),
                            accent = Color(0xFFF97316),
                            modifier = Modifier.weight(1f)
                        )
                        GamifiedStatMiniBox(
                            value = toBengaliDigits(stats.completedLessonsCount),
                            label = tr("🎓 Lessons", "🎓 ক্লাস সম্পন্ন"),
                            accent = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        GamifiedStatMiniBox(
                            value = toBengaliDigits(stats.watchedAnimatedCount),
                            label = tr("🎬 Animated", "🎬 অ্যানিমেশন"),
                            accent = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Academic Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileDetailRow(label = tr("Class", "শ্রেণি"), value = classDisplayBn)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                ProfileDetailRow(label = tr("Group", "বিভাগ"), value = groupDisplayBn)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                ProfileDetailRow(label = tr("Institution", "শিক্ষাপ্রতিষ্ঠান"), value = schoolDisplayBn)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Offline Downloads Navigation Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToDownloads),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Downloads", "ডাউনলোড"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tr("Saved offline videos & PDFs", "অফলাইন ভিডিও ও পিডিএফ লেকচার"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        if (canViewProfile) {
            Spacer(modifier = Modifier.height(12.dp))

            // Session Share & QR Card (Only visible when Profile View Permission is ON)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSessionShare),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Session QR", "সেশন কিউআর"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tr("Transfer account via QR code", "কিউআর দিয়ে অন্য ডিভাইসে সেশন নিন"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Saved Items Navigation Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToSavedItems),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr(
                            "Saved (${stats.savedItemsCount})",
                            "সংরক্ষিত (${toBengaliDigits(stats.savedItemsCount)})"
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tr("Bookmarked questions, notes & classes", "বুকমার্ক করা প্রশ্ন, নোট ও ক্লাস"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Clear Storage Cache Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isClearingCache) {
                    isClearingCache = true
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            com.example.utils.AppCacheManager.clearAllAppCache(context)
                        }
                        rawCacheSize = "0.0 MB"
                        isClearingCache = false
                        android.widget.Toast.makeText(
                            context,
                            AppLang.tr("App cache cleared!", "অ্যাপের ক্যাশ মেমোরি খালি করা হয়েছে!"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Clear Cache", "ক্যাশ পরিষ্কার"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val isCacheEmpty = rawCacheSize == "0.0 MB" || rawCacheSize == "0 B" || rawCacheSize.startsWith("0.0 ")
                    Text(
                        text = when {
                            isClearingCache -> tr("Clearing cache...", "ক্যাশ পরিষ্কার করা হচ্ছে...")
                            !isCacheEmpty -> tr(
                                "$rawCacheSize cached • Tap to clear",
                                "${toBengaliDigits(rawCacheSize)} ক্যাশ জমা আছে • খালি করতে ট্যাপ করুন"
                            )
                            else -> tr("Cache is clean (0.0 MB)", "ক্যাশ পরিষ্কার আছে (০.০ MB)")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun GamifiedStatMiniBox(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
