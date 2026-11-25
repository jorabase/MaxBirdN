package com.example.tts

import android.content.Context
import android.util.Log
import com.example.library.TtsModelPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

enum class TtsEnginePreference(val storageKey: String, val labelBn: String, val subtitleBn: String) {
    AUTO(
        storageKey = "auto",
        labelBn = "অটো (নিউরাল আগে)",
        subtitleBn = "ভয়েস প্যাক থাকলে Sherpa-ONNX নিউরাল, না থাকলে Google অফলাইন"
    ),
    NEURAL_SHERPA(
        storageKey = "sherpa",
        labelBn = "নিউরাল অফলাইন (Sherpa-ONNX)",
        subtitleBn = "ডাউনলোড করা বাংলা/ইংরেজি ONNX হিউম্যান ভয়েস মডেল"
    ),
    GOOGLE_SYSTEM(
        storageKey = "google",
        labelBn = "Google অফলাইন TTS",
        subtitleBn = "ফোনের বিল্ট-ইন Google Speech Services ভয়েস"
    );

    companion object {
        fun fromKey(raw: String?): TtsEnginePreference {
            return entries.firstOrNull { it.storageKey.equals(raw, ignoreCase = true) } ?: AUTO
        }
    }
}

data class InstalledNeuralModelPaths(
    val lang: String,
    val modelOnnxPath: String,
    val tokensTxtPath: String,
    val dataDirPath: String,
    val lexiconPath: String = "",
    val totalSizeBytes: Long = 0L
)

data class VoicePackDownloadProgress(
    val isDownloading: Boolean = false,
    val progressFraction: Float = 0f,
    val statusTextBn: String = "",
    val errorTextBn: String? = null
)

/**
 * Manages on-demand Offline Neural TTS Voice Packs (`bn` and `en`) in private internal storage.
 *
 * - Works out-of-the-box with official open-source Sherpa-ONNX VITS/Piper voice packs (`.tar.bz2`)
 *   AND seamlessly supports custom Cloudflare R2 mirrors (`.zip` / `.tar.bz2` / `.tar.gz`) via `library_index.json`.
 * - Persists user engine choice so both Google Offline TTS and Sherpa-ONNX Neural TTS work side-by-side.
 */
object TtsVoicePackManager {

    private const val TAG = "TtsVoicePackMgr"
    private const val MODELS_ROOT_DIR = "offline_neural_tts_models"
    private const val PREFS_NAME = "maxbird_tts_prefs"
    private const val KEY_ENGINE_PREF = "tts_engine_preference"

    /**
     * Built-in official Sherpa-ONNX offline neural voice packs so users can download Bangla & English
     * neural models immediately without requiring manual R2 uploads (R2 entries in `library_index.json` override these).
     */
    val DEFAULT_VOICE_PACKS: Map<String, TtsModelPackInfo> = linkedMapOf(
        "bn" to TtsModelPackInfo(
            lang = "bn",
            name = "বাংলা নিউরাল ভয়েস (Coqui VITS)",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-bn-custom_female.tar.bz2",
            version = 1,
            sizeMb = 50
        ),
        "en" to TtsModelPackInfo(
            lang = "en",
            name = "English Neural Voice (Piper Lessac)",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2",
            version = 1,
            sizeMb = 62
        )
    )

    private val _downloadStates = MutableStateFlow<Map<String, VoicePackDownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<String, VoicePackDownloadProgress>> = _downloadStates.asStateFlow()

    private val _enginePreference = MutableStateFlow(TtsEnginePreference.AUTO)
    val enginePreferenceFlow: StateFlow<TtsEnginePreference> = _enginePreference.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { com.example.security.AntiTamperSecurity.applyToOkHttpBuilder(this) }
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getEnginePreference(context: Context): TtsEnginePreference {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENGINE_PREF, TtsEnginePreference.AUTO.storageKey)
        val resolved = TtsEnginePreference.fromKey(raw)
        if (_enginePreference.value != resolved) {
            _enginePreference.value = resolved
        }
        return resolved
    }

    fun setEnginePreference(context: Context, pref: TtsEnginePreference) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE_PREF, pref.storageKey)
            .apply()
        _enginePreference.value = pref
    }

    /**
     * Merges custom Cloudflare R2 `tts_models` from `library_index.json` over built-in defaults.
     */
    fun resolveEffectiveVoicePacks(remoteModels: Map<String, TtsModelPackInfo>): Map<String, TtsModelPackInfo> {
        val merged = LinkedHashMap<String, TtsModelPackInfo>(DEFAULT_VOICE_PACKS)
        for ((k, v) in remoteModels) {
            val cleanKey = k.trim().lowercase()
            if (cleanKey.isNotBlank() && v.url.isNotBlank()) {
                merged[cleanKey] = v
            }
        }
        return merged
    }

    fun isSherpaRuntimeBundled(): Boolean {
        return try {
            Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getLangDir(context: Context, lang: String): File {
        val safeLang = lang.trim().lowercase().replace("[^a-z0-9_]".toRegex(), "")
        return File(File(context.applicationContext.filesDir, MODELS_ROOT_DIR), safeLang)
    }

    /**
     * Locates an installed ONNX voice model (`*.onnx` + `tokens.txt` + optional `espeak-ng-data`) for [lang].
     */
    fun getInstalledModelPaths(context: Context, lang: String): InstalledNeuralModelPaths? {
        val dir = getLangDir(context, lang)
        if (!dir.exists() || !dir.isDirectory) return null

        var onnxFile: File? = null
        var tokensFile: File? = null
        var lexiconFile: File? = null
        var espeakDir: File? = null
        var totalSize = 0L

        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                totalSize += f.length()
                val lower = f.name.lowercase()
                if (lower.endsWith(".onnx") && !lower.contains("quantized_tmp") && onnxFile == null) {
                    onnxFile = f
                } else if (lower == "tokens.txt" && tokensFile == null) {
                    tokensFile = f
                } else if (lower == "lexicon.txt" && lexiconFile == null) {
                    lexiconFile = f
                }
            } else if (f.isDirectory && f.name.equals("espeak-ng-data", ignoreCase = true) && espeakDir == null) {
                espeakDir = f
            }
        }

        val resolvedOnnx = onnxFile ?: return null
        val resolvedTokens = tokensFile ?: return null
        if (resolvedOnnx.length() <= 0L || resolvedTokens.length() <= 0L) return null

        return InstalledNeuralModelPaths(
            lang = lang.trim().lowercase(),
            modelOnnxPath = resolvedOnnx.absolutePath,
            tokensTxtPath = resolvedTokens.absolutePath,
            dataDirPath = espeakDir?.absolutePath.orEmpty(),
            lexiconPath = lexiconFile?.absolutePath.orEmpty(),
            totalSizeBytes = totalSize
        )
    }

    fun isModelInstalled(context: Context, lang: String): Boolean {
        return getInstalledModelPaths(context, lang) != null
    }

    fun deleteVoicePack(context: Context, lang: String): Boolean {
        val dir = getLangDir(context, lang)
        return try {
            val ok = !dir.exists() || dir.deleteRecursively()
            _downloadStates.update { it - lang.lowercase() }
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to delete voice pack $lang: ${e.message}")
            false
        }
    }

    /**
     * Downloads and extracts a `.tar.bz2`, `.tar.gz`, or `.zip` offline neural TTS voice pack for [packInfo].
     * Protects against zip-slip / tar-slip path traversal and validates `.onnx` + `tokens.txt`.
     */
    suspend fun downloadAndInstallVoicePack(
        context: Context,
        packInfo: TtsModelPackInfo
    ): Result<InstalledNeuralModelPaths> = withContext(Dispatchers.IO) {
        val lang = packInfo.lang.trim().lowercase()
        val url = packInfo.url.trim()
        if (url.isBlank()) {
            return@withContext Result.failure(Exception("ভয়েস প্যাক ডাউনলোড লিংক পাওয়া যায়নি।"))
        }

        _downloadStates.update {
            it + (lang to VoicePackDownloadProgress(
                isDownloading = true,
                progressFraction = 0.02f,
                statusTextBn = "ভয়েস প্যাক ডাউনলোড শুরু হচ্ছে..."
            ))
        }

        val tempArchive = File(context.applicationContext.cacheDir, "tts_pack_${lang}.archive.tmp")
        val targetDir = getLangDir(context, lang)
        val stagingDir = File(context.applicationContext.cacheDir, "tts_stage_${lang}")

        try {
            if (tempArchive.exists()) tempArchive.delete()
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MaxBird-Android/1.0")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw Exception("ডাউনলোড ব্যর্থ হয়েছে (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw Exception("খালি রেসপন্স পাওয়া গেছে")
                val totalLen = body.contentLength().coerceAtLeast(1L)
                var downloaded = 0L

                FileOutputStream(tempArchive).buffered(65536).use { out ->
                    val buf = ByteArray(65536)
                    val input = body.byteStream()
                    var read: Int
                    var lastEmit = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 180L) {
                            lastEmit = now
                            val frac = (downloaded.toFloat() / totalLen.toFloat()).coerceIn(0.02f, 0.82f)
                            val mbDone = downloaded / (1024.0 * 1024.0)
                            val mbTotal = totalLen / (1024.0 * 1024.0)
                            _downloadStates.update {
                                it + (lang to VoicePackDownloadProgress(
                                    isDownloading = true,
                                    progressFraction = frac,
                                    statusTextBn = String.format(Locale.US, "ডাউনলোড হচ্ছে %.1f / %.1f MB", mbDone, mbTotal)
                                ))
                            }
                        }
                    }
                    out.flush()
                }
            }

            _downloadStates.update {
                it + (lang to VoicePackDownloadProgress(
                    isDownloading = true,
                    progressFraction = 0.88f,
                    statusTextBn = "ভয়েস মডেল আনপ্যাক করা হচ্ছে... (একটু অপেক্ষা করুন)"
                ))
            }

            extractArchiveSafely(tempArchive, stagingDir, url)

            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.parentFile?.mkdirs()
            if (!stagingDir.renameTo(targetDir)) {
                stagingDir.copyRecursively(targetDir, overwrite = true)
                stagingDir.deleteRecursively()
            }
            tempArchive.delete()

            val installed = getInstalledModelPaths(context, lang)
                ?: throw Exception("ভয়েস প্যাকে .onnx বা tokens.txt ফাইল পাওয়া যায়নি।")

            _downloadStates.update {
                it + (lang to VoicePackDownloadProgress(
                    isDownloading = false,
                    progressFraction = 1f,
                    statusTextBn = "অফলাইন ভয়েস প্যাক প্রস্তুত!"
                ))
            }

            Result.success(installed)
        } catch (e: Throwable) {
            tempArchive.delete()
            stagingDir.deleteRecursively()
            val msg = e.localizedMessage ?: "ভয়েস প্যাক ডাউনলোড ব্যর্থ হয়েছে।"
            Log.e(TAG, "Voice pack download error ($lang): $msg", e)
            _downloadStates.update {
                it + (lang to VoicePackDownloadProgress(
                    isDownloading = false,
                    progressFraction = 0f,
                    statusTextBn = "",
                    errorTextBn = msg
                ))
            }
            Result.failure(Exception(msg))
        }
    }

    /**
     * Auto-detects archive format via magic bytes (BZip2 `.tar.bz2`, GZip `.tar.gz`, or ZIP `.zip`)
     * and extracts with strict canonical path-traversal protection.
     */
    private fun extractArchiveSafely(archiveFile: File, stagingDir: File, sourceUrl: String) {
        val header = ByteArray(4)
        val readCount = FileInputStream(archiveFile).use { it.read(header) }
        val isBzip2 = (readCount >= 3 && header[0] == 'B'.code.toByte() && header[1] == 'Z'.code.toByte() && header[2] == 'h'.code.toByte()) ||
            sourceUrl.endsWith(".tar.bz2", ignoreCase = true) || sourceUrl.endsWith(".tbz2", ignoreCase = true)
        val isGzip = (readCount >= 2 && header[0] == 0x1F.toByte() && header[1] == 0x8B.toByte()) ||
            sourceUrl.endsWith(".tar.gz", ignoreCase = true) || sourceUrl.endsWith(".tgz", ignoreCase = true)
        val isZip = (readCount >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) ||
            sourceUrl.endsWith(".zip", ignoreCase = true)

        val canonicalStage = stagingDir.canonicalPath
        val buffer = ByteArray(32768)

        when {
            isBzip2 -> {
                FileInputStream(archiveFile).buffered(65536).use { fis ->
                    BZip2CompressorInputStream(fis, true).use { bzis ->
                        TarArchiveInputStream(bzis).use { tis ->
                            extractTarEntries(tis, stagingDir, canonicalStage, buffer)
                        }
                    }
                }
            }
            isGzip -> {
                FileInputStream(archiveFile).buffered(65536).use { fis ->
                    GZIPInputStream(fis).use { gzis ->
                        TarArchiveInputStream(gzis).use { tis ->
                            extractTarEntries(tis, stagingDir, canonicalStage, buffer)
                        }
                    }
                }
            }
            isZip -> {
                ZipInputStream(BufferedInputStream(archiveFile.inputStream(), 65536)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(stagingDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(canonicalStage + File.separator)) {
                            throw SecurityException("অবৈধ ফাইল পাথ শনাক্ত হয়েছে")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).buffered().use { fos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            else -> {
                throw Exception("অসমর্থিত ভয়েস প্যাক ফরম্যাট (.tar.bz2 বা .zip প্রয়োজন)।")
            }
        }
    }

    private fun extractTarEntries(
        tis: TarArchiveInputStream,
        stagingDir: File,
        canonicalStage: String,
        buffer: ByteArray
    ) {
        var entry = tis.nextEntry
        while (entry != null) {
            val outFile = File(stagingDir, entry.name)
            if (!outFile.canonicalPath.startsWith(canonicalStage + File.separator)) {
                throw SecurityException("অবৈধ আর্কাইভ পাথ শনাক্ত হয়েছে")
            }
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else if (entry.isFile) {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).buffered().use { fos ->
                    var len: Int
                    while (tis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                }
            }
            entry = tis.nextEntry
        }
    }
}
