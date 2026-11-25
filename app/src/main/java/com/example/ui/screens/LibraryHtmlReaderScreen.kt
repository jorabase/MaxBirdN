package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.download.AppFileDownloadManager
import com.example.library.LibraryRepository
import com.example.tts.OfflineBookTtsManager
import com.example.utils.AppLang
import com.example.utils.isBangla
import com.example.utils.tr
import com.example.utils.toBengaliDigits
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import kotlin.math.roundToInt

private enum class ReaderThemeMode(val labelEn: String, val labelBn: String) {
    ORIGINAL("Original", "মূল"),
    LIGHT("Light", "লাইট"),
    SEPIA("Sepia", "সেপিয়া"),
    DARK("Dark", "ডার্ক");

    val displayLabel: String get() = AppLang.tr(labelEn, labelBn)
}

/**
 * Exact JavaScript bridge exposed as `window.AndroidApp` inside the HTML book WebView.
 * Compatible out-of-the-box with HTML files calling:
 *  - `window.AndroidApp.speak(text, rate)` / `window.AndroidApp.speak(text)`
 *  - `window.AndroidApp.stop()`
 *  - Callback `window.onAndroidTtsDone()` fired on Main Thread upon utterance completion.
 */
private class AndroidAppTtsJsBridge(
    private val ttsManager: OfflineBookTtsManager,
    private val mainHandler: Handler,
    private val getWebView: () -> WebView?,
    private val onJsTriggeredSpeech: (isSpeaking: Boolean) -> Unit
) {
    @JavascriptInterface
    fun speak(text: String?, rate: Float) {
        val safeText = text?.trim().orEmpty()
        if (safeText.isBlank()) {
            notifyDoneToWebView()
            return
        }
        val safeRate = if (rate.isNaN() || rate <= 0f) 1.0f else rate.coerceIn(0.6f, 2.0f)
        mainHandler.post { onJsTriggeredSpeech(true) }
        ttsManager.speak(
            text = safeText,
            rate = safeRate,
            onDone = {
                mainHandler.post {
                    onJsTriggeredSpeech(false)
                    notifyDoneToWebView()
                }
            }
        )
    }

    @JavascriptInterface
    fun speak(text: String?) {
        speak(text, ttsManager.state.value.speechRate)
    }

    @JavascriptInterface
    fun stop() {
        ttsManager.stop()
        mainHandler.post { onJsTriggeredSpeech(false) }
    }

    @JavascriptInterface
    fun pause() {
        ttsManager.pause()
    }

    @JavascriptInterface
    fun resume() {
        ttsManager.resume()
    }

    @JavascriptInterface
    fun isSpeaking(): Boolean = ttsManager.isCurrentlySpeaking()

    private fun notifyDoneToWebView() {
        mainHandler.post {
            val wv = getWebView() ?: return@post
            wv.evaluateJavascript(
                "try { if (typeof window.onAndroidTtsDone === 'function') { window.onAndroidTtsDone(); } } catch(e) {}",
                null
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHtmlReaderScreen(
    fileId: String,
    title: String,
    subtitle: String,
    remoteUrl: String,
    initialLocalFilePath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val downloadManager = remember { AppFileDownloadManager.getInstance(context) }
    val isDarkSystem = com.example.ui.theme.isAppInDarkTheme()

    val ttsManager = remember { OfflineBookTtsManager(context.applicationContext) }
    val ttsState by ttsManager.state.collectAsStateWithLifecycle()

    val downloadedEntity by remember(fileId) {
        downloadManager.getDownloadedItemById(fileId)
    }.collectAsStateWithLifecycle(initialValue = null)

    val resolvedLocalPath = remember(initialLocalFilePath, downloadedEntity) {
        val dbPath = downloadedEntity?.takeIf { it.isCompleted }?.localFilePath.orEmpty()
        when {
            dbPath.isNotBlank() && File(dbPath).exists() && File(dbPath).length() > 0L -> dbPath
            initialLocalFilePath.isNotBlank() && File(initialLocalFilePath).exists() && File(initialLocalFilePath).length() > 0L -> initialLocalFilePath
            else -> ""
        }
    }
    val isOfflineAvailable = resolvedLocalPath.isNotBlank()
    val isDownloadingNow = downloadedEntity?.isDownloading == true

    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoadingHtml by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadedFromOffline by remember { mutableStateOf(false) }

    var readerTheme by remember { mutableStateOf(ReaderThemeMode.ORIGINAL) }
    var textZoomPercent by remember { mutableIntStateOf(100) }
    var showTtsControlBar by remember { mutableStateOf(false) }
    var showReaderOptionsSheet by remember { mutableStateOf(false) }
    var showVoiceSettingsDialog by remember { mutableStateOf(false) }
    var isHtmlDrivenSpeech by remember { mutableStateOf(false) }

    // Native paragraph-by-paragraph reader state (used when user taps Read Aloud from the 3-dot menu)
    var extractedParagraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentParagraphIdx by remember { mutableIntStateOf(-1) }
    var isNativeAutoReading by remember { mutableStateOf(false) }

    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    val jsBridge = remember(ttsManager) {
        AndroidAppTtsJsBridge(
            ttsManager = ttsManager,
            mainHandler = mainHandler,
            getWebView = { webViewRef[0] },
            onJsTriggeredSpeech = { speaking ->
                isHtmlDrivenSpeech = speaking
                if (speaking) {
                    isNativeAutoReading = false
                }
            }
        )
    }

    // Load HTML content (offline internal storage first, else Cloudflare R2 online)
    fun reloadHtml() {
        scope.launch {
            isLoadingHtml = true
            loadError = null
            if (resolvedLocalPath.isNotBlank()) {
                val res = LibraryRepository.readLocalHtmlFile(resolvedLocalPath)
                res.onSuccess { html ->
                    htmlContent = html
                    loadedFromOffline = true
                    isLoadingHtml = false
                }.onFailure {
                    if (remoteUrl.isNotBlank()) {
                        val onlineRes = LibraryRepository.fetchOnlineHtmlContent(remoteUrl)
                        onlineRes.onSuccess { html ->
                            htmlContent = html
                            loadedFromOffline = false
                            isLoadingHtml = false
                        }.onFailure { err ->
                            loadError = err.localizedMessage ?: AppLang.tr("Unable to load book.", "বইটি লোড করা যায়নি।")
                            isLoadingHtml = false
                        }
                    } else {
                        loadError = it.localizedMessage ?: AppLang.tr("Offline file not found.", "অফলাইন ফাইল পাওয়া যায়নি।")
                        isLoadingHtml = false
                    }
                }
            } else {
                val onlineRes = LibraryRepository.fetchOnlineHtmlContent(remoteUrl)
                onlineRes.onSuccess { html ->
                    htmlContent = html
                    loadedFromOffline = false
                    isLoadingHtml = false
                }.onFailure { err ->
                    loadError = err.localizedMessage ?: AppLang.tr("Unable to load book online.", "অনলাইন থেকে বইটি লোড করা যায়নি।")
                    isLoadingHtml = false
                }
            }
        }
    }

    LaunchedEffect(resolvedLocalPath, remoteUrl) {
        if (htmlContent == null) {
            reloadHtml()
        }
    }

    // Highlight active paragraph in WebView during native read-aloud
    fun highlightParagraphInWebView(index: Int) {
        val wv = webViewRef[0] ?: return
        val js = """
            (function() {
                document.querySelectorAll('.maxbird-tts-hl').forEach(function(el) {
                    el.classList.remove('maxbird-tts-hl');
                    el.style.outline = '';
                    el.style.backgroundColor = el.getAttribute('data-mb-orig-bg') || '';
                });
                var idx = $index;
                if (idx >= 0 && window.__mbTtsNodes && window.__mbTtsNodes[idx]) {
                    var target = window.__mbTtsNodes[idx];
                    if (!target.hasAttribute('data-mb-orig-bg')) {
                        target.setAttribute('data-mb-orig-bg', target.style.backgroundColor || '');
                    }
                    target.classList.add('maxbird-tts-hl');
                    target.style.backgroundColor = 'rgba(16, 185, 129, 0.22)';
                    target.style.borderRadius = '6px';
                    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    fun speakParagraphAt(index: Int, list: List<String>) {
        if (index !in list.indices) {
            isNativeAutoReading = false
            currentParagraphIdx = -1
            highlightParagraphInWebView(-1)
            return
        }
        currentParagraphIdx = index
        isNativeAutoReading = true
        highlightParagraphInWebView(index)
        ttsManager.speak(
            text = list[index],
            rate = ttsState.speechRate,
            onDone = {
                mainHandler.post {
                    if (isNativeAutoReading && currentParagraphIdx == index) {
                        val nextIdx = index + 1
                        if (nextIdx < list.size) {
                            speakParagraphAt(nextIdx, list)
                        } else {
                            isNativeAutoReading = false
                            currentParagraphIdx = -1
                            highlightParagraphInWebView(-1)
                        }
                    }
                }
            }
        )
    }

    fun startNativeReadAloud() {
        val wv = webViewRef[0] ?: return
        showTtsControlBar = true
        val extractJs = """
            (function() {
                var nodes = Array.from(document.querySelectorAll('h1,h2,h3,h4,p,li,blockquote,td,th,.card-title,.lesson-text'));
                var filtered = nodes.filter(function(el) {
                    if (!el || !el.innerText) return false;
                    var style = window.getComputedStyle(el);
                    if (style && (style.display === 'none' || style.visibility === 'hidden')) return false;
                    if (el.closest('button, nav, script, style, .no-tts')) return false;
                    var txt = el.innerText.replace(/\s+/g, ' ').trim();
                    if (txt.length < 3) return false;
                    // Avoid duplicate parent+child text
                    return !nodes.some(function(other) { return other !== el && el.contains(other) && other.innerText.trim().length > 3; });
                });
                window.__mbTtsNodes = filtered;
                return JSON.stringify(filtered.map(function(el) { return el.innerText.replace(/\s+/g, ' ').trim(); }));
            })();
        """.trimIndent()
        wv.evaluateJavascript(extractJs) { rawResult ->
            try {
                if (rawResult.isNullOrBlank() || rawResult == "null") return@evaluateJavascript
                // evaluateJavascript returns a JSON-encoded string literal
                val unescaped = org.json.JSONTokener(rawResult).nextValue() as? String ?: return@evaluateJavascript
                val arr = JSONArray(unescaped)
                val paras = buildList {
                    for (i in 0 until arr.length()) {
                        val t = arr.optString(i, "").trim()
                        if (t.isNotBlank()) add(t)
                    }
                }
                if (paras.isEmpty()) {
                    Toast.makeText(context, AppLang.tr("No readable text found on this page", "এই পাতায় পড়ার মতো লেখা পাওয়া যায়নি"), Toast.LENGTH_SHORT).show()
                } else {
                    extractedParagraphs = paras
                    val startIdx = if (currentParagraphIdx in paras.indices) currentParagraphIdx else 0
                    speakParagraphAt(startIdx, paras)
                }
            } catch (_: Throwable) {
                Toast.makeText(context, AppLang.tr("Unable to process text", "টেক্সট প্রসেস করা যায়নি"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun applyThemeOverlayToWebView(wv: WebView?, mode: ReaderThemeMode) {
        if (wv == null) return
        val css = when (mode) {
            ReaderThemeMode.ORIGINAL -> ""
            ReaderThemeMode.LIGHT -> "html, body { background: #FFFFFF !important; color: #111827 !important; }"
            ReaderThemeMode.SEPIA -> "html, body { background: #FBF4E4 !important; color: #3B2F2F !important; } p, li, div, span { color: #3B2F2F !important; }"
            ReaderThemeMode.DARK -> "html, body { background: #0B1220 !important; color: #E2E8F0 !important; } p, li, h1, h2, h3, h4, span { color: #E2E8F0 !important; }"
        }
        val escapedCss = css.replace("'", "\\'")
        val js = """
            (function() {
                var id = 'maxbird-reader-theme-style';
                var el = document.getElementById(id);
                if (!el) {
                    el = document.createElement('style');
                    el.id = id;
                    document.head && document.head.appendChild(el);
                }
                el.innerHTML = '$escapedCss';
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    LaunchedEffect(readerTheme) {
        applyThemeOverlayToWebView(webViewRef[0], readerTheme)
    }

    LaunchedEffect(textZoomPercent) {
        webViewRef[0]?.settings?.textZoom = textZoomPercent
    }

    // Save scroll progress & release TTS cleanly on exit
    DisposableEffect(Unit) {
        onDispose {
            val wv = webViewRef[0]
            if (wv != null && wv.contentHeight > 0) {
                val ratio = (wv.scrollY.toFloat() / (wv.contentHeight * wv.scale).coerceAtLeast(1f)).coerceIn(0f, 1f)
                LibraryRepository.saveScrollProgress(context, fileId, ratio)
            }
            isNativeAutoReading = false
            ttsManager.release()
            webViewRef[0]?.apply {
                removeJavascriptInterface("AndroidApp")
                stopLoading()
                destroy()
            }
            webViewRef[0] = null
        }
    }

    BackHandler {
        isNativeAutoReading = false
        ttsManager.stop()
        onBack()
    }

    if (showVoiceSettingsDialog) {
        val cachedIndex = remember { LibraryRepository.getCachedIndexSync(context) }
        LibraryVoiceSettingsDialog(
            ttsModels = cachedIndex?.ttsModels.orEmpty(),
            onDismiss = { showVoiceSettingsDialog = false },
            onVoiceChanged = { ttsManager.refreshVoiceAvailability() }
        )
    }

    val containerBg = when (readerTheme) {
        ReaderThemeMode.DARK -> Color(0xFF0B1220)
        ReaderThemeMode.SEPIA -> Color(0xFFFBF4E4)
        ReaderThemeMode.LIGHT -> Color.White
        ReaderThemeMode.ORIGINAL -> if (isDarkSystem) Color(0xFF0B1220) else Color(0xFF0F172A)
    }

    // 3-Dot Quick Reader Options Bottom Sheet (replaces the old bulky top bar)
    if (showReaderOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReaderOptionsSheet = false },
            containerColor = if (isDarkSystem) Color(0xFF0F1A2E) else Color.White,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Book Header + Back Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            showReaderOptionsSheet = false
                            isNativeAutoReading = false
                            ttsManager.stop()
                            onBack()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("reader_back_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF10B981).copy(alpha = 0.14f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back", "পেছনে"),
                            tint = Color(0xFF10B981)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifBlank { tr("Book", "বই") },
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val modeTag = if (loadedFromOffline) tr("Offline", "অফলাইন") else tr("Online", "অনলাইন")
                        Text(
                            text = if (subtitle.isNotBlank()) "$subtitle • $modeTag" else modeTag,
                            fontSize = 11.5.sp,
                            color = if (loadedFromOffline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary Quick Actions Row: Audio Reader, Offline Save, Voice Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val audioActive = ttsState.isSpeaking || showTtsControlBar
                    Surface(
                        onClick = {
                            showReaderOptionsSheet = false
                            if (!showTtsControlBar) {
                                showTtsControlBar = true
                                if (!ttsState.isSpeaking && !isHtmlDrivenSpeech) {
                                    startNativeReadAloud()
                                }
                            } else {
                                if (ttsState.isSpeaking) {
                                    isNativeAutoReading = false
                                    ttsManager.stop()
                                    highlightParagraphInWebView(-1)
                                }
                                showTtsControlBar = false
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (audioActive) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.13f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reader_tts_toggle_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (ttsState.isSpeaking) Icons.Default.VolumeUp else Icons.Default.Headphones,
                                contentDescription = tr("Audio", "অডিও"),
                                tint = if (audioActive) Color.White else Color(0xFF10B981),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (audioActive) tr("Stop Audio", "অডিও বন্ধ") else tr("Read Aloud", "অডিও রিডার"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (audioActive) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }

                    // Offline Download / Saved Status
                    Surface(
                        onClick = {
                            if (!isOfflineAvailable && remoteUrl.isNotBlank() && !isDownloadingNow) {
                                LibraryRepository.downloadHtmlFileByParams(
                                    context = context,
                                    downloadEntityId = fileId,
                                    title = title,
                                    subtitle = subtitle,
                                    remoteUrl = remoteUrl
                                )
                                Toast.makeText(
                                    context,
                                    AppLang.tr("Saving for offline reading...", "অফলাইনের জন্য ডাউনলোড শুরু হয়েছে..."),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showReaderOptionsSheet = false
                            } else if (isOfflineAvailable) {
                                Toast.makeText(
                                    context,
                                    AppLang.tr("Already saved offline", "বইটি আগেই অফলাইনে সেভ করা আছে"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isOfflineAvailable) {
                            Color(0xFF10B981).copy(alpha = 0.12f)
                        } else {
                            Color(0xFF0284C7).copy(alpha = 0.13f)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reader_download_offline_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isDownloadingNow) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.2.dp,
                                    color = Color(0xFF0284C7)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isOfflineAvailable) Icons.Default.DownloadDone else Icons.Default.DownloadForOffline,
                                    contentDescription = tr("Offline Download", "ডাউনলোড"),
                                    tint = if (isOfflineAvailable) Color(0xFF10B981) else Color(0xFF0284C7),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = when {
                                    isDownloadingNow -> tr("Saving...", "ডাউনলোড...")
                                    isOfflineAvailable -> tr("Saved", "সেভড")
                                    else -> tr("Download", "ডাউনলোড")
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }

                    // Voice Settings
                    Surface(
                        onClick = {
                            showReaderOptionsSheet = false
                            showVoiceSettingsDialog = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.13f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reader_voice_settings_button")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = tr("Voice", "ভয়েস"),
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = tr("Voice", "ভয়েস"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Reading Theme Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tr("Theme", "পড়ার থিম"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isBnNow = isBangla()
                        ReaderThemeMode.entries.forEach { mode ->
                            val selected = readerTheme == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { readerTheme = mode }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBnNow) mode.labelBn else mode.labelEn,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Font Size / Zoom Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tr("Text Size", "লেখার সাইজ"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { textZoomPercent = (textZoomPercent - 10).coerceAtLeast(75) }
                        ) {
                            Text(
                                text = "A-",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Text(
                            text = "${textZoomPercent.toBengaliDigits()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { textZoomPercent = (textZoomPercent + 10).coerceAtMost(175) }
                        ) {
                            Text(
                                text = "A+",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        if (textZoomPercent != 100) {
                            TextButton(
                                onClick = { textZoomPercent = 100 },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(tr("Reset", "রিসেট"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }
                    }
                }
            }
        }
    }

    // True Full-Screen Reader Surface (no bulky top bar!)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(containerBg)
            .statusBarsPadding()
    ) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val fabSizePx = with(density) { 48.dp.toPx() }

        // Draggable offset for the unobtrusive floating 3-dot button (anchored at BottomEnd by default so it never covers HTML header icons)
        var fabOffsetX by remember { mutableFloatStateOf(0f) }
        var fabOffsetY by remember { mutableFloatStateOf(0f) }

        when {
            isLoadingHtml -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF10B981))
                        Text(
                            text = tr("Opening book...", "বইটি খোলা হচ্ছে..."),
                            fontSize = 13.5.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

                loadError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = loadError ?: tr("Unable to load book", "বইটি লোড করা যায়নি"),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { reloadHtml() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text(tr("Try Again", "আবার চেষ্টা করুন"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                htmlContent != null -> {
                    val baseUrlForWebView = remember(remoteUrl) {
                        if (remoteUrl.startsWith("http", ignoreCase = true)) {
                            remoteUrl.substringBeforeLast('/') + "/"
                        } else {
                            "file:///android_asset/"
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef[0] = this
                                setBackgroundColor(if (isDarkSystem) AndroidColor.parseColor("#0B1220") else AndroidColor.WHITE)
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    textZoom = textZoomPercent
                                    cacheMode = if (loadedFromOffline) WebSettings.LOAD_CACHE_ELSE_NETWORK else WebSettings.LOAD_DEFAULT
                                }
                                // CRITICAL: Exact JS Bridge name `AndroidApp` matching HTML books' `window.AndroidApp.speak(text, rate)` & `.stop()`
                                addJavascriptInterface(jsBridge, "AndroidApp")

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString().orEmpty()
                                        // Allow anchor jumps within the same HTML page
                                        return !(url.startsWith("#") || url.contains("#"))
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        applyThemeOverlayToWebView(view, readerTheme)
                                        val savedRatio = LibraryRepository.getScrollProgress(ctx, fileId)
                                        if (savedRatio > 0.02f && view != null) {
                                            view.postDelayed({
                                                val targetY = (view.contentHeight * view.scale * savedRatio).toInt()
                                                if (targetY > 0) view.scrollTo(0, targetY)
                                            }, 250L)
                                        }
                                    }
                                }

                                loadDataWithBaseURL(
                                    baseUrlForWebView,
                                    htmlContent!!,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = if (showTtsControlBar) 108.dp else 0.dp)
                    )
                }
            }

            // Sleek, unobtrusive, draggable 3-Dot Menu Floating Button (Bottom-Right so HTML top bar is 100% clear)
            if (!showTtsControlBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 20.dp)
                        .offset { IntOffset(fabOffsetX.roundToInt(), fabOffsetY.roundToInt()) }
                        .pointerInput(maxWidthPx, maxHeightPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val minX = -(maxWidthPx - fabSizePx - 36f).coerceAtLeast(0f)
                                val minY = -(maxHeightPx - fabSizePx - 80f).coerceAtLeast(0f)
                                fabOffsetX = (fabOffsetX + dragAmount.x).coerceIn(minX, 0f)
                                fabOffsetY = (fabOffsetY + dragAmount.y).coerceIn(minY, 0f)
                            }
                        }
                ) {
                    Surface(
                        onClick = { showReaderOptionsSheet = true },
                        shape = CircleShape,
                        color = Color(0xFF0F172A).copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.35f))
                            .testTag("reader_more_options_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = tr("Reader options", "রিডার অপশন"),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            if (ttsState.isSpeaking) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-8).dp, y = 8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Floating Offline TTS Audio Player Bar
            AnimatedVisibility(
                visible = showTtsControlBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isDarkSystem) Color(0xFF0F1D32) else Color.White,
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (ttsState.isSpeaking) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Text(
                                text = when {
                                    ttsState.isSpeaking && isNativeAutoReading && extractedParagraphs.isNotEmpty() ->
                                        tr(
                                            "Reading (${currentParagraphIdx + 1}/${extractedParagraphs.size}) • ${if (ttsState.detectedLanguage == "en") "English" else "Bangla"}",
                                            "পড়া হচ্ছে (${(currentParagraphIdx + 1).toBengaliDigits()}/${extractedParagraphs.size.toBengaliDigits()}) • ${if (ttsState.detectedLanguage == "en") "English" else "বাংলা"}"
                                        )
                                    ttsState.isSpeaking ->
                                        "${ttsState.engineLabelBn} • ${if (ttsState.detectedLanguage == "en") "English" else tr("Bangla", "বাংলা")}"
                                    ttsState.isPaused -> tr("Paused", "পজ করা")
                                    else -> ttsState.engineLabelBn
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            // Speed Cycle Button (0.75x -> 1.0x -> 1.25x -> 1.5x)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val nextRate = when {
                                            ttsState.speechRate < 0.9f -> 1.0f
                                            ttsState.speechRate < 1.15f -> 1.25f
                                            ttsState.speechRate < 1.4f -> 1.5f
                                            else -> 0.75f
                                        }
                                        ttsManager.setSpeechRate(nextRate)
                                        if (isNativeAutoReading && currentParagraphIdx in extractedParagraphs.indices) {
                                            speakParagraphAt(currentParagraphIdx, extractedParagraphs)
                                        }
                                    }
                            ) {
                                Text(
                                    text = "${String.format("%.2g", ttsState.speechRate)}x",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isNativeAutoReading = false
                                    ttsManager.stop()
                                    highlightParagraphInWebView(-1)
                                    showTtsControlBar = false
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = tr("Close", "বন্ধ করুন"),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = {
                                    if (extractedParagraphs.isNotEmpty() && currentParagraphIdx > 0) {
                                        speakParagraphAt(currentParagraphIdx - 1, extractedParagraphs)
                                    }
                                },
                                enabled = extractedParagraphs.isNotEmpty() && currentParagraphIdx > 0
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = tr("Previous", "আগের"))
                            }

                            FilledIconButton(
                                onClick = {
                                    when {
                                        ttsState.isSpeaking -> ttsManager.pause()
                                        ttsState.isPaused -> ttsManager.resume()
                                        else -> startNativeReadAloud()
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(46.dp)
                                    .testTag("reader_tts_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (ttsState.isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (ttsState.isSpeaking) tr("Pause", "পজ") else tr("Play", "চালু"),
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (extractedParagraphs.isNotEmpty() && currentParagraphIdx < extractedParagraphs.lastIndex) {
                                        speakParagraphAt(currentParagraphIdx + 1, extractedParagraphs)
                                    }
                                },
                                enabled = extractedParagraphs.isNotEmpty() && currentParagraphIdx < extractedParagraphs.lastIndex
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = tr("Next", "পরের"))
                            }

                            IconButton(
                                onClick = {
                                    isNativeAutoReading = false
                                    isHtmlDrivenSpeech = false
                                    ttsManager.stop()
                                    highlightParagraphInWebView(-1)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = tr("Stop", "থামান"),
                                    tint = Color(0xFFEF4444)
                                )
                            }
                    }
                }
            }
        }
    }
}
