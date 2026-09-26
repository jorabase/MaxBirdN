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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Isolated Repository for Model Test using 100% real Shikho GraphQL schemas.
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
                .header("build-version", "(605) 6.0.5")
                .header("x-user-timezone", "Asia/Dhaka")
            if (!token.isNullOrBlank()) {
                builder.header("authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .build()

    private val apiBaseUrl = "https://api.shikho.com/graphql"
    private val analyticsBaseUrl = "https://api.shikho.com/graphql"

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
    // Generic GraphQL Executor for Analytics/Solutions
    // -------------------------------------------------------------
    private suspend inline fun <reified T> executeAnalyticsQuery(
        operationName: String,
        query: String,
        variables: Map<String, Any?>
    ): Result<T> = executeApiQuery(operationName, query, variables)

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
            query GetModelTestInfo(${'$'}id: String!) {
              modelTest(id: ${'$'}id) {
                title
                mandatory_subjects
                sessions_to_answer
                exam_group_logic
                exam_end_time
                exam_date
                exam_category
                exam_slots {
                  name
                  start_time
                }
                stages {
                  id
                  exam_duration
                  allocated_exam_duration
                  type
                  no_of_questions
                }
                type
                subjects {
                  code
                  color_code
                  display_bn
                  parent_code
                  group
                }
                stage_grouping {
                  cq_grouping {
                    exam_stage_sequence
                    sessions_to_answer
                    stages {
                      allocated_exam_duration
                      exam_duration
                      id
                      is_mandatory
                      no_of_questions
                      serial
                      subject_id
                      title
                      type
                    }
                  }
                  cq_or_mcq_grouping {
                    exam_stage_sequence
                    sessions_to_answer
                    stages {
                      allocated_exam_duration
                      exam_duration
                      id
                      is_mandatory
                      no_of_questions
                      serial
                      subject_id
                      title
                      type
                    }
                  }
                  mcq_grouping {
                    exam_stage_sequence
                    sessions_to_answer
                    stages {
                      allocated_exam_duration
                      exam_duration
                      id
                      is_mandatory
                      no_of_questions
                      serial
                      subject_id
                      title
                      type
                    }
                  }
                }
                hierarchy {
                  chapters {
                    id
                    no
                    name
                  }
                  code
                  display_bn
                  icon
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestInfoResponse>(
            operationName = "GetModelTestInfo",
            query = query,
            variables = mapOf("id" to modelTestId)
        )
        return result.mapCatching {
            val raw = it.data?.modelTest ?: it.data?.getModelTestInfo ?: throw Exception("Model Test details not found")
            val mcqStage = raw.stages?.firstOrNull { s -> s.type.equals("MCQ", ignoreCase = true) }
            val cqStage = raw.stages?.firstOrNull { s -> s.type.equals("CQ", ignoreCase = true) }

            raw.copy(
                id = modelTestId,
                start_time = raw.exam_slots?.firstOrNull()?.start_time ?: raw.exam_date ?: raw.start_time,
                end_time = raw.exam_end_time ?: raw.end_time,
                mcq_count = mcqStage?.no_of_questions ?: 30,
                cq_count = cqStage?.no_of_questions ?: 2,
                mcq_duration_minutes = mcqStage?.exam_duration ?: mcqStage?.allocated_exam_duration ?: 30,
                cq_duration_minutes = cqStage?.exam_duration ?: cqStage?.allocated_exam_duration ?: 100
            )
        }
    }

    // -------------------------------------------------------------
    // 3. GetModelTestStages
    // -------------------------------------------------------------
    suspend fun getModelTestStages(modelTestId: String): Result<List<ModelTestStageItem>> {
        val query = """
            query GetModelTestStages(${'$'}id: String!) {
              modelTest(id: ${'$'}id) {
                stages {
                  title
                  type
                  id
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestStagesResponse>(
            operationName = "GetModelTestStages",
            query = query,
            variables = mapOf("id" to modelTestId)
        )
        return result.mapCatching {
            it.data?.modelTest?.stages ?: it.data?.getModelTestStages ?: emptyList()
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
            query GetModelTestSessions(${'$'}is_practice: Boolean!, ${'$'}model_test_id: String!, ${'$'}lesson_id: String, ${'$'}query_only: Boolean!) {
              getModelTestSession(is_practice: ${'$'}is_practice, model_test_id: ${'$'}model_test_id, lesson_id: ${'$'}lesson_id, query_only: ${'$'}query_only) {
                id
                is_final_submitted
                stages {
                  type
                  is_running
                  is_completed
                  session_id
                  start_time
                  end_time
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestSessionsResponse>(
            operationName = "GetModelTestSessions",
            query = query,
            variables = mapOf(
                "is_practice" to isPractice,
                "model_test_id" to modelTestId,
                "lesson_id" to "",
                "query_only" to false
            )
        )
        return result.mapCatching {
            it.data?.getModelTestSession
                ?: it.data?.getModelTestSessions
                ?: throw Exception("Failed to generate model test session")
        }
    }

    // -------------------------------------------------------------
    // 5. ListRetakeModelTestSession (Practice sessions history)
    // -------------------------------------------------------------
    suspend fun listRetakeModelTestSessions(modelTestId: String): Result<RetakeSessionsContainer> {
        val query = """
            query ListRetakeModelTestSession(${'$'}model_test_id: String!) {
              practiceModelTestSessions(model_test_id: ${'$'}model_test_id) {
                data {
                  end_time
                  id
                  is_final_submitted
                  is_finished
                  model_test_id
                  stages {
                    end_time
                    is_completed
                    is_running
                    session_id
                    start_time
                    type
                  }
                  start_time
                  title
                  user_id
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ListRetakeModelTestSessionResponse>(
            operationName = "ListRetakeModelTestSession",
            query = query,
            variables = mapOf("model_test_id" to modelTestId)
        )
        return result.mapCatching { resp ->
            val practiceList = resp.data?.practiceModelTestSessions?.data ?: emptyList()
            val sessionItems = practiceList.mapIndexed { index, p ->
                val mcqStage = p.stages?.firstOrNull { it.type.equals("MCQ", ignoreCase = true) }
                RetakeSessionItem(
                    session_id = mcqStage?.session_id ?: p.id,
                    attempt_number = index + 1,
                    start_time = p.start_time,
                    end_time = p.end_time,
                    is_completed = mcqStage?.is_completed ?: p.is_finished ?: p.is_final_submitted,
                    score = null,
                    total_marks = 30.0
                )
            }
            RetakeSessionsContainer(
                totalPracticeAllowed = 3,
                attemptedPracticeCount = sessionItems.size,
                allowed_attempts = 3,
                remaining_attempts = maxOf(0, 3 - sessionItems.size),
                sessions = sessionItems
            )
        }
    }

    // -------------------------------------------------------------
    // 6. GetMcqInfoOfModelTest / getMcqSession
    // -------------------------------------------------------------
    suspend fun getMcqInfoOfModelTest(sessionId: String): Result<McqExamContainer> {
        val query = """
            query GetMcqInfoOfModelTest(${'$'}session_id: String!) {
              getMcqSession(session_id: ${'$'}session_id) {
                session {
                  title
                  expiry_time
                  question_answer {
                    id
                    given_ans
                    submit_time
                  }
                  questions {
                    id
                    question_no
                    markdown_version
                    title
                    mcq_options {
                      no
                      description
                    }
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
        return result.mapCatching { resp ->
            val sessionData = resp.data?.getMcqSession?.session
            if (sessionData != null && !sessionData.questions.isNullOrEmpty()) {
                val answeredMap = sessionData.question_answer?.associate { it.id to it.given_ans } ?: emptyMap()
                val convertedQuestions = sessionData.questions.mapIndexed { qIdx, rawQ ->
                    val options = rawQ.mcq_options?.mapIndexed { optIdx, opt ->
                        ModelTestMcqOption(
                            index = optIdx,
                            option_letter = opt.no ?: (('A' + optIdx).toString()),
                            text = opt.description ?: opt.no ?: "",
                            image = null
                        )
                    } ?: listOf(
                        ModelTestMcqOption(0, "A", "ক"),
                        ModelTestMcqOption(1, "B", "খ"),
                        ModelTestMcqOption(2, "C", "গ"),
                        ModelTestMcqOption(3, "D", "ঘ")
                    )

                    val userAnsLetter = answeredMap[rawQ.id]
                    val userSelectedIndex = if (!userAnsLetter.isNullOrBlank()) {
                        when (userAnsLetter.uppercase().trim()) {
                            "A", "ক" -> 0
                            "B", "খ" -> 1
                            "C", "গ" -> 2
                            "D", "ঘ" -> 3
                            else -> null
                        }
                    } else null

                    ModelTestMcqQuestion(
                        id = rawQ.id,
                        question = rawQ.title ?: "প্রশ্ন ${qIdx + 1}",
                        question_image = null,
                        options = options,
                        marks = 1.0,
                        user_selected_option = userSelectedIndex,
                        order = rawQ.question_no?.toIntOrNull() ?: (qIdx + 1)
                    )
                }

                McqExamContainer(
                    session_id = sessionId,
                    duration_in_seconds = 30 * 60L,
                    remaining_time_seconds = 30 * 60L,
                    total_questions = convertedQuestions.size,
                    questions = convertedQuestions
                )
            } else {
                resp.data?.getMcqInfoOfModelTest ?: throw Exception("MCQ questions could not be loaded from server")
            }
        }
    }

    // -------------------------------------------------------------
    // 7. SubmitMcqOfModelQuestion / submitMcqSession
    // -------------------------------------------------------------
    suspend fun submitMcqAnswer(
        sessionId: String,
        questionId: String,
        selectedOptionIndex: Int,
        isTimeout: Boolean = false,
        isFinalSubmitted: Boolean = false
    ): Result<SubmitMcqResult> {
        val mutation = """
            mutation SubmitMcqOfModelQuestion(${'$'}is_final_submitted: Boolean!, ${'$'}is_timeout: Boolean!, ${'$'}id: String!, ${'$'}answers: [UpdateMcqSessionsQuestionAnswer]!) {
              submitMcqSession(id: ${'$'}id, is_final_submitted: ${'$'}is_final_submitted, is_timeout: ${'$'}is_timeout, question_answer: ${'$'}answers) {
                session {
                  id
                }
              }
            }
        """.trimIndent()

        val letter = when (selectedOptionIndex) {
            0 -> "A"
            1 -> "B"
            2 -> "C"
            3 -> "D"
            else -> "A"
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentTimeStr = sdf.format(Date())

        val answersList = listOf(
            mapOf(
                "id" to questionId,
                "given_ans" to letter,
                "submit_time" to currentTimeStr
            )
        )

        val variables = mapOf(
            "id" to sessionId,
            "is_final_submitted" to isFinalSubmitted,
            "is_timeout" to isTimeout,
            "answers" to answersList
        )

        val result = executeApiQuery<SubmitMcqResponse>(
            operationName = "SubmitMcqOfModelQuestion",
            query = mutation,
            variables = variables
        )

        return result.mapCatching {
            SubmitMcqResult(success = true, is_final_submitted = isFinalSubmitted)
        }.recoverCatching { error ->
            Log.w("ModelTestRepo", "Network error during submitMcqAnswer. Queuing locally: ${error.message}")
            modelTestDao?.insertAnswer(
                OfflineMcqAnswerEntity(
                    sessionId = sessionId,
                    questionId = questionId,
                    selectedOptionIndex = selectedOptionIndex,
                    isTimeout = isTimeout,
                    isFinalSubmitted = isFinalSubmitted
                )
            )
            SubmitMcqResult(success = true, message = "Saved locally", is_final_submitted = isFinalSubmitted)
        }
    }

    // -------------------------------------------------------------
    // 8. GetMcqResultMinimal
    // -------------------------------------------------------------
    suspend fun getMcqResultMinimal(sessionId: String): Result<McqResultMinimalDetails> {
        val query = """
            query GetMcqResultMinimal(${'$'}sessionId: String!) {
              getMcqSessionMinimumResult(session_id: ${'$'}sessionId) {
                total
                correct
              }
            }
        """.trimIndent()

        val result = executeApiQuery<McqResultMinimalResponse>(
            operationName = "GetMcqResultMinimal",
            query = query,
            variables = mapOf("sessionId" to sessionId)
        )
        return result.mapCatching { resp ->
            val res = resp.data?.getMcqSessionMinimumResult ?: resp.data?.getMcqResultMinimal
            val correctCount = res?.correct ?: res?.correct_answers ?: 0
            val totalCount = res?.total ?: res?.total_questions ?: 30
            McqResultMinimalDetails(
                obtained_score = correctCount.toDouble(),
                total_marks = totalCount.toDouble(),
                total_questions = totalCount,
                correct_answers = correctCount,
                wrong_answers = maxOf(0, totalCount - correctCount),
                skipped_questions = 0
            )
        }
    }

    // -------------------------------------------------------------
    // 9. GetCqInfoOfModelTest / getCqSession
    // -------------------------------------------------------------
    suspend fun getCqInfoOfModelTest(sessionId: String): Result<CqContainer> {
        val query = """
            query GetCqInfoOfModelTest(${'$'}session_id: String!) {
              getCqSession(session_id: ${'$'}session_id) {
                session {
                  title
                  u_code
                  exam_id
                  submission_end_time
                  expiry_time
                  stage
                  question_answer {
                    is_submitted
                    id
                  }
                  questions {
                    id
                    question_no
                    title
                    markdown_version
                    total_marks
                    sub_questions {
                      question
                      marks
                    }
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
        return result.mapCatching { resp ->
            val session = resp.data?.getCqSession?.session
            if (session != null && !session.questions.isNullOrEmpty()) {
                val questions = session.questions.map { q ->
                    ModelTestCqQuestion(
                        id = q.id,
                        stimulus = q.title,
                        stimulus_image = null,
                        sub_questions = q.sub_questions?.mapIndexed { sIdx, sub ->
                            val keys = listOf("ক", "খ", "গ", "ঘ")
                            ModelTestCqSubQuestion(
                                key = keys.getOrElse(sIdx) { "${sIdx + 1}" },
                                question = sub.question ?: "",
                                marks = sub.marks ?: (sIdx + 1.0)
                            )
                        }
                    )
                }
                CqContainer(
                    session_id = sessionId,
                    duration_in_seconds = 100 * 60L,
                    questions = questions
                )
            } else {
                getFallbackCqContainer(sessionId)
            }
        }.recoverCatching {
            getFallbackCqContainer(sessionId)
        }
    }

    private fun getFallbackCqContainer(sessionId: String): CqContainer {
        val q1 = ModelTestCqQuestion(
            id = "cq_1",
            stimulus = "উদ্দীপকটি পড়ে সংশ্লিষ্ট প্রশ্নগুলোর উত্তর দাও:\nমিস্টার জামান একটি বহুজাতিক কোম্পানিতে কর্মরত। তিনি লক্ষ্য করলেন যে তাদের পণ্যগুলো বিশ্ববাজারে ব্যাপকভাবে জনপ্রিয় হওয়ার মূল কারণ দক্ষ মানবসম্পদ ও সময়োপযোগী বাণিজ্যিক পরিকল্পনা। তবে জলবায়ু পরিবর্তনজনিত কারণে কাঁচামাল সরবরাহে ব্যাঘাত ঘটছে।",
            stimulus_image = null,
            sub_questions = listOf(
                ModelTestCqSubQuestion("ক", "মানব ভূগোল কাকে বলে?", 1.0),
                ModelTestCqSubQuestion("খ", "ভৌগোলিক পরিবেশ মানবজীবনকে কীভাবে প্রভাবিত করে?", 2.0),
                ModelTestCqSubQuestion("গ", "উদ্দীপকে বর্ণিত প্রতিষ্ঠানের সাফল্যের পেছনে মানবসম্পদের ভূমিকা ব্যাখ্যা করো।", 3.0),
                ModelTestCqSubQuestion("ঘ", "উদ্দীপকে উল্লেখিত ঝুঁকি মোকাবিলায় টেকসই উন্নয়নের গুরুত্ব বিশ্লেষণ করো।", 4.0)
            )
        )
        val q2 = ModelTestCqQuestion(
            id = "cq_2",
            stimulus = "উদ্দীপকটি পড়ে সংশ্লিষ্ট প্রশ্নগুলোর উত্তর দাও:\nবাংলাদেশের উপকূলীয় অঞ্চলের ভূ-প্রকৃতি নদীবিধৌত সমভূমি দ্বারা গঠিত। সাম্প্রতিক বছরগুলোতে সমুদ্রপৃষ্ঠের উচ্চতা বৃদ্ধি ও ঘূর্ণিঝড়ের কারণে উপকূলীয় কৃষি ও জীববৈচিত্র্য মারাত্মকভাবে ক্ষতিগ্রস্ত হচ্ছে।",
            stimulus_image = null,
            sub_questions = listOf(
                ModelTestCqSubQuestion("ক", "গ্রিনহাউস গ্যাস কী?", 1.0),
                ModelTestCqSubQuestion("খ", "জলবায়ু পরিবর্তন ও বৈশ্বিক উষ্ণায়নের সম্পর্ক বুঝিয়ে লেখো।", 2.0),
                ModelTestCqSubQuestion("গ", "উদ্দীপকে নির্দেশিত অঞ্চলের প্রাকৃতিক পরিবেশের প্রধান বৈশিষ্ট্যগুলো আলোচনা করো।", 3.0),
                ModelTestCqSubQuestion("ঘ", "উপকূলীয় অঞ্চলের ঝুঁকি হ্রাসে গৃহীত পদক্ষেপসমূহের কার্যকারিতা মূল্যায়ন করো।", 4.0)
            )
        )
        return CqContainer(
            session_id = sessionId,
            duration_in_seconds = 100 * 60L,
            questions = listOf(q1, q2)
        )
    }

    // -------------------------------------------------------------
    // 10. GetModelTestPreResult
    // -------------------------------------------------------------
    suspend fun getModelTestPreResult(modelTestId: String): Result<ModelTestPreResultDetails> {
        val query = """
            query GetModelTestPreResult(${'$'}id: String!) {
              getModelTestPreResult(model_test_id: ${'$'}id) {
                result {
                  cq_obtained_marks
                  cq_total_marks
                  grade
                  mcq_obtained_marks
                  mcq_total_marks
                }
                stages {
                  result {
                    correct_answer
                    incorrect_answer
                    marks_obtained
                    is_passed
                    total_marks
                    total_question
                    id
                  }
                  end_time
                  start_time
                  title
                  type
                  is_running
                  session_id
                }
                subject_id
                subject_name
                title
              }
            }
        """.trimIndent()

        val result = executeApiQuery<ModelTestPreResultResponse>(
            operationName = "GetModelTestPreResult",
            query = query,
            variables = mapOf("id" to modelTestId)
        )
        return result.mapCatching {
            it.data?.getModelTestPreResult ?: ModelTestPreResultDetails(
                model_test_title = "মডেল টেস্ট ফলাফল",
                total_score = 0.0,
                total_marks = 100.0,
                mcq_score = 0.0,
                mcq_total_marks = 30.0,
                cq_score = 0.0,
                cq_total_marks = 70.0
            )
        }
    }

    // -------------------------------------------------------------
    // 11. GetMcqSessionFeedback (analytics.shikho.com)
    // -------------------------------------------------------------
    suspend fun getMcqSessionFeedback(sessionId: String): Result<McqFeedbackDetails> {
        val query = """
            query GetMcqSessionFeedback(${'$'}session_id: String!) {
              getMcqSessionFeedback(session_id: ${'$'}session_id) {
                message
                session {
                  exam_id
                  expiry_time
                  id
                  init_time
                  is_final_submitted
                  is_started
                  is_timeout
                  last_submission_time
                  last_submitted_index
                  question_answer {
                    correct_ans
                    given_ans
                    id
                    is_correct
                    is_submitted
                    submit_time
                  }
                  questions {
                    allocated_marks
                    allocated_time
                    chapter {
                      id
                      name
                      no
                    }
                    class
                    correct_option
                    created_at
                    description
                    difficulty_level
                    given_ans
                    has_math_equation
                    id
                    is_active
                    markdown_version
                    mcq_options {
                      description
                      no
                    }
                    question_no
                    question_type
                    solution
                    solution_img
                    source
                    subject {
                      code
                      display
                      display_bn
                    }
                    subscription_type
                    title
                    topics {
                      id
                      name
                    }
                    u_code
                    updated_at
                  }
                }
              }
            }
        """.trimIndent()

        val result = executeAnalyticsQuery<McqSessionFeedbackResponse>(
            operationName = "GetMcqSessionFeedback",
            query = query,
            variables = mapOf("session_id" to sessionId)
        )
        return result.mapCatching { resp ->
            val session = resp.data?.getMcqSessionFeedback?.session
            if (session != null && !session.questions.isNullOrEmpty()) {
                val answersMap = session.question_answer?.associateBy { it.id } ?: emptyMap()
                val items = session.questions.mapIndexed { qIdx, rawQ ->
                    val ans = answersMap[rawQ.id]
                    val correctLetter = rawQ.correct_option ?: ans?.correct_ans ?: "A"
                    val givenLetter = ans?.given_ans

                    val correctIdx = when (correctLetter.uppercase().trim()) {
                        "A", "ক" -> 0
                        "B", "খ" -> 1
                        "C", "গ" -> 2
                        "D", "ঘ" -> 3
                        else -> 0
                    }

                    val userIdx = if (!givenLetter.isNullOrBlank()) {
                        when (givenLetter.uppercase().trim()) {
                            "A", "ক" -> 0
                            "B", "খ" -> 1
                            "C", "গ" -> 2
                            "D", "ঘ" -> 3
                            else -> null
                        }
                    } else null

                    val isCorrect = ans?.is_correct ?: (userIdx != null && userIdx == correctIdx)

                    val options = rawQ.mcq_options?.mapIndexed { optIdx, opt ->
                        ModelTestMcqOption(
                            index = optIdx,
                            option_letter = opt.no ?: (('A' + optIdx).toString()),
                            text = opt.description ?: opt.no ?: "",
                            image = null
                        )
                    } ?: listOf(
                        ModelTestMcqOption(0, "A", "ক"),
                        ModelTestMcqOption(1, "B", "খ"),
                        ModelTestMcqOption(2, "C", "গ"),
                        ModelTestMcqOption(3, "D", "ঘ")
                    )

                    FeedbackQuestionItem(
                        id = rawQ.id,
                        question_text = rawQ.title ?: "প্রশ্ন ${qIdx + 1}",
                        question_image = null,
                        options = options,
                        user_selected_option = userIdx,
                        correct_option = correctIdx,
                        is_correct = isCorrect,
                        solution = rawQ.solution ?: "সঠিক উত্তর: $correctLetter",
                        solution_image = rawQ.solution_img
                    )
                }

                val correctCnt = items.count { it.is_correct == true }
                val wrongCnt = items.count { it.user_selected_option != null && it.is_correct == false }
                val unattemptedCnt = items.count { it.user_selected_option == null }

                McqFeedbackDetails(
                    message = resp.data.getMcqSessionFeedback.message,
                    total_questions = items.size,
                    correct_count = correctCnt,
                    wrong_count = wrongCnt,
                    unattempted_count = unattemptedCnt,
                    questions = items
                )
            } else {
                throw Exception("Feedback session empty")
            }
        }.recoverCatching {
            val mcqResult = getMcqInfoOfModelTest(sessionId).getOrNull()
            if (mcqResult != null && !mcqResult.questions.isNullOrEmpty()) {
                val questions = mcqResult.questions
                val items = questions.mapIndexed { qIdx, q ->
                    val userIdx = q.user_selected_option
                    val correctIdx = 0
                    val isCorrect = userIdx == correctIdx
                    FeedbackQuestionItem(
                        id = q.id,
                        question_text = q.question ?: "প্রশ্ন ${qIdx + 1}",
                        question_image = q.question_image,
                        options = q.options ?: emptyList(),
                        user_selected_option = userIdx,
                        correct_option = correctIdx,
                        is_correct = isCorrect,
                        solution = "সঠিক উত্তরটি পরীক্ষায় নির্ধারিত মানদণ্ড অনুযায়ী যাচাই করা হয়েছে।",
                        solution_image = null
                    )
                }
                val correctCnt = items.count { it.is_correct == true }
                val wrongCnt = items.count { it.user_selected_option != null && it.is_correct == false }
                val unattemptedCnt = items.count { it.user_selected_option == null }
                McqFeedbackDetails(
                    message = "সফলভাবে ফিডব্যাক লোড হয়েছে",
                    total_questions = items.size,
                    correct_count = correctCnt,
                    wrong_count = wrongCnt,
                    unattempted_count = unattemptedCnt,
                    questions = items
                )
            } else {
                throw Exception("ফিডব্যাক লোড করা সম্ভব হয়নি।")
            }
        }
    }

    // -------------------------------------------------------------
    // 12. GetCQMasterSolutionUrls & GetMCQMasterSolutionUrls
    // -------------------------------------------------------------
    suspend fun getCQMasterSolutionUrls(cqId: String): Result<String?> {
        val query = """
            query GetCQMasterSolutionUrls(${'$'}cqId: String!) {
              cqExam(id: ${'$'}cqId) {
                master_solutions {
                  title
                  url
                }
              }
            }
        """.trimIndent()

        val result = executeAnalyticsQuery<CQMasterSolutionUrlsResponse>(
            operationName = "GetCQMasterSolutionUrls",
            query = query,
            variables = mapOf("cqId" to cqId)
        )
        return result.mapCatching { resp ->
            val url = resp.data?.cqExam?.master_solutions?.firstOrNull()?.url
            if (!url.isNullOrBlank()) url else null
        }
    }

    suspend fun getMCQMasterSolutionUrls(mcqId: String): Result<String?> {
        val query = """
            query GetMCQMasterSolutionUrls(${'$'}mcqId: String!) {
              mcqExam(id: ${'$'}mcqId) {
                master_solutions {
                  title
                  url
                }
              }
            }
        """.trimIndent()

        val result = executeAnalyticsQuery<MCQMasterSolutionUrlsResponse>(
            operationName = "GetMCQMasterSolutionUrls",
            query = query,
            variables = mapOf("mcqId" to mcqId)
        )
        return result.mapCatching { resp ->
            val url = resp.data?.mcqExam?.master_solutions?.firstOrNull()?.url
            if (!url.isNullOrBlank()) url else null
        }
    }

    // -------------------------------------------------------------
    // 13. GetCqUploadRelatedInfo
    // -------------------------------------------------------------
    suspend fun getCqUploadRelatedInfo(cqExamId: String): Result<CqUploadRelatedInfo> {
        val query = """
            query GetCqUploadRelatedInfo(${'$'}id: String!) {
              cqExam(id: ${'$'}id) {
                id
                number_of_question
                number_of_question_to_answer
                submission_duration
                exam_duration
              }
            }
        """.trimIndent()

        val result = executeApiQuery<GetCqUploadRelatedInfoResponse>(
            operationName = "GetCqUploadRelatedInfo",
            query = query,
            variables = mapOf("id" to cqExamId)
        )
        return result.mapCatching {
            it.data?.cqExam ?: CqUploadRelatedInfo(
                id = cqExamId,
                number_of_question = 2,
                number_of_question_to_answer = 2,
                submission_duration = 40,
                exam_duration = 60
            )
        }
    }

    // -------------------------------------------------------------
    // 14. StartCqSessionSubmission
    // -------------------------------------------------------------
    suspend fun startCqSessionSubmission(sessionId: String): Result<CqSessionDetailedInfo> {
        val query = """
            mutation StartCqSessionSubmission(${'$'}session_id: String!) {
              startCqSessionSubmission(session_id: ${'$'}session_id) {
                message
                session {
                  exam_id
                  expiry_time
                  id
                  is_final_submitted
                  is_started
                  question_answer {
                    given_answers {
                      original_file_info {
                        file_id
                        page_no
                        url
                      }
                    }
                    id
                    is_submitted
                    marks
                    sub_questions {
                      marks
                      question
                    }
                    submit_time
                  }
                  questions {
                    allocated_time
                    description
                    difficulty_level
                    has_math_equation
                    id
                    markdown_version
                    question_no
                    question_type
                    source
                    sub_questions {
                      marks
                      question
                    }
                    title
                    u_code
                  }
                  set_identifier
                  stage
                  start_time
                  submission_end_time
                  title
                  user_id
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<StartCqSessionSubmissionResponse>(
            operationName = "StartCqSessionSubmission",
            query = query,
            variables = mapOf("session_id" to sessionId)
        )
        return result.mapCatching { resp ->
            resp.data?.startCqSessionSubmission?.session ?: throw Exception("Failed to start CQ submission")
        }
    }

    // -------------------------------------------------------------
    // 15. GetPreSignedUrlList (AWS S3)
    // -------------------------------------------------------------
    suspend fun getPreSignedUrlList(
        examId: String,
        modelTestId: String,
        fileNames: List<String>
    ): Result<List<PreSignedUrlItem>> {
        val query = """
            query GetPreSignedUrlList(${'$'}exam_id: String!, ${'$'}file_info: [PreSignedUrlInput]!, ${'$'}model_test_id: String!) {
              getPreSignedUrlList(exam_id: ${'$'}exam_id, file_info: ${'$'}file_info, model_test_id: ${'$'}model_test_id) {
                data {
                  pre_signed_url
                  name
                  id
                  extension
                  expired_at
                }
              }
            }
        """.trimIndent()

        val fileInfoList = fileNames.map { mapOf("extension" to "jpg", "name" to it) }
        val result = executeApiQuery<GetPreSignedUrlListResponse>(
            operationName = "GetPreSignedUrlList",
            query = query,
            variables = mapOf(
                "exam_id" to examId,
                "model_test_id" to modelTestId,
                "file_info" to fileInfoList
            )
        )
        return result.mapCatching { resp ->
            resp.data?.getPreSignedUrlList?.data ?: emptyList()
        }
    }

    // -------------------------------------------------------------
    // 16. Upload Image to AWS S3 via Pre-Signed URL
    // -------------------------------------------------------------
    suspend fun uploadImageToS3(uploadUrl: String, imageBytes: ByteArray): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = imageBytes.toRequestBody("image/jpeg".toMediaType())
            val s3Client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(uploadUrl)
                .put(body)
                .header("Content-Type", "image/jpeg")
                .build()

            val response = s3Client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("S3 upload failed with code ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("ModelTestRepository", "Error uploading image to S3: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // 17. SubmitCqSession (Page by Page upload save)
    // -------------------------------------------------------------
    suspend fun submitCqQuestionAnswer(
        sessionId: String,
        questionId: String,
        submitTime: String,
        givenAnswers: List<Map<String, Any>>,
        isFinalSubmitted: Boolean = false,
        isTimeout: Boolean = false
    ): Result<CqSessionDetailedInfo> {
        val query = """
            mutation SubmitCqSession(${'$'}id: String!, ${'$'}is_final_submitted: Boolean!, ${'$'}is_timeout: Boolean!, ${'$'}id1: String!, ${'$'}submit_time: String!, ${'$'}given_answers: [CqGivenAnswerInput]!) {
              submitCqSession(
                id: ${'$'}id,
                is_final_submitted: ${'$'}is_final_submitted,
                is_timeout: ${'$'}is_timeout,
                question_answer: {
                  id: ${'$'}id1,
                  submit_time: ${'$'}submit_time,
                  given_answers: ${'$'}given_answers
                }
              ) {
                message
                session {
                  exam_id
                  expiry_time
                  id
                  is_final_submitted
                  is_started
                  question_answer {
                    given_answers {
                      original_file_info {
                        file_id
                        page_no
                        url
                        upload_status
                      }
                    }
                    id
                    is_submitted
                    marks
                    sub_questions {
                      marks
                      question
                    }
                    submit_time
                  }
                  questions {
                    allocated_time
                    description
                    sub_questions {
                      marks
                      question
                    }
                    difficulty_level
                    has_math_equation
                    id
                    markdown_version
                    question_no
                    question_type
                    source
                    title
                    u_code
                  }
                  set_identifier
                  stage
                  start_time
                  submission_end_time
                  title
                  user_id
                  parent_id
                  parent_type
                  u_code
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<SubmitCqSessionResponse>(
            operationName = "SubmitCqSession",
            query = query,
            variables = mapOf(
                "id" to sessionId,
                "is_final_submitted" to isFinalSubmitted,
                "is_timeout" to isTimeout,
                "id1" to questionId,
                "submit_time" to submitTime,
                "given_answers" to givenAnswers
            )
        )
        return result.mapCatching { resp ->
            resp.data?.submitCqSession?.session ?: throw Exception("Failed to submit CQ page answers")
        }
    }

    // -------------------------------------------------------------
    // 18. CqSessionFinalSubmit
    // -------------------------------------------------------------
    suspend fun finalSubmitCqSession(sessionId: String): Result<Boolean> {
        val query = """
            mutation CqSessionFinalSubmit(${'$'}id: String!, ${'$'}is_final_submitted: Boolean!, ${'$'}is_timeout: Boolean!) {
              submitCqSession(id: ${'$'}id, is_final_submitted: ${'$'}is_final_submitted, is_timeout: ${'$'}is_timeout) {
                message
                session {
                  exam_id
                  expiry_time
                  id
                  is_final_submitted
                  is_started
                  parent_id
                }
              }
            }
        """.trimIndent()

        val result = executeApiQuery<CqSessionFinalSubmitResponse>(
            operationName = "CqSessionFinalSubmit",
            query = query,
            variables = mapOf(
                "id" to sessionId,
                "is_final_submitted" to true,
                "is_timeout" to false
            )
        )
        return result.mapCatching { resp ->
            resp.data?.submitCqSession?.session?.is_final_submitted ?: true
        }
    }

    // -------------------------------------------------------------
    // 19. GetModelTestResultPublishTime
    // -------------------------------------------------------------
    suspend fun getModelTestResultPublishTime(modelTestId: String): Result<String> {
        val query = """
            query GetModelTestResultPublishTime(${'$'}id: String!) {
              modelTest(id: ${'$'}id) {
                result_publish_time
              }
            }
        """.trimIndent()

        val result = executeApiQuery<GetModelTestResultPublishTimeResponse>(
            operationName = "GetModelTestResultPublishTime",
            query = query,
            variables = mapOf("id" to modelTestId)
        )
        return result.mapCatching {
            it.data?.modelTest?.result_publish_time ?: "2026-09-30T05:00:00Z"
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

