package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

class SessionManager(context: Context) {
    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "shikho_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        android.util.Log.e("SessionManager", "Failed to initialize EncryptedSharedPreferences, falling back to standard SharedPreferences: ${e.message}", e)
        context.getSharedPreferences("shikho_prefs_fallback", Context.MODE_PRIVATE)
    }

    fun getDeviceId(): String {
        val fcm = getFcmToken()
        if (!fcm.isNullOrBlank()) {
            return fcm
        }
        var deviceId = sharedPreferences.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun getGoogleAdsId(): String {
        var adsId = sharedPreferences.getString("google_ads_id", null)
        if (adsId == null) {
            adsId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("google_ads_id", adsId).apply()
        }
        return adsId
    }

    @Volatile
    private var isUnauthorizedNotified = false

    fun saveTokens(accessToken: String, refreshToken: String?, userId: String) {
        isUnauthorizedNotified = false
        sharedPreferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .apply()
    }

    fun updateAuthTokens(accessToken: String, refreshToken: String?, idToken: String? = null) {
        val editor = sharedPreferences.edit()
            .putString("access_token", accessToken)
        if (!refreshToken.isNullOrBlank()) {
            editor.putString("refresh_token", refreshToken)
        }
        if (!idToken.isNullOrBlank()) {
            editor.putString("id_token", idToken)
        }
        editor.apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString("access_token", null)
    fun getUserId(): String? {
        val stored = sharedPreferences.getString("user_id", null)
        if (!stored.isNullOrBlank()) return stored
        val token = getAccessToken()
        if (!token.isNullOrBlank()) {
            try {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val decoded = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
                    val json = org.json.JSONObject(decoded)
                    val aud = json.optString("aud", "")
                    if (aud.isNotBlank()) {
                        sharedPreferences.edit().putString("user_id", aud).apply()
                        return aud
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun setFcmToken(token: String) {
        sharedPreferences.edit()
            .putString("fcm_token", token)
            .putString("device_id", token)
            .apply()
    }

    fun getFcmToken(): String? = sharedPreferences.getString("fcm_token", null)

    fun saveActiveProgram(
        programId: String,
        titleBn: String?,
        batchId: String? = null,
        classCode: String? = null,
        phaseId: String? = null
    ) {
        val editor = sharedPreferences.edit()
            .putString("active_program_id", programId)
            .putString("active_program_title_bn", titleBn)
        if (!batchId.isNullOrBlank()) {
            editor.putString("active_program_batch_id", batchId)
        }
        if (!classCode.isNullOrBlank()) {
            editor.putString("active_program_class_code", classCode)
        }
        if (!phaseId.isNullOrBlank()) {
            editor.putString("active_program_phase_id", phaseId)
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

    fun saveUserAcademicInfo(
        batchId: String?,
        className: String?,
        group: String?,
        vendor: String? = "BD",
        passingYear: String? = null
    ) {
        val editor = sharedPreferences.edit()
            .putString("academic_batch_id", batchId)
            .putString("academic_class_name", className)
            .putString("academic_group", group)
            .putString("academic_vendor", vendor ?: "BD")
        if (!passingYear.isNullOrBlank()) {
            editor.putString("academic_passing_year", passingYear)
        }
        editor.apply()
        _userProfileUpdateFlow.value = System.currentTimeMillis()
    }

    fun getAcademicPassingYear(): String? =
        sharedPreferences.getString("academic_passing_year", null)

    fun getUserBatchId(): String? = sharedPreferences.getString("academic_batch_id", null)
    fun getUserClassName(): String? = sharedPreferences.getString("academic_class_name", "C11")
    fun getUserGroup(): String? = sharedPreferences.getString("academic_group", "Humanities")
    fun getUserVendor(): String? = sharedPreferences.getString("academic_vendor", "BD")

    private val _userAvatarFlow = kotlinx.coroutines.flow.MutableStateFlow(getUserAvatar())
    val userAvatarFlow: kotlinx.coroutines.flow.StateFlow<String?> = _userAvatarFlow

    private val _userProfileUpdateFlow = kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis())
    val userProfileUpdateFlow: kotlinx.coroutines.flow.StateFlow<Long> = _userProfileUpdateFlow

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
        if (!phone.isNullOrBlank()) {
            editor.putString("user_phone", phone)
        }
        editor.apply()
        _userAvatarFlow.value = avatar
        _userProfileUpdateFlow.value = System.currentTimeMillis()
    }

    fun saveUserPhone(phone: String?) {
        if (!phone.isNullOrBlank()) {
            sharedPreferences.edit().putString("user_phone", phone).apply()
        }
    }

    fun getUserPhone(): String? = sharedPreferences.getString("user_phone", null)

    fun getUserFirstName(): String? = sharedPreferences.getString("user_first_name", null)
    fun getUserSchoolName(): String? = sharedPreferences.getString("user_school_name", null)
    fun getUserClassDisplay(): String? = sharedPreferences.getString("user_class_display", null)

    fun getUserFullName(): String? {
        val first = sharedPreferences.getString("user_first_name", null)
        val last = sharedPreferences.getString("user_last_name", null)
        return when {
            !first.isNullOrBlank() && !last.isNullOrBlank() -> "$first $last"
            !first.isNullOrBlank() -> first
            !last.isNullOrBlank() -> last
            else -> null
        }
    }

    fun getUserAvatar(): String? = sharedPreferences.getString("user_avatar", null)

    fun saveSelectedSubjectCodes(programId: String, subjectCodes: Set<String>) {
        sharedPreferences.edit()
            .putStringSet("priority_subjects_$programId", subjectCodes)
            .apply()
    }

    fun getSelectedSubjectCodes(programId: String): Set<String>? {
        return sharedPreferences.getStringSet("priority_subjects_$programId", null)
    }

    // Theme Mode Management ("system", "light", "dark")
    private val _themeModeFlow = kotlinx.coroutines.flow.MutableStateFlow(getThemeMode())
    val themeModeFlow: kotlinx.coroutines.flow.StateFlow<String> = _themeModeFlow

    fun getThemeMode(): String {
        return sharedPreferences.getString("app_theme_mode", "system") ?: "system"
    }

    fun setThemeMode(mode: String) {
        sharedPreferences.edit().putString("app_theme_mode", mode).apply()
        _themeModeFlow.value = mode
    }

    // Header Wallpaper & Live Theme Management
    private val _headerWallpaperFlow = kotlinx.coroutines.flow.MutableStateFlow(getHeaderWallpaperConfig())
    val headerWallpaperFlow: kotlinx.coroutines.flow.StateFlow<com.example.ui.theme.HeaderWallpaperConfig> = _headerWallpaperFlow

    fun getHeaderWallpaperConfig(): com.example.ui.theme.HeaderWallpaperConfig {
        val type = sharedPreferences.getString("header_wallpaper_type", com.example.ui.theme.HeaderWallpaperConfig.PRESET_COSMIC) ?: com.example.ui.theme.HeaderWallpaperConfig.PRESET_COSMIC
        val uri = sharedPreferences.getString("header_wallpaper_custom_uri", null)
        val title = sharedPreferences.getString("header_wallpaper_custom_title", null)
        val anim = sharedPreferences.getBoolean("header_wallpaper_anim_enabled", true)
        val dim = sharedPreferences.getFloat("header_wallpaper_dim", 0.25f)
        return com.example.ui.theme.HeaderWallpaperConfig(
            type = type,
            customUri = uri,
            customTitle = title,
            isAnimationEnabled = anim,
            dimOpacity = dim
        )
    }

    fun setHeaderWallpaperConfig(config: com.example.ui.theme.HeaderWallpaperConfig) {
        sharedPreferences.edit()
            .putString("header_wallpaper_type", config.type)
            .putString("header_wallpaper_custom_uri", config.customUri)
            .putString("header_wallpaper_custom_title", config.customTitle)
            .putBoolean("header_wallpaper_anim_enabled", config.isAnimationEnabled)
            .putFloat("header_wallpaper_dim", config.dimOpacity)
            .apply()
        _headerWallpaperFlow.value = config
    }

    // Class Notification Lead Time (Default 25 minutes)
    private val _classNotificationLeadTimeFlow = kotlinx.coroutines.flow.MutableStateFlow(getClassNotificationLeadTimeMinutes())
    val classNotificationLeadTimeFlow: kotlinx.coroutines.flow.StateFlow<Int> = _classNotificationLeadTimeFlow

    fun getClassNotificationLeadTimeMinutes(): Int {
        return sharedPreferences.getInt("class_notification_lead_time_minutes", 25)
    }

    fun setClassNotificationLeadTimeMinutes(minutes: Int) {
        val validMinutes = if (minutes in listOf(5, 10, 15, 20, 25, 30, 45, 60)) minutes else 25
        sharedPreferences.edit().putInt("class_notification_lead_time_minutes", validMinutes).apply()
        _classNotificationLeadTimeFlow.value = validMinutes
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
        sharedPreferences.edit().putStringSet("disabled_alarm_ids", current).apply()
    }

    // Custom Alarms Persistence
    fun getCustomAlarms(): List<CustomAlarmData> {
        val json = sharedPreferences.getString("custom_alarms_list", "[]") ?: "[]"
        val list = mutableListOf<CustomAlarmData>()
        try {
            val array = org.json.JSONArray(json)
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
        } catch (_: Exception) {}
        return list
    }

    fun saveCustomAlarms(list: List<CustomAlarmData>) {
        val array = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject().apply {
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
    private val _unauthorizedEvent = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val unauthorizedEvent: SharedFlow<Unit> = _unauthorizedEvent.asSharedFlow()

    fun notifyUnauthorized() {
        if (isUnauthorizedNotified) return
        val activeToken = getAccessToken()
        if (activeToken.isNullOrBlank()) return
        isUnauthorizedNotified = true
        clearSession()
        _unauthorizedEvent.tryEmit(Unit)
    }

    fun resetUnauthorizedNotified() {
        isUnauthorizedNotified = false
    }

    fun getSubscribedTopics(): Set<String> {
        return sharedPreferences.getStringSet("subscribed_fcm_topics", emptySet()) ?: emptySet()
    }

    fun setSubscribedTopics(topics: Set<String>) {
        sharedPreferences.edit()
            .putStringSet("subscribed_fcm_topics", topics)
            .apply()
    }

    fun clearSession() {
        sharedPreferences.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_id")
            .remove("active_program_id")
            .remove("active_program_title_bn")
            .remove("academic_batch_id")
            .remove("academic_class_name")
            .remove("academic_group")
            .remove("academic_vendor")
            .remove("academic_passing_year")
            .remove("user_first_name")
            .remove("user_last_name")
            .remove("user_avatar")
            .remove("just_signed_up")
            .apply()
        _userAvatarFlow.value = null
        _userProfileUpdateFlow.value = System.currentTimeMillis()
    }
}

data class CustomAlarmData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val triggerTimeMs: Long,
    val isEnabled: Boolean = true
)
