package com.example.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

object SubjectIconUtils {

    fun getFallbackIcon(subjectName: String?, subjectCode: String?): ImageVector {
        val raw = "${subjectName ?: ""} ${subjectCode ?: ""}".lowercase()
        return when {
            raw.contains("বাংলা") || raw.contains("bangla") || raw.contains("bng") -> Icons.AutoMirrored.Filled.MenuBook
            raw.contains("ইংরেজি") || raw.contains("english") || raw.contains("eng") -> Icons.Default.Translate
            raw.contains("তথ্য") || raw.contains("ict") || raw.contains("কম্পিউটার") -> Icons.Default.Computer
            raw.contains("পদার্থ") || raw.contains("physics") || raw.contains("phy") -> Icons.Default.Science
            raw.contains("রসায়ন") || raw.contains("রসায়ন") || raw.contains("chemistry") || raw.contains("chm") -> Icons.Default.Biotech
            raw.contains("জীব") || raw.contains("biology") || raw.contains("bio") -> Icons.Default.Eco
            raw.contains("গণিত") || raw.contains("math") || raw.contains("উচ্চতর") -> Icons.Default.Calculate
            raw.contains("অর্থনীতি") || raw.contains("economics") || raw.contains("eco") -> Icons.AutoMirrored.Filled.TrendingUp
            raw.contains("পৌরনীতি") || raw.contains("civics") || raw.contains("সুশাসন") -> Icons.Default.AccountBalance
            raw.contains("ভূগোল") || raw.contains("geography") || raw.contains("geo") -> Icons.Default.Public
            raw.contains("ইতিহাস") || raw.contains("history") -> Icons.Default.HistoryEdu
            raw.contains("যুক্তি") || raw.contains("logic") || raw.contains("মনোবিজ্ঞান") -> Icons.Default.Psychology
            raw.contains("হিসাব") || raw.contains("accounting") || raw.contains("ফিন্যান্স") || raw.contains("ব্যবসায়") || raw.contains("ব্যবস্থাপনা") -> Icons.AutoMirrored.Filled.ReceiptLong
            raw.contains("সমাজ") || raw.contains("social") -> Icons.Default.Groups
            raw.contains("ইসলাম") || raw.contains("islam") -> Icons.Default.AutoStories
            raw.contains("কৃষি") || raw.contains("agri") -> Icons.Default.Agriculture
            raw.contains("পরিসংখ্যান") || raw.contains("stat") -> Icons.Default.BarChart
            raw.contains("গার্হস্থ্য") || raw.contains("home") -> Icons.Default.HomeWork
            else -> Icons.Default.Book
        }
    }
}

@Composable
fun SubjectIconBadge(
    iconUrl: String?,
    subjectName: String?,
    subjectCode: String?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp
) {
    val context = LocalContext.current
    val fallbackVector = remember(subjectName, subjectCode) {
        SubjectIconUtils.getFallbackIcon(subjectName, subjectCode)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.22f),
                shape = RoundedCornerShape(13.dp)
            )
    ) {
        if (!iconUrl.isNullOrBlank()) {
            val imageRequest = remember(iconUrl) {
                ImageRequest.Builder(context)
                    .data(iconUrl)
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(true)
                    .build()
            }

            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = subjectName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(iconSize),
                loading = {
                    Icon(
                        imageVector = fallbackVector,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(iconSize)
                    )
                },
                error = {
                    Icon(
                        imageVector = fallbackVector,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(iconSize)
                    )
                }
            )
        } else {
            Icon(
                imageVector = fallbackVector,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
