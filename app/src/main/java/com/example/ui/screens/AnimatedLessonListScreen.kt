package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil.compose.AsyncImage
import com.example.api.TopicFullItem
import com.example.course.CourseViewModel
import com.example.gamification.StudentGamificationManager
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedLessonListScreen(
    chapterId: String,
    chapterName: String,
    subjectColorHex: String? = null,
    fromChapterPage: Boolean = false,
    viewModel: CourseViewModel,
    onBack: () -> Unit,
    onPlayVideo: (videoUrl: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var topicsList by remember { mutableStateOf<List<TopicFullItem>>(emptyList()) }
    val watchedUrls = remember(topicsList) { StudentGamificationManager.getWatchedAnimatedUrls(context) }

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

    fun loadTopics() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val fetched = viewModel.getTopics(chapterId)
                // Filter out items without valid playback URL
                topicsList = fetched.filter { topic ->
                    val vList = topic.videos?.data
                    !vList.isNullOrEmpty() && vList.any { !it.playback_url.isNullOrBlank() }
                }
                if (topicsList.isNotEmpty()) {
                    viewModel.setAnimatedPlaylist(topicsList)
                } else {
                    errorMessage = "এই অধ্যায়ে কোনো অ্যানিমেটেড লেসন পাওয়া যায়নি"
                }
            } catch (e: Exception) {
                errorMessage = "লেসন লোড করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক এরর"}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(chapterId) {
        loadTopics()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
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
                            text = chapterName.ifBlank { "অ্যানিমেটেড লেসনস" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartDisplay,
                                contentDescription = null,
                                tint = themeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "থ্রিডি অ্যানিমেটেড কোয়েস্ট প্লেলিস্ট",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = themeColor
                            )
                        }
                    }

                    if (topicsList.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "+${toBengaliDigits(topicsList.size * 50)} XP",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("animated_lesson_list_screen")
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
                            text = "অ্যানিমেটেড লেসন লোড হচ্ছে...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null && topicsList.isEmpty() -> {
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
                                    imageVector = Icons.Default.SmartDisplay,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "কোনো অ্যানিমেটেড ভিডিও পাওয়া যায়নি",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadTopics() },
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
                    val watchedCount = remember(topicsList, watchedUrls) {
                        topicsList.count { topic ->
                            val url = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }?.playback_url
                            url != null && watchedUrls.contains(url)
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            // Gamified Chapter Quest Summary Banner
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.2.dp, themeColor.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF0F172A),
                                                    Color(0xFF1E1B4B)
                                                )
                                            )
                                        )
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = themeColor.copy(alpha = 0.25f),
                                                border = BorderStroke(1.dp, themeColor.copy(alpha = 0.6f)),
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.MovieFilter,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = "অ্যানিমেটেড ভিডিও প্লেলিস্ট: ${toBengaliDigits(topicsList.size)}টি",
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "একটি প্লে করলে প্লেয়ারের ভেতরেই সব ভিডিও পরপর দেখতে পারবেন!",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFFCBD5E1)
                                                )
                                            }
                                        }

                                        if (watchedCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "${toBengaliDigits(watchedCount)}/${toBengaliDigits(topicsList.size)} সম্পন্ন",
                                                    color = Color(0xFF34D399),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        itemsIndexed(topicsList) { index, topic ->
                            val videoData = topic.videos?.data?.firstOrNull { !it.playback_url.isNullOrBlank() }
                            val playbackUrl = videoData?.playback_url ?: ""
                            val thumbnail = videoData?.video_thumbnail_url?.firstOrNull()
                            val topicTitle = topic.name ?: "ভিডিও ${index + 1}"
                            val serialNo = topic.no?.let { toBengaliDigits(it) } ?: toBengaliDigits(index + 1)
                            val isWatched = watchedUrls.contains(playbackUrl)

                            AnimatedVideoCard(
                                serialNo = serialNo,
                                title = topicTitle,
                                description = topic.description,
                                thumbnailUrl = thumbnail,
                                themeColor = themeColor,
                                isWatched = isWatched,
                                onClick = {
                                    if (playbackUrl.isNotBlank()) {
                                        viewModel.setAnimatedPlaylist(topicsList)
                                        onPlayVideo(playbackUrl, topicTitle)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedVideoCard(
    serialNo: String,
    title: String,
    description: String?,
    thumbnailUrl: String?,
    themeColor: Color,
    isWatched: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isWatched) Color(0xFF10B981).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail with Aspect Ratio 16:9 & Play Button Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF1E293B))
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl?.takeIf { it.isNotBlank() } ?: com.example.R.drawable.placeholder_animated)
                        .crossfade(true)
                        .error(com.example.R.drawable.placeholder_animated)
                        .placeholder(com.example.R.drawable.placeholder_animated)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )

                // Serial No Badge (Top Left)
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 12.dp, topStart = 18.dp),
                    color = themeColor,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "লেসন $serialNo",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                // XP & Watched Badges (Top Right)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isWatched) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = "দেখা হয়েছে ✓",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.7f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "+৫০ XP",
                                color = Color(0xFFFDE68A),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Center Play Icon Circle
                Surface(
                    shape = CircleShape,
                    color = themeColor.copy(alpha = 0.85f),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)),
                    modifier = Modifier
                        .size(50.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Info Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
