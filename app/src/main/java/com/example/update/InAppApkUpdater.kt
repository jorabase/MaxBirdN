package com.example.update

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.security.AntiTamperSecurity
import com.example.utils.AppLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class ApkUpdateDownloadState {
    object Idle : ApkUpdateDownloadState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : ApkUpdateDownloadState()

    object Verifying : ApkUpdateDownloadState()
    data class PermissionRequired(val apkFile: File) : ApkUpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : ApkUpdateDownloadState()
    data class Installing(val apkFile: File) : ApkUpdateDownloadState()
    data class Error(val messageBn: String, val fallbackUrl: String) : ApkUpdateDownloadState()
}

/**
 * BroadcastReceiver triggered automatically by the Android OS (`ACTION_MY_PACKAGE_REPLACED`)
 * the exact moment an app update finishes installing.
 * Immediately purges all downloaded APK files so 0 bytes of APK cache remain after updating.
 */
class UpdatePackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("UpdateReceiver", "✅ App updated (MY_PACKAGE_REPLACED). Purging downloaded APK cache...")
            InAppApkUpdater.clearAllDownloadedApks(context.applicationContext)
            InAppApkUpdater.recordInstalledVersionAndPurgeOnUpgrade(context.applicationContext)
        }
    }
}

/**
 * Production-grade In-App APK Downloader, Self-Installer & Automatic Post-Update Cache Cleaner:
 * - Resolves direct APK URLs, Google Drive links (including large-file virus-scan bypass with cookies & tokens),
 *   GitHub Releases, Supabase Storage, MediaFire, OneDrive, and Dropbox links.
 * - Streams the APK directly inside the app with real-time progress (%), MB downloaded, and speed (MB/s).
 * - Strictly validates APK archive integrity (ZIP header + End-Of-Central-Directory footer + PackageManager archive info + optional SHA-256).
 * - Automatically deletes downloaded APK files as soon as the app updates or when the installed version matches the APK.
 */
object InAppApkUpdater {
    private const val TAG = "InAppApkUpdater"
    private const val PREFS_NAME = "in_app_updater_prefs"
    private const val KEY_LAST_VERSION_CODE = "last_installed_version_code"
    private const val KEY_LAST_VERSION_NAME = "last_installed_version_name"
    private const val KEY_LAST_DOWNLOAD_KEY = "last_download_target_key"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeDownloadJob: Job? = null
    @Volatile
    private var activeTargetKey: String = ""

    private val _downloadState = MutableStateFlow<ApkUpdateDownloadState>(ApkUpdateDownloadState.Idle)
    val downloadState: StateFlow<ApkUpdateDownloadState> = _downloadState.asStateFlow()

    // In-memory cookie store so Google Drive / CDN multi-hop confirmation redirects preserve session cookies
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { AntiTamperSecurity.applyToOkHttpBuilder(this) }
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
                    synchronized(hostCookies) {
                        cookies.forEach { newCookie ->
                            hostCookies.removeAll { it.name == newCookie.name }
                            hostCookies.add(newCookie)
                        }
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val result = mutableListOf<Cookie>()
                    cookieStore.forEach { (host, list) ->
                        if (url.host == host || url.host.endsWith(".$host") || host.endsWith(".${url.host}")) {
                            synchronized(list) {
                                result.addAll(list)
                            }
                        }
                    }
                    return result
                }
            })
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Throwable) {
                true
            }
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val activity = findActivity(context)
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    if (activity == null) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (activity != null) {
                    activity.startActivity(intent)
                } else {
                    context.startActivity(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to open unknown app sources settings: ${e.message}")
            }
        }
    }

    fun resetState() {
        activeDownloadJob?.cancel()
        _downloadState.value = ApkUpdateDownloadState.Idle
    }

    /**
     * Synchronizes the updater state when `UpdateDialog` opens for a specific `AppUpdateInfo`.
     * If the target version/URL changed or if a previously referenced APK file was deleted/invalid,
     * resets state to `Idle` so the user can immediately download.
     */
    fun syncDialogForTarget(context: Context, updateInfo: AppUpdateInfo) {
        val targetKey = buildTargetKey(updateInfo)
        val currentState = _downloadState.value
        if (currentState is ApkUpdateDownloadState.Downloading) return

        if (activeTargetKey.isNotBlank() && activeTargetKey != targetKey) {
            _downloadState.value = ApkUpdateDownloadState.Idle
            activeTargetKey = targetKey
            return
        }
        activeTargetKey = targetKey

        when (currentState) {
            is ApkUpdateDownloadState.ReadyToInstall -> {
                if (!isValidApkArchive(context, currentState.apkFile, updateInfo.versionCode)) {
                    try { currentState.apkFile.delete() } catch (_: Throwable) {}
                    _downloadState.value = ApkUpdateDownloadState.Idle
                }
            }
            is ApkUpdateDownloadState.Installing -> {
                if (!isValidApkArchive(context, currentState.apkFile, updateInfo.versionCode)) {
                    try { currentState.apkFile.delete() } catch (_: Throwable) {}
                    _downloadState.value = ApkUpdateDownloadState.Idle
                } else {
                    _downloadState.value = ApkUpdateDownloadState.ReadyToInstall(currentState.apkFile)
                }
            }
            is ApkUpdateDownloadState.PermissionRequired -> {
                if (!currentState.apkFile.exists()) {
                    _downloadState.value = ApkUpdateDownloadState.Idle
                }
            }
            else -> {}
        }
    }

    /**
     * Automatically detects if the app was just updated to a new version (or downgraded)
     * and purges all downloaded `.apk` files and stale update caches immediately.
     * Also deletes any cached `.apk` whose version matches or is older than the currently running app!
     */
    fun cleanupInstalledOrStaleApks(context: Context) {
        try {
            val appCtx = context.applicationContext
            recordInstalledVersionAndPurgeOnUpgrade(appCtx)

            // If we are not actively downloading right now, inspect all apk_updates folders
            if (_downloadState.value is ApkUpdateDownloadState.Downloading) return

            val dirs = getAllCandidateUpdateDirectories(appCtx)
            for (dir in dirs) {
                if (!dir.exists()) continue
                dir.listFiles()?.forEach { file ->
                    try {
                        val name = file.name.lowercase(Locale.US)
                        // Always delete incomplete .part or .tmp files when not downloading
                        if (name.endsWith(".part") || name.endsWith(".tmp")) {
                            file.delete()
                            return@forEach
                        }
                        if (name.endsWith(".apk")) {
                            val archiveInfo = getApkArchiveInfo(appCtx, file)
                            if (archiveInfo == null) {
                                // Corrupted / invalid APK -> delete immediately
                                file.delete()
                            } else {
                                val apkVerCode = getArchiveVersionCode(archiveInfo)
                                val apkVerName = (archiveInfo.versionName ?: "").trim().removePrefix("v").removePrefix("V")
                                val currentVerName = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")

                                // If this APK is already installed (matches current versionCode or versionName), delete it!
                                if (apkVerCode == BuildConfig.VERSION_CODE ||
                                    (apkVerName.isNotBlank() && apkVerName.equals(currentVerName, ignoreCase = true))
                                ) {
                                    Log.i(TAG, "🧹 Deleting already-installed APK from cache: ${file.name} (v$apkVerName / $apkVerCode)")
                                    file.delete()
                                    if (_downloadState.value !is ApkUpdateDownloadState.Downloading) {
                                        _downloadState.value = ApkUpdateDownloadState.Idle
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error during cleanupInstalledOrStaleApks: ${e.message}")
        }
    }

    /**
     * Checks SharedPreferences to see if `BuildConfig.VERSION_CODE` or `BuildConfig.VERSION_NAME` changed
     * since last launch. If it changed (meaning an app update succeeded!), wipes all APK caches immediately.
     */
    fun recordInstalledVersionAndPurgeOnUpgrade(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val prevCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
            val prevName = prefs.getString(KEY_LAST_VERSION_NAME, "") ?: ""

            val currentCode = BuildConfig.VERSION_CODE
            val currentName = BuildConfig.VERSION_NAME

            if (prevCode != currentCode || prevName != currentName) {
                Log.i(TAG, "🚀 App version transition detected ($prevName [$prevCode] -> $currentName [$currentCode]). Purging all APK update caches!")
                clearAllDownloadedApks(context)
                prefs.edit()
                    .putInt(KEY_LAST_VERSION_CODE, currentCode)
                    .putString(KEY_LAST_VERSION_NAME, currentName)
                    .remove(KEY_LAST_DOWNLOAD_KEY)
                    .apply()
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Completely deletes all downloaded `.apk` and `.part` files across all update directories.
     * Called automatically when:
     * 1. App update completes (`MY_PACKAGE_REPLACED` or version change on startup)
     * 2. Database reports no update is required (`shouldBlockApp == false`)
     * 3. User clears cache from Settings
     * 4. User forces a fresh re-download
     */
    fun clearAllDownloadedApks(context: Context) {
        try {
            val appCtx = context.applicationContext
            val dirs = getAllCandidateUpdateDirectories(appCtx)
            var deletedCount = 0
            var freedBytes = 0L

            for (dir in dirs) {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        try {
                            val len = file.length()
                            if (file.deleteRecursively()) {
                                deletedCount++
                                freedBytes += len
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            }

            if (_downloadState.value !is ApkUpdateDownloadState.Downloading) {
                _downloadState.value = ApkUpdateDownloadState.Idle
            }

            if (deletedCount > 0) {
                Log.i(TAG, "✅ Cleared $deletedCount APK cache file(s), freed ${formatMegabytes(freedBytes)}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to clear downloaded APKs: ${e.message}")
        }
    }

    /**
     * Starts downloading the APK inside the app and automatically triggers installation upon completion.
     * Pass `forceRedownload = true` to delete any cached APK and download fresh from the server.
     */
    fun startDownloadAndInstall(
        context: Context,
        updateInfo: AppUpdateInfo,
        forceRedownload: Boolean = false
    ) {
        val appContext = context.applicationContext
        val targetKey = buildTargetKey(updateInfo)
        activeTargetKey = targetKey

        val currentState = _downloadState.value
        if (currentState is ApkUpdateDownloadState.Downloading && !forceRedownload) {
            return
        }

        if (forceRedownload) {
            activeDownloadJob?.cancel()
            clearAllDownloadedApks(appContext)
            cookieStore.clear()
        } else {
            if (currentState is ApkUpdateDownloadState.PermissionRequired &&
                isValidApkArchive(appContext, currentState.apkFile, updateInfo.versionCode)
            ) {
                triggerInstallOrRequestPermission(context, currentState.apkFile)
                return
            }
            if (currentState is ApkUpdateDownloadState.ReadyToInstall &&
                isValidApkArchive(appContext, currentState.apkFile, updateInfo.versionCode)
            ) {
                triggerInstallOrRequestPermission(context, currentState.apkFile)
                return
            }
        }

        val rawUrl = updateInfo.downloadUrl.trim()
        if (rawUrl.isBlank() || rawUrl.equals("https://t.me/", ignoreCase = true)) {
            _downloadState.value = ApkUpdateDownloadState.Error(
                messageBn = AppLang.tr(
                    "No valid APK download link found. Please configure a direct APK link from the admin panel.",
                    "ডাটাবেজে বৈধ APK ডাউনলোড লিংক দেওয়া হয়নি। অ্যাডমিন প্যানেল থেকে সরাসরি APK ডাউনলোড লিংক যুক্ত করুন।"
                ),
                fallbackUrl = rawUrl
            )
            return
        }
        if (!isHttpsUrl(rawUrl)) {
            _downloadState.value = ApkUpdateDownloadState.Error(
                messageBn = AppLang.tr(
                    "Security alert: Only secure HTTPS download links are allowed.",
                    "নিরাপত্তা সতর্কতা: শুধুমাত্র নিরাপদ HTTPS ডাউনলোড লিংক অনুমোদিত।"
                ),
                fallbackUrl = ""
            )
            return
        }

        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            try {
                val updatesDir = getUpdatesDirectory(appContext)
                val urlHash = shortHash(rawUrl)
                val safeVerName = updateInfo.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetApkFile = File(updatesDir, "MaxBird_v${updateInfo.versionCode}_${safeVerName}_$urlHash.apk")

                // Only reuse cached APK if not forcing redownload AND it passes strict archive + version + EOF validation
                if (!forceRedownload &&
                    targetApkFile.exists() &&
                    isValidApkArchive(appContext, targetApkFile, updateInfo.versionCode) &&
                    verifySha256IfRequired(targetApkFile, updateInfo.signatureHash)
                ) {
                    Log.i(TAG, "Reusing verified APK file: ${targetApkFile.name} (${formatMegabytes(targetApkFile.length())})")
                    withContext(Dispatchers.Main) {
                        triggerInstallOrRequestPermission(context, targetApkFile)
                    }
                    return@launch
                }

                // Wipe all older/other files in apk_updates before starting fresh download
                cleanUpdatesDirectory(updatesDir, keepFileName = null)

                _downloadState.value = ApkUpdateDownloadState.Downloading(
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    speedBytesPerSec = 0L
                )

                val resolvedUrl = normalizeDirectDownloadUrl(rawUrl)
                if (!isHttpsUrl(resolvedUrl)) {
                    _downloadState.value = ApkUpdateDownloadState.Error(
                        messageBn = AppLang.tr(
                            "Security alert: Insecure (HTTP) download link blocked.",
                            "নিরাপত্তা সতর্কতা: অনিরাপদ (HTTP) ডাউনলোড লিংক ব্লক করা হয়েছে।"
                        ),
                        fallbackUrl = ""
                    )
                    return@launch
                }
                val tempFile = File(updatesDir, "downloading_${updateInfo.versionCode}_$urlHash.apk.part")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val downloadSuccess = streamUrlToFileWithRetry(resolvedUrl, tempFile, rawUrl)
                if (!downloadSuccess) {
                    try { tempFile.delete() } catch (_: Throwable) {}
                    return@launch
                }

                _downloadState.value = ApkUpdateDownloadState.Verifying

                if (!isValidApkArchive(appContext, tempFile, expectedVersionCode = null)) {
                    tempFile.delete()
                    _downloadState.value = ApkUpdateDownloadState.Error(
                        messageBn = AppLang.tr(
                            "Downloaded file is not a valid APK for this app (package name or archive structure mismatch).",
                            "ডাউনলোডকৃত ফাইলটি এই অ্যাপের বৈধ APK নয় (প্যাকেজ নাম বা ফাইল গঠন মেলেনি)।"
                        ),
                        fallbackUrl = rawUrl
                    )
                    return@launch
                }

                if (!verifyApkSigningCertificateMatchesInstalledApp(appContext, tempFile)) {
                    tempFile.delete()
                    _downloadState.value = ApkUpdateDownloadState.Error(
                        messageBn = AppLang.tr(
                            "Security alert: Downloaded APK signature does not match the installed app! File may be unauthorized or tampered.",
                            "নিরাপত্তা সতর্কতা: ডাউনলোডকৃত APK-এর ডিজিটাল সিগনেচার আসল অ্যাপের সাথে মেলেনি! এটি অননুমোদিত বা পরিবর্তিত ফাইল হতে পারে।"
                        ),
                        fallbackUrl = ""
                    )
                    return@launch
                }

                if (!verifySha256IfRequired(tempFile, updateInfo.signatureHash)) {
                    tempFile.delete()
                    _downloadState.value = ApkUpdateDownloadState.Error(
                        messageBn = AppLang.tr(
                            "APK security checksum (SHA-256) mismatch. Please download again.",
                            "APK ফাইলের সিকিউরিটি চেকসাম (SHA-256) মেলেনি। পুনরায় ডাউনলোড করুন।"
                        ),
                        fallbackUrl = rawUrl
                    )
                    return@launch
                }

                if (targetApkFile.exists()) {
                    targetApkFile.delete()
                }
                val renamed = tempFile.renameTo(targetApkFile)
                val finalApkFile = if (renamed && targetApkFile.exists()) targetApkFile else tempFile

                // Save target key in prefs
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_DOWNLOAD_KEY, targetKey)
                    .apply()

                withContext(Dispatchers.Main) {
                    triggerInstallOrRequestPermission(context, finalApkFile)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "In-app APK download failed: ${e.message}", e)
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = AppLang.tr(
                        "Download failed: ${e.localizedMessage ?: "Check your network connection and try again"}",
                        "ডাউনলোড ব্যর্থ হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক সংযোগ যাচাই করে আবার চেষ্টা করুন"}"
                    ),
                    fallbackUrl = rawUrl
                )
            }
        }
    }

    fun triggerInstallOrRequestPermission(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() < 50_000L) {
            _downloadState.value = ApkUpdateDownloadState.Idle
            return
        }
        if (!canInstallPackages(context)) {
            _downloadState.value = ApkUpdateDownloadState.PermissionRequired(apkFile)
            return
        }
        _downloadState.value = ApkUpdateDownloadState.Installing(apkFile)
        val launched = launchPackageInstaller(context, apkFile)
        if (launched) {
            _downloadState.value = ApkUpdateDownloadState.ReadyToInstall(apkFile)
        } else {
            _downloadState.value = ApkUpdateDownloadState.Error(
                messageBn = AppLang.tr(
                    "Could not launch automatic installer. Tap the button below to try again.",
                    "অটোমেটিক ইনস্টলার চালু করা যায়নি। নিচের বাটনে ট্যাপ করে পুনরায় চেষ্টা করুন।"
                ),
                fallbackUrl = ""
            )
        }
    }

    fun launchPackageInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val appCtx = context.applicationContext
            val authority = "${appCtx.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(appCtx, authority, apkFile)

            val activity = findActivity(context)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, appCtx.packageName)
            }

            // Explicitly grant URI read permission to all package installer activities on the device
            try {
                val resInfoList = appCtx.packageManager.queryIntentActivities(
                    installIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                for (resolveInfo in resInfoList) {
                    val pkgName = resolveInfo.activityInfo?.packageName ?: continue
                    appCtx.grantUriPermission(
                        pkgName,
                        apkUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (_: Throwable) {
            }

            if (activity != null && !activity.isFinishing) {
                activity.startActivity(installIntent)
            } else {
                appCtx.startActivity(installIntent)
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            false
        }
    }

    private fun streamUrlToFileWithRetry(
        initialUrl: String,
        destinationFile: File,
        fallbackOriginalUrl: String
    ): Boolean {
        var lastErrorMsg = ""
        for (attempt in 1..2) {
            try {
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                val ok = streamUrlToFile(initialUrl, destinationFile, fallbackOriginalUrl)
                if (ok) return true
            } catch (e: Throwable) {
                lastErrorMsg = e.localizedMessage ?: "Connection interrupted"
                Log.w(TAG, "Download attempt $attempt failed: $lastErrorMsg")
            }
        }
        if (_downloadState.value !is ApkUpdateDownloadState.Error && lastErrorMsg.isNotBlank()) {
            _downloadState.value = ApkUpdateDownloadState.Error(
                messageBn = AppLang.tr(
                    "Download connection interrupted: $lastErrorMsg. Please try again.",
                    "ডাউনলোড সংযোগ বিচ্ছিন্ন হয়েছে: $lastErrorMsg। আবার চেষ্টা করুন।"
                ),
                fallbackUrl = fallbackOriginalUrl
            )
        }
        return false
    }

    private fun streamUrlToFile(
        initialUrl: String,
        destinationFile: File,
        fallbackOriginalUrl: String
    ): Boolean {
        val request = Request.Builder()
            .url(initialUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .get()
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.request.url.isHttps) {
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = AppLang.tr(
                        "Security alert: Download redirect cancelled because it was insecure (HTTP).",
                        "নিরাপত্তা সতর্কতা: ডাউনলোড রিডাইরেক্টটি অনিরাপদ (HTTP) হওয়ায় বাতিল করা হয়েছে।"
                    ),
                    fallbackUrl = ""
                )
                return false
            }
            if (!response.isSuccessful) {
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = AppLang.tr(
                        "Could not download APK from server (HTTP ${response.code}). Verify the link is active.",
                        "সার্ভার থেকে APK ফাইল ডাউনলোড করা যায়নি (HTTP ${response.code})। লিংকটি সচল আছে কিনা যাচাই করুন।"
                    ),
                    fallbackUrl = fallbackOriginalUrl
                )
                return false
            }

            val contentType = response.header("Content-Type")?.lowercase(Locale.US).orEmpty()
            val body = response.body ?: return false

            if (contentType.contains("text/html")) {
                val htmlPreview = body.string()
                val confirmedUrl = extractSecondaryDownloadUrl(htmlPreview, initialUrl, fallbackOriginalUrl)
                if (!confirmedUrl.isNullOrBlank() && confirmedUrl != initialUrl) {
                    if (!isHttpsUrl(confirmedUrl)) {
                        _downloadState.value = ApkUpdateDownloadState.Error(
                            messageBn = AppLang.tr(
                                "Security alert: Secondary download link is not HTTPS.",
                                "নিরাপত্তা সতর্কতা: সেকেন্ডারি ডাউনলোড লিংকটি HTTPS নয়।"
                            ),
                            fallbackUrl = ""
                        )
                        return false
                    }
                    return streamConfirmedUrlToFile(confirmedUrl, destinationFile, fallbackOriginalUrl)
                }
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = AppLang.tr(
                        "The link returned a webpage instead of an APK file. Use a direct .apk, Google Drive, or GitHub link.",
                        "প্রদত্ত লিংকটি সরাসরি APK ফাইলের পরিবর্তে একটি ওয়েবপেজ রিটার্ন করেছে। সরাসরি .apk বা Google Drive/GitHub ডিরেক্ট লিংক দিন।"
                    ),
                    fallbackUrl = fallbackOriginalUrl
                )
                return false
            }

            return writeResponseStreamToFile(body.byteStream(), body.contentLength(), destinationFile)
        }
    }

    private fun streamConfirmedUrlToFile(
        confirmedUrl: String,
        destinationFile: File,
        fallbackOriginalUrl: String
    ): Boolean {
        if (!isHttpsUrl(confirmedUrl)) return false
        val request = Request.Builder()
            .url(confirmedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .get()
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.request.url.isHttps) {
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = AppLang.tr(
                        "Security alert: Download redirect cancelled because it was insecure (HTTP).",
                        "নিরাপত্তা সতর্কতা: ডাউনলোড রিডাইরেক্টটি অনিরাপদ (HTTP) হওয়ায় বাতিল করা হয়েছে।"
                    ),
                    fallbackUrl = ""
                )
                return false
            }
            if (!response.isSuccessful) {
                _downloadState.value = ApkUpdateDownloadState.Error(
                    messageBn = AppLang.tr(
                        "Failed to download file from direct server (HTTP ${response.code}).",
                        "ডিরেক্ট সার্ভার থেকে ফাইল ডাউনলোড ব্যর্থ হয়েছে (HTTP ${response.code})।"
                    ),
                    fallbackUrl = fallbackOriginalUrl
                )
                return false
            }
            val body = response.body ?: return false
            return writeResponseStreamToFile(body.byteStream(), body.contentLength(), destinationFile)
        }
    }

    private fun writeResponseStreamToFile(
        inputStream: java.io.InputStream,
        totalBytes: Long,
        destinationFile: File
    ): Boolean {
        val buffer = ByteArray(64 * 1024) // 64KB high-speed stream buffer
        var downloadedBytes = 0L
        val startTimeMs = System.currentTimeMillis()
        var lastEmitTimeMs = 0L

        inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTimeMs >= 100L || (totalBytes > 0L && downloadedBytes >= totalBytes)) {
                        lastEmitTimeMs = now
                        val elapsedSec = ((now - startTimeMs) / 1000.0).coerceAtLeast(0.1)
                        val speedBytesPerSec = (downloadedBytes / elapsedSec).toLong()
                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        _downloadState.value = ApkUpdateDownloadState.Downloading(
                            progressPercent = percent,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes.coerceAtLeast(0L),
                            speedBytesPerSec = speedBytesPerSec
                        )
                    }
                }
                output.flush()
                try {
                    output.fd.sync()
                } catch (_: Throwable) {
                }
            }
        }

        // Verify that if Content-Length was known, we received the full byte count
        if (totalBytes > 0L && downloadedBytes < totalBytes) {
            Log.w(TAG, "Incomplete download: received $downloadedBytes of $totalBytes bytes")
            return false
        }
        return downloadedBytes > 50_000L
    }

    fun normalizeDirectDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()

        // 1. Google Drive /file/d/{id}
        val driveFileIdRegex = Regex("""drive\.google\.com/file/d/([a-zA-Z0-9_-]+)""")
        val driveMatch = driveFileIdRegex.find(trimmed)
        if (driveMatch != null) {
            val fileId = driveMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        // 2. Google Drive open?id={id} or uc?id={id}
        val driveOpenIdRegex = Regex("""(?:drive\.google\.com|drive\.usercontent\.google\.com)/.*[?&]id=([a-zA-Z0-9_-]+)""")
        val driveOpenMatch = driveOpenIdRegex.find(trimmed)
        if (driveOpenMatch != null) {
            val fileId = driveOpenMatch.groupValues[1]
            return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
        }

        // 3. Dropbox links
        if (trimmed.contains("dropbox.com/", ignoreCase = true)) {
            return trimmed
                .replace("?dl=0", "?dl=1")
                .replace("&dl=0", "&dl=1")
                .let { if (!it.contains("dl=1")) "$it${if (it.contains("?")) "&" else "?"}dl=1" else it }
        }

        // 4. GitHub blob -> raw
        if (trimmed.contains("github.com/", ignoreCase = true) && trimmed.contains("/blob/")) {
            return trimmed.replace("/blob/", "/raw/")
        }

        return trimmed
    }

    /**
     * Extracts confirmed direct download link from Google Drive virus-scan warning HTML or MediaFire HTML.
     */
    private fun extractSecondaryDownloadUrl(
        html: String,
        currentUrl: String,
        originalUrl: String
    ): String? {
        // 1. MediaFire direct download button extraction (always upgrade to HTTPS)
        val mediaFireRegex = Regex("""href=["'](https?://download\d+\.mediafire\.com/[^"']+)["']""")
        val mfMatch = mediaFireRegex.find(html)
        if (mfMatch != null) {
            val rawMf = mfMatch.groupValues[1]
            return if (rawMf.startsWith("http://", ignoreCase = true)) {
                "https://" + rawMf.substring(7)
            } else rawMf
        }

        // 2. Google Drive virus-scan form extraction (supports both double and single quotes, plus URL fallback for id)
        val actionRegex = Regex("""action=["']([^"']+)["'][^>]*id=["']download-form["']""")
        val actionAltRegex = Regex("""id=["']download-form["'][^>]*action=["']([^"']+)["']""")
        val idRegex = Regex("""name=["']id["']\s+value=["']([^"']+)["']""")
        val confirmRegex = Regex("""name=["']confirm["']\s+value=["']([^"']+)["']""")
        val uuidRegex = Regex("""name=["']uuid["']\s+value=["']([^"']+)["']""")
        val atRegex = Regex("""name=["']at["']\s+value=["']([^"']+)["']""")

        val urlIdRegex = Regex("""[?&]id=([a-zA-Z0-9_-]+)""")
        val pathIdRegex = Regex("""/file/d/([a-zA-Z0-9_-]+)""")

        val fileId = idRegex.find(html)?.groupValues?.getOrNull(1)
            ?: urlIdRegex.find(currentUrl)?.groupValues?.getOrNull(1)
            ?: pathIdRegex.find(originalUrl)?.groupValues?.getOrNull(1)
            ?: urlIdRegex.find(originalUrl)?.groupValues?.getOrNull(1)

        val confirm = confirmRegex.find(html)?.groupValues?.getOrNull(1) ?: "t"
        val uuid = uuidRegex.find(html)?.groupValues?.getOrNull(1)
        val atToken = atRegex.find(html)?.groupValues?.getOrNull(1)

        if (!fileId.isNullOrBlank()) {
            val base = (actionRegex.find(html)?.groupValues?.getOrNull(1)
                ?: actionAltRegex.find(html)?.groupValues?.getOrNull(1))
                ?.replace("&amp;", "&")
                ?: "https://drive.usercontent.google.com/download"

            val sb = StringBuilder(base)
            sb.append(if (base.contains("?")) "&" else "?")
            sb.append("id=").append(fileId)
            sb.append("&export=download&confirm=").append(confirm)
            if (!uuid.isNullOrBlank()) {
                sb.append("&uuid=").append(uuid)
            }
            if (!atToken.isNullOrBlank()) {
                sb.append("&at=").append(atToken)
            }
            return sb.toString()
        }
        return null
    }

    /**
     * Strictly verifies that `file` is a complete, uncorrupted Android APK archive:
     * 1. Exists and is > 50 KB
     * 2. Starts with ZIP Local File Header magic bytes (`PK\x03\x04`)
     * 3. Contains ZIP End of Central Directory record (`PK\x05\x06`) near the end of the file (proves download did not truncate!)
     * 4. `PackageManager.getPackageArchiveInfo` parses a valid `PackageInfo` with a non-blank `packageName`
     */
    private fun isValidApkArchive(
        context: Context,
        file: File,
        expectedVersionCode: Int? = null
    ): Boolean {
        if (!file.exists() || file.length() < 50_000L) return false
        return try {
            // 1. Check ZIP header (PK\x03\x04)
            val header = ByteArray(4)
            FileInputStream(file).use { fis ->
                if (fis.read(header) != 4) return false
            }
            val isZipHeader = header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
            if (!isZipHeader) return false

            // 2. Check ZIP End of Central Directory (PK\x05\x06) in the last 65KB to guarantee file is 100% complete
            if (!hasZipEndOfCentralDirectory(file)) {
                Log.w(TAG, "APK file ${file.name} is missing ZIP EOCD signature (truncated download)")
                return false
            }

            // 3. Verify Android PackageManager can parse the APK manifest AND packageName strictly matches this app
            val archiveInfo = getApkArchiveInfo(context, file) ?: return false
            if (archiveInfo.packageName != context.packageName) {
                Log.w(TAG, "Security reject: APK packageName (${archiveInfo.packageName}) != installed (${context.packageName})")
                return false
            }

            if (expectedVersionCode != null && expectedVersionCode > 0) {
                val apkCode = getArchiveVersionCode(archiveInfo)
                // If the cached APK is for a completely different old version, don't reuse it
                if (apkCode > 0 && apkCode != expectedVersionCode && apkCode == BuildConfig.VERSION_CODE) {
                    return false
                }
            }
            // 4. Verify cached/reused APK signing certificate also matches the installed app
            if (!verifyApkSigningCertificateMatchesInstalledApp(context, file)) {
                return false
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "APK archive validation failed for ${file.name}: ${e.message}")
            false
        }
    }

    private fun isHttpsUrl(url: String): Boolean {
        return url.trim().startsWith("https://", ignoreCase = true)
    }

    /**
     * Verifies that the downloaded APK archive is signed with the exact same signing certificate
     * as the currently installed app. Prevents Trojan/tampered APK installation even if an update
     * link is hijacked or a third-party file is uploaded.
     */
    private fun verifyApkSigningCertificateMatchesInstalledApp(context: Context, apkFile: File): Boolean {
        return try {
            val installedCerts = getInstalledAppCertSha256Set(context)
            val archiveCerts = getArchiveApkCertSha256Set(context, apkFile)
            if (installedCerts.isEmpty() || archiveCerts.isEmpty()) {
                Log.w(TAG, "Could not extract signing certificates for comparison (installed=${installedCerts.size}, archive=${archiveCerts.size})")
                return false
            }
            val matches = installedCerts.any { it in archiveCerts }
            if (!matches) {
                Log.e(TAG, "🚨 Security Alert: Downloaded APK signing certificate does NOT match installed app!")
            }
            matches
        } catch (e: Throwable) {
            Log.e(TAG, "APK signing verification error: ${e.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstalledAppCertSha256Set(context: Context): Set<String> {
        val pm = context.packageManager
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        return extractCertSha256Set(pkgInfo)
    }

    @Suppress("DEPRECATION")
    private fun getArchiveApkCertSha256Set(context: Context, apkFile: File): Set<String> {
        val pm = context.packageManager
        val archiveInfo: PackageInfo = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            else -> pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: return emptySet()
        return extractCertSha256Set(archiveInfo)
    }

    @Suppress("DEPRECATION")
    private fun extractCertSha256Set(pkgInfo: PackageInfo): Set<String> {
        val rawSigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory ?: signingInfo?.apkContentsSigners
            }
        } else {
            pkgInfo.signatures
        } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return rawSigs.mapNotNull { sig ->
            try {
                md.reset()
                md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            } catch (_: Throwable) {
                null
            }
        }.toSet()
    }

    private fun hasZipEndOfCentralDirectory(file: File): Boolean {
        return try {
            val length = file.length()
            if (length < 22L) return false
            val tailSize = minOf(length, 65_557L).toInt()
            val tailBytes = ByteArray(tailSize)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(length - tailSize)
                raf.readFully(tailBytes)
            }
            // Search backwards for PK\x05\x06 (0x50, 0x4B, 0x05, 0x06)
            for (i in (tailSize - 22) downTo 0) {
                if (tailBytes[i] == 0x50.toByte() &&
                    tailBytes[i + 1] == 0x4B.toByte() &&
                    tailBytes[i + 2] == 0x05.toByte() &&
                    tailBytes[i + 3] == 0x06.toByte()
                ) {
                    return true
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun getApkArchiveInfo(context: Context, file: File): PackageInfo? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, 0)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getArchiveVersionCode(info: PackageInfo): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    private fun verifySha256IfRequired(file: File, expectedSha256: String?): Boolean {
        val cleanExpected = expectedSha256?.trim().orEmpty()
        // If Admin left checksum blank, signing-certificate + packageName verification still protects integrity.
        if (cleanExpected.isEmpty()) return true
        // If a checksum IS provided, strictly reject malformed / truncated / non-hex checksums instead of bypassing.
        if (cleanExpected.length != 64 || !cleanExpected.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            Log.w(TAG, "Security reject: malformed SHA-256 checksum length=${cleanExpected.length}")
            return false
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            FileInputStream(file).use { fis ->
                while (true) {
                    val read = fis.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                actualHex.lowercase(Locale.US).toByteArray(Charsets.UTF_8),
                cleanExpected.lowercase(Locale.US).toByteArray(Charsets.UTF_8)
            )
        } catch (_: Throwable) {
            false
        }
    }

    private fun getUpdatesDirectory(context: Context): File {
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val dir = File(baseDir, "apk_updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getAllCandidateUpdateDirectories(context: Context): List<File> {
        return listOfNotNull(
            context.cacheDir?.let { File(it, "apk_updates") },
            context.externalCacheDir?.let { File(it, "apk_updates") },
            context.filesDir?.let { File(it, "apk_updates") }
        )
    }

    private fun cleanUpdatesDirectory(dir: File, keepFileName: String?) {
        try {
            dir.listFiles()?.forEach { f ->
                if (keepFileName == null || f.name != keepFileName) {
                    f.deleteRecursively()
                }
            }
        } catch (_: Throwable) {}
    }

    private fun buildTargetKey(info: AppUpdateInfo): String {
        return "${info.versionCode}|${info.versionName.trim()}|${info.downloadUrl.trim()}"
    }

    private fun shortHash(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            digest.take(5).joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            input.hashCode().toUInt().toString(16)
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    fun formatMegabytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        val mbps = bytesPerSec.toDouble() / (1024.0 * 1024.0)
        return if (mbps >= 1.0) {
            String.format(Locale.US, "%.2f MB/s", mbps)
        } else {
            val kbps = bytesPerSec.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB/s", kbps)
        }
    }
}
