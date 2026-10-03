package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.util.regex.Pattern

/**
 * Represents a parsed fragment of a quiz question/option: either formatted text or an image.
 */
sealed class QuizContentSegment {
    data class Text(val text: String) : QuizContentSegment()
    data class Image(val url: String, val altText: String? = null) : QuizContentSegment()
}

/**
 * Helper object to parse raw text containing URLs, Markdown images, or HTML images.
 */
object QuizContentParser {

    private val MARKDOWN_IMAGE_REGEX = Pattern.compile("!\\[(.*?)\\]\\((https?://[^\\s\\)]+)\\)", Pattern.CASE_INSENSITIVE)
    private val HTML_IMAGE_REGEX = Pattern.compile("<img\\s+[^>]*src=[\"'](https?://[^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE)
    private val GENERAL_URL_REGEX = Pattern.compile("(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]+)", Pattern.CASE_INSENSITIVE)

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "svg", "gif", "bmp")

    fun isLikelyImageUrl(url: String): Boolean {
        val cleanUrl = url.trim().lowercase()
        // Check file extension
        val withoutQuery = cleanUrl.substringBefore('?').substringBefore('#')
        val ext = withoutQuery.substringAfterLast('.', "")
        if (IMAGE_EXTENSIONS.contains(ext)) return true

        // Check common educational/S3/CDN image paths
        if (cleanUrl.contains("amazonaws.com") ||
            cleanUrl.contains("cloudinary.com") ||
            cleanUrl.contains("shikho") ||
            cleanUrl.contains("cloudfront.net") ||
            cleanUrl.contains("storage.googleapis.com") ||
            cleanUrl.contains("/images/") ||
            cleanUrl.contains("/mcq/") ||
            cleanUrl.contains("/questions/") ||
            cleanUrl.contains("/assets/") ||
            cleanUrl.contains("/uploads/") ||
            cleanUrl.contains("latex") ||
            cleanUrl.contains("equation") ||
            cleanUrl.contains("render")
        ) {
            return true
        }

        // In quiz options/questions, any standalone link is almost universally an image asset
        return true
    }

    /**
     * Parse content into an ordered list of Text and Image segments.
     */
    fun parse(content: String?): List<QuizContentSegment> {
        if (content.isNullOrBlank()) return emptyList()

        val trimmed = content.trim()

        // 1. Check if the entire string is just a URL
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val tokens = trimmed.split("\\s+".toRegex())
            if (tokens.size == 1 && isLikelyImageUrl(tokens[0])) {
                return listOf(QuizContentSegment.Image(url = tokens[0]))
            }
        }

        // 2. Check for Markdown image syntax: ![alt](url)
        val segments = mutableListOf<QuizContentSegment>()
        var workingText = trimmed

        // Extract Markdown images first
        val mdMatcher = MARKDOWN_IMAGE_REGEX.matcher(workingText)
        var lastEnd = 0
        var foundMd = false
        while (mdMatcher.find()) {
            foundMd = true
            val start = mdMatcher.start()
            val end = mdMatcher.end()
            if (start > lastEnd) {
                val prefix = workingText.substring(lastEnd, start).trim()
                if (prefix.isNotEmpty()) {
                    segments.add(QuizContentSegment.Text(prefix))
                }
            }
            val alt = mdMatcher.group(1)
            val url = mdMatcher.group(2)
            if (!url.isNullOrBlank()) {
                segments.add(QuizContentSegment.Image(url = url.trim(), altText = alt?.takeIf { it.isNotBlank() }))
            }
            lastEnd = end
        }

        if (foundMd) {
            if (lastEnd < workingText.length) {
                val suffix = workingText.substring(lastEnd).trim()
                if (suffix.isNotEmpty()) {
                    segments.add(QuizContentSegment.Text(suffix))
                }
            }
            return segments
        }

        // 3. Check for HTML <img> tag
        val htmlMatcher = HTML_IMAGE_REGEX.matcher(workingText)
        lastEnd = 0
        var foundHtml = false
        while (htmlMatcher.find()) {
            foundHtml = true
            val start = htmlMatcher.start()
            val end = htmlMatcher.end()
            if (start > lastEnd) {
                val prefix = workingText.substring(lastEnd, start).trim()
                if (prefix.isNotEmpty()) {
                    segments.add(QuizContentSegment.Text(prefix))
                }
            }
            val url = htmlMatcher.group(1)
            if (!url.isNullOrBlank()) {
                segments.add(QuizContentSegment.Image(url = url.trim()))
            }
            lastEnd = end
        }

        if (foundHtml) {
            if (lastEnd < workingText.length) {
                val suffix = workingText.substring(lastEnd).trim()
                if (suffix.isNotEmpty()) {
                    segments.add(QuizContentSegment.Text(suffix))
                }
            }
            return segments
        }

        // 4. Check for standalone HTTP/HTTPS URLs embedded in text
        val urlMatcher = GENERAL_URL_REGEX.matcher(workingText)
        lastEnd = 0
        var foundUrl = false
        while (urlMatcher.find()) {
            val start = urlMatcher.start()
            val end = urlMatcher.end()
            val rawUrl = urlMatcher.group(1) ?: continue

            if (isLikelyImageUrl(rawUrl)) {
                foundUrl = true
                if (start > lastEnd) {
                    val prefix = workingText.substring(lastEnd, start).trim()
                    if (prefix.isNotEmpty()) {
                        segments.add(QuizContentSegment.Text(prefix))
                    }
                }
                segments.add(QuizContentSegment.Image(url = rawUrl.trim()))
                lastEnd = end
            }
        }

        if (foundUrl) {
            if (lastEnd < workingText.length) {
                val suffix = workingText.substring(lastEnd).trim()
                if (suffix.isNotEmpty()) {
                    segments.add(QuizContentSegment.Text(suffix))
                }
            }
            return segments
        }

        // Default: Pure text
        return listOf(QuizContentSegment.Text(trimmed))
    }
}

/**
 * A versatile Composable that renders Quiz questions, MCQ options, solutions, and explanations
 * automatically rendering any image URLs, Markdown images, or equations as crisp images.
 */
@Composable
fun QuizRichContent(
    text: String?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    maxImageHeight: Dp = 160.dp,
    imageAlignment: Alignment.Horizontal = Alignment.Start,
    allowZoomDialog: Boolean = true,
    showImageBorder: Boolean = true
) {
    if (text.isNullOrBlank()) return

    val segments = remember(text) { QuizContentParser.parse(text) }
    var selectedZoomImageUrl by remember { mutableStateOf<String?>(null) }

    if (segments.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = imageAlignment
    ) {
        segments.forEach { segment ->
            when (segment) {
                is QuizContentSegment.Text -> {
                    Text(
                        text = segment.text,
                        style = textStyle.copy(
                            color = textColor,
                            fontWeight = fontWeight ?: textStyle.fontWeight
                        )
                    )
                }
                is QuizContentSegment.Image -> {
                    QuizImageCard(
                        imageUrl = segment.url,
                        altText = segment.altText,
                        maxHeight = maxImageHeight,
                        showBorder = showImageBorder,
                        onImageClick = {
                            if (allowZoomDialog) {
                                selectedZoomImageUrl = segment.url
                            }
                        }
                    )
                }
            }
        }
    }

    // Fullscreen Zoom Dialog on click
    if (selectedZoomImageUrl != null) {
        QuizImageZoomDialog(
            imageUrl = selectedZoomImageUrl!!,
            onDismiss = { selectedZoomImageUrl = null }
        )
    }
}

/**
 * Image card that renders an SVG or Web/Cloud image with loading shimmer, error state, and zoom affordance.
 */
@Composable
fun QuizImageCard(
    imageUrl: String,
    modifier: Modifier = Modifier,
    altText: String? = null,
    maxHeight: Dp = 160.dp,
    showBorder: Boolean = true,
    onImageClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .then(
                if (showBorder) {
                    Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onImageClick)
            .padding(4.dp)
    ) {
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = altText ?: "চিত্র",
            modifier = Modifier
                .heightIn(min = 40.dp, max = maxHeight)
                .wrapContentHeight()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High
        ) {
            val painterState = painter.state
            when (painterState) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier
                            .height(maxHeight.coerceAtMost(100.dp))
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "ছবি লোড হচ্ছে...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color(0xFFFEF2F2), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ছবি লোড করা যায়নি (ক্লিক করুন)",
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }

        // Tap to zoom icon badge overlay in corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .padding(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "বড় করে দেখুন",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Fullscreen Interactive Zoom Dialog with Pan & Pinch-to-Zoom
 */
@Composable
fun QuizImageZoomDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.7f, 5f)
        val extraWidth = (scale - 1) * 300
        val extraHeight = (scale - 1) * 300
        val maxX = extraWidth / 2
        val maxY = extraHeight / 2
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(-maxX.coerceAtLeast(0f), maxX.coerceAtLeast(0f)),
            y = (offset.y + panChange.y).coerceIn(-maxY.coerceAtLeast(0f), maxY.coerceAtLeast(0f))
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            // Header with Close & Reset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "জুম বা প্যান করুন",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Reset Button
                    if (scale != 1f || offset != Offset.Zero) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "রিসেট",
                                tint = Color.White
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = Color.White
                        )
                    }
                }
            }

            // Image Container with Pan & Zoom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = "ফুলস্ক্রিন চিত্র",
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                ) {
                    val state = painter.state
                    if (state is AsyncImagePainter.State.Loading) {
                        CircularProgressIndicator(color = Color.White)
                    } else if (state is AsyncImagePainter.State.Error) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "চিত্রটি লোড করা সম্ভব হয়নি",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
        }
    }
}
