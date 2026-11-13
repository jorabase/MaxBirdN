package com.example.quiz

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import com.example.database.SavedItemEntity
import com.example.database.SavedItemRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PracticeQuizViewModel(
    private val repository: PracticeQuizRepository,
    private val savedItemRepository: SavedItemRepository? = null,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeQuizVM"
    }

    private val _uiState = MutableStateFlow(PracticeQuizUiState())
    val uiState: StateFlow<PracticeQuizUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var saveAnswerJob: Job? = null
    private var quizStartTimeMs: Long = 0L
    private var currentQuestionStartMs: Long = 0L
    private val questionTimestamps = mutableMapOf<String, Pair<String, String>>() // questionId -> (startIso, submitIso)

    init {
        savedItemRepository?.let { repo ->
            viewModelScope.launch {
                repo.allSavedItemIds.collect { savedIds ->
                    _uiState.update { state ->
                        state.copy(bookmarkedQuestionIds = (state.bookmarkedQuestionIds + savedIds).toSet())
                    }
                }
            }
        }
    }

    /**
     * Step 1: Load subject chapters with question counts
     */
    fun loadSubjectChapters(
        subjectCode: String,
        subjectTitle: String,
        subjectColorHex: String?,
        targetChapterId: String? = null,
        targetChapterName: String? = null
    ) {
        _uiState.update {
            it.copy(
                subjectCode = subjectCode,
                subjectTitle = subjectTitle,
                subjectColorHex = subjectColorHex,
                targetChapterId = targetChapterId?.ifBlank { null },
                targetChapterName = targetChapterName?.ifBlank { null },
                isSingleChapterMode = !targetChapterId.isNullOrBlank(),
                isChaptersLoading = true,
                chaptersError = null
            )
        }

        viewModelScope.launch {
            try {
                val (subjectItem, chapters) = repository.getSubjectChaptersWithQuestionCounts(
                    subjectCode = subjectCode,
                    subjectTitle = subjectTitle
                )

                val limits = repository.checkPracticeQuizAccess()

                val cleanTargetId = targetChapterId?.trim()?.ifBlank { null }
                val cleanTargetName = targetChapterName?.trim()?.ifBlank { null }

                // Find matching chapter if targetChapterId or targetChapterName is specified
                fun normalizeBengali(s: String?): String {
                    if (s.isNullOrBlank()) return ""
                    return s.replace("য়", "y")
                        .replace("য়", "y")
                        .replace("১ম", "1")
                        .replace("২য়", "2")
                        .replace("৩য়", "3")
                        .replace("৪র্থ", "4")
                        .replace("৫ম", "5")
                        .replace("৬ষ্ঠ", "6")
                        .replace("৭ম", "7")
                        .replace("৮ম", "8")
                        .replace("৯ম", "9")
                        .replace("১০ম", "10")
                        .replace("অধ্যায়", "")
                        .replace("অধ্যায়", "")
                        .replace(Regex("[^a-zA-Z0-9\\p{L}]"), "")
                        .lowercase()
                        .trim()
                }

                val matchedChapter = if (cleanTargetId != null || cleanTargetName != null) {
                    val normTargetName = normalizeBengali(cleanTargetName)
                    chapters.find { ch ->
                        val normChName = normalizeBengali(ch.name)
                        (cleanTargetId != null && (ch.id == cleanTargetId || ch.id.contains(cleanTargetId) || cleanTargetId.contains(ch.id))) ||
                        (cleanTargetName != null && normChName.isNotBlank() && normTargetName.isNotBlank() && (normChName.contains(normTargetName) || normTargetName.contains(normChName)))
                    } ?: if (cleanTargetId != null) {
                        HierarchyChapterItem(
                            id = cleanTargetId,
                            name = cleanTargetName ?: "অধ্যায়",
                            should_render = true,
                            total_active_questions = null
                        )
                    } else null
                } else null

                val effectiveChapters = if (matchedChapter != null) {
                    listOf(matchedChapter)
                } else {
                    chapters
                }

                val selectedIds = if (matchedChapter != null) {
                    setOf(matchedChapter.id)
                } else {
                    chapters.map { it.id }.toSet()
                }

                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chapters = effectiveChapters,
                        allSubjectChapters = chapters,
                        selectedChapterIds = selectedIds,
                        isSingleChapterMode = matchedChapter != null,
                        subjectIcon = subjectItem?.icon,
                        totalActiveQuestionsInSubject = subjectItem?.total_active_questions ?: chapters.sumOf { ch -> ch.total_active_questions ?: 0 },
                        practiceLimits = limits,
                        chaptersError = if (effectiveChapters.isEmpty()) "এই বিষয়ে বর্তমানে কোনো প্র্যাকটিস কুইজ উপলব্ধ নেই" else null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading subject chapters", e)
                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chaptersError = "অধ্যায়সমূহ লোড করতে সমস্যা হয়েছে। পুনরায় চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    fun showAllChapters() {
        _uiState.update { state ->
            val all = state.allSubjectChapters
            state.copy(
                chapters = all,
                isSingleChapterMode = false,
                selectedChapterIds = all.map { it.id }.toSet()
            )
        }
    }

    fun toggleChapterSelection(chapterId: String) {
        _uiState.update { state ->
            val updated = state.selectedChapterIds.toMutableSet()
            if (updated.contains(chapterId)) {
                updated.remove(chapterId)
            } else {
                updated.add(chapterId)
            }
            state.copy(selectedChapterIds = updated)
        }
    }

    fun toggleSelectAllChapters() {
        _uiState.update { state ->
            if (state.areAllChaptersSelected) {
                state.copy(selectedChapterIds = emptySet())
            } else {
                state.copy(selectedChapterIds = state.chapters.map { it.id }.toSet())
            }
        }
    }

    /**
     * Step 2: Select Question Count
     */
    fun selectQuestionCount(count: Int) {
        _uiState.update {
            it.copy(
                selectedQuestionCount = count,
                showSummarySheet = true
            )
        }
    }

    fun dismissSummarySheet() {
        _uiState.update { it.copy(showSummarySheet = false) }
    }

    fun openSummarySheet() {
        _uiState.update { it.copy(showSummarySheet = true) }
    }

    /**
     * Step 3: Start Practice Quiz Session
     */
    fun startQuiz(onSuccess: (sessionId: String) -> Unit) {
        val state = _uiState.value
        val chapterIds = state.selectedChapterIds.toList()
        if (chapterIds.isEmpty()) {
            _uiState.update { it.copy(startQuizError = "অনুগ্রহ করে অন্তত একটি অধ্যায় সিলেক্ট করুন") }
            return
        }

        _uiState.update {
            it.copy(
                isStartingQuiz = true,
                startQuizError = null,
                showSummarySheet = false
            )
        }

        viewModelScope.launch {
            try {
                val session = repository.startPracticeQuizSession(
                    subjectIds = listOfNotNull(state.subjectCode.ifBlank { null }),
                    chapterIds = chapterIds,
                    totalCount = state.selectedQuestionCount
                )

                val durationSeconds = (session.questions?.size ?: state.selectedQuestionCount) * 60L
                val nowMs = System.currentTimeMillis()
                quizStartTimeMs = nowMs
                currentQuestionStartMs = nowMs
                questionTimestamps.clear()

                _uiState.update {
                    it.copy(
                        isStartingQuiz = false,
                        session = session,
                        sessionId = session.id,
                        currentQuestionIndex = 0,
                        userAnswers = emptyMap(),
                        remainingSeconds = durationSeconds,
                        initialTotalSeconds = durationSeconds,
                        elapsedTimeSeconds = 0L,
                        isQuizFinished = false
                    )
                }

                startCountdownTimer()
                onSuccess(session.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting practice quiz session", e)
                _uiState.update {
                    it.copy(
                        isStartingQuiz = false,
                        startQuizError = e.message ?: "কুইজ শুরু করতে সমস্যা হয়েছে। আবার চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0 && !_uiState.value.isQuizFinished) {
                delay(1000L)
                _uiState.update {
                    val nextSec = it.remainingSeconds - 1
                    it.copy(remainingSeconds = nextSec)
                }
            }

            if (_uiState.value.remainingSeconds <= 0 && !_uiState.value.isQuizFinished) {
                // Auto-submit on timeout
                submitFinalQuiz(isTimeout = true) {}
            }
        }
    }

    private fun getIsoUtcString(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return sdf.format(java.util.Date(millis))
    }

    /**
     * Helper to build properly structured question_answer payload with accurate per-question timestamps
     */
    private fun buildQuestionAnswersPayload(
        session: PracticeQuizSessionItem?,
        userAnswers: Map<String, String>
    ): List<Map<String, Any?>> {
        val nowMs = System.currentTimeMillis()
        val fallbackNowIso = getIsoUtcString(nowMs)
        val fallbackStartIso = getIsoUtcString((nowMs - 4000L).coerceAtLeast(quizStartTimeMs))

        return userAnswers.mapNotNull { (qId, ans) ->
            if (ans.isNotBlank()) {
                val pair = questionTimestamps[qId]
                mapOf(
                    "id" to qId,
                    "given_ans" to ans,
                    "start_time" to (pair?.first ?: fallbackStartIso),
                    "submit_time" to (pair?.second ?: fallbackNowIso)
                )
            } else null
        }
    }

    /**
     * In Quiz: Select Option & debounce save
     */
    fun selectOption(questionId: String, optionNo: String) {
        val nowMs = System.currentTimeMillis()
        val existingStartIso = questionTimestamps[questionId]?.first
            ?: getIsoUtcString(currentQuestionStartMs.takeIf { it > 0L } ?: (nowMs - 3000L))
        val submitIso = getIsoUtcString(nowMs)
        questionTimestamps[questionId] = existingStartIso to submitIso

        val updatedAnswers = _uiState.value.userAnswers + (questionId to optionNo)
        _uiState.update {
            it.copy(userAnswers = updatedAnswers)
        }

        // Debounced save progress in background
        saveAnswerJob?.cancel()
        saveAnswerJob = viewModelScope.launch {
            delay(400L)
            try {
                val sessionId = _uiState.value.sessionId
                val session = _uiState.value.session
                if (sessionId.isNotBlank()) {
                    val answersList = buildQuestionAnswersPayload(session, updatedAnswers)
                    repository.submitPracticeQuizSession(
                        sessionId = sessionId,
                        isFinal = false,
                        isTimeout = false,
                        questionAnswers = answersList.ifEmpty { null }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background answer progress save error: ${e.message}")
            }
        }
    }

    fun goToNextQuestion() {
        val total = _uiState.value.session?.questions?.size ?: 0
        if (_uiState.value.currentQuestionIndex < total - 1) {
            currentQuestionStartMs = System.currentTimeMillis()
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex + 1) }
        }
    }

    fun goToPreviousQuestion() {
        if (_uiState.value.currentQuestionIndex > 0) {
            currentQuestionStartMs = System.currentTimeMillis()
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex - 1) }
        }
    }

    fun goToQuestionIndex(index: Int) {
        val total = _uiState.value.session?.questions?.size ?: 0
        if (index in 0 until total) {
            currentQuestionStartMs = System.currentTimeMillis()
            _uiState.update { it.copy(currentQuestionIndex = index) }
        }
    }

    /**
     * Submit Final Quiz
     */
    fun submitFinalQuiz(
        explicitSessionId: String? = null,
        isTimeout: Boolean = false,
        onSuccess: (sessionId: String) -> Unit = {}
    ) {
        timerJob?.cancel()
        saveAnswerJob?.cancel()
        val sessionId = explicitSessionId?.ifBlank { null }
            ?: _uiState.value.sessionId.ifBlank { null }
            ?: return

        val stateBeforeSubmit = _uiState.value
        val elapsedByTimer = (stateBeforeSubmit.initialTotalSeconds - stateBeforeSubmit.remainingSeconds).coerceAtLeast(1L)
        val elapsedByClock = if (quizStartTimeMs > 0L) {
            ((System.currentTimeMillis() - quizStartTimeMs) / 1000L).coerceAtLeast(1L)
        } else {
            elapsedByTimer
        }
        val computedElapsedSeconds = minOf(elapsedByTimer, elapsedByClock).coerceAtLeast(1L)

        _uiState.update {
            it.copy(
                isSubmitting = true,
                submitError = null,
                sessionId = sessionId,
                elapsedTimeSeconds = computedElapsedSeconds
            )
        }
        viewModelScope.launch {
            try {
                val session = _uiState.value.session
                val answersList = buildQuestionAnswersPayload(session, _uiState.value.userAnswers)

                // ১. উত্তরগুলো সহ ফাইনাল সাবমিশন
                repository.submitPracticeQuizSession(
                    sessionId = sessionId,
                    isFinal = true,
                    isTimeout = isTimeout,
                    questionAnswers = answersList.ifEmpty { null }
                )

                // সার্ভার যাতে এগ্রিগেশন সম্পন্ন করতে পারে তার জন্য সামান্য বাফার ডিলে
                delay(800L)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isQuizFinished = true,
                        sessionId = sessionId
                    )
                }
                onSuccess(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error submitting final quiz", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = e.message ?: "সাবমিট করতে সমস্যা হয়েছে"
                    )
                }
            }
        }
    }

    /**
     * Step 4: Load Quiz Result
     */
    fun loadQuizResult(sessionId: String) {
        Log.d(TAG, "loadQuizResult called for sessionId=$sessionId")
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                isResultLoading = true,
                resultError = null
            )
        }

        viewModelScope.launch {
            try {
                var summary = repository.getQuizResultSummary(sessionId)

                // If result shows 0 correct and 0 incorrect despite user having answered questions,
                // give server backend another 1s to finish aggregation and retry once.
                val answeredCount = _uiState.value.userAnswers.size
                val totalCorrect = summary.correctCount
                val totalIncorrect = summary.incorrectCount
                if (answeredCount > 0 && totalCorrect == 0 && totalIncorrect == 0) {
                    Log.d(TAG, "Result shows 0/0 despite answeredCount=$answeredCount. Retrying getQuizResultSummary in 1000ms...")
                    delay(1000L)
                    try {
                        val retrySummary = repository.getQuizResultSummary(sessionId)
                        val retryCorrect = retrySummary.correctCount
                        val retryIncorrect = retrySummary.incorrectCount
                        if (retryCorrect > 0 || retryIncorrect > 0 || retrySummary.total_spent_time != null) {
                            summary = retrySummary
                        }
                    } catch (retryEx: Exception) {
                        Log.w(TAG, "Retry getQuizResultSummary failed", retryEx)
                    }
                }

                if (sessionId.isNotBlank()) {
                    completedItemRepository?.markCompleted(
                        itemId = sessionId,
                        itemType = "QUIZ",
                        title = _uiState.value.targetChapterName ?: "প্র্যাকটিস কুইজ"
                    )
                }

                _uiState.update {
                    it.copy(
                        isResultLoading = false,
                        resultSummary = summary
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading quiz result", e)
                _uiState.update {
                    it.copy(
                        isResultLoading = false,
                        resultError = "ফলাফল লোড করা যায়নি। পুনরায় চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    /**
     * Step 5: Load Quiz Feedback / Solutions
     * Merges server feedback session with in-memory session questions & userAnswers so question titles,
     * options, explanations, and user selections are always 100% complete.
     */
    fun loadQuizFeedback(sessionId: String) {
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                isFeedbackLoading = true,
                feedbackError = null
            )
        }

        viewModelScope.launch {
            try {
                val rawSession = repository.getMcqSessionFeedback(sessionId)
                val inMemorySession = _uiState.value.session
                val inMemoryAnswers = _uiState.value.userAnswers
                val inMemoryQuestionsById = inMemorySession?.questions?.associateBy { it.id }.orEmpty()

                val mergedQuestions = if (!rawSession.questions.isNullOrEmpty()) {
                    rawSession.questions.mapIndexed { idx, fbQ ->
                        val origQ = inMemoryQuestionsById[fbQ.id] ?: inMemorySession?.questions?.getOrNull(idx)
                        val localGivenAns = inMemoryAnswers[fbQ.id]
                        fbQ.copy(
                            title = fbQ.title?.takeIf { it.isNotBlank() } ?: origQ?.title,
                            description = fbQ.description?.takeIf { it.isNotBlank() } ?: origQ?.description,
                            solution = fbQ.solution?.takeIf { it.isNotBlank() }
                                ?: fbQ.description?.takeIf { it.isNotBlank() }
                                ?: origQ?.solution?.takeIf { it.isNotBlank() }
                                ?: origQ?.description?.takeIf { it.isNotBlank() },
                            question_no = fbQ.question_no?.takeIf { it.isNotBlank() } ?: origQ?.question_no ?: "${idx + 1}",
                            source = fbQ.source?.takeIf { it.isNotBlank() } ?: origQ?.source,
                            chapter = fbQ.chapter ?: origQ?.chapter,
                            given_ans = fbQ.given_ans?.takeIf { it.isNotBlank() } ?: localGivenAns ?: origQ?.given_ans,
                            mcq_options = fbQ.mcq_options?.takeIf { it.isNotEmpty() } ?: origQ?.mcq_options
                        )
                    }
                } else {
                    inMemorySession?.questions.orEmpty()
                }

                // Also ensure question_answer has entries for any local userAnswers
                val existingQaById = rawSession.question_answer?.associateBy { it.id }.orEmpty().toMutableMap()
                inMemoryAnswers.forEach { (qId, givenAns) ->
                    val existing = existingQaById[qId]
                    if (existing == null) {
                        existingQaById[qId] = PracticeQuizQuestionAnswerItem(
                            id = qId,
                            given_ans = givenAns,
                            is_submitted = true
                        )
                    } else if (existing.given_ans.isNullOrBlank() && givenAns.isNotBlank()) {
                        existingQaById[qId] = existing.copy(given_ans = givenAns)
                    }
                }

                val enrichedSession = rawSession.copy(
                    questions = mergedQuestions,
                    question_answer = existingQaById.values.toList()
                )

                _uiState.update {
                    it.copy(
                        isFeedbackLoading = false,
                        feedbackSession = enrichedSession
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading quiz feedback", e)
                // Fallback to in-memory session if available so user never hits a dead end
                val fallbackSession = _uiState.value.session
                if (fallbackSession != null && !fallbackSession.questions.isNullOrEmpty()) {
                    _uiState.update {
                        it.copy(
                            isFeedbackLoading = false,
                            feedbackSession = fallbackSession,
                            feedbackError = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isFeedbackLoading = false,
                            feedbackError = "ফিডব্যাক লোড করা যায়নি। পুনরায় চেষ্টা করুন।"
                        )
                    }
                }
            }
        }
    }

    fun setFeedbackFilter(filter: FeedbackFilter) {
        _uiState.update { it.copy(feedbackFilter = filter) }
    }

    fun toggleBookmark(questionId: String) {
        val sessionId = _uiState.value.sessionId
        val isAlreadyBookmarked = _uiState.value.bookmarkedQuestionIds.contains(questionId)

        if (isAlreadyBookmarked) {
            _uiState.update {
                it.copy(
                    bookmarkedQuestionIds = it.bookmarkedQuestionIds - questionId,
                    bookmarkMessage = "বুকমার্ক সরানো হয়েছে"
                )
            }
            viewModelScope.launch {
                try {
                    savedItemRepository?.deleteSavedItemById(questionId)
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting saved question from database", e)
                }
            }
            return
        }

        _uiState.update {
            it.copy(
                bookmarkedQuestionIds = it.bookmarkedQuestionIds + questionId,
                bookmarkMessage = "প্রশ্নটি সংরক্ষণ করা হয়েছে"
            )
        }

        viewModelScope.launch {
            // 1. Save question to local Room database
            try {
                val session = _uiState.value.session
                val feedbackSession = _uiState.value.feedbackSession
                val fbQ = feedbackSession?.questions?.find { it.id == questionId }
                val origQ = session?.questions?.find { it.id == questionId }
                val question = fbQ ?: origQ
                val qaItem = feedbackSession?.question_answer?.find { it.id == questionId }

                val userGivenAns = _uiState.value.userAnswers[questionId]
                    ?: qaItem?.given_ans
                    ?: fbQ?.given_ans
                    ?: origQ?.given_ans

                val explanation = fbQ?.effectiveExplanation ?: origQ?.effectiveExplanation
                val optionsList = (fbQ?.mcq_options?.takeIf { it.isNotEmpty() } ?: origQ?.mcq_options)?.map { opt ->
                    (opt.no ?: "") to (opt.description ?: "")
                } ?: emptyList()

                val resolvedCorrectOpt = com.example.utils.McqAnswerUtils.resolveCorrectOptionBengali(
                    directCorrectOption = fbQ?.correct_option ?: origQ?.correct_option,
                    ansCorrectOption = qaItem?.correct_ans,
                    solutionText = explanation,
                    optionsDescriptions = optionsList.map { it.second },
                    userSelectedOptionIndex = com.example.utils.McqAnswerUtils.normalizeToOptionIndex(userGivenAns),
                    isUserMarkedCorrectByServer = qaItem?.is_correct
                )

                val subjectTitle = _uiState.value.subjectTitle.ifBlank { null }
                val chapterName = fbQ?.chapter?.name ?: origQ?.chapter?.name ?: _uiState.value.targetChapterName

                val contentJson = SavedItemEntity.buildQuestionContentJson(
                    options = optionsList,
                    correctOption = resolvedCorrectOpt,
                    userGivenAns = userGivenAns,
                    explanation = explanation,
                    chapterName = chapterName
                )

                val entity = SavedItemEntity(
                    id = questionId,
                    type = SavedItemEntity.TYPE_QUESTION,
                    title = fbQ?.title?.takeIf { it.isNotBlank() }
                        ?: origQ?.title?.takeIf { it.isNotBlank() }
                        ?: "প্রশ্ন",
                    subtitle = listOfNotNull(subjectTitle, chapterName).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { null },
                    contentJson = contentJson,
                    timestamp = System.currentTimeMillis()
                )

                savedItemRepository?.saveItem(entity)
            } catch (e: Exception) {
                Log.w(TAG, "Error saving question to local database", e)
            }

            // 2. Call backend GraphQL mutation to sync
            try {
                repository.createSavedQuestion(sessionId, questionId)
            } catch (e: Exception) {
                Log.w(TAG, "Error creating saved question on backend", e)
            }
        }
    }

    fun clearBookmarkMessage() {
        _uiState.update { it.copy(bookmarkMessage = null) }
    }

    fun resetQuizFlow() {
        timerJob?.cancel()
        saveAnswerJob?.cancel()
        quizStartTimeMs = 0L
        currentQuestionStartMs = 0L
        questionTimestamps.clear()
        _uiState.update {
            it.copy(
                session = null,
                sessionId = "",
                currentQuestionIndex = 0,
                userAnswers = emptyMap(),
                remainingSeconds = 0L,
                elapsedTimeSeconds = 0L,
                isSubmitting = false,
                submitError = null,
                isQuizFinished = false,
                resultSummary = null,
                feedbackSession = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        saveAnswerJob?.cancel()
    }
}

class PracticeQuizViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val savedItemRepository: SavedItemRepository? = null,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeQuizViewModel::class.java)) {
            val repository = PracticeQuizRepository(apiService, sessionManager)
            return PracticeQuizViewModel(repository, savedItemRepository, completedItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
