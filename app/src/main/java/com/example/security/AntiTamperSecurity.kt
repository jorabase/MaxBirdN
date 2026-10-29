package com.example.security

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.view.Window
import android.view.WindowManager
import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.NetworkInterface
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

data class SecurityViolation(
    val code: String,
    val titleBn: String,
    val messageBn: String,
    val detectedDetail: String
)

/**
 * Advanced Release-Grade Security Shield for MaxBird:
 * 1. Screenshot & Screen Recording Protection (FLAG_SECURE + Android 13+ Recents Shield)
 *    - Controlled at build time via `BuildConfig.ENABLE_SCREENSHOT_PROTECTION` (GitHub Actions checkbox)
 * 2. Anti-Network Capture & Anti-MITM Protection (Blocks Reqable, HttpCanary, PCAPdroid, HTTP Toolkit,
 *    Packet Capture, Charles, Burp, Mitmproxy, System Proxy, and Root/Magisk MITM CAs)
 *    - Controlled at build time via `BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE` (GitHub Actions checkbox)
 * 3. Anti-Tamper, Anti-Frida, Anti-Xposed, Anti-SSL-Unpinning & Anti-Debugger Protection
 *    - Controlled at build time via `BuildConfig.ENABLE_ANTI_TAMPER` (GitHub Actions checkbox)
 * 4. Optional Strict Anti-VPN Protection
 *    - Controlled at build time via `BuildConfig.BLOCK_VPN_CONNECTION` (GitHub Actions checkbox)
 */
object AntiTamperSecurity {

    @Volatile
    private var appContextRef: Context? = null

    @Volatile
    private var activeMitmCaptureDetail: String? = null

    private val SUSPICIOUS_HOOK_FILES = listOf(
        "/data/local/tmp/frida",
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
        "/system/framework/XposedBridge.jar",
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so"
    )

    /**
     * Known MITM Certificate Issuer / Subject keywords used by Reqable, HttpCanary, Burp, Charles, Mitmproxy, etc.
     */
    private val MITM_CERT_KEYWORDS = listOf(
        "reqable",
        "httpcanary",
        "guoshi",
        "portswigger",
        "burp suite",
        "burpsuite",
        "charles proxy",
        "xk72",
        "fiddler",
        "do_not_trust",
        "mitmproxy",
        "httptoolkit",
        "http toolkit",
        "pcapdroid",
        "proxyman",
        "sslcapture",
        "packet capture",
        "netcapture",
        "adguard personal ca",
        "zap-roxy"
    )

    private val SUSPICIOUS_HOOK_CLASSES = listOf(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XposedHelpers",
        "com.saurik.substrate.MS\$MethodPointer",
        "me.weishu.epic.art.Epic",
        "com.swift.sandhook.SandHook",
        "just.trust.me.Main",
        "mobi.acpm.sslunpinning.Main",
        "io.github.tehcneko.sslunpinning.Main"
    )

    fun init(context: Context) {
        appContextRef = context.applicationContext
    }

    /**
     * Clears transient MITM / TLS capture alert when the user taps Recheck after stopping capture
     */
    fun clearTransientCaptureAlert() {
        activeMitmCaptureDetail = null
    }

    /**
     * Applies or clears Screenshot & Screen Recording protection (FLAG_SECURE) on the Activity
     * based on `BuildConfig.ENABLE_SCREENSHOT_PROTECTION`.
     */
    fun applyScreenshotProtection(activity: Activity) {
        try {
            appContextRef = activity.applicationContext
            applyWindowSecureFlag(activity.window)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.setRecentsScreenshotEnabled(!BuildConfig.ENABLE_SCREENSHOT_PROTECTION)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Applies or clears FLAG_SECURE on any Window (Activity, Dialog, or ModalBottomSheet)
     */
    fun applyWindowSecureFlag(window: Window?) {
        if (window == null) return
        try {
            if (BuildConfig.ENABLE_SCREENSHOT_PROTECTION) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Performs a complete runtime security inspection according to the active BuildConfig flags.
     * Note: Having apps like Reqable or HttpCanary installed on the phone is completely allowed;
     * a violation is triggered ONLY when network capture / proxy / MITM interception is actively running on this app.
     */
    fun inspectSecurityViolation(context: Context? = appContextRef): SecurityViolation? {
        val ctx = context?.applicationContext ?: appContextRef

        // 1. Anti-Network Capture Check (Only when actively running on this app)
        if (BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE) {
            // 1A. Check active HTTP, HTTPS, SOCKS, or VPN Proxy routing this app's traffic
            val proxyDetail = detectActiveSystemProxy(ctx)
            if (proxyDetail != null) {
                return SecurityViolation(
                    code = "NETWORK_PROXY_DETECTED",
                    titleBn = "অ্যাপের ওপর নেটওয়ার্ক ক্যাপচার / প্রক্সি চালু রয়েছে!",
                    messageBn = "অ্যাপের ডাটা সুরক্ষার স্বার্থে এই অ্যাপের ওপর Reqable, HttpCanary বা অন্য কোনো Network Capture / Proxy চালু রেখে ব্যবহার করা যাবে না। অনুগ্রহ করে ক্যাপচারটি বন্ধ করে 'পুনরায় যাচাই করুন' বাটনে ট্যাপ করুন (অ্যাপ আনইনস্টল করার প্রয়োজন নেই)।",
                    detectedDetail = proxyDetail
                )
            }

            // 1B. Check if active TLS/SSL MITM traffic interception was caught on this app's connections
            val mitmDetail = activeMitmCaptureDetail
            if (mitmDetail != null) {
                // Auto-clear if VPN/Proxy capture is no longer active on this app
                val stillInVpnCapture = ctx != null && isVpnConnectionActiveForApp(ctx)
                if (!stillInVpnCapture) {
                    activeMitmCaptureDetail = null
                } else {
                    return SecurityViolation(
                        code = "ACTIVE_MITM_CAPTURE_DETECTED",
                        titleBn = "অ্যাপের নেটওয়ার্ক ট্রাফিক ক্যাপচার শনাক্ত হয়েছে!",
                        messageBn = "এই অ্যাপের নেটওয়ার্ক রিকোয়েস্ট Reqable বা অন্য কোনো ক্যাপচার টুল দিয়ে ইন্টারসেপ্ট করার চেষ্টা শনাক্ত হয়েছে। অনুগ্রহ করে এই অ্যাপের ওপর থেকে ক্যাপচার বা VPN ইন্টারসেপশন বন্ধ করে 'পুনরায় যাচাই করুন' বাটনে ট্যাপ করুন।",
                        detectedDetail = mitmDetail
                    )
                }
            }
        }

        // 2. Strict Anti-VPN Check (when enabled at build time)
        if (BuildConfig.BLOCK_VPN_CONNECTION && ctx != null) {
            if (isVpnConnectionActive(ctx)) {
                return SecurityViolation(
                    code = "VPN_DETECTED",
                    titleBn = "VPN কানেকশন শনাক্ত হয়েছে!",
                    messageBn = "নিরাপত্তার স্বার্থে VPN বা টানেল সার্ভিস চালু রেখে এই অ্যাপ ব্যবহার করা যাবে না। অনুগ্রহ করে আপনার VPN বন্ধ করে পুনরায় চেষ্টা করুন।",
                    detectedDetail = "Active VPN Transport / Tunnel Interface"
                )
            }
        }

        // 3. Anti-Tamper, Anti-Frida, Anti-Xposed & Debugger Check
        if (BuildConfig.ENABLE_ANTI_TAMPER) {
            if (ctx != null && isDebuggerAttached(ctx)) {
                return SecurityViolation(
                    code = "DEBUGGER_DETECTED",
                    titleBn = "ডিবাগার / ট্রেসার শনাক্ত হয়েছে!",
                    messageBn = "অ্যাপের নিরাপত্তা সুরক্ষার কারণে কোনো ডিবাগার বা রিভার্স ইঞ্জিনিয়ারিং টুল সংযুক্ত অবস্থায় অ্যাপটি চলবে না।",
                    detectedDetail = "Active Debugger / TracerPid"
                )
            }

            val hookDetail = detectHookingOrTamperDetail()
            if (hookDetail != null) {
                return SecurityViolation(
                    code = "HOOK_TAMPER_DETECTED",
                    titleBn = "অননুমোদিত হুক / মডিফিকেশন শনাক্ত হয়েছে!",
                    messageBn = "আপনার ডিভাইসে Frida, Xposed, LSPosed বা SSL-Unpinning হুক শনাক্ত হয়েছে। নিরাপত্তার স্বার্থে এগুলো বন্ধ করে অ্যাপটি পুনরায় চালু করুন।",
                    detectedDetail = hookDetail
                )
            }
        }

        return null
    }

    /**
     * Legacy boolean check for backward compatibility
     */
    fun isAppSecure(context: Context): Boolean {
        return inspectSecurityViolation(context) == null
    }

    /**
     * Checks if a system HTTP/HTTPS/SOCKS proxy or active network VPN HTTP proxy is configured for this app
     */
    fun detectActiveSystemProxy(context: Context?): String? {
        try {
            val httpHost = System.getProperty("http.proxyHost")?.trim().orEmpty()
            val httpsHost = System.getProperty("https.proxyHost")?.trim().orEmpty()
            val socksHost = System.getProperty("socksProxyHost")?.trim().orEmpty()
            val httpPort = System.getProperty("http.proxyPort")?.trim().orEmpty()
            val httpsPort = System.getProperty("https.proxyPort")?.trim().orEmpty()

            if (httpHost.isNotEmpty()) {
                return "HTTP Proxy ($httpHost:${httpPort.ifEmpty { "80" }})"
            }
            if (httpsHost.isNotEmpty()) {
                return "HTTPS Proxy ($httpsHost:${httpsPort.ifEmpty { "443" }})"
            }
            if (socksHost.isNotEmpty()) {
                return "SOCKS Proxy ($socksHost)"
            }

            if (context != null) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val proxyInfo = cm?.defaultProxy
                if (proxyInfo != null && !proxyInfo.host.isNullOrBlank()) {
                    return "Active Proxy (${proxyInfo.host}:${proxyInfo.port})"
                }
                val activeNetwork = cm?.activeNetwork
                val linkProxy = if (activeNetwork != null) cm.getLinkProperties(activeNetwork)?.httpProxy else null
                if (linkProxy != null && !linkProxy.host.isNullOrBlank()) {
                    return "Active Capture Proxy (${linkProxy.host}:${linkProxy.port})"
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * Checks whether the active network specifically routing THIS app is a VPN tunnel (`TRANSPORT_VPN`)
     */
    fun isVpnConnectionActiveForApp(context: Context): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Throwable) {}
        return false
    }

    /**
     * Checks if an active VPN tunnel (`TRANSPORT_VPN` or `tun0`/`ppp0` interface) is active
     */
    fun isVpnConnectionActive(context: Context): Boolean {
        try {
            if (isVpnConnectionActiveForApp(context)) {
                return true
            }

            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            for (intf in Collections.list(interfaces)) {
                if (!intf.isUp) continue
                val name = intf.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("pptp") || name.startsWith("tap")) {
                    return true
                }
            }
        } catch (_: Throwable) {}
        return false
    }

    /**
     * Checks if a dynamic debugger or ptrace tracer is attached to the process
     */
    fun isDebuggerAttached(context: Context): Boolean {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return true
            }
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (!isDebuggable && checkTracerPid()) {
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun checkTracerPid(): Boolean {
        try {
            val file = File("/proc/self/status")
            if (!file.exists()) return false
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("TracerPid:", ignoreCase = true) == true) {
                        val pid = line?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                        return pid > 0
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun detectHookingOrTamperDetail(): String? {
        for (className in SUSPICIOUS_HOOK_CLASSES) {
            try {
                Class.forName(className)
                return "Loaded Hook Class: $className"
            } catch (_: ClassNotFoundException) {
                // Normal
            } catch (_: Exception) {}
        }

        for (path in SUSPICIOUS_HOOK_FILES) {
            try {
                if (File(path).exists()) return "Hook Binary: $path"
            } catch (_: Exception) {}
        }

        // Inspect /proc/self/maps for injected Frida / Xposed / SSL-Unpinning / Hook libraries
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val lower = line?.lowercase() ?: continue
                        when {
                            lower.contains("frida-agent") || lower.contains("frida-gadget") -> return "Frida Memory Injection"
                            lower.contains("libapkhook") || lower.contains("mt.bin") -> return "APK Hook Injection"
                            lower.contains("libsandhook") || lower.contains("libxposed") -> return "Xposed/SandHook Memory Injection"
                            lower.contains("sslunpinning") || lower.contains("justtrustme") -> return "SSL-Unpinning Hook"
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * ProxySelector that forces DIRECT connections and refuses any HTTP/SOCKS proxy
     * so Reqable, Charles, Burp, Fiddler, and Wi-Fi proxies are completely bypassed.
     */
    private val noProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = listOf(Proxy.NO_PROXY)
        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }

    /**
     * Pre-request Application Interceptor that blocks outbound requests if a network sniffer,
     * MITM proxy, or hooking framework is active.
     */
    private val preRequestSecurityInterceptor = Interceptor { chain ->
        if (BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE || BuildConfig.ENABLE_ANTI_TAMPER || BuildConfig.BLOCK_VPN_CONNECTION) {
            val violation = inspectSecurityViolation(appContextRef)
            if (violation != null) {
                throw IOException(violation.titleBn)
            }
        }
        try {
            chain.proceed(chain.request())
        } catch (e: SSLPeerUnverifiedException) {
            if (BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE && activeMitmCaptureDetail == null) {
                activeMitmCaptureDetail = "Active SSL/TLS MITM Capture"
            }
            throw e
        } catch (e: SSLHandshakeException) {
            val ctx = appContextRef
            if (BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE && ctx != null && isVpnConnectionActiveForApp(ctx)) {
                val msg = e.message?.lowercase().orEmpty()
                if (msg.contains("certpathvalidatorexception") || msg.contains("trust anchor") || msg.contains("untrusted")) {
                    activeMitmCaptureDetail = "Active VPN SSL/TLS Interception (Reqable / Sniffer CA)"
                }
            }
            throw e
        }
    }

    /**
     * TLS Handshake Network Interceptor:
     * Runs right after the TLS handshake and BEFORE writing any HTTP request headers/tokens/body to the socket.
     * Even on a rooted phone where a user installed Reqable/HttpCanary/Mitmproxy CA into System CAs via Magisk,
     * this inspects the peer certificate's Issuer & Subject DN and aborts immediately if it matches any MITM tool!
     */
    private val tlsMitmCertificateGuardInterceptor = Interceptor { chain ->
        if (BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE) {
            val handshake = chain.connection()?.handshake()
            if (handshake != null) {
                for (cert in handshake.peerCertificates) {
                    if (cert is X509Certificate) {
                        val issuer = cert.issuerX500Principal?.name?.lowercase().orEmpty()
                        val subject = cert.subjectX500Principal?.name?.lowercase().orEmpty()
                        for (keyword in MITM_CERT_KEYWORDS) {
                            if (issuer.contains(keyword) || subject.contains(keyword)) {
                                activeMitmCaptureDetail = "Active TLS MITM Certificate ($keyword)"
                                throw SSLPeerUnverifiedException(
                                    "Blocked untrusted MITM/Sniffer TLS Certificate ($keyword)"
                                )
                            }
                        }
                    }
                }
            }
        }
        chain.proceed(chain.request())
    }

    /**
     * Attaches multi-layer Anti-Network-Capture (No-Proxy + Sniffer Detector + TLS MITM Certificate Guard)
     * to any OkHttpClient.Builder in the application.
     */
    fun applyToOkHttpBuilder(builder: OkHttpClient.Builder, context: Context? = null): OkHttpClient.Builder {
        if (context != null) {
            appContextRef = context.applicationContext
        }
        if (BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE) {
            builder.proxy(Proxy.NO_PROXY)
            builder.proxySelector(noProxySelector)
        }
        if (!builder.interceptors().contains(preRequestSecurityInterceptor)) {
            builder.addInterceptor(preRequestSecurityInterceptor)
        }
        if (!builder.networkInterceptors().contains(tlsMitmCertificateGuardInterceptor)) {
            builder.addNetworkInterceptor(tlsMitmCertificateGuardInterceptor)
        }
        return builder
    }

    fun exitIfTampered(context: Context) {
        try {
            init(context)
        } catch (_: Throwable) {}
    }
}

fun OkHttpClient.Builder.applyAntiCaptureSecurity(context: Context? = null): OkHttpClient.Builder {
    return AntiTamperSecurity.applyToOkHttpBuilder(this, context)
}
