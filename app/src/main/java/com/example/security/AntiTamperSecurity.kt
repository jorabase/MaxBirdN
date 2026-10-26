package com.example.security

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.cert.X509Certificate
import java.util.Collections
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

    private val SUSPICIOUS_HOOK_FILES = listOf(
        "/data/local/tmp/frida",
        "/data/local/tmp/frida-server",
        "/data/local/tmp/re.frida.server",
        "/system/framework/XposedBridge.jar",
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so"
    )

    /**
     * Known Network Capture / Packet Sniffer / MITM Proxy applications on Android
     */
    private val NETWORK_CAPTURE_PACKAGES = mapOf(
        "com.reqable.android" to "Reqable Network Capture",
        "com.reqable.app" to "Reqable Packet Sniffer",
        "com.guoshi.httpcanary" to "HttpCanary Packet Capture",
        "com.guoshi.httpcanary.premium" to "HttpCanary Premium",
        "com.achep.httpcanary" to "HttpCanary Sniffer",
        "tech.httptoolkit.android.v1" to "HTTP Toolkit Interceptor",
        "tech.httptoolkit.android" to "HTTP Toolkit",
        "com.emanuelef.remote_capture" to "PCAPdroid Network Monitor",
        "com.emanuelef.remote_capture.debug" to "PCAPdroid Debug",
        "app.greyshirts.sslcapture" to "Packet Capture (SSL Capture)",
        "jp.co.taosoftware.android.packetcapture" to "Packet Capture",
        "com.minhui.networkcapture" to "NetCapture",
        "com.minhui.networkcapture.pro" to "NetCapture Pro",
        "com.evbadroid.proxymon" to "ProxyMon",
        "org.sandrob.drony" to "Drony Proxy Capture",
        "org.proxydroid" to "ProxyDroid",
        "com.xk72.charles" to "Charles Proxy",
        "io.proxyman.android" to "Proxyman Capture",
        "com.egorovandreyrm.pcapremote" to "PCAP Remote"
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
     * Returns null if safe, or a [SecurityViolation] describing the threat in Bengali.
     */
    fun inspectSecurityViolation(context: Context? = appContextRef): SecurityViolation? {
        val ctx = context?.applicationContext ?: appContextRef

        // 1. Anti-Network Capture Check (Reqable / HttpCanary / Proxy / Packet Sniffers)
        if (BuildConfig.ENABLE_ANTI_NETWORK_CAPTURE) {
            // 1A. Check System / Wi-Fi HTTP, HTTPS, or SOCKS Proxy
            val proxyDetail = detectActiveSystemProxy(ctx)
            if (proxyDetail != null) {
                return SecurityViolation(
                    code = "NETWORK_PROXY_DETECTED",
                    titleBn = "নেটওয়ার্ক প্রক্সি / ক্যাপচার শনাক্ত হয়েছে!",
                    messageBn = "অ্যাপের ডাটা সুরক্ষার স্বার্থে কোনো প্রকার Proxy, Reqable বা Network Interceptor চালু রেখে এই অ্যাপ ব্যবহার করা যাবে না। অনুগ্রহ করে Wi-Fi/System Proxy বা ক্যাপচার অ্যাপ বন্ধ করে পুনরায় চেষ্টা করুন।",
                    detectedDetail = proxyDetail
                )
            }

            // 1B. Check Installed Network Sniffer / MITM Apps (Reqable, HttpCanary, HTTP Toolkit, PCAPdroid, etc.)
            if (ctx != null) {
                val snifferApp = detectInstalledNetworkSnifferApp(ctx)
                if (snifferApp != null) {
                    return SecurityViolation(
                        code = "SNIFFER_APP_DETECTED",
                        titleBn = "নেটওয়ার্ক ক্যাপচার অ্যাপ শনাক্ত হয়েছে!",
                        messageBn = "আপনার ডিভাইসে নেটওয়ার্ক ক্যাপচার/স্নিফার অ্যাপ ($snifferApp) শনাক্ত হয়েছে। অ্যাপের সিকিউরিটি নীতিমালার কারণে Reqable, HttpCanary বা সমজাতীয় ক্যাপচার টুল ফোনে থাকা অবস্থায় অ্যাপটি চালানো যাবে না। অনুগ্রহ করে ওই অ্যাপটি আনইনস্টল করে পুনরায় প্রবেশ করুন।",
                        detectedDetail = snifferApp
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
     * Checks if a system HTTP/HTTPS/SOCKS proxy is configured on the JVM or Android ConnectivityManager
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
                    return "System Proxy (${proxyInfo.host}:${proxyInfo.port})"
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * Checks if Reqable, HttpCanary, PCAPdroid, HTTP Toolkit, Packet Capture, etc. are installed
     */
    fun detectInstalledNetworkSnifferApp(context: Context): String? {
        val pm = context.packageManager
        for ((pkg, label) in NETWORK_CAPTURE_PACKAGES) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                return "$label ($pkg)"
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * Checks if an active VPN tunnel (`TRANSPORT_VPN` or `tun0`/`ppp0` interface) is active
     */
    fun isVpnConnectionActive(context: Context): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
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
        chain.proceed(chain.request())
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
