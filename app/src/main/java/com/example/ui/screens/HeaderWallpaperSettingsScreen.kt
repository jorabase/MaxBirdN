package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.auth.SessionManager
import com.example.ui.components.HomeHeader
import com.example.ui.theme.HeaderWallpaperCodeManager
import com.example.ui.theme.HeaderWallpaperConfig
import com.example.ui.theme.HeaderWallpaperPresets
import com.example.ui.theme.ImportedWallpaperTheme
import com.example.ui.theme.LiveHeaderBackground
import com.example.ui.theme.WallpaperPresetItem
import com.example.ui.theme.isAppInDarkTheme
import com.example.utils.AppLang
import com.example.utils.toBengaliDigits
import com.example.utils.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderWallpaperSettingsScreen(
    sessionManager: SessionManager = SessionManager.getInstance(LocalContext.current),
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val currentConfig by sessionManager.headerWallpaperFlow.collectAsStateWithLifecycle()
    val importedThemes by sessionManager.importedCustomThemesFlow.collectAsStateWithLifecycle()
    val isDark = isAppInDarkTheme()

    var selectedCategoryKey by remember { mutableStateOf(HeaderWallpaperPresets.CAT_ALL) }
    var showImportCodeDialog by remember { mutableStateOf(false) }

    val userName = remember {
        sessionManager.getUserFullName()?.takeIf { it.isNotBlank() } ?: AppLang.tr("Student", "শিক্ষার্থী")
    }
    val classDisplay = remember {
        sessionManager.getUserClassDisplay()?.takeIf { it.isNotBlank() }
            ?: com.example.syllabus.formatClassDisplayName(sessionManager.getUserClassName())
            ?: ""
    }
    val activeCourseTitle = remember {
        sessionManager.getActiveProgramTitleBn() ?: AppLang.tr("Academic Program", "একাডেমিক প্রোগ্রাম")
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
            }
            val updated = currentConfig.copy(
                type = HeaderWallpaperConfig.CUSTOM_IMAGE,
                customUri = uri.toString(),
                customTitle = AppLang.tr("Custom Photo", "গ্যালারি ছবি")
            )
            sessionManager.setHeaderWallpaperConfig(updated)
            Toast.makeText(
                context,
                AppLang.tr("Custom wallpaper applied", "নিজস্ব ওয়ালপেপার সেট হয়েছে"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val pageBg = if (isDark) Color(0xFF070D19) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val cardBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val filteredPresets = remember(selectedCategoryKey) {
        HeaderWallpaperPresets.presets.filter { HeaderWallpaperPresets.matchesCategory(it, selectedCategoryKey) }
    }

    val isAutoDailyActive = currentConfig.type == HeaderWallpaperConfig.PRESET_AUTO_DAILY
    val todayDayIndex = remember { HeaderWallpaperPresets.getTodayDayIndex() }
    val todayPreset = remember(todayDayIndex) { HeaderWallpaperPresets.getPresetForDayIndex(todayDayIndex) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = tr("Banner Themes", "ব্যানার থিম"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tr(
                                "${HeaderWallpaperPresets.presets.size} interactive themes • 7-day auto",
                                "${toBengaliDigits(HeaderWallpaperPresets.presets.size)}টি ইন্টারঅ্যাক্টিভ থিম • ৭ দিনের অটো"
                            ),
                            fontSize = 11.5.sp,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("wallpaper_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back", "পেছনে"),
                            tint = primaryText
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { showImportCodeDialog = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("open_theme_code_dialog_top_btn"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF8B5CF6).copy(alpha = 0.16f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = tr("Theme Code", "থিম কোড"),
                            tint = Color(0xFF8B5CF6)
                        )
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Interactive Preview Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tr("Tap or drag to interact", "টাচ বা ড্র্যাগ করে দেখুন"),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF10B981).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.28f))
                        ) {
                            Text(
                                text = if (currentConfig.isAnimationEnabled) tr("Live", "লাইভ") else tr("Static", "স্থির"),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                1.5.dp,
                                Color(0xFF38BDF8).copy(alpha = 0.35f),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        HomeHeader(
                            userName = userName,
                            subtitle = classDisplay,
                            avatarUrl = sessionManager.getUserAvatar() ?: "",
                            isPremium = true,
                            onAvatarClick = {},
                            activeCourseTitle = activeCourseTitle,
                            onOpenCourseSwitcher = {},
                            unreadNotificationCount = 2,
                            onNotificationClick = {},
                            wallpaperConfig = currentConfig
                        )
                    }
                }
            }

            // 2. Auto-Daily 7-Day Rotation Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAutoDailyActive) {
                        if (isDark) Color(0xFF0E213A) else Color(0xFFF0F9FF)
                    } else {
                        cardBg
                    }
                ),
                border = BorderStroke(
                    width = if (isAutoDailyActive) 2.dp else 1.dp,
                    color = if (isAutoDailyActive) Color(0xFF38BDF8) else cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable {
                        val nextType = if (isAutoDailyActive) todayPreset.id else HeaderWallpaperConfig.PRESET_AUTO_DAILY
                        sessionManager.setHeaderWallpaperConfig(
                            currentConfig.copy(type = nextType, customThemeCode = null)
                        )
                    }
                    .testTag("auto_daily_theme_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF0284C7), Color(0xFF6366F1), Color(0xFFEC4899))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = tr("Daily Auto Theme", "প্রতিদিন অটো থিম"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText
                                )
                                Text(
                                    text = tr(
                                        "Today: ${todayPreset.nameEn} (7-day cycle)",
                                        "আজ: ${todayPreset.nameBn} (৭ দিনের চক্র)"
                                    ),
                                    fontSize = 11.5.sp,
                                    color = if (isAutoDailyActive) Color(0xFF0284C7) else secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Switch(
                            checked = isAutoDailyActive,
                            onCheckedChange = { enabled ->
                                sessionManager.setHeaderWallpaperConfig(
                                    currentConfig.copy(
                                        type = if (enabled) HeaderWallpaperConfig.PRESET_AUTO_DAILY else todayPreset.id,
                                        customThemeCode = null
                                    )
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7)
                            )
                        )
                    }

                    // 7-Day Schedule Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (dayIdx in 0..6) {
                            val dayPreset = HeaderWallpaperPresets.getPresetForDayIndex(dayIdx)
                            val isToday = dayIdx == todayDayIndex
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isToday) dayPreset.accentColor.copy(alpha = 0.20f)
                                else if (isDark) Color(0xFF162235) else Color(0xFFF1F5F9),
                                border = BorderStroke(
                                    width = if (isToday) 1.2.dp else 0.6.dp,
                                    color = if (isToday) dayPreset.accentColor else cardBorder
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dayPreset.accentColor)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = HeaderWallpaperPresets.getDayShortLabel(dayIdx),
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isToday) primaryText else secondaryText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Action Row: Theme Code + Gallery Wallpaper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6D28D9), Color(0xFF4F46E5), Color(0xFF0284C7))
                            )
                        )
                        .clickable { showImportCodeDialog = true }
                        .testTag("import_theme_code_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = tr("Theme Code", "থিম কোড"),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = tr("Import or create", "ইমপোর্ট ও তৈরি"),
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = BorderStroke(
                        width = if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) 2.dp else 1.dp,
                        color = if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) Color(0xFFEC4899) else cardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("pick_custom_wallpaper_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEC4899).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = tr("Gallery Photo", "গ্যালারি ছবি"),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText,
                                maxLines = 1
                            )
                            Text(
                                text = tr("Use own image", "নিজের ছবি দিন"),
                                fontSize = 10.5.sp,
                                color = secondaryText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Custom Gallery Contrast Control
            AnimatedVisibility(visible = !currentConfig.customUri.isNullOrBlank()) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentConfig.customUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = tr("Saved photo", "সংরক্ষিত ছবি"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Saved Photo", "সংরক্ষিত ছবি"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Text(
                                    text = if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE)
                                        tr("Active", "চালু আছে")
                                    else tr("Tap to apply", "ব্যবহার করতে ট্যাপ করুন"),
                                    fontSize = 11.5.sp,
                                    color = if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) Color(0xFF10B981) else secondaryText
                                )
                            }
                            if (currentConfig.type != HeaderWallpaperConfig.CUSTOM_IMAGE) {
                                FilledTonalButton(
                                    onClick = {
                                        sessionManager.setHeaderWallpaperConfig(
                                            currentConfig.copy(type = HeaderWallpaperConfig.CUSTOM_IMAGE)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(tr("Apply", "সেট করুন"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tr("Dark overlay", "ডার্ক কন্ট্রাস্ট"),
                                    fontSize = 11.5.sp,
                                    color = secondaryText
                                )
                                Text(
                                    text = "${toBengaliDigits((currentConfig.dimOpacity * 100).toInt())}%",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEC4899)
                                )
                            }
                            Slider(
                                value = currentConfig.dimOpacity.coerceIn(0.1f, 0.8f),
                                onValueChange = { newDim ->
                                    sessionManager.setHeaderWallpaperConfig(
                                        currentConfig.copy(dimOpacity = newDim)
                                    )
                                },
                                valueRange = 0.1f..0.8f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFEC4899),
                                    activeTrackColor = Color(0xFFEC4899)
                                )
                            )
                        }
                    }
                }
            }

            // Imported Custom Code Themes
            if (importedThemes.isNotEmpty() || currentConfig.type == HeaderWallpaperConfig.CUSTOM_CODE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr("Custom Codes (${importedThemes.size})", "কাস্টম কোড (${toBengaliDigits(importedThemes.size)})"),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        TextButton(onClick = { showImportCodeDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(tr("Add", "নতুন"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    importedThemes.forEach { customTheme ->
                        val isSelected = currentConfig.type == HeaderWallpaperConfig.CUSTOM_CODE &&
                            currentConfig.customThemeCode == customTheme.toShareableCode()

                        ImportedThemeCard(
                            theme = customTheme,
                            isSelected = isSelected,
                            isDark = isDark,
                            onSelect = {
                                sessionManager.setHeaderWallpaperConfig(
                                    currentConfig.copy(
                                        type = HeaderWallpaperConfig.CUSTOM_CODE,
                                        customTitle = customTheme.titleBn,
                                        customThemeCode = customTheme.toShareableCode()
                                    )
                                )
                            },
                            onCopyCode = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("MaxBird Theme Code", customTheme.toShareableCode())
                                )
                                Toast.makeText(
                                    context,
                                    AppLang.tr("Theme code copied", "থিম কোড কপি হয়েছে"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = {
                                sessionManager.deleteImportedCustomTheme(customTheme.id)
                                if (isSelected) {
                                    sessionManager.setHeaderWallpaperConfig(
                                        currentConfig.copy(
                                            type = HeaderWallpaperConfig.PRESET_COSMIC,
                                            customThemeCode = null
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // 4. Live Animation Switch
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0EA5E9).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = Color(0xFF0EA5E9),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = tr("Live Motion", "লাইভ অ্যানিমেশন"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                            Text(
                                text = tr("Smooth seamless motion", "মসৃণ ও নিরবচ্ছিন্ন গতি"),
                                fontSize = 11.5.sp,
                                color = secondaryText
                            )
                        }
                    }
                    Switch(
                        checked = currentConfig.isAnimationEnabled,
                        onCheckedChange = { enabled ->
                            sessionManager.setHeaderWallpaperConfig(
                                currentConfig.copy(isAnimationEnabled = enabled)
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }
            }

            // 5. 7 Interactive Themes Collection
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = tr(
                            "Interactive Themes (${HeaderWallpaperPresets.presets.size})",
                            "ইন্টারঅ্যাক্টিভ থিম (${toBengaliDigits(HeaderWallpaperPresets.presets.size)})"
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText
                    )
                }

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeaderWallpaperPresets.categoryKeys.forEach { key ->
                        val selected = selectedCategoryKey == key
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategoryKey = key },
                            label = {
                                Text(
                                    text = HeaderWallpaperPresets.categoryLabel(key),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = cardBg,
                                labelColor = secondaryText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = if (selected) Color(0xFF0284C7) else cardBorder
                            ),
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                filteredPresets.forEach { preset ->
                    val isSelected = currentConfig.type == preset.id
                    LivePresetThemeCard(
                        preset = preset,
                        isSelected = isSelected,
                        isDark = isDark,
                        onSelect = {
                            sessionManager.setHeaderWallpaperConfig(
                                currentConfig.copy(
                                    type = preset.id,
                                    customThemeCode = null
                                )
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showImportCodeDialog) {
        ThemeCodeStudioDialog(
            isDark = isDark,
            onDismiss = { showImportCodeDialog = false },
            onApplyImportedTheme = { imported ->
                sessionManager.addImportedCustomTheme(imported)
                sessionManager.setHeaderWallpaperConfig(
                    currentConfig.copy(
                        type = HeaderWallpaperConfig.CUSTOM_CODE,
                        customTitle = imported.titleBn,
                        customThemeCode = imported.toShareableCode()
                    )
                )
                showImportCodeDialog = false
                Toast.makeText(
                    context,
                    AppLang.tr("'${imported.titleBn}' applied", "'${imported.titleBn}' চালু হয়েছে"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun LivePresetThemeCard(
    preset: WallpaperPresetItem,
    isSelected: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val borderCol = if (isSelected) preset.accentColor else if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                preset.accentColor.copy(alpha = if (isDark) 0.14f else 0.08f)
            } else {
                cardBg
            }
        ),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderCol),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
            .testTag("preset_wallpaper_${preset.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        preset.accentColor.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                LiveHeaderBackground(
                    config = HeaderWallpaperConfig(
                        type = preset.id,
                        isAnimationEnabled = true
                    ),
                    modifier = Modifier.fillMaxSize(),
                    interactive = false
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.52f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = tr("TOUCH", "টাচ"),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = preset.displayName,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = preset.accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = preset.displayCategory,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = preset.accentColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.displayDescription,
                    fontSize = 11.5.sp,
                    color = secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = preset.accentColor,
                    unselectedColor = secondaryText
                )
            )
        }
    }
}

@Composable
private fun ImportedThemeCard(
    theme: ImportedWallpaperTheme,
    isSelected: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit,
    onCopyCode: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentCol = HeaderWallpaperCodeManager.parseColorSafe(theme.accentHex, Color(0xFF8B5CF6))

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentCol.copy(alpha = 0.14f) else cardBg
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) accentCol else if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, accentCol.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                LiveHeaderBackground(
                    config = HeaderWallpaperConfig(
                        type = HeaderWallpaperConfig.CUSTOM_CODE,
                        customThemeCode = theme.toShareableCode(),
                        isAnimationEnabled = true
                    ),
                    modifier = Modifier.fillMaxSize(),
                    interactive = false
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.titleBn,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${theme.effectStyle} • ${theme.primaryHex} → ${theme.accentHex}",
                    fontSize = 11.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onCopyCode, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = tr("Copy Code", "কোড কপি"),
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = tr("Delete", "মুছুন"),
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeCodeStudioDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onApplyImportedTheme: (ImportedWallpaperTheme) -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) }
    var codeInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var builderTitle by remember { mutableStateOf(AppLang.tr("My Live Theme", "আমার লাইভ থিম")) }
    var builderStyle by remember { mutableStateOf("AURORA") }
    var primaryHex by remember { mutableStateOf("#061329") }
    var secondaryHex by remember { mutableStateOf("#1E1B4B") }
    var accentHex by remember { mutableStateOf("#38BDF8") }
    var particleHex by remember { mutableStateOf("#FDE68A") }

    val builderPreviewTheme = remember(builderTitle, builderStyle, primaryHex, secondaryHex, accentHex, particleHex) {
        ImportedWallpaperTheme(
            titleBn = builderTitle.ifBlank { AppLang.tr("Custom Theme", "কাস্টম থিম") },
            effectStyle = builderStyle,
            primaryHex = primaryHex,
            secondaryHex = secondaryHex,
            accentHex = accentHex,
            particleHex = particleHex,
            speedMultiplier = 1
        )
    }

    val parsedImportPreview = remember(codeInput) {
        if (codeInput.isBlank()) null else HeaderWallpaperCodeManager.parseThemeCode(codeInput).getOrNull()
    }

    val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF0284C7)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = tr("Theme Code Studio", "থিম কোড স্টুডিও"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText
                    )
                    Text(
                        text = tr("Import or build custom themes", "কাস্টম থিম ইমপোর্ট বা তৈরি করুন"),
                        fontSize = 11.sp,
                        color = secondaryText
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0 to tr("Import Code", "কোড ইমপোর্ট"),
                        1 to tr("Create", "তৈরি করুন")
                    ).forEach { (idx, label) ->
                        val selected = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Color(0xFF4F46E5) else Color.Transparent)
                                .clickable { activeTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else secondaryText
                            )
                        }
                    }
                }

                if (activeTab == 0) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = {
                            codeInput = it
                            errorMsg = null
                        },
                        label = { Text(tr("Paste theme code", "থিম কোড পেস্ট করুন"), fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                "MAXTHEME:v1|Name|AURORA|#090D16|#1E1B4B|#38BDF8|#FFF|1",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        minLines = 3,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (!pasted.isNullOrBlank()) {
                                    codeInput = pasted
                                    errorMsg = null
                                } else {
                                    Toast.makeText(
                                        context,
                                        AppLang.tr("Clipboard is empty", "ক্লিপবোর্ডে কোনো কোড নেই"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Paste", "পেস্ট"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (codeInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = { codeInput = ""; errorMsg = null },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(tr("Clear", "মুছুন"), fontSize = 12.sp)
                            }
                        }
                    }

                    if (!errorMsg.isNullOrBlank()) {
                        Text(
                            text = errorMsg ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (parsedImportPreview != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(14.dp))
                        ) {
                            LiveHeaderBackground(
                                config = HeaderWallpaperConfig(
                                    type = HeaderWallpaperConfig.CUSTOM_CODE,
                                    customThemeCode = parsedImportPreview.toShareableCode(),
                                    isAnimationEnabled = true
                                ),
                                modifier = Modifier.fillMaxSize(),
                                interactive = false
                            )
                            Text(
                                text = "✓ ${parsedImportPreview.titleBn}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            )
                        }
                    }

                    Text(
                        text = tr("Sample Codes", "স্যাম্পল কোড"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HeaderWallpaperCodeManager.sampleCommunityCodes.forEach { sample ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        codeInput = sample.toShareableCode()
                                        errorMsg = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(sample.parseColors()))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sample.titleBn,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText
                                        )
                                        Text(
                                            text = sample.effectStyle,
                                            fontSize = 10.sp,
                                            color = secondaryText
                                        )
                                    }
                                    Text(
                                        text = tr("Use", "ব্যবহার"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        LiveHeaderBackground(
                            config = HeaderWallpaperConfig(
                                type = HeaderWallpaperConfig.CUSTOM_CODE,
                                customThemeCode = builderPreviewTheme.toShareableCode(),
                                isAnimationEnabled = true
                            ),
                            modifier = Modifier.fillMaxSize(),
                            interactive = false
                        )
                        Text(
                            text = builderPreviewTheme.titleBn,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = builderTitle,
                        onValueChange = { builderTitle = it },
                        label = { Text(tr("Theme Name", "থিমের নাম"), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = tr("Effect Style", "ইফেক্ট স্টাইল"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HeaderWallpaperCodeManager.effectStyles.forEach { (styleKey, _) ->
                            FilterChip(
                                selected = builderStyle == styleKey,
                                onClick = { builderStyle = styleKey },
                                label = { Text(styleKey, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = primaryHex,
                            onValueChange = { primaryHex = it },
                            label = { Text(tr("Primary", "মূল রঙ"), fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = accentHex,
                            onValueChange = { accentHex = it },
                            label = { Text(tr("Accent", "অ্যাকসেন্ট"), fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = secondaryHex,
                            onValueChange = { secondaryHex = it },
                            label = { Text(tr("Secondary", "দ্বিতীয়"), fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = particleHex,
                            onValueChange = { particleHex = it },
                            label = { Text(tr("Particles", "কণা"), fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (activeTab == 0) {
                        val result = HeaderWallpaperCodeManager.parseThemeCode(codeInput)
                        result.onSuccess { theme ->
                            onApplyImportedTheme(theme)
                        }.onFailure { err ->
                            errorMsg = err.message
                        }
                    } else {
                        onApplyImportedTheme(builderPreviewTheme.copy(rawCode = builderPreviewTheme.toShareableCode()))
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text(tr("Apply", "চালু করুন"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel", "বাতিল"), color = secondaryText)
            }
        }
    )
}
