package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.AcademicChapterItem
import com.example.course.CourseViewModel
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedLessonChaptersScreen(
    subjectId: String,
    subjectTitle: String,
    subjectColorHex: String? = null,
    programId: String? = null,
    phaseId: String? = null,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, chapterName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var chaptersList by remember { mutableStateOf<List<AcademicChapterItem>>(emptyList()) }

    val themeColor = remember(subjectColorHex) {
        try {
            if (!subjectColorHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(subjectColorHex))
            } else {
                Color(0xFF8B5CF6)
            }
        } catch (_: Exception) {
            Color(0xFF8B5CF6)
        }
    }

    fun loadChapters() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val courseState = viewModel.uiState.value
                val effectiveProgId = programId?.takeIf { it.isNotBlank() }
                    ?: courseState.programId.takeIf { it.isNotBlank() }
                    ?: "6864d3a806800acba2e27099"
                val effectivePhaseId = phaseId?.takeIf { it.isNotBlank() }
                    ?: courseState.selectedPhase?.id?.takeIf { it.isNotBlank() }
                    ?: courseState.activePhaseId.takeIf { it.isNotBlank() }
                    ?: "6864d62506800acba2e27111"
                
                val fetched = viewModel.getPhaseWiseChapters(
                    programId = effectiveProgId,
                    phaseId = effectivePhaseId,
                    subjectCode = subjectId
                )
                
                chaptersList = fetched.sortedBy { ch ->
                    when (val no = ch.effectiveNo) {
                        is Number -> no.toDouble()
                        is String -> no.toDoubleOrNull() ?: 999.0
                        else -> 999.0
                    }
                }
                
                if (chaptersList.isEmpty()) {
                    errorMessage = "এই বিষয়ের কোনো অধ্যায় পাওয়া যায়নি"
                }
            } catch (e: Exception) {
                errorMessage = "অধ্যায় লোড করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক এরর"}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(subjectId, programId, phaseId) {
        loadChapters()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subjectTitle.ifBlank { "অ্যানিমেটেড লেসনস" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartDisplay,
                                contentDescription = null,
                                tint = themeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "অধ্যায় নির্বাচন করুন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = themeColor
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("animated_lesson_chapters_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = themeColor,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "অধ্যায়সমূহ লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null && chaptersList.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "কোনো অধ্যায় পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadChapters() },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুনরায় চেষ্টা করুন")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = themeColor.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, themeColor.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartDisplay,
                                        contentDescription = null,
                                        tint = themeColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "মোট অধ্যায়: ${toBengaliDigits(chaptersList.size)}টি",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = themeColor
                                    )
                                }
                            }
                        }

                        itemsIndexed(chaptersList) { index, chapter ->
                            val effectiveId = chapter.chapter_id?.takeIf { it.isNotBlank() } ?: chapter.id
                            val chapterName = chapter.effectiveName
                            val rawNo = chapter.effectiveNo
                            val serialText = when (rawNo) {
                                is Number -> "অধ্যায় ${toBengaliDigits(rawNo.toInt())}"
                                is String -> if (rawNo.isNotBlank()) "অধ্যায় ${toBengaliDigits(rawNo)}" else "অধ্যায় ${toBengaliDigits(index + 1)}"
                                else -> "অধ্যায় ${toBengaliDigits(index + 1)}"
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onChapterClick(effectiveId, chapterName)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = themeColor.copy(alpha = 0.12f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.SmartDisplay,
                                                contentDescription = null,
                                                tint = themeColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = serialText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = themeColor
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = chapterName,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
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
