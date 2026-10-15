package com.example.ui.components

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * HTML5 Web Player embedded inside WebView for directly streaming HLS .m3u8 URLs
 */
@Composable
fun HlsWebPlayerView(
    streamUrl: String,
    onBackToStream: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewInstance = null
        }
    }

    val htmlContent = remember(streamUrl) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                video { width: 100%; height: 100%; object-fit: contain; }
            </style>
            <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
        </head>
        <body>
            <video id="video" controls autoplay playsinline webkit-playsinline></video>
            <script>
                var video = document.getElementById('video');
                var videoSrc = "$streamUrl";
                if (Hls.isSupported()) {
                    var hls = new Hls({ enableWorker: true, lowLatencyMode: true });
                    hls.loadSource(videoSrc);
                    hls.attachMedia(video);
                    hls.on(Hls.Events.MANIFEST_PARSED, function() { video.play(); });
                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    video.src = videoSrc;
                    video.addEventListener('loadedmetadata', function() { video.play(); });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    val baseUrl = try {
                        val uri = android.net.Uri.parse(streamUrl)
                        if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else "https://app.shikho.com"
                    } catch (_: Exception) {
                        "https://app.shikho.com"
                    }
                    loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                    lastLoadedUrl = streamUrl
                    webViewInstance = this
                }
            },
            update = { wv ->
                if (lastLoadedUrl != streamUrl) {
                    lastLoadedUrl = streamUrl
                    val baseUrl = try {
                        val uri = android.net.Uri.parse(streamUrl)
                        if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else "https://app.shikho.com"
                    } catch (_: Exception) {
                        "https://app.shikho.com"
                    }
                    wv.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2563EB), modifier = Modifier.size(32.dp))
            }
        }

        // Top control buttons bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🌐 ব্রাউজার প্লেয়ার",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(onClick = { 
                        isLoading = true
                        webViewInstance?.reload() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "রিফ্রেশ", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "ব্রাউজার", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.9f),
                    modifier = Modifier.clickable { onBackToStream() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ExoPlayer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
