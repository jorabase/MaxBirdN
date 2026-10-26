package com.example.course

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.auth.SessionManager
import com.example.security.DeviceActivationRepository
import com.example.security.DeviceSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class AdminPaymentSettings(
    val bkashNumber: String = "01700000000",
    val nagadNumber: String = "01700000000",
    val rocketNumber: String = "",
    val instructionBn: String = "উপরের যেকোনো নাম্বারে Send Money করে নিচের ফর্মে আপনার মোবাইল নম্বর, পাঠানো টাকার পরিমাণ এবং Transaction ID (TrxID) সঠিকভাবে প্রদান করুন।"
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
    val adminNote: String = ""
) {
    val isCurrentlyUnlockedForStudy: Boolean
        get() {
            if (accessState != CourseAccessState.ACTIVE) return false
            // যদি কিস্তিতে কেনা হয় এবং বকেয়া থাকে (dueAmount > 0), তবে নির্দিষ্ট সময়ের মধ্যে কিস্তি না দিলে এক্সেস বন্ধ!
            if (dueAmount > 0.0 && nextDueDateMillis != null && System.currentTimeMillis() > nextDueDateMillis) {
                return false
            }
            return true
        }

    val isOverdueNow: Boolean
        get() {
            if (accessState == CourseAccessState.INSTALLMENT_OVERDUE) return true
            if (accessState == CourseAccessState.ACTIVE && dueAmount > 0.0 && nextDueDateMillis != null && System.currentTimeMillis() > nextDueDateMillis) {
                return true
            }
            return false
        }

    val effectiveState: CourseAccessState
        get() = if (isOverdueNow) CourseAccessState.INSTALLMENT_OVERDUE else accessState

    val hasPendingInstallmentPayment: Boolean
        get() = pendingInstallmentTxId.isNotBlank() && pendingInstallmentAmount > 0.0

    fun getRemainingDaysTextBn(): String {
        val dueMs = nextDueDateMillis ?: return ""
        val diff = dueMs - System.currentTimeMillis()
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
        fun toBanglaDigits(input: String): String {
            val en = "0123456789"
            val bn = "০১২৩৪৫৬৭৮৯"
            return input.map { ch ->
                val idx = en.indexOf(ch)
                if (idx >= 0) bn[idx] else ch
            }.joinToString("")
        }

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
     * A course is unlocked ONLY if:
     * 1. The student has an ACTIVE (and non-overdue) enrollment in `course_access_enrollments`, OR
     * 2. The Admin explicitly listed this course ID/title in `perms.allowedPrograms` (and it's not overdue in enrollments).
     */
    fun isCourseActivelyUnlocked(
        programId: String,
        titleBn: String?,
        perms: com.example.security.DeviceCoursePermissions
    ): Boolean {
        if (!perms.isAccountAuthorized) return false
        val enr = enrollmentsByProgramId[programId]
            ?: enrollmentsByProgramId.values.firstOrNull {
                !titleBn.isNullOrBlank() && it.programTitle.equals(titleBn.trim(), ignoreCase = true)
            }

        // যদি কিস্তির মেয়াদ শেষ হয়ে গিয়ে থাকে (OVERDUE), তবে সাথে সাথে এক্সেস বন্ধ!
        if (enr != null && enr.isOverdueNow) {
            return false
        }

        // ১. যদি স্টুডেন্ট কোর্সটি কিনে থাকে বা কিস্তি চালু থাকে (ACTIVE)
        if (enr != null && enr.isCurrentlyUnlockedForStudy) {
            return true
        }

        // ২. যদি অ্যাডমিন সরাসরি নির্দিষ্ট প্রোগ্রামটিকে allowed_programs এ টিক দিয়ে ফ্রি আনলক করে দিয়ে থাকেন
        // (কিন্তু যদি allowedPrograms ফাঁকা থাকে, তবে ডিফল্টভাবে কোনো কোর্সই ফ্রি আনলক হবে না — সব কোর্স লক ও ঝাপসা থাকবে!)
        if (perms.allowedPrograms.isNotEmpty() &&
            !perms.allowedPrograms.any { it.equals("__UNAUTHORIZED_ACCOUNT__", ignoreCase = true) } &&
            perms.isProgramAllowed(programId, titleBn)
        ) {
            // তবে যদি এই কোর্সের জন্য স্টুডেন্টের একটি পেন্ডিং/ওভারডিউ/অ্যাপ্রুভড-টু-বাই রেকর্ড থাকে যা এখনো পেইড নয়, তবে পেমেন্ট না করা পর্যন্ত আনলক হবে না
            if (enr != null && enr.effectiveState != CourseAccessState.ACTIVE) {
                return false
            }
            return true
        }

        return false
    }

    fun resolveCourseItemState(
        programId: String,
        titleBn: String?,
        perms: com.example.security.DeviceCoursePermissions
    ): CourseCommerceItemState {
        val existing = enrollmentsByProgramId[programId]
            ?: enrollmentsByProgramId.values.firstOrNull {
                !titleBn.isNullOrBlank() && it.programTitle.equals(titleBn.trim(), ignoreCase = true)
            }

        if (existing != null) {
            return existing.copy(accessState = existing.effectiveState)
        }

        // যদি অ্যাডমিন সরাসরি allowed_programs এ এই নির্দিষ্ট কোর্সটি ফ্রি এক্সেস দিয়ে থাকেন
        if (perms.isAccountAuthorized &&
            perms.allowedPrograms.isNotEmpty() &&
            !perms.allowedPrograms.any { it.equals("__UNAUTHORIZED_ACCOUNT__", ignoreCase = true) } &&
            perms.isProgramAllowed(programId, titleBn)
        ) {
            return CourseCommerceItemState(
                programId = programId,
                programTitle = titleBn ?: "",
                accessState = CourseAccessState.ACTIVE
            )
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
    private const val KEY_CACHED_SNAPSHOT_JSON = "key_cached_snapshot_json"
    private const val KEY_CACHED_FOR_UID = "key_cached_for_uid"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                    val count = obj.optInt("installments_count", 1).coerceAtLeast(1)
                    val interval = obj.optInt("interval_days", if (count > 1) 7 else 0)
                    val defaultPart = if (totalPrice > 0) kotlin.math.round(totalPrice / count) else 0.0
                    val firstAmt = obj.optDouble("first_amount", defaultPart).let { if (it.isNaN() || it <= 0) defaultPart else it }
                    val perAmt = obj.optDouble("per_installment_amount", firstAmt).let { if (it.isNaN() || it <= 0) firstAmt else it }
                    val title = obj.optString("title_bn", "").ifBlank {
                        if (count == 1) "এককালীন সম্পূর্ণ পেমেন্ট (${CourseCommerceItemState.formatTakaBn(totalPrice)})"
                        else "${CourseCommerceItemState.toBanglaDigits(count.toString())} কিস্তিতে পরিশোধ (${CourseCommerceItemState.formatTakaBn(firstAmt)} করে)"
                    }
                    list.add(
                        InstallmentPlanOption(
                            planId = obj.optString("plan_id", "plan_$i"),
                            titleBn = title,
                            installmentsCount = count,
                            intervalDays = interval,
                            firstAmount = firstAmt,
                            perInstallmentAmount = perAmt
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            }
        } catch (_: Throwable) {}
        return buildDefaultInstallmentPlans(totalPrice, allowInstallments)
    }

    private fun parseEnrollmentItem(obj: JSONObject): CourseCommerceItemState? {
        val progId = obj.optString("program_id", "").trim()
        if (progId.isBlank()) return null

        val rawStatus = obj.optString("status", "REQUESTED").trim().uppercase()
        val totalPrice = obj.optDouble("total_price", 0.0).let { if (it.isNaN()) 0.0 else it }
        val paidAmount = obj.optDouble("paid_amount", 0.0).let { if (it.isNaN()) 0.0 else it }
        val dueAmount = obj.optDouble("due_amount", (totalPrice - paidAmount).coerceAtLeast(0.0)).let { if (it.isNaN()) 0.0 else it }
        val allowInst = obj.optBoolean("allow_installments", true)
        val nextDueIso = obj.optString("next_due_date", "").takeIf { it.isNotBlank() && !it.equals("null", true) }
        val nextDueMillis = parseIsoTimestampMillis(nextDueIso)

        val mappedState = when (rawStatus) {
            "REQUESTED" -> CourseAccessState.ACCESS_REQUESTED
            "APPROVED_TO_BUY" -> CourseAccessState.APPROVED_TO_BUY
            "PAYMENT_PENDING" -> CourseAccessState.PAYMENT_PENDING
            "PAYMENT_REJECTED", "REJECTED" -> CourseAccessState.PAYMENT_REJECTED
            "ACTIVE" -> {
                if (dueAmount > 0.0 && nextDueMillis != null && System.currentTimeMillis() > nextDueMillis) {
                    CourseAccessState.INSTALLMENT_OVERDUE
                } else {
                    CourseAccessState.ACTIVE
                }
            }
            "OVERDUE" -> CourseAccessState.INSTALLMENT_OVERDUE
            "REVOKED" -> CourseAccessState.LOCKED_BLURRED
            else -> CourseAccessState.LOCKED_BLURRED
        }

        val plans = parseInstallmentPlans(obj.opt("installment_plans_json"), totalPrice, allowInst)
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
            enrollmentId = obj.optString("id", ""),
            programId = progId,
            programTitle = obj.optString("program_title", ""),
            accessState = mappedState,
            totalPrice = totalPrice,
            paidAmount = paidAmount,
            dueAmount = dueAmount,
            allowInstallments = allowInst,
            installmentPlans = plans,
            activePlanId = obj.optString("active_plan_id", ""),
            activePlanTitle = obj.optString("active_plan_title", ""),
            totalInstallments = totalInst,
            completedInstallments = completedInst,
            installmentIntervalDays = intervalDays,
            nextInstallmentAmount = nextInstAmt,
            nextDueDateIso = nextDueIso,
            nextDueDateMillis = nextDueMillis,
            lastTransactionId = obj.optString("last_transaction_id", ""),
            lastPaymentAmount = obj.optDouble("last_payment_amount", 0.0).let { if (it.isNaN()) 0.0 else it },
            lastSenderMobile = obj.optString("last_sender_mobile", ""),
            lastPaymentMethod = obj.optString("last_payment_method", "bKash"),
            pendingInstallmentTxId = obj.optString("pending_installment_tx_id", "").takeIf { !it.equals("null", true) } ?: "",
            pendingInstallmentAmount = obj.optDouble("pending_installment_amount", 0.0).let { if (it.isNaN()) 0.0 else it },
            rejectionReason = obj.optString("rejection_reason", "").takeIf { !it.equals("null", true) } ?: "",
            adminNote = obj.optString("admin_note", "").takeIf { !it.equals("null", true) } ?: ""
        )
    }

    private fun parseSnapshotJson(root: JSONObject): CourseCommerceSnapshot {
        val settingsObj = root.optJSONObject("payment_settings")
        val settings = if (settingsObj != null) {
            AdminPaymentSettings(
                bkashNumber = settingsObj.optString("bkash_number", "01700000000").ifBlank { "01700000000" },
                nagadNumber = settingsObj.optString("nagad_number", "01700000000").ifBlank { "01700000000" },
                rocketNumber = settingsObj.optString("rocket_number", ""),
                instructionBn = settingsObj.optString(
                    "payment_instruction_bn",
                    "উপরের যেকোনো নাম্বারে Send Money করে নিচের ফর্মে আপনার মোবাইল নম্বর, পাঠানো টাকার পরিমাণ এবং Transaction ID (TrxID) সঠিকভাবে প্রদান করুন।"
                )
            )
        } else {
            AdminPaymentSettings()
        }

        val map = mutableMapOf<String, CourseCommerceItemState>()
        val enrArr = root.optJSONArray("enrollments")
        if (enrArr != null) {
            for (i in 0 until enrArr.length()) {
                val obj = enrArr.optJSONObject(i) ?: continue
                val parsed = parseEnrollmentItem(obj) ?: continue
                map[parsed.programId] = parsed
            }
        }
        return CourseCommerceSnapshot(
            paymentSettings = settings,
            enrollmentsByProgramId = map
        )
    }

    fun getCachedSnapshot(context: Context): CourseCommerceSnapshot {
        return try {
            val session = SessionManager(context)
            val currentUid = (session.getUserId() ?: session.getUserPhone() ?: "").trim()
            val prefs = getPrefs(context)
            val cachedUid = prefs.getString(KEY_CACHED_FOR_UID, "") ?: ""
            if (currentUid.isNotBlank() && cachedUid.isNotBlank() && !currentUid.equals(cachedUid, ignoreCase = true)) {
                return CourseCommerceSnapshot()
            }
            val raw = prefs.getString(KEY_CACHED_SNAPSHOT_JSON, null) ?: return CourseCommerceSnapshot()
            parseSnapshotJson(JSONObject(raw))
        } catch (_: Throwable) {
            CourseCommerceSnapshot()
        }
    }

    private fun saveCachedSnapshot(context: Context, jsonStr: String, uid: String) {
        try {
            getPrefs(context).edit()
                .putString(KEY_CACHED_SNAPSHOT_JSON, jsonStr)
                .putString(KEY_CACHED_FOR_UID, uid)
                .apply()
        } catch (_: Throwable) {}
    }

    /**
     * Fetches the student's real-time course access requests, custom pricing, installment plans,
     * payment verification status, rejection reasons, and overdue locks from Supabase.
     */
    suspend fun fetchCourseCommerceStatus(context: Context): CourseCommerceSnapshot = withContext(Dispatchers.IO) {
        try {
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext getCachedSnapshot(context)
            }

            val session = SessionManager(context)
            val deviceHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val userId = (session.getUserId() ?: "").trim()
            val userPhone = (session.getUserPhone() ?: "").trim()
            val effectiveUid = userId.ifBlank { userPhone }

            // 1. Try RPC `get_student_course_commerce_status`
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/get_student_course_commerce_status"
                val rpcBody = JSONObject().apply {
                    put("p_device_hash", deviceHash)
                    put("p_display_device_id", displayDeviceId)
                    put("p_shikho_user_id", effectiveUid)
                    put("p_user_phone", userPhone)
                }
                val req = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    if (bodyStr.trim().startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("success", false) || json.has("enrollments")) {
                            saveCachedSnapshot(context, bodyStr, effectiveUid)
                            return@withContext parseSnapshotJson(json)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "RPC get_student_course_commerce_status fallback: ${e.message}")
            }

            // 2. Direct REST Fallback if RPC is not yet created
            val rootJson = JSONObject()
            try {
                val settingsReq = Request.Builder()
                    .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_payment_settings?select=*&limit=1")
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()
                val settingsResp = httpClient.newCall(settingsReq).execute()
                if (settingsResp.isSuccessful) {
                    val arr = JSONArray(settingsResp.body?.string() ?: "[]")
                    if (arr.length() > 0) {
                        rootJson.put("payment_settings", arr.getJSONObject(0))
                    }
                }
            } catch (_: Throwable) {}

            try {
                val enrUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?or=(device_hash.eq.$deviceHash,display_device_id.eq.$displayDeviceId)&select=*&order=updated_at.desc"
                val enrReq = Request.Builder()
                    .url(enrUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .get()
                    .build()
                val enrResp = httpClient.newCall(enrReq).execute()
                if (enrResp.isSuccessful) {
                    val rawArr = JSONArray(enrResp.body?.string() ?: "[]")
                    val filteredArr = JSONArray()
                    for (i in 0 until rawArr.length()) {
                        val item = rawArr.getJSONObject(i)
                        val rowUid = item.optString("shikho_user_id", "").trim()
                        val rowPhone = item.optString("user_phone", "").trim()
                        if (effectiveUid.isBlank() ||
                            rowUid.equals(effectiveUid, ignoreCase = true) ||
                            (userPhone.isNotBlank() && rowPhone.equals(userPhone, ignoreCase = true))
                        ) {
                            filteredArr.put(item)
                        }
                    }
                    rootJson.put("enrollments", filteredArr)
                    saveCachedSnapshot(context, rootJson.toString(), effectiveUid)
                    return@withContext parseSnapshotJson(rootJson)
                }
            } catch (_: Throwable) {}

            getCachedSnapshot(context)
        } catch (e: Throwable) {
            Log.e(TAG, "fetchCourseCommerceStatus error: ${e.message}")
            getCachedSnapshot(context)
        }
    }

    /**
     * Sends a "কোর্সে এক্সেস পান" request to the Admin App so Admin can enable buying & set the price/installments for this student.
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
            val (supabaseUrl, anonKey) = DeviceActivationRepository.getSupabaseConfig()
            if (supabaseUrl.isBlank() || anonKey.isBlank()) {
                return@withContext Result.failure(Exception("সার্ভার কনফিগারেশন পাওয়া যায়নি।"))
            }

            val session = SessionManager(context)
            val deviceHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val userId = (session.getUserId() ?: "").trim()
            val userPhone = (session.getUserPhone() ?: "").trim()
            val effectiveUid = userId.ifBlank { userPhone.ifBlank { "guest" } }
            val studentName = session.getUserFullName() ?: "শিক্ষার্থী"

            // 1. Try RPC `request_course_purchase_access`
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/request_course_purchase_access"
                val rpcBody = JSONObject().apply {
                    put("p_device_hash", deviceHash)
                    put("p_display_device_id", displayDeviceId)
                    put("p_shikho_user_id", effectiveUid)
                    put("p_user_phone", userPhone)
                    put("p_student_name", studentName)
                    put("p_program_id", programId)
                    put("p_program_title", programTitle)
                    put("p_class_code", classCode ?: "")
                    put("p_group_code", groupCode ?: "")
                    put("p_batch_id", batchId ?: "")
                    put("p_banner_url", bannerUrl ?: "")
                }
                val req = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    if (bodyStr.trim().startsWith("{")) {
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("success", false)) {
                            val msg = json.optString("message", "অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে!")
                            fetchCourseCommerceStatus(context)
                            return@withContext Result.success(msg)
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 2. Direct REST Upsert Fallback into `course_access_enrollments`
            val upsertUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/course_access_enrollments?on_conflict=device_hash,shikho_user_id,program_id"
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val upsertJson = JSONObject().apply {
                put("device_hash", deviceHash)
                put("display_device_id", displayDeviceId)
                put("shikho_user_id", effectiveUid)
                put("user_phone", userPhone)
                put("student_name", studentName)
                put("program_id", programId)
                put("program_title", programTitle)
                put("class_code", classCode ?: "")
                put("group_code", groupCode ?: "")
                put("batch_id", batchId ?: "")
                put("banner_url", bannerUrl ?: "")
                put("status", "REQUESTED")
                put("requested_at", nowIso)
                put("updated_at", nowIso)
            }
            val upsertReq = Request.Builder()
                .url(upsertUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(upsertJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val upsertResp = httpClient.newCall(upsertReq).execute()
            if (upsertResp.isSuccessful) {
                fetchCourseCommerceStatus(context)
                return@withContext Result.success("অ্যাডমিনের কাছে কোর্সে এক্সেস রিকোয়েস্ট পাঠানো হয়েছে! অ্যাডমিন দাম সেট করে এক্সেস দিলে এখানে দেখতে পাবেন।")
            } else {
                return@withContext Result.failure(Exception("রিকোয়েস্ট পাঠানো যায়নি (HTTP ${upsertResp.code})।"))
            }
        } catch (e: Throwable) {
            return@withContext Result.failure(Exception("নেটওয়ার্ক ত্রুটি: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"))
        }
    }

    /**
     * Submits a course purchase or installment payment (Sender Mobile Number, Sent Amount, Transaction ID, Plan).
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
            val cleanMobile = DeviceCoursePermissionsConvertDigits(senderMobile).trim()
            val cleanTrx = transactionId.trim().uppercase()
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

            val session = SessionManager(context)
            val deviceHash = DeviceSecurityManager.getDeviceHardwareHash(context)
            val displayDeviceId = DeviceSecurityManager.getDisplayDeviceId(context)
            val userId = (session.getUserId() ?: "").trim()
            val userPhone = (session.getUserPhone() ?: "").trim()
            val effectiveUid = userId.ifBlank { userPhone.ifBlank { "guest" } }
            val studentName = session.getUserFullName() ?: "শিক্ষার্থী"

            // 1. Try RPC `submit_course_payment_transaction`
            try {
                val rpcUrl = "${supabaseUrl.trimEnd('/')}/rest/v1/rpc/submit_course_payment_transaction"
                val rpcBody = JSONObject().apply {
                    put("p_device_hash", deviceHash)
                    put("p_display_device_id", displayDeviceId)
                    put("p_shikho_user_id", effectiveUid)
                    put("p_user_phone", userPhone)
                    put("p_student_name", studentName)
                    put("p_program_id", programId)
                    put("p_program_title", programTitle)
                    put("p_payment_method", paymentMethod)
                    put("p_sender_mobile", cleanMobile)
                    put("p_amount", amount)
                    put("p_transaction_id", cleanTrx)
                    put("p_selected_plan_id", selectedPlan.planId)
                    put("p_selected_plan_title", selectedPlan.titleBn)
                    put("p_total_installments", selectedPlan.installmentsCount)
                    put("p_interval_days", selectedPlan.intervalDays)
                }
                val req = Request.Builder()
                    .url(rpcUrl)
                    .addHeader("apikey", anonKey)
                    .addHeader("Authorization", "Bearer $anonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(rpcBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = httpClient.newCall(req).execute()
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
                            return@withContext Result.success(msg)
                        } else {
                            val errMsg = json.optString("message", "পেমেন্ট জমা দেওয়া যায়নি।")
                            return@withContext Result.failure(Exception(errMsg))
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 2. Direct REST Fallback into `course_payment_transactions` and `course_access_enrollments`
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val existingItem = getCachedSnapshot(context).enrollmentsByProgramId[programId]
            val enrollmentId = existingItem?.enrollmentId?.takeIf { it.isNotBlank() }

            val txJson = JSONObject().apply {
                if (enrollmentId != null) put("enrollment_id", enrollmentId)
                put("device_hash", deviceHash)
                put("display_device_id", displayDeviceId)
                put("shikho_user_id", effectiveUid)
                put("user_phone", userPhone)
                put("student_name", studentName)
                put("program_id", programId)
                put("program_title", programTitle)
                put("payment_type", if ((existingItem?.completedInstallments ?: 0) > 0) "INSTALLMENT" else "INITIAL")
                put("selected_plan_id", selectedPlan.planId)
                put("selected_plan_title", selectedPlan.titleBn)
                put("installment_number", (existingItem?.completedInstallments ?: 0) + 1)
                put("total_installments", selectedPlan.installmentsCount)
                put("interval_days", selectedPlan.intervalDays)
                put("payment_method", paymentMethod)
                put("sender_mobile", cleanMobile)
                put("amount", amount)
                put("transaction_id", cleanTrx)
                put("status", "PENDING")
                put("created_at", nowIso)
            }

            val txReq = Request.Builder()
                .url("${supabaseUrl.trimEnd('/')}/rest/v1/course_payment_transactions")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(txJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val txResp = httpClient.newCall(txReq).execute()

            if (enrollmentId != null) {
                val patchJson = JSONObject().apply {
                    if (existingItem.accessState != CourseAccessState.ACTIVE) {
                        put("status", "PAYMENT_PENDING")
                    }
                    put("last_transaction_id", cleanTrx)
                    put("last_payment_amount", amount)
                    put("last_sender_mobile", cleanMobile)
                    put("last_payment_method", paymentMethod)
                    put("pending_installment_tx_id", cleanTrx)
                    put("pending_installment_amount", amount)
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
                httpClient.newCall(patchReq).execute().close()
            }

            if (txResp.isSuccessful) {
                fetchCourseCommerceStatus(context)
                return@withContext Result.success("আপনার পেমেন্ট তথ্য সফলভাবে জমা হয়েছে! অ্যাডমিন যাচাই করে এপ্রুভ করলেই কোর্সটি আনলক হয়ে যাবে।")
            } else {
                return@withContext Result.failure(Exception("পেমেন্ট জমা দিতে সমস্যা হয়েছে (HTTP ${txResp.code})।"))
            }
        } catch (e: Throwable) {
            return@withContext Result.failure(Exception("নেটওয়ার্ক সমস্যা: ${e.localizedMessage ?: "আবার চেষ্টা করুন"}"))
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
