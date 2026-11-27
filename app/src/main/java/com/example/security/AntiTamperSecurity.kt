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
import com.example.utils.AppLang
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
        "io.github.tehcneko.sslunpinning.Main",
        "bin.mt.signature.KillerApplication",
        "cc.binmt.signature.PmsHookApplication",
        "bin.mt.apksignaturekillerplus.HookApplication",
        "np.manager.HookApplication"
    )

    private val VIRTUAL_CONTAINER_MARKERS = listOf(
        "com.lbe.parallel",
        "parallel.intl",
        "dualspace",
        "virtualxposed",
        "io.va.exposed",
        "dkplugin",
        "sandvxposed",
        "multi.parallel",
        "ludashi.dualspace",
        "excelliance.multiaccounts",
        "cloneapp.parallelspace"
    )

    /**
     * Reconstructs expected official package name ("com.maxbird.ff.app") from ASCII codes
     * so MT Manager's automatic string-replace APK cloner cannot rewrite it.
     */
    private fun decodeExpectedPackageName(): String {
        val codes = intArrayOf(
            99, 111, 109, 46,
            109, 97, 120, 98, 105, 114, 100, 46,
            102, 102, 46,
            97, 112, 112
        )
        return String(CharArray(codes.size) { codes[it].toChar() })
    }

    /**
     * Reconstructs expected Application class ("com.example.ShikhoApp") from ASCII codes
     * to detect MT Manager / NP Manager Signature-Killer Application wrappers.
     */
    private fun decodeExpectedAppClassName(): String {
        val codes = intArrayOf(
            99, 111, 109, 46,
            101, 120, 97, 109, 112, 108, 101, 46,
            83, 104, 105, 107, 104, 111, 65, 112, 112
        )
        return String(CharArray(codes.size) { codes[it].toChar() })
    }

    /**
     * Decodes the XOR-obfuscated release signing certificate SHA-256 pinned at build time.
     */
    private fun decodeExpectedReleaseCertSha256(): String {
        return try {
            val obf = BuildConfig.EXPECTED_RELEASE_CERT_OBF.trim()
            if (obf.length != 128) return ""
            val bytes = ByteArray(64) { idx ->
                val hexPair = obf.substring(idx * 2, idx * 2 + 2)
                (hexPair.toInt(16) xor 0x5A).toByte()
            }
            String(bytes, Charsets.UTF_8).lowercase()
        } catch (_: Throwable) {
            ""
        }
    }

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
                    titleBn = AppLang.tr(
                        "Network Capture / Proxy Active!",
                        "অ্যাপের ওপর নেটওয়ার্ক ক্যাপচার / প্রক্সি চালু রয়েছে!"
                    ),
                    messageBn = AppLang.tr(
                        "For data security, network capture or proxy tools (Reqable, HttpCanary, etc.) cannot run on this app. Please disable capture and tap Recheck.",
                        "অ্যাপের ডাটা সুরক্ষার স্বার্থে Reqable, HttpCanary বা অন্য কোনো Network Capture / Proxy চালু রেখে ব্যবহার করা যাবে না। ক্যাপচার বন্ধ করে পুনরায় যাচাই করুন।"
                    ),
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
                        titleBn = AppLang.tr(
                            "Traffic Capture Detected!",
                            "অ্যাপের নেটওয়ার্ক ট্রাফিক ক্যাপচার শনাক্ত হয়েছে!"
                        ),
                        messageBn = AppLang.tr(
                            "Network interception was detected on this app. Please stop traffic capture or VPN interception and tap Recheck.",
                            "এই অ্যাপের নেটওয়ার্ক রিকোয়েস্ট ইন্টারসেপ্ট করার চেষ্টা শনাক্ত হয়েছে। ক্যাপচার বা VPN ইন্টারসেপশন বন্ধ করে পুনরায় যাচাই করুন।"
                        ),
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
                    titleBn = AppLang.tr("VPN Connection Detected!", "VPN কানেকশন শনাক্ত হয়েছে!"),
                    messageBn = AppLang.tr(
                        "For security, this app cannot be used while a VPN or tunnel service is active. Please disconnect VPN and try again.",
                        "নিরাপত্তার স্বার্থে VPN বা টানেল সার্ভিস চালু রেখে এই অ্যাপ ব্যবহার করা যাবে না। VPN বন্ধ করে আবার চেষ্টা করুন।"
                    ),
                    detectedDetail = "Active VPN Transport / Tunnel Interface"
                )
            }
        }

        // 3. Anti-Clone, Signing Certificate Integrity, Anti-Tamper, Anti-Frida & Debugger Check
        if (BuildConfig.ENABLE_ANTI_TAMPER) {
            // 3A. Cloned / Repackaged Package Name Check (blocks MT Manager Clone APK immediately)
            if (ctx != null) {
                val expectedPkg = decodeExpectedPackageName()
                val actualPkg = ctx.packageName.orEmpty()
                if (actualPkg != expectedPkg) {
                    return SecurityViolation(
                        code = "CLONED_APP_DETECTED",
                        titleBn = AppLang.tr(
                            "Cloned / Modified App Detected!",
                            "ক্লোন বা পরিবর্তিত (Mod) অ্যাপ শনাক্ত হয়েছে!"
                        ),
                        messageBn = AppLang.tr(
                            "This APK has been cloned or repackaged with an unauthorized package name. Please uninstall it and install the official MaxBird app.",
                            "এই অ্যাপটি ক্লোন বা পরিবর্তন (Repackage) করা হয়েছে। নিরাপত্তার কারণে ক্লোন/মড অ্যাপ চলবে না — অনুগ্রহ করে অফিশিয়াল MaxBird অ্যাপ ইনস্টল করুন।"
                        ),
                        detectedDetail = "Unauthorized Package: $actualPkg"
                    )
                }

                // 3B. Virtual Space / Dual-App Cloner Container Check
                val filesPath = runCatching { ctx.filesDir?.absolutePath?.lowercase().orEmpty() }.getOrDefault("")
                val matchedVirtual = VIRTUAL_CONTAINER_MARKERS.firstOrNull { filesPath.contains(it) }
                val unexpectedDataDir = filesPath.startsWith("/data/") && !filesPath.contains("/$expectedPkg/")
                if (matchedVirtual != null || unexpectedDataDir) {
                    return SecurityViolation(
                        code = "VIRTUAL_CLONE_DETECTED",
                        titleBn = AppLang.tr(
                            "Virtual / Cloned Environment Detected!",
                            "ভার্চুয়াল বা ক্লোন স্পেস শনাক্ত হয়েছে!"
                        ),
                        messageBn = AppLang.tr(
                            "Running MaxBird inside a cloner or virtual space app is not permitted. Please open the official app directly.",
                            "ক্লোনার বা ভার্চুয়াল স্পেসের ভেতর MaxBird চালানো অনুমোদিত নয়। অনুগ্রহ করে সরাসরি অফিশিয়াল অ্যাপটি ব্যবহার করুন।"
                        ),
                        detectedDetail = "Virtual Container (${matchedVirtual ?: "Sandboxed Clone"})"
                    )
                }

                // 3C. Release-Only: Application Wrapper Check (blocks MT Manager Signature Killer PMS Hook)
                if (!BuildConfig.DEBUG) {
                    val appClassName = ctx.applicationContext?.javaClass?.name.orEmpty()
                    val expectedAppClass = decodeExpectedAppClassName()
                    if (appClassName.isNotBlank() && appClassName != expectedAppClass) {
                        return SecurityViolation(
                            code = "SIGNATURE_KILLER_DETECTED",
                            titleBn = AppLang.tr(
                                "Modified APK Detected!",
                                "মডিফায়েড (Modded) APK শনাক্ত হয়েছে!"
                            ),
                            messageBn = AppLang.tr(
                                "Unauthorized APK patching or signature bypass was detected. Please uninstall this APK and install the official MaxBird app.",
                                "অ্যাপের ভেতরে অননুমোদিত প্যাচ বা সিগনেচার বাইপাস শনাক্ত হয়েছে। অনুগ্রহ করে এই অ্যাপটি আনইনস্টল করে অফিশিয়াল MaxBird অ্যাপ ব্যবহার করুন।"
                            ),
                            detectedDetail = "Tampered Application Wrapper ($appClassName)"
                        )
                    }

                    // 3D. Release-Only: Signing Certificate SHA-256 Verification
                    val expectedCert = decodeExpectedReleaseCertSha256()
                    if (expectedCert.length == 64) {
                        val actualCerts = DeviceSecurityManager.getAllAppSignaturesSha256(ctx)
                        val signatureValid = actualCerts.isNotEmpty() &&
                            actualCerts.all { it.equals(expectedCert, ignoreCase = true) }
                        if (!signatureValid) {
                            return SecurityViolation(
                                code = "INVALID_APP_SIGNATURE",
                                titleBn = AppLang.tr(
                                    "Unauthorized / Modded APK!",
                                    "অননুমোদিত বা পরিবর্তিত (Modded) APK!"
                                ),
                                messageBn = AppLang.tr(
                                    "This APK's signing certificate does not match the official MaxBird release signature. Please uninstall it and download the official APK.",
                                    "এই APK-এর ডিজিটাল সিগনেচার অফিশিয়াল MaxBird রিলিজ সিগনেচারের সাথে মেলেনি। অনুগ্রহ করে এটি আনইনস্টল করে অফিশিয়াল অ্যাপ ইনস্টল করুন।"
                                ),
                                detectedDetail = "Invalid Signing Certificate (Repackaged / Cloned APK)"
                            )
                        }
                    }
                }
            }

            if (ctx != null && isDebuggerAttached(ctx)) {
                return SecurityViolation(
                    code = "DEBUGGER_DETECTED",
                    titleBn = AppLang.tr("Debugger Detected!", "ডিবাগার / ট্রেসার শনাক্ত হয়েছে!"),
                    messageBn = AppLang.tr(
                        "For security, this app cannot run with a debugger or reverse-engineering tool attached.",
                        "নিরাপত্তার কারণে কোনো ডিবাগার বা রিভার্স ইঞ্জিনিয়ারিং টুল সংযুক্ত অবস্থায় অ্যাপটি চলবে না।"
                    ),
                    detectedDetail = "Active Debugger / TracerPid"
                )
            }

            val hookDetail = detectHookingOrTamperDetail()
            if (hookDetail != null) {
                return SecurityViolation(
                    code = "HOOK_TAMPER_DETECTED",
                    titleBn = AppLang.tr("Unauthorized Hook Detected!", "অননুমোদিত হুক / মডিফিকেশন শনাক্ত হয়েছে!"),
                    messageBn = AppLang.tr(
                        "Frida, Xposed, LSPosed, or an SSL-Unpinning hook was detected on this device. Please disable it and restart the app.",
                        "আপনার ডিভাইসে Frida, Xposed, LSPosed বা SSL-Unpinning হুক শনাক্ত হয়েছে। এগুলো বন্ধ করে অ্যাপটি পুনরায় চালু করুন।"
                    ),
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
