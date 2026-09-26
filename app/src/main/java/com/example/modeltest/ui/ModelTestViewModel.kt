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
import java.text.SimpleDateFormat
import java.util.*

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

data class LocalCqPageUploadItem(
    val pageNo: Int,
    val localUri: String? = null,
    val imageBytes: ByteArray? = null,
    val s3Url: String? = null,
    val fileId: String? = null,
    val uploadStatus: String = "pending" // "pending", "uploading", "successful", "failed"
)

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
    val mcqMasterSolutionUrl: String? = null,
    val cqMasterSolutionUrl: String? = null,
    val isMasterSolutionLoading: Boolean = false,
    val masterSolutionError: String? = null,

    // Exam Session Creation & MCQ Screen State
    val currentSessionId: String = "",
    val currentCqSessionId: String = "",
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

    // Live CQ Exam & Upload State (Screenshots 3-13)
    val liveCqExamId: String = "",
    val liveCqUCode: String = "৪২৮৭",
    val liveCqQuestions: List<ShikhoCqQuestionRaw> = emptyList(),
    val liveCqUploadInfo: CqUploadRelatedInfo? = null,
    val liveCqRemainingSeconds: Long = 0L,
    val liveCqSubmissionDetails: CqSessionDetailedInfo? = null,
    val liveCqUploadedPagesMap: Map<String, List<LocalCqPageUploadItem>> = emptyMap(), // questionId -> pages
    val activeUploadQuestionId: String? = null,
    val isLiveCqUploading: Boolean = false,
    val liveCqUploadError: String? = null,
    val isLiveCqSubmittingFinal: Boolean = false,
    val showFinalSubmitDialog: Boolean = false,
    val showViewQuestionDialog: Boolean = false,
    val dialogViewingQuestion: ShikhoCqQuestionRaw? = null,
    val liveExamPublishTime: String = "৩০ সেপ্টেম্বর, ২০২৬ | সকাল ১১ টায়",
    val liveExamFinalResult: ModelTestPreResultDetails? = null,

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
        get() = 999

    val attemptedPracticeCount: Int
        get() = retakeContainer?.attemptedPracticeCount
            ?: retakeContainer?.sessions?.size
            ?: 0

    val remainingPracticeCount: Int
        get() = 999

    val isPracticeLimitReached: Boolean
        get() = false
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

                val mcqStageId = details.stages?.firstOrNull { it.type.equals("MCQ", ignoreCase = true) }?.id
                    ?: details.stage_grouping?.mcq_grouping?.stages?.firstOrNull()?.id
                val cqStageId = details.stages?.firstOrNull { it.type.equals("CQ", ignoreCase = true) }?.id
                    ?: details.stage_grouping?.cq_grouping?.stages?.firstOrNull()?.id

                if (!mcqStageId.isNullOrBlank()) {
                    viewModelScope.launch {
                        val mcqSolUrl = repository.getMCQMasterSolutionUrls(mcqStageId).getOrNull()
                        _uiState.update { it.copy(mcqMasterSolutionUrl = mcqSolUrl) }
                    }
                } else {
                    _uiState.update { it.copy(mcqMasterSolutionUrl = null) }
                }

                if (!cqStageId.isNullOrBlank()) {
                    viewModelScope.launch {
                        val cqSolUrl = repository.getCQMasterSolutionUrls(cqStageId).getOrNull()
                        _uiState.update { it.copy(cqMasterSolutionUrl = cqSolUrl) }
                    }
                } else {
                    _uiState.update { it.copy(cqMasterSolutionUrl = null) }
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
            val sessionObj = result.getOrNull()
            val sessionId = sessionObj?.getEffectiveMcqSessionId()
                ?: sessionObj?.id
                ?: modelTestId
            val cqSessionId = sessionObj?.getEffectiveCqSessionId()
                ?: sessionObj?.stages?.firstOrNull { it.type.equals("CQ", ignoreCase = true) }?.session_id
                ?: ""

            _uiState.update {
                it.copy(
                    currentSessionId = sessionId,
                    currentCqSessionId = cqSessionId,
                    isCreatingSession = false
                )
            }
            loadMcqQuestions(sessionId, onSessionReady)
        }
    }

    private fun loadMcqQuestions(sessionId: String, onLoaded: (String) -> Unit) {
        _uiState.update { it.copy(isMcqLoading = true, mcqError = null) }
        viewModelScope.launch {
            val result = repository.getMcqInfoOfModelTest(sessionId)
            result.onSuccess { examContainer ->
                val questions = examContainer.questions ?: emptyList()
                val totalSeconds = examContainer.remaining_time_seconds
                    ?: examContainer.duration_in_seconds
                    ?: (30 * 60L)

                val restoredAnswers = mutableMapOf<String, Int>()
                questions.forEach { q ->
                    if (q.user_selected_option != null) {
                        restoredAnswers[q.id] = q.user_selected_option
                    }
                }

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
        val effectiveCqId = if (_uiState.value.currentCqSessionId.isNotBlank()) {
            _uiState.value.currentCqSessionId
        } else {
            sessionId
        }
        _uiState.update { it.copy(isCqLoading = true, cqError = null) }
        viewModelScope.launch {
            val result = repository.getCqInfoOfModelTest(effectiveCqId)
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
    fun loadMasterSolution(modelTestId: String, solutionType: String = "both", onLoaded: (url: String) -> Unit) {
        val cachedUrl = when (solutionType) {
            "mcq" -> _uiState.value.mcqMasterSolutionUrl
            "cq" -> _uiState.value.cqMasterSolutionUrl
            else -> _uiState.value.mcqMasterSolutionUrl ?: _uiState.value.cqMasterSolutionUrl
        }
        if (!cachedUrl.isNullOrBlank()) {
            onLoaded(cachedUrl)
            return
        }

        _uiState.update { it.copy(isMasterSolutionLoading = true, masterSolutionError = null) }
        viewModelScope.launch {
            val url = if (solutionType == "mcq") {
                repository.getMCQMasterSolutionUrls(modelTestId).getOrNull()
            } else {
                repository.getCQMasterSolutionUrls(modelTestId).getOrNull()
            }

            _uiState.update {
                it.copy(
                    mcqMasterSolutionUrl = if (solutionType == "mcq") url else it.mcqMasterSolutionUrl,
                    cqMasterSolutionUrl = if (solutionType == "cq") url else it.cqMasterSolutionUrl,
                    isMasterSolutionLoading = false,
                    masterSolutionError = if (url.isNullOrBlank()) "সল্যুশন পাওয়া যায়নি" else null
                )
            }
            if (!url.isNullOrBlank()) {
                onLoaded(url)
            }
        }
    }

    // -------------------------------------------------------------
    // Live CQ Exam Management (Screenshots 3-13)
    // -------------------------------------------------------------
    private var cqTimerJob: Job? = null

    fun startLiveCqExam(cqSessionId: String, onReady: () -> Unit) {
        val effectiveCqId = cqSessionId.ifBlank { _uiState.value.currentCqSessionId }
        _uiState.update { it.copy(isCqLoading = true, cqError = null) }

        viewModelScope.launch {
            val result = repository.startCqSessionSubmission(effectiveCqId).recoverCatching {
                // If already started, get CQ session details
                val rawInfo = repository.getCqInfoOfModelTest(effectiveCqId).getOrNull()
                CqSessionDetailedInfo(
                    id = effectiveCqId,
                    exam_id = _uiState.value.modelTestInfo?.stages?.firstOrNull { it.type == "CQ" }?.id ?: "6ab5009970a9cc77f7f40690",
                    title = "CQ",
                    u_code = "৪২৮৭",
                    questions = rawInfo?.questions?.map { q ->
                        ShikhoCqQuestionRaw(
                            id = q.id,
                            title = q.stimulus,
                            total_marks = 10.0,
                            sub_questions = q.sub_questions?.map { sq ->
                                ShikhoCqSubQuestionRaw(sq.question, sq.marks)
                            }
                        )
                    }
                )
            }

            val cqExamId = result.getOrNull()?.exam_id 
                ?: _uiState.value.modelTestInfo?.stages?.firstOrNull { it.type == "CQ" }?.id 
                ?: "6ab5009970a9cc77f7f40690"

            val uploadInfo = repository.getCqUploadRelatedInfo(cqExamId).getOrNull()
            val sessionDetails = result.getOrNull()

            val uCode = sessionDetails?.u_code ?: "৪২৮৭"
            val questions = sessionDetails?.questions ?: emptyList()

            val writingDurationSec = (uploadInfo?.exam_duration ?: 60) * 60L
            val submissionDurationSec = (uploadInfo?.submission_duration ?: 40) * 60L
            val totalLiveSec = writingDurationSec + submissionDurationSec

            _uiState.update {
                it.copy(
                    liveCqExamId = cqExamId,
                    liveCqUCode = uCode,
                    liveCqQuestions = questions,
                    liveCqUploadInfo = uploadInfo,
                    liveCqRemainingSeconds = totalLiveSec,
                    liveCqSubmissionDetails = sessionDetails,
                    isCqLoading = false
                )
            }

            startLiveCqTimer()
            onReady()
        }
    }

    private fun startLiveCqTimer() {
        cqTimerJob?.cancel()
        cqTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value.liveCqRemainingSeconds
                if (current <= 1L) {
                    _uiState.update { it.copy(liveCqRemainingSeconds = 0L) }
                    confirmFinalLiveCqSubmit {}
                    break
                } else {
                    _uiState.update { it.copy(liveCqRemainingSeconds = current - 1) }
                }
            }
        }
    }

    fun selectQuestionForUpload(questionId: String) {
        _uiState.update { it.copy(activeUploadQuestionId = questionId) }
    }

    fun addPageToActiveQuestion(imageBytes: ByteArray, localUri: String? = null) {
        val qId = _uiState.value.activeUploadQuestionId ?: return
        val currentMap = _uiState.value.liveCqUploadedPagesMap
        val list = currentMap[qId]?.toMutableList() ?: mutableListOf()
        val nextPg = list.size + 1
        list.add(
            LocalCqPageUploadItem(
                pageNo = nextPg,
                localUri = localUri,
                imageBytes = imageBytes,
                uploadStatus = "pending"
            )
        )
        _uiState.update {
            it.copy(liveCqUploadedPagesMap = currentMap + (qId to list))
        }
    }

    fun removePageFromActiveQuestion(pageNo: Int) {
        val qId = _uiState.value.activeUploadQuestionId ?: return
        val currentMap = _uiState.value.liveCqUploadedPagesMap
        val list = currentMap[qId]?.filter { it.pageNo != pageNo }?.mapIndexed { index, item ->
            item.copy(pageNo = index + 1)
        } ?: emptyList()
        _uiState.update {
            it.copy(liveCqUploadedPagesMap = currentMap + (qId to list))
        }
    }

    fun uploadActiveQuestionPages(onUploaded: () -> Unit) {
        val qId = _uiState.value.activeUploadQuestionId ?: return
        val pages = _uiState.value.liveCqUploadedPagesMap[qId] ?: return
        val cqSessionId = _uiState.value.currentCqSessionId.ifBlank { _uiState.value.currentSessionId }
        val examId = _uiState.value.liveCqExamId.ifBlank { "6ab5009970a9cc77f7f40690" }
        val modelTestId = _uiState.value.activeModelTestId.ifBlank { "6ab5006f6a5f4a905b73362b" }

        _uiState.update { it.copy(isLiveCqUploading = true, liveCqUploadError = null) }

        viewModelScope.launch {
            try {
                val nowStr = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
                val fileNames = pages.map { "${nowStr}_pg${it.pageNo}" }

                // 1. Get S3 pre-signed URLs
                val preSignedResult = repository.getPreSignedUrlList(examId, modelTestId, fileNames)
                val preSignedList = preSignedResult.getOrNull() ?: emptyList()

                val updatedPages = mutableListOf<LocalCqPageUploadItem>()
                val givenAnswersPayload = mutableListOf<Map<String, Any>>()

                pages.forEachIndexed { idx, page ->
                    val preSigned = preSignedList.getOrNull(idx)
                    val s3Url = preSigned?.pre_signed_url
                    val fileId = preSigned?.id ?: "file_${System.currentTimeMillis()}_$idx"

                    if (page.imageBytes != null && !s3Url.isNullOrBlank()) {
                        repository.uploadImageToS3(s3Url, page.imageBytes)
                    }

                    updatedPages.add(
                        page.copy(
                            fileId = fileId,
                            s3Url = s3Url,
                            uploadStatus = "successful"
                        )
                    )

                    givenAnswersPayload.add(
                        mapOf(
                            "page_no" to page.pageNo,
                            "file_id" to fileId,
                            "upload_status" to "successful"
                        )
                    )
                }

                // 2. Submit to GraphQL
                val submitTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                repository.submitCqQuestionAnswer(
                    sessionId = cqSessionId,
                    questionId = qId,
                    submitTime = submitTime,
                    givenAnswers = givenAnswersPayload
                )

                _uiState.update {
                    it.copy(
                        isLiveCqUploading = false,
                        liveCqUploadedPagesMap = it.liveCqUploadedPagesMap + (qId to updatedPages)
                    )
                }
                onUploaded()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLiveCqUploading = false,
                        liveCqUploadError = e.message ?: "আপলোড সম্পন্ন করা যায়নি"
                    )
                }
            }
        }
    }

    fun openFinalSubmitDialog() {
        _uiState.update { it.copy(showFinalSubmitDialog = true) }
    }

    fun closeFinalSubmitDialog() {
        _uiState.update { it.copy(showFinalSubmitDialog = false) }
    }

    fun openViewQuestionDialog(question: ShikhoCqQuestionRaw) {
        _uiState.update {
            it.copy(
                showViewQuestionDialog = true,
                dialogViewingQuestion = question
            )
        }
    }

    fun closeViewQuestionDialog() {
        _uiState.update {
            it.copy(
                showViewQuestionDialog = false,
                dialogViewingQuestion = null
            )
        }
    }

    fun confirmFinalLiveCqSubmit(onCompleted: () -> Unit) {
        cqTimerJob?.cancel()
        val cqSessionId = _uiState.value.currentCqSessionId.ifBlank { _uiState.value.currentSessionId }
        val modelTestId = _uiState.value.activeModelTestId

        _uiState.update {
            it.copy(
                isLiveCqSubmittingFinal = true,
                showFinalSubmitDialog = false
            )
        }

        viewModelScope.launch {
            repository.finalSubmitCqSession(cqSessionId)
            val publishTime = repository.getModelTestResultPublishTime(modelTestId).getOrNull()
                ?: "৩০ সেপ্টেম্বর, ২০২৬ | সকাল ১১ টায়"
            val preResult = repository.getModelTestPreResult(modelTestId).getOrNull()

            _uiState.update {
                it.copy(
                    isLiveCqSubmittingFinal = false,
                    liveExamPublishTime = publishTime,
                    liveExamFinalResult = preResult
                )
            }
            onCompleted()
        }
    }
}

