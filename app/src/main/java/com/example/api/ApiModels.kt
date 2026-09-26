package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

fun parseIsoToDhakaMillis(isoString: String?): Long? {
    if (isoString.isNullOrBlank()) return null
    val clean = isoString.trim()
    if (clean.startsWith("0000-00-00") || clean.startsWith("1970-01-01")) return null
    val dhakaZone = java.util.TimeZone.getTimeZone("Asia/Dhaka")

    if (clean.endsWith("Z", ignoreCase = true)) {
        try {
            val formatStr = if (clean.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
            val sdfUtc = java.text.SimpleDateFormat(formatStr, java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdfUtc.parse(clean.replace(" ", "T"))
            if (date != null) return date.time
        } catch (_: Exception) {}

        try {
            val sdfUtc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdfUtc.parse(clean.replace(" ", "T").take(19))
            if (date != null) return date.time
        } catch (_: Exception) {}
    }

    if (clean.contains("+") || (clean.contains("-") && clean.length > 10 && clean.lastIndexOf("-") > 10)) {
        try {
            val cleanT = clean.replace(" ", "T")
            val formatStr = if (cleanT.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
            val date = java.text.SimpleDateFormat(formatStr, java.util.Locale.US).parse(cleanT)
            if (date != null) return date.time
        } catch (_: Exception) {}
    }

    try {
        val cleanT = clean.replace(" ", "T").take(19)
        val sdfLocal = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = dhakaZone
        }
        val date = sdfLocal.parse(cleanT)
        if (date != null) return date.time
    } catch (_: Exception) {}

    try {
        val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = dhakaZone
        }
        val date = sdfDate.parse(clean.take(10))
        if (date != null) return date.time
    } catch (_: Exception) {}

    try {
        val ms = clean.toLongOrNull()
        if (ms != null && ms > 1000000000L) {
            return if (ms < 100000000000L) ms * 1000L else ms
        }
    } catch (_: Exception) {}

    return null
}


@JsonClass(generateAdapter = true)
data class UserCheckRequest(
    val phone: String,
    val type: String = "student"
)

@JsonClass(generateAdapter = true)
data class UserCheckResponse(
    val code: Int? = null,
    val message: String? = null,
    val pin_exist: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class SendSmsRequest(
    val phone: String,
    val type: String = "student",
    val auth_type: String,
    val vendor: String = "shikho",
    val google_ads_id: String
)

@JsonClass(generateAdapter = true)
data class SendSmsResponse(
    val code: Int? = null,
    val message: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val phone: String,
    val otp: String,
    val type: String = "student"
)

@JsonClass(generateAdapter = true)
data class VerifyOtpResponse(
    val code: Int? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileDevice(
    val device_id: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val phone: String,
    val otp: String,
    val type: String = "student",
    val profile: ProfileDevice,
    val google_ads_id: String
)

@JsonClass(generateAdapter = true)
data class LoginTokens(
    val access_token: String,
    val refresh_token: String?,
    val user_id: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val tokens: LoginTokens
)

@JsonClass(generateAdapter = true)
data class LogoutResponse(
    val message: String? = null,
    val code: Int? = null
)

// GraphQL generic request payload
@JsonClass(generateAdapter = true)
data class GraphQlQuery(
    val operationName: String,
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
    val extensions: Map<String, Any?>? = mapOf("clientLibrary" to mapOf("name" to "apollo-kotlin", "version" to "5.0.1"))
)

@JsonClass(generateAdapter = true)
data class SetPinData(
    val setUserPin: SetUserPinResponse?
)

@JsonClass(generateAdapter = true)
data class SetUserPinResponse(
    val message: String?
)

@JsonClass(generateAdapter = true)
data class SetPinResponse(
    val data: SetPinData?
)

// ==========================================
// 1. Profile Models
// ==========================================
@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val data: ProfileData?
)

@JsonClass(generateAdapter = true)
data class ProfileData(
    val profile: UserProfile?
)

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String?,
    val first_name: String?,
    val last_name: String? = null,
    val avatar: String? = null,
    val gender: String? = null,
    val dob: String? = null,
    val shift: String? = null,
    val guardian_name: String? = null,
    val guardian_mobile: String? = null,
    val ssc_board_name: String? = null,
    val hsc_board_name: String? = null,
    val board_roll_number: String? = null,
    val hsc_board_roll_number: String? = null,
    val board_reg_number: String? = null,
    val other_tutoring_source: List<String>? = null,
    val study_group: String? = null,
    val `class`: ClassInfo? = null,
    val school: SchoolInfo? = null,
    val user: UserInfo? = null,
    val passing_year: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassInfo(
    val code: String?,
    val display: String?
)

@JsonClass(generateAdapter = true)
data class SchoolInfo(
    val id: String?,
    val name: String?,
    val address: SchoolAddressInfo? = null
)

@JsonClass(generateAdapter = true)
data class SchoolAddressInfo(
    val division: DivisionDistrictInfo? = null,
    val district: DivisionDistrictInfo? = null
)

@JsonClass(generateAdapter = true)
data class DivisionDistrictInfo(
    val code: String? = null,
    val display: String? = null
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val phone: String?,
    val email: String?
)

// Address API Models (/address)
@JsonClass(generateAdapter = true)
data class AddressListResponse(
    val body: List<AddressItem>? = null,
    val code: Int? = null
)

@JsonClass(generateAdapter = true)
data class AddressItem(
    val code: String? = null,
    val display: String? = null,
    val ref: String? = null,
    val _key: String? = null
)

// School Search Models (query GetSchools / searchSchoolV1)
@JsonClass(generateAdapter = true)
data class SchoolSearchResponse(
    val data: SchoolSearchData? = null
)

@JsonClass(generateAdapter = true)
data class SchoolSearchData(
    val searchSchoolV1: SchoolSearchResult? = null
)

@JsonClass(generateAdapter = true)
data class SchoolSearchResult(
    val data: List<SchoolItem>? = null
)

@JsonClass(generateAdapter = true)
data class SchoolItem(
    val id: String? = null,
    val name: String? = null
)

// Update Profile & School Mutations
@JsonClass(generateAdapter = true)
data class UpdateProfileResponse(
    val data: UpdateProfileData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfileData(
    val updateProfile: UserProfile? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSchoolResponse(
    val data: UpdateSchoolData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSchoolData(
    val updateProfile: UpdateSchoolProfileResult? = null
)

@JsonClass(generateAdapter = true)
data class UpdateSchoolProfileResult(
    val school: SchoolItem? = null
)

// ==========================================
// 2. Enrolled Academic Programs & Course Switcher
// ==========================================
@JsonClass(generateAdapter = true)
data class AcademicProgramResponse(
    val data: AcademicProgramData?
)

@JsonClass(generateAdapter = true)
data class AcademicProgramData(
    val listAcademicProgramByEnrollment: ListAcademicProgramByEnrollment?
)

@JsonClass(generateAdapter = true)
data class ListAcademicProgramByEnrollment(
    val enrolled_programs: List<EnrolledProgram>? = emptyList(),
    val other_programs: List<OtherProgram>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class OtherProgramPricing(
    val sale_price_quarterly: Double? = 0.0,
    val sale_price_full: Double? = 0.0
)

@JsonClass(generateAdapter = true)
data class OtherProgram(
    val id: String,
    val classes: List<String>? = emptyList(),
    val title_bn: String?,
    val facebook_group_url: String? = null,
    val banner_url: String?,
    val phase_pricing: Double? = null,
    val is_free: Boolean? = false,
    val has_animated_video: Boolean? = false,
    val full_program_discount_price: String? = null,
    val trial_enabled: Boolean? = false,
    val trial_duration: Int? = null,
    val pricing: OtherProgramPricing? = null
) {
    fun toEnrolledProgram(): EnrolledProgram {
        return EnrolledProgram(
            id = this.id,
            classes = this.classes,
            title_bn = this.title_bn,
            banner_url = this.banner_url,
            color = null,
            is_free = this.is_free,
            trial_enabled = this.trial_enabled,
            has_animated_video = this.has_animated_video,
            enrollment_details = EnrollmentDetails(
                batch_id = null,
                is_active = true,
                trial_end_date = null,
                type = if (this.is_free == true) "FREE" else "Paid",
                expiry_date = null
            ),
            subjects = emptyList()
        )
    }
}

@JsonClass(generateAdapter = true)
data class EnrolledProgram(
    val id: String,
    val classes: List<String>? = emptyList(),
    val title_bn: String?,
    val banner_url: String?,
    val color: String?,
    val is_free: Boolean? = false,
    val trial_enabled: Boolean? = false,
    val has_animated_video: Boolean? = false,
    val enrollment_details: EnrollmentDetails? = null,
    val subjects: List<ProgramSubject>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class EnrollmentDetails(
    val batch_id: String?,
    val is_active: Boolean? = false,
    val trial_end_date: String?,
    val type: String?, // e.g. "FullApTrial", "Paid"
    val expiry_date: String?
)

@JsonClass(generateAdapter = true)
data class ProgramSubject(
    val code: String?,
    val display_bn: String?,
    val color_code: String?,
    val icon: String?
)

// ==========================================
// 3. Student Specific Lessons & Weekly Routine
// ==========================================
@JsonClass(generateAdapter = true)
data class UpcomingLessonsPhaseWiseResponse(
    val data: UpcomingLessonsPhaseWiseData? = null
)

@JsonClass(generateAdapter = true)
data class UpcomingLessonsPhaseWiseData(
    val upcomingLessonsPhaseWise: UpcomingLessonsPhaseWisePayload? = null
)

@JsonClass(generateAdapter = true)
data class UpcomingLessonsPhaseWisePayload(
    val data: List<StudentLessonItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class StudentSpecificLessonsResponse(
    val data: StudentSpecificLessonsData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class StudentSpecificLessonsData(
    val studentSpecificLessons: StudentLessonsInnerData?
)

@JsonClass(generateAdapter = true)
data class StudentLessonsInnerData(
    val data: List<StudentLessonItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class StudentLessonItem(
    val id: String = "",
    val title: String? = null,
    val content_id: String? = null,
    val content_type: String? = null, // "LiveClass", "LiveExam", "RecordedClass"
    val access_level: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val subject_id: String? = null,
    val subject_name: String? = null,
    val subject_code: String? = null,
    val chapter_id: String? = null,
    val chapter_name: String? = null,
    val batch_id: String? = null,
    val program_id: String? = null,
    val color_code: String? = null,
    val icon: String? = null,
    val user_activity_state: String? = null, // "UPCOMING", "ATTENDED", "MISSED", "COMPLETED"
    val class_type: String? = null,
    val type: String? = null,
    val tag: String? = null,
    val live_class: LiveClassDetails? = null,
    val model_test: ModelTestDetails? = null,
    val slide_url: String? = null,
    val attachments: List<LessonAttachmentItem>? = null,
    val video_url: String? = null,
    val stream_url: String? = null,
    val recording_url: String? = null,
    val session_id: String? = null,
    val hw_type: String? = null,
    val phase_id: String? = null,
    val topics: List<TopicItem>? = null,
    val is_free: Boolean? = false,
    val is_locked: Boolean? = false
) {
    val isFree: Boolean
        get() = is_free == true || access_level.equals("FREE", ignoreCase = true) || live_class?.is_free == true

    val isLocked: Boolean
        get() = is_locked == true || access_level.equals("LOCKED", ignoreCase = true)

    val hasRecording: Boolean
        get() = !recording_url.isNullOrBlank() ||
                !stream_url.isNullOrBlank() ||
                !video_url.isNullOrBlank() ||
                !live_class?.recording_url.isNullOrBlank() ||
                !live_class?.stream_url.isNullOrBlank() ||
                !live_class?.video_url.isNullOrBlank() ||
                !live_class?.playback_url.isNullOrBlank() ||
                !live_class?.hls_url.isNullOrBlank()

    val isExam: Boolean
        get() {
            if (content_type?.equals("LiveClass", ignoreCase = true) == true) {
                return live_class?.type?.contains("EXAM", ignoreCase = true) == true ||
                       live_class?.type?.contains("QUIZ", ignoreCase = true) == true
            }
            return content_type?.equals("LiveExam", ignoreCase = true) == true ||
                content_type?.equals("ModelTest", ignoreCase = true) == true ||
                content_type?.equals("Quiz", ignoreCase = true) == true ||
                content_type?.contains("Exam", ignoreCase = true) == true ||
                content_type?.contains("Quiz", ignoreCase = true) == true ||
                class_type?.contains("Exam", ignoreCase = true) == true ||
                class_type?.contains("Quiz", ignoreCase = true) == true ||
                type?.contains("Exam", ignoreCase = true) == true ||
                live_class?.class_type?.contains("Exam", ignoreCase = true) == true ||
                live_class?.type?.contains("EXAM", ignoreCase = true) == true ||
                live_class?.type?.contains("QUIZ", ignoreCase = true) == true ||
                model_test != null ||
                (title != null && (
                    title.contains("Exam", true) || 
                    title.contains("পরীক্ষা", true) || 
                    title.contains("Model Test", true) ||
                    title.contains("Quiz", true) ||
                    title.contains("কুইজ", true)
                ))
        }

    val classStartMs: Long
        get() {
            val timeStr = start_time ?: live_class?.start_time ?: return Long.MAX_VALUE
            return parseIsoToDhakaMillis(timeStr) ?: Long.MAX_VALUE
        }

    val classEndMs: Long
        get() {
            val endTimeStr = end_time ?: live_class?.end_time
            val startMs = classStartMs
            if (!endTimeStr.isNullOrBlank()) {
                val endParsed = parseIsoToDhakaMillis(endTimeStr)
                if (endParsed != null && endParsed > 0L) {
                    return endParsed
                }
            }
            return if (startMs != Long.MAX_VALUE) startMs + (90 * 60 * 1000L) else Long.MAX_VALUE
        }

    val isLiveNow: Boolean
        get() {
            if (isExam) return false

            // Explicitly not live if completed, ended, recorded, attended, or missed
            if (user_activity_state.equals("COMPLETED", true) ||
                user_activity_state.equals("ENDED", true) ||
                user_activity_state.equals("RECORDED", true) ||
                user_activity_state.equals("ATTENDED", true) ||
                user_activity_state.equals("MISSED", true)
            ) {
                return false
            }

            // If live_class object explicitly says ongoing is false
            if (live_class?.is_on_going == false) {
                return false
            }

            // If content type is recorded class or video
            if (content_type?.equals("RecordedClass", ignoreCase = true) == true ||
                content_type?.equals("Video", ignoreCase = true) == true ||
                content_type?.equals("Record", ignoreCase = true) == true
            ) {
                return false
            }

            if (hasRecording) {
                return false
            }

            if (live_class?.is_on_going == true || user_activity_state.equals("LIVE", ignoreCase = true)) {
                return true
            }

            val startMs = classStartMs
            val endMs = classEndMs
            if (startMs != Long.MAX_VALUE && endMs != Long.MAX_VALUE) {
                val now = System.currentTimeMillis()
                if (now in (startMs - 5 * 60 * 1000L)..endMs) {
                    return live_class?.is_on_going != false
                }
            }

            return false
        }

    val isUpcoming: Boolean
        get() {
            if (isExam) return false
            if (isLiveNow) return false
            if (user_activity_state.equals("COMPLETED", true) ||
                user_activity_state.equals("ENDED", true) ||
                user_activity_state.equals("RECORDED", true) ||
                user_activity_state.equals("ATTENDED", true) ||
                user_activity_state.equals("MISSED", true) ||
                live_class?.is_on_going == false
            ) {
                return false
            }
            val startMs = classStartMs
            if (startMs != Long.MAX_VALUE) {
                return System.currentTimeMillis() < (startMs - 5 * 60 * 1000L)
            }
            return user_activity_state.equals("UPCOMING", ignoreCase = true)
        }

    val isLive: Boolean
        get() {
            if (isExam) return false
            if (live_class?.is_on_going == false) return false
            if (user_activity_state.equals("COMPLETED", true) ||
                user_activity_state.equals("ENDED", true) ||
                user_activity_state.equals("RECORDED", true) ||
                user_activity_state.equals("ATTENDED", true) ||
                user_activity_state.equals("MISSED", true)
            ) return false
            val endMs = classEndMs
            if (endMs != Long.MAX_VALUE && System.currentTimeMillis() > endMs) {
                return false
            }
            return isLiveNow || live_class?.is_on_going == true
        }

    val isRecorded: Boolean
        get() = !isExam && !isLiveNow && !isUpcoming
    val candidateStreamUrls: List<String>
        get() {
            val list = mutableListOf<String>()

            // 1. Direct recording / video URLs from API response (if provided directly by Shikho)
            val directUrls = listOfNotNull(
                live_class?.playback_url,
                live_class?.recording_url,
                live_class?.stream_url,
                live_class?.video_url,
                live_class?.url,
                live_class?.hls_url,
                recording_url,
                stream_url,
                video_url
            ).map { it.trim() }.filter { it.isNotBlank() && it != "null" }

            for (url in directUrls) {
                if (!list.contains(url)) list.add(url)
            }

            return list.filter { it.isNotBlank() && it != "null" }.distinct()
        }

    val resolvedVideoUrl: String?
        get() = candidateStreamUrls.firstOrNull { it.isNotBlank() }

    val resolvedSlideUrl: String?
        get() = live_class?.lectureSlideUrl
            ?: slide_url
            ?: attachments?.firstOrNull { it.file_type.equals("pdf", ignoreCase = true) || it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }?.downloadUrl
            ?: attachments?.firstOrNull()?.downloadUrl

    val allAttachments: List<LessonAttachmentItem>
        get() {
            val list = mutableListOf<LessonAttachmentItem>()
            live_class?.attachments?.let { list.addAll(it) }
            live_class?.attachment_list?.let { list.addAll(it) }
            attachments?.let { list.addAll(it) }
            val directSlide = live_class?.slide_url ?: slide_url
            if (!directSlide.isNullOrBlank() && list.none { it.downloadUrl == directSlide }) {
                list.add(0, LessonAttachmentItem(title = "লেকচার স্লাইড (PDF)", url = directSlide, file_type = "pdf"))
            }
            return list.distinctBy { it.downloadUrl }
        }
}

@JsonClass(generateAdapter = true)
data class TopicItem(
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val description: String? = null
) {
    val displayTitle: String
        get() = title ?: name ?: description ?: ""
}

@JsonClass(generateAdapter = true)
data class TeacherItem(
    val id: String? = null,
    val name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar: String? = null,
    val marketing_avatar: String? = null,
    val image: String? = null,
    val subject: String? = null,
    val designation: String? = null,
    val bio: String? = null,
    val marketing_points: List<String>? = emptyList(),
    val university_degree: List<String>? = emptyList()
) {
    val displayName: String
        get() = name ?: listOfNotNull(first_name, last_name).joinToString(" ").ifBlank { "শিক্ষক" }

    val displayAvatar: String?
        get() = marketing_avatar ?: avatar ?: image
}

@JsonClass(generateAdapter = true)
data class LessonAttachmentItem(
    val id: String? = null,
    val title: String? = null,
    val name: String? = null,
    val url: String? = null,
    val link: String? = null,
    val file_url: String? = null,
    val path: String? = null,
    val file_type: String? = null,
    val is_solution_sheet: Boolean? = null
) {
    val downloadUrl: String?
        get() = url ?: link ?: file_url ?: path

    val displayTitle: String
        get() = title ?: name ?: if (is_solution_sheet == true) "সমাধান শিট" else "লেকচার স্লাইড"
}

@JsonClass(generateAdapter = true)
data class LiveClassDetails(
    val id: String? = null,
    val session_id: String? = null,
    val recording_url: String? = null,
    val stream_url: String? = null,
    val video_url: String? = null,
    val url: String? = null,
    val playback_url: String? = null,
    val hls_url: String? = null,
    val chapter_name: String? = null,
    val chapter_id: String? = null,
    val subject_name: String? = null,
    val subject_id: String? = null,
    val is_on_going: Boolean? = false,
    val start_time: String? = null,
    val end_time: String? = null,
    val type: String? = null,
    val class_type: String? = null,
    val topics: List<TopicItem>? = emptyList(),
    val teacher: TeacherItem? = null,
    val instructor: TeacherItem? = null,
    val attachments: List<LessonAttachmentItem>? = emptyList(),
    val attachment_list: List<LessonAttachmentItem>? = emptyList(),
    val slide_url: String? = null,
    val is_free: Boolean? = false,
    val is_locked: Boolean? = false,
    val join_link: String? = null,
    val provider: String? = null,
    val hms_room_id: String? = null,
    val hms_token: String? = null,
    val blocked_chat: Boolean? = false
) {
    val liveMeetingUrl: String?
        get() {
            if (!join_link.isNullOrBlank() && !join_link.contains("live.shikho.com") && (join_link.startsWith("http://") || join_link.startsWith("https://"))) return join_link
            if (!hms_room_id.isNullOrBlank()) {
                val cleanId = hms_room_id.trim()
                val tokenParam = if (!hms_token.isNullOrBlank()) "?token=$hms_token" else ""
                return "https://app.100ms.live/meeting/$cleanId$tokenParam"
            }
            return null
        }
    val candidateStreamUrls: List<String>
        get() {
            val list = mutableListOf<String>()

            // 1. Direct URLs from API (prioritizing playback_url from GetAcademicLiveClassDetails)
            val direct = playback_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: recording_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: stream_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: video_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: hls_url?.takeIf { it.isNotBlank() && it != "null" }
                ?: url?.takeIf { it.isNotBlank() && it != "null" }
            if (!direct.isNullOrBlank()) list.add(direct)

            return list.filter { it.isNotBlank() && it != "null" }.distinct()
        }

    val resolvedVideoUrl: String?
        get() = candidateStreamUrls.firstOrNull { it.isNotBlank() }

    val teacherName: String?
        get() = teacher?.displayName ?: instructor?.displayName

    val teacherAvatar: String?
        get() = teacher?.displayAvatar ?: instructor?.displayAvatar

    val lectureSlideUrl: String?
        get() = slide_url
            ?: attachments?.firstOrNull { it.file_type.equals("pdf", ignoreCase = true) || it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }?.downloadUrl
            ?: attachment_list?.firstOrNull { it.file_type.equals("pdf", ignoreCase = true) || it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }?.downloadUrl
            ?: attachments?.firstOrNull()?.downloadUrl
            ?: attachment_list?.firstOrNull()?.downloadUrl
}

@JsonClass(generateAdapter = true)
data class ModelTestDetails(
    val exam_category: String?,
    val type: String?,
    val result_publish_time: String? = null
)

// ==========================================
// 3.5. Academic Subjects & Progress (Tier 1)
// ==========================================
@JsonClass(generateAdapter = true)
data class AcademicSubjectsResponse(
    val data: AcademicSubjectsData? = null
)

@JsonClass(generateAdapter = true)
data class AcademicSubjectsData(
    val academicProgram: AcademicProgramDetail? = null
)

@JsonClass(generateAdapter = true)
data class AcademicProgramDetail(
    val id: String? = null,
    val subjects: List<AcademicSubjectItem>? = emptyList(),
    val subjects_progress_bar: List<SubjectProgressBarItem>? = emptyList(),
    val trial_subject_list: List<String>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class AcademicSubjectItem(
    val code: String? = null,
    val color_code: String? = null,
    val display: String? = null,
    val display_bn: String? = null,
    val icon: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectProgressBarItem(
    val code: String? = null,
    val percentage: Double? = null,
    val total_chapters: Int? = null,
    val completed_chapters: Int? = null
)

// ==========================================
// 3.6. Academic Program Chapters (Tier 2)
// ==========================================
@JsonClass(generateAdapter = true)
data class AcademicChaptersResponse(
    val data: AcademicChaptersData? = null
)

@JsonClass(generateAdapter = true)
data class AcademicChaptersData(
    val listAcademicProgramChapters: ListAcademicProgramChaptersPayload? = null,
    val chapters: ChaptersPayload? = null
)

@JsonClass(generateAdapter = true)
data class ChaptersPayload(
    val data: List<AcademicChapterItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ListAcademicProgramChaptersPayload(
    val data: List<AcademicChapterItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TopicsMetaWrapper(
    val meta: TopicsMetaCount? = null
)

@JsonClass(generateAdapter = true)
data class TopicsMetaCount(
    val count: Int? = null
)

@JsonClass(generateAdapter = true)
data class AcademicChapterItem(
    val id: String = "",
    val chapter_id: String? = null,
    val chapter_name: String? = null,
    val name: String? = null,
    val chapter_no: Any? = null,
    val no: Any? = null,
    val status: String? = null, // e.g. "IN_PROGRESS", "COMPLETED", "ON_GOING", "UPCOMING"
    val class_counter: Int? = null,
    val exam_counter: Int? = null,
    val chapters_progress_percentage: Double? = null,
    val all_topics_free: Boolean? = null,
    val topics: TopicsMetaWrapper? = null
) {
    val effectiveName: String
        get() = chapter_name ?: name ?: "অধ্যায়"

    val effectiveNo: Any?
        get() = chapter_no ?: no

    val topicCount: Int?
        get() = topics?.meta?.count

    val displayProgress: Int
        get() = chapters_progress_percentage?.toInt() ?: 0

    val isCompleted: Boolean
        get() {
            val st = status?.uppercase() ?: ""
            if (st in listOf("COMPLETED", "FINISHED", "DONE", "ENDED", "ATTENDED", "PASSED", "ARCHIVED", "PAST", "PUBLISHED", "COMPLETED_TEACHING")) {
                return true
            }
            if (displayProgress >= 100) return true
            if (st.isBlank() && ((class_counter ?: 0) > 0 || (exam_counter ?: 0) > 0)) return true
            return false
        }

    val isInProgress: Boolean
        get() {
            val st = status?.uppercase() ?: ""
            if (st in listOf("IN_PROGRESS", "ON_GOING", "ONGOING", "ACTIVE", "RUNNING", "CURRENT", "LIVE")) {
                return true
            }
            if (displayProgress in 1..99) return true
            return false
        }
}

// ==========================================
// 3.7. Subject Hierarchy With Question Counts
// ==========================================
@JsonClass(generateAdapter = true)
data class SubjectHierarchyWithQuestionCountsResponse(
    val data: SubjectHierarchyWithQuestionCountsData? = null
)

@JsonClass(generateAdapter = true)
data class SubjectHierarchyWithQuestionCountsData(
    val subjectHierarchyWithQuestionCounts: SubjectHierarchyInnerData? = null
)

@JsonClass(generateAdapter = true)
data class SubjectHierarchyInnerData(
    val data: List<SubjectHierarchyItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SubjectHierarchyItem(
    val code: String? = null,
    val color_code: String? = null,
    val display: String? = null,
    val display_bn: String? = null,
    val icon: String? = null,
    val should_render: Boolean? = true,
    val total_active_questions: Int? = null,
    val chapters: List<HierarchyChapterItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class HierarchyChapterItem(
    val id: String = "",
    val name: String? = null,
    val no: Any? = null,
    val should_render: Boolean? = true,
    val total_active_questions: Int? = null
)

// ==========================================
// 4. Program Phases / Course Progress Quarters
// ==========================================
@JsonClass(generateAdapter = true)
data class ProgramPhasesResponse(
    val data: ProgramPhasesData?
)

@JsonClass(generateAdapter = true)
data class ProgramPhasesData(
    val programPhasesByStudent: ProgramPhasesInnerData? = null,
    val programPhases: ProgramPhasesInnerData? = null
)

@JsonClass(generateAdapter = true)
data class ProgramPhasesInnerData(
    val data: List<PhaseItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class PhaseItem(
    val id: String = "",
    val academic_program_id: String? = null,
    val batch_id: String? = null,
    val title: String? = null,
    val status: String? = null, // "ACTIVE", "COMPLETED", "UPCOMING", "UNENROLLED"
    val is_current: Boolean? = false,
    val has_enrolment: Boolean? = false,
    val has_free_trial_enrolment: Boolean? = false,
    val is_backlog: Boolean? = false,
    val is_last_month: Boolean? = false,
    val is_purchasable: Boolean? = false,
    val type: String? = null,
    val facebook_group_url: String? = null,
    val syllabus_attachment_url: String? = null,
    val report_exists: Boolean? = false,
    val report_version: String? = null,
    val course_progress_percentage: Double? = 0.0,
    val start_date: String? = null,
    val end_date: String? = null
)

// ==========================================
// 5. Practice Quiz Limits
// ==========================================
@JsonClass(generateAdapter = true)
data class PracticeQuizAccessResponse(
    val data: PracticeQuizAccessData?
)

@JsonClass(generateAdapter = true)
data class PracticeQuizAccessData(
    val getPracticeQuizAccess: PracticeQuizAccessPayload?
)

@JsonClass(generateAdapter = true)
data class PracticeQuizAccessPayload(
    val custom_practice_limits: CustomPracticeLimits?
)

@JsonClass(generateAdapter = true)
data class CustomPracticeLimits(
    val has_limit: Boolean? = true,
    val limit_per_day: Int? = 3,
    val used_today: Int? = 0
)

// ==========================================
// 6. Quarterly Results / Performance Score REST
// ==========================================
@JsonClass(generateAdapter = true)
data class QuarterlyResultResponse(
    val status: String? = null,
    val total_score_percentage: Double? = null,
    val total_exams: Int? = null,
    val attended_exams: Int? = null,
    val message: String? = null
)

// ==========================================
// 7. Video List Model
// ==========================================
@JsonClass(generateAdapter = true)
data class VideoListResponse(
    val data: VideoListData?
)

@JsonClass(generateAdapter = true)
data class VideoListData(
    val userSpecificVideoList: List<VideoItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class VideoItem(
    val id: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val duration: String? = null,
    val teacher_name: String? = null,
    val stream_url: String? = null,
    val subject: String? = null,
    val view_count: String? = null
)

// ==========================================
// 8. Syllabus Change, Class List & Batch Models
// ==========================================
@JsonClass(generateAdapter = true)
data class ClassListResponse(
    val classes: List<ClassItem>? = null,
    val data: List<ClassItem>? = null,
    val code: Int? = null,
    val status: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ClassItem(
    val serial: Int? = null,
    val code: String = "",
    val name_en: String? = null,
    val name_bn: String? = null,
    val title_bn: String? = null,
    val title_en: String? = null,
    val is_group_required: Boolean? = null,
    val has_batch_selection: Boolean? = null,
    val show_on_boarding: Boolean? = null,
    val parent_name: String? = null,
    val parent_name_bn: String? = null,
    val is_active: Boolean? = true,
    val vendor: String? = "BD",
    val groups: List<StudyGroupItem>? = null
) {
    val displayNameBn: String
        get() = when (code.uppercase()) {
            "C5", "C05" -> "ক্লাস ৫"
            "C6", "C06" -> "ক্লাস ৬"
            "C7", "C07" -> "ক্লাস ৭"
            "C8", "C08" -> "ক্লাস ৮"
            "C9", "C09" -> "ক্লাস ৯"
            "C10" -> "ক্লাস ১০"
            "C11" -> "এইচএসসি"
            "C12" -> "এডমিশন"
            else -> name_bn ?: title_bn ?: code
        }

    val displaySubtitle: String
        get() = when (code.uppercase()) {
            "C5", "C05" -> "Class 5"
            "C6", "C06" -> "Class 6"
            "C7", "C07" -> "Class 7"
            "C8", "C08" -> "Class 8"
            "C9", "C09" -> "Class 9"
            "C10" -> "Class 10"
            "C11" -> "HSC"
            "C12" -> "Admission"
            else -> name_en ?: title_en ?: ""
        }

    val isGroupRequired: Boolean
        get() = is_group_required == true || code.uppercase() in listOf("C9", "C09", "C10", "C11", "C12")
}

@JsonClass(generateAdapter = true)
data class StudyGroupItem(
    val code: String = "",
    val name_en: String? = null,
    val name_bn: String? = null,
    val title_bn: String? = null,
    val title_en: String? = null
) {
    val displayNameBn: String
        get() = when (code.lowercase()) {
            "science" -> "বিজ্ঞান"
            "humanities", "hum" -> "মানবিক"
            "business_studies", "business", "business studies", "commerce" -> "ব্যবসায় শিক্ষা"
            else -> name_bn ?: title_bn ?: code
        }
}

@JsonClass(generateAdapter = true)
data class BatchOptionsResponse(
    val data: BatchOptionsData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class BatchOptionsData(
    val batchOptions: BatchOptionsPayload? = null
)

@JsonClass(generateAdapter = true)
data class BatchOptionsPayload(
    val classCode: String? = null,
    val date: String? = null,
    val options: List<BatchOptionItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class BatchOptionItem(
    val year: Any? = null,
    val label: String? = null
) {
    val yearString: String
        get() = when (val y = year) {
            is Number -> y.toLong().toString()
            is String -> y
            null -> ""
            else -> y.toString()
        }
}

@JsonClass(generateAdapter = true)
data class GraphQlError(
    val message: String? = null
)

// ==========================================
// 8.1. Join Live Class Mutation Models
// ==========================================
@JsonClass(generateAdapter = true)
data class AvailTrialResponse(
    val data: AvailTrialData? = null,
    val errors: List<GraphQlError>? = null,
    val message: String? = null,
    val code: Int? = null
)

@JsonClass(generateAdapter = true)
data class AvailTrialData(
    val generateFreeTrialEnrolment: GenerateFreeTrialEnrolmentResult? = null
)

@JsonClass(generateAdapter = true)
data class GenerateFreeTrialEnrolmentResult(
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class EnrollInFreeProgramResponse(
    val data: EnrollInFreeProgramData? = null,
    val errors: List<GraphQlError>? = null,
    val message: String? = null,
    val code: Int? = null
)

@JsonClass(generateAdapter = true)
data class EnrollInFreeProgramData(
    val enrollInFreeProgram: EnrollInFreeProgramResult? = null
)

@JsonClass(generateAdapter = true)
data class EnrollInFreeProgramResult(
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ChangeSyllabusResponse(
    val data: ChangeSyllabusData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ChangeSyllabusData(
    val changeSyllabus: AuthTokensPayload? = null
)

@JsonClass(generateAdapter = true)
data class AuthTokensPayload(
    val access_token: String? = null,
    val id_token: String? = null,
    val refresh_token: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateExamYearResponse(
    val data: UpdateExamYearData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateExamYearData(
    val updateProfile: UpdateProfilePayload? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfilePayload(
    val passing_year: String? = null
)

// ==========================================
// Live Class Details Response
// ==========================================
@JsonClass(generateAdapter = true)
data class AcademicLiveClassDetailsResponse(
    val data: AcademicLiveClassDetailsData? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class AcademicLiveClassDetailsData(
    val academicProgramLiveClass: AcademicProgramLiveClassItem? = null
)

@JsonClass(generateAdapter = true)
data class AcademicProgramLiveClassItem(
    val id: String? = null,
    val title: String? = null,
    val class_type: String? = null,
    val playback_url: String? = null,
    val recording_url: String? = null,
    val stream_url: String? = null,
    val video_url: String? = null,
    val url: String? = null,
    val hls_url: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val on_going: Boolean? = false,
    val chapter: HierarchyChapterItem? = null,
    val subject: AcademicSubjectItem? = null,
    val study_materials: List<StudyMaterialItem>? = emptyList(),
    val teacher: TeacherItem? = null,
    val instructor: TeacherItem? = null,
    val topics: List<TopicItem>? = emptyList(),
    val create_practice_mcq: Boolean? = null,
    val batch_ids: List<String>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class StudyMaterialItem(
    val id: String? = null,
    val name: String? = null,
    val file_url: String? = null
)

// ==========================================
// Teacher Details & Topics Response
// ==========================================
@JsonClass(generateAdapter = true)
data class TeacherDetailsResponse(
    val data: TeacherDetailsData? = null
)

@JsonClass(generateAdapter = true)
data class TeacherDetailsData(
    val teacher: TeacherItem? = null
)

@JsonClass(generateAdapter = true)
data class GetTopicsResponse(
    val data: TopicsDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class TopicsDataContainer(
    val topics: TopicsListContainer? = null
)

@JsonClass(generateAdapter = true)
data class TopicsListContainer(
    val data: List<TopicFullItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TopicFullItem(
    val id: String? = null,
    val no: String? = null,
    val name: String? = null,
    val description: String? = null,
    val subscription_type: String? = null,
    val videos: TopicVideosContainer? = null,
    val header: TopicHeaderItem? = null
)

@JsonClass(generateAdapter = true)
data class TopicVideosContainer(
    val data: List<TopicVideoData>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TopicVideoData(
    val id: String? = null,
    val playback_url: String? = null,
    val video_thumbnail_url: List<String>? = emptyList(),
    val category: String? = null
)

@JsonClass(generateAdapter = true)
data class TopicHeaderItem(
    val chapter_id: String? = null,
    val chapter_name: String? = null
)

// ==========================================
// User Priority Subjects Models
// ==========================================
@JsonClass(generateAdapter = true)
data class PrioritySubjectsResponse(
    val data: PrioritySubjectsData? = null
)

@JsonClass(generateAdapter = true)
data class PrioritySubjectsData(
    val userPrioritySubjects: UserPrioritySubjectsPayload? = null
)

@JsonClass(generateAdapter = true)
data class UpsertPrioritySubjectsResponse(
    val data: UpsertPrioritySubjectsData? = null
)

@JsonClass(generateAdapter = true)
data class UpsertPrioritySubjectsData(
    val upsertUserPrioritySubjects: UserPrioritySubjectsPayload? = null
)

@JsonClass(generateAdapter = true)
data class UserPrioritySubjectsPayload(
    val id: String? = null,
    val user_id: String? = null,
    val academic_program_id: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val subjects: List<PrioritySubjectItem>? = emptyList(),
    val subject_progress_cards: List<SubjectProgressCardItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class PrioritySubjectItem(
    val code: String? = null,
    val color_code: String? = null,
    val display: String? = null,
    val display_bn: String? = null,
    val icon: String? = null
)

@JsonClass(generateAdapter = true)
data class SubjectProgressCardItem(
    val subject_id: String? = null,
    val subject_name: String? = null,
    val subject_icon: String? = null,
    val running_chapters: List<SubjectRunningChapterItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SubjectRunningChapterItem(
    val chapter_id: String? = null,
    val title: String? = null,
    val completed_classes_count: Int? = null,
    val total_classes_count: Int? = null,
    val next_exam_start_time: String? = null,
    val is_urgency_active: Boolean? = null,
    val is_live_exam_completed: Boolean? = null,
    val progress_percentage: Double? = null,
    val status: String? = null
)

// ==========================================
// Chapter Live Exam API Models
// ==========================================

@JsonClass(generateAdapter = true)
data class GetLiveExamInfoResponse(
    val data: LiveExamInfoDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class LiveExamInfoDataContainer(
    val liveExamSession: LiveExamSessionDetails? = null
)

@JsonClass(generateAdapter = true)
data class LiveExamSessionDetails(
    val id: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val title: String? = null,
    val markdown_version: Int? = null,
    val total_number_of_question: Int? = null,
    val chapters: List<ExamChapterInfo>? = emptyList(),
    val subject: ExamSubjectInfo? = null
)

@JsonClass(generateAdapter = true)
data class ExamChapterInfo(
    val id: String? = null,
    val name: String? = null,
    val no: String? = null
)

@JsonClass(generateAdapter = true)
data class ExamSubjectInfo(
    val display: String? = null
)

@JsonClass(generateAdapter = true)
data class GetLiveQuestionsResponse(
    val data: LiveQuestionsDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class LiveQuestionsDataContainer(
    val academicProgramLiveExamQuestions: LiveExamQuestionsListContainer? = null
)

@JsonClass(generateAdapter = true)
data class LiveExamQuestionsListContainer(
    val data: List<LiveExamQuestionItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class LiveExamQuestionItem(
    val id: String? = null,
    val title: String? = null,
    val solution: String? = null,
    val markdown_version: String? = null,
    val mcq_options: List<McqOptionItem>? = emptyList(),
    val correct_option: String? = null
)

@JsonClass(generateAdapter = true)
data class McqOptionItem(
    val no: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class SubmitLiveExamResponse(
    val data: SubmitLiveExamDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class SubmitLiveExamDataContainer(
    val acpLiveExamResult: SubmitResultPayload? = null
)

@JsonClass(generateAdapter = true)
data class SubmitResultPayload(
    val code: Int? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class GetLiveExamPerformanceAnalysisResponse(
    val data: LiveExamPerformanceDataContainer? = null
)

typealias GetLiveExamSolutionsResponse = GetLiveExamPerformanceAnalysisResponse

@JsonClass(generateAdapter = true)
data class LiveExamPerformanceDataContainer(
    val acpLiveExamResultHistory: LiveExamResultHistoryPayload? = null
)

@JsonClass(generateAdapter = true)
data class LiveExamResultHistoryPayload(
    val live_exam_session_id: String? = null,
    val result_summary: ExamResultSummary? = null,
    val answers: List<ExamAnswerSolutionItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExamResultSummary(
    val correct_ans: String? = null,
    val incorrect_ans: String? = null,
    val total_length_of_exam: String? = null,
    val total_question: String? = null,
    val total_time_spent: String? = null,
    val unanswered: String? = null
)

@JsonClass(generateAdapter = true)
data class ExamAnswerSolutionItem(
    val given_ans: String? = null,
    val question: LiveExamQuestionItem? = null
)

@JsonClass(generateAdapter = true)
data class ResourceAttachmentsResponse(
    val data: AttachmentListDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class AttachmentListDataContainer(
    val attachmentList: AttachmentListPayload? = null
)

@JsonClass(generateAdapter = true)
data class AttachmentListPayload(
    val data: List<AttachmentDataItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class AttachmentDataItem(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null
)

// ==========================================
// 7.1. Taggable Resources Models (Smart Notes / E-Books)
// ==========================================

@JsonClass(generateAdapter = true)
data class TaggableResourcesResponse(
    val data: TaggableResourcesDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class TaggableResourcesDataContainer(
    val listTaggableResourceType: TaggableResourcePayload? = null
)

@JsonClass(generateAdapter = true)
data class TaggableResourcePayload(
    val data: List<TaggableResourceItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TaggableResourceItem(
    val id: String? = null,
    val title: String? = null,
    val icon_url: String? = null,
    val is_chapter_resource: Boolean? = null,
    val is_subject_resource: Boolean? = null
)

// ==========================================
// 8. Practice Quiz / MCQ Session Models
// ==========================================

@JsonClass(generateAdapter = true)
data class StartPracticeQuizMcqSessionResponse(
    val data: StartPracticeQuizDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class StartPracticeQuizDataContainer(
    val startPracticeQuizMcqSession: PracticeQuizSessionPayload? = null
)

@JsonClass(generateAdapter = true)
data class PracticeQuizSessionPayload(
    val message: String? = null,
    val session: PracticeQuizSessionItem? = null
)

@JsonClass(generateAdapter = true)
data class PracticeQuizSessionItem(
    val id: String = "",
    val start_time: String? = null,
    val expiry_time: String? = null,
    val init_time: String? = null,
    val is_final_submitted: Boolean? = null,
    val is_started: Boolean? = null,
    val is_timeout: Boolean? = null,
    val last_submission_time: String? = null,
    val last_submitted_index: String? = null,
    val quiz_type: String? = null,
    val set_id: String? = null,
    val user_id: String? = null,
    val exam_id: String? = null,
    val title: String? = null,
    val question_answer: List<PracticeQuizQuestionAnswerItem>? = emptyList(),
    val questions: List<PracticeQuizQuestionItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class PracticeQuizQuestionAnswerItem(
    val id: String? = null,
    val given_ans: String? = null,
    val is_submitted: Boolean? = null,
    val start_time: String? = null,
    val submit_time: String? = null,
    val correct_ans: String? = null,
    val is_correct: Boolean? = null,
    val is_saved: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class PracticeQuizQuestionItem(
    val id: String = "",
    val question_no: String? = null,
    val title: String? = null,
    val description: String? = null,
    val difficulty_level: String? = null,
    val allocated_marks: String? = null,
    val allocated_time: String? = null,
    val has_math_equation: Boolean? = null,
    val markdown_version: String? = null,
    val question_type: String? = null,
    val source: String? = null,
    val u_code: String? = null,
    val correct_option: String? = null,
    val given_ans: String? = null,
    val is_active: Boolean? = null,
    val mcq_options: List<McqOptionItem>? = emptyList(),
    val chapter: HierarchyChapterRef? = null
)

@JsonClass(generateAdapter = true)
data class HierarchyChapterRef(
    val id: String? = null,
    val name: String? = null,
    val no: String? = null
)

@JsonClass(generateAdapter = true)
data class GetMcqSessionResponse(
    val data: GetMcqSessionDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class GetMcqSessionDataContainer(
    val getMcqSession: PracticeQuizSessionPayload? = null
)

@JsonClass(generateAdapter = true)
data class SubmitPracticeQuizResponse(
    val data: SubmitPracticeQuizDataContainer? = null,
    val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class SubmitPracticeQuizDataContainer(
    val submitPracticeQuizMcqSession: PracticeQuizSessionPayload? = null
)

@JsonClass(generateAdapter = true)
data class GetQuizResultSummaryResponse(
    @Json(name = "data") val data: GetQuizResultSummaryDataContainer? = null,
    @Json(name = "errors") val errors: List<GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class GetQuizResultSummaryDataContainer(
    @Json(name = "getQuizResultSummery") val getQuizResultSummery: QuizResultSummaryPayload? = null
)

@JsonClass(generateAdapter = true)
data class QuizChapterIdItem(
    @Json(name = "id") val id: String? = null
)

@JsonClass(generateAdapter = true)
data class QuizTopicIdItem(
    @Json(name = "id") val id: String? = null
)

@JsonClass(generateAdapter = true)
data class QuizResultSubjectItem(
    @Json(name = "code") val code: String? = null,
    @Json(name = "icon") val icon: String? = null
)

@JsonClass(generateAdapter = true)
data class QuizSubjectResultDetailItem(
    @Json(name = "code") val code: String? = null,
    @Json(name = "proficiency") val proficiency: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "title_bn") val title_bn: String? = null,
    @Json(name = "total_correct") val total_correct: Int? = null,
    @Json(name = "total_in_correct") val total_in_correct: Int? = null,
    @Json(name = "total_questions") val total_questions: Int? = null
) {
    val correctCount: Int get() = total_correct ?: 0
    val inCorrectCount: Int get() = total_in_correct ?: 0
    val questionsCount: Int get() = total_questions ?: 0
}

@JsonClass(generateAdapter = true)
data class QuizResultSummaryPayload(
    @Json(name = "id") val id: String? = null,
    @Json(name = "badge") val badge: String? = null,
    @Json(name = "badge_image_base_url") val badge_image_base_url: String? = null,
    @Json(name = "badge_image_name") val badge_image_name: String? = null,
    @Json(name = "quiz_type") val quiz_type: String? = null,
    @Json(name = "total_correct") val total_correct: Int? = null,
    @Json(name = "total_incorrect") val total_incorrect: Int? = null,
    @Json(name = "total_questions") val total_questions: Int? = null,
    @Json(name = "parent_id") val parent_id: String? = null,
    @Json(name = "total_spent_time") val total_spent_time: Long? = null,
    @Json(name = "chapters") val chapters: List<QuizChapterIdItem>? = emptyList(),
    @Json(name = "topics") val topics: List<QuizTopicIdItem>? = emptyList(),
    @Json(name = "subjects") val subjects: List<QuizResultSubjectItem>? = emptyList(),
    @Json(name = "subject_results") val subject_results: List<QuizSubjectResultDetailItem>? = emptyList()
) {
    val fullBadgeImageUrl: String?
        get() = if (!badge_image_base_url.isNullOrBlank() && !badge_image_name.isNullOrBlank()) {
            val base = badge_image_base_url.trim().trimEnd('/')
            val name = badge_image_name.trim().trimStart('/')
            "$base/male/$name"
        } else null

    val correctCount: Int get() = total_correct ?: 0
    val incorrectCount: Int get() = total_incorrect ?: 0
    val questionsCount: Int get() = total_questions ?: 0
    val spentTimeSeconds: Long get() = total_spent_time ?: 0L
}

@JsonClass(generateAdapter = true)
data class GetMcqSessionFeedbackResponse(
    val data: GetMcqSessionFeedbackDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class GetMcqSessionFeedbackDataContainer(
    val getMcqSessionFeedback: PracticeQuizSessionPayload? = null
)

@JsonClass(generateAdapter = true)
data class CreateSavedQuestionResponse(
    val data: CreateSavedQuestionDataContainer? = null
)

@JsonClass(generateAdapter = true)
data class CreateSavedQuestionDataContainer(
    val createSavedQuestion: SavedQuestionPayload? = null
)

@JsonClass(generateAdapter = true)
data class SavedQuestionPayload(
    val id: String? = null,
    val question_id: String? = null,
    val session_id: String? = null,
    val title: String? = null,
    val solution: String? = null,
    val is_correct: Boolean? = null,
    val is_submitted: Boolean? = null
)


