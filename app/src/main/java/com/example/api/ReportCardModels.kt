package com.example.api

import com.squareup.moshi.JsonClass

// =======================================================
// 1. Quarterly Result Details Model
// =======================================================
@JsonClass(generateAdapter = true)
data class QuarterlyReportResponse(
    val performance_report: PerformanceReportContainer? = null,
    val learning_progress: LearningProgressContainer? = null,
    val subject_wise_performance: SubjectWisePerformanceContainer? = null,
    val learning_activity_summary: LearningActivitySummary? = null
)

@JsonClass(generateAdapter = true)
data class PerformanceReportContainer(
    val total_score: ScorePercentage? = null,
    val class_rank: ClassRankData? = null
)

@JsonClass(generateAdapter = true)
data class ScorePercentage(val percentage: Int? = null)

@JsonClass(generateAdapter = true)
data class ClassRankData(val rank: Int? = null, val total_students: Int? = null)

@JsonClass(generateAdapter = true)
data class LearningProgressContainer(
    val class_completion: ClassCompletionProgress? = null,
    val chapter_exam_score: ChapterExamProgress? = null
)

@JsonClass(generateAdapter = true)
data class ClassCompletionProgress(
    val percentage: Int? = null,
    val attended_classes: Int? = null,
    val total_classes: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChapterExamProgress(
    val percentage: Int? = null,
    val obtained: Int? = null,
    val total: Int? = null
)

@JsonClass(generateAdapter = true)
data class SubjectWisePerformanceContainer(
    val groups: List<PerformanceGroupItem>? = null
)

@JsonClass(generateAdapter = true)
data class PerformanceGroupItem(
    val performance_level: String? = null, // "good", "moderate", "needs_improvement"
    val label: String? = null,             // "ভালো", "মাঝারি", "উন্নতির প্রয়োজন"
    val subjects: List<SubjectPerformanceItem>? = null
)

@JsonClass(generateAdapter = true)
data class SubjectPerformanceItem(
    val subject_id: String? = null,
    val title: String? = null,
    val student_avg_score: Int? = null,
    val topper_score: Int? = null,
    val is_topper: Boolean? = null,
    val icon: String? = null,
    val color_code: String? = null,
    val total_live_class: Int? = null,
    val completed_live_class: Int? = null,
    val total_exam_score: Int? = null,
    val total_obtained_score: Int? = null
)

@JsonClass(generateAdapter = true)
data class LearningActivitySummary(
    val animated_lessons_watched: Int? = null,
    val practice_quiz_attempted: Int? = null,
    val learning_resource_viewed: Int? = null
)

// =======================================================
// 2. Performance Trend (Graph) Model
// =======================================================
@JsonClass(generateAdapter = true)
data class PerformanceTrendResponse(
    val metric_type: String? = null,
    val x_labels: List<String>? = null,
    val current_phase: PhaseDataPoints? = null,
    val summary: TrendSummary? = null
)

@JsonClass(generateAdapter = true)
data class PhaseDataPoints(
    val phase_id: String? = null,
    val data_points: List<Float>? = null
)

@JsonClass(generateAdapter = true)
data class TrendSummary(
    val current_average: Int? = null,
    val compare_average: Int? = null
)

// =======================================================
// 3. Leaderboard / Rankings Model
// =======================================================
@JsonClass(generateAdapter = true)
data class LeaderboardRankingRequest(
    val program_id: String,
    val result_type: String = "phase",
    val identifier: String, // phaseId
    val scope: String = "national",
    val subject_id: String,
    val metric: String = "total_score",
    val pagination: RankingPagination = RankingPagination(50, 0),
    val search: String? = null,
    val query: String? = null,
    val search_query: String? = null
)

@JsonClass(generateAdapter = true)
data class RankingPagination(val limit: Int = 50, val offset: Int = 0)

@JsonClass(generateAdapter = true)
data class LeaderboardRankingResponse(
    val user_rank: Int? = null,
    val user_marks: Int? = null,
    val data: List<LeaderboardUserItem>? = null,
    val meta: LeaderboardMeta? = null
)

@JsonClass(generateAdapter = true)
data class LeaderboardUserItem(
    val id: String? = null,
    val _id: String? = null,
    val user_id: String? = null,
    val student_id: String? = null,
    val name: String? = null,
    val full_name: String? = null,
    val avatar: String? = null,
    val photo: String? = null,
    val school: String? = null,
    val college: String? = null,
    val institution: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val district: String? = null,
    val division: String? = null,
    val group: String? = null,
    val study_group: String? = null,
    val batch: String? = null,
    val passing_year: String? = null,
    val roll_no: String? = null,
    val roll: String? = null,
    val rank: Int? = null,
    val score: Int? = null,
    val marks: Int? = null,
    val total_marks: Int? = null,
    val user: LeaderboardUserInfo? = null
) {
    val effectiveUserId: String?
        get() = user_id?.takeIf { it.isNotBlank() }
            ?: user?.effectiveId
            ?: id?.takeIf { it.isNotBlank() }
            ?: _id?.takeIf { it.isNotBlank() }
            ?: student_id?.takeIf { it.isNotBlank() }

    val effectiveScore: Int
        get() = score ?: marks ?: 0

    val effectiveName: String
        get() = user?.effectiveName
            ?: name?.takeIf { it.isNotBlank() }
            ?: full_name?.takeIf { it.isNotBlank() }
            ?: "শিক্ষার্থী"

    val effectiveAvatar: String?
        get() = user?.effectiveAvatar
            ?: avatar?.takeIf { it.isNotBlank() }
            ?: photo?.takeIf { it.isNotBlank() }

    val effectiveCollege: String?
        get() = user?.effectiveCollege
            ?: college?.takeIf { it.isNotBlank() }
            ?: school?.takeIf { it.isNotBlank() }
            ?: institution?.takeIf { it.isNotBlank() }

    val effectivePhone: String?
        get() = user?.effectivePhone
            ?: phone?.takeIf { it.isNotBlank() }
            ?: mobile?.takeIf { it.isNotBlank() }

    val effectiveRoll: String?
        get() = user?.effectiveRoll
            ?: roll_no?.takeIf { it.isNotBlank() }
            ?: roll?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class LeaderboardUserInfo(
    val id: String? = null,
    val _id: String? = null,
    val user_id: String? = null,
    val student_id: String? = null,
    val name: String? = null,
    val full_name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar: String? = null,
    val photo: String? = null,
    val profile_pic: String? = null,
    val image: String? = null,
    val school: String? = null,
    val college: String? = null,
    val institution: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val district: String? = null,
    val division: String? = null,
    val group: String? = null,
    val study_group: String? = null,
    val batch: String? = null,
    val passing_year: String? = null,
    val roll_no: String? = null,
    val roll: String? = null
) {
    val effectiveId: String?
        get() = id?.takeIf { it.isNotBlank() }
            ?: user_id?.takeIf { it.isNotBlank() }
            ?: _id?.takeIf { it.isNotBlank() }
            ?: student_id?.takeIf { it.isNotBlank() }

    val effectiveName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: full_name?.takeIf { it.isNotBlank() }
            ?: run {
                val f = first_name?.trim() ?: ""
                val l = last_name?.trim() ?: ""
                "$f $l".trim()
            }.takeIf { it.isNotBlank() }
            ?: "শিক্ষার্থী"

    val effectiveAvatar: String?
        get() = avatar?.takeIf { it.isNotBlank() }
            ?: photo?.takeIf { it.isNotBlank() }
            ?: profile_pic?.takeIf { it.isNotBlank() }
            ?: image?.takeIf { it.isNotBlank() }

    val effectiveCollege: String?
        get() = college?.takeIf { it.isNotBlank() }
            ?: school?.takeIf { it.isNotBlank() }
            ?: institution?.takeIf { it.isNotBlank() }

    val effectivePhone: String?
        get() = phone?.takeIf { it.isNotBlank() }
            ?: mobile?.takeIf { it.isNotBlank() }

    val effectiveGroup: String?
        get() = group?.takeIf { it.isNotBlank() }
            ?: study_group?.takeIf { it.isNotBlank() }

    val effectiveRoll: String?
        get() = roll_no?.takeIf { it.isNotBlank() }
            ?: roll?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class LeaderboardMeta(val total: Int? = null)
