package com.example.modeltest.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * GraphQL Models and Data Classes for Model Test Feature matching Shikho HAR logs
 */

// 1. GetModelTestInfo
@JsonClass(generateAdapter = true)
data class ModelTestInfoResponse(
    val data: ModelTestInfoData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestInfoData(
    val modelTest: ModelTestInfoDetails? = null,
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
    val exam_date: String? = null,
    val exam_end_time: String? = null,
    val instructions: List<String>? = null,
    val is_missed: Boolean? = null,
    val is_completed: Boolean? = null,
    val exam_category: String? = null,
    val subject_name: String? = null,
    val mcq_count: Int? = null,
    val cq_count: Int? = null,
    val mcq_duration_minutes: Int? = null,
    val cq_duration_minutes: Int? = null,
    val master_solution_available: Boolean? = null,
    val stages: List<ModelTestStageRaw>? = null,
    val stage_grouping: StageGroupingRaw? = null,
    val exam_slots: List<ExamSlotRaw>? = null,
    val subjects: List<ModelTestSubjectRaw>? = null,
    val hierarchy: List<ModelTestHierarchyRaw>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestSubjectRaw(
    val code: String? = null,
    val color_code: String? = null,
    val display_bn: String? = null,
    val parent_code: String? = null,
    val group: String? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestHierarchyRaw(
    val code: String? = null,
    val display_bn: String? = null,
    val icon: String? = null,
    val chapters: List<ModelTestChapterRaw>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestChapterRaw(
    val id: String? = null,
    val no: Int? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class ExamSlotRaw(
    val name: String? = null,
    val start_time: String? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestStageRaw(
    val id: String? = null,
    val allocated_exam_duration: Int? = null,
    val exam_duration: Int? = null,
    val no_of_questions: Int? = null,
    val type: String? = null, // "MCQ", "CQ"
    val title: String? = null
)

@JsonClass(generateAdapter = true)
data class StageGroupingRaw(
    val mcq_grouping: GroupingDetailRaw? = null,
    val cq_grouping: GroupingDetailRaw? = null
)

@JsonClass(generateAdapter = true)
data class GroupingDetailRaw(
    val sessions_to_answer: Int? = null,
    val stages: List<ModelTestStageRaw>? = null
)

// 2. GetModelTestStages
@JsonClass(generateAdapter = true)
data class ModelTestStagesResponse(
    val data: ModelTestStagesData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestStagesData(
    val modelTest: ModelTestStagesContainer? = null,
    val getModelTestStages: List<ModelTestStageItem>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestStagesContainer(
    val stages: List<ModelTestStageItem>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestStageItem(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val type: String? = null, // "MCQ" or "CQ"
    val total_questions: Int? = null,
    val no_of_questions: Int? = null,
    val duration_minutes: Int? = null,
    val allocated_exam_duration: Int? = null,
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
    val getModelTestSession: ModelTestSessionResult? = null,
    val getModelTestSessions: ModelTestSessionResult? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestSessionResult(
    val id: String? = null,
    val is_final_submitted: Boolean? = null,
    val stages: List<ModelTestStageSession>? = null,
    val session_id: String? = null,
    val mcq_session_id: String? = null,
    val cq_session_id: String? = null,
    val status: String? = null,
    val remaining_time_seconds: Long? = null,
    val is_practice: Boolean? = null
) {
    fun getEffectiveMcqSessionId(): String? {
        return stages?.firstOrNull { it.type.equals("MCQ", ignoreCase = true) }?.session_id
            ?: mcq_session_id
            ?: session_id
    }

    fun getEffectiveCqSessionId(): String? {
        return stages?.firstOrNull { it.type.equals("CQ", ignoreCase = true) }?.session_id
            ?: cq_session_id
    }
}

@JsonClass(generateAdapter = true)
data class ModelTestStageSession(
    val session_id: String? = null,
    val type: String? = null, // "MCQ" or "CQ"
    val is_running: Boolean? = null,
    val is_completed: Boolean? = null,
    val start_time: String? = null,
    val end_time: String? = null
)

// 4. ListRetakeModelTestSession (Practice sessions history)
@JsonClass(generateAdapter = true)
data class ListRetakeModelTestSessionResponse(
    val data: ListRetakeModelTestData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class ListRetakeModelTestData(
    val practiceModelTestSessions: PracticeModelTestSessionsContainer? = null,
    val listRetakeModelTestSession: RetakeSessionsContainer? = null
)

@JsonClass(generateAdapter = true)
data class PracticeModelTestSessionsContainer(
    val data: List<PracticeSessionData>? = null
)

@JsonClass(generateAdapter = true)
data class PracticeSessionData(
    val id: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val is_final_submitted: Boolean? = null,
    val is_finished: Boolean? = null,
    val model_test_id: String? = null,
    val title: String? = null,
    val user_id: String? = null,
    val stages: List<ModelTestStageSession>? = null
)

@JsonClass(generateAdapter = true)
data class RetakeSessionsContainer(
    val totalPracticeAllowed: Int? = 3,
    val attemptedPracticeCount: Int? = 0,
    val allowed_attempts: Int? = 3,
    val remaining_attempts: Int? = 3,
    val sessions: List<RetakeSessionItem>? = emptyList()
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

// 5. GetMcqInfoOfModelTest / getMcqSession
@JsonClass(generateAdapter = true)
data class McqInfoOfModelTestResponse(
    val data: McqInfoOfModelTestData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class McqInfoOfModelTestData(
    val getMcqSession: McqSessionWrapper? = null,
    val getMcqInfoOfModelTest: McqExamContainer? = null
)

@JsonClass(generateAdapter = true)
data class McqSessionWrapper(
    val session: McqSessionData? = null
)

@JsonClass(generateAdapter = true)
data class McqSessionData(
    val title: String? = null,
    val expiry_time: String? = null,
    val question_answer: List<QuestionAnswerStateItem>? = null,
    val questions: List<ShikhoMcqQuestionRaw>? = null
)

@JsonClass(generateAdapter = true)
data class QuestionAnswerStateItem(
    val id: String,
    val given_ans: String? = null,
    val submit_time: String? = null
)

@JsonClass(generateAdapter = true)
data class ShikhoMcqQuestionRaw(
    val id: String,
    val question_no: String? = null,
    val title: String? = null,
    val markdown_version: Int? = null,
    val mcq_options: List<McqOptionRaw>? = null
)

@JsonClass(generateAdapter = true)
data class McqOptionRaw(
    val no: String? = null, // "A", "B", "C", "D"
    val description: String? = null
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
    val marks: Double? = 1.0,
    val user_selected_option: Int? = null,
    val order: Int? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestMcqOption(
    val index: Int? = null,
    val option_letter: String? = null, // "A", "B", "C", "D"
    val text: String? = null,
    val image: String? = null
)

// 6. SubmitMcqSession
@JsonClass(generateAdapter = true)
data class SubmitMcqResponse(
    val data: SubmitMcqData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class SubmitMcqData(
    val submitMcqSession: SubmitMcqSessionWrapper? = null,
    val submitMcqOfModelQuestion: SubmitMcqResult? = null
)

@JsonClass(generateAdapter = true)
data class SubmitMcqSessionWrapper(
    val session: SessionIdOnly? = null
)

@JsonClass(generateAdapter = true)
data class SessionIdOnly(
    val id: String? = null
)

@JsonClass(generateAdapter = true)
data class SubmitMcqResult(
    val success: Boolean? = true,
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
    val getMcqSessionMinimumResult: McqResultMinimalDetails? = null,
    val getMcqResultMinimal: McqResultMinimalDetails? = null
)

@JsonClass(generateAdapter = true)
data class McqResultMinimalDetails(
    val obtained_score: Double? = null,
    val total_marks: Double? = null,
    val total_questions: Int? = null,
    val total: Int? = null,
    val correct: Int? = null,
    val correct_answers: Int? = null,
    val wrong_answers: Int? = null,
    val skipped_questions: Int? = null
)

// 8. GetCqInfoOfModelTest / getCqSession
@JsonClass(generateAdapter = true)
data class CqInfoOfModelTestResponse(
    val data: CqInfoOfModelTestData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class CqInfoOfModelTestData(
    val getCqSession: CqSessionWrapper? = null,
    val getCqInfoOfModelTest: CqContainer? = null
)

@JsonClass(generateAdapter = true)
data class CqSessionWrapper(
    val session: CqSessionData? = null
)

@JsonClass(generateAdapter = true)
data class CqSessionData(
    val title: String? = null,
    val u_code: String? = null,
    val exam_id: String? = null,
    val submission_end_time: String? = null,
    val expiry_time: String? = null,
    val stage: String? = null,
    val questions: List<ShikhoCqQuestionRaw>? = null
)

@JsonClass(generateAdapter = true)
data class ShikhoCqQuestionRaw(
    val id: String,
    val question_no: String? = null,
    val title: String? = null, // Stimulus
    val markdown_version: Int? = null,
    val total_marks: Double? = null,
    val sub_questions: List<ShikhoCqSubQuestionRaw>? = null
)

@JsonClass(generateAdapter = true)
data class ShikhoCqSubQuestionRaw(
    val question: String? = null,
    val marks: Double? = null
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
    val stimulus: String? = null,
    val stimulus_image: String? = null,
    val sub_questions: List<ModelTestCqSubQuestion>? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestCqSubQuestion(
    val key: String? = null,
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
    val message: String? = null,
    val session: McqFeedbackSessionRaw? = null,
    val total_questions: Int? = null,
    val correct_count: Int? = null,
    val wrong_count: Int? = null,
    val unattempted_count: Int? = null,
    val questions: List<FeedbackQuestionItem>? = null
)

@JsonClass(generateAdapter = true)
data class McqFeedbackSessionRaw(
    val id: String? = null,
    val exam_id: String? = null,
    val is_final_submitted: Boolean? = null,
    val is_timeout: Boolean? = null,
    val question_answer: List<FeedbackAnswerRaw>? = null,
    val questions: List<FeedbackQuestionRaw>? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackAnswerRaw(
    val id: String,
    val correct_ans: String? = null,
    val given_ans: String? = null,
    val is_correct: Boolean? = null,
    val is_submitted: Boolean? = null,
    val submit_time: String? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackQuestionRaw(
    val id: String,
    val question_no: String? = null,
    val title: String? = null,
    val correct_option: String? = null,
    val difficulty_level: String? = null,
    val solution: String? = null,
    val solution_img: String? = null,
    val mcq_options: List<McqOptionRaw>? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackQuestionItem(
    val id: String,
    val question_text: String? = null,
    val question_image: String? = null,
    val options: List<ModelTestMcqOption>? = null,
    val user_selected_option: Int? = null,
    val correct_option: Int? = null,
    val is_correct: Boolean? = null,
    val solution: String? = null,
    val solution_image: String? = null
)

// 11. GetCQMasterSolutionUrls & GetMCQMasterSolutionUrls
@JsonClass(generateAdapter = true)
data class CQMasterSolutionUrlsResponse(
    val data: CQMasterSolutionUrlsData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class CQMasterSolutionUrlsData(
    val cqExam: CqExamMasterSolutionRaw? = null,
    val getCQMasterSolutionUrls: CQMasterSolutionUrlsDetails? = null
)

@JsonClass(generateAdapter = true)
data class MCQMasterSolutionUrlsResponse(
    val data: MCQMasterSolutionUrlsData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class MCQMasterSolutionUrlsData(
    val mcqExam: McqExamMasterSolutionRaw? = null
)

@JsonClass(generateAdapter = true)
data class McqExamMasterSolutionRaw(
    val id: String? = null,
    val master_solutions: List<MasterSolutionItemRaw>? = null
)

@JsonClass(generateAdapter = true)
data class CqExamMasterSolutionRaw(
    val id: String? = null,
    val master_solutions: List<MasterSolutionItemRaw>? = null
)

@JsonClass(generateAdapter = true)
data class MasterSolutionItemRaw(
    val title: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class CQMasterSolutionUrlsDetails(
    val mcq_solution_url: String? = null,
    val cq_solution_url: String? = null,
    val master_solution_pdf_url: String? = null
)

// 12. GetCqUploadRelatedInfo
@JsonClass(generateAdapter = true)
data class GetCqUploadRelatedInfoResponse(
    val data: GetCqUploadRelatedInfoData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class GetCqUploadRelatedInfoData(
    val cqExam: CqUploadRelatedInfo? = null
)

@JsonClass(generateAdapter = true)
data class CqUploadRelatedInfo(
    val id: String? = null,
    val number_of_question: Int? = null,
    val number_of_question_to_answer: Int? = null,
    val submission_duration: Int? = null,
    val exam_duration: Int? = null
)

// 13. GetPreSignedUrlList (AWS S3)
@JsonClass(generateAdapter = true)
data class GetPreSignedUrlListResponse(
    val data: GetPreSignedUrlListData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class GetPreSignedUrlListData(
    val getPreSignedUrlList: GetPreSignedUrlListContainer? = null
)

@JsonClass(generateAdapter = true)
data class GetPreSignedUrlListContainer(
    val data: List<PreSignedUrlItem>? = null
)

@JsonClass(generateAdapter = true)
data class PreSignedUrlItem(
    val id: String? = null,
    val name: String? = null,
    val extension: String? = null,
    val expired_at: String? = null,
    val pre_signed_url: String? = null
)

@JsonClass(generateAdapter = true)
data class PreSignedUrlInput(
    val extension: String = "jpg",
    val name: String
)

// 14. StartCqSessionSubmission
@JsonClass(generateAdapter = true)
data class StartCqSessionSubmissionResponse(
    val data: StartCqSessionSubmissionData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class StartCqSessionSubmissionData(
    val startCqSessionSubmission: StartCqSessionSubmissionResult? = null
)

@JsonClass(generateAdapter = true)
data class StartCqSessionSubmissionResult(
    val message: String? = null,
    val session: CqSessionDetailedInfo? = null
)

// 15. SubmitCqSession (Page by Page upload submission)
@JsonClass(generateAdapter = true)
data class SubmitCqSessionResponse(
    val data: SubmitCqSessionData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class SubmitCqSessionData(
    val submitCqSession: SubmitCqSessionResult? = null
)

@JsonClass(generateAdapter = true)
data class SubmitCqSessionResult(
    val message: String? = null,
    val session: CqSessionDetailedInfo? = null
)

@JsonClass(generateAdapter = true)
data class CqSessionDetailedInfo(
    val id: String? = null,
    val exam_id: String? = null,
    val title: String? = null,
    val u_code: String? = null,
    val user_id: String? = null,
    val is_started: Boolean? = null,
    val is_final_submitted: Boolean? = null,
    val stage: String? = null,
    val start_time: String? = null,
    val expiry_time: String? = null,
    val submission_end_time: String? = null,
    val set_identifier: String? = null,
    val question_answer: List<CqQuestionAnswerItemRaw>? = null,
    val questions: List<ShikhoCqQuestionRaw>? = null
)

@JsonClass(generateAdapter = true)
data class CqQuestionAnswerItemRaw(
    val id: String,
    val is_submitted: Boolean? = null,
    val marks: Double? = null,
    val submit_time: String? = null,
    val given_answers: List<CqGivenAnswerItemRaw>? = null
)

@JsonClass(generateAdapter = true)
data class CqGivenAnswerItemRaw(
    val page_no: Int? = null,
    val file_id: String? = null,
    val upload_status: String? = null,
    val original_file_info: OriginalFileInfoRaw? = null
)

@JsonClass(generateAdapter = true)
data class OriginalFileInfoRaw(
    val file_id: String? = null,
    val page_no: Int? = null,
    val url: String? = null,
    val upload_status: String? = null
)

// 16. GetModelTestResultPublishTime
@JsonClass(generateAdapter = true)
data class GetModelTestResultPublishTimeResponse(
    val data: GetModelTestResultPublishTimeData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class GetModelTestResultPublishTimeData(
    val modelTest: ModelTestPublishTimeContainer? = null
)

@JsonClass(generateAdapter = true)
data class ModelTestPublishTimeContainer(
    val result_publish_time: String? = null
)

// 17. CqSessionFinalSubmit
@JsonClass(generateAdapter = true)
data class CqSessionFinalSubmitResponse(
    val data: CqSessionFinalSubmitData? = null,
    val errors: List<com.example.api.GraphQlError>? = null
)

@JsonClass(generateAdapter = true)
data class CqSessionFinalSubmitData(
    val submitCqSession: CqSessionFinalSubmitResult? = null
)

@JsonClass(generateAdapter = true)
data class CqSessionFinalSubmitResult(
    val message: String? = null,
    val session: CqSessionMinimalRaw? = null
)

@JsonClass(generateAdapter = true)
data class CqSessionMinimalRaw(
    val id: String? = null,
    val exam_id: String? = null,
    val is_final_submitted: Boolean? = null,
    val is_started: Boolean? = null,
    val expiry_time: String? = null
)

