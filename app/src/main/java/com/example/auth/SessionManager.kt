package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SessionManager(context: Context) {
    val appContext: Context = context.applicationContext

    private val prefsHolder: PrefsHolder = getOrInitPrefs(appContext)
    private val fallbackPrefs: SharedPreferences get() = prefsHolder.fallbackPrefs
    private val sharedPreferences: SharedPreferences get() = prefsHolder.sharedPrefs
    private val isEncryptedStorageActive: Boolean get() = prefsHolder.isEncrypted

    private class PrefsHolder(
        val fallbackPrefs: SharedPreferences,
        val sharedPrefs: SharedPreferences,
        val isEncrypted: Boolean
    )

    init {
        if (!hasSyncedInitialFlows) {
            synchronized(prefsLock) {
                if (!hasSyncedInitialFlows) {
                    syncCompanionFlowsFromPrefs()
                    hasSyncedInitialFlows = true
                }
            }
        }
    }

    private fun syncCompanionFlowsFromPrefs() {
        try {
            _sharedThemeModeFlow.value = getThemeMode()
            val savedLang = getAppLanguage()
            com.example.utils.AppLang.current = savedLang
            _sharedAppLanguageFlow.value = savedLang
            _sharedHeaderWallpaperFlow.value = getHeaderWallpaperConfig()
            _sharedClassNotificationLeadTimeFlow.value = getClassNotificationLeadTimeMinutes()
            _sharedUserAvatarFlow.value = getUserAvatar()
            _sharedImportedThemesFlow.value = getImportedCustomThemes()
        } catch (_: Exception) {
        }
    }

    fun getDeviceId(): String {
        val fcm = getFcmToken()
        if (!fcm.isNullOrBlank()) {
            return fcm
        }
        var deviceId = sharedPreferences.getString("device_id", null)
            ?: fallbackPrefs.getString("device_id", null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("device_id", deviceId).apply()
            fallbackPrefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun getGoogleAdsId(): String {
        var adsId = sharedPreferences.getString("google_ads_id", null)
            ?: fallbackPrefs.getString("google_ads_id", null)
        if (adsId.isNullOrBlank()) {
            adsId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("google_ads_id", adsId).apply()
            fallbackPrefs.edit().putString("google_ads_id", adsId).apply()
        }
        return adsId
    }

    companion object {
        /**
         * Normalizes any Bangladeshi phone number (e.g. "+8801712345678", "8801712345678", "01712345678", or Bengali digits)
         * into a canonical 11-digit format "01XXXXXXXXX".
         */
        fun normalizePhone(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val trimmed = raw.trim()
            // Never treat hex device_hash or alphanumeric IDs as phone numbers
            if (trimmed.any { it.isLetter() }) return ""
            val bnDigits = "০১২৩৪৫৬৭৮৯"
            val ascii = trimmed.map { ch ->
                val idx = bnDigits.indexOf(ch)
                if (idx >= 0) ('0' + idx) else ch
            }.joinToString("")
            val digits = ascii.filter { it.isDigit() }
            return when {
                digits.length == 13 && digits.startsWith("8801") -> digits.substring(2)
                digits.length == 14 && digits.startsWith("008801") -> digits.substring(4)
                digits.length == 11 && digits.startsWith("01") -> digits
                digits.length == 10 && digits.startsWith("1") -> "0$digits"
                digits.length > 11 && digits.takeLast(11).startsWith("01") -> digits.takeLast(11)
                else -> ""
            }
        }

        /**
         * Returns all common representations of a phone number ("01XXXXXXXXX", "8801XXXXXXXXX", "+8801XXXXXXXXX")
         * so database queries and comparisons match regardless of formatting.
         */
        fun getPhoneVariants(raw: String?): Set<String> {
            val norm11 = normalizePhone(raw)
            if (norm11.isBlank()) return emptySet()
            val set = linkedSetOf(norm11)
            if (norm11.length == 11 && norm11.startsWith("01")) {
                set.add("88$norm11")
                set.add("+88$norm11")
            }
            raw?.trim()?.takeIf { it.isNotBlank() }?.let { set.add(it) }
            return set
        }

        fun phonesMatch(phoneA: String?, phoneB: String?): Boolean {
            val normA = normalizePhone(phoneA)
            val normB = normalizePhone(phoneB)
            return normA.isNotBlank() && normA == normB
        }

        private val prefsLock = Any()

        @Volatile
        private var cachedPrefsHolder: PrefsHolder? = null

        @Volatile
        private var hasSyncedInitialFlows: Boolean = false

        @Volatile
        private var singletonInstance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return singletonInstance ?: synchronized(prefsLock) {
                singletonInstance ?: SessionManager(context.applicationContext).also {
                    singletonInstance = it
                }
            }
        }

        private fun getOrInitPrefs(appContext: Context): PrefsHolder {
            cachedPrefsHolder?.let { return it }
            return synchronized(prefsLock) {
                cachedPrefsHolder?.let { return@synchronized it }
                val fallback = appContext.getSharedPreferences("shikho_prefs", Context.MODE_PRIVATE)
                var encrypted = false
                val prefs: SharedPreferences = try {
                    val masterKey = MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    val encPrefs = EncryptedSharedPreferences.create(
                        appContext,
                        "shikho_secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    encrypted = true
                    encPrefs
                } catch (_: Throwable) {
                    encrypted = false
                    fallback
                }

                // One-time migration of legacy plaintext tokens/keys into EncryptedSharedPreferences
                if (encrypted && !prefs.getBoolean("migrated_from_fallback_v1", false)) {
                    try {
                        val encEditor = prefs.edit()
                        val fbEditor = fallback.edit()
                        val sensitiveKeys = setOf("access_token", "refresh_token", "id_token")
                        for ((key, value) in fallback.all) {
                            if (prefs.contains(key)) continue
                            when (value) {
                                is String -> encEditor.putString(key, value)
                                is Boolean -> encEditor.putBoolean(key, value)
                                is Int -> encEditor.putInt(key, value)
                                is Long -> encEditor.putLong(key, value)
                                is Float -> encEditor.putFloat(key, value)
                                is Set<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val strSet = (value as? Set<String>)?.let { HashSet(it) }
                                    if (strSet != null) encEditor.putStringSet(key, strSet)
                                }
                            }
                        }
                        for (sKey in sensitiveKeys) {
                            fbEditor.remove(sKey)
                        }
                        encEditor.putBoolean("migrated_from_fallback_v1", true)
                        encEditor.apply()
                        fbEditor.apply()
                    } catch (_: Throwable) {
                    }
                }

                PrefsHolder(fallback, prefs, encrypted).also {
                    cachedPrefsHolder = it
                }
            }
        }

        internal val _sharedThemeModeFlow = MutableStateFlow("system")
        internal val _sharedAppLanguageFlow = MutableStateFlow(com.example.utils.AppLanguage.ENGLISH)
        internal val _sharedHeaderWallpaperFlow = MutableStateFlow(com.example.ui.theme.HeaderWallpaperConfig())
        internal val _sharedClassNotificationLeadTimeFlow = MutableStateFlow(30)
        internal val _sharedUserAvatarFlow = MutableStateFlow<String?>(null)
        internal val _sharedUserProfileUpdateFlow = MutableStateFlow(System.currentTimeMillis())
        internal val _sharedImportedThemesFlow = MutableStateFlow<List<com.example.ui.theme.ImportedWallpaperTheme>>(emptyList())
        internal val _sharedUnauthorizedEvent = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        @Volatile
        internal var isUnauthorizedNotified = false
    }

    fun saveTokens(accessToken: String, refreshToken: String?, userId: String) {
        isUnauthorizedNotified = false
        val previousUserId = sharedPreferences.getString("user_id", null)
            ?: fallbackPrefs.getString("user_id", null)
        if (!previousUserId.isNullOrBlank() && previousUserId != userId) {
            clearActiveProgram()
            try {
                com.example.course.CourseCommerceRepository.clearCache(appContext)
            } catch (_: Throwable) {}
        }

        sharedPreferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .apply()

        if (isEncryptedStorageActive) {
            fallbackPrefs.edit()
                .remove("access_token")
                .remove("refresh_token")
                .remove("id_token")
                .putString("user_id", userId)
                .apply()
        }
    }

    fun updateAuthTokens(accessToken: String, refreshToken: String?, idToken: String? = null) {
        val editor = sharedPreferences.edit().putString("access_token", accessToken)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString("refresh_token", refreshToken)
        }
        if (!idToken.isNullOrBlank()) {
            editor.putString("id_token", idToken)
        }
        editor.apply()

        if (isEncryptedStorageActive) {
            fallbackPrefs.edit()
                .remove("access_token")
                .remove("refresh_token")
                .remove("id_token")
                .apply()
        }
    }

    fun getAccessToken(): String? {
        val token = sharedPreferences.getString("access_token", null)
        if (!token.isNullOrBlank()) return token
        return if (!isEncryptedStorageActive) fallbackPrefs.getString("access_token", null) else null
    }

    fun getRefreshToken(): String? {
        val rToken = sharedPreferences.getString("refresh_token", null)
        if (!rToken.isNullOrBlank()) return rToken
        return if (!isEncryptedStorageActive) fallbackPrefs.getString("refresh_token", null) else null
    }

    fun getUserId(): String? {
        val stored = sharedPreferences.getString("user_id", null)
            ?: fallbackPrefs.getString("user_id", null)
        if (!stored.isNullOrBlank()) return stored
        val token = getAccessToken()
        if (!token.isNullOrBlank()) {
            try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val decoded = String(
                        android.util.Base64.decode(
                            parts[1],
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                    )
                    val json = JSONObject(decoded)
                    val resolvedId = json.optString("user_id", "").ifBlank {
                        json.optString("sub", "").ifBlank { json.optString("aud", "") }
                    }
                    if (resolvedId.isNotBlank()) {
                        sharedPreferences.edit().putString("user_id", resolvedId).apply()
                        fallbackPrefs.edit().putString("user_id", resolvedId).apply()
                        return resolvedId
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun markModelTestCompleted(modelTestId: String) {
        if (modelTestId.isBlank()) return
        val completed = getCompletedModelTests().toMutableSet()
        completed.add(modelTestId)
        sharedPreferences.edit().putStringSet("completed_model_tests", HashSet(completed)).apply()
    }

    fun isModelTestCompleted(modelTestId: String): Boolean {
        return getCompletedModelTests().contains(modelTestId)
    }

    fun getCompletedModelTests(): Set<String> {
        return sharedPreferences.getStringSet("completed_model_tests", emptySet()) ?: emptySet()
    }

    fun setFcmToken(token: String) {
        sharedPreferences.edit()
            .putString("fcm_token", token)
            .putString("device_id", token)
            .apply()
        fallbackPrefs.edit()
            .putString("fcm_token", token)
            .putString("device_id", token)
            .apply()
    }

    fun getFcmToken(): String? =
        sharedPreferences.getString("fcm_token", null) ?: fallbackPrefs.getString("fcm_token", null)

    fun saveActiveProgram(
        programId: String,
        titleBn: String?,
        batchId: String? = null,
        classCode: String? = null,
        phaseId: String? = null
    ) {
        val oldProgramId = sharedPreferences.getString("active_program_id", null)
        val editor = sharedPreferences.edit()
            .putString("active_program_id", programId)
        if (!titleBn.isNullOrBlank()) {
            editor.putString("active_program_title_bn", titleBn)
        }
        if (!batchId.isNullOrBlank()) {
            editor.putString("active_program_batch_id", batchId)
        }
        if (!classCode.isNullOrBlank()) {
            editor.putString("active_program_class_code", classCode)
        }
        if (!phaseId.isNullOrBlank()) {
            editor.putString("active_program_phase_id", phaseId)
        } else if (oldProgramId != null && oldProgramId != programId) {
            // কোর্স পরিবর্তন হলে পুরাতন কোর্সের phase_id মুছে ফেলা হবে যাতে নতুন কোর্সের সাথে না মেশে
            editor.remove("active_program_phase_id")
        }
        editor.apply()
    }

    fun saveActiveProgramPhaseId(phaseId: String?) {
        val editor = sharedPreferences.edit()
        if (!phaseId.isNullOrBlank()) {
            editor.putString("active_program_phase_id", phaseId)
        } else {
            editor.remove("active_program_phase_id")
        }
        editor.apply()
    }

    fun getActiveProgramId(): String? = sharedPreferences.getString("active_program_id", null)
    fun getActiveProgramTitleBn(): String? = sharedPreferences.getString("active_program_title_bn", null)
    fun getActiveProgramBatchId(): String? = sharedPreferences.getString("active_program_batch_id", null)
    fun getActiveProgramClassCode(): String? = sharedPreferences.getString("active_program_class_code", null)
    fun getActiveProgramPhaseId(): String? = sharedPreferences.getString("active_program_phase_id", null)

    fun clearActiveProgram() {
        sharedPreferences.edit()
            .remove("active_program_id")
            .remove("active_program_title_bn")
            .remove("active_program_batch_id")
            .remove("active_program_class_code")
            .remove("active_program_phase_id")
            .apply()
    }

    fun getSubscribedFcmProgramId(): String? = sharedPreferences.getString("subscribed_fcm_program_id", null)
    fun setSubscribedFcmProgramId(programId: String?) {
        sharedPreferences.edit().putString("subscribed_fcm_program_id", programId).apply()
    }

    fun getSubscribedFcmPhaseId(): String? = sharedPreferences.getString("subscribed_fcm_phase_id", null)
    fun setSubscribedFcmPhaseId(phaseId: String?) {
        sharedPreferences.edit().putString("subscribed_fcm_phase_id", phaseId).apply()
    }

    fun getSubscribedCourseTopics(): Set<String> {
        return sharedPreferences.getStringSet("subscribed_fcm_course_topics", emptySet()) ?: emptySet()
    }

    fun setSubscribedCourseTopics(topics: Set<String>) {
        sharedPreferences.edit().putStringSet("subscribed_fcm_course_topics", HashSet(topics)).apply()
    }

    fun getActiveEnrolledFcmCourses(): List<StoredEnrolledFcmCourse> {
        val raw = sharedPreferences.getString("active_enrolled_fcm_courses", "[]") ?: "[]"
        val list = mutableListOf<StoredEnrolledFcmCourse>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pid = obj.optString("programId", "").trim()
                if (pid.isBlank()) continue
                list.add(
                    StoredEnrolledFcmCourse(
                        programId = pid,
                        titleBn = obj.optString("titleBn", "").trim(),
                        phaseId = obj.optString("phaseId", "").trim().takeIf { it.isNotBlank() },
                        expiryDate = obj.optString("expiryDate", "").trim().takeIf { it.isNotBlank() },
                        isActive = obj.optBoolean("isActive", true)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun setActiveEnrolledFcmCourses(courses: List<StoredEnrolledFcmCourse>) {
        val arr = JSONArray()
        courses.distinctBy { it.programId }.forEach { item ->
            if (item.programId.isBlank()) return@forEach
            val obj = JSONObject().apply {
                put("programId", item.programId)
                put("titleBn", item.titleBn)
                put("phaseId", item.phaseId ?: "")
                put("expiryDate", item.expiryDate ?: "")
                put("isActive", item.isActive)
            }
            arr.put(obj)
        }
        sharedPreferences.edit().putString("active_enrolled_fcm_courses", arr.toString()).apply()
    }

    fun getActiveEnrolledProgramIds(): Set<String> {
        val ids = linkedSetOf<String>()
        getActiveEnrolledFcmCourses().forEach { c ->
            if (c.programId.isNotBlank()) ids.add(c.programId)
        }
        getActiveProgramId()?.trim()?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
        return ids
    }

    fun saveUserAcademicInfo(
        batchId: String?,
        className: String?,
        group: String?,
        vendor: String? = null,
        passingYear: String? = null,
        clearPassingYearIfNull: Boolean = false
    ) {
        val editor = sharedPreferences.edit()
            .putString("academic_batch_id", batchId)
            .putString("academic_class_name", className)
            .putString("academic_group", group)
        if (!vendor.isNullOrBlank()) {
            editor.putString("academic_vendor", vendor)
        }
        if (!passingYear.isNullOrBlank()) {
            editor.putString("academic_passing_year", passingYear)
        } else if (clearPassingYearIfNull) {
            editor.remove("academic_passing_year")
        }
        editor.apply()
        _sharedUserProfileUpdateFlow.value = System.currentTimeMillis()
    }

    fun getAcademicPassingYear(): String? =
        sharedPreferences.getString("academic_passing_year", null)

    fun getUserBatchId(): String? = sharedPreferences.getString("academic_batch_id", null)
    fun getUserClassName(): String? = sharedPreferences.getString("academic_class_name", null)
    fun getUserGroup(): String? = sharedPreferences.getString("academic_group", null)
    fun getUserVendor(): String? = sharedPreferences.getString("academic_vendor", null)

    val userAvatarFlow: StateFlow<String?> = _sharedUserAvatarFlow
    val userProfileUpdateFlow: StateFlow<Long> = _sharedUserProfileUpdateFlow

    fun saveUserProfile(
        firstName: String?,
        lastName: String?,
        avatar: String?,
        schoolName: String? = null,
        classDisplay: String? = null,
        phone: String? = null
    ) {
        val editor = sharedPreferences.edit()
            .putString("user_first_name", firstName)
            .putString("user_last_name", lastName)
            .putString("user_avatar", avatar)
            .putString("user_school_name", schoolName)
            .putString("user_class_display", classDisplay)
        val fallbackEditor = fallbackPrefs.edit()
            .putString("user_first_name", firstName)
            .putString("user_last_name", lastName)
        if (!phone.isNullOrBlank()) {
            editor.putString("user_phone", phone)
            fallbackEditor.putString("user_phone", phone)
        }
        editor.apply()
        fallbackEditor.apply()
        _sharedUserAvatarFlow.value = avatar
        _sharedUserProfileUpdateFlow.value = System.currentTimeMillis()
    }

    fun saveUserPhone(phone: String?) {
        if (!phone.isNullOrBlank()) {
            val prevPhone = sharedPreferences.getString("user_phone", null)
                ?: fallbackPrefs.getString("user_phone", null)
            if (!prevPhone.isNullOrBlank() && !phonesMatch(prevPhone, phone)) {
                clearActiveProgram()
                try {
                    com.example.course.CourseCommerceRepository.clearCache(appContext)
                } catch (_: Throwable) {}
            }
            sharedPreferences.edit().putString("user_phone", phone.trim()).apply()
            fallbackPrefs.edit().putString("user_phone", phone.trim()).apply()
        }
    }

    fun getUserPhone(): String? {
        val stored = sharedPreferences.getString("user_phone", null)
            ?: fallbackPrefs.getString("user_phone", null)
        if (!stored.isNullOrBlank()) return stored
        val token = getAccessToken()
        if (!token.isNullOrBlank()) {
            try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val decoded = String(
                        android.util.Base64.decode(
                            parts[1],
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                    )
                    val json = JSONObject(decoded)
                    val resolvedPhone = json.optString("phone", "")
                        .ifBlank { json.optString("phone_number", "") }
                        .ifBlank { json.optString("mobile", "") }
                        .ifBlank { json.optString("username", "") }
                        .trim()
                    if (resolvedPhone.isNotBlank() && resolvedPhone.any { it.isDigit() }) {
                        sharedPreferences.edit().putString("user_phone", resolvedPhone).apply()
                        fallbackPrefs.edit().putString("user_phone", resolvedPhone).apply()
                        return resolvedPhone
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Returns the canonical 11-digit Bangladeshi phone number ("01XXXXXXXXX") for the logged-in user.
     */
    fun getNormalizedUserPhone(): String = normalizePhone(getUserPhone())

    /**
     * Returns the canonical account key for this student (normalized phone number first, or Shikho user_id).
     * Never depends on device_id.
     */
    fun getPrimaryAccountKey(): String {
        val normPhone = getNormalizedUserPhone()
        if (normPhone.isNotBlank()) return normPhone
        return getUserId()?.trim().orEmpty()
    }

    fun getUserFirstName(): String? =
        sharedPreferences.getString("user_first_name", null) ?: fallbackPrefs.getString("user_first_name", null)

    fun getUserLastName(): String? =
        sharedPreferences.getString("user_last_name", null) ?: fallbackPrefs.getString("user_last_name", null)

    fun getUserSchoolName(): String? = sharedPreferences.getString("user_school_name", null)
    fun getUserClassDisplay(): String? = sharedPreferences.getString("user_class_display", null)

    fun getUserFullName(): String? {
        val first = sharedPreferences.getString("user_first_name", null)
            ?: fallbackPrefs.getString("user_first_name", null)
        val last = sharedPreferences.getString("user_last_name", null)
            ?: fallbackPrefs.getString("user_last_name", null)
        return when {
            !first.isNullOrBlank() && !last.isNullOrBlank() -> "${first.trim()} ${last.trim()}"
            !first.isNullOrBlank() -> first.trim()
            !last.isNullOrBlank() -> last.trim()
            else -> null
        }
    }

    fun getUserAvatar(): String? = sharedPreferences.getString("user_avatar", null)

    /**
     * Returns the user's saved custom Live Class display name, or falls back to their profile full name.
     */
    fun getLiveCustomStudentName(): String? {
        val custom = sharedPreferences.getString("live_custom_student_name", null)
            ?: fallbackPrefs.getString("live_custom_student_name", null)
        if (!custom.isNullOrBlank()) return custom.trim()
        return getUserFullName()
    }

    /**
     * Saves the student's chosen display name for Live Classes.
     */
    fun setLiveCustomStudentName(name: String) {
        val clean = name.trim()
        sharedPreferences.edit().putString("live_custom_student_name", clean).apply()
        fallbackPrefs.edit().putString("live_custom_student_name", clean).apply()
    }

    /**
     * Stores and retrieves class-specific student display name if set for a particular live class session.
     */
    fun getLiveStudentNameForClass(classId: String): String? {
        if (classId.isBlank()) return getLiveCustomStudentName()
        val perClass = sharedPreferences.getString("live_student_name_$classId", null)
            ?: fallbackPrefs.getString("live_student_name_$classId", null)
        if (!perClass.isNullOrBlank()) return perClass.trim()
        return getLiveCustomStudentName()
    }

    fun setLiveStudentNameForClass(classId: String, name: String) {
        val clean = name.trim()
        setLiveCustomStudentName(clean)
        if (classId.isNotBlank()) {
            sharedPreferences.edit().putString("live_student_name_$classId", clean).apply()
            fallbackPrefs.edit().putString("live_student_name_$classId", clean).apply()
        }
    }

    fun saveSelectedSubjectCodes(programId: String, subjectCodes: Set<String>) {
        sharedPreferences.edit()
            .putStringSet("priority_subjects_$programId", HashSet(subjectCodes))
            .apply()
    }

    fun getSelectedSubjectCodes(programId: String): Set<String>? {
        return sharedPreferences.getStringSet("priority_subjects_$programId", null)
    }

    // Theme Mode Management ("system", "light", "dark")
    val themeModeFlow: StateFlow<String> = _sharedThemeModeFlow

    fun getThemeMode(): String {
        return sharedPreferences.getString("app_theme_mode", null)
            ?: fallbackPrefs.getString("app_theme_mode", "system")
            ?: "system"
    }

    fun setThemeMode(mode: String) {
        val cleanMode = when (mode.lowercase()) {
            "light", "dark", "system" -> mode.lowercase()
            else -> "system"
        }
        sharedPreferences.edit().putString("app_theme_mode", cleanMode).apply()
        fallbackPrefs.edit().putString("app_theme_mode", cleanMode).apply()
        _sharedThemeModeFlow.value = cleanMode
    }

    // App Language Management ("en" default, "bn" Bangla)
    val appLanguageFlow: StateFlow<com.example.utils.AppLanguage> = _sharedAppLanguageFlow

    fun getAppLanguage(): com.example.utils.AppLanguage {
        val raw = sharedPreferences.getString("app_language", null)
            ?: fallbackPrefs.getString("app_language", null)
        return com.example.utils.AppLanguage.fromCode(raw)
    }

    fun setAppLanguage(language: com.example.utils.AppLanguage) {
        sharedPreferences.edit().putString("app_language", language.code).apply()
        fallbackPrefs.edit().putString("app_language", language.code).apply()
        com.example.utils.AppLang.current = language
        _sharedAppLanguageFlow.value = language
    }

    // Header Wallpaper & Live Theme Management
    val headerWallpaperFlow: StateFlow<com.example.ui.theme.HeaderWallpaperConfig> = _sharedHeaderWallpaperFlow
    val importedCustomThemesFlow: StateFlow<List<com.example.ui.theme.ImportedWallpaperTheme>> = _sharedImportedThemesFlow

    fun getHeaderWallpaperConfig(): com.example.ui.theme.HeaderWallpaperConfig {
        val type = sharedPreferences.getString(
            "header_wallpaper_type",
            com.example.ui.theme.HeaderWallpaperConfig.PRESET_COSMIC
        ) ?: com.example.ui.theme.HeaderWallpaperConfig.PRESET_COSMIC
        val uri = sharedPreferences.getString("header_wallpaper_custom_uri", null)
        val title = sharedPreferences.getString("header_wallpaper_custom_title", null)
        val anim = sharedPreferences.getBoolean("header_wallpaper_anim_enabled", true)
        val dim = sharedPreferences.getFloat("header_wallpaper_dim", 0.25f)
        val customCode = sharedPreferences.getString("header_wallpaper_custom_code", null)
        return com.example.ui.theme.HeaderWallpaperConfig(
            type = type,
            customUri = uri,
            customTitle = title,
            isAnimationEnabled = anim,
            dimOpacity = dim,
            customThemeCode = customCode
        )
    }

    fun setHeaderWallpaperConfig(config: com.example.ui.theme.HeaderWallpaperConfig) {
        sharedPreferences.edit()
            .putString("header_wallpaper_type", config.type)
            .putString("header_wallpaper_custom_uri", config.customUri)
            .putString("header_wallpaper_custom_title", config.customTitle)
            .putBoolean("header_wallpaper_anim_enabled", config.isAnimationEnabled)
            .putFloat("header_wallpaper_dim", config.dimOpacity)
            .putString("header_wallpaper_custom_code", config.customThemeCode)
            .apply()
        _sharedHeaderWallpaperFlow.value = config
    }

    fun getImportedCustomThemes(): List<com.example.ui.theme.ImportedWallpaperTheme> {
        val raw = sharedPreferences.getString("imported_custom_header_themes", "[]") ?: "[]"
        return com.example.ui.theme.HeaderWallpaperCodeManager.parseThemeListJson(raw)
    }

    fun saveImportedCustomThemes(themes: List<com.example.ui.theme.ImportedWallpaperTheme>) {
        val json = com.example.ui.theme.HeaderWallpaperCodeManager.serializeThemeListJson(themes)
        sharedPreferences.edit().putString("imported_custom_header_themes", json).apply()
        _sharedImportedThemesFlow.value = themes
    }

    fun addImportedCustomTheme(theme: com.example.ui.theme.ImportedWallpaperTheme) {
        val current = getImportedCustomThemes().toMutableList()
        current.removeAll { it.id == theme.id || it.titleBn.equals(theme.titleBn, ignoreCase = true) }
        current.add(0, theme)
        saveImportedCustomThemes(current)
    }

    fun deleteImportedCustomTheme(themeId: String) {
        val current = getImportedCustomThemes().filterNot { it.id == themeId }
        saveImportedCustomThemes(current)
    }

    // Class Notification Lead Time (Default 30 minutes)
    val classNotificationLeadTimeFlow: StateFlow<Int> = _sharedClassNotificationLeadTimeFlow

    fun getClassNotificationLeadTimeMinutes(): Int {
        return sharedPreferences.getInt("class_notification_lead_time_minutes", 30)
    }

    fun setClassNotificationLeadTimeMinutes(minutes: Int) {
        val validMinutes = if (minutes in listOf(5, 10, 15, 20, 25, 30, 45, 60)) minutes else 30
        sharedPreferences.edit().putInt("class_notification_lead_time_minutes", validMinutes).apply()
        _sharedClassNotificationLeadTimeFlow.value = validMinutes
    }

    // Comprehensive Notification & Alarm Preferences
    fun isAllNotificationsEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_all_enabled", true)
    }

    fun setAllNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_all_enabled", enabled).apply()
    }

    fun isLiveClassNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_live_class_enabled", true)
    }

    fun setLiveClassNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_live_class_enabled", enabled).apply()
    }

    fun isExamNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_exam_enabled", true)
    }

    fun setExamNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_exam_enabled", enabled).apply()
    }

    fun isNotificationSoundEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_sound_enabled", true)
    }

    fun setNotificationSoundEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_sound_enabled", enabled).apply()
    }

    fun isNotificationVibrateEnabled(): Boolean {
        return sharedPreferences.getBoolean("notif_vibrate_enabled", true)
    }

    fun setNotificationVibrateEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("notif_vibrate_enabled", enabled).apply()
    }

    fun getDisabledAlarmIds(): Set<String> {
        return sharedPreferences.getStringSet("disabled_alarm_ids", emptySet()) ?: emptySet()
    }

    fun setAlarmDisabled(id: String, disabled: Boolean) {
        val current = getDisabledAlarmIds().toMutableSet()
        if (disabled) current.add(id) else current.remove(id)
        sharedPreferences.edit().putStringSet("disabled_alarm_ids", HashSet(current)).apply()
    }

    // Custom Alarms Persistence
    fun getCustomAlarms(): List<CustomAlarmData> {
        val json = sharedPreferences.getString("custom_alarms_list", "[]") ?: "[]"
        val list = mutableListOf<CustomAlarmData>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomAlarmData(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        message = obj.optString("message"),
                        triggerTimeMs = obj.optLong("triggerTimeMs"),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun saveCustomAlarms(list: List<CustomAlarmData>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("message", item.message)
                put("triggerTimeMs", item.triggerTimeMs)
                put("isEnabled", item.isEnabled)
            }
            array.put(obj)
        }
        sharedPreferences.edit().putString("custom_alarms_list", array.toString()).apply()
    }

    // Account Completion Status
    fun setAccountComplete(completed: Boolean) {
        sharedPreferences.edit().putBoolean("is_account_completed", completed).apply()
    }

    fun isAccountComplete(): Boolean {
        return sharedPreferences.getBoolean("is_account_completed", true)
    }

    fun isAccountIncomplete(): Boolean = !isAccountComplete()

    fun setJustSignedUp(isNew: Boolean) {
        sharedPreferences.edit().putBoolean("just_signed_up", isNew).apply()
    }

    // Watched / Completed Lessons Tracking
    fun markLessonCompleted(lessonId: String) {
        if (lessonId.isBlank()) return
        val current = sharedPreferences.getStringSet("completed_lessons", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(lessonId)
        sharedPreferences.edit().putStringSet("completed_lessons", HashSet(current)).apply()
    }

    fun isLessonCompleted(lessonId: String): Boolean {
        if (lessonId.isBlank()) return false
        val set = sharedPreferences.getStringSet("completed_lessons", emptySet()) ?: emptySet()
        return set.contains(lessonId)
    }

    fun getCompletedLessonIds(): Set<String> {
        return sharedPreferences.getStringSet("completed_lessons", emptySet()) ?: emptySet()
    }

    fun getJustSignedUp(): Boolean {
        return sharedPreferences.getBoolean("just_signed_up", false)
    }

    // Unauthorized / Session Expiry Event
    val unauthorizedEvent: SharedFlow<Unit> = _sharedUnauthorizedEvent.asSharedFlow()

    fun notifyUnauthorized() {
        if (isUnauthorizedNotified) return
        val activeToken = getAccessToken()
        if (activeToken.isNullOrBlank()) return
        isUnauthorizedNotified = true
        _sharedUnauthorizedEvent.tryEmit(Unit)
    }

    fun resetUnauthorizedNotified() {
        isUnauthorizedNotified = false
    }

    fun getSubscribedTopics(): Set<String> {
        return sharedPreferences.getStringSet("subscribed_fcm_topics", emptySet()) ?: emptySet()
    }

    fun setSubscribedTopics(topics: Set<String>) {
        sharedPreferences.edit()
            .putStringSet("subscribed_fcm_topics", HashSet(topics))
            .apply()
    }

    fun getLiveAutoPollEnabled(): Boolean {
        return sharedPreferences.getBoolean("live_auto_poll_enabled", false) ||
            fallbackPrefs.getBoolean("live_auto_poll_enabled", false)
    }

    fun setLiveAutoPollEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("live_auto_poll_enabled", enabled).apply()
        fallbackPrefs.edit().putBoolean("live_auto_poll_enabled", enabled).apply()
    }

    fun getLiveAutoPollDelayMs(): Long {
        val storedMs = sharedPreferences.getLong("live_auto_poll_delay_ms", -1L)
            .takeIf { it > 0L }
            ?: fallbackPrefs.getLong("live_auto_poll_delay_ms", -1L).takeIf { it > 0L }
        if (storedMs != null) {
            return storedMs.coerceIn(10L, 120_000L)
        }
        val legacySec = sharedPreferences.getInt("live_auto_poll_delay_sec", -1)
            .takeIf { it > 0 }
            ?: fallbackPrefs.getInt("live_auto_poll_delay_sec", -1).takeIf { it > 0 }
        return if (legacySec != null) {
            (legacySec * 1000L).coerceIn(10L, 120_000L)
        } else {
            670L // Default 0.67s
        }
    }

    fun setLiveAutoPollDelayMs(delayMs: Long) {
        val safeMs = delayMs.coerceIn(10L, 120_000L)
        sharedPreferences.edit().putLong("live_auto_poll_delay_ms", safeMs).apply()
        fallbackPrefs.edit().putLong("live_auto_poll_delay_ms", safeMs).apply()
    }

    fun getLiveAutoPollDelaySeconds(): Int {
        return ((getLiveAutoPollDelayMs() + 500L) / 1000L).toInt().coerceIn(1, 120)
    }

    fun setLiveAutoPollDelaySeconds(seconds: Int) {
        val safeSec = seconds.coerceIn(1, 120)
        setLiveAutoPollDelayMs(safeSec * 1000L)
    }

    fun clearSession() {
        val keys = listOf(
            "access_token", "refresh_token", "id_token", "user_id",
            "active_program_id", "active_program_title_bn",
            "active_program_batch_id", "active_program_class_code", "active_program_phase_id",
            "academic_batch_id", "academic_class_name", "academic_group",
            "academic_vendor", "academic_passing_year",
            "user_first_name", "user_last_name", "user_avatar",
            "user_school_name", "user_class_display", "user_phone",
            "live_custom_student_name", "just_signed_up",
            "completed_lessons", "completed_model_tests",
            "subscribed_fcm_program_id", "subscribed_fcm_phase_id",
            "subscribed_fcm_course_topics", "active_enrolled_fcm_courses",
            "subscribed_fcm_topics", "is_account_completed",
            "custom_alarms_list", "disabled_alarm_ids"
        )
        val editor = sharedPreferences.edit()
        val fallbackEditor = fallbackPrefs.edit()
        for (k in keys) {
            editor.remove(k)
            fallbackEditor.remove(k)
        }
        // Remove any dynamic per-program / per-class account keys
        try {
            sharedPreferences.all.keys.forEach { key ->
                if (key.startsWith("priority_subjects_") || key.startsWith("live_student_name_")) {
                    editor.remove(key)
                }
            }
            fallbackPrefs.all.keys.forEach { key ->
                if (key.startsWith("priority_subjects_") || key.startsWith("live_student_name_")) {
                    fallbackEditor.remove(key)
                }
            }
        } catch (_: Exception) {}
        editor.apply()
        fallbackEditor.apply()

        try {
            com.example.course.CourseCommerceRepository.clearCache(appContext)
        } catch (_: Throwable) {}
        try {
            com.example.notification.ClassAlarmScheduler.cancelAllAlarms(appContext)
        } catch (_: Throwable) {}

        _sharedUserAvatarFlow.value = null
        _sharedUserProfileUpdateFlow.value = System.currentTimeMillis()
    }
}

data class CustomAlarmData(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val triggerTimeMs: Long,
    val isEnabled: Boolean = true
)

data class StoredEnrolledFcmCourse(
    val programId: String,
    val titleBn: String = "",
    val phaseId: String? = null,
    val expiryDate: String? = null,
    val isActive: Boolean = true
)
