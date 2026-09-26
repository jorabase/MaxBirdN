package com.example.modeltest.data

import android.util.Log
import com.example.api.GraphQlQuery
import com.example.api.ShikhoApiService
import com.example.api.StudentLessonItem
import com.example.auth.SessionManager
import com.example.modeltest.data.local.ModelTestDao
import com.example.modeltest.data.local.OfflineMcqAnswerEntity
import com.example.modeltest.data.local.ActiveModelTestSessionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Isolated Repository for Model Test.
 * Interacts with:
 * 1. https://api.shikho.com/graphql (Main operations)
 * 2. https://analytics.shikho.com/graphql (Feedback & Master Solutions)
 * 3. Room Database for offline queueing & lifecycle persistence.
 */
class ModelTestRepository(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager? = null,
    private val modelTestDao: ModelTestDao? = null
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val token = sessionManager?.getAccessToken()
            val builder = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .build()

    private val apiBaseUrl = "https://api.shikho.com/graphql"
    private val analyticsBaseUrl = "https://analytics.shikho.com/graphql"

    // -------------------------------------------------------------
    // Generic GraphQL Executor for api.shikho.com
    // -------------------------------------------------------------
    private suspend inline fun <reified T> executeApiQuery(
        operationName: String,
        query: String,
        variables: Map<String, Any?>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val q = GraphQlQuery(operationName = operationName, query = query, variables = variables)
            val jsonBody = moshi.adapter(GraphQlQuery::class.java).toJson(q)
            val req = Request.Builder()
                .url(apiBaseUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(req).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val adapter = moshi.adapter(T::class.java)
            val result = adapter.fromJson(respBody)
                ?: return@withContext Result.failure(Exception("Failed to parse response"))
            Result.success(result)
        } catch (e: Exception) {
            Log.e("ModelTestRepo", "Error executing $operationName: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // Generic GraphQL Executor for analytics.shikho.com
    // -------------------------------------------------------------
    private suspend inline fun <reified T> executeAnalyticsQuery(
        operationName: String,
        query: String,
        variables: Map<String, Any?>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val q = GraphQlQuery(operationName = operationName, query = query, variables = variables)
            val jsonBody = moshi.adapter(GraphQlQuery::class.java).toJson(q)
            val req = Request.Builder()
                .url(analyticsBaseUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(req).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val adapter = moshi.adapter(T::class.java)
            val result = adapter.fromJson(respBody)
                ?: return@withContext Result.failure(Exception("Failed to parse analytics response"))
            Result.success(result)
        } catch (e: Exception) {
            Log.e("ModelTestRepo", "Analytics error in $operationName: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // 1. SubjectSpecificModelTestsOrLiveClass
    // -------------------------------------------------------------
    suspend fun getSubjectSpecificLessons(
        programId: String,
        phaseId: String,
        subjectId: String,
        contentType: String
    ): Result<List<StudentLessonItem>> = withContext(Dispatchers.IO) {
        val query = """
            query SubjectSpecificModelTestsOrLiveClass(${'$'}program_id: String!, ${'$'}content_type: LessonContentTypeEnum!, ${'$'}phase_id: String!, ${'$'}subject_id: String!) {
              studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, content_type: ${'$'}content_type, subject_id: ${'$'}subject_id) {
                data {
                  access_level
                  title
                  id
                  content_id
                  content_type
                  program_id
                  user_activity_state
                  start_time
                  end_time
                  subject_name
                  model_test {
                    result_publish_time
                    type
                    exam_category
                  }
                  live_class {
                    chapter_id
                    chapter_name
                    end_time
                    is_on_going
                    start_time
                    subject_name
                    subject_id
                    id
                    type
                  }
                  phase_id
                }
              }
            }
        """.trimIndent()

        val variables = mapOf(
            "program_id" to programId,
            "phase_id" to phaseId,
            "subject_id" to subjectId,
            "content_type" to contentType
        )

        try {
            val q = GraphQlQuery(
                operationName = "SubjectSpecificModelTestsOrLiveClass",
                query = query,
                variables = variables
            )
            val response = apiService.getStudentLessons(q)
            val lessons = response.data?.studentSpecificLessons?.data ?: emptyList()
            Result.success(lessons)
        } catch (e: Exception) {
            Log.e("ModelTestRepo", "Error getSubjectSpecificLessons: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // 2. GetModelTestInfo
    // -------------------------------------------------------------
    suspend fun getModelTestInfo(modelTestId: String): Result<ModelTestInfoDetails> {
        val query = """
            query GetModelTestInfo(${'$'}model_test_id: String!) {
              getModelTestInfo(model_test_id: ${'$'}model_test_id) {
                id
                title
                duration_in_minutes
                total_marks
                start_time
                end_time
                instructions
                is_missed
                is_completed
                exam_category
                subject_name
                mcq_count
                cq_count
                mcq_duration_minutes
                cq_duration_minutes
                master_solution_available
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestInfoResponse>(
            operationName = "GetModelTestInfo",
            query = query,
            variables = mapOf("model_test_id" to modelTestId)
        )
        return result.mapCatching {
            it.data?.getModelTestInfo ?: throw Exception("Model Test details not found")
        }
    }

    // -------------------------------------------------------------
    // 3. GetModelTestStages
    // -------------------------------------------------------------
    suspend fun getModelTestStages(modelTestId: String): Result<List<ModelTestStageItem>> {
        val query = """
            query GetModelTestStages(${'$'}model_test_id: String!) {
              getModelTestStages(model_test_id: ${'$'}model_test_id) {
                id
                name
                type
                total_questions
                duration_minutes
                total_marks
                order
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestStagesResponse>(
            operationName = "GetModelTestStages",
            query = query,
            variables = mapOf("model_test_id" to modelTestId)
        )
        return result.mapCatching {
            it.data?.getModelTestStages ?: emptyList()
        }
    }

    // -------------------------------------------------------------
    // 4. GetModelTestSessions
    // -------------------------------------------------------------
    suspend fun createModelTestSession(
        modelTestId: String,
        isPractice: Boolean
    ): Result<ModelTestSessionResult> {
        val query = """
            query GetModelTestSessions(${'$'}model_test_id: String!, ${'$'}is_practice: Boolean) {
              getModelTestSessions(model_test_id: ${'$'}model_test_id, is_practice: ${'$'}is_practice) {
                session_id
                mcq_session_id
                cq_session_id
                status
                remaining_time_seconds
                is_practice
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestSessionsResponse>(
            operationName = "GetModelTestSessions",
            query = query,
            variables = mapOf(
                "model_test_id" to modelTestId,
                "is_practice" to isPractice
            )
        )
        return result.mapCatching {
            it.data?.getModelTestSessions ?: throw Exception("Failed to generate model test session")
        }
    }

    // -------------------------------------------------------------
    // 5. ListRetakeModelTestSession (Practice sessions history)
    // -------------------------------------------------------------
    suspend fun listRetakeModelTestSessions(modelTestId: String): Result<RetakeSessionsContainer> {
        val query = """
            query ListRetakeModelTestSession(${'$'}model_test_id: String!) {
              listRetakeModelTestSession(model_test_id: ${'$'}model_test_id) {
                totalPracticeAllowed
                attemptedPracticeCount
                allowed_attempts
                remaining_attempts
                sessions {
                  session_id
                  attempt_number
                  start_time
                  end_time
                  is_completed
                  score
                  total_marks
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ListRetakeModelTestSessionResponse>(
            operationName = "ListRetakeModelTestSession",
            query = query,
            variables = mapOf("model_test_id" to modelTestId)
        )
        return result.mapCatching {
            it.data?.listRetakeModelTestSession ?: RetakeSessionsContainer()
        }
    }

    // -------------------------------------------------------------
    // 6. GetMcqInfoOfModelTest
    // -------------------------------------------------------------
    suspend fun getMcqInfoOfModelTest(sessionId: String): Result<McqExamContainer> {
        val query = """
            query GetMcqInfoOfModelTest(${'$'}session_id: String!) {
              getMcqInfoOfModelTest(session_id: ${'$'}session_id) {
                session_id
                duration_in_seconds
                remaining_time_seconds
                total_questions
                questions {
                  id
                  question
                  question_image
                  marks
                  user_selected_option
                  order
                  options {
                    index
                    text
                    image
                  }
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<McqInfoOfModelTestResponse>(
            operationName = "GetMcqInfoOfModelTest",
            query = query,
            variables = mapOf("session_id" to sessionId)
        )
        return result.mapCatching {
            it.data?.getMcqInfoOfModelTest ?: throw Exception("MCQ questions could not be loaded")
        }
    }

    // -------------------------------------------------------------
    // 7. SubmitMcqOfModelQuestion (Auto-save & Final submit with offline queue)
    // -------------------------------------------------------------
    suspend fun submitMcqAnswer(
        sessionId: String,
        questionId: String,
        selectedOptionIndex: Int,
        isTimeout: Boolean = false,
        isFinalSubmitted: Boolean = false
    ): Result<SubmitMcqResult> {
        val mutation = """
            mutation SubmitMcqOfModelQuestion(${'$'}session_id: String!, ${'$'}question_id: String!, ${'$'}selected_option: Int, ${'$'}is_timeout: Boolean, ${'$'}is_final_submitted: Boolean) {
              submitMcqOfModelQuestion(session_id: ${'$'}session_id, question_id: ${'$'}question_id, selected_option: ${'$'}selected_option, is_timeout: ${'$'}is_timeout, is_final_submitted: ${'$'}is_final_submitted) {
                success
                message
                is_final_submitted
              }
            }
        """.trimIndent()

        val variables = mapOf(
            "session_id" to sessionId,
            "question_id" to questionId,
            "selected_option" to selectedOptionIndex,
            "is_timeout" to isTimeout,
            "is_final_submitted" to isFinalSubmitted
        )

        val result = executeApiQuery<SubmitMcqResponse>(
            operationName = "SubmitMcqOfModelQuestion",
            query = mutation,
            variables = variables
        )

        return result.mapCatching {
            val res = it.data?.submitMcqOfModelQuestion ?: SubmitMcqResult(success = true)
            // Trigger background offline queue sync if connected
            syncOfflineAnswers(sessionId)
            res
        }.recoverCatching { error ->
            Log.w("ModelTestRepo", "Network error during submitMcqAnswer. Queuing locally: ${error.message}")
            // Cache locally in Room
            modelTestDao?.insertAnswer(
                OfflineMcqAnswerEntity(
                    sessionId = sessionId,
                    questionId = questionId,
                    selectedOptionIndex = selectedOptionIndex,
                    isTimeout = isTimeout,
                    isFinalSubmitted = isFinalSubmitted
                )
            )
            // Return optimistic success so UX continues smoothly
            SubmitMcqResult(success = true, message = "Saved locally", is_final_submitted = isFinalSubmitted)
        }
    }

    // -------------------------------------------------------------
    // Offline Sync Worker
    // -------------------------------------------------------------
    suspend fun syncOfflineAnswers(sessionId: String) = withContext(Dispatchers.IO) {
        val dao = modelTestDao ?: return@withContext
        val pending = dao.getPendingAnswersForSession(sessionId)
        if (pending.isEmpty()) return@withContext

        Log.d("ModelTestRepo", "Syncing ${pending.size} pending offline answers for session: $sessionId")
        for (item in pending) {
            val mutation = """
                mutation SubmitMcqOfModelQuestion(${'$'}session_id: String!, ${'$'}question_id: String!, ${'$'}selected_option: Int, ${'$'}is_timeout: Boolean, ${'$'}is_final_submitted: Boolean) {
                  submitMcqOfModelQuestion(session_id: ${'$'}session_id, question_id: ${'$'}question_id, selected_option: ${'$'}selected_option, is_timeout: ${'$'}is_timeout, is_final_submitted: ${'$'}is_final_submitted) {
                    success
                  }
                }
            """.trimIndent()
            val res = executeApiQuery<SubmitMcqResponse>(
                operationName = "SubmitMcqOfModelQuestion",
                query = mutation,
                variables = mapOf(
                    "session_id" to item.sessionId,
                    "question_id" to item.questionId,
                    "selected_option" to item.selectedOptionIndex,
                    "is_timeout" to item.isTimeout,
                    "is_final_submitted" to item.isFinalSubmitted
                )
            )
            if (res.isSuccess) {
                dao.deleteAnswerById(item.id)
            }
        }
    }

    // -------------------------------------------------------------
    // 8. GetMcqResultMinimal (Instant Score Popup)
    // -------------------------------------------------------------
    suspend fun getMcqResultMinimal(sessionId: String): Result<McqResultMinimalDetails> {
        val query = """
            query GetMcqResultMinimal(${'$'}session_id: String!) {
              getMcqResultMinimal(session_id: ${'$'}session_id) {
                obtained_score
                total_marks
                total_questions
                correct_answers
                wrong_answers
                skipped_questions
              }
            }
        """.trimIndent()

        val result = executeApiQuery<McqResultMinimalResponse>(
            operationName = "GetMcqResultMinimal",
            query = query,
            variables = mapOf("session_id" to sessionId)
        )
        return result.mapCatching {
            it.data?.getMcqResultMinimal ?: McqResultMinimalDetails()
        }
    }

    // -------------------------------------------------------------
    // 9. GetCqInfoOfModelTest (Read-Only CQ Questions)
    // -------------------------------------------------------------
    suspend fun getCqInfoOfModelTest(sessionId: String): Result<CqContainer> {
        val query = """
            query GetCqInfoOfModelTest(${'$'}session_id: String!) {
              getCqInfoOfModelTest(session_id: ${'$'}session_id) {
                session_id
                duration_in_seconds
                questions {
                  id
                  stimulus
                  stimulus_image
                  sub_questions {
                    key
                    question
                    marks
                  }
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<CqInfoOfModelTestResponse>(
            operationName = "GetCqInfoOfModelTest",
            query = query,
            variables = mapOf("session_id" to sessionId)
        )
        return result.mapCatching {
            it.data?.getCqInfoOfModelTest ?: throw Exception("CQ questions could not be loaded")
        }
    }

    // -------------------------------------------------------------
    // 10. GetModelTestPreResult (Overall Combined Result)
    // -------------------------------------------------------------
    suspend fun getModelTestPreResult(sessionId: String): Result<ModelTestPreResultDetails> {
        val query = """
            query GetModelTestPreResult(${'$'}session_id: String!) {
              getModelTestPreResult(session_id: ${'$'}session_id) {
                model_test_title
                total_score
                total_marks
                mcq_score
                mcq_total_marks
                cq_score
                cq_total_marks
                rank
                total_participants
                accuracy_percentage
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestPreResultResponse>(
            operationName = "GetModelTestPreResult",
            query = query,
            variables = mapOf("session_id" to sessionId)
        )
        return result.mapCatching {
            it.data?.getModelTestPreResult ?: throw Exception("Result details unavailable")
        }
    }

    // -------------------------------------------------------------
    // 11. GetMcqSessionFeedback (analytics.shikho.com)
    // -------------------------------------------------------------
    suspend fun getMcqSessionFeedback(sessionId: String): Result<McqFeedbackDetails> {
        val query = """
            query GetMcqSessionFeedback(${'$'}session_id: String!) {
              getMcqSessionFeedback(session_id: ${'$'}session_id) {
                total_questions
                correct_count
                wrong_count
                unattempted_count
                questions {
                  id
                  question_text
                  question_image
                  options {
                    index
                    text
                    image
                  }
                  user_selected_option
                  correct_option
                  is_correct
                  solution
                  solution_image
                }
              }
            }
        """.trimIndent()

        val result = executeAnalyticsQuery<McqSessionFeedbackResponse>(
            operationName = "GetMcqSessionFeedback",
            query = query,
            variables = mapOf("session_id" to sessionId)
        )
        return result.mapCatching {
            it.data?.getMcqSessionFeedback ?: throw Exception("Feedback unavailable")
        }
    }

    // -------------------------------------------------------------
    // 12. GetCQMasterSolutionUrls (analytics.shikho.com)
    // -------------------------------------------------------------
    suspend fun getCQMasterSolutionUrls(modelTestId: String): Result<CQMasterSolutionUrlsDetails> {
        val query = """
            query GetCQMasterSolutionUrls(${'$'}model_test_id: String!) {
              getCQMasterSolutionUrls(model_test_id: ${'$'}model_test_id) {
                mcq_solution_url
                cq_solution_url
                master_solution_pdf_url
              }
            }
        """.trimIndent()

        val result = executeAnalyticsQuery<CQMasterSolutionUrlsResponse>(
            operationName = "GetCQMasterSolutionUrls",
            query = query,
            variables = mapOf("model_test_id" to modelTestId)
        )
        return result.mapCatching {
            it.data?.getCQMasterSolutionUrls ?: throw Exception("Master solution URLs unavailable")
        }
    }

    // -------------------------------------------------------------
    // Lifecycle State Persistence (Save & Restore)
    // -------------------------------------------------------------
    suspend fun saveActiveExamState(
        sessionId: String,
        modelTestId: String,
        isPractice: Boolean,
        currentIndex: Int,
        remainingSec: Long,
        answers: Map<String, Int>
    ) = withContext(Dispatchers.IO) {
        val json = moshi.adapter(Map::class.java).toJson(answers)
        modelTestDao?.saveActiveSession(
            ActiveModelTestSessionEntity(
                sessionId = sessionId,
                modelTestId = modelTestId,
                isPractice = isPractice,
                currentQuestionIndex = currentIndex,
                remainingSeconds = remainingSec,
                answersJson = json
            )
        )
    }

    suspend fun getActiveExamState(sessionId: String): ActiveModelTestSessionEntity? = withContext(Dispatchers.IO) {
        modelTestDao?.getActiveSession(sessionId)
    }

    suspend fun clearActiveExamState(sessionId: String) = withContext(Dispatchers.IO) {
        modelTestDao?.clearActiveSession(sessionId)
    }
}
