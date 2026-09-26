package com.example.modeltest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.StudentLessonItem
import com.example.modeltest.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModelTestTab {
    MODEL_TEST,
    CLASS
}

enum class FeedbackFilter {
    ALL,
    CORRECT,
    WRONG,
    UNATTEMPTED
}

data class ModelTestUiState(
    // Dashboard State
    val selectedTab: ModelTestTab = ModelTestTab.MODEL_TEST,
    val modelTests: List<StudentLessonItem> = emptyList(),
    val liveClasses: List<StudentLessonItem> = emptyList(),
    val isModelTestsLoading: Boolean = false,
    val isClassesLoading: Boolean = false,
    val modelTestsError: String? = null,
    val classesError: String? = null,
    val programId: String = "",
    val phaseId: String = "",
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val subjectColor: String = "",

    // Unified Detail & Rules Screen State
    val activeModelTestId: String = "",
    val modelTestInfo: ModelTestInfoDetails? = null,
    val isInfoLoading: Boolean = false,
    val infoError: String? = null,
    val isMainExamCountdownFinished: Boolean = false,
    val retakeContainer: RetakeSessionsContainer? = null,
    val isRetakeLoading: Boolean = false,
    val masterSolutionDetails: CQMasterSolutionUrlsDetails? = null,
    val isMasterSolutionLoading: Boolean = false,
    val masterSolutionError: String? = null,

    // Exam Session Creation & MCQ Screen State
    val currentSessionId: String = "",
    val isCreatingSession: Boolean = false,
    val sessionCreateError: String? = null,
    val isPracticeSession: Boolean = false,
    val mcqQuestions: List<ModelTestMcqQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedAnswers: Map<String, Int> = emptyMap(), // questionId -> optionIndex
    val isMcqLoading: Boolean = false,
    val mcqError: String? = null,
    val remainingTimeSeconds: Long = 0L,
    val isSubmittingMcq: Boolean = false,
    val showExitDialog: Boolean = false,

    // MCQ Score Popup State
    val showScorePopup: Boolean = false,
    val minimalScoreResult: McqResultMinimalDetails? = null,

    // CQ Screen State (Practice Read-only)
    val cqContainer: CqContainer? = null,
    val isCqLoading: Boolean = false,
    val cqError: String? = null,

    // CQ Exam Upload Screen State (Main Exam)
    val cqEndTimeMillis: Long? = null,
    val isCqUploadTimeExpired: Boolean = false,

    // Pre-Result State (Main Exam Final Result)
    val preResult: ModelTestPreResultDetails? = null,
    val isPreResultLoading: Boolean = false,
    val preResultError: String? = null,

    // Feedback State (analytics.shikho.com)
    val feedbackDetails: McqFeedbackDetails? = null,
    val isFeedbackLoading: Boolean = false,
    val feedbackError: String? = null,
    val feedbackFilter: FeedbackFilter = FeedbackFilter.ALL
) {
    val totalPracticeAllowed: Int
        get() = retakeContainer?.totalPracticeAllowed
            ?: retakeContainer?.allowed_attempts
            ?: 3

    val attemptedPracticeCount: Int
        get() = retakeContainer?.attemptedPracticeCount
            ?: retakeContainer?.sessions?.size
            ?: 0

    val remainingPracticeCount: Int
        get() = retakeContainer?.remaining_attempts
            ?: maxOf(0, totalPracticeAllowed - attemptedPracticeCount)

    val isPracticeLimitReached: Boolean
        get() = attemptedPracticeCount >= totalPracticeAllowed && totalPracticeAllowed > 0
}

class ModelTestViewModel(
    private val repository: ModelTestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelTestUiState())
    val uiState: StateFlow<ModelTestUiState> = _uiState.asStateFlow()

    private var examTimerJob: Job? = null

    // -------------------------------------------------------------
    // Dashboard & Subject Initialization
    // -------------------------------------------------------------
    fun initSubject(
        programId: String,
        phaseId: String,
        subjectCode: String,
        subjectTitle: String,
        subjectColor: String
    ) {
        val isNewSubject = _uiState.value.subjectCode != subjectCode || _uiState.value.phaseId != phaseId
        if (isNewSubject) {
            _uiState.update {
                it.copy(
                    programId = programId,
                    phaseId = phaseId,
                    subjectCode = subjectCode,
                    subjectTitle = subjectTitle,
                    subjectColor = subjectColor,
                    selectedTab = ModelTestTab.MODEL_TEST,
                    modelTests = emptyList(),
                    liveClasses = emptyList(),
                    modelTestsError = null,
                    classesError = null
                )
            }
            loadModelTests(forceRefresh = true)
            loadClasses(forceRefresh = true)
        }
    }

    fun selectTab(tab: ModelTestTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            ModelTestTab.MODEL_TEST -> {
                if (_uiState.value.modelTests.isEmpty() && !_uiState.value.isModelTestsLoading) {
                    loadModelTests()
                }
            }
            ModelTestTab.CLASS -> {
                if (_uiState.value.liveClasses.isEmpty() && !_uiState.value.isClassesLoading) {
                    loadClasses()
                }
            }
        }
    }

    fun loadModelTests(forceRefresh: Boolean = false) {
        val s = _uiState.value
        if (s.programId.isBlank() || s.phaseId.isBlank() || s.subjectCode.isBlank()) return
        if (s.modelTests.isNotEmpty() && !forceRefresh) return

        viewModelScope.launch {
            _uiState.update { it.copy(isModelTestsLoading = true, modelTestsError = null) }
            val result = repository.getSubjectSpecificLessons(
                programId = s.programId,
                phaseId = s.phaseId,
                subjectId = s.subjectCode,
                contentType = "ModelTest"
            )
            result.onSuccess { tests ->
                _uiState.update {
                    it.copy(
                        modelTests = tests,
                        isModelTestsLoading = false,
                        modelTestsError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isModelTestsLoading = false,
                        modelTestsError = err.message ?: "মডেল টেস্ট লোড করতে সমস্যা হয়েছে"
                    )
                }
            }
        }
    }

    fun loadClasses(forceRefresh: Boolean = false) {
        val s = _uiState.value
        if (s.programId.isBlank() || s.phaseId.isBlank() || s.subjectCode.isBlank()) return
        if (s.liveClasses.isNotEmpty() && !forceRefresh) return

        viewModelScope.launch {
            _uiState.update { it.copy(isClassesLoading = true, classesError = null) }
            val result = repository.getSubjectSpecificLessons(
                programId = s.programId,
                phaseId = s.phaseId,
                subjectId = s.subjectCode,
                contentType = "LiveClass"
            )
            result.onSuccess { classes ->
                _uiState.update {
                    it.copy(
                        liveClasses = classes,
                        isClassesLoading = false,
                        classesError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isClassesLoading = false,
                        classesError = err.message ?: "ক্লাস লোড করতে সমস্যা হয়েছে"
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Unified Detail & Rules Screen Data Loading
    // -------------------------------------------------------------
    fun loadModelTestDetails(modelTestId: String, lessonStartTime: String? = null, lessonEndTime: String? = null) {
        _uiState.update {
            it.copy(
                activeModelTestId = modelTestId,
                isInfoLoading = true,
                infoError = null,
                isRetakeLoading = true
            )
        }

        viewModelScope.launch {
            val infoResult = repository.getModelTestInfo(modelTestId)
            infoResult.onSuccess { details ->
                _uiState.update {
                    it.copy(
                        modelTestInfo = details,
                        isInfoLoading = false,
                        infoError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isInfoLoading = false,
                        // If GraphQL fails, provide a fallback details item from lesson times
                        modelTestInfo = it.modelTestInfo ?: ModelTestInfoDetails(
                            id = modelTestId,
                            title = "মডেল টেস্ট",
                            start_time = lessonStartTime,
                            end_time = lessonEndTime,
                            duration_in_minutes = 30,
                            mcq_count = 30
                        ),
                        infoError = null
                    )
                }
            }
        }

        viewModelScope.launch {
            val retakeResult = repository.listRetakeModelTestSessions(modelTestId)
            retakeResult.onSuccess { container ->
                _uiState.update {
                    it.copy(
                        retakeContainer = container,
                        isRetakeLoading = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isRetakeLoading = false) }
            }
        }
    }

    fun onMainExamCountdownFinished() {
        _uiState.update { it.copy(isMainExamCountdownFinished = true) }
    }

    // -------------------------------------------------------------
    // Session Creation & Starting Exam Flow
    // -------------------------------------------------------------
    fun startExamSession(
        modelTestId: String,
        isPractice: Boolean,
        onSessionReady: (sessionId: String) -> Unit
    ) {
        _uiState.update {
            it.copy(
                isCreatingSession = true,
                sessionCreateError = null,
                isPracticeSession = isPractice
            )
        }

        viewModelScope.launch {
            val result = repository.createModelTestSession(modelTestId, isPractice)
            result.onSuccess { sessionResult ->
                val sessionId = sessionResult.mcq_session_id
                    ?: sessionResult.session_id
                    ?: ""
                if (sessionId.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            currentSessionId = sessionId,
                            isCreatingSession = false
                        )
                    }
                    loadMcqQuestions(sessionId, onSessionReady)
                } else {
                    _uiState.update {
                        it.copy(
                            isCreatingSession = false,
                            sessionCreateError = "সেশন আইডি পাওয়া যায়নি"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCreatingSession = false,
                        sessionCreateError = err.message ?: "সেশন শুরু করা যায়নি। আবার চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    private fun loadMcqQuestions(sessionId: String, onLoaded: (String) -> Unit) {
        _uiState.update { it.copy(isMcqLoading = true, mcqError = null) }
        viewModelScope.launch {
            val result = repository.getMcqInfoOfModelTest(sessionId)
            result.onSuccess { container ->
                val questions = container.questions ?: emptyList()
                val totalSeconds = container.remaining_time_seconds
                    ?: container.duration_in_seconds
                    ?: (30 * 60L)

                // Restore any pre-selected answers from API or local cache
                val restoredAnswers = mutableMapOf<String, Int>()
                questions.forEach { q ->
                    if (q.user_selected_option != null) {
                        restoredAnswers[q.id] = q.user_selected_option
                    }
                }

                // Check Room for preserved lifecycle state
                val localState = repository.getActiveExamState(sessionId)
                val effectiveTime = localState?.remainingSeconds ?: totalSeconds
                val effectiveIndex = localState?.currentQuestionIndex ?: 0

                _uiState.update {
                    it.copy(
                        mcqQuestions = questions,
                        currentQuestionIndex = effectiveIndex,
                        selectedAnswers = restoredAnswers,
                        remainingTimeSeconds = effectiveTime,
                        isMcqLoading = false,
                        mcqError = null
                    )
                }
                startExamTimer()
                onLoaded(sessionId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isMcqLoading = false,
                        mcqError = err.message ?: "MCQ প্রশ্ন লোড করা যায়নি"
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // MCQ Examination Timer & Navigation
    // -------------------------------------------------------------
    private fun startExamTimer() {
        examTimerJob?.cancel()
        examTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value.remainingTimeSeconds
                if (current <= 1L) {
                    _uiState.update { it.copy(remainingTimeSeconds = 0L) }
                    // Auto submit with timeout flag
                    submitFinalMcq(isTimeout = true)
                    break
                } else {
                    _uiState.update { it.copy(remainingTimeSeconds = current - 1) }
                }
            }
        }
    }

    fun selectOption(questionId: String, optionIndex: Int) {
        val sessionId = _uiState.value.currentSessionId
        _uiState.update {
            it.copy(selectedAnswers = it.selectedAnswers + (questionId to optionIndex))
        }

        // Auto-save in background
        viewModelScope.launch {
            repository.submitMcqAnswer(
                sessionId = sessionId,
                questionId = questionId,
                selectedOptionIndex = optionIndex,
                isTimeout = false,
                isFinalSubmitted = false
            )
        }
    }

    fun navigateToQuestion(index: Int) {
        if (index in _uiState.value.mcqQuestions.indices) {
            _uiState.update { it.copy(currentQuestionIndex = index) }
        }
    }

    fun nextQuestion() {
        val nextIdx = _uiState.value.currentQuestionIndex + 1
        if (nextIdx < _uiState.value.mcqQuestions.size) {
            _uiState.update { it.copy(currentQuestionIndex = nextIdx) }
        }
    }

    fun previousQuestion() {
        val prevIdx = _uiState.value.currentQuestionIndex - 1
        if (prevIdx >= 0) {
            _uiState.update { it.copy(currentQuestionIndex = prevIdx) }
        }
    }

    fun setExitDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showExitDialog = visible) }
    }

    // -------------------------------------------------------------
    // Submit MCQ & Score Minimal Popup
    // -------------------------------------------------------------
    fun submitFinalMcq(isTimeout: Boolean = false, onSubmitted: (() -> Unit)? = null) {
        examTimerJob?.cancel()
        val sessionId = _uiState.value.currentSessionId
        val currQ = _uiState.value.mcqQuestions.getOrNull(_uiState.value.currentQuestionIndex)
        val selectedOpt = currQ?.id?.let { _uiState.value.selectedAnswers[it] } ?: -1

        _uiState.update { it.copy(isSubmittingMcq = true) }

        viewModelScope.launch {
            if (currQ != null && selectedOpt >= 0) {
                repository.submitMcqAnswer(
                    sessionId = sessionId,
                    questionId = currQ.id,
                    selectedOptionIndex = selectedOpt,
                    isTimeout = isTimeout,
                    isFinalSubmitted = true
                )
            }

            // Clear local cached state
            repository.clearActiveExamState(sessionId)

            // Fetch minimal score result
            val minimalResult = repository.getMcqResultMinimal(sessionId).getOrNull()
            _uiState.update {
                it.copy(
                    isSubmittingMcq = false,
                    showScorePopup = true,
                    minimalScoreResult = minimalResult
                )
            }
            onSubmitted?.invoke()
        }
    }

    fun dismissScorePopup() {
        _uiState.update { it.copy(showScorePopup = false) }
    }

    // -------------------------------------------------------------
    // App Lifecycle State Preservation (onPause / onStop)
    // -------------------------------------------------------------
    fun saveLifecycleState() {
        val s = _uiState.value
        if (s.currentSessionId.isNotBlank() && s.mcqQuestions.isNotEmpty()) {
            viewModelScope.launch {
                repository.saveActiveExamState(
                    sessionId = s.currentSessionId,
                    modelTestId = s.activeModelTestId,
                    isPractice = s.isPracticeSession,
                    currentIndex = s.currentQuestionIndex,
                    remainingSec = s.remainingTimeSeconds,
                    answers = s.selectedAnswers
                )
            }
        }
    }

    // -------------------------------------------------------------
    // CQ Questions Loading (Practice Mode Read-Only)
    // -------------------------------------------------------------
    fun loadCqQuestions(sessionId: String) {
        _uiState.update { it.copy(isCqLoading = true, cqError = null) }
        viewModelScope.launch {
            val result = repository.getCqInfoOfModelTest(sessionId)
            result.onSuccess { container ->
                _uiState.update {
                    it.copy(
                        cqContainer = container,
                        isCqLoading = false,
                        cqError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCqLoading = false,
                        cqError = err.message ?: "CQ প্রশ্ন লোড করা যায়নি"
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // CQ Upload Screen End-Time Configuration
    // -------------------------------------------------------------
    fun initCqUploadScreen(endTimeMillis: Long?) {
        _uiState.update {
            it.copy(
                cqEndTimeMillis = endTimeMillis,
                isCqUploadTimeExpired = (endTimeMillis != null && System.currentTimeMillis() >= endTimeMillis)
            )
        }
    }

    fun onCqUploadTimeExpired() {
        _uiState.update { it.copy(isCqUploadTimeExpired = true) }
    }

    // -------------------------------------------------------------
    // Main Exam Pre-Result (Overall Result)
    // -------------------------------------------------------------
    fun loadPreResult(sessionId: String) {
        _uiState.update { it.copy(isPreResultLoading = true, preResultError = null) }
        viewModelScope.launch {
            val result = repository.getModelTestPreResult(sessionId)
            result.onSuccess { res ->
                _uiState.update {
                    it.copy(
                        preResult = res,
                        isPreResultLoading = false,
                        preResultError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isPreResultLoading = false,
                        preResultError = err.message ?: "সার্বিক ফলাফল লোড করা যায়নি"
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Analytics Feedback Loading with Filter
    // -------------------------------------------------------------
    fun loadFeedback(sessionId: String) {
        _uiState.update { it.copy(isFeedbackLoading = true, feedbackError = null) }
        viewModelScope.launch {
            val result = repository.getMcqSessionFeedback(sessionId)
            result.onSuccess { fb ->
                _uiState.update {
                    it.copy(
                        feedbackDetails = fb,
                        isFeedbackLoading = false,
                        feedbackError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isFeedbackLoading = false,
                        feedbackError = err.message ?: "ফিডব্যাক লোড করা যায়নি। আবার চেষ্টা করুন।"
                    )
                }
            }
        }
    }

    fun setFeedbackFilter(filter: FeedbackFilter) {
        _uiState.update { it.copy(feedbackFilter = filter) }
    }

    // -------------------------------------------------------------
    // Master Solution Loading
    // -------------------------------------------------------------
    fun loadMasterSolution(modelTestId: String, onLoaded: (url: String) -> Unit) {
        _uiState.update { it.copy(isMasterSolutionLoading = true, masterSolutionError = null) }
        viewModelScope.launch {
            val result = repository.getCQMasterSolutionUrls(modelTestId)
            result.onSuccess { details ->
                _uiState.update {
                    it.copy(
                        masterSolutionDetails = details,
                        isMasterSolutionLoading = false,
                        masterSolutionError = null
                    )
                }
                val url = details.master_solution_pdf_url
                    ?: details.cq_solution_url
                    ?: details.mcq_solution_url
                    ?: ""
                if (url.isNotBlank()) onLoaded(url)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isMasterSolutionLoading = false,
                        masterSolutionError = err.message ?: "মাস্টার সলুশন লোড করা যায়নি"
                    )
                }
            }
        }
    }
}
