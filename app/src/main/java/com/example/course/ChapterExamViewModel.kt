package com.example.course

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import com.example.quiz.PracticeQuizRepository
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
    val chapterId: String = "",
    val chapterName: String = "",
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val examTitle: String = "",
    val isPracticeMcqBacking: Boolean = false,
    val activeMcqSessionId: String = "",
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

    private val practiceQuizRepository = PracticeQuizRepository(apiService, sessionManager)

    private var questionStartTimeMs: Long = 0L
    private var timerJob: Job? = null

    private fun mapPracticeQuestionToLiveQuestion(q: PracticeQuizQuestionItem): LiveExamQuestionItem {
        return LiveExamQuestionItem(
            id = q.id,
            title = q.title,
            solution = q.effectiveExplanation,
            markdown_version = q.markdown_version ?: "2",
            mcq_options = q.mcq_options,
            correct_option = q.correct_option
        )
    }

    private fun findCachedLesson(sessionId: String, lessonId: String): StudentLessonItem? {
        return LessonCacheManager.getLessonById(lessonId)
            ?: LessonCacheManager.getLessonById(sessionId)
    }

    private fun findSiblingLiveExamCandidateIds(
        sessionId: String,
        lessonId: String,
        chapterId: String,
        chapterName: String,
        subjectTitle: String,
        examTitle: String
    ): List<String> {
        val cachedLesson = findCachedLesson(sessionId, lessonId)
        val candidateChapterIds = listOfNotNull(
            chapterId.takeIf { it.isNotBlank() },
            cachedLesson?.chapter_id?.takeIf { it.isNotBlank() },
            cachedLesson?.live_class?.chapter_id?.takeIf { it.isNotBlank() }
        ).distinct()

        val effChapterName = chapterName.ifBlank {
            cachedLesson?.live_class?.chapter_name?.takeIf { it.isNotBlank() }
                ?: LessonCacheManager.cleanChapterTitle(examTitle.ifBlank { cachedLesson?.title ?: "" })
        }
        val effSubjectTitle = subjectTitle.ifBlank {
            cachedLesson?.subject_name ?: cachedLesson?.live_class?.subject_name ?: ""
        }

        val chapterLessons = LessonCacheManager.findLessonsForChapter(
            candidateChapterIds = candidateChapterIds,
            chapterName = effChapterName,
            subjectTitle = effSubjectTitle,
            programId = cachedLesson?.program_id
        )

        val liveExamIds = mutableListOf<String>()
        chapterLessons.filter { it.isLiveExam || it.content_type.equals("LiveExam", ignoreCase = true) || it.isExam }
            .sortedByDescending { it.isLiveExam || it.content_type.equals("LiveExam", ignoreCase = true) }
            .forEach { item ->
                item.session_id?.takeIf { it.isNotBlank() }?.let { liveExamIds.add(it) }
                item.content_id?.takeIf { it.isNotBlank() }?.let { liveExamIds.add(it) }
                item.id.takeIf { it.isNotBlank() }?.let { liveExamIds.add(it) }
            }
        return liveExamIds.distinct()
    }

    private suspend fun queryLiveExamInfoById(candidateId: String): LiveExamSessionDetails? {
        if (candidateId.isBlank()) return null
        return try {
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
                variables = mapOf("session_id" to candidateId)
            )
            val response = apiService.getLiveExamInfo(query)
            response.data?.liveExamSession
        } catch (e: Exception) {
            Log.w("ChapterExamVM", "GetLiveExamInfo failed for candidateId=$candidateId: ${e.message}")
            null
        }
    }

    private suspend fun queryLiveQuestionsById(candidateId: String): List<LiveExamQuestionItem> {
        if (candidateId.isBlank()) return emptyList()
        return try {
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
                variables = mapOf("live_exam_session_id" to candidateId)
            )
            val response = apiService.getLiveQuestions(query)
            response.data?.academicProgramLiveExamQuestions?.data ?: emptyList()
        } catch (e: Exception) {
            Log.w("ChapterExamVM", "GetLiveQuestions failed for candidateId=$candidateId: ${e.message}")
            emptyList()
        }
    }

    fun loadExamInfo(
        sessionId: String,
        lessonId: String,
        chapterId: String = "",
        chapterName: String = "",
        subjectCode: String = "",
        subjectTitle: String = "",
        examTitle: String = ""
    ) {
        val cachedLesson = findCachedLesson(sessionId, lessonId)
        val effChapterId = chapterId.ifBlank {
            cachedLesson?.chapter_id ?: cachedLesson?.live_class?.chapter_id ?: ""
        }
        val effChapterName = chapterName.ifBlank {
            cachedLesson?.live_class?.chapter_name
                ?: LessonCacheManager.cleanChapterTitle(examTitle.ifBlank { cachedLesson?.title ?: "" })
        }
        val effSubjectCode = subjectCode.ifBlank {
            cachedLesson?.subject_id ?: cachedLesson?.live_class?.subject_id ?: ""
        }
        val effSubjectTitle = subjectTitle.ifBlank {
            cachedLesson?.subject_name ?: cachedLesson?.live_class?.subject_name ?: ""
        }
        val effExamTitle = examTitle.ifBlank {
            cachedLesson?.title ?: effChapterName.ifBlank { "অধ্যায় পরীক্ষা" }
        }

        _uiState.update {
            it.copy(
                sessionId = sessionId,
                lessonId = lessonId,
                chapterId = effChapterId,
                chapterName = effChapterName,
                subjectCode = effSubjectCode,
                subjectTitle = effSubjectTitle,
                examTitle = effExamTitle,
                isPracticeMcqBacking = false,
                activeMcqSessionId = "",
                questions = emptyList(),
                userAnswers = emptyMap(),
                timeSpentPerQuestion = emptyMap(),
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            var info: LiveExamSessionDetails? = null
            var resolvedLiveSessionId = sessionId
            var preloadedQuestions: List<LiveExamQuestionItem> = emptyList()
            var isMcqBacking = false
            var mcqSessionId = ""

            val directCandidates = listOfNotNull(
                sessionId.takeIf { it.isNotBlank() },
                lessonId.takeIf { it.isNotBlank() },
                cachedLesson?.content_id?.takeIf { it.isNotBlank() },
                cachedLesson?.session_id?.takeIf { it.isNotBlank() }
            ).distinct()

            // 1. Try direct candidate IDs with GetLiveExamInfo
            for (candId in directCandidates) {
                val found = queryLiveExamInfoById(candId)
                if (found != null) {
                    info = found
                    resolvedLiveSessionId = candId
                    break
                }
            }

            // 2. If still null, check sibling LiveExam IDs in the same chapter
            if (info == null) {
                val siblingIds = findSiblingLiveExamCandidateIds(
                    sessionId = sessionId,
                    lessonId = lessonId,
                    chapterId = effChapterId,
                    chapterName = effChapterName,
                    subjectTitle = effSubjectTitle,
                    examTitle = effExamTitle
                ).filter { !directCandidates.contains(it) }

                for (sibId in siblingIds) {
                    val found = queryLiveExamInfoById(sibId)
                    if (found != null) {
                        info = found
                        resolvedLiveSessionId = sibId
                        break
                    }
                }
            }

            // 3. If still null, check if sessionId/content_id is an existing MCQ session
            if (info == null) {
                for (candId in directCandidates) {
                    val mcqSession = runCatching { practiceQuizRepository.getMcqSession(candId) }.getOrNull()
                    val qs = mcqSession?.questions
                    if (mcqSession != null && !qs.isNullOrEmpty()) {
                        preloadedQuestions = qs.map { mapPracticeQuestionToLiveQuestion(it) }
                        isMcqBacking = true
                        mcqSessionId = mcqSession.id.ifBlank { candId }
                        info = LiveExamSessionDetails(
                            start_time = cachedLesson?.start_time,
                            end_time = cachedLesson?.end_time,
                            title = mcqSession.title?.takeIf { it.isNotBlank() } ?: effExamTitle,
                            markdown_version = 2,
                            chapters = listOf(ExamChapterInfo(id = effChapterId, name = effChapterName.ifBlank { "অধ্যায়" }, no = "1")),
                            subject = ExamSubjectInfo(display = effSubjectTitle.ifBlank { "বিষয়" }),
                            total_number_of_question = preloadedQuestions.size
                        )
                        break
                    }
                }
            }

            // 4. Construct metadata for Quiz HW / Chapter Quiz so Intro & Rules screens always work seamlessly
            if (info == null) {
                info = LiveExamSessionDetails(
                    start_time = cachedLesson?.start_time,
                    end_time = cachedLesson?.end_time,
                    title = effExamTitle,
                    markdown_version = 2,
                    chapters = listOf(
                        ExamChapterInfo(
                            id = effChapterId,
                            name = effChapterName.ifBlank { effExamTitle },
                            no = "1"
                        )
                    ),
                    subject = ExamSubjectInfo(display = effSubjectTitle.ifBlank { "বিষয়" }),
                    total_number_of_question = 25
                )
            }

            _uiState.update {
                it.copy(
                    sessionId = resolvedLiveSessionId,
                    isLoading = false,
                    examInfo = info,
                    questions = preloadedQuestions,
                    isPracticeMcqBacking = isMcqBacking,
                    activeMcqSessionId = mcqSessionId,
                    stage = ExamStage.INTRO,
                    errorMessage = null
                )
            }
        }
    }

    fun proceedToRules() {
        _uiState.update { it.copy(stage = ExamStage.RULES, errorMessage = null) }
    }

    fun startExamQuestions() {
        val state = _uiState.value
        val sId = state.sessionId
        val lId = state.lessonId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            var fetchedQuestions: List<LiveExamQuestionItem> = state.questions
            var resolvedLiveSessionId = sId
            var isMcqBacking = state.isPracticeMcqBacking
            var mcqSessionId = state.activeMcqSessionId

            val cachedLesson = findCachedLesson(sId, lId)
            val directCandidates = listOfNotNull(
                sId.takeIf { it.isNotBlank() },
                lId.takeIf { it.isNotBlank() },
                cachedLesson?.content_id?.takeIf { it.isNotBlank() },
                cachedLesson?.session_id?.takeIf { it.isNotBlank() }
            ).distinct()

            // 1. Try direct candidate IDs with GetLiveQuestions
            if (fetchedQuestions.isEmpty()) {
                for (candId in directCandidates) {
                    val qs = queryLiveQuestionsById(candId)
                    if (qs.isNotEmpty()) {
                        fetchedQuestions = qs
                        resolvedLiveSessionId = candId
                        isMcqBacking = false
                        break
                    }
                }
            }

            // 2. Try sibling LiveExam candidate IDs from the same chapter in LessonCacheManager
            if (fetchedQuestions.isEmpty()) {
                val siblingIds = findSiblingLiveExamCandidateIds(
                    sessionId = sId,
                    lessonId = lId,
                    chapterId = state.chapterId,
                    chapterName = state.chapterName,
                    subjectTitle = state.subjectTitle,
                    examTitle = state.examTitle
                ).filter { !directCandidates.contains(it) }

                for (sibId in siblingIds) {
                    val qs = queryLiveQuestionsById(sibId)
                    if (qs.isNotEmpty()) {
                        fetchedQuestions = qs
                        resolvedLiveSessionId = sibId
                        isMcqBacking = false
                        break
                    }
                }
            }

            // 3. Try existing Practice MCQ session by candidate IDs
            if (fetchedQuestions.isEmpty()) {
                for (candId in directCandidates) {
                    val mcqSession = runCatching { practiceQuizRepository.getMcqSession(candId) }.getOrNull()
                    val qs = mcqSession?.questions
                    if (mcqSession != null && !qs.isNullOrEmpty()) {
                        fetchedQuestions = qs.map { mapPracticeQuestionToLiveQuestion(it) }
                        isMcqBacking = true
                        mcqSessionId = mcqSession.id.ifBlank { candId }
                        break
                    }
                }
            }

            // 4. Start a Chapter Practice Quiz / Quiz HW MCQ session for this chapter & subject
            if (fetchedQuestions.isEmpty()) {
                val startedSession = startChapterPracticeMcqSession(state, cachedLesson)
                val qs = startedSession?.questions
                if (startedSession != null && !qs.isNullOrEmpty()) {
                    fetchedQuestions = qs.map { mapPracticeQuestionToLiveQuestion(it) }
                    isMcqBacking = true
                    mcqSessionId = startedSession.id.ifBlank { sId }
                }
            }

            if (fetchedQuestions.isNotEmpty()) {
                val totalCount = fetchedQuestions.size
                val defaultDurationSeconds = (totalCount * 60).toLong().coerceAtLeast(300L)

                _uiState.update { current ->
                    current.copy(
                        sessionId = resolvedLiveSessionId,
                        isPracticeMcqBacking = isMcqBacking,
                        activeMcqSessionId = mcqSessionId,
                        isLoading = false,
                        questions = fetchedQuestions,
                        examInfo = current.examInfo?.copy(total_number_of_question = totalCount),
                        currentQuestionIndex = 0,
                        userAnswers = emptyMap(),
                        timeSpentPerQuestion = emptyMap(),
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
                        errorMessage = "প্রশ্ন লোড করা সম্ভব হয়নি। অনুগ্রহ করে ইন্টারনেট সংযোগ চেক করে আবার চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    private suspend fun startChapterPracticeMcqSession(
        state: ChapterExamUiState,
        cachedLesson: StudentLessonItem?
    ): PracticeQuizSessionItem? {
        val rawSubCode = state.subjectCode.ifBlank {
            cachedLesson?.subject_id ?: cachedLesson?.live_class?.subject_id ?: ""
        }
        val rawSubTitle = state.subjectTitle.ifBlank {
            cachedLesson?.subject_name
                ?: cachedLesson?.live_class?.subject_name
                ?: state.examInfo?.subject?.display
                ?: ""
        }
        val rawChapId = state.chapterId.ifBlank {
            cachedLesson?.chapter_id
                ?: cachedLesson?.live_class?.chapter_id
                ?: state.examInfo?.chapters?.firstOrNull()?.id
                ?: ""
        }
        val rawChapName = state.chapterName.ifBlank {
            cachedLesson?.live_class?.chapter_name
                ?: state.examInfo?.chapters?.firstOrNull()?.name
                ?: LessonCacheManager.cleanChapterTitle(state.examTitle)
        }

        val cleanTargetChapName = LessonCacheManager.cleanChapterTitle(rawChapName)
        val cleanExamTitleName = LessonCacheManager.cleanChapterTitle(state.examTitle)

        // Resolve hierarchy subject & chapter IDs via PracticeQuizRepository
        val hierarchyPair = try {
            practiceQuizRepository.getSubjectChaptersWithQuestionCounts(
                subjectCode = rawSubCode,
                subjectTitle = rawSubTitle
            )
        } catch (e: Exception) {
            Log.w("ChapterExamVM", "Hierarchy lookup failed: ${e.message}")
            null
        }

        val matchedSubject = hierarchyPair?.first
        val hierarchyChapters = hierarchyPair?.second.orEmpty()

        val matchedHierarchyChapters = hierarchyChapters.filter { ch ->
            val chClean = LessonCacheManager.cleanChapterTitle(ch.name)
            (rawChapId.isNotBlank() && ch.id.equals(rawChapId, ignoreCase = true)) ||
                (cleanTargetChapName.isNotBlank() && (
                    chClean.equals(cleanTargetChapName, ignoreCase = true) ||
                        (cleanTargetChapName.length >= 3 && chClean.contains(cleanTargetChapName, ignoreCase = true)) ||
                        (chClean.length >= 3 && cleanTargetChapName.contains(chClean, ignoreCase = true))
                    )) ||
                (cleanExamTitleName.isNotBlank() && (
                    chClean.equals(cleanExamTitleName, ignoreCase = true) ||
                        (cleanExamTitleName.length >= 3 && chClean.contains(cleanExamTitleName, ignoreCase = true))
                    ))
        }

        val hierarchyChapterIds = matchedHierarchyChapters
            .mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
            .distinct()

        val allCandidateChapterIds = (hierarchyChapterIds + listOfNotNull(
            rawChapId.takeIf { it.isNotBlank() },
            cachedLesson?.chapter_id?.takeIf { it.isNotBlank() },
            cachedLesson?.live_class?.chapter_id?.takeIf { it.isNotBlank() }
        )).distinct()

        val resolvedSubjectIds = listOfNotNull(
            matchedSubject?.code?.takeIf { it.isNotBlank() },
            rawSubCode.takeIf { it.isNotBlank() }
        ).distinct()

        val desiredCount = (state.examInfo?.total_number_of_question ?: 25).coerceIn(5, 50)

        // Attempt 1: Hierarchy matched chapter IDs + subject IDs
        if (hierarchyChapterIds.isNotEmpty()) {
            val s1 = runCatching {
                practiceQuizRepository.startPracticeQuizSession(
                    subjectIds = resolvedSubjectIds.ifEmpty { null },
                    chapterIds = listOf(hierarchyChapterIds.first()),
                    totalCount = desiredCount
                )
            }.getOrNull()
            if (s1 != null && !s1.questions.isNullOrEmpty()) return s1

            val s1b = runCatching {
                practiceQuizRepository.startPracticeQuizSession(
                    subjectIds = null,
                    chapterIds = hierarchyChapterIds,
                    totalCount = desiredCount
                )
            }.getOrNull()
            if (s1b != null && !s1b.questions.isNullOrEmpty()) return s1b
        }

        // Attempt 2: All candidate chapter IDs + subject IDs
        if (allCandidateChapterIds.isNotEmpty()) {
            for (cid in allCandidateChapterIds) {
                val s2 = runCatching {
                    practiceQuizRepository.startPracticeQuizSession(
                        subjectIds = resolvedSubjectIds.ifEmpty { null },
                        chapterIds = listOf(cid),
                        totalCount = desiredCount
                    )
                }.getOrNull()
                if (s2 != null && !s2.questions.isNullOrEmpty()) return s2
            }
        }

        // Attempt 3: All chapters of the subject if single chapter had no questions in MCQ bank
        val allSubjectChapterIds = hierarchyChapters.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
        if (allSubjectChapterIds.isNotEmpty()) {
            val s3 = runCatching {
                practiceQuizRepository.startPracticeQuizSession(
                    subjectIds = resolvedSubjectIds.ifEmpty { null },
                    chapterIds = allSubjectChapterIds,
                    totalCount = desiredCount
                )
            }.getOrNull()
            if (s3 != null && !s3.questions.isNullOrEmpty()) return s3
        }

        return null
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
            if (state.isPracticeMcqBacking && state.activeMcqSessionId.isNotBlank()) {
                try {
                    val mcqAnswers = state.questions.mapNotNull { q ->
                        val qId = q.id ?: return@mapNotNull null
                        val ans = state.userAnswers[qId]
                        if (ans.isNullOrBlank()) null
                        else mapOf<String, Any?>(
                            "id" to qId,
                            "given_ans" to ans,
                            "is_submitted" to true
                        )
                    }
                    runCatching {
                        practiceQuizRepository.submitPracticeQuizSession(
                            sessionId = state.activeMcqSessionId,
                            isFinal = true,
                            isTimeout = state.remainingSeconds <= 0L,
                            questionAnswers = mcqAnswers
                        )
                    }
                    val feedback = runCatching {
                        practiceQuizRepository.getMcqSessionFeedback(state.activeMcqSessionId)
                    }.getOrNull()
                    val feedbackQuestions = feedback?.questions.orEmpty().associateBy { it.id }
                    val feedbackAnswers = feedback?.question_answer.orEmpty().associateBy { it.id }
                    if (feedbackQuestions.isNotEmpty() || feedbackAnswers.isNotEmpty()) {
                        val enrichedQuestions = _uiState.value.questions.map { q ->
                            val fbQ = feedbackQuestions[q.id]
                            val fbAns = feedbackAnswers[q.id]
                            val mergedSol = fbQ?.effectiveExplanation ?: q.solution
                            val mergedOptions = fbQ?.mcq_options?.takeIf { it.isNotEmpty() } ?: q.mcq_options
                            val userGiven = state.userAnswers[q.id] ?: fbAns?.given_ans ?: fbQ?.given_ans
                            val resolvedCorrect = com.example.utils.McqAnswerUtils.resolveCorrectOptionBengali(
                                directCorrectOption = fbQ?.correct_option ?: q.correct_option,
                                ansCorrectOption = fbAns?.correct_ans,
                                solutionText = mergedSol,
                                optionsDescriptions = mergedOptions?.map { it.description },
                                userSelectedOptionIndex = com.example.utils.McqAnswerUtils.normalizeToOptionIndex(userGiven),
                                isUserMarkedCorrectByServer = fbAns?.is_correct
                            )
                            q.copy(
                                title = fbQ?.title?.takeIf { it.isNotBlank() } ?: q.title,
                                solution = mergedSol,
                                correct_option = resolvedCorrect.ifBlank { fbQ?.correct_option ?: fbAns?.correct_ans ?: q.correct_option },
                                mcq_options = mergedOptions
                            )
                        }
                        _uiState.update { it.copy(questions = enrichedQuestions) }
                    }
                } catch (e: Exception) {
                    Log.e("ChapterExamVM", "Error submitting practice MCQ session: ${state.activeMcqSessionId}", e)
                }
            } else {
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
            }

            // Mark exam completed
            if (state.lessonId.isNotBlank()) {
                sessionManager.markLessonCompleted(state.lessonId)
                LessonCacheManager.markLessonCompletedInCache(state.lessonId)
            }
            if (state.sessionId.isNotBlank()) {
                sessionManager.markLessonCompleted(state.sessionId)
                LessonCacheManager.markLessonCompletedInCache(state.sessionId)
            }
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
            val isMcqBacking = _uiState.value.isPracticeMcqBacking

            if (!isMcqBacking && sessionId.isNotBlank()) {
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

            if (!isMcqBacking && summary == null && lessonId.isNotBlank() && lessonId != sessionId) {
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
        val state = _uiState.value
        val sId = state.sessionId
        val lId = state.lessonId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            var items: List<ExamAnswerSolutionItem> = emptyList()

            if (!state.isPracticeMcqBacking && sId.isNotBlank()) {
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

            if (!state.isPracticeMcqBacking && items.isEmpty() && lId.isNotBlank() && lId != sId) {
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
                items.mapIndexed { idx, item ->
                    val origQ = qMap[item.question?.id] ?: _uiState.value.questions.getOrNull(idx)
                    val mergedTitle = item.question?.title?.takeIf { it.isNotBlank() } ?: origQ?.title
                    val mergedOptions = item.question?.mcq_options?.ifEmpty { null } ?: origQ?.mcq_options
                    val solText = item.question?.solution?.ifBlank { null } ?: origQ?.solution ?: ""
                    val directCorrect = item.question?.correct_option ?: origQ?.correct_option
                    val givenAns = item.given_ans?.takeIf { it.isNotBlank() }
                        ?: origQ?.id?.let { _uiState.value.userAnswers[it] }
                        ?: ""
                    val resolvedCorrect = directCorrect ?: deduceCorrectOption(solText, mergedOptions, directCorrect, givenAns)
                    item.copy(
                        given_ans = givenAns,
                        question = item.question?.copy(
                            title = mergedTitle,
                            mcq_options = mergedOptions,
                            solution = solText,
                            correct_option = resolvedCorrect
                        ) ?: origQ?.copy(
                            title = mergedTitle,
                            correct_option = resolvedCorrect
                        )
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

    fun backToIntro() {
        _uiState.update { it.copy(stage = ExamStage.INTRO, errorMessage = null) }
    }

    fun backToPerformance() {
        _uiState.update { it.copy(stage = ExamStage.PERFORMANCE, errorMessage = null) }
    }

    fun goToSolutionIndex(index: Int) {
        val state = _uiState.value
        if (index in state.questions.indices) {
            _uiState.update { it.copy(solutionIndex = index) }
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
