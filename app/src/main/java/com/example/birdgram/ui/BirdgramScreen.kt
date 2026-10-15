package com.example.birdgram.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import com.example.birdgram.BirdgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * JavaScript Bridge connecting WebView Telegram Client to Android BirdgramRepository
 */
class BirdgramBridge(
    private val onSessionCaptured: (cookies: String, localStorageJson: String) -> Unit
) {
    @JavascriptInterface
    fun syncSession(cookies: String, localStorageJson: String) {
        onSessionCaptured(cookies, localStorageJson)
    }
}

/**
 * BirdgramScreen - 100% Immersive Native Telegram App Experience.
 *
 * Removes all outer clunky headers and navigation bars for true edge-to-edge native Telegram UI.
 * Features an ultra-premium soaring bird splash animation while loading,
 * ensuring zero web-like flicker or loading bars.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BirdgramScreen(
    repository: BirdgramRepository,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionInfo by repository.sessionInfo.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // File Chooser for Photo, Video, and Document uploads in Telegram chats
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            val uris = mutableListOf<Uri>()
            if (intent?.clipData != null) {
                val clipData = intent.clipData!!
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else if (intent?.data != null) {
                uris.add(intent.data!!)
            }
            filePathCallback?.onReceiveValue(uris.toTypedArray())
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // Smart Native Back Handler:
    // If inside a chat or search: goes back inside Telegram.
    // If at Telegram root (main chat list): smoothly returns to the Home screen!
    BackHandler {
        val web = webViewRef
        if (web != null && web.canGoBack()) {
            web.goBack()
        } else {
            onNavigateToHome()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1621))
    ) {
        // 1. Fullscreen Native Telegram WebView (Zero Overlapping Bars)
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#0E1621"))
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // Enable Cookies & HTML5 Storage
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    // Web Settings optimized to match official Telegram Android App
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(false)
                        // Official Chrome Mobile UA to ensure Telegram loads its modern app client seamlessly
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"
                    }

                    // Attach Bridge to persist sessions under student access credentials
                    addJavascriptInterface(
                        BirdgramBridge { cookies, localStorageJson ->
                            repository.onTelegramSessionCaptured(cookies, localStorageJson)
                        },
                        "BirdgramBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            hasError = false
                            // Inject native dark background & scrollbar styling immediately
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    var style = document.createElement('style');
                                    style.innerHTML = '::-webkit-scrollbar { display: none !important; } body { background-color: #0E1621 !important; -webkit-tap-highlight-color: transparent !important; }';
                                    document.head.appendChild(style);
                                })();
                                """.trimIndent(),
                                null
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.let { wv ->
                                coroutineScope.launch {
                                    injectAndExtractSession(wv, repository)
                                    // Smoothly delay transition so Telegram UI settles completely without flicker
                                    delay(600)
                                    isLoading = false
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                hasError = true
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString() ?: return false

                            // Handle Telegram deep links safely
                            if (targetUrl.startsWith("tg://") || targetUrl.startsWith("intent://")) {
                                try {
                                    val intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
                                    if (intent != null) {
                                        view?.context?.startActivity(intent)
                                        return true
                                    }
                                } catch (_: Exception) {
                                    return true
                                }
                            }

                            if (targetUrl.startsWith("tel:") || targetUrl.startsWith("mailto:")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                    view?.context?.startActivity(intent)
                                    return true
                                } catch (_: Exception) {
                                    return true
                                }
                            }

                            // Keep Telegram domains inside this native view
                            if (targetUrl.contains("web.telegram.org") || targetUrl.contains("t.me")) {
                                return false
                            }

                            // External links open in browser
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                view?.context?.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress >= 90) {
                                coroutineScope.launch {
                                    delay(400)
                                    isLoading = false
                                }
                            }
                        }

                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackInternal: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = filePathCallbackInternal

                            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            try {
                                fileChooserLauncher.launch(intent)
                            } catch (_: Exception) {
                                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                fileChooserLauncher.launch(fallbackIntent)
                            }
                            return true
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            val host = request?.origin?.host ?: ""
                            if (host.endsWith("telegram.org", ignoreCase = true) || host.endsWith("shikho.com", ignoreCase = true)) {
                                request?.grant(request.resources)
                            } else {
                                request?.deny()
                            }
                        }
                    }

                    // Pre-restore session cookies and load Telegram Web
                    coroutineScope.launch {
                        val session = repository.getActiveSessionForInjection()
                        if (session != null && session.cookiesData.isNotBlank()) {
                            try {
                                val cm = CookieManager.getInstance()
                                session.cookiesData.split(";").forEach { c ->
                                    val tr = c.trim()
                                    if (tr.isNotEmpty()) {
                                        cm.setCookie("https://web.telegram.org", tr)
                                    }
                                }
                                cm.flush()
                            } catch (_: Exception) {}
                        }
                        withContext(Dispatchers.Main) {
                            loadUrl(sessionInfo.selectedVariant.url)
                        }
                    }

                    webViewRef = this
                }
            },
            update = { webView ->
                webViewRef = webView
            }
        )

        // 2. Draggable Floating Home Button positioned at Top-End (Top-Right)
        // Completely leaves Telegram's top-left 3-line hamburger menu (☰) 100% unobstructed and clickable!
        var homeOffsetX by remember { mutableFloatStateOf(0f) }
        var homeOffsetY by remember { mutableFloatStateOf(0f) }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xDD17212B),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 12.dp)
                .offset { IntOffset(homeOffsetX.roundToInt(), homeOffsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        homeOffsetX += dragAmount.x
                        homeOffsetY += dragAmount.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onNavigateToHome() }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .defaultMinSize(minHeight = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Return to Shikho Home",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "হোম",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // 3. Ethereal Soaring Bird Splash Screen (Shown when launching/loading Birdgram)
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(550)) + scaleOut(targetScale = 1.04f, animationSpec = tween(550))
        ) {
            BirdgramSplashAnimation(
                modifier = Modifier.fillMaxSize()
            )
        }

        // 4. Offline / Error Retry Screen (Only if network completely disconnects)
        AnimatedVisibility(
            visible = hasError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1621))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "টেলিগ্রাম সার্ভারে সংযোগ করা যায়নি",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ইন্টারনেট সংযোগ চেক করে পুনরায় চেষ্টা করুন।",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            hasError = false
                            isLoading = true
                            webViewRef?.reload()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("পুনরায় চেষ্টা করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Injects session restoration script if local storage is missing keys,
 * and extracts any active session to pass to BirdgramBridge.
 */
private suspend fun injectAndExtractSession(webView: WebView?, repository: BirdgramRepository) {
    if (webView == null) return
    val savedSession = repository.getActiveSessionForInjection()
    val savedLocalStorage = savedSession?.localStorageData ?: ""

    withContext(Dispatchers.Main) {
        val sanitizedJson = if (savedLocalStorage.isNotBlank()) {
            JSONObject(savedLocalStorage).toString()
        } else "{}"

        val jsScript = """
            (function() {
                try {
                    // 1. Auto-inject restored localStorage keys if missing
                    var backup = $sanitizedJson;
                    if (backup && typeof backup === 'object') {
                        var count = 0;
                        for (var k in backup) {
                            if (backup.hasOwnProperty(k) && !localStorage.getItem(k) && backup[k] !== null) {
                                localStorage.setItem(k, backup[k]);
                                count++;
                            }
                        }
                    }

                    // 2. Dump active localStorage state to Android Bridge
                    function syncToApp() {
                        try {
                            var store = {};
                            for (var i = 0; i < localStorage.length; i++) {
                                var key = localStorage.key(i);
                                store[key] = localStorage.getItem(key);
                            }
                            if (window.BirdgramBridge && window.BirdgramBridge.syncSession) {
                                window.BirdgramBridge.syncSession(document.cookie || '', JSON.stringify(store));
                            }
                        } catch(e) {}
                    }

                    syncToApp();
                    window.addEventListener('storage', syncToApp);
                } catch(err) {}
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsScript, null)
    }
}
