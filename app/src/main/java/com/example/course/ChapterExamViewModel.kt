package com.example.course

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExamStage {
    INTRO,
    RULES,
    QUESTIONS,
    SUBMITTING,
    PERFORMANCE,
    SOLUTIONS
}

data class ChapterExamUiState(
    val stage: ExamStage = ExamStage.INTRO,
    val sessionId: String = "",
    val lessonId: String = "",
    val examInfo: LiveExamSessionDetails? = null,
    val questions: List<LiveExamQuestionItem> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val userAnswers: Map<String, String> = emptyMap(), // questionId -> "A", "B", "C", "D"
    val timeSpentPerQuestion: Map<String, Double> = emptyMap(), // questionId -> seconds
    val remainingSeconds: Long = 0,
    val performanceSummary: ExamResultSummary? = null,
    val pdfAttachmentUrl: String? = null,
    val solutions: List<ExamAnswerSolutionItem> = emptyList(),
    val solutionIndex: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ChapterExamViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChapterExamUiState())
    val uiState: StateFlow<ChapterExamUiState> = _uiState.asStateFlow()

    private var questionStartTimeMs: Long = 0L
    private var timerJob: Job? = null

    fun loadExamInfo(sessionId: String, lessonId: String) {
        _uiState.update { it.copy(sessionId = sessionId, lessonId = lessonId, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            var info: LiveExamSessionDetails? = null

            // 1. Try with sessionId
            if (sessionId.isNotBlank()) {
                try {
                    val query = GraphQlQuery(
                        operationName = "GetLiveExamInfo",
                        query = """
                            query GetLiveExamInfo(${'$'}session_id: String!) {
                              liveExamSession(id: ${'$'}session_id) {
                                start_time
                                end_time
                                title
                                markdown_version
                                chapters { id name no }
                                subject { display }
                                total_number_of_question
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("session_id" to sessionId)
                    )
                    val response = apiService.getLiveExamInfo(query)
                    info = response.data?.liveExamSession
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading exam info with sessionId: $sessionId", e)
                }
            }

            // 2. Try with lessonId if info is still null
            if (info == null && lessonId.isNotBlank() && lessonId != sessionId) {
                try {
                    val query = GraphQlQuery(
                        operationName = "GetLiveExamInfo",
                        query = """
                            query GetLiveExamInfo(${'$'}session_id: String!) {
                              liveExamSession(id: ${'$'}session_id) {
                                start_time
                                end_time
                                title
                                markdown_version
                                chapters { id name no }
                                subject { display }
                                total_number_of_question
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("session_id" to lessonId)
                    )
                    val response = apiService.getLiveExamInfo(query)
                    info = response.data?.liveExamSession
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading exam info with lessonId: $lessonId", e)
                }
            }

            if (info != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        examInfo = info,
                        stage = ExamStage.INTRO,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "পরীক্ষার তথ্য লোড করা সম্ভব হয়নি।"
                    )
                }
            }
        }
    }

    fun proceedToRules() {
        _uiState.update { it.copy(stage = ExamStage.RULES) }
    }

    fun startExamQuestions() {
        val sId = _uiState.value.sessionId
        val lId = _uiState.value.lessonId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            var fetchedQuestions: List<LiveExamQuestionItem> = emptyList()

            // 1. Try with sId
            if (sId.isNotBlank()) {
                try {
                    val query = GraphQlQuery(
                        operationName = "GetLiveQuestions",
                        query = """
                            query GetLiveQuestions(${'$'}live_exam_session_id: String) {
                              academicProgramLiveExamQuestions(live_exam_session_id: ${'$'}live_exam_session_id) {
                                data {
                                  id
                                  title
                                  solution
                                  markdown_version
                                  mcq_options { description no }
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("live_exam_session_id" to sId)
                    )
                    val response = apiService.getLiveQuestions(query)
                    fetchedQuestions = response.data?.academicProgramLiveExamQuestions?.data ?: emptyList()
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading questions with sId: $sId", e)
                }
            }

            // 2. If empty, try with lId
            if (fetchedQuestions.isEmpty() && lId.isNotBlank() && lId != sId) {
                try {
                    val query = GraphQlQuery(
                        operationName = "GetLiveQuestions",
                        query = """
                            query GetLiveQuestions(${'$'}live_exam_session_id: String) {
                              academicProgramLiveExamQuestions(live_exam_session_id: ${'$'}live_exam_session_id) {
                                data {
                                  id
                                  title
                                  solution
                                  markdown_version
                                  mcq_options { description no }
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("live_exam_session_id" to lId)
                    )
                    val response = apiService.getLiveQuestions(query)
                    fetchedQuestions = response.data?.academicProgramLiveExamQuestions?.data ?: emptyList()
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading questions with lId: $lId", e)
                }
            }

            if (fetchedQuestions.isNotEmpty()) {
                val totalCount = fetchedQuestions.size
                val defaultDurationSeconds = (totalCount * 60).toLong().coerceAtLeast(300L)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        questions = fetchedQuestions,
                        currentQuestionIndex = 0,
                        remainingSeconds = defaultDurationSeconds,
                        stage = ExamStage.QUESTIONS,
                        errorMessage = null
                    )
                }
                questionStartTimeMs = System.currentTimeMillis()
                startTimer()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "প্রশ্ন লোড করা সম্ভব হয়নি। অনুগ্রহ করে পরবর্তীতে আবার চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0) {
                delay(1000L)
                _uiState.update { current ->
                    if (current.remainingSeconds > 1) {
                        current.copy(remainingSeconds = current.remainingSeconds - 1)
                    } else {
                        current.copy(remainingSeconds = 0)
                    }
                }
                if (_uiState.value.remainingSeconds == 0L) {
                    submitExam()
                    break
                }
            }
        }
    }

    fun selectOption(optionNo: String) {
        val currentQ = _uiState.value.questions.getOrNull(_uiState.value.currentQuestionIndex) ?: return
        val currentAnswers = _uiState.value.userAnswers.toMutableMap()
        currentAnswers[currentQ.id ?: ""] = optionNo
        _uiState.update { it.copy(userAnswers = currentAnswers) }
    }

    fun recordTimeForCurrentQuestion() {
        val currentQ = _uiState.value.questions.getOrNull(_uiState.value.currentQuestionIndex) ?: return
        val elapsedSec = (System.currentTimeMillis() - questionStartTimeMs) / 1000.0
        val qId = currentQ.id ?: ""
        val timeMap = _uiState.value.timeSpentPerQuestion.toMutableMap()
        val prev = timeMap[qId] ?: 0.0
        timeMap[qId] = prev + elapsedSec
        _uiState.update { it.copy(timeSpentPerQuestion = timeMap) }
        questionStartTimeMs = System.currentTimeMillis()
    }

    fun nextQuestion() {
        recordTimeForCurrentQuestion()
        val nextIdx = _uiState.value.currentQuestionIndex + 1
        if (nextIdx < _uiState.value.questions.size) {
            _uiState.update { it.copy(currentQuestionIndex = nextIdx) }
        } else {
            submitExam()
        }
    }

    fun previousQuestion() {
        recordTimeForCurrentQuestion()
        val prevIdx = _uiState.value.currentQuestionIndex - 1
        if (prevIdx >= 0) {
            _uiState.update { it.copy(currentQuestionIndex = prevIdx) }
        }
    }

    fun submitExam() {
        timerJob?.cancel()
        recordTimeForCurrentQuestion()

        val state = _uiState.value
        val answersPayload = state.questions.map { q ->
            val qId = q.id ?: ""
            val ans = state.userAnswers[qId] ?: ""
            val time = state.timeSpentPerQuestion[qId] ?: 2.5
            mapOf(
                "question_id" to qId,
                "given_ans" to ans,
                "taken_time" to time
            )
        }

        _uiState.update { it.copy(stage = ExamStage.SUBMITTING, isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val mutation = GraphQlQuery(
                    operationName = "SubmitLiveExam",
                    query = """
                        mutation SubmitLiveExam(${'$'}exam_session_id: String!, ${'$'}lesson_id: String, ${'$'}question_type: LiveExamSessionTypeEnum!, ${'$'}answers: [AcpLiveExamQuestionAnswerInputObject]!) {
                          acpLiveExamResult(answers: ${'$'}answers, exam_session_id: ${'$'}exam_session_id, lesson_id: ${'$'}lesson_id, question_type: ${'$'}question_type) {
                            code
                            message
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf(
                        "exam_session_id" to state.sessionId,
                        "lesson_id" to state.lessonId.ifBlank { null },
                        "question_type" to "MCQ",
                        "answers" to answersPayload
                    )
                )
                apiService.submitLiveExam(mutation)
            } catch (e: Exception) {
                Log.e("ChapterExamVM", "Error submitting live exam: ${state.sessionId}", e)
            }

            // Mark exam completed
            if (state.lessonId.isNotBlank()) sessionManager.markLessonCompleted(state.lessonId)
            if (state.sessionId.isNotBlank()) sessionManager.markLessonCompleted(state.sessionId)
            val examId = state.lessonId.ifBlank { state.sessionId }
            completedItemRepository?.markCompleted(
                itemId = examId,
                itemType = "EXAM",
                title = state.examInfo?.title ?: "পরিক্ষা"
            )

            // Fetch performance analysis
            loadPerformanceAnalysis(state.sessionId, state.lessonId)
        }
    }

    private fun loadPerformanceAnalysis(sessionId: String, lessonId: String) {
        viewModelScope.launch {
            var summary: ExamResultSummary? = null
            var pdfUrl: String? = null

            if (sessionId.isNotBlank()) {
                try {
                    val queryPerf = GraphQlQuery(
                        operationName = "GetLiveExamPerformanceAnalysis",
                        query = """
                            query GetLiveExamPerformanceAnalysis(${'$'}exam_session_id: String!) {
                              acpLiveExamResultHistory(exam_session_id: ${'$'}exam_session_id) {
                                result_summary {
                                  correct_ans
                                  incorrect_ans
                                  total_length_of_exam
                                  total_question
                                  total_time_spent
                                  unanswered
                                }
                                live_exam_session_id
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("exam_session_id" to sessionId)
                    )
                    val perfRes = apiService.getLiveExamPerformance(queryPerf)
                    summary = perfRes.data?.acpLiveExamResultHistory?.result_summary
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading performance with sessionId: $sessionId", e)
                }
            }

            if (summary == null && lessonId.isNotBlank() && lessonId != sessionId) {
                try {
                    val queryPerf = GraphQlQuery(
                        operationName = "GetLiveExamPerformanceAnalysis",
                        query = """
                            query GetLiveExamPerformanceAnalysis(${'$'}exam_session_id: String!) {
                              acpLiveExamResultHistory(exam_session_id: ${'$'}exam_session_id) {
                                result_summary {
                                  correct_ans
                                  incorrect_ans
                                  total_length_of_exam
                                  total_question
                                  total_time_spent
                                  unanswered
                                }
                                live_exam_session_id
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("exam_session_id" to lessonId)
                    )
                    val perfRes = apiService.getLiveExamPerformance(queryPerf)
                    summary = perfRes.data?.acpLiveExamResultHistory?.result_summary
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading performance with lessonId: $lessonId", e)
                }
            }

            val targetModuleId = lessonId.ifBlank { sessionId }
            if (targetModuleId.isNotBlank()) {
                try {
                    val queryAtt = GraphQlQuery(
                        operationName = "ResourceAttachmentsLiveExam",
                        query = """
                            query ResourceAttachmentsLiveExam(${'$'}module_id: String!, ${'$'}module_name: AttachmentModuleEnum!) {
                              attachmentList(module_id: ${'$'}module_id, module_name: ${'$'}module_name) {
                                data { id description title url }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf(
                            "module_id" to targetModuleId,
                            "module_name" to "LiveExam"
                        )
                    )
                    val attRes = apiService.getResourceAttachments(queryAtt)
                    pdfUrl = attRes.data?.attachmentList?.data?.firstOrNull()?.url
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading resource attachments for $targetModuleId", e)
                }
            }

            // Calculate locally if summary is null (using real user test data)
            if (summary == null) {
                val questions = _uiState.value.questions
                val userAns = _uiState.value.userAnswers
                var correct = 0
                var incorrect = 0
                var unanswered = 0

                questions.forEach { q ->
                    val given = userAns[q.id] ?: ""
                    val sol = q.solution ?: ""
                    val correctOpt = q.correct_option ?: deduceCorrectOption(sol, q.mcq_options, q.correct_option, given)
                    if (given.isBlank()) {
                        unanswered++
                    } else if (com.example.utils.McqAnswerUtils.isAnswerCorrect(given, correctOpt)) {
                        correct++
                    } else {
                        incorrect++
                    }
                }

                val totalQuestionsCount = questions.size
                val totalExamLengthSec = _uiState.value.examInfo?.total_number_of_question?.let { (it * 60).toString() }
                    ?: (totalQuestionsCount * 60).toString()
                val totalTimeSpentSec = _uiState.value.timeSpentPerQuestion.values.sum()
                val formattedTimeSpent = String.format(java.util.Locale.US, "%.1f", totalTimeSpentSec)

                summary = ExamResultSummary(
                    correct_ans = correct.toString(),
                    incorrect_ans = incorrect.toString(),
                    unanswered = unanswered.toString(),
                    total_question = totalQuestionsCount.toString(),
                    total_length_of_exam = totalExamLengthSec,
                    total_time_spent = formattedTimeSpent
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    performanceSummary = summary,
                    pdfAttachmentUrl = pdfUrl,
                    stage = ExamStage.PERFORMANCE,
                    errorMessage = null
                )
            }
        }
    }

    fun loadSolutions() {
        val sId = _uiState.value.sessionId
        val lId = _uiState.value.lessonId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            var items: List<ExamAnswerSolutionItem> = emptyList()

            if (sId.isNotBlank()) {
                try {
                    val query = GraphQlQuery(
                        operationName = "GetLiveExamSolutions",
                        query = """
                            query GetLiveExamSolutions(${'$'}exam_session_id: String!) {
                              acpLiveExamResultHistory(exam_session_id: ${'$'}exam_session_id) {
                                answers {
                                  question { id title solution markdown_version correct_option }
                                  given_ans
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("exam_session_id" to sId)
                    )
                    val res = apiService.getLiveExamSolutions(query)
                    items = res.data?.acpLiveExamResultHistory?.answers ?: emptyList()
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading solutions with sId: $sId", e)
                }
            }

            if (items.isEmpty() && lId.isNotBlank() && lId != sId) {
                try {
                    val query = GraphQlQuery(
                        operationName = "GetLiveExamSolutions",
                        query = """
                            query GetLiveExamSolutions(${'$'}exam_session_id: String!) {
                              acpLiveExamResultHistory(exam_session_id: ${'$'}exam_session_id) {
                                answers {
                                  question { id title solution markdown_version correct_option }
                                  given_ans
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("exam_session_id" to lId)
                    )
                    val res = apiService.getLiveExamSolutions(query)
                    items = res.data?.acpLiveExamResultHistory?.answers ?: emptyList()
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error loading solutions with lId: $lId", e)
                }
            }

            val finalSolutions = if (items.isNotEmpty()) {
                val qMap = _uiState.value.questions.associateBy { it.id }
                items.map { item ->
                    val origQ = qMap[item.question?.id]
                    val mergedOptions = item.question?.mcq_options?.ifEmpty { null } ?: origQ?.mcq_options
                    val solText = item.question?.solution?.ifBlank { null } ?: origQ?.solution ?: ""
                    val directCorrect = item.question?.correct_option ?: origQ?.correct_option
                    val resolvedCorrect = directCorrect ?: deduceCorrectOption(solText, mergedOptions, directCorrect, item.given_ans)
                    item.copy(
                        question = item.question?.copy(
                            mcq_options = mergedOptions,
                            solution = solText,
                            correct_option = resolvedCorrect
                        ) ?: origQ?.copy(correct_option = resolvedCorrect)
                    )
                }
            } else {
                buildLocalSolutions()
            }

            if (finalSolutions.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        solutions = finalSolutions,
                        solutionIndex = 0,
                        stage = ExamStage.SOLUTIONS,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "সমাধান লোড করা সম্ভব হয়নি।"
                    )
                }
            }
        }
    }

    private fun buildLocalSolutions(): List<ExamAnswerSolutionItem> {
        val questions = _uiState.value.questions
        val userAns = _uiState.value.userAnswers
        return questions.map { q ->
            val given = userAns[q.id] ?: ""
            val correctOpt = q.correct_option ?: deduceCorrectOption(q.solution ?: "", q.mcq_options, q.correct_option, given)
            ExamAnswerSolutionItem(
                given_ans = given,
                question = q.copy(correct_option = correctOpt)
            )
        }
    }

    fun nextSolution() {
        val next = _uiState.value.solutionIndex + 1
        if (next < _uiState.value.solutions.size) {
            _uiState.update { it.copy(solutionIndex = next) }
        }
    }

    fun previousSolution() {
        val prev = _uiState.value.solutionIndex - 1
        if (prev >= 0) {
            _uiState.update { it.copy(solutionIndex = prev) }
        }
    }

    private fun deduceCorrectOption(
        solutionText: String,
        options: List<McqOptionItem>?,
        directCorrectOption: String? = null,
        userGivenAns: String? = null
    ): String {
        val descs = options?.map { it.description }
        val userIdx = com.example.utils.McqAnswerUtils.normalizeToOptionIndex(userGivenAns)
        val resolvedIdx = com.example.utils.McqAnswerUtils.resolveCorrectOptionIndex(
            directCorrectOption = directCorrectOption,
            ansCorrectOption = null,
            solutionText = solutionText,
            optionsDescriptions = descs,
            userSelectedOptionIndex = userIdx,
            isUserMarkedCorrectByServer = null
        )
        val matchedOpt = options?.getOrNull(resolvedIdx)
        return matchedOpt?.no ?: com.example.utils.McqAnswerUtils.indexToBengaliLetter(resolvedIdx)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class ChapterExamViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChapterExamViewModel::class.java)) {
            return ChapterExamViewModel(apiService, sessionManager, completedItemRepository = completedItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
