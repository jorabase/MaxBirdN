package com.example.library

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import com.example.update.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages the Cloudflare R2-backed HTML Book Library (`library/library_index.json`),
 * zero-config R2 Public URL auto-discovery, offline caching, and unified downloads via
 * [AppFileDownloadManager] so downloaded HTML books automatically appear in Downloads too.
 */
object LibraryRepository {

    private const val TAG = "LibraryRepository"
    private const val PREFS_NAME = "maxbird_library_prefs"
    private const val KEY_CACHED_INDEX_JSON = "cached_library_index_json"
    private const val KEY_CACHED_INDEX_AT = "cached_library_index_at"
    private const val KEY_RESOLVED_R2_BASE_URL = "resolved_r2_base_url"
    private const val KEY_CUSTOM_R2_BASE_URL = "custom_r2_base_url"
    private const val KEY_VERSION_PREFIX = "html_file_ver_"
    private const val KEY_SCROLL_PREFIX = "html_scroll_pos_"

    private const val INDEX_MEMORY_TTL_MS = 5 * 60_000L // 5 minutes session cache

    private val indexMutex = Mutex()
    @Volatile
    private var inMemoryIndex: LibraryIndex? = null
    @Volatile
    private var lastFetchedElapsedMs: Long = 0L

    private val httpClient: OkHttpClient by lazy {
        com.example.network.SharedHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Resolves the Cloudflare R2 Public Base URL in priority order:
     * 1. Explicit `BuildConfig.LIBRARY_R2_BASE_URL` (from .env / GitHub Secret)
     * 2. Saved custom / previously auto-discovered R2 base URL in SharedPreferences
     * 3. Automatic origin discovery from the active `app_updates.download_url` (which already points to Cloudflare R2!)
     */
    suspend fun resolveR2BaseUrl(context: Context): String = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)

        val customSaved = prefs.getString(KEY_CUSTOM_R2_BASE_URL, "")?.trim().orEmpty()
        if (customSaved.startsWith("https://", ignoreCase = true) || customSaved.startsWith("http://", ignoreCase = true)) {
            return@withContext customSaved.trimEnd('/')
        }

        val fromBuildConfig = try {
            BuildConfig.LIBRARY_R2_BASE_URL.trim().removeSurrounding("\"").removeSurrounding("'")
        } catch (_: Throwable) {
            ""
        }
        if (fromBuildConfig.startsWith("https://", ignoreCase = true) || fromBuildConfig.startsWith("http://", ignoreCase = true)) {
            val clean = fromBuildConfig.trimEnd('/')
            prefs.edit().putString(KEY_RESOLVED_R2_BASE_URL, clean).apply()
            return@withContext clean
        }

        val previouslyDiscovered = prefs.getString(KEY_RESOLVED_R2_BASE_URL, "")?.trim().orEmpty()
        if (previouslyDiscovered.startsWith("https://", ignoreCase = true)) {
            return@withContext previouslyDiscovered.trimEnd('/')
        }

        // Auto-discover the Cloudflare R2 public origin from existing app_updates APK download_url
        try {
            val updateInfo = AppUpdateRepository.fetchLatestUpdate(forceRefresh = false)
            val downloadUrl = updateInfo?.downloadUrl?.trim().orEmpty()
            val discoveredOrigin = extractPublicOrigin(downloadUrl)
            if (discoveredOrigin.isNotBlank()) {
                prefs.edit().putString(KEY_RESOLVED_R2_BASE_URL, discoveredOrigin).apply()
                Log.d(TAG, "Auto-discovered Cloudflare R2 base URL: $discoveredOrigin")
                return@withContext discoveredOrigin
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Auto-discovery of R2 URL skipped: ${e.message}")
        }

        ""
    }

    fun getCachedR2BaseUrlSync(context: Context): String {
        val prefs = getPrefs(context)
        val custom = prefs.getString(KEY_CUSTOM_R2_BASE_URL, "")?.trim().orEmpty()
        if (custom.startsWith("http", ignoreCase = true)) return custom.trimEnd('/')
        val buildCfg = try {
            BuildConfig.LIBRARY_R2_BASE_URL.trim().removeSurrounding("\"").removeSurrounding("'")
        } catch (_: Throwable) {
            ""
        }
        if (buildCfg.startsWith("http", ignoreCase = true)) return buildCfg.trimEnd('/')
        return prefs.getString(KEY_RESOLVED_R2_BASE_URL, "")?.trim().orEmpty().trimEnd('/')
    }

    fun saveCustomR2BaseUrl(context: Context, rawUrl: String) {
        val clean = rawUrl.trim().trimEnd('/')
        getPrefs(context).edit().apply {
            if (clean.isBlank()) {
                remove(KEY_CUSTOM_R2_BASE_URL)
            } else {
                putString(KEY_CUSTOM_R2_BASE_URL, clean)
            }
        }.apply()
        inMemoryIndex = null
        lastFetchedElapsedMs = 0L
    }

    private fun extractPublicOrigin(url: String): String {
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            return ""
        }
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return ""
            val host = uri.host?.lowercase() ?: return ""
            // Avoid accidentally using github.com / drive.google.com / t.me if APK is hosted there
            if (host.contains("github.com") || host.contains("drive.google.com") || host.contains("t.me") || host.contains("telegram")) {
                return ""
            }
            val portPart = if (uri.port > 0) ":${uri.port}" else ""
            "$scheme://$host$portPart"
        } catch (_: Throwable) {
            ""
        }
    }

    fun getCachedIndexSync(context: Context): LibraryIndex? {
        inMemoryIndex?.let { return it }
        return try {
            val raw = getPrefs(context).getString(KEY_CACHED_INDEX_JSON, null)?.trim()
            if (raw.isNullOrBlank() || !raw.startsWith("{")) return null
            val baseUrl = getCachedR2BaseUrlSync(context)
            parseLibraryIndex(JSONObject(raw), baseUrl).also { inMemoryIndex = it }
        } catch (_: Throwable) {
            null
        }
    }

    data class FetchIndexResult(
        val index: LibraryIndex?,
        val r2BaseUrl: String,
        val isFromOfflineCache: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Fetches `library/library_index.json` from Cloudflare R2 with 5-minute in-memory cache
     * and offline SharedPreferences fallback.
     */
    suspend fun fetchLibraryIndex(
        context: Context,
        forceRefresh: Boolean = false
    ): FetchIndexResult = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            val baseUrl = resolveR2BaseUrl(context)
            val nowElapsed = SystemClock.elapsedRealtime()

            if (!forceRefresh && inMemoryIndex != null && (nowElapsed - lastFetchedElapsedMs) < INDEX_MEMORY_TTL_MS) {
                return@withLock FetchIndexResult(
                    index = inMemoryIndex,
                    r2BaseUrl = baseUrl,
                    isFromOfflineCache = false
                )
            }

            if (baseUrl.isBlank()) {
                val cached = getCachedIndexSync(context)
                return@withLock FetchIndexResult(
                    index = cached,
                    r2BaseUrl = "",
                    isFromOfflineCache = cached != null,
                    errorMessage = if (cached == null) {
                        com.example.utils.AppLang.tr(
                            "Cloudflare R2 link is not configured yet. Upload books from the admin app or set the R2 link to see books here.",
                            "Cloudflare R2 লিংক এখনো সেট করা হয়নি। অ্যাডমিন অ্যাপ থেকে বই আপলোড করলে অথবা R2 লিংক সেট করলে এখানে সব বই দেখাবে।"
                        )
                    } else null
                )
            }

            val cacheBuster = if (forceRefresh) "?t=${System.currentTimeMillis()}" else "?v=${System.currentTimeMillis() / 60_000L}"
            val candidateUrls = listOf(
                "$baseUrl/library/library_index.json$cacheBuster",
                "$baseUrl/library_index.json$cacheBuster"
            )

            var lastError = ""
            for (endpoint in candidateUrls) {
                try {
                    val req = Request.Builder()
                        .url(endpoint)
                        .header("Cache-Control", "no-cache")
                        .header("Accept", "application/json")
                        .get()
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string()?.trim().orEmpty()
                            if (bodyStr.startsWith("{")) {
                                val parsed = parseLibraryIndex(JSONObject(bodyStr), baseUrl)
                                inMemoryIndex = parsed
                                lastFetchedElapsedMs = SystemClock.elapsedRealtime()
                                getPrefs(context).edit()
                                    .putString(KEY_CACHED_INDEX_JSON, bodyStr)
                                    .putLong(KEY_CACHED_INDEX_AT, System.currentTimeMillis())
                                    .apply()
                                return@withLock FetchIndexResult(
                                    index = parsed,
                                    r2BaseUrl = baseUrl,
                                    isFromOfflineCache = false
                                )
                            }
                        } else if (resp.code == 404) {
                            lastError = com.example.utils.AppLang.tr(
                                "No books have been uploaded to the library yet (library_index.json not found).",
                                "অ্যাডমিন অ্যাপ থেকে এখনো লাইব্রেরিতে কোনো বই আপলোড করা হয়নি (library_index.json পাওয়া যায়নি)।"
                            )
                        } else {
                            lastError = com.example.utils.AppLang.tr(
                                "Could not load library from server (HTTP ${resp.code}).",
                                "সার্ভার থেকে লাইব্রেরি লোড করা যায়নি (HTTP ${resp.code})।"
                            )
                        }
                    }
                } catch (e: Throwable) {
                    lastError = com.example.utils.AppLang.tr(
                        "No internet connection. You can still read your downloaded books offline.",
                        "ইন্টারনেট সংযোগ পাওয়া যায়নি। ডাউনলোড করা বইগুলো অফলাইনে পড়তে পারবেন।"
                    )
                    Log.w(TAG, "fetchLibraryIndex failed for $endpoint: ${e.message}")
                }
            }

            // Fallback to cached index if available
            val cached = getCachedIndexSync(context)
            FetchIndexResult(
                index = cached,
                r2BaseUrl = baseUrl,
                isFromOfflineCache = cached != null,
                errorMessage = if (cached == null) lastError.ifBlank { com.example.utils.AppLang.tr("Could not load library.", "লাইব্রেরি লোড করা যায়নি।") } else null
            )
        }
    }

    /**
     * Fetches raw HTML content directly from Cloudflare R2 for online reading without requiring download.
     */
    suspend fun fetchOnlineHtmlContent(remoteUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = remoteUrl.trim()
            if (cleanUrl.isBlank()) {
                return@withContext Result.failure(Exception(com.example.utils.AppLang.tr("Book link not found.", "বইয়ের লিংক পাওয়া যায়নি।")))
            }
            val req = Request.Builder()
                .url(cleanUrl)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception(com.example.utils.AppLang.tr("Failed to load book (HTTP ${resp.code}).", "বইটি লোড করা যায়নি (HTTP ${resp.code})।")))
                }
                val html = resp.body?.string().orEmpty()
                if (html.isBlank()) {
                    return@withContext Result.failure(Exception(com.example.utils.AppLang.tr("The HTML file is empty.", "HTML ফাইলটি খালি পাওয়া গেছে।")))
                }
                Result.success(html)
            }
        } catch (e: Throwable) {
            Result.failure(Exception(com.example.utils.AppLang.tr("Internet connection failed: ${e.localizedMessage ?: "Please try again"}", "ইন্টারনেট সংযোগ ব্যর্থ হয়েছে: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}")))
        }
    }

    /**
     * Reads an offline downloaded HTML book file from secured internal storage.
     */
    suspend fun readLocalHtmlFile(localFilePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(localFilePath)
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(Exception(com.example.utils.AppLang.tr("Offline file not found on device.", "অফলাইন ফাইলটি ডিভাইসে পাওয়া যায়নি।")))
            }
            Result.success(file.readText(Charsets.UTF_8))
        } catch (e: Throwable) {
            Result.failure(Exception(com.example.utils.AppLang.tr("Could not read file: ${e.localizedMessage ?: "Unknown error"}", "ফাইলটি পড়া যায়নি: ${e.localizedMessage ?: "অজানা ত্রুটি"}")))
        }
    }

    /**
     * Downloads a single HTML book file via [AppFileDownloadManager] so it is saved in internal vault
     * and automatically appears in both the Library tab AND the Downloads tab.
     */
    fun downloadHtmlFile(
        context: Context,
        file: LibraryHtmlFile,
        forceRedownload: Boolean = false
    ) {
        val manager = AppFileDownloadManager.getInstance(context)
        saveDownloadedFileVersion(context, file.downloadEntityId, file.version)
        manager.downloadFile(
            id = file.downloadEntityId,
            title = file.title,
            subtitle = file.downloadSubtitle.ifBlank { com.example.utils.AppLang.tr("Library Book", "লাইব্রেরি বই") },
            fileType = DownloadedItemEntity.FILE_TYPE_HTML_BOOK,
            remoteUrl = file.fileUrl,
            forceRedownload = forceRedownload
        )
    }

    /**
     * Downloads an HTML book directly from inside the reader when opened via URL.
     */
    fun downloadHtmlFileByParams(
        context: Context,
        downloadEntityId: String,
        title: String,
        subtitle: String,
        remoteUrl: String,
        forceRedownload: Boolean = false
    ) {
        if (remoteUrl.isBlank()) return
        val manager = AppFileDownloadManager.getInstance(context)
        manager.downloadFile(
            id = downloadEntityId,
            title = title,
            subtitle = subtitle.ifBlank { com.example.utils.AppLang.tr("Library Book", "লাইব্রেরি বই") },
            fileType = DownloadedItemEntity.FILE_TYPE_HTML_BOOK,
            remoteUrl = remoteUrl,
            forceRedownload = forceRedownload
        )
    }

    /**
     * Downloads all files in a chapter that aren't already completed.
     */
    fun downloadChapterFiles(
        context: Context,
        chapter: LibraryChapter,
        downloadedMap: Map<String, DownloadedItemEntity>
    ): Int {
        var queued = 0
        for (file in chapter.files) {
            val existing = downloadedMap[file.downloadEntityId]
            val alreadyCompleted = existing != null &&
                existing.isCompleted &&
                File(existing.localFilePath).exists() &&
                getDownloadedFileVersion(context, file.downloadEntityId) >= file.version
            if (!alreadyCompleted) {
                downloadHtmlFile(context, file, forceRedownload = existing != null && existing.isCompleted)
                queued++
            }
        }
        return queued
    }

    fun deleteDownloadedHtmlFile(context: Context, downloadEntityId: String) {
        AppFileDownloadManager.getInstance(context).deleteDownloadedFile(downloadEntityId)
        getPrefs(context).edit().remove("$KEY_VERSION_PREFIX$downloadEntityId").apply()
    }

    fun saveDownloadedFileVersion(context: Context, downloadEntityId: String, version: Int) {
        getPrefs(context).edit().putInt("$KEY_VERSION_PREFIX$downloadEntityId", version).apply()
    }

    fun getDownloadedFileVersion(context: Context, downloadEntityId: String): Int {
        return getPrefs(context).getInt("$KEY_VERSION_PREFIX$downloadEntityId", 1)
    }

    fun getAllDownloadedVersions(context: Context): Map<String, Int> {
        val prefs = getPrefs(context)
        val out = mutableMapOf<String, Int>()
        for ((k, v) in prefs.all) {
            if (k.startsWith(KEY_VERSION_PREFIX) && v is Int) {
                out[k.removePrefix(KEY_VERSION_PREFIX)] = v
            }
        }
        return out
    }

    fun saveScrollProgress(context: Context, fileId: String, scrollRatio: Float) {
        if (fileId.isBlank()) return
        getPrefs(context).edit().putFloat("$KEY_SCROLL_PREFIX$fileId", scrollRatio.coerceIn(0f, 1f)).apply()
    }

    fun getScrollProgress(context: Context, fileId: String): Float {
        if (fileId.isBlank()) return 0f
        return getPrefs(context).getFloat("$KEY_SCROLL_PREFIX$fileId", 0f).coerceIn(0f, 1f)
    }

    // =========================================================================
    // FLEXIBLE JSON PARSER (SUPPORTS 4-LEVEL CLASSES->SUBJECTS->CHAPTERS->FILES
    // AS WELL AS FLAT SUBJECTS FALLBACK)
    // =========================================================================

    private fun resolveFullUrl(raw: String, baseUrl: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            return trimmed
        }
        if (baseUrl.isBlank()) return trimmed
        return "${baseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
    }

    private fun parseLibraryIndex(root: JSONObject, fallbackBaseUrl: String): LibraryIndex {
        val baseUrl = root.optString("r2_base_url", "")
            .ifBlank { root.optString("base_url", "") }
            .trim()
            .trimEnd('/')
            .ifBlank { fallbackBaseUrl.trimEnd('/') }

        val updatedAt = root.optString("updated_at", "").trim()

        // 1. Parse optional offline neural TTS voice pack manifest
        val ttsModels = mutableMapOf<String, TtsModelPackInfo>()
        val ttsObj = root.optJSONObject("tts_models")
        if (ttsObj != null) {
            val keys = ttsObj.keys()
            while (keys.hasNext()) {
                val langKey = keys.next().trim().lowercase()
                val mObj = ttsObj.optJSONObject(langKey) ?: continue
                val url = resolveFullUrl(mObj.optString("url", ""), baseUrl)
                if (url.isNotBlank()) {
                    ttsModels[langKey] = TtsModelPackInfo(
                        lang = langKey,
                        name = mObj.optString("name", if (langKey == "bn") com.example.utils.AppLang.tr("Bangla Natural Voice", "বাংলা ন্যাচারাল ভয়েস") else "English Natural Voice").trim(),
                        url = url,
                        version = mObj.optInt("version", 1),
                        sizeMb = mObj.optInt("size_mb", 35).coerceAtLeast(1)
                    )
                }
            }
        }

        // 2. Parse 4-level hierarchy: classes -> subjects -> chapters -> files
        val parsedClasses = mutableListOf<LibraryClassGroup>()
        val classesArr = root.optJSONArray("classes") ?: root.optJSONArray("categories")

        if (classesArr != null && classesArr.length() > 0) {
            for (cIdx in 0 until classesArr.length()) {
                val cObj = classesArr.optJSONObject(cIdx) ?: continue
                if (cObj.has("is_active") && !cObj.optBoolean("is_active", true)) continue
                val classId = cObj.optString("id", "class_$cIdx").trim().ifBlank { "class_$cIdx" }
                val classTitle = cObj.optString("title", "")
                    .ifBlank { cObj.optString("name", com.example.utils.AppLang.tr("Class ${cIdx + 1}", "শ্রেণী ${cIdx + 1}")) }.trim()
                val classSubtitle = cObj.optString("subtitle", "")
                    .ifBlank { cObj.optString("description", "") }.trim()
                val classBadge = cObj.optString("badge", "").trim()
                val classColor = cObj.optString("color_hex", "")
                    .ifBlank { cObj.optString("color", "") }.trim()
                val classOrder = cObj.optInt("order", cObj.optInt("sort_order", cIdx))

                val subjectsArr = cObj.optJSONArray("subjects") ?: JSONArray()
                val subjects = parseSubjectsArray(
                    subjectsArr = subjectsArr,
                    classId = classId,
                    classTitle = classTitle,
                    baseUrl = baseUrl
                )

                parsedClasses.add(
                    LibraryClassGroup(
                        id = classId,
                        title = classTitle,
                        subtitle = classSubtitle,
                        badge = classBadge,
                        colorHex = classColor,
                        order = classOrder,
                        subjects = subjects
                    )
                )
            }
        } else {
            // Backward-compatible fallback: if admin puts top-level `subjects`, group them by `class_name`/`class_title` or default group
            val topSubjectsArr = root.optJSONArray("subjects")
            if (topSubjectsArr != null && topSubjectsArr.length() > 0) {
                val grouped = linkedMapOf<String, MutableList<JSONObject>>()
                for (i in 0 until topSubjectsArr.length()) {
                    val sObj = topSubjectsArr.optJSONObject(i) ?: continue
                    val groupName = sObj.optString("class_title", "")
                        .ifBlank { sObj.optString("class_name", "") }
                        .ifBlank { sObj.optString("class_code", "") }
                        .trim()
                        .ifBlank { com.example.utils.AppLang.tr("All Books & Textbooks", "সকল বই ও পাঠ্যবই") }
                    grouped.getOrPut(groupName) { mutableListOf() }.add(sObj)
                }
                var gIdx = 0
                for ((groupTitle, sList) in grouped) {
                    val classId = "cls_${gIdx}_${groupTitle.hashCode()}"
                    val arr = JSONArray()
                    sList.forEach { arr.put(it) }
                    val subjects = parseSubjectsArray(arr, classId, groupTitle, baseUrl)
                    parsedClasses.add(
                        LibraryClassGroup(
                            id = classId,
                            title = groupTitle,
                            subtitle = com.example.utils.AppLang.tr("Library Collection", "লাইব্রেরি সংগ্রহ"),
                            order = gIdx,
                            subjects = subjects
                        )
                    )
                    gIdx++
                }
            }
        }

        return LibraryIndex(
            updatedAt = updatedAt,
            r2BaseUrl = baseUrl,
            ttsModels = ttsModels,
            classes = parsedClasses.sortedBy { it.order }
        )
    }

    private fun parseSubjectsArray(
        subjectsArr: JSONArray,
        classId: String,
        classTitle: String,
        baseUrl: String
    ): List<LibrarySubject> {
        val subjects = mutableListOf<LibrarySubject>()
        for (sIdx in 0 until subjectsArr.length()) {
            val sObj = subjectsArr.optJSONObject(sIdx) ?: continue
            if (sObj.has("is_active") && !sObj.optBoolean("is_active", true)) continue
            val subId = sObj.optString("id", "${classId}_sub_$sIdx").trim().ifBlank { "${classId}_sub_$sIdx" }
            val subTitle = sObj.optString("title", "")
                .ifBlank { sObj.optString("name", com.example.utils.AppLang.tr("Subject ${sIdx + 1}", "সাবজেক্ট ${sIdx + 1}")) }.trim()
            val subSubtitle = sObj.optString("subtitle", "")
                .ifBlank { sObj.optString("description", "") }.trim()
            val coverUrl = resolveFullUrl(sObj.optString("cover_url", ""), baseUrl)
            val colorHex = sObj.optString("color_hex", "")
                .ifBlank { sObj.optString("color", "") }.trim()
            val subOrder = sObj.optInt("order", sObj.optInt("sort_order", sIdx))

            val chaptersArr = sObj.optJSONArray("chapters") ?: JSONArray()
            val chapters = mutableListOf<LibraryChapter>()
            for (chIdx in 0 until chaptersArr.length()) {
                val chObj = chaptersArr.optJSONObject(chIdx) ?: continue
                if (chObj.has("is_active") && !chObj.optBoolean("is_active", true)) continue
                val chId = chObj.optString("id", "${subId}_ch_$chIdx").trim().ifBlank { "${subId}_ch_$chIdx" }
                val chNum = chObj.optInt("chapter_number", chObj.optInt("chapter_no", chIdx + 1))
                val chTitle = chObj.optString("title", "")
                    .ifBlank { chObj.optString("name", com.example.utils.AppLang.tr("Chapter $chNum", "অধ্যায় $chNum")) }.trim()
                val chSubtitle = chObj.optString("subtitle", "")
                    .ifBlank { chObj.optString("description", "") }.trim()
                val chOrder = chObj.optInt("order", chObj.optInt("sort_order", chIdx))

                val filesArr = chObj.optJSONArray("files") ?: chObj.optJSONArray("lessons") ?: JSONArray()
                val files = mutableListOf<LibraryHtmlFile>()
                for (fIdx in 0 until filesArr.length()) {
                    val fObj = filesArr.optJSONObject(fIdx) ?: continue
                    if (fObj.has("is_active") && !fObj.optBoolean("is_active", true)) continue
                    val rawUrl = fObj.optString("file_url", "")
                        .ifBlank { fObj.optString("url", "") }
                        .ifBlank { fObj.optString("html_url", "") }
                    val resolvedFileUrl = resolveFullUrl(rawUrl, baseUrl)
                    if (resolvedFileUrl.isBlank()) continue
                    val fileId = fObj.optString("id", "${chId}_f_$fIdx").trim().ifBlank { "${chId}_f_$fIdx" }
                    val fileTitle = fObj.optString("title", "")
                        .ifBlank { fObj.optString("name", com.example.utils.AppLang.tr("Lesson ${fIdx + 1}", "পাঠ ${fIdx + 1}")) }.trim()
                    val fileDesc = fObj.optString("description", "")
                        .ifBlank { fObj.optString("subtitle", "") }.trim()
                    val fileSize = fObj.optLong("file_size_bytes", fObj.optLong("size_bytes", fObj.optLong("size", 0L)))
                    val fileVersion = fObj.optInt("version", 1).coerceAtLeast(1)
                    val fileOrder = fObj.optInt("order", fObj.optInt("sort_order", fIdx))

                    files.add(
                        LibraryHtmlFile(
                            id = fileId,
                            chapterId = chId,
                            classTitle = classTitle,
                            subjectTitle = subTitle,
                            chapterTitle = chTitle,
                            title = fileTitle,
                            description = fileDesc,
                            fileUrl = resolvedFileUrl,
                            fileSizeBytes = fileSize,
                            version = fileVersion,
                            order = fileOrder
                        )
                    )
                }

                chapters.add(
                    LibraryChapter(
                        id = chId,
                        subjectId = subId,
                        chapterNumber = chNum,
                        title = chTitle,
                        subtitle = chSubtitle,
                        order = chOrder,
                        files = files.sortedBy { it.order }
                    )
                )
            }

            subjects.add(
                LibrarySubject(
                    id = subId,
                    classId = classId,
                    title = subTitle,
                    subtitle = subSubtitle,
                    coverUrl = coverUrl,
                    colorHex = colorHex,
                    order = subOrder,
                    chapters = chapters.sortedBy { it.order }
                )
            )
        }
        return subjects.sortedBy { it.order }
    }
}
