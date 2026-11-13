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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderWallpaperSettingsScreen(
    sessionManager: SessionManager = SessionManager(LocalContext.current),
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val currentConfig by sessionManager.headerWallpaperFlow.collectAsState()
    val importedThemes by sessionManager.importedCustomThemesFlow.collectAsState()
    val isDark = isAppInDarkTheme()

    var selectedCategory by remember { mutableStateOf("সব থিম") }
    var showImportCodeDialog by remember { mutableStateOf(false) }

    val userName = remember {
        sessionManager.getUserFullName()?.takeIf { it.isNotBlank() } ?: "শিক্ষার্থী"
    }
    val classDisplay = remember {
        sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "HSC '26"
    }
    val activeCourseTitle = remember {
        sessionManager.getActiveProgramTitleBn() ?: "অ্যাকাডেমিক প্রোগ্রাম"
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
                customTitle = "কাস্টম গ্যালারি ওয়ালপেপার"
            )
            sessionManager.setHeaderWallpaperConfig(updated)
            Toast.makeText(context, "নিজস্ব ওয়ালপেপার সেট করা হয়েছে!", Toast.LENGTH_SHORT).show()
        }
    }

    val pageBg = if (isDark) Color(0xFF070D19) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF111C2D) else Color.White
    val cardBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val filteredPresets = remember(selectedCategory) {
        if (selectedCategory == "সব থিম") {
            HeaderWallpaperPresets.presets
        } else {
            HeaderWallpaperPresets.presets.filter { it.categoryBn == selectedCategory }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "হেডার থিম স্টুডিও",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${com.example.utils.toBengaliDigits(HeaderWallpaperPresets.presets.size)}টি প্রিমিয়াম লাইভ থিম ও কাস্টম কোড",
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
                            contentDescription = "Back",
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
                            contentDescription = "Import Theme Code",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Interactive Header Preview Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "লাইভ হেডার প্রিভিউ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF10B981).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AllInclusive,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (currentConfig.isAnimationEnabled) "অবিরাম ৬০ FPS লুপ" else "স্থির মোড",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
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
                            subtitle = "$classDisplay • বিজ্ঞান",
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

            // 2. Action Row: Import/Build Theme Code + Pick Custom Gallery Image
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Theme Code Import & Studio Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6D28D9), Color(0xFF4F46E5), Color(0xFF0284C7))
                            )
                        )
                        .clickable { showImportCodeDialog = true }
                        .testTag("import_theme_code_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.22f)
                            ) {
                                Text(
                                    text = "নতুন",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "থিম কোড ইমপোর্ট",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "কোড পেস্ট বা নতুন থিম তৈরি করুন",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Custom Gallery Image Picker Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(
                        width = if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) 2.dp else 1.dp,
                        color = if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) Color(0xFFEC4899) else cardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("pick_custom_wallpaper_btn")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEC4899).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "গ্যালারি ওয়ালপেপার",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = "নিজের পছন্দের ছবি সেট করুন",
                            fontSize = 11.sp,
                            color = secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Custom Image Active Preview & Brightness/Contrast Control
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
                                contentDescription = "Selected Custom Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "সংরক্ষিত গ্যালারি ওয়ালপেপার",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryText
                                )
                                Text(
                                    text = if (currentConfig.type == HeaderWallpaperConfig.CUSTOM_IMAGE) "বর্তমানে ব্যবহৃত হচ্ছে" else "পুনরায় ব্যবহার করতে ট্যাপ করুন",
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
                                    Text("সেট করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    text = "ছবির ডার্ক কন্ট্রাস্ট (লেখা স্পষ্ট করার জন্য)",
                                    fontSize = 11.5.sp,
                                    color = secondaryText
                                )
                                Text(
                                    text = "${(currentConfig.dimOpacity * 100).toInt()}%",
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

            // 3. Imported / Saved Custom Code Themes Section
            if (importedThemes.isNotEmpty() || currentConfig.type == HeaderWallpaperConfig.CUSTOM_CODE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.IntegrationInstructions,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "আপনার ইমপোর্ট করা থিম কোড (${importedThemes.size})",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryText
                            )
                        }
                        TextButton(onClick = { showImportCodeDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("নতুন কোড", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                Toast.makeText(context, "থিম কোড কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
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

            // 4. Seamless Infinite Animation Toggle
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0EA5E9).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = Color(0xFF0EA5E9),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "নন-স্টপ সিমলেস অ্যানিমেশন",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryText
                            )
                            Text(
                                text = "কোনো থেমে যাওয়া বা রিস্টার্ট ঝাঁকুনি ছাড়াই অবিরাম চলবে",
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

            // 5. Built-in 16 Creative Live Themes Header & Category Filter Chips
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = "প্রিমিয়াম লাইভ অ্যানিমেটেড থিম (${com.example.utils.toBengaliDigits(HeaderWallpaperPresets.presets.size)}টি)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                    }
                }

                // Category Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeaderWallpaperPresets.categories.forEach { category ->
                        val selected = selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    text = category,
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

                // Presets List with Live Mini-Canvas Previews
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
                Toast.makeText(context, "'${imported.titleBn}' থিমটি সফলভাবে চালু হয়েছে!", Toast.LENGTH_SHORT).show()
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
            // Live Animated Mini-Preview Box
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(64.dp)
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
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.48f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = "LIVE",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = preset.nameBn,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = preset.accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = preset.categoryBn,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = preset.accentColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.description,
                    fontSize = 11.5.sp,
                    color = secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

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
                    .width(78.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, accentCol.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                LiveHeaderBackground(
                    config = HeaderWallpaperConfig(
                        type = HeaderWallpaperConfig.CUSTOM_CODE,
                        customThemeCode = theme.toShareableCode(),
                        isAnimationEnabled = true
                    ),
                    modifier = Modifier.fillMaxSize()
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
                    text = "ইফেক্ট: ${theme.effectStyle} • ${theme.primaryHex} → ${theme.accentHex}",
                    fontSize = 11.sp,
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onCopyCode, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemeCodeStudioDialog(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onApplyImportedTheme: (ImportedWallpaperTheme) -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Import Code, 1 = Build Custom Code
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Custom Theme Builder states
    var customName by remember { mutableStateOf("আমার স্পেশাল থিম") }
    var selectedStyle by remember { mutableStateOf("AURORA") }
    var selectedPaletteIndex by remember { mutableIntStateOf(0) }

    val colorPalettes = remember {
        listOf(
            Triple("#050816", "#1E1B4B", "#38BDF8") to "কসমিক সায়ান",
            Triple("#090418", "#3B0764", "#EC4899") to "নিয়ন ম্যাজেন্টা",
            Triple("#022C22", "#064E3B", "#10B981") to "এমারেল্ড অরোরা",
            Triple("#180409", "#4C0519", "#F43F5E") to "ক্রিমসন ফ্লেম",
            Triple("#0F0B05", "#422006", "#F59E0B") to "রয়েল গোল্ড",
            Triple("#070A1E", "#1E1B4B", "#A855F7") to "অ্যামেথিস্ট নাইট"
        )
    }

    val generatedBuilderTheme = remember(customName, selectedStyle, selectedPaletteIndex) {
        val (c1, c2, c3) = colorPalettes[selectedPaletteIndex].first
        ImportedWallpaperTheme(
            titleBn = customName.ifBlank { "কাস্টম লাইভ থিম" },
            effectStyle = selectedStyle,
            primaryHex = c1,
            secondaryHex = c2,
            accentHex = c3,
            particleHex = "#FFFFFF",
            speedMultiplier = 1
        )
    }

    val dialogBg = if (isDark) Color(0xFF0F172A) else Color.White
    val primaryText = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val secondaryText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6)
                        )
                    }
                    Column {
                        Text(
                            text = "থিম কোড ইমপোর্ট ও মেকার",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryText
                        )
                        Text(
                            text = "নতুন থিমের কোড পেস্ট করুন বা নিজের থিম বানান",
                            fontSize = 11.5.sp,
                            color = secondaryText
                        )
                    }
                }

                // Mode Switcher Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("কোড ইমপোর্ট করুন", "নতুন কোড তৈরি করুন").forEachIndexed { idx, label ->
                        val selected = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Color(0xFF8B5CF6) else Color.Transparent)
                                .clickable {
                                    activeTab = idx
                                    errorMessage = null
                                }
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
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTab == 0) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = {
                            codeInput = it
                            errorMessage = null
                        },
                        label = { Text("থিম কোড পেস্ট করুন (MAXTHEME:... বা #Hex)") },
                        placeholder = {
                            Text(
                                "MAXTHEME:v1|নিয়ন গ্যালাক্সি|GALAXY|#050816|#1E1B4B|#A855F7|#38BDF8|1",
                                fontSize = 11.sp
                            )
                        },
                        minLines = 3,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("theme_code_input_field"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (!clipText.isNullOrBlank()) {
                                    codeInput = clipText.trim()
                                    errorMessage = null
                                } else {
                                    Toast.makeText(context, "ক্লিপবোর্ডে কোনো কোড পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পেস্ট করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (codeInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    codeInput = ""
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("মুছুন", fontSize = 12.sp)
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }

                    // Live preview if valid code is typed
                    val previewParsed = remember(codeInput) {
                        HeaderWallpaperCodeManager.parseThemeCode(codeInput).getOrNull()
                    }
                    if (previewParsed != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(74.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, Color(0xFF10B981), RoundedCornerShape(14.dp))
                        ) {
                            LiveHeaderBackground(
                                config = HeaderWallpaperConfig(
                                    type = HeaderWallpaperConfig.CUSTOM_CODE,
                                    customThemeCode = previewParsed.toShareableCode(),
                                    isAnimationEnabled = true
                                ),
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "প্রিভিউ: ${previewParsed.titleBn} (${previewParsed.effectStyle})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = secondaryText.copy(alpha = 0.2f))

                    Text(
                        text = "রেডিমেড বোনাস থিম কোড (১ ট্যাপে টেস্ট করুন):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )

                    HeaderWallpaperCodeManager.sampleCommunityCodes.forEach { sample ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, secondaryText.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    codeInput = sample.toShareableCode()
                                    errorMessage = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sample.titleBn,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryText
                                    )
                                    Text(
                                        text = sample.toShareableCode(),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = secondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Tab 1: Custom Theme Builder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, Color(0xFF8B5CF6), RoundedCornerShape(16.dp))
                    ) {
                        LiveHeaderBackground(
                            config = HeaderWallpaperConfig(
                                type = HeaderWallpaperConfig.CUSTOM_CODE,
                                customThemeCode = generatedBuilderTheme.toShareableCode(),
                                isAnimationEnabled = true
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "${generatedBuilderTheme.titleBn} • LIVE",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("থিমের নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "অ্যানিমেশন ইঞ্জিন নির্বাচন করুন:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeaderWallpaperCodeManager.effectStyles.forEach { (styleCode, styleLabel) ->
                            val selected = selectedStyle == styleCode
                            FilterChip(
                                selected = selected,
                                onClick = { selectedStyle = styleCode },
                                label = { Text(styleLabel, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    Text(
                        text = "কালার প্যালেট:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorPalettes.forEachIndexed { idx, (_, label) ->
                            val selected = selectedPaletteIndex == idx
                            FilterChip(
                                selected = selected,
                                onClick = { selectedPaletteIndex = idx },
                                label = { Text(label, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = generatedBuilderTheme.toShareableCode(),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = secondaryText,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("MaxBird Theme Code", generatedBuilderTheme.toShareableCode())
                                    )
                                    Toast.makeText(context, "কোড কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF8B5CF6))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (activeTab == 0) {
                        val result = HeaderWallpaperCodeManager.parseThemeCode(codeInput)
                        result.onSuccess { parsed ->
                            onApplyImportedTheme(parsed)
                        }.onFailure { err ->
                            errorMessage = err.message ?: "সঠিক থিম কোড দিন"
                        }
                    } else {
                        onApplyImportedTheme(
                            generatedBuilderTheme.copy(rawCode = generatedBuilderTheme.toShareableCode())
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("apply_theme_code_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ইমপোর্ট ও চালু করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", color = secondaryText, fontWeight = FontWeight.Bold)
            }
        }
    )
}
