package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.library.*
import com.example.utils.toBengaliDigits
import java.io.File

private val LibraryEmerald = Color(0xFF10B981)
private val LibraryTeal = Color(0xFF0D9488)
private val LibraryAmber = Color(0xFFF59E0B)
private val LibrarySky = Color(0xFF0284C7)

private fun parseSafeAccentColor(hex: String, fallback: Color): Color {
    if (hex.isBlank()) return fallback
    return try {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(clean))
    } catch (_: Throwable) {
        fallback
    }
}

private val ClassPalette = listOf(
    Color(0xFF10B981),
    Color(0xFF0284C7),
    Color(0xFF8B5CF6),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF06B6D4)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenHtmlBook: (
        fileId: String,
        title: String,
        subtitle: String,
        remoteUrl: String,
        localFilePath: String
    ) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = com.example.ui.theme.isAppInDarkTheme()
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }

    var showVoiceSettingsDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<LibraryHtmlFile?>(null) }

    val navLevel = state.activeNavLevel

    val totalOfflineSavedCount = remember(state.downloadedById) {
        state.downloadedById.values.count {
            it.isCompleted && File(it.localFilePath).exists()
        }
    }

    // Voice Pack / Offline TTS Settings Dialog
    if (showVoiceSettingsDialog) {
        LibraryVoiceSettingsDialog(
            ttsModels = state.index?.ttsModels.orEmpty(),
            onDismiss = { showVoiceSettingsDialog = false }
        )
    }

    // Confirm Delete Downloaded HTML Book
    fileToDelete?.let { targetFile ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "অফলাইন বই মুছে ফেলবেন?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "\"${targetFile.title}\" আপনার অফলাইন মেমোরি ও ডাউনলোড তালিকা থেকে মুছে ফেলা হবে। পরে আবার ডাউনলোড বা অনলাইনে পড়তে পারবেন।",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownloadedFile(targetFile)
                        fileToDelete = null
                        Toast.makeText(context, "বইটি অফলাইন মেমোরি থেকে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("মুছে ফেলুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Header Bar (Clean — pull down anywhere to refresh, no manual refresh button)
        LibraryTopHeader(
            navLevel = navLevel,
            isOfflineSnapshot = state.isOfflineSnapshot,
            isDark = isDark,
            onBack = { viewModel.navigateBack() },
            onOpenVoiceSettings = { showVoiceSettingsDialog = true }
        )

        // Breadcrumb Trail when drilled into Subject / Chapter / Files
        if (navLevel !is LibraryNavLevel.Classes) {
            LibraryBreadcrumbRow(
                navLevel = navLevel,
                isDark = isDark,
                onJumpRoot = { viewModel.jumpToRoot() },
                onJumpClass = { viewModel.jumpToClassLevel() },
                onJumpSubject = { viewModel.jumpToSubjectLevel() }
            )
        }

        // Compact Search Bar (works across all levels!)
        if ((state.index?.classes?.isNotEmpty() == true) || state.searchQuery.isNotBlank()) {
            LibrarySearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                isDark = isDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Main Content Body with Pull-to-Refresh gesture
        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.loadLibrary(forceRefresh = true) },
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = state.isRefreshing,
                    containerColor = if (isDark) Color(0xFF112035) else Color.White,
                    color = LibraryEmerald,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("library_pull_to_refresh")
        ) {
            val query = state.searchQuery.trim()
            if (state.isLoading && state.index == null) {
                LibraryLoadingView()
            } else if (query.isNotBlank()) {
                // Global instant search across all HTML files
                val matchedFiles = remember(state.index, query) {
                    state.index?.allFiles.orEmpty().filter { f ->
                        f.title.contains(query, ignoreCase = true) ||
                            f.chapterTitle.contains(query, ignoreCase = true) ||
                            f.subjectTitle.contains(query, ignoreCase = true) ||
                            f.classTitle.contains(query, ignoreCase = true) ||
                            f.description.contains(query, ignoreCase = true)
                    }
                }
                if (matchedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        LibraryEmptyStateView(
                            icon = Icons.Default.SearchOff,
                            title = "কোনো বই খুঁজে পাওয়া যায়নি",
                            subtitle = "\"$query\" নামে কোনো শ্রেণী, সাবজেক্ট, অধ্যায় বা পাঠ নেই।",
                            actionLabel = "সার্চ মুছুন",
                            onAction = { viewModel.updateSearchQuery("") }
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "খুঁজে পাওয়া পাঠসমূহ (${matchedFiles.size.toBengaliDigits()})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(matchedFiles, key = { it.id }) { file ->
                            LibraryFileCard(
                                file = file,
                                showBreadcrumbSubtitle = true,
                                downloadedItem = state.downloadedById[file.downloadEntityId],
                                downloadedVersion = state.downloadedVersions[file.downloadEntityId] ?: 1,
                                downloadManager = downloadManager,
                                isDark = isDark,
                                onReadClick = { localPath ->
                                    onOpenHtmlBook(
                                        file.downloadEntityId,
                                        file.title,
                                        file.downloadSubtitle,
                                        file.fileUrl,
                                        localPath
                                    )
                                },
                                onDownloadClick = { forceRedownload ->
                                    viewModel.downloadFile(file, forceRedownload = forceRedownload)
                                    Toast.makeText(context, "\"${file.title}\" ডাউনলোড শুরু হয়েছে...", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteClick = { fileToDelete = file }
                            )
                        }
                    }
                }
            } else if (state.index == null || state.index?.classes.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    LibraryEmptyStateView(
                        icon = Icons.Default.MenuBook,
                        title = "লাইব্রেরি এখনো খালি রয়েছে",
                        subtitle = state.errorMessage
                            ?: "অ্যাডমিন অ্যাপ থেকে শ্রেণী, সাবজেক্ট, অধ্যায় ও HTML বই আপলোড করলে এখানে স্বয়ংক্রিয়ভাবে দেখাবে। নিচে টেনে রিফ্রেশ করুন।",
                        actionLabel = "আবার চেষ্টা করুন",
                        onAction = { viewModel.loadLibrary(forceRefresh = true) },
                        secondaryActionLabel = if (totalOfflineSavedCount > 0) "ডাউনলোড করা বই দেখুন (${totalOfflineSavedCount.toBengaliDigits()})" else null,
                        onSecondaryAction = onNavigateToDownloads
                    )
                }
            } else {
                AnimatedContent(
                    targetState = navLevel,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "LibraryLevelTransition"
                ) { level ->
                    when (level) {
                        is LibraryNavLevel.Classes -> {
                            val classes = state.index?.classes.orEmpty()
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                item {
                                    LibraryHeroBanner(
                                        totalClasses = classes.size,
                                        totalFiles = state.index?.allFiles?.size ?: 0,
                                        offlineSavedCount = totalOfflineSavedCount,
                                        isDark = isDark,
                                        onVoiceSettingsClick = { showVoiceSettingsDialog = true },
                                        onDownloadsClick = onNavigateToDownloads
                                    )
                                }
                                item {
                                    Text(
                                        text = "শ্রেণী নির্বাচন করুন",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                                itemsIndexed(classes, key = { _, item -> item.id }) { idx, cls ->
                                    val offlineInClass = cls.subjects.sumOf { sub ->
                                        sub.chapters.sumOf { ch ->
                                            ch.files.count { f ->
                                                val dl = state.downloadedById[f.downloadEntityId]
                                                dl != null && dl.isCompleted && File(dl.localFilePath).exists()
                                            }
                                        }
                                    }
                                    LibraryClassCard(
                                        classGroup = cls,
                                        accentColor = parseSafeAccentColor(cls.colorHex, ClassPalette[idx % ClassPalette.size]),
                                        offlineCount = offlineInClass,
                                        isDark = isDark,
                                        onClick = { viewModel.selectClass(cls.id) }
                                    )
                                }
                            }
                        }

                        is LibraryNavLevel.Subjects -> {
                            val subjects = level.classGroup.subjects
                            if (subjects.isEmpty()) {
                                LibraryEmptyStateView(
                                    icon = Icons.Default.LibraryBooks,
                                    title = "এই শ্রেণীতে এখনো কোনো সাবজেক্ট নেই",
                                    subtitle = "অ্যাডমিন অ্যাপ থেকে সাবজেক্ট যোগ করলে এখানে দেখাবে।",
                                    actionLabel = "পেছনে যান",
                                    onAction = { viewModel.navigateBack() }
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        Text(
                                            text = "সাবজেক্ট তালিকা (${subjects.size.toBengaliDigits()} টি)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                    itemsIndexed(subjects, key = { _, item -> item.id }) { idx, subject ->
                                        val offlineInSubject = subject.chapters.sumOf { ch ->
                                            ch.files.count { f ->
                                                val dl = state.downloadedById[f.downloadEntityId]
                                                dl != null && dl.isCompleted && File(dl.localFilePath).exists()
                                            }
                                        }
                                        LibrarySubjectCard(
                                            subject = subject,
                                            accentColor = parseSafeAccentColor(subject.colorHex, ClassPalette[(idx + 1) % ClassPalette.size]),
                                            offlineCount = offlineInSubject,
                                            isDark = isDark,
                                            onClick = { viewModel.selectSubject(subject.id) }
                                        )
                                    }
                                }
                            }
                        }

                        is LibraryNavLevel.Chapters -> {
                            val chapters = level.subject.chapters
                            if (chapters.isEmpty()) {
                                LibraryEmptyStateView(
                                    icon = Icons.Default.AutoStories,
                                    title = "এই সাবজেক্টে এখনো কোনো অধ্যায় নেই",
                                    subtitle = "নতুন অধ্যায় যুক্ত হলে এখানে দেখতে পাবেন।",
                                    actionLabel = "পেছনে যান",
                                    onAction = { viewModel.navigateBack() }
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        Text(
                                            text = "অধ্যায়সমূহ (${chapters.size.toBengaliDigits()} টি)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                    items(chapters, key = { it.id }) { chapter ->
                                        val offlineInChapter = chapter.files.count { f ->
                                            val dl = state.downloadedById[f.downloadEntityId]
                                            dl != null && dl.isCompleted && File(dl.localFilePath).exists()
                                        }
                                        LibraryChapterCard(
                                            chapter = chapter,
                                            offlineCount = offlineInChapter,
                                            isDark = isDark,
                                            onClick = { viewModel.selectChapter(chapter.id) },
                                            onDownloadAllChapter = {
                                                val queued = viewModel.downloadEntireChapter(chapter)
                                                val msg = if (queued > 0) {
                                                    "${queued.toBengaliDigits()} টি ফাইল ডাউনলোড শুরু হয়েছে"
                                                } else {
                                                    "এই অধ্যায়ের সব ফাইল আগেই ডাউনলোড করা আছে"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        is LibraryNavLevel.Files -> {
                            val files = level.chapter.files
                            if (files.isEmpty()) {
                                LibraryEmptyStateView(
                                    icon = Icons.Default.Description,
                                    title = "এই অধ্যায়ে এখনো কোনো HTML ফাইল নেই",
                                    subtitle = "অ্যাডমিন অ্যাপ থেকে HTML পাঠ আপলোড করলে এখানে দেখতে পাবেন।",
                                    actionLabel = "পেছনে যান",
                                    onAction = { viewModel.navigateBack() }
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "পাঠ ও HTML ফাইল (${files.size.toBengaliDigits()} টি)",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            if (files.size > 1) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        val queued = viewModel.downloadEntireChapter(level.chapter)
                                                        val msg = if (queued > 0) {
                                                            "${queued.toBengaliDigits()} টি ফাইল ডাউনলোড হচ্ছে..."
                                                        } else {
                                                            "সবগুলো ফাইল আগেই অফলাইনে সেভ করা আছে"
                                                        }
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 38.dp)
                                                        .testTag("download_all_chapter_files_btn")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DownloadForOffline,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text("সব ডাউনলোড", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    items(files, key = { it.id }) { file ->
                                        LibraryFileCard(
                                            file = file,
                                            showBreadcrumbSubtitle = false,
                                            downloadedItem = state.downloadedById[file.downloadEntityId],
                                            downloadedVersion = state.downloadedVersions[file.downloadEntityId] ?: 1,
                                            downloadManager = downloadManager,
                                            isDark = isDark,
                                            onReadClick = { localPath ->
                                                onOpenHtmlBook(
                                                    file.downloadEntityId,
                                                    file.title,
                                                    file.downloadSubtitle,
                                                    file.fileUrl,
                                                    localPath
                                                )
                                            },
                                            onDownloadClick = { forceRedownload ->
                                                viewModel.downloadFile(file, forceRedownload = forceRedownload)
                                                Toast.makeText(context, "\"${file.title}\" ডাউনলোড শুরু হয়েছে...", Toast.LENGTH_SHORT).show()
                                            },
                                            onDeleteClick = { fileToDelete = file }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTopHeader(
    navLevel: LibraryNavLevel,
    isOfflineSnapshot: Boolean,
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenVoiceSettings: () -> Unit
) {
    val headerTitle = when (navLevel) {
        is LibraryNavLevel.Classes -> "ডিজিটাল লাইব্রেরি"
        is LibraryNavLevel.Subjects -> navLevel.classGroup.title
        is LibraryNavLevel.Chapters -> navLevel.subject.title
        is LibraryNavLevel.Files -> navLevel.chapter.title
    }
    val headerSub = when (navLevel) {
        is LibraryNavLevel.Classes -> if (isOfflineSnapshot) "অফলাইন ক্যাশ • নিচে টেনে রিফ্রেশ করুন" else "ইন্টারেক্টিভ বই ও অফলাইন রিডার"
        is LibraryNavLevel.Subjects -> "সাবজেক্ট নির্বাচন করুন"
        is LibraryNavLevel.Chapters -> "${navLevel.classGroup.title} • অধ্যায়সমূহ"
        is LibraryNavLevel.Files -> "${navLevel.subject.title} • পাঠসমূহ"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (navLevel !is LibraryNavLevel.Classes) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("library_back_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0).copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(listOf(LibraryEmerald, LibraryTeal))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(23.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headerTitle,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = headerSub,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Sleek Offline Voice / TTS Pill Button
        Surface(
            onClick = onOpenVoiceSettings,
            shape = RoundedCornerShape(14.dp),
            color = LibraryEmerald.copy(alpha = if (isDark) 0.2f else 0.12f),
            border = BorderStroke(1.dp, LibraryEmerald.copy(alpha = if (isDark) 0.4f else 0.28f)),
            modifier = Modifier
                .heightIn(min = 42.dp)
                .testTag("library_voice_settings_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "অফলাইন ভয়েস সেটিংস",
                    tint = LibraryEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "ভয়েস",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                )
            }
        }
    }
}

@Composable
private fun LibraryBreadcrumbRow(
    navLevel: LibraryNavLevel,
    isDark: Boolean,
    onJumpRoot: () -> Unit,
    onJumpClass: () -> Unit,
    onJumpSubject: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BreadcrumbChip("লাইব্রেরি", active = false, isDark = isDark, onClick = onJumpRoot)
        when (navLevel) {
            is LibraryNavLevel.Subjects -> {
                BreadcrumbChevron()
                BreadcrumbChip(navLevel.classGroup.title, active = true, isDark = isDark, onClick = {})
            }
            is LibraryNavLevel.Chapters -> {
                BreadcrumbChevron()
                BreadcrumbChip(navLevel.classGroup.title, active = false, isDark = isDark, onClick = onJumpClass)
                BreadcrumbChevron()
                BreadcrumbChip(navLevel.subject.title, active = true, isDark = isDark, onClick = {})
            }
            is LibraryNavLevel.Files -> {
                BreadcrumbChevron()
                BreadcrumbChip(navLevel.classGroup.title, active = false, isDark = isDark, onClick = onJumpClass)
                BreadcrumbChevron()
                BreadcrumbChip(navLevel.subject.title, active = false, isDark = isDark, onClick = onJumpSubject)
                BreadcrumbChevron()
                BreadcrumbChip(navLevel.chapter.title, active = true, isDark = isDark, onClick = {})
            }
            else -> Unit
        }
    }
}

@Composable
private fun BreadcrumbChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun BreadcrumbChip(
    label: String,
    active: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) LibraryEmerald.copy(alpha = if (isDark) 0.24f else 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !active, onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) LibraryEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 170.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                text = "বই, অধ্যায় বা পাঠ খুঁজুন...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = LibraryEmerald,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (isDark) Color(0xFF111C2E) else Color.White,
            unfocusedContainerColor = if (isDark) Color(0xFF0E1726) else Color.White,
            focusedBorderColor = LibraryEmerald,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier.testTag("library_search_input")
    )
}

@Composable
private fun LibraryHeroBanner(
    totalClasses: Int,
    totalFiles: Int,
    offlineSavedCount: Int,
    isDark: Boolean,
    onVoiceSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, LibraryEmerald.copy(alpha = if (isDark) 0.42f else 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        if (isDark) listOf(Color(0xFF072924), Color(0xFF0D2036), Color(0xFF071B26))
                        else listOf(Color(0xFFECFDF5), Color(0xFFE0F2FE), Color(0xFFF0FDFA))
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Brush.linearGradient(listOf(LibraryEmerald, LibraryTeal))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "অফলাইন HTML বই ও অডিও রিডার",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color.White else Color(0xFF064E3B)
                        )
                        Text(
                            text = "${totalClasses.toBengaliDigits()} টি শ্রেণী • ${totalFiles.toBengaliDigits()} টি পাঠ • ${offlineSavedCount.toBengaliDigits()} টি অফলাইনে সেভড",
                            fontSize = 11.5.sp,
                            color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF047857)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onVoiceSettingsClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LibraryEmerald.copy(alpha = if (isDark) 0.24f else 0.16f),
                            contentColor = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("বাংলা/ইংরেজি TTS", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = onDownloadsClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LibrarySky.copy(alpha = if (isDark) 0.24f else 0.14f),
                            contentColor = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ডাউনলোড বই (${offlineSavedCount.toBengaliDigits()})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryClassCard(
    classGroup: LibraryClassGroup,
    accentColor: Color,
    offlineCount: Int,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (isDark) 0.35f else 0.25f)),
        shadowElevation = if (isDark) 4.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("library_class_card_${classGroup.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.72f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = classGroup.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (classGroup.badge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = classGroup.badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (classGroup.subtitle.isNotBlank()) {
                    Text(
                        text = classGroup.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoMiniPill("${classGroup.subjects.size.toBengaliDigits()} সাবজেক্ট", accentColor)
                    InfoMiniPill("${classGroup.totalFilesCount.toBengaliDigits()} পাঠ", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (offlineCount > 0) {
                        InfoMiniPill("${offlineCount.toBengaliDigits()} অফলাইন", LibraryEmerald)
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun LibrarySubjectCard(
    subject: LibrarySubject,
    accentColor: Color,
    offlineCount: Int,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (isDark) 0.35f else 0.25f)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("library_subject_card_${subject.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = if (isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (subject.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = subject.coverUrl,
                        contentDescription = subject.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = subject.title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subject.subtitle.isNotBlank()) {
                    Text(
                        text = subject.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoMiniPill("${subject.chapters.size.toBengaliDigits()} অধ্যায়", accentColor)
                    InfoMiniPill("${subject.totalFilesCount.toBengaliDigits()} ফাইল", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (offlineCount > 0) {
                        InfoMiniPill("${offlineCount.toBengaliDigits()} অফলাইন", LibraryEmerald)
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LibraryChapterCard(
    chapter: LibraryChapter,
    offlineCount: Int,
    isDark: Boolean,
    onClick: () -> Unit,
    onDownloadAllChapter: () -> Unit
) {
    val allOffline = chapter.files.isNotEmpty() && offlineCount >= chapter.files.size

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (allOffline) LibraryEmerald.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = 1.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("library_chapter_card_${chapter.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (allOffline) LibraryEmerald.copy(alpha = if (isDark) 0.22f else 0.14f)
                        else LibrarySky.copy(alpha = if (isDark) 0.2f else 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chapter.chapterNumber.coerceAtLeast(1).toBengaliDigits(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (allOffline) LibraryEmerald else LibrarySky
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = chapter.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoMiniPill("${chapter.files.size.toBengaliDigits()} টি HTML পাঠ", LibrarySky)
                    if (offlineCount > 0) {
                        InfoMiniPill(
                            if (allOffline) "সম্পূর্ণ অফলাইন ✓" else "${offlineCount.toBengaliDigits()} সেভড",
                            LibraryEmerald
                        )
                    }
                }
            }

            if (chapter.files.isNotEmpty()) {
                FilledTonalIconButton(
                    onClick = onDownloadAllChapter,
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (allOffline) LibraryEmerald.copy(alpha = 0.18f)
                        else LibrarySky.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = if (allOffline) Icons.Default.DownloadDone else Icons.Default.Download,
                        contentDescription = "অধ্যায় ডাউনলোড",
                        tint = if (allOffline) LibraryEmerald else LibrarySky,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFileCard(
    file: LibraryHtmlFile,
    showBreadcrumbSubtitle: Boolean,
    downloadedItem: DownloadedItemEntity?,
    downloadedVersion: Int,
    downloadManager: AppFileDownloadManager,
    isDark: Boolean,
    onReadClick: (localFilePath: String) -> Unit,
    onDownloadClick: (forceRedownload: Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {
    val localFileExists = remember(downloadedItem) {
        downloadedItem != null &&
            downloadedItem.isCompleted &&
            File(downloadedItem.localFilePath).exists() &&
            File(downloadedItem.localFilePath).length() > 0L
    }
    val isDownloading = downloadedItem?.isDownloading == true
    val hasUpdate = localFileExists && file.version > downloadedVersion

    val sizeText = remember(file.fileSizeBytes, downloadedItem?.totalBytes) {
        val bytes = when {
            (downloadedItem?.totalBytes ?: 0L) > 0L -> downloadedItem!!.totalBytes
            file.fileSizeBytes > 0L -> file.fileSizeBytes
            else -> 0L
        }
        if (bytes > 0L) downloadManager.formatFileSize(bytes, inBengali = true) else "HTML বই"
    }

    val accent = if (localFileExists) LibraryEmerald else LibrarySky

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (localFileExists) LibraryEmerald.copy(alpha = if (isDark) 0.45f else 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                onReadClick(if (localFileExists) downloadedItem!!.localFilePath else "")
            }
            .testTag("library_file_card_${file.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = if (isDark) 0.2f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (localFileExists) Icons.Default.MenuBook else Icons.Default.Description,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InfoMiniPill(
                            text = if (localFileExists) "অফলাইন সেভড" else "অনলাইন",
                            color = if (localFileExists) LibraryEmerald else LibrarySky
                        )
                        if (hasUpdate) {
                            InfoMiniPill(text = "আপডেট আছে", color = LibraryAmber)
                        }
                        Text(
                            text = "• $sizeText",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = file.title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val subLine = if (showBreadcrumbSubtitle) file.downloadSubtitle else file.description
                    if (subLine.isNotBlank()) {
                        Text(
                            text = subLine,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Buttons (Download / Update / Delete)
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = LibrarySky
                    )
                } else if (hasUpdate) {
                    FilledTonalIconButton(
                        onClick = { onDownloadClick(true) },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = LibraryAmber.copy(alpha = 0.18f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = "আপডেট ডাউনলোড",
                            tint = LibraryAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (localFileExists) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "অফলাইন ফাইল মুছুন",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = { onDownloadClick(false) },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = LibrarySky.copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "ডাউনলোড করুন",
                            tint = LibrarySky,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isDownloading && downloadedItem != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloadedItem.progressFraction.coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = LibrarySky
                )
            }
        }
    }
}

@Composable
private fun InfoMiniPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun LibraryLoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = LibraryEmerald)
            Text(
                text = "লাইব্রেরি লোড হচ্ছে...",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LibraryEmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LibraryEmerald.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LibraryEmerald,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LibraryEmerald),
                modifier = Modifier.heightIn(min = 46.dp)
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
            if (!secondaryActionLabel.isNullOrBlank() && onSecondaryAction != null) {
                OutlinedButton(
                    onClick = onSecondaryAction,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(secondaryActionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
