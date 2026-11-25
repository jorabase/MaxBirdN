package com.example.course

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.auth.SessionManager
import com.example.security.DeviceActivationRepository
import com.example.security.DeviceSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class CourseAccessState {
    LOCKED_BLURRED,      // ডিফল্ট ঝাপসা ও লকড — "কোর্সে এক্সেস পান" বাটন দেখাবে
    ACCESS_REQUESTED,    // "কোর্সে এক্সেস পান" ক্লিক করেছে — অ্যাডমিন দাম ও কিস্তি সেট করার অপেক্ষায়
    APPROVED_TO_BUY,     // অ্যাডমিন এক্সেস ও দাম সেট করেছে — ঝাপসা কেটে গিয়ে দাম ও কিস্তির অপশন দেখাবে
    PAYMENT_PENDING,     // স্টুডেন্ট মোবাইল নম্বর, টাকা ও TrxID জমা দিয়েছে — অ্যাডমিন ভেরিফিকেশনের অপেক্ষায়
    PAYMENT_REJECTED,    // অ্যাডমিন পেমেন্ট রিজেক্ট করেছে + কারণ লিখে দিয়েছে
    ACTIVE,              // কোর্স আনলকড ও সক্রিয় (ফুল পেইড অথবা কিস্তি চলমান এবং মেয়াদ আছে)
    INSTALLMENT_OVERDUE  // কিস্তির নির্দিষ্ট সময় পার হয়ে গেছে কিন্তু পরিশোধ করেনি — এক্সেস অটোমেটিক বন্ধ!
}

data class InstallmentPlanOption(
    val planId: String,
    val titleBn: String,
    val installmentsCount: Int,
    val intervalDays: Int,
    val firstAmount: Double,
    val perInstallmentAmount: Double
)

data class PaymentMethodOption(
    val id: String,
    val code: String,
    val nameBn: String,
    val accountNumber: String,
    val accountType: String = "Send Money করুন",
    val instructionBn: String = "",
    val colorHex: String = "",
    val isEnabled: Boolean = true
)

data class AdminPaymentSettings(
    val bkashNumber: String = "",
    val nagadNumber: String = "",
    val rocketNumber: String = "",
    val upayNumber: String = "",
    val instructionBn: String = "উপরের নম্বরে টাকা পাঠিয়ে নিচের ফর্মে আপনার মোবাইল নম্বর, পাঠানো টাকার পরিমাণ এবং Transaction ID (TrxID) সঠিকভাবে প্রদান করুন।",
    val enabledMethods: List<PaymentMethodOption> = emptyList()
)

data class CourseCommerceItemState(
    val enrollmentId: String = "",
    val programId: String,
    val programTitle: String = "",
    val accessState: CourseAccessState = CourseAccessState.LOCKED_BLURRED,
    val totalPrice: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val allowInstallments: Boolean = true,
    val installmentPlans: List<InstallmentPlanOption> = emptyList(),
    val activePlanId: String = "",
    val activePlanTitle: String = "",
    val totalInstallments: Int = 1,
    val completedInstallments: Int = 0,
    val installmentIntervalDays: Int = 7,
    val nextInstallmentAmount: Double = 0.0,
    val nextDueDateIso: String? = null,
    val nextDueDateMillis: Long? = null,
    val lastTransactionId: String = "",
    val lastPaymentAmount: Double = 0.0,
    val lastSenderMobile: String = "",
    val lastPaymentMethod: String = "bKash",
    val pendingInstallmentTxId: String = "",
    val pendingInstallmentAmount: Double = 0.0,
    val rejectionReason: String = "",
    val adminNote: String = "",
    // Free Trial Management (Controlled by Admin App)
    val trialEnabled: Boolean = false,
    val trialDays: Int = 0,
    val trialHours: Int = 0,
    val trialStartedAtIso: String? = null,
    val trialEndsAtIso: String? = null,
    val trialEndsAtMillis: Long? = null,
    val isTrialUsed: Boolean = false
) {
    /**
     * True if the student is currently in an active, non-expired Free Trial (and hasn't paid yet).
     */
    val isTrialCurrentlyActive: Boolean
        get() = trialEnabled &&
            accessState == CourseAccessState.ACTIVE &&
            paidAmount <= 0.0 &&
            trialEndsAtMillis != null &&
            CourseCommerceRepository.currentTrustedTimeMillis() < trialEndsAtMillis

    /**
     * True if the student used a Free Trial whose time has now expired (and hasn't paid for the course yet).
     */
    val isTrialExpiredNow: Boolean
        get() = paidAmount <= 0.0 && (
            (trialEndsAtMillis != null && CourseCommerceRepository.currentTrustedTimeMillis() >= trialEndsAtMillis) ||
            (isTrialUsed && !isTrialCurrentlyActive && trialEndsAtMillis != null)
        )

    /**
     * True if Admin enabled Free Trial for this course and the student can start it right now before buying.
     */
    val canStartFreeTrial: Boolean
        get() = trialEnabled &&
            (trialDays > 0 || trialHours > 0) &&
            paidAmount <= 0.0 &&
            !isTrialCurrentlyActive &&
            !isTrialExpiredNow &&
            !isTrialUsed &&
            trialEndsAtMillis == null

    val isCurrentlyUnlockedForStudy: Boolean
        get() {
            // ১. ফ্রি ট্রায়াল চলমান থাকলে (মেয়াদ থাকা পর্যন্ত) কোর্স আনলকড থাকবে!
            if (isTrialCurrentlyActive) return true

            if (accessState != CourseAccessState.ACTIVE) return false
            // যদি পেমেন্ট ছাড়া শুধু ট্রায়াল হয়ে থাকে এবং ট্রায়ালের মেয়াদ শেষ হয়ে যায়, তবে সাথে সাথে লক!
            if (paidAmount <= 0.0 && isTrialExpiredNow) return false
            // যদি কিস্তিতে কেনা হয় এবং বকেয়া থাকে (dueAmount > 0), তবে নির্দিষ্ট সময়ের মধ্যে কিস্তি না দিলে এক্সেস বন্ধ!
            if (dueAmount > 0.0 && nextDueDateMillis != null && CourseCommerceRepository.currentTrustedTimeMillis() > nextDueDateMillis) {
                return false
            }
            return true
        }

    val isOverdueNow: Boolean
        get() {
            if (accessState == CourseAccessState.INSTALLMENT_OVERDUE) return true
            if (accessState == CourseAccessState.ACTIVE && paidAmount > 0.0 && dueAmount > 0.0 && nextDueDateMillis != null && CourseCommerceRepository.currentTrustedTimeMillis() > nextDueDateMillis) {
                return true
            }
            return false
        }

    val effectiveState: CourseAccessState
        get() = when {
            isOverdueNow -> CourseAccessState.INSTALLMENT_OVERDUE
            isTrialCurrentlyActive -> CourseAccessState.ACTIVE
            accessState == CourseAccessState.ACTIVE && paidAmount <= 0.0 && isTrialExpiredNow -> CourseAccessState.APPROVED_TO_BUY
            else -> accessState
        }

    val hasPendingInstallmentPayment: Boolean
        get() = pendingInstallmentTxId.isNotBlank() && pendingInstallmentAmount > 0.0

    fun getTrialDurationLabelBn(): String {
        return when {
            trialDays > 0 && trialHours > 0 -> "${toBanglaDigits(trialDays.toString())} দিন ${toBanglaDigits(trialHours.toString())} ঘণ্টা"
            trialDays > 0 -> "${toBanglaDigits(trialDays.toString())} দিন"
            trialHours > 0 -> "${toBanglaDigits(trialHours.toString())} ঘণ্টা"
            else -> "১ দিন"
        }
    }

    fun getRemainingTrialTimeTextBn(): String {
        val endMs = trialEndsAtMillis ?: return ""
        val diff = endMs - CourseCommerceRepository.currentTrustedTimeMillis()
        if (diff <= 0L) return "ট্রায়ালের মেয়াদ শেষ!"
        val totalMinutes = (diff / (1000L * 60)).toInt().coerceAtLeast(1)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val mins = totalMinutes % 60
        return when {
            days > 0 && hours > 0 -> "${toBanglaDigits(days.toString())} দিন ${toBanglaDigits(hours.toString())} ঘণ্টা বাকি"
            days > 0 -> "${toBanglaDigits(days.toString())} দিন বাকি"
            hours > 0 && mins > 0 -> "${toBanglaDigits(hours.toString())} ঘণ্টা ${toBanglaDigits(mins.toString())} মিনিট বাকি"
            hours > 0 -> "${toBanglaDigits(hours.toString())} ঘণ্টা বাকি"
            else -> "${toBanglaDigits(mins.toString())} মিনিট বাকি"
        }
    }

    fun getRemainingDaysTextBn(): String {
        val dueMs = nextDueDateMillis ?: return ""
        val diff = dueMs - CourseCommerceRepository.currentTrustedTimeMillis()
        if (diff <= 0L) return "মেয়াদ শেষ!"
        val days = (diff / (1000L * 60 * 60 * 24)).toInt()
        val hours = ((diff % (1000L * 60 * 60 * 24)) / (1000L * 60 * 60)).toInt()
        return if (days > 0) {
            "${toBanglaDigits(days.toString())} দিন ${toBanglaDigits(hours.toString())} ঘণ্টা বাকি"
        } else {
            "${toBanglaDigits(hours.coerceAtLeast(1).toString())} ঘণ্টা বাকি"
        }
    }

    fun getFormattedNextDueDateBn(): String {
        val iso = nextDueDateIso ?: return ""
        return try {
            val clean = iso.substringBefore("T")
            val parts = clean.split("-")
            if (parts.size == 3) {
                val months = listOf("জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে")
                val m = (parts[1].toIntOrNull() ?: 1).coerceIn(1, 12)
                "${toBanglaDigits(parts[2].trimStart('0').ifBlank { "0" })} ${months[m - 1]}, ${toBanglaDigits(parts[0])}"
            } else iso
        } catch (_: Throwable) {
            iso
        }
    }

    companion object {
        fun toBanglaDigits(input: String): String = com.example.utils.toBengaliDigits(input)

        fun formatTakaBn(amount: Double): String {
            val longVal = amount.toLong()
            val formatted = if (amount == longVal.toDouble()) longVal.toString() else String.format(Locale.US, "%.0f", amount)
            return "৳${toBanglaDigits(formatted)}"
        }
    }
}

data class CourseCommerceSnapshot(
    val paymentSettings: AdminPaymentSettings = AdminPaymentSettings(),
    val enrollmentsByProgramId: Map<String, CourseCommerceItemState> = emptyMap()
) {
    /**
     * Checks whether a given program is actively unlocked for this student & device.
     * A course is unlocked ONLY if the student has an ACTIVE (and non-overdue) enrollment in `course_access_enrollments`.
     */
    fun isCourseActivelyUnlocked(
        programId: String,
        titleBn: String? = null
    ): Boolean {
        val enr = enrollmentsByProgramId[programId]
            ?: enrollmentsByProgramId.values.firstOrNull {
                !titleBn.isNullOrBlank() && it.programTitle.equals(titleBn.trim(), ignoreCase = true)
            }

        // যদি কিস্তির মেয়াদ শেষ হয়ে গিয়ে থাকে (OVERDUE), তবে সাথে সাথে এক্সেস বন্ধ!
        if (enr != null && enr.isOverdueNow) {
            return false
        }

        // স্টুডেন্ট কোর্সটি কিনে থাকলে বা কিস্তি চালু থাকলে (ACTIVE) আনলক হবে
        return enr != null && enr.isCurrentlyUnlockedForStudy
    }

    fun resolveCourseItemState(
        programId: String,
        titleBn: String? = null
    ): CourseCommerceItemState {
        val existing = enrollmentsByProgramId[programId]
            ?: enrollmentsByProgramId.values.firstOrNull {
                !titleBn.isNullOrBlank() && it.programTitle.equals(titleBn.trim(), ignoreCase = true)
            }

        if (existing != null) {
            return existing.copy(accessState = existing.effectiveState)
        }

        // ডিফল্টভাবে সকল কোর্স লকড এবং ঝাপসা (LOCKED_BLURRED) থাকবে!
        return CourseCommerceItemState(
            programId = programId,
            programTitle = titleBn ?: "",
            accessState = CourseAccessState.LOCKED_BLURRED
        )
    }
}

object CourseCommerceRepository {
    private const val TAG = "CourseCommerceRepo"
    private const val PREFS_NAME = "course_commerce_cache_prefs"
    private const val PREFS_NAME_ENCRYPTED = "course_commerce_cache_enc"
    private const val KEY_MIGRATED_TO_ENC = "key_migrated_to_enc_v1"
    private const val KEY_CACHED_SNAPSHOT_JSON = "key_cached_snapshot_json"
    private const val KEY_CACHED_FOR_UID = "key_cached_for_uid"

    private val prefsLock = Any()
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    // Monotonic server-synced clock to prevent local device clock rollback bypass on Free Trials & Installments
    @Volatile
    private var serverAnchorEpochMs: Long = 0L
    @Volatile
    private var serverAnchorElapsedMs: Long = 0L

    fun currentTrustedTimeMillis(): Long {
        val anchorEpoch = serverAnchorEpochMs
        val anchorElapsed = serverAnchorElapsedMs
        return if (anchorEpoch > 0L && anchorElapsed > 0L) {
            val delta = (SystemClock.elapsedRealtime() - anchorElapsed).coerceAtLeast(0L)
            anchorEpoch + delta
        } else {
            System.currentTimeMillis()
        }
    }

    private fun syncServerClockFromHttpDate(dateHeader: String?) {
        if (dateHeader.isNullOrBlank()) return
        try {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val parsed = sdf.parse(dateHeader.trim()) ?: return
            if (parsed.time > 1_700_000_000_000L) {
                serverAnchorEpochMs = parsed.time
                serverAnchorElapsedMs = SystemClock.elapsedRealtime()
            }
        } catch (_: Throwable) {}
    }

    private val httpClient = OkHttpClient.Builder()
        .apply { com.example.security.AntiTamperSecurity.applyToOkHttpBuilder(this) }
        .addInterceptor(Interceptor { chain ->
            val response = chain.proceed(chain.request())
            syncServerClockFromHttpDate(response.header("Date"))
            response
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val DUMMY_PAYMENT_NUMBERS = setOf(
        "01700000000",
        "01800000000",
        "01900000000",
        "01600000000",
        "01500000000",
        "01300000000",
        "01400000000",
        "00000000000",
        "1234567890",
        "12345678901"
    )

    fun isRealPaymentNumber(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val clean = raw.trim()
        if (clean.equals("null", ignoreCase = true) || clean.length < 6) return false
        val digitsOnly = DeviceCoursePermissionsConvertDigits(clean).filter { it.isDigit() }
        if (digitsOnly.isEmpty() || digitsOnly in DUMMY_PAYMENT_NUMBERS) return false
        // Reject placeholder numbers like 01700000000 where last 8 digits are all '0'
        if (digitsOnly.length == 11 && digitsOnly.endsWith("00000000")) return false
        return true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(prefsLock) {
            cachedPrefs?.let { return@synchronized it }
            val appCtx = context.applicationContext
            val resolved = try {
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val encPrefs = EncryptedSharedPreferences.create(
                    appCtx,
                    PREFS_NAME_ENCRYPTED,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                if (!encPrefs.getBoolean(KEY_MIGRATED_TO_ENC, false)) {
                    val legacy = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = encPrefs.edit()
                    legacy.getString(KEY_CACHED_SNAPSHOT_JSON, null)?.let {
                        editor.putString(KEY_CACHED_SNAPSHOT_JSON, it)
                    }
                    legacy.getString(KEY_CACHED_FOR_UID, null)?.let {
                        editor.putString(KEY_CACHED_FOR_UID, it)
                    }
                    editor.putBoolean(KEY_MIGRATED_TO_ENC, true).commit()
                    legacy.edit().clear().apply()
                }
                encPrefs
            } catch (e: Throwable) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable for commerce cache, using fallback: ${e.message}")
                appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            cachedPrefs = resolved
            resolved
        }
    }

    private fun JSONObject.optCleanString(key: String, fallback: String = ""): String {
        if (!has(key) || isNull(key)) return fallback
        val v = optString(key, "").trim()
        return if (v.isBlank() || v.equals("null", ignoreCase = true)) fallback else v
    }

    private fun parseIsoTimestampMillis(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso.equals("null", ignoreCase = true)) return null
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        val clean = iso.trim()
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(clean)
                if (date != null) return date.time
            } catch (_: Throwable) {}
        }
        return try {
            val base = clean.substringBefore(".").substringBefore("+").replace("Z", "")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(base)?.time
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildDefaultInstallmentPlans(totalPrice: Double, allowInstallments: Boolean): List<InstallmentPlanOption> {
        val price = if (totalPrice > 0.0) totalPrice else 1000.0
        val fullOption = InstallmentPlanOption(
            planId = "full",
            titleBn = "এককালীন সম্পূর্ণ পেমেন্ট (${CourseCommerceItemState.formatTakaBn(price)})",
            installmentsCount = 1,
            intervalDays = 0,
            firstAmount = price,
            perInstallmentAmount = price
        )
        if (!allowInstallments || price <= 0.0) {
            return listOf(fullOption)
        }
        val part2 = kotlin.math.round(price / 2.0)
        val part5 = kotlin.math.round(price / 5.0)
        val part3 = kotlin.math.round(price / 3.0)
        return listOf(
            fullOption,
            InstallmentPlanOption(
                planId = "inst_2_weekly",
                titleBn = "২ কিস্তিতে পরিশোধ (প্রথমে ${CourseCommerceItemState.formatTakaBn(part2)} + পরে ${CourseCommerceItemState.formatTakaBn(price - part2)})",
                installmentsCount = 2,
                intervalDays = 15,
                firstAmount = part2,
                perInstallmentAmount = price - part2
            ),
            InstallmentPlanOption(
                planId = "inst_5_weekly",
                titleBn = "৫ কিস্তিতে পরিশোধ (প্রতি সপ্তাহে ${CourseCommerceItemState.formatTakaBn(part5)} করে)",
                installmentsCount = 5,
                intervalDays = 7,
                firstAmount = part5,
                perInstallmentAmount = part5
            ),
            InstallmentPlanOption(
                planId = "inst_3_ten_days",
                titleBn = "৩ কিস্তিতে পরিশোধ (প্রতি ১০ দিন পরপর ${CourseCommerceItemState.formatTakaBn(part3)} করে)",
                installmentsCount = 3,
                intervalDays = 10,
                firstAmount = part3,
                perInstallmentAmount = part3
            )
        )
    }

    private fun parseInstallmentPlans(rawJsonAny: Any?, totalPrice: Double, allowInstallments: Boolean): List<InstallmentPlanOption> {
        try {
            val arr: JSONArray? = when (rawJsonAny) {
                is JSONArray -> rawJsonAny
                is String -> if (rawJsonAny.trim().startsWith("[")) JSONArray(rawJsonAny.trim()) else null
                else -> null
            }
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<InstallmentPlanOption>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.has("is_enabled") && !obj.optBoolean("is_enabled", true)) continue
                    if (obj.has("enabled") && !obj.optBoolean("enabled", true)) continue

                    val count = obj.optInt(
                        "installments_count",
                        obj.optInt("installment_count", obj.optInt("count", 1))
                    ).coerceAtLeast(1)
                    val interval = obj.optInt("interval_days", if (count > 1) 7 else 0)
                    val defaultPart = if (totalPrice > 0) kotlin.math.round(totalPrice / count) else 0.0

                    val rawFirst = listOf(
                        obj.optDouble("first_amount", Double.NaN),
                        obj.optDouble("first_payment", Double.NaN),
                        obj.optDouble("amount_per_installment", Double.NaN),
                        obj.optDouble("per_installment_amount", Double.NaN),
                        obj.optDouble("installment_amount", Double.NaN),
                        obj.optDouble("amount", Double.NaN)
                    ).firstOrNull { !it.isNaN() && it > 0.0 } ?: defaultPart

                    val rawPer = listOf(
                        obj.optDouble("per_installment_amount", Double.NaN),
                        obj.optDouble("amount_per_installment", Double.NaN),
                        obj.optDouble("installment_amount", Double.NaN),
                        obj.optDouble("amount", Double.NaN)
                    ).firstOrNull { !it.isNaN() && it > 0.0 } ?: rawFirst

                    val title = obj.optString("title_bn", "")
                        .ifBlank { obj.optString("title", "") }
                        .ifBlank { obj.optString("name", "") }
                        .ifBlank {
                            if (count == 1) "এককালীন সম্পূর্ণ পেমেন্ট (${CourseCommerceItemState.formatTakaBn(totalPrice)})"
                            else "${CourseCommerceItemState.toBanglaDigits(count.toString())} কিস্তিতে পরিশোধ (${CourseCommerceItemState.formatTakaBn(rawFirst)} করে)"
                        }
                    list.add(
                        InstallmentPlanOption(
                            planId = obj.optString("plan_id", obj.optString("id", "plan_$i")),
                            titleBn = title,
                            installmentsCount = count,
                            intervalDays = interval,
                            firstAmount = rawFirst,
                            perInstallmentAmount = rawPer
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            }
        } catch (_: Throwable) {}
        return buildDefaultInstallmentPlans(totalPrice, allowInstallments)
    }

    private fun parseEnrollmentItem(obj: JSONObject): CourseCommerceItemState? {
        val progId = obj.optCleanString("program_id").ifBlank { obj.optCleanString("course_id") }
        if (progId.isBlank()) return null

        val rawStatus = obj.optCleanString("status")
            .ifBlank { obj.optCleanString("access_status", "REQUESTED") }
            .uppercase()
        val totalPrice = obj.optDouble("total_price", obj.optDouble("total_fee", 0.0)).let { if (it.isNaN()) 0.0 else it }
        val paidAmount = obj.optDouble("paid_amount", 0.0).let { if (it.isNaN()) 0.0 else it }
        val dueAmount = obj.optDouble("due_amount", (totalPrice - paidAmount).coerceAtLeast(0.0)).let { if (it.isNaN()) 0.0 else it }
        val rawPlansAny = obj.opt("installment_plans_json")?.takeIf { it != JSONObject.NULL }
            ?: obj.opt("installment_plans")?.takeIf { it != JSONObject.NULL }
        val hasMultiPlanInJson = runCatching {
            val arr = when (rawPlansAny) {
                is JSONArray -> rawPlansAny
                is String -> if (rawPlansAny.trim().startsWith("[")) JSONArray(rawPlansAny.trim()) else null
                else -> null
            }
            var foundMulti = false
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    if (p.optInt("installments_count", p.optInt("installment_count", 1)) > 1) {
                        foundMulti = true
                        break
                    }
                }
            }
            foundMulti
        }.getOrDefault(false)

        val allowInst = when {
            hasMultiPlanInJson -> true
            obj.optBoolean("installment_enabled", false) -> true
            obj.has("installment_enabled") && !obj.optBoolean("installment_enabled", false) && !obj.optBoolean("allow_installments", false) -> false
            else -> obj.optBoolean("allow_installments", false)
        }
        val nextDueIso = obj.optCleanString("next_due_date").ifBlank {
            obj.optCleanString("next_installment_due_at")
        }.takeIf { it.isNotBlank() }
        val nextDueMillis = parseIsoTimestampMillis(nextDueIso)

        // Parse Free Trial fields strictly matching live `course_access_enrollments` columns
        val rawTrialDays = obj.optInt(
            "trial_days",
            obj.optInt("trial_days_granted", 0)
        ).coerceAtLeast(0)
        val rawTrialHours = obj.optInt("trial_hours", 0).coerceAtLeast(0)
        val trialStartedIso = obj.optCleanString("trial_started_at").takeIf { it.isNotBlank() }
        val trialEndsIso = obj.optCleanString("trial_ends_at").ifBlank {
            obj.optCleanString("trial_expires_at")
        }.takeIf { it.isNotBlank() }
        val trialEndsMillis = parseIsoTimestampMillis(trialEndsIso)
        val nowTrustedMs = currentTrustedTimeMillis()
        val trialEnabled = when {
            obj.has("trial_enabled") && !obj.isNull("trial_enabled") && obj.optBoolean("trial_enabled", false) -> true
            rawStatus == "TRIAL_ACTIVE" -> true
            trialEndsMillis != null && nowTrustedMs < trialEndsMillis -> true
            rawTrialDays > 0 || rawTrialHours > 0 -> true
            else -> obj.optBoolean("trial_enabled", false)
        }
        val effectiveTrialDays = if (trialEnabled && rawTrialDays == 0 && rawTrialHours == 0) 1 else rawTrialDays
        val isTrialUsed = obj.optBoolean("is_trial_used", false) ||
            obj.optBoolean("trial_used", false) ||
            trialStartedIso != null ||
            rawStatus == "TRIAL_EXPIRED" ||
            (trialEndsMillis != null && nowTrustedMs >= trialEndsMillis)

        val requestedIso = obj.optCleanString("requested_at").takeIf { it.isNotBlank() }
        val approvedIso = obj.optCleanString("approved_at").takeIf { it.isNotBlank() }
        val unlockedIso = obj.optCleanString("unlocked_at").takeIf { it.isNotBlank() }
        val requestedMillis = parseIsoTimestampMillis(requestedIso)
        val approvedMillis = parseIsoTimestampMillis(approvedIso)
        val unlockedMillis = parseIsoTimestampMillis(unlockedIso)
        val trialStartedMillis = parseIsoTimestampMillis(trialStartedIso)
        val latestAdminStamp = maxOf(approvedMillis ?: 0L, unlockedMillis ?: 0L, trialStartedMillis ?: 0L)

        // If the student clicked 'কোর্সে এক্সেস পান' (requested_at) AFTER the old approval/unlock timestamp
        // and hasn't paid (paidAmount <= 0), or if any course has ACTIVE/TRIAL_ACTIVE/APPROVED_TO_BUY
        // without Admin approval (approved_at == null && unlocked_at == null && paidAmount <= 0 && no active trial),
        // treat it strictly as REQUESTED (pending Admin approval).
        val isHijackedRequestRow = (
            paidAmount <= 0.0 &&
            requestedMillis != null &&
            latestAdminStamp > 0L &&
            requestedMillis > latestAdminStamp + 2000L &&
            rawStatus in listOf("ACTIVE", "TRIAL_ACTIVE", "APPROVED_TO_BUY", "APPROVED_UNPAID")
        ) || (
            rawStatus in listOf("ACTIVE", "TRIAL_ACTIVE", "APPROVED_TO_BUY", "APPROVED_UNPAID") &&
            paidAmount <= 0.0 &&
            unlockedIso == null &&
            approvedIso == null &&
            (trialEndsMillis == null || nowTrustedMs >= trialEndsMillis)
        )

        val effectiveRawStatus = if (isHijackedRequestRow) "REQUESTED" else rawStatus

        val mappedState = when (effectiveRawStatus) {
            "REQUESTED", "ACCESS_REQUESTED", "PENDING_APPROVAL" -> CourseAccessState.ACCESS_REQUESTED
            "APPROVED_TO_BUY", "APPROVED_UNPAID", "TRIAL_EXPIRED" -> CourseAccessState.APPROVED_TO_BUY
            "TRIAL_ACTIVE" -> {
                if (trialEndsMillis != null && nowTrustedMs < trialEndsMillis) {
                    CourseAccessState.ACTIVE
                } else {
                    CourseAccessState.APPROVED_TO_BUY
                }
            }
            "PAYMENT_PENDING", "PENDING_VERIFICATION" -> CourseAccessState.PAYMENT_PENDING
            "PAYMENT_REJECTED", "REJECTED" -> CourseAccessState.PAYMENT_REJECTED
            "ACTIVE", "INSTALLMENT_ACTIVE", "PARTIAL_PAID" -> {
                if (paidAmount <= 0.0 && trialEndsMillis != null && nowTrustedMs >= trialEndsMillis) {
                    CourseAccessState.APPROVED_TO_BUY
                } else if (paidAmount > 0.0 && dueAmount > 0.0 && nextDueMillis != null && nowTrustedMs > nextDueMillis) {
                    CourseAccessState.INSTALLMENT_OVERDUE
                } else {
                    CourseAccessState.ACTIVE
                }
            }
            "OVERDUE", "INSTALLMENT_OVERDUE" -> CourseAccessState.INSTALLMENT_OVERDUE
            "REVOKED", "BLOCKED", "LOCKED_BLURRED" -> CourseAccessState.LOCKED_BLURRED
            else -> CourseAccessState.LOCKED_BLURRED
        }

        val plans = parseInstallmentPlans(rawPlansAny, totalPrice, allowInst)
        val totalInst = obj.optInt("total_installments", 1).coerceAtLeast(1)
        val completedInst = obj.optInt("completed_installments", 0).coerceAtLeast(0)
        val intervalDays = obj.optInt("installment_interval_days", 7).coerceAtLeast(1)
        val remainingInst = (totalInst - completedInst).coerceAtLeast(1)
        val nextInstAmt = obj.optDouble("next_installment_amount", 0.0).let {
            if (it.isNaN() || it <= 0.0) {
                if (dueAmount > 0.0) kotlin.math.round(dueAmount / remainingInst) else 0.0
            } else it
        }

        return CourseCommerceItemState(
            enrollmentId = obj.optCleanString("id"),
            programId = progId,
            programTitle = obj.optCleanString("program_title").ifBlank { obj.optCleanString("course_title") },
            accessState = mappedState,
            totalPrice = totalPrice,
            paidAmount = paidAmount,
            dueAmount = dueAmount,
            allowInstallments = allowInst,
            installmentPlans = plans,
            activePlanId = obj.optCleanString("active_plan_id").ifBlank { obj.optCleanString("selected_plan_id") },
            activePlanTitle = obj.optCleanString("active_plan_title").ifBlank { obj.optCleanString("selected_plan_title") },
            totalInstallments = totalInst,
            completedInstallments = completedInst,
            installmentIntervalDays = intervalDays,
            nextInstallmentAmount = nextInstAmt,
            nextDueDateIso = nextDueIso,
            nextDueDateMillis = nextDueMillis,
            lastTransactionId = obj.optCleanString("last_transaction_id"),
            lastPaymentAmount = obj.optDouble("last_payment_amount", 0.0).let { if (it.isNaN()) 0.0 else it },
            lastSenderMobile = obj.optCleanString("last_sender_mobile"),
            lastPaymentMethod = obj.optCleanString("last_payment_method", "bKash"),
            pendingInstallmentTxId = obj.optCleanString("pending_installment_tx_id"),
            pendingInstallmentAmount = obj.optDouble("pending_installment_amount", 0.0).let { if (it.isNaN()) 0.0 else it },
            rejectionReason = obj.optCleanString("rejection_reason"),
            adminNote = obj.optCleanString("admin_note"),
            trialEnabled = trialEnabled,
            trialDays = effectiveTrialDays,
            trialHours = rawTrialHours,
            trialStartedAtIso = trialStartedIso,
            trialEndsAtIso = trialEndsIso,
            trialEndsAtMillis = trialEndsMillis,
            isTrialUsed = isTrialUsed
        )
    }

    private fun parseDynamicPaymentMethods(
        settingsObj: JSONObject?,
        separateMethodsArr: JSONArray?
    ): List<PaymentMethodOption> {
        val result = mutableListOf<PaymentMethodOption>()
        val explicitlyDisabledCodes = mutableSetOf<String>()
        val addedCodes = mutableSetOf<String>()

        fun parseMethodArray(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val mObj = arr.optJSONObject(i) ?: continue
                val rawCode = mObj.optString("code", mObj.optString("method_code", mObj.optString("id", ""))).trim()
                val rawName = mObj.optString("name_bn", mObj.optString("name", mObj.optString("title_bn", mObj.optString("title", rawCode)))).trim()
                val codeKey = rawCode.ifBlank { rawName }.lowercase()

                val isEnabled = when {
                    mObj.has("is_enabled") -> mObj.optBoolean("is_enabled", true)
                    mObj.has("enabled") -> mObj.optBoolean("enabled", true)
                    mObj.has("is_active") -> mObj.optBoolean("is_active", true)
                    mObj.has("active") -> mObj.optBoolean("active", true)
                    else -> true
                }
                if (!isEnabled) {
                    if (codeKey.isNotBlank()) explicitlyDisabledCodes.add(codeKey)
                    continue
                }

                val number = listOf(
                    mObj.optString("account_number", ""),
                    mObj.optString("number", ""),
                    mObj.optString("wallet_number", ""),
                    mObj.optString("phone", ""),
                    mObj.optString("account_no", "")
                ).firstOrNull { isRealPaymentNumber(it) }?.trim() ?: continue

                val displayCode = rawCode.ifBlank { rawName }
                val displayName = rawName.ifBlank { displayCode }
                val accountType = mObj.optString("account_type", mObj.optString("type", mObj.optString("mode", "Send Money করুন"))).trim()
                    .ifBlank { "Send Money করুন" }
                val instruction = mObj.optString("instruction_bn", mObj.optString("instruction", mObj.optString("note", ""))).trim()
                    .takeIf { !it.equals("null", true) } ?: ""
                val colorHex = mObj.optString("color_hex", mObj.optString("color", "")).trim()
                    .takeIf { !it.equals("null", true) } ?: ""

                if (codeKey.isNotBlank()) addedCodes.add(codeKey)
                result.add(
                    PaymentMethodOption(
                        id = mObj.optString("id", displayCode.lowercase()),
                        code = displayCode,
                        nameBn = displayName,
                        accountNumber = number,
                        accountType = accountType,
                        instructionBn = instruction,
                        colorHex = colorHex,
                        isEnabled = true
                    )
                )
            }
        }

        // 1. Parse from separate `course_payment_methods` table array if present
        parseMethodArray(separateMethodsArr)

        // 2. Parse from `payment_methods_json` / `payment_methods` / `methods_json` inside `course_payment_settings`
        if (settingsObj != null) {
            val rawMethodsAny = settingsObj.opt("payment_methods_json")
                ?: settingsObj.opt("payment_methods")
                ?: settingsObj.opt("methods_json")
            val jsonArr = when (rawMethodsAny) {
                is JSONArray -> rawMethodsAny
                is String -> if (rawMethodsAny.trim().startsWith("[")) runCatching { JSONArray(rawMethodsAny.trim()) }.getOrNull() else null
                else -> null
            }
            parseMethodArray(jsonArr)

            // 3. Also check individual columns (`bkash_number`, `nagad_number`, `rocket_number`, `upay_number`, `cellfin_number`)
            // ONLY if no custom methods array was provided OR for methods not already handled in the JSON array.
            // Dummy numbers like 01700000000 / 01800000000 are strictly ignored!
            val hasExplicitJsonList = jsonArr != null && jsonArr.length() > 0
            if (!hasExplicitJsonList) {
                val standardColumns = listOf(
                    Triple("bkash", "bKash", "বিকাশ") to Pair("bkash_number", "bkash_enabled"),
                    Triple("nagad", "Nagad", "নগদ") to Pair("nagad_number", "nagad_enabled"),
                    Triple("rocket", "Rocket", "রকেট") to Pair("rocket_number", "rocket_enabled"),
                    Triple("upay", "Upay", "উপায়") to Pair("upay_number", "upay_enabled"),
                    Triple("cellfin", "CellFin", "সেলফিন") to Pair("cellfin_number", "cellfin_enabled")
                )
                for ((meta, colPair) in standardColumns) {
                    val (idKey, codeVal, nameBnVal) = meta
                    val (numCol, enabledCol) = colPair
                    if (idKey in addedCodes || idKey in explicitlyDisabledCodes) continue
                    val colEnabled = if (settingsObj.has(enabledCol)) settingsObj.optBoolean(enabledCol, true) else true
                    val rawNum = settingsObj.optString(numCol, "").trim()
                    if (colEnabled && isRealPaymentNumber(rawNum)) {
                        addedCodes.add(idKey)
                        result.add(
                            PaymentMethodOption(
                                id = idKey,
                                code = codeVal,
                                nameBn = nameBnVal,
                                accountNumber = rawNum,
                                accountType = "Send Money করুন",
                                isEnabled = true
                            )
                        )
                    }
                }
            }
        }

        return result
    }

    private fun parseSnapshotJson(root: JSONObject): CourseCommerceSnapshot {
        val settingsObj = root.optJSONObject("payment_settings")
        val separateMethodsArr = root.optJSONArray("payment_methods")
        val enabledMethods = parseDynamicPaymentMethods(settingsObj, separateMethodsArr)

        val settings = if (settingsObj != null) {
            val rawBkash = settingsObj.optCleanString("bkash_number").takeIf { isRealPaymentNumber(it) } ?: ""
            val rawNagad = settingsObj.optCleanString("nagad_number").takeIf { isRealPaymentNumber(it) } ?: ""
            val rawRocket = settingsObj.optCleanString("rocket_number").takeIf { isRealPaymentNumber(it) } ?: ""
            val rawUpay = settingsObj.optCleanString("upay_number").takeIf { isRealPaymentNumber(it) } ?: ""
            val resolvedInstruction = settingsObj.optCleanString("payment_instruction_bn")
                .ifBlank { settingsObj.optCleanString("payment_instructions_bn") }
                .ifBlank { "উপরের নম্বরে টাকা পাঠিয়ে নিচের ফর্মে আপনার মোবাইল নম্বর, পাঠানো টাকার পরিমাণ এবং Transaction ID (TrxID) সঠিকভাবে প্রদান করুন।" }
            AdminPaymentSettings(
                bkashNumber = rawBkash,
                nagadNumber = rawNagad,
                rocketNumber = rawRocket,
                upayNumber = rawUpay,
                instructionBn = resolvedInstruction,
                enabledMethods = enabledMethods
            )
        } else {
            AdminPaymentSettings(enabledMethods = enabledMethods)
        }

        val map = mutableMapOf<String, CourseCommerceItemState>()
        val txByEnrollmentId = mutableMapOf<String, JSONObject>()
        val txByProgramId = mutableMapOf<String, JSONObject>()
        val txArr = root.optJSONArray("transactions")
        if (txArr != null) {
            for (i in 0 until txArr.length()) {
                val txObj = txArr.optJSONObject(i) ?: continue
                if (txObj.optString("status", "").equals("PENDING", ignoreCase = true)) {
                    val enrId = txObj.optString("enrollment_id", "").trim()
                    val progId = txObj.optString("program_id", "").trim()
                    if (enrId.isNotBlank() && !txByEnrollmentId.containsKey(enrId)) {
                        txByEnrollmentId[enrId] = txObj
                    }
                    if (progId.isNotBlank() && !txByProgramId.containsKey(progId)) {
                        txByProgramId[progId] = txObj
                    }
                }
            }
        }

        val enrArr = root.optJSONArray("enrollments")
        if (enrArr != null) {
            for (i in 0 until enrArr.length()) {
                val obj = enrArr.optJSONObject(i) ?: continue
                var parsed = parseEnrollmentItem(obj) ?: continue
                val pendingTx = txByEnrollmentId[parsed.enrollmentId] ?: txByProgramId[parsed.programId]
                if (pendingTx != null && parsed.pendingInstallmentTxId.isBlank()) {
                    parsed = parsed.copy(
                        pendingInstallmentTxId = pendingTx.optString("transaction_id", ""),
                        pendingInstallmentAmount = pendingTx.optDouble("amount", 0.0).let { if (it.isNaN()) 0.0 else it }
                    )
                }
                val existing = map[parsed.programId]
                if (existing == null || statePriority(parsed.effectiveState) >= statePriority(existing.effectiveState)) {
                    map[parsed.programId] = parsed
                }
            }
        }
        return CourseCommerceSnapshot(
            paymentSettings = settings,
            enrollmentsByProgramId = map
        )
    }

    private fun statePriority(state: CourseAccessState): Int = when (state) {
        CourseAccessState.ACTIVE -> 7
        CourseAccessState.PAYMENT_PENDING -> 6
        CourseAccessState.INSTALLMENT_OVERDUE -> 5
        CourseAccessState.APPROVED_TO_BUY -> 4
        CourseAccessState.PAYMENT_REJECTED -> 3
        CourseAccessState.ACCESS_REQUESTED -> 2
        CourseAccessState.LOCKED_BLURRED -> 1
    }

    private data class StudentAccountRef(
        val normPhone: String,
        val rawPhone: String,
        val userId: String,
        val accountKey: String,
        val studentName: String,
        val cacheKey: String
    ) {
        val isLoggedIn: Boolean
            get() = normPhone.isNotBlank() || userId.isNotBlank()
    }

    private fun resolveStudentAccount(context: Context): StudentAccountRef {
        val session = SessionManager(context)
        val rawPhone = (session.getUserPhone() ?: "").trim()
        val normPhone = session.getNormalizedUserPhone()
        val userId = (session.getUserId() ?: "").trim()
        // Primary key is strictly the student's 11-digit phone number (or Shikho userId if phone not loaded yet)
        val accountKey = normPhone.ifBlank { userId }
        val studentName = session.getUserFullName() ?: "শিক্ষার্থী"
        val cacheKey = "${normPhone}_${userId}"
        return StudentAccountRef(
            normPhone = normPhone,
            rawPhone = rawPhone,
            userId = userId,
            accountKey = accountKey,
            studentName = studentName,
            cacheKey = cacheKey
        )
    }

    /**
     * Strictly matches a row in `course_access_enrollments` or `course_payment_transactions`
     * against the logged-in student's Phone Number (`01XXXXXXXXX`) and/or Shikho Account ID (`userId`).
     * NEVER matches by physical `device_id`, ensuring:
     * 1. Logging in with the same phone number on ANY device unlocks all their courses & installments.
     * 2. Logging in with a different phone number on the SAME device never leaks another account's courses.
     */
    private fun doesRowBelongToStudent(item: JSONObject, account: StudentAccountRef): Boolean {
        if (!account.isLoggedIn) return false

        val rowStudentPhone = SessionManager.normalizePhone(item.optString("student_phone", ""))
        val rowUserPhone = SessionManager.normalizePhone(item.optString("user_phone", ""))
        val rowAccountKeyRaw = item.optString("shikho_account_key", "").trim()
        val rowStudentKeyRaw = item.optString("student_key", "").trim()
        val rowUserIdRaw = item.optString("shikho_user_id", "").trim()
        val rowDeviceIdRaw = item.optString("device_id", "").trim()

        val rowAccountPhone = SessionManager.normalizePhone(rowAccountKeyRaw)
        val rowStudentKeyPhone = SessionManager.normalizePhone(rowStudentKeyRaw)
        val rowUserIdPhone = SessionManager.normalizePhone(rowUserIdRaw)
        val rowDevicePhone = SessionManager.normalizePhone(rowDeviceIdRaw)

        // 1. Match by canonical 11-digit phone number ("01XXXXXXXXX")
        if (account.normPhone.isNotBlank()) {
            if (rowStudentPhone == account.normPhone ||
                rowUserPhone == account.normPhone ||
                rowAccountPhone == account.normPhone ||
                rowStudentKeyPhone == account.normPhone ||
                rowUserIdPhone == account.normPhone ||
                rowDevicePhone == account.normPhone
            ) {
                return true
            }
        }

        // 2. Match by Shikho user_id (only if non-blank and not "guest")
        if (account.userId.isNotBlank() && !account.userId.equals("guest", ignoreCase = true)) {
            val rowHasDifferentPhone = (rowStudentPhone.isNotBlank() && account.normPhone.isNotBlank() && rowStudentPhone != account.normPhone) ||
                (rowUserPhone.isNotBlank() && account.normPhone.isNotBlank() && rowUserPhone != account.normPhone)
            if (!rowHasDifferentPhone) {
                if (rowUserIdRaw.equals(account.userId, ignoreCase = true) ||
                    rowAccountKeyRaw.equals(account.userId, ignoreCase = true) ||
                    rowStudentKeyRaw.equals(account.userId, ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        return false
    }

    fun clearCache(context: Context) {
        try {
            getPrefs(context).edit()
                .remove(KEY_CACHED_SNAPSHOT_JSON)
                .remove(KEY_CACHED_FOR_UID)
                .apply()
        } catch (_: Throwable) {}
    }

    fun getCachedSnapshot(context: Context): CourseCommerceSnapshot {
        return try {
            val account = resolveStudentAccount(context)
            if (!account.isLoggedIn) return CourseCommerceSnapshot()

            val prefs = getPrefs(context)
            val cachedUid = prefs.getString(KEY_CACHED_FOR_UID, "") ?: ""
            if (cachedUid.isBlank() || !account.cacheKey.equals(cachedUid, ignoreCase = true)) {
                return CourseCommerceSnapshot()
            }
            val raw = prefs.getString(KEY_CACHED_SNAPSHOT_JSON, null) ?: return CourseCommerceSnapshot()
            parseSnapshotJson(JSONObject(raw))
        } catch (_: Throwable) {
            CourseCommerceSnapshot()
        }
    }

    private fun saveCachedSnapshot(context: Context, jsonStr: String, cacheKey: String) {
        try {
            getPrefs(context).edit()
                .putString(KEY_CACHED_SNAPSHOT_JSON, jsonStr)
                .putString(KEY_CACHED_FOR_UID, cacheKey)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun markOptimisticCourseRequested(context: Context, programId: String, programTitle: String) {
        try {
            val account = resolveStudentAccount(context)
            if (!account.isLoggedIn) return
            val prefs = getPrefs(context)
            val cachedUid = prefs.getString(KEY_CACHED_FOR_UID, "") ?: ""
            val raw = if (cachedUid == account.cacheKey) prefs.getString(KEY_CACHED_SNAPSHOT_JSON, null) else null
            val root = if (!raw.isNullOrBlank() && raw.trim().startsWith("{")) JSONObject(raw) else JSONObject()
            val enrArr = root.optJSONArray("enrollments") ?: JSONArray()
            var found = false
            for (i in 0 until enrArr.length()) {
                val obj = enrArr.optJSONObject(i) ?: continue
                if (obj.optString("program_id") == programId) {
                    obj.put("status", "REQUESTED")
                    obj.put("access_status", "REQUESTED")
                    obj.put("paid_amount", 0)
                    obj.put("trial_enabled", false)
                    obj.remove("trial_ends_at")
                    obj.remove("trial_expires_at")
                    obj.remove("approved_at")
                    obj.remove("unlocked_at")
                    found = true
                    break
                }
            }
            if (!found) {
                enrArr.put(
                    JSONObject().apply {
                        put("program_id", programId)
                        put("program_title", programTitle)
                        put("student_phone", account.normPhone)
                        put("user_phone", account.normPhone)
                        put("shikho_account_key", account.accountKey)
                        put("shikho_user_id", account.userId.ifBlank { account.accountKey })
                        put("status", "REQUESTED")
                    }
                )
            }
            root.put("enrollments", enrArr)
            saveCachedSnapshot(context, root.toString(), account.cacheKey)
        } catch (_: Throwable) {}
    }

    /**
     * Fetches the student's real-time course access requests, custom pricing, installment plans,
     * payment verification status, rejection reasons, and overdue locks from Supabase.
     * Strictly scoped by the student's Phone Number (`01XXXXXXXXX`) and Shikho Account ID (`userId`)
     * so it works across ANY device where the student logs in with their phone number!
     */
    suspend fun fetchCourseCommerceStatus(context: Context): CourseCommerceSnapshot = withContext(Dispatchers.IO) {
        try {
            val account = resolveStudentAccount(context)
            if (!account.isLoggedIn) {
                return@withContext CourseCommerceSnapshot()
            }

            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext getCachedSnapshot(context)
            }

            val rootJson = JSONObject()

            fun enrichWithLivePaymentSettings(targetJson: JSONObject) {
                try {
                    val settingsReq = Request.Builder()
                        .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_payment_settings?select=*&limit=1")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .get()
                        .build()
                    httpClient.newCall(settingsReq).execute().use { settingsResp ->
                        if (settingsResp.isSuccessful) {
                            val arr = JSONArray(settingsResp.body?.string() ?: "[]")
                            if (arr.length() > 0) {
                                targetJson.put("payment_settings", arr.getJSONObject(0))
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            enrichWithLivePaymentSettings(rootJson)

            val matchedEnrollments = JSONArray()
            val matchedTransactions = JSONArray()
            val seenEnrollmentIds = mutableSetOf<String>()
            val seenTxIds = mutableSetOf<String>()

            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            var restFetchSucceeded = false
            var rpcCallSucceeded = false
            var rpcNotFound = false

            // 1. Call exact Supabase SECURITY DEFINER RPC `get_student_course_commerce_status` (4-arg live signature)
            val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_student_course_commerce_status"
            val candidateRpcBodies = listOf(
                JSONObject().apply {
                    put("p_device_id", deviceHardwareHash)
                    put("p_shikho_account_key", account.accountKey)
                    put("p_shikho_user_id", account.userId.ifBlank { account.accountKey })
                    put("p_user_phone", account.normPhone)
                }
            )

            fun sanitizeAndAddEnrollmentRow(item: JSONObject) {
                if (!doesRowBelongToStudent(item, account)) return
                val rowId = item.optString("id", "").trim()
                if (rowId.isNotBlank() && !seenEnrollmentIds.add(rowId)) return

                val rStat = item.optString("status", "").ifBlank { item.optString("access_status", "") }.trim().uppercase()
                val rPaid = item.optDouble("paid_amount", 0.0).let { if (it.isNaN()) 0.0 else it }
                val rReqMs = parseIsoTimestampMillis(item.optString("requested_at", "").takeIf { it.isNotBlank() && !it.equals("null", true) })
                val rAppMs = parseIsoTimestampMillis(item.optString("approved_at", "").takeIf { it.isNotBlank() && !it.equals("null", true) })
                val rUnlMs = parseIsoTimestampMillis(item.optString("unlocked_at", "").takeIf { it.isNotBlank() && !it.equals("null", true) })
                val rTrStartMs = parseIsoTimestampMillis(item.optString("trial_started_at", "").takeIf { it.isNotBlank() && !it.equals("null", true) })
                val rTrEndMs = parseIsoTimestampMillis(
                    item.optString("trial_ends_at", "").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        ?: item.optString("trial_expires_at", "").takeIf { it.isNotBlank() && !it.equals("null", true) }
                )
                val latestAdminMs = maxOf(rAppMs ?: 0L, rUnlMs ?: 0L, rTrStartMs ?: 0L)
                val needsStatusResetToRequested = (
                    rPaid <= 0.0 && rReqMs != null && latestAdminMs > 0L && rReqMs > latestAdminMs + 2000L &&
                    rStat in listOf("ACTIVE", "TRIAL_ACTIVE", "APPROVED_TO_BUY", "APPROVED_UNPAID")
                ) || (
                    rStat in listOf("ACTIVE", "TRIAL_ACTIVE", "APPROVED_TO_BUY", "APPROVED_UNPAID") &&
                    rPaid <= 0.0 && rUnlMs == null && rAppMs == null &&
                    (rTrEndMs == null || currentTrustedTimeMillis() >= rTrEndMs)
                )

                if (needsStatusResetToRequested) {
                    item.put("status", "REQUESTED")
                    item.put("access_status", "REQUESTED")
                }

                matchedEnrollments.put(item)
            }

            for (rpcBody in candidateRpcBodies) {
                try {
                    val req = Request.Builder()
                        .url(rpcUrl)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .post(rpcBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            if (bodyStr.trim().startsWith("{")) {
                                val json = JSONObject(bodyStr)
                                rpcCallSucceeded = true
                                restFetchSucceeded = true
                                if (!rootJson.has("payment_settings") && json.optJSONObject("payment_settings") != null) {
                                    rootJson.put("payment_settings", json.optJSONObject("payment_settings"))
                                }
                                val enrArr = json.optJSONArray("enrollments")
                                if (enrArr != null) {
                                    for (i in 0 until enrArr.length()) {
                                        val item = enrArr.optJSONObject(i) ?: continue
                                        sanitizeAndAddEnrollmentRow(item)
                                    }
                                }
                                val txArr = json.optJSONArray("transactions")
                                if (txArr != null) {
                                    for (i in 0 until txArr.length()) {
                                        val txItem = txArr.optJSONObject(i) ?: continue
                                        val enrId = txItem.optString("enrollment_id", "").trim()
                                        if (doesRowBelongToStudent(txItem, account) || (enrId.isNotBlank() && enrId in seenEnrollmentIds)) {
                                            val txRowId = txItem.optString("id", "").trim()
                                            if (txRowId.isBlank() || seenTxIds.add(txRowId)) {
                                                matchedTransactions.put(txItem)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (resp.code == 404) {
                            rpcNotFound = true
                        }
                    }
                    if (rpcCallSucceeded) break
                } catch (_: Throwable) {}
            }

            // 2. Legacy Fallback ONLY when RPC function does not exist on server (HTTP 404).
            //    Never hit locked tables directly under Zero-Trust RLS (prevents 401 / 42501 permission denied logs).
            if (!rpcCallSucceeded && rpcNotFound) {
                val orClauses = mutableListOf<String>()
                if (deviceHardwareHash.isNotBlank()) {
                    val encHash = android.net.Uri.encode(deviceHardwareHash)
                    orClauses.add("device_id.eq.$encHash")
                    orClauses.add("device_hash.eq.$encHash")
                }
                if (displayDeviceId.isNotBlank()) {
                    val encDisplay = android.net.Uri.encode(displayDeviceId)
                    orClauses.add("display_device_id.eq.$encDisplay")
                }
                if (account.normPhone.isNotBlank()) {
                    val encPhone = android.net.Uri.encode(account.normPhone)
                    orClauses.add("student_phone.eq.$encPhone")
                    orClauses.add("user_phone.eq.$encPhone")
                    orClauses.add("student_key.eq.$encPhone")
                    orClauses.add("shikho_account_key.eq.$encPhone")
                    orClauses.add("device_id.eq.$encPhone")
                }
                if (account.accountKey.isNotBlank()) {
                    val encKey = android.net.Uri.encode(account.accountKey)
                    orClauses.add("shikho_account_key.eq.$encKey")
                    orClauses.add("student_key.eq.$encKey")
                    orClauses.add("device_id.eq.$encKey")
                }
                if (account.userId.isNotBlank()) {
                    val encUid = android.net.Uri.encode(account.userId)
                    orClauses.add("shikho_user_id.eq.$encUid")
                    orClauses.add("shikho_account_key.eq.$encUid")
                }
                val studentOrFilter = if (orClauses.isNotEmpty()) {
                    "&or=(${orClauses.distinct().joinToString(",")})"
                } else ""

                if (studentOrFilter.isNotEmpty()) {
                    try {
                        val enrUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?select=*${studentOrFilter}&order=updated_at.desc&limit=50"
                        val enrReq = Request.Builder()
                            .url(enrUrl)
                            .addHeader("apikey", anonKey)
                            .addHeader("Authorization", "Bearer $anonKey")
                            .get()
                            .build()
                        httpClient.newCall(enrReq).execute().use { enrResp ->
                            if (enrResp.isSuccessful) {
                                restFetchSucceeded = true
                                val rawArr = JSONArray(enrResp.body?.string() ?: "[]")
                                for (i in 0 until rawArr.length()) {
                                    val item = rawArr.optJSONObject(i) ?: continue
                                    sanitizeAndAddEnrollmentRow(item)
                                }
                            }
                        }
                    } catch (_: Throwable) {}

                    val txOrClauses = mutableListOf<String>()
                    if (deviceHardwareHash.isNotBlank()) {
                        val encHash = android.net.Uri.encode(deviceHardwareHash)
                        txOrClauses.add("device_id.eq.$encHash")
                        txOrClauses.add("device_hash.eq.$encHash")
                    }
                    if (account.normPhone.isNotBlank()) {
                        val encPhone = android.net.Uri.encode(account.normPhone)
                        txOrClauses.add("user_phone.eq.$encPhone")
                        txOrClauses.add("student_phone.eq.$encPhone")
                        txOrClauses.add("shikho_account_key.eq.$encPhone")
                        txOrClauses.add("device_id.eq.$encPhone")
                    }
                    if (account.accountKey.isNotBlank()) {
                        val encKey = android.net.Uri.encode(account.accountKey)
                        txOrClauses.add("shikho_account_key.eq.$encKey")
                        txOrClauses.add("device_id.eq.$encKey")
                    }
                    if (account.userId.isNotBlank()) {
                        val encUid = android.net.Uri.encode(account.userId)
                        txOrClauses.add("shikho_user_id.eq.$encUid")
                    }
                    val txOrFilter = if (txOrClauses.isNotEmpty()) {
                        "&or=(${txOrClauses.distinct().joinToString(",")})"
                    } else ""

                    try {
                        val txUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/course_payment_transactions?select=*${txOrFilter}&order=id.desc&limit=50"
                        val txReq = Request.Builder()
                            .url(txUrl)
                            .addHeader("apikey", anonKey)
                            .addHeader("Authorization", "Bearer $anonKey")
                            .get()
                            .build()
                        httpClient.newCall(txReq).execute().use { txResp ->
                            if (txResp.isSuccessful) {
                                val rawTxArr = JSONArray(txResp.body?.string() ?: "[]")
                                for (i in 0 until rawTxArr.length()) {
                                    val txItem = rawTxArr.optJSONObject(i) ?: continue
                                    val enrId = txItem.optString("enrollment_id", "").trim()
                                    if (doesRowBelongToStudent(txItem, account) || (enrId.isNotBlank() && enrId in seenEnrollmentIds)) {
                                        val txRowId = txItem.optString("id", "").trim()
                                        if (txRowId.isBlank() || seenTxIds.add(txRowId)) {
                                            matchedTransactions.put(txItem)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }

            if (restFetchSucceeded) {
                rootJson.put("enrollments", matchedEnrollments)
                rootJson.put("transactions", matchedTransactions)
                saveCachedSnapshot(context, rootJson.toString(), account.cacheKey)
                return@withContext parseSnapshotJson(rootJson)
            }

            getCachedSnapshot(context)
        } catch (e: Throwable) {
            Log.e(TAG, "fetchCourseCommerceStatus error: ${e.message}")
            getCachedSnapshot(context)
        }
    }

    /**
     * Resolves an existing enrollment row in `course_access_enrollments` for the student + programId
     * across any historical `device_id` / `shikho_account_key` so we never hit duplicate key 23505.
     */
    private fun findExistingEnrollmentForStudent(
        supabaseUrl: String,
        anonKey: String,
        account: StudentAccountRef,
        programId: String
    ): JSONObject? {
        return try {
            val orClauses = mutableListOf<String>()
            if (account.normPhone.isNotBlank()) {
                val encPhone = android.net.Uri.encode(account.normPhone)
                orClauses.add("student_phone.eq.$encPhone")
                orClauses.add("user_phone.eq.$encPhone")
                orClauses.add("shikho_account_key.eq.$encPhone")
                orClauses.add("device_id.eq.$encPhone")
            }
            if (account.accountKey.isNotBlank()) {
                val encKey = android.net.Uri.encode(account.accountKey)
                orClauses.add("shikho_account_key.eq.$encKey")
                orClauses.add("device_id.eq.$encKey")
            }
            if (account.userId.isNotBlank()) {
                val encUid = android.net.Uri.encode(account.userId)
                orClauses.add("shikho_user_id.eq.$encUid")
                orClauses.add("shikho_account_key.eq.$encUid")
            }
            val filterPart = if (orClauses.isNotEmpty()) "&or=(${orClauses.distinct().joinToString(",")})" else ""
            val encProgramId = android.net.Uri.encode(programId)
            val url = "${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?program_id=eq.$encProgramId${filterPart}&select=*&order=updated_at.desc&limit=10"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val arr = JSONArray(resp.body?.string() ?: "[]")
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (doesRowBelongToStudent(obj, account)) {
                        return obj
                    }
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Sends a "কোর্সে এক্সেস পান" request to the Admin App keyed strictly by the student's Phone Number (`01XXXXXXXXX`)
     * and Shikho Account ID (`userId`) so it works across any device!
     */
    suspend fun requestCoursePurchaseAccess(
        context: Context,
        programId: String,
        programTitle: String,
        classCode: String?,
        groupCode: String?,
        batchId: String?,
        bannerUrl: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = resolveStudentAccount(context)
            if (!account.isLoggedIn) {
                return@withContext Result.failure(Exception("অনুগ্রহ করে আগে আপনার শিখো একাউন্টে লগইন করুন।"))
            }

            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext Result.failure(Exception("সার্ভার কনফিগারেশন পাওয়া যায়নি।"))
            }

            val accountKey = account.accountKey
            val normPhone = account.normPhone.ifBlank { account.rawPhone }
            val userId = account.userId.ifBlank { accountKey }
            val studentName = account.studentName
            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val oneSignalPlayerId = com.example.notification.OneSignalPushManager.getSavedPlayerId(context)
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            // 1. Primary (Zero-Trust RLS compatible): Call Supabase SECURITY DEFINER RPC `request_course_purchase_access`
            val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/request_course_purchase_access"
            val rpcBody = JSONObject().apply {
                put("p_device_id", deviceHardwareHash)
                put("p_shikho_account_key", accountKey)
                put("p_student_name", studentName)
                put("p_student_phone", normPhone)
                put("p_student_class", classCode ?: "")
                put("p_student_group", groupCode ?: "")
                put("p_program_id", programId)
                put("p_program_title", programTitle)
            }

            var rpcNotFound = false
            var rpcHttpCode = 0
            try {
                val req = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    rpcHttpCode = resp.code
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("{")) {
                            val json = JSONObject(bodyStr)
                            val explicitFailure = json.has("success") && !json.optBoolean("success", true)
                            if (!explicitFailure && (json.optBoolean("success", false) || json.optString("status", "").isNotBlank())) {
                                val msg = json.optString(
                                    "message",
                                    "অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন দাম সেট করে এক্সেস দিলে এখানে দেখতে পাবেন।"
                                )
                                markOptimisticCourseRequested(context, programId, programTitle)
                                fetchCourseCommerceStatus(context)
                                com.example.notification.OneSignalPushManager.notifyAdminCourseAccessRequest(
                                    context = context,
                                    studentName = studentName,
                                    studentPhone = normPhone,
                                    programTitle = programTitle,
                                    programId = programId
                                )
                                return@withContext Result.success(msg)
                            } else {
                                val errMsg = json.optString("message", "").ifBlank { "রিকোয়েস্ট পাঠানো যায়নি।" }
                                return@withContext Result.failure(Exception(errMsg))
                            }
                        }
                    } else if (resp.code == 404) {
                        rpcNotFound = true
                    }
                }
            } catch (_: Throwable) {}

            if (!rpcNotFound) {
                return@withContext Result.failure(
                    Exception(
                        if (rpcHttpCode > 0) "রিকোয়েস্ট পাঠানো যায়নি (HTTP $rpcHttpCode)।"
                        else "নেটওয়ার্ক ত্রুটি: অনুগ্রহ করে আবার চেষ্টা করুন।"
                    )
                )
            }

            // 2. Legacy Fallback ONLY if RPC does not exist on server (HTTP 404)
            val existingRow = findExistingEnrollmentForStudent(supabaseUrl, anonKey, account, programId)
            if (existingRow != null) {
                val existingId = existingRow.optString("id", "").trim()
                if (existingId.isNotBlank()) {
                    val strictResetObj = JSONObject().apply {
                        put("device_id", deviceHardwareHash)
                        put("device_hash", deviceHardwareHash)
                        put("display_device_id", displayDeviceId)
                        put("shikho_account_key", accountKey)
                        put("student_key", accountKey)
                        put("shikho_user_id", userId)
                        put("student_name", studentName)
                        put("student_phone", normPhone)
                        put("user_phone", normPhone)
                        if (oneSignalPlayerId.isNotBlank()) put("onesignal_player_id", oneSignalPlayerId)
                        put("student_class", classCode ?: "")
                        put("student_group", groupCode ?: "")
                        put("program_title", programTitle)
                        put("class_code", classCode ?: "")
                        put("group_code", groupCode ?: "")
                        put("batch_id", batchId ?: "")
                        put("banner_url", bannerUrl ?: "")
                        put("status", "REQUESTED")
                        put("access_status", "REQUESTED")
                        put("requested_at", nowIso)
                        put("updated_at", nowIso)
                    }
                    val patchReq = Request.Builder()
                        .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?id=eq.$existingId")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .patch(strictResetObj.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val patchOk = runCatching { httpClient.newCall(patchReq).execute().use { it.isSuccessful } }.getOrDefault(false)
                    if (patchOk) {
                        markOptimisticCourseRequested(context, programId, programTitle)
                        fetchCourseCommerceStatus(context)
                        com.example.notification.OneSignalPushManager.notifyAdminCourseAccessRequest(
                            context = context,
                            studentName = studentName,
                            studentPhone = normPhone,
                            programTitle = programTitle,
                            programId = programId
                        )
                        return@withContext Result.success("অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন দাম সেট করে এক্সেস দিলে এখানে দেখতে পাবেন।")
                    }
                }
            }

            // 3. Direct REST Fallback into `course_access_enrollments` with all live NOT NULL columns populated
            val fullEnrollmentPayload = JSONObject().apply {
                put("device_id", deviceHardwareHash)
                put("device_hash", deviceHardwareHash)
                put("display_device_id", displayDeviceId)
                put("shikho_account_key", accountKey)
                put("student_key", accountKey)
                put("shikho_user_id", userId)
                put("student_name", studentName)
                put("student_phone", normPhone)
                put("user_phone", normPhone)
                if (oneSignalPlayerId.isNotBlank()) put("onesignal_player_id", oneSignalPlayerId)
                put("student_class", classCode ?: "")
                put("student_group", groupCode ?: "")
                put("program_id", programId)
                put("course_id", programId)
                put("program_title", programTitle)
                put("course_title", programTitle)
                put("class_code", classCode ?: "")
                put("group_code", groupCode ?: "")
                put("batch_id", batchId ?: "")
                put("banner_url", bannerUrl ?: "")
                put("status", "REQUESTED")
                put("access_status", "REQUESTED")
                put("rejection_reason", "")
                put("admin_note", "")
                put("requested_at", nowIso)
                put("updated_at", nowIso)
            }

            val upsertUrls = listOf(
                "${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?on_conflict=device_id,shikho_account_key,program_id",
                "${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?on_conflict=shikho_account_key,program_id"
            )

            var lastStatusCode = 400
            var lastErrorBody = ""
            for (url in upsertUrls) {
                try {
                    val upsertReq = Request.Builder()
                        .url(url)
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(fullEnrollmentPayload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(upsertReq).execute().use { upsertResp ->
                        lastStatusCode = upsertResp.code
                        if (upsertResp.isSuccessful) {
                            markOptimisticCourseRequested(context, programId, programTitle)
                            fetchCourseCommerceStatus(context)
                            com.example.notification.OneSignalPushManager.notifyAdminCourseAccessRequest(
                                context = context,
                                studentName = studentName,
                                studentPhone = normPhone,
                                programTitle = programTitle,
                                programId = programId
                            )
                            return@withContext Result.success("অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন দাম সেট করে এক্সেস দিলে এখানে দেখতে পাবেন।")
                        } else {
                            lastErrorBody = upsertResp.body?.string() ?: ""
                        }
                    }
                } catch (_: Throwable) {}
            }

            // 3. Plain POST fallback without on_conflict query param (all live NOT NULL columns populated)
            val plainPayload = JSONObject().apply {
                put("device_id", accountKey)
                put("shikho_account_key", accountKey)
                put("shikho_user_id", userId)
                put("student_phone", normPhone)
                put("user_phone", normPhone)
                put("student_name", studentName)
                put("student_class", classCode ?: "")
                put("student_group", groupCode ?: "")
                put("class_code", classCode ?: "")
                put("group_code", groupCode ?: "")
                put("batch_id", batchId ?: "")
                put("program_id", programId)
                put("program_title", programTitle)
                put("status", "REQUESTED")
                put("rejection_reason", "")
                put("admin_note", "")
                put("requested_at", nowIso)
                put("updated_at", nowIso)
            }
            try {
                val plainReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(plainPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(plainReq).execute().use { plainResp ->
                    if (plainResp.isSuccessful || plainResp.code == 409) {
                        markOptimisticCourseRequested(context, programId, programTitle)
                        fetchCourseCommerceStatus(context)
                        com.example.notification.OneSignalPushManager.notifyAdminCourseAccessRequest(
                            context = context,
                            studentName = studentName,
                            studentPhone = normPhone,
                            programTitle = programTitle,
                            programId = programId
                        )
                        return@withContext Result.success("অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন দাম সেট করে এক্সেস দিলে এখানে দেখতে পাবেন।")
                    }
                }
            } catch (_: Throwable) {}

            Log.e(TAG, "requestCoursePurchaseAccess failed ($lastStatusCode): $lastErrorBody")
            return@withContext Result.failure(Exception("রিকোয়েস্ট পাঠানো যায়নি (HTTP $lastStatusCode)।"))
        } catch (e: Throwable) {
            return@withContext Result.failure(Exception("নেটওয়ার্ক ত্রুটি: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"))
        }
    }

    /**
     * Submits a course purchase or installment payment (Sender Mobile Number, Sent Amount, Transaction ID, Plan)
     * strictly tied to the student's Phone Number (`01XXXXXXXXX`) and Shikho Account ID (`userId`).
     */
    suspend fun submitCoursePaymentTransaction(
        context: Context,
        programId: String,
        programTitle: String,
        paymentMethod: String,
        senderMobile: String,
        amount: Double,
        transactionId: String,
        selectedPlan: InstallmentPlanOption
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = resolveStudentAccount(context)
            if (!account.isLoggedIn) {
                return@withContext Result.failure(Exception("অনুগ্রহ করে আগে আপনার শিখো একাউন্টে লগইন করুন।"))
            }

            val cleanMobile = DeviceCoursePermissionsConvertDigits(senderMobile).trim()
            val cleanTrx = transactionId.trim().uppercase()
            val amountInt = amount.toInt().coerceAtLeast(1)
            if (cleanMobile.length < 10) {
                return@withContext Result.failure(Exception("যে মোবাইল নম্বর থেকে টাকা পাঠিয়েছেন তা সঠিকভাবে লিখুন (১১ ডিজিট)।"))
            }
            if (amount <= 0.0) {
                return@withContext Result.failure(Exception("পাঠানো টাকার সঠিক পরিমাণ লিখুন।"))
            }
            if (cleanTrx.length < 4) {
                return@withContext Result.failure(Exception("সঠিক ট্রানজেকশন আইডি (TrxID) লিখুন।"))
            }

            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext Result.failure(Exception("সার্ভার কনফিগারেশন পাওয়া যায়নি।"))
            }

            val accountKey = account.accountKey
            val normPhone = account.normPhone.ifBlank { account.rawPhone }
            val userId = account.userId.ifBlank { accountKey }
            val studentName = account.studentName
            val deviceHardwareHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val oneSignalPlayerId = com.example.notification.OneSignalPushManager.getSavedPlayerId(context)

            val existingItem = getCachedSnapshot(context).enrollmentsByProgramId[programId]

            // 1. Primary (Zero-Trust RLS compatible): Call exact Supabase SECURITY DEFINER RPC `submit_course_payment_transaction`
            val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/submit_course_payment_transaction"
            val rpcBody = JSONObject().apply {
                put("p_device_id", deviceHardwareHash)
                put("p_shikho_account_key", accountKey)
                put("p_student_name", studentName)
                put("p_program_id", programId)
                put("p_program_title", programTitle)
                put("p_plan_id", selectedPlan.planId)
                put("p_plan_title", selectedPlan.titleBn)
                put("p_total_installments", selectedPlan.installmentsCount)
                put("p_installment_interval_days", selectedPlan.intervalDays)
                put("p_payment_method", paymentMethod)
                put("p_sender_mobile", cleanMobile)
                put("p_amount", amountInt)
                put("p_transaction_id", cleanTrx)
            }

            var rpcNotFound = false
            var rpcHttpCode = 0
            try {
                val req = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    rpcHttpCode = resp.code
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("{")) {
                            val json = JSONObject(bodyStr)
                            if (json.optBoolean("success", false)) {
                                val msg = json.optString(
                                    "message",
                                    "আপনার পেমেন্ট তথ্য সফলভাবে জমা হয়েছে! অ্যাডমিন যাচাই করে এপ্রুভ করলেই কোর্সটি আনলক হয়ে যাবে।"
                                )
                                fetchCourseCommerceStatus(context)
                                com.example.notification.OneSignalPushManager.notifyAdminCoursePaymentSubmitted(
                                    context = context,
                                    studentName = studentName,
                                    studentPhone = normPhone,
                                    programTitle = programTitle,
                                    paymentMethod = paymentMethod,
                                    amount = amountInt,
                                    transactionId = cleanTrx,
                                    planTitle = selectedPlan.titleBn
                                )
                                return@withContext Result.success(msg)
                            } else {
                                val errMsg = json.optString("message", "").ifBlank { "পেমেন্ট জমা দিতে সমস্যা হয়েছে।" }
                                return@withContext Result.failure(Exception(errMsg))
                            }
                        }
                    } else if (resp.code == 404) {
                        rpcNotFound = true
                    }
                }
            } catch (_: Throwable) {}

            if (!rpcNotFound) {
                return@withContext Result.failure(
                    Exception(
                        if (rpcHttpCode > 0) "পেমেন্ট জমা দিতে সমস্যা হয়েছে (HTTP $rpcHttpCode)।"
                        else "নেটওয়ার্ক সমস্যা: অনুগ্রহ করে আবার চেষ্টা করুন।"
                    )
                )
            }

            // 2. Legacy Direct REST Fallback ONLY if RPC does not exist on server (HTTP 404)
            var enrollmentId = existingItem?.enrollmentId?.takeIf { it.isNotBlank() }
            if (enrollmentId == null) {
                enrollmentId = findExistingEnrollmentForStudent(supabaseUrl, anonKey, account, programId)
                    ?.optString("id", "")
                    ?.takeIf { it.isNotBlank() }
            }
            // Strictly uses ONLY columns that exist in `public.course_payment_transactions`!
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val nextInstallmentNum = (existingItem?.completedInstallments ?: 0) + 1
            val payType = if ((existingItem?.completedInstallments ?: 0) > 0) "INSTALLMENT" else "INITIAL"
            val longEnrId = enrollmentId?.toLongOrNull()

            val totalInstallmentsSafe = selectedPlan.installmentsCount.coerceAtLeast(1)
            val txVariants = listOf(
                JSONObject().apply {
                    if (longEnrId != null) put("enrollment_id", longEnrId)
                    put("device_id", deviceHardwareHash)
                    put("device_hash", deviceHardwareHash)
                    put("display_device_id", displayDeviceId)
                    put("shikho_account_key", accountKey)
                    put("shikho_user_id", userId)
                    put("user_phone", normPhone)
                    put("student_phone", normPhone)
                    put("student_name", studentName)
                    put("program_id", programId)
                    put("course_id", programId)
                    put("program_title", programTitle)
                    put("course_title", programTitle)
                    put("plan_id", selectedPlan.planId)
                    put("plan_title", selectedPlan.titleBn)
                    put("selected_plan_id", selectedPlan.planId)
                    put("selected_plan_title", selectedPlan.titleBn)
                    put("payment_type", payType)
                    put("installment_number", nextInstallmentNum)
                    put("total_installments", totalInstallmentsSafe)
                    put("interval_days", selectedPlan.intervalDays)
                    put("payment_method", paymentMethod)
                    put("sender_mobile", cleanMobile)
                    put("amount", amountInt)
                    put("transaction_id", cleanTrx)
                    put("status", "PENDING")
                    put("rejection_reason", "")
                    put("submitted_at", nowIso)
                    put("created_at", nowIso)
                },
                JSONObject().apply {
                    if (longEnrId != null) put("enrollment_id", longEnrId)
                    put("device_id", accountKey)
                    put("shikho_account_key", accountKey)
                    put("student_name", studentName)
                    put("program_id", programId)
                    put("program_title", programTitle)
                    put("plan_id", selectedPlan.planId)
                    put("plan_title", selectedPlan.titleBn)
                    put("installment_number", nextInstallmentNum)
                    put("total_installments", totalInstallmentsSafe)
                    put("payment_method", paymentMethod)
                    put("sender_mobile", cleanMobile)
                    put("amount", amountInt)
                    put("transaction_id", cleanTrx)
                    put("status", "PENDING")
                    put("rejection_reason", "")
                    put("submitted_at", nowIso)
                }
            )

            var txSucceeded = false
            var lastTxCode = 400
            for (txJson in txVariants) {
                try {
                    val txReq = Request.Builder()
                        .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_payment_transactions")
                        .addHeader("apikey", anonKey)
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("Content-Type", "application/json")
                        .post(txJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(txReq).execute().use { txResp ->
                        lastTxCode = txResp.code
                        if (txResp.isSuccessful) {
                            txSucceeded = true
                        }
                    }
                    if (txSucceeded) break
                } catch (_: Throwable) {}
            }

            if (enrollmentId != null) {
                val patchJson = JSONObject().apply {
                    if (existingItem?.accessState != CourseAccessState.ACTIVE || (existingItem.paidAmount <= 0.0)) {
                        put("status", "PAYMENT_PENDING")
                    }
                    put("selected_plan_id", selectedPlan.planId)
                    put("selected_plan_title", selectedPlan.titleBn)
                    put("active_plan_id", selectedPlan.planId)
                    put("active_plan_title", selectedPlan.titleBn)
                    if ((existingItem?.completedInstallments ?: 0) == 0) {
                        put("total_installments", selectedPlan.installmentsCount.coerceAtLeast(1))
                        put("installment_interval_days", selectedPlan.intervalDays.coerceAtLeast(1))
                    }
                    put("last_transaction_id", cleanTrx)
                    put("last_payment_amount", amountInt)
                    put("last_sender_mobile", cleanMobile)
                    put("last_payment_method", paymentMethod)
                    put("pending_installment_tx_id", cleanTrx)
                    put("pending_installment_amount", amountInt)
                    put("rejection_reason", "")
                    put("updated_at", nowIso)
                }
                val patchReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?id=eq.$enrollmentId")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(patchJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                runCatching { httpClient.newCall(patchReq).execute().close() }
            }

            if (txSucceeded) {
                fetchCourseCommerceStatus(context)
                com.example.notification.OneSignalPushManager.notifyAdminCoursePaymentSubmitted(
                    context = context,
                    studentName = studentName,
                    studentPhone = normPhone,
                    programTitle = programTitle,
                    paymentMethod = paymentMethod,
                    amount = amountInt,
                    transactionId = cleanTrx,
                    planTitle = selectedPlan.titleBn
                )
                return@withContext Result.success("আপনার পেমেন্ট তথ্য সফলভাবে জমা হয়েছে! অ্যাডমিন যাচাই করে এপ্রুভ করলেই কোর্সটি আনলক হয়ে যাবে।")
            } else {
                return@withContext Result.failure(Exception("পেমেন্ট জমা দিতে সমস্যা হয়েছে (HTTP $lastTxCode)।"))
            }
        } catch (e: Throwable) {
            return@withContext Result.failure(Exception("নেটওয়ার্ক সমস্যা: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"))
        }
    }

    /**
     * Activates the Admin-approved Free Trial for a course so the student can explore the course
     * for `trial_days` / `trial_hours` before buying. When the trial expires, the course automatically
     * locks and prompts the student to purchase.
     */
    suspend fun startCourseFreeTrial(
        context: Context,
        programId: String,
        programTitle: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = resolveStudentAccount(context)
            if (!account.isLoggedIn) {
                return@withContext Result.failure(Exception("অনুগ্রহ করে আগে আপনার শিখো একাউন্টে লগইন করুন।"))
            }

            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext Result.failure(Exception("সার্ভার কনফিগারেশন পাওয়া যায়নি।"))
            }

            val cachedItem = getCachedSnapshot(context).enrollmentsByProgramId[programId]
            val trialDays = (cachedItem?.trialDays ?: 1).coerceAtLeast(0)
            val trialHours = (cachedItem?.trialHours ?: 0).coerceAtLeast(0)
            val durationLabelBn = when {
                trialDays > 0 -> "${CourseCommerceItemState.toBanglaDigits(trialDays.toString())} দিনের"
                trialHours > 0 -> "${CourseCommerceItemState.toBanglaDigits(trialHours.toString())} ঘণ্টার"
                else -> "১ দিনের"
            }

            // 1. Primary (Zero-Trust RLS compatible): Call exact Supabase SECURITY DEFINER RPC `start_course_free_trial`
            var rpcSucceeded = false
            var rpcNotFound = false
            var rpcMessage = ""
            val studentKey = account.normPhone.ifBlank { account.accountKey }.ifBlank { DeviceSecurityManager.getDeviceHardwareHash(context) }
            try {
                val rpcPayload = JSONObject().apply {
                    put("p_student_key", studentKey)
                    put("p_program_id", programId)
                    put("p_program_title", programTitle)
                }
                val rpcReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/start_course_free_trial")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(rpcReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string() ?: ""
                        if (bodyStr.trim().startsWith("{")) {
                            val json = JSONObject(bodyStr)
                            if (json.optBoolean("success", false)) {
                                rpcSucceeded = true
                                rpcMessage = json.optString("message", "")
                            } else {
                                val errMsg = json.optString("message", "")
                                if (errMsg.isNotBlank()) {
                                    return@withContext Result.failure(Exception(errMsg))
                                }
                            }
                        }
                    } else if (resp.code == 404) {
                        rpcNotFound = true
                    }
                }
            } catch (_: Throwable) {}

            // 2. Legacy Direct REST Fallback ONLY if RPC does not exist on server (HTTP 404)
            var patchSucceeded = rpcSucceeded
            if (!rpcSucceeded && rpcNotFound) {
                val existingRow = findExistingEnrollmentForStudent(supabaseUrl, anonKey, account, programId)
                val rowId = existingRow?.optString("id", "")?.trim().orEmpty()
                    .ifBlank { cachedItem?.enrollmentId.orEmpty() }
                if (rowId.isNotBlank()) {
                    val nowMs = currentTrustedTimeMillis()
                    val durationMs = when {
                        trialDays > 0 && trialHours > 0 -> (trialDays * 24L + trialHours) * 3600L * 1000L
                        trialDays > 0 -> trialDays * 24L * 3600L * 1000L
                        trialHours > 0 -> trialHours * 3600L * 1000L
                        else -> 24L * 3600L * 1000L
                    }
                    val endMs = nowMs + durationMs
                    val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val nowIso = isoFormatter.format(Date(nowMs))
                    val endIso = isoFormatter.format(Date(endMs))

                    val patchPayloads = listOf(
                        JSONObject().apply {
                            put("status", "TRIAL_ACTIVE")
                            put("access_status", "TRIAL_ACTIVE")
                            put("trial_enabled", true)
                            put("trial_started_at", nowIso)
                            put("trial_ends_at", endIso)
                            put("trial_expires_at", endIso)
                            put("is_trial_used", true)
                            put("trial_used", true)
                            put("updated_at", nowIso)
                        },
                        JSONObject().apply {
                            put("status", "TRIAL_ACTIVE")
                            put("trial_enabled", true)
                            put("trial_started_at", nowIso)
                            put("trial_ends_at", endIso)
                            put("is_trial_used", true)
                            put("updated_at", nowIso)
                        }
                    )
                    for (patchObj in patchPayloads) {
                        try {
                            val patchReq = Request.Builder()
                                .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?id=eq.$rowId")
                                .addHeader("apikey", anonKey)
                                .addHeader("Authorization", "Bearer $anonKey")
                                .addHeader("Content-Type", "application/json")
                                .patch(patchObj.toString().toRequestBody("application/json".toMediaType()))
                                .build()
                            httpClient.newCall(patchReq).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    patchSucceeded = true
                                }
                            }
                            if (patchSucceeded) break
                        } catch (_: Throwable) {}
                    }
                }
            }

            if (patchSucceeded) {
                fetchCourseCommerceStatus(context)
                com.example.notification.OneSignalPushManager.notifyAdminFreeTrialStarted(
                    context = context,
                    studentName = account.studentName,
                    studentPhone = account.normPhone.ifBlank { account.rawPhone },
                    programTitle = programTitle,
                    durationLabelBn = durationLabelBn
                )
                val finalMsg = rpcMessage.ifBlank {
                    "🎉 অভিনন্দন! আপনার $durationLabelBn ফ্রি ট্রায়াল চালু হয়েছে। এখন আপনি কোর্সটির ক্লাসগুলো ফ্রিতে দেখতে পারবেন।"
                }
                return@withContext Result.success(finalMsg)
            } else {
                return@withContext Result.failure(Exception("ফ্রি ট্রায়াল চালু করতে সমস্যা হয়েছে। অনুগ্রহ করে ডাটাবেজে ট্রায়াল SQL রান করা আছে কিনা নিশ্চিত করুন।"))
            }
        } catch (e: Throwable) {
            return@withContext Result.failure(Exception("নেটওয়ার্ক ত্রুটি: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"))
        }
    }

    private fun DeviceCoursePermissionsConvertDigits(input: String): String {
        val bn = "০১২৩৪৫৬৭৮৯"
        return input.map { ch ->
            val idx = bn.indexOf(ch)
            if (idx >= 0) ('0' + idx) else ch
        }.joinToString("")
    }
}

