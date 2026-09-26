package com.example.modeltest.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * GraphQL Models and Data Classes for Model Test Feature
 */

// 1. GetModelTestInfo
@JsonClass(generateAdapter = true)
data class ModelTestInfoResponse(
    val data: ModelTestInfoData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestInfoData(
    val getModelTestInfo: ModelTestInfoDetails? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestInfoDetails(
    val id: String? = null,
    val title: String? = null,
    val duration_in_minutes: Int? = null,
    val total_marks: Double? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val instructions: List<String>? = null,
    val is_missed: Boolean? = null,
    val is_completed: Boolean? = null,
    val exam_category: String? = null,
    val subject_name: String? = null,
    val mcq_count: Int? = null,
    val cq_count: Int? = null,
    val mcq_duration_minutes: Int? = null,
    val cq_duration_minutes: Int? = null,
    val master_solution_available: Boolean? = null
)

// 2. GetModelTestStages
@JsonClass(generateAdapter = true)
data class ModelTestStagesResponse(
    val data: ModelTestStagesData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestStagesData(
    val getModelTestStages: List<ModelTestStageItem>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestStageItem(
    val id: String? = null,
    val name: String? = null, // "MCQ" or "CQ"
    val type: String? = null,
    val total_questions: Int? = null,
    val duration_minutes: Int? = null,
    val total_marks: Double? = null,
    val order: Int? = null
)

// 3. GetModelTestSessions
@JsonClass(generateAdapter = true)
data class ModelTestSessionsResponse(
    val data: ModelTestSessionsData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestSessionsData(
    val getModelTestSessions: ModelTestSessionResult? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestSessionResult(
    val session_id: String? = null,
    val mcq_session_id: String? = null,
    val cq_session_id: String? = null,
    val status: String? = null,
    val remaining_time_seconds: Long? = null,
    val is_practice: Boolean? = null
)

// 4. ListRetakeModelTestSession (Practice sessions history & limits)
// NOTE: totalPracticeAllowed & attemptedPracticeCount are placeholder mappings
// that fall back safely to dynamic counting from session items.
@JsonClass(generateAdapter = true)
data class ListRetakeModelTestSessionResponse(
    val data: ListRetakeModelTestData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ListRetakeModelTestData(
    val listRetakeModelTestSession: RetakeSessionsContainer? = null
)

@JsonClass(generateAdapter = true)
data class RetakeSessionsContainer(
    val totalPracticeAllowed: Int? = null,
    val attemptedPracticeCount: Int? = null,
    val allowed_attempts: Int? = null,
    val remaining_attempts: Int? = null,
    val sessions: List<RetakeSessionItem>? = null
)

@JsonClass(generateAdapter = true)
data class RetakeSessionItem(
    val session_id: String? = null,
    val attempt_number: Int? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val is_completed: Boolean? = null,
    val score: Double? = null,
    val total_marks: Double? = null
)

// 5. GetMcqInfoOfModelTest
@JsonClass(generateAdapter = true)
data class McqInfoOfModelTestResponse(
    val data: McqInfoOfModelTestData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class McqInfoOfModelTestData(
    val getMcqInfoOfModelTest: McqExamContainer? = null
)

@JsonClass(generateAdapter = true)
data class McqExamContainer(
    val session_id: String? = null,
    val duration_in_seconds: Long? = null,
    val remaining_time_seconds: Long? = null,
    val total_questions: Int? = null,
    val questions: List<ModelTestMcqQuestion>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestMcqQuestion(
    val id: String,
    val question: String? = null,
    val question_image: String? = null,
    val options: List<ModelTestMcqOption>? = null,
    val marks: Double? = null,
    val user_selected_option: Int? = null,
    val order: Int? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestMcqOption(
    val index: Int? = null,
    val text: String? = null,
    val image: String? = null
)

// 6. SubmitMcqOfModelQuestion
@JsonClass(generateAdapter = true)
data class SubmitMcqResponse(
    val data: SubmitMcqData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class SubmitMcqData(
    val submitMcqOfModelQuestion: SubmitMcqResult? = null
)

@JsonClass(generateAdapter = true)
data class SubmitMcqResult(
    val success: Boolean? = null,
    val message: String? = null,
    val is_final_submitted: Boolean? = null
)

// 7. GetMcqResultMinimal (Instant Score Popup)
@JsonClass(generateAdapter = true)
data class McqResultMinimalResponse(
    val data: McqResultMinimalData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class McqResultMinimalData(
    val getMcqResultMinimal: McqResultMinimalDetails? = null
)

@JsonClass(generateAdapter = true)
data class McqResultMinimalDetails(
    val obtained_score: Double? = null,
    val total_marks: Double? = null,
    val total_questions: Int? = null,
    val correct_answers: Int? = null,
    val wrong_answers: Int? = null,
    val skipped_questions: Int? = null
)

// 8. GetCqInfoOfModelTest (Read-only CQ Questions)
@JsonClass(generateAdapter = true)
data class CqInfoOfModelTestResponse(
    val data: CqInfoOfModelTestData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class CqInfoOfModelTestData(
    val getCqInfoOfModelTest: CqContainer? = null
)

@JsonClass(generateAdapter = true)
data class CqContainer(
    val session_id: String? = null,
    val duration_in_seconds: Long? = null,
    val questions: List<ModelTestCqQuestion>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestCqQuestion(
    val id: String,
    val stimulus: String? = null, // উদ্দীপক
    val stimulus_image: String? = null,
    val sub_questions: List<ModelTestCqSubQuestion>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestCqSubQuestion(
    val key: String? = null, // "ক", "খ", "গ", "ঘ"
    val question: String? = null,
    val marks: Double? = null
)

// 9. GetModelTestPreResult (Overall Combined Result)
@JsonClass(generateAdapter = true)
data class ModelTestPreResultResponse(
    val data: ModelTestPreResultData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestPreResultData(
    val getModelTestPreResult: ModelTestPreResultDetails? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestPreResultDetails(
    val model_test_title: String? = null,
    val total_score: Double? = null,
    val total_marks: Double? = null,
    val mcq_score: Double? = null,
    val mcq_total_marks: Double? = null,
    val cq_score: Double? = null,
    val cq_total_marks: Double? = null,
    val rank: Int? = null,
    val total_participants: Int? = null,
    val accuracy_percentage: Double? = null
)

// 10. GetMcqSessionFeedback (analytics.shikho.com)
@JsonClass(generateAdapter = true)
data class McqSessionFeedbackResponse(
    val data: McqSessionFeedbackData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class McqSessionFeedbackData(
    val getMcqSessionFeedback: McqFeedbackDetails? = null
)

@JsonClass(generateAdapter = true)
data class McqFeedbackDetails(
    val total_questions: Int? = null,
    val correct_count: Int? = null,
    val wrong_count: Int? = null,
    val unattempted_count: Int? = null,
    val questions: List<FeedbackQuestionItem>? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackQuestionItem(
    val id: String,
    val question_text: String? = null,
    val question_image: String? = null,
    val options: List<ModelTestMcqOption>? = null,
    val user_selected_option: Int? = null, // null if unanswered
    val correct_option: Int? = null,
    val is_correct: Boolean? = null,
    val solution: String? = null,
    val solution_image: String? = null
)

// 11. GetCQMasterSolutionUrls (analytics.shikho.com)
@JsonClass(generateAdapter = true)
data class CQMasterSolutionUrlsResponse(
    val data: CQMasterSolutionUrlsData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class CQMasterSolutionUrlsData(
    val getCQMasterSolutionUrls: CQMasterSolutionUrlsDetails? = null
)

@JsonClass(generateAdapter = true)
data class CQMasterSolutionUrlsDetails(
    val mcq_solution_url: String? = null,
    val cq_solution_url: String? = null,
    val master_solution_pdf_url: String? = null
)
