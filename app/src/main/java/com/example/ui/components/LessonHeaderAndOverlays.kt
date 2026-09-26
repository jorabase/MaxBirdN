package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.LessonAttachmentItem
import com.example.api.StudentLessonItem
import com.example.utils.formatBanglaDateTime

/**
 * Diagnostic error overlay displayed on top of the player when a stream error occurs.
 */
@Composable
fun PlayerErrorOverlay(
    playbackError: String,
    playbackErrorDetails: String?,
    slideUrl: String?,
    onRefreshLesson: (() -> Unit)?,
    onRetryPlayback: () -> Unit,
    onLaunchWebPlayer: () -> Unit,
    onViewSlide: (LessonAttachmentItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE60F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playbackError,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playbackErrorDetails ?: "সার্ভার রেসপন্স চেক করুন",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRetryPlayback,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("পুনরায় চেষ্টা", fontSize = 12.sp)
                }

                Button(
                    onClick = onLaunchWebPlayer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ব্রাউজার প্লেয়ার", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!slideUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        onViewSlide(
                            LessonAttachmentItem(
                                title = "লেকচার স্লাইড ও নোটস",
                                url = slideUrl,
                                file_type = "pdf"
                            )
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF93C5FD))
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লেকচার স্লাইড ও ক্লাস নোটস পড়ুন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Top Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "ফিরে যান",
                tint = Color.White
            )
        }
    }
}

/**
 * Empty / Placeholder state when video stream is not directly available.
 */
@Composable
fun LessonStreamPlaceholder(
    lesson: StudentLessonItem?,
    slideUrl: String?,
    onRefreshLesson: (() -> Unit)?,
    onViewSlide: (LessonAttachmentItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "এই ক্লাসের সরাসরি রেকর্ডিং লিংক পাওয়া যায়নি",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "কোর্সে সরাসরি ভর্তি না থাকলে বা ক্লাস অপ্রস্তুত থাকলে Shikho API লিংক পাঠায় না।",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onRefreshLesson != null) {
                    Button(
                        onClick = onRefreshLesson,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সার্ভার রিফ্রেশ", fontSize = 12.sp)
                    }
                }
            }

            if (!slideUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        onViewSlide(
                            LessonAttachmentItem(
                                title = "লেকচার স্লাইড ও নোটস",
                                url = slideUrl,
                                file_type = "pdf"
                            )
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF93C5FD))
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লেকচার স্লাইড ও ক্লাস নোটস পড়ুন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Top Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "ফিরে যান",
                tint = Color.White
            )
        }
    }
}

/**
 * Class Header & Info Section with Bengali Date-Time formatting.
 */
@Composable
fun LessonDetailHeader(
    lesson: StudentLessonItem?,
    modifier: Modifier = Modifier
) {
    val teacher = lesson?.live_class?.teacher 
        ?: lesson?.live_class?.instructor
    val subjectName = lesson?.subject_name
        ?: lesson?.live_class?.subject_name
        ?: "বিষয়"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Badges Row: Subject Tag + Teacher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Subject Pill (Teal background, white text)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF14B8A6),
                shadowElevation = 0.dp
            ) {
                Text(
                    text = subjectName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // 2. Teacher Pill
            if (teacher != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    val avatarUrl = teacher.displayAvatar
                    val teacherName = teacher.displayName
                    val context = LocalContext.current

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank() && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://"))) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = teacherName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = teacherName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Title
        Text(
            text = lesson?.title ?: "ক্লাস লেকচার",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Date and Time in Bengali
        val rawStart = lesson?.live_class?.start_time ?: lesson?.start_time
        val formattedTime = formatBanglaDateTime(rawStart)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = formattedTime,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }


    }
}
