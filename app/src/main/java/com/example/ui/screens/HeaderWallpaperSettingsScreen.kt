package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.auth.SessionManager
import com.example.ui.components.HomeHeader
import com.example.ui.theme.HeaderWallpaperConfig
import com.example.ui.theme.HeaderWallpaperPresets
import com.example.ui.theme.WallpaperPresetItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderWallpaperSettingsScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentConfig by sessionManager.headerWallpaperFlow.collectAsState()

    var selectedType by remember(currentConfig) { mutableStateOf(currentConfig.type) }
    var customUri by remember(currentConfig) { mutableStateOf(currentConfig.customUri) }
    var isAnimEnabled by remember(currentConfig) { mutableStateOf(currentConfig.isAnimationEnabled) }
    var showGuideDialog by remember { mutableStateOf(false) }

    // Live preview config
    val previewConfig = remember(selectedType, customUri, isAnimEnabled) {
        HeaderWallpaperConfig(
            type = selectedType,
            customUri = customUri,
            isAnimationEnabled = isAnimEnabled
        )
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            customUri = uri.toString()
            selectedType = HeaderWallpaperConfig.CUSTOM_IMAGE
            Toast.makeText(context, "কাস্টম ওয়ালপেপার নির্বাচিত হয়েছে!", Toast.LENGTH_SHORT).show()
        }
    }

    val userName = remember { sessionManager.getUserFullName() ?: "শিক্ষার্থী" }
    val userAvatar = remember { sessionManager.getUserAvatar() ?: "" }
    val userClass = remember { sessionManager.getUserClassDisplay() ?: "একাদশ শ্রেণি • মানবিক" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "হেডার ও লাইভ ওয়ালপেপার",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            val newConfig = HeaderWallpaperConfig(
                                type = selectedType,
                                customUri = customUri,
                                isAnimationEnabled = isAnimEnabled
                            )
                            sessionManager.setHeaderWallpaperConfig(newConfig)
                            Toast.makeText(context, "হেডার ওয়ালপেপার সফলভাবে প্রয়োগ করা হয়েছে! ✨", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ওয়ালপেপার সংরক্ষণ ও প্রয়োগ করুন",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
            // ===== 1. Live Interactive Preview =====
            Text(
                text = "লাইভ প্রিভিউ (হোম স্ক্রিন যেমন দেখাবে)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 4.dp
            ) {
                HomeHeader(
                    userName = userName,
                    subtitle = userClass,
                    avatarUrl = userAvatar,
                    isPremium = true,
                    activeCourseTitle = "HSC '27 মানবিক - ২য় বর্ষ প্রস্তুতি",
                    onAvatarClick = {},
                    onOpenCourseSwitcher = {},
                    wallpaperConfig = previewConfig
                )
            }

            // ===== 2. Preset Themes Section =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "বিল্ট-ইন লাইভ ওয়ালপেপার থিম",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "৫টি থিম",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Preset Cards Grid/List
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderWallpaperPresets.presets.forEach { preset ->
                    val isSelected = selectedType == preset.id
                    PresetThemeCard(
                        preset = preset,
                        isSelected = isSelected,
                        onClick = {
                            selectedType = preset.id
                        }
                    )
                }
            }

            // ===== 3. Custom Imported Wallpaper Section =====
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (selectedType == HeaderWallpaperConfig.CUSTOM_IMAGE) 2.dp else 1.dp,
                    if (selectedType == HeaderWallpaperConfig.CUSTOM_IMAGE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "কাস্টম ওয়ালপেপার (নিজের পছন্দের ছবি)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "গ্যালারি থেকে নিজস্ব ওয়ালপেপার ইমপোর্ট করো",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selectedType == HeaderWallpaperConfig.CUSTOM_IMAGE) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!customUri.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .clickable { selectedType = HeaderWallpaperConfig.CUSTOM_IMAGE }
                        ) {
                            AsyncImage(
                                model = customUri,
                                contentDescription = "Custom Wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "নির্বাচিত কাস্টম ইমেজ",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("অন্য ছবি বেছে নাও")
                            }

                            FilledTonalButton(
                                onClick = { selectedType = HeaderWallpaperConfig.CUSTOM_IMAGE },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (selectedType == HeaderWallpaperConfig.CUSTOM_IMAGE) "সক্রিয় আছে" else "এটি সেট করুন")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "গ্যালারি থেকে ওয়ালপেপার সিলেক্ট করুন",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ===== 4. Wallpaper Creation Guidelines Card =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showGuideDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ওয়ালপেপার তৈরির নিয়ম ও নির্দেশিকা",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "কীভাবে সেরা মাপ ও ফরম্যাটে নিজের ওয়ালপেপার ডিজাইন করবেন জানতে ট্যাপ করুন",
                            fontSize = 11.5.sp,
                            color = Color(0xFF1D4ED8),
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF2563EB)
                    )
                }
            }

            // ===== 5. Animation Toggle Card =====
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "লাইভ অ্যানিমেশন ও গ্লো ইফেক্ট",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "তারা, মেঘ ও তরঙ্গের চলমান অ্যানিমেশন সক্রিয় রাখুন",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isAnimEnabled,
                        onCheckedChange = { isAnimEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Guidelines Dialog
    if (showGuideDialog) {
        WallpaperGuidelinesDialog(onDismiss = { showGuideDialog = false })
    }
}

@Composable
private fun PresetThemeCard(
    preset: WallpaperPresetItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isSelected) 5.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Color Swatch Pill
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 44.dp)
                        .shadow(
                            elevation = if (isSelected) 6.dp else 2.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = preset.previewColors.firstOrNull() ?: Color.Black
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(preset.previewColors))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (preset.isLiveAnimated) {
                        Text("✨", fontSize = 14.sp)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = preset.nameBn,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (preset.isLiveAnimated) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "লাইভ",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = preset.description,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun WallpaperGuidelinesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("ওয়ালপেপার ডিজাইনের নির্দেশিকা", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GuideItem(
                    number = "১",
                    title = "সাইজ ও রেশিও (Aspect Ratio):",
                    detail = "আদর্শ অনুপাত ২১:৯ বা ১৬:৬ (ব্যানার ফরম্যাট)। সবচেয়ে ভালো মাপ হলো ১০৮০ x ৩৮০ পিক্সেল অথবা ১২০০ x ৪২০ পিক্সেল।"
                )
                GuideItem(
                    number = "২",
                    title = "সাপোর্টেড ফাইল ফরম্যাট:",
                    detail = "PNG, JPG, WebP অথবা অ্যানিমেটেড GIF ফাইল পুরোপুরি সাপোর্ট করবে।"
                )
                GuideItem(
                    number = "৩",
                    title = "সেফ জোন ও টেক্সট স্পষ্টতা:",
                    detail = "অ্যাপের উপরের অংশে ঘড়ি ও স্ট্যাটাস বার থাকে এবং ডানে প্রোফাইল ছবি থাকে। তাই ছবির প্রধান ইল্যাস্ট্রেশন বা আর্ট মাঝখানে বা নিচে রাখা ভালো।"
                )
                GuideItem(
                    number = "৪",
                    title = "কনট্রাস্ট ও কালার টিপস:",
                    detail = "একটু গাঢ় (Dark) বা মিডিয়াম স্যাচুরেটেড ব্যাকগ্রাউন্ড দিলে হ্যালো টেক্সট ও কোর্সের নাম সবচেয়ে আকর্ষণীয় ও স্পষ্ট দেখা যাবে।"
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("বুঝতে পেরেছি")
            }
        }
    )
}

@Composable
private fun GuideItem(number: String, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}
