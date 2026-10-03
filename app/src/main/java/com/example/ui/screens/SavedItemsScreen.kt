package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.ParsedOption
import com.example.database.SavedItemEntity
import com.example.saved.SavedItemFilter
import com.example.saved.SavedViewModel
import com.example.ui.components.QuizRichContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun toBengaliOptionLetter(optionNo: String?): String {
    return when (optionNo?.trim()?.uppercase()) {
        "A", "1" -> "ক"
        "B", "2" -> "খ"
        "C", "3" -> "গ"
        "D", "4" -> "ঘ"
        else -> optionNo ?: ""
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedItemsScreen(
    viewModel: SavedViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "সংরক্ষিত তালিকা",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${uiState.items.size}টি আইটেম সংরক্ষিত",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("saved_items_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SavedItemFilter.values().forEach { filter ->
                    val isSelected = uiState.currentFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFilter(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            // Content List or Empty State
            if (uiState.items.isEmpty()) {
                EmptySavedState(filter = uiState.currentFilter)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = uiState.items,
                        key = { it.id }
                    ) { item ->
                        SavedItemCard(
                            item = item,
                            onDelete = { viewModel.deleteSavedItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedItemCard(
    item: SavedItemEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val parsedContent = remember(item.contentJson) {
        SavedItemEntity.parseQuestionContent(item.contentJson)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("saved_item_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Type badge, subtitle & delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Type Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (item.type) {
                            SavedItemEntity.TYPE_QUESTION -> MaterialTheme.colorScheme.primaryContainer
                            SavedItemEntity.TYPE_NOTE -> Color(0xFFFEF3C7)
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = when (item.type) {
                                SavedItemEntity.TYPE_QUESTION -> "প্রশ্ন"
                                SavedItemEntity.TYPE_NOTE -> "নোট"
                                SavedItemEntity.TYPE_CLASS -> "ক্লাস"
                                else -> item.type
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (item.type) {
                                SavedItemEntity.TYPE_QUESTION -> MaterialTheme.colorScheme.onPrimaryContainer
                                SavedItemEntity.TYPE_NOTE -> Color(0xFFB45309)
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Subtitle / Chapter Name
                    val displaySubtitle = item.subtitle ?: parsedContent.chapterName
                    if (!displaySubtitle.isNullOrBlank()) {
                        Text(
                            text = displaySubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // Delete / Unsave button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("delete_saved_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Unsave",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title / Question Text & Diagrams
            QuizRichContent(
                text = item.title,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                textColor = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxImageHeight = 200.dp
            )

            // If it's a question with options
            if (item.type == SavedItemEntity.TYPE_QUESTION && parsedContent.options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parsedContent.options.forEach { opt ->
                        SavedOptionRow(
                            option = opt,
                            isCorrect = parsedContent.correctOption?.equals(opt.no, ignoreCase = true) == true,
                            isUserGiven = parsedContent.userGivenAns?.equals(opt.no, ignoreCase = true) == true
                        )
                    }
                }
            }

            // Solution / Explanation toggle
            if (!parsedContent.explanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isExpanded) "সমাধান লুকান" else "ব্যাখ্যা ও সমাধান দেখুন",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(
                                color = Color(0xFFF59E0B).copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        QuizRichContent(
                            text = parsedContent.explanation,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                            textColor = MaterialTheme.colorScheme.onSurface,
                            maxImageHeight = 180.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTimestamp(item.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SavedOptionRow(
    option: ParsedOption,
    isCorrect: Boolean,
    isUserGiven: Boolean
) {
    val bgColor = when {
        isCorrect -> Color(0xFF10B981).copy(alpha = 0.12f)
        isUserGiven -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val borderColor = when {
        isCorrect -> Color(0xFF10B981)
        isUserGiven -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Option Letter Circle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCorrect -> Color(0xFF10B981)
                            isUserGiven -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = toBengaliOptionLetter(option.no),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isCorrect || isUserGiven -> Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            QuizRichContent(
                text = option.description,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                textColor = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                maxImageHeight = 130.dp,
                modifier = Modifier.weight(1f)
            )

            if (isCorrect) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Correct Answer",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptySavedState(filter: SavedItemFilter) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(90.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "কোনো সংরক্ষিত আইটেম পাওয়া যায়নি",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (filter) {
                    SavedItemFilter.QUESTIONS -> "কুইজ দেওয়ার সময় বা ফলাফল দেখার পর বুকমার্ক আইকনে চাপ দিয়ে গুরুত্বপূর্ণ প্রশ্ন সংরক্ষণ করুন।"
                    else -> "আপনার প্রয়োজনীয় প্রশ্ন, নোট বা ক্লাস সংরক্ষণ করে পরবর্তীতে অফলাইনেও দেখতে পারবেন।"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
