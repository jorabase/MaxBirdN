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
import com.example.utils.AvatarUtils
import com.example.utils.toBengaliDigits

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
    var showSessionShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "ফিরে যান",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "প্রোফাইল ও অ্যাচিভমেন্ট",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = "ডাউনলোড",
                            tint = Color(0xFF10B981)
                        )
                    }
                    IconButton(onClick = onNavigateToSavedItems) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "সংরক্ষিত আইটেম",
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    IconButton(onClick = onNavigateToEditProfile) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "প্রোফাইল এডিট",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "লগআউট",
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
                            Text("পুনরায় চেষ্টা করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is AuthState.ProfileLoaded -> {
                    val profile = authState.profile
                    ProfileContent(
                        profile = profile,
                        sessionManager = sessionManager,
                        onNavigateToSavedItems = onNavigateToSavedItems,
                        onNavigateToDownloads = onNavigateToDownloads,
                        onOpenSessionShare = { showSessionShareDialog = true }
                    )
                }
                else -> {
                    // Fallback profile display from sessionManager if AuthState is idle
                    val fallbackProfile = remember(sessionManager) {
                        UserProfile(
                            id = sessionManager?.getUserId(),
                            first_name = sessionManager?.getUserFullName() ?: "শিক্ষার্থী",
                            last_name = "",
                            avatar = sessionManager?.getUserAvatar(),
                            study_group = sessionManager?.getUserGroup()
                        )
                    }
                    ProfileContent(
                        profile = fallbackProfile,
                        sessionManager = sessionManager,
                        onNavigateToSavedItems = onNavigateToSavedItems,
                        onNavigateToDownloads = onNavigateToDownloads,
                        onOpenSessionShare = { showSessionShareDialog = true }
                    )
                }
            }

            if (showSessionShareDialog && sessionManager != null) {
                com.example.ui.dialogs.SessionShareDialog(
                    sessionManager = sessionManager,
                    onDismiss = { showSessionShareDialog = false }
                )
            }
        }
    }
}

@Composable
fun ProfileContent(
    profile: UserProfile,
    sessionManager: com.example.auth.SessionManager? = null,
    onNavigateToSavedItems: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onOpenSessionShare: () -> Unit = {}
) {
    val context = LocalContext.current
    val stats = rememberGamificationStats(context)
    var formattedCacheSize by remember {
        mutableStateOf(com.example.utils.AppCacheManager.getFormattedCacheSize(context))
    }
    val imageRequest = remember(profile.avatar, profile.first_name, context) {
        AvatarUtils.buildImageRequest(context, profile.avatar, profile.first_name)
    }
    val initial = remember(profile.first_name) {
        profile.first_name?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "S"
    }

    val fullName = remember(profile.first_name, profile.last_name) {
        "${profile.first_name ?: ""} ${profile.last_name ?: ""}".trim().ifBlank { "শিক্ষার্থী" }
    }

    val classDisplayBn = remember(profile.`class`?.display, sessionManager) {
        val raw = profile.`class`?.display ?: sessionManager?.getUserClassDisplay() ?: sessionManager?.getUserClassName() ?: ""
        when (raw.trim().uppercase()) {
            "C11", "CLASS 11", "11", "XI" -> "একাদশ শ্রেণি"
            "C12", "CLASS 12", "12", "XII" -> "দ্বাদশ শ্রেণি"
            "C10", "CLASS 10", "10", "X" -> "দশম শ্রেণি"
            "C9", "CLASS 9", "9", "IX" -> "নবম শ্রেণি"
            else -> raw.ifBlank { "এইচএসসি" }
        }
    }

    val groupDisplayBn = remember(profile.study_group, sessionManager) {
        val raw = profile.study_group ?: sessionManager?.getUserGroup() ?: ""
        when (raw.trim().lowercase()) {
            "humanities", "humanities_group", "hum", "arts" -> "মানবিক বিভাগ"
            "science", "science_group", "sci" -> "বিজ্ঞান বিভাগ"
            "business", "business_studies", "commerce", "bs", "bba" -> "ব্যবসায় শিক্ষা বিভাগ"
            else -> raw.ifBlank { "নির্ধারিত নয়" }
        }
    }

    val schoolDisplayBn = remember(profile.school?.name) {
        profile.school?.name?.takeIf { it.isNotBlank() } ?: "কলেজ / শিক্ষাপ্রতিষ্ঠান যুক্ত করুন"
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

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B),
                            border = BorderStroke(1.5.dp, Color.White),
                            modifier = Modifier.offset(y = 8.dp)
                        ) {
                            Text(
                                text = "LV. ${toBengaliDigits(stats.level)}",
                                color = Color(0xFF0F172A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
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

                    // Rank Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stats.rankTitleBn,
                                color = Color(0xFFFDE68A),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // XP Progress Bar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "মোট অভিজ্ঞতা: ${toBengaliDigits(stats.totalXp)} XP",
                                color = Color(0xFFBAE6FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${toBengaliDigits(stats.currentLevelXp)}/${toBengaliDigits(stats.nextLevelXpThreshold)} XP",
                                color = Color(0xFFC4B5FD),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { stats.levelProgressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFFF59E0B),
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3 Gamified Stat Mini-Pill Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GamifiedStatMiniBox(
                            value = "${toBengaliDigits(stats.streakDays)} দিন",
                            label = "🔥 স্ট্রিক",
                            accent = Color(0xFFF97316),
                            modifier = Modifier.weight(1f)
                        )
                        GamifiedStatMiniBox(
                            value = toBengaliDigits(stats.completedLessonsCount),
                            label = "🎓 ক্লাস সম্পন্ন",
                            accent = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        GamifiedStatMiniBox(
                            value = toBengaliDigits(stats.watchedAnimatedCount),
                            label = "🎬 অ্যানিমেশন",
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
                ProfileDetailRow(label = "শ্রেণি", value = classDisplayBn)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                ProfileDetailRow(label = "বিভাগ", value = groupDisplayBn)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                ProfileDetailRow(label = "শিক্ষাপ্রতিষ্ঠান", value = schoolDisplayBn)
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
                        text = "অফলাইন ডাউনলোড",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ইন্টারনেট ছাড়া সংরক্ষিত ভিডিও ও পিডিএফ লেকচার",
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

        // Session Share & QR Card
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
                        text = "সেশন শেয়ার ও কিউআর ব্যাকআপ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "অন্য ডিভাইসে অ্যাকাউন্ট পাঠাতে কিউআর কোড দেখুন",
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
                        text = "সংরক্ষিত আইটেম (${toBengaliDigits(stats.savedItemsCount)})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "বুকমার্ক করা প্রশ্ন, নোট ও গুরুত্বপূর্ণ ক্লাস",
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
                .clickable {
                    com.example.utils.AppCacheManager.clearAllAppCache(context)
                    formattedCacheSize = "0.0 MB"
                    android.widget.Toast.makeText(
                        context,
                        "অ্যাপের সমস্ত ক্যাশ মেমোরি সম্পূর্ণ খালি করা হয়েছে!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
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
                        text = "ক্যাশ মেমোরি পরিষ্কার করুন",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (formattedCacheSize != "0.0 MB") {
                            "$formattedCacheSize ক্যাশ জমা আছে • খালি করতে ট্যাপ করুন"
                        } else {
                            "ক্যাশ মেমোরি সম্পূর্ণ পরিষ্কার আছে (০.০ MB)"
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
