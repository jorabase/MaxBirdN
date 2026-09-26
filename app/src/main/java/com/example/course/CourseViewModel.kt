package com.example.course

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CourseViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    val repository: CourseRepository = CourseRepository(apiService),
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModel() {

    suspend fun getTopics(chapterId: String, topicIds: List<String>? = null): List<TopicFullItem> {
        return try {
            repository.getTopics(chapterId, topicIds)
        } catch (e: Exception) {
            android.util.Log.e("CourseViewModel", "Error fetching topics for $chapterId", e)
            emptyList()
        }
    }

    suspend fun getPhaseWiseChapters(programId: String, phaseId: String, subjectCode: String): List<AcademicChapterItem> {
        return try {
            val effectiveProgId = programId.ifBlank { "6864d3a806800acba2e27099" }
            val effectivePhaseId = phaseId.ifBlank { "6864d62506800acba2e27111" }
            val list = repository.getPhaseWiseChapters(effectiveProgId, effectivePhaseId, subjectCode)
            if (list.isNotEmpty()) {
                list
            } else {
                repository.getChaptersBySubjectCode(subjectCode)
            }
        } catch (e: Exception) {
            android.util.Log.e("CourseViewModel", "Error fetching chapters for $subjectCode", e)
            try {
                repository.getChaptersBySubjectCode(subjectCode)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // Caching for instant sub-second loading
    private val chaptersCache = java.util.concurrent.ConcurrentHashMap<String, List<AcademicChapterItem>>()
    private val lessonsCache = java.util.concurrent.ConcurrentHashMap<String, List<StudentLessonItem>>()

    private val _uiState = MutableStateFlow(CourseUiState())
    val uiState: StateFlow<CourseUiState> = _uiState.asStateFlow()

    override fun onCleared() {
        super.onCleared()
    }

    init {
        fetchEnrolledPrograms()
        loadSubjects()
        completedItemRepository?.let { repo ->
            viewModelScope.launch {
                repo.completedIdsState.collect { completedSet ->
                    if (_uiState.value.lessons.isNotEmpty()) {
                        val enriched = LessonCacheManager.enrichWithCompletedState(_uiState.value.lessons, completedSet)
                        _uiState.update { it.copy(lessons = enriched) }
                    }
                }
            }
        }
    }

    fun openCourse(program: EnrolledProgram) {
        val title = program.title_bn ?: "প্রোগ্রাম"
        _uiState.update { 
            it.copy(
                selectedCourseProgram = program,
                hasAnimatedVideo = program.has_animated_video == true
            ) 
        }
        switchProgram(
            newProgramId = program.id,
            newProgramTitle = title,
            batchId = program.enrollment_details?.batch_id,
            classCode = program.classes?.firstOrNull()
        )
    }

    fun closeCourseDetails() {
        _uiState.update { it.copy(selectedCourseProgram = null) }
    }

    fun fetchEnrolledPrograms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProgramsLoading = true) }
            try {
                val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
                val userClassName = sessionManager.getUserClassName() ?: "C11"
                val group = sessionManager.getUserGroup() ?: "Humanities"
                val vendor = sessionManager.getUserVendor() ?: "BD"

                val response = repository.getAcademicProgramByEnrollment(
                    batchId = batchId,
                    className = userClassName,
                    group = group,
                    vendor = vendor
                )
                val enrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                val otherAll = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()

                val freeList = otherAll.filter { it.is_free == true }
                val paidOtherList = otherAll.filter { it.is_free != true }

                _uiState.update {
                    it.copy(
                        enrolledPrograms = enrolled,
                        freePrograms = freeList,
                        otherPrograms = paidOtherList,
                        isProgramsLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    val userClassName = sessionManager.getUserClassName() ?: "C11"
                    val group = sessionManager.getUserGroup() ?: "Humanities"
                    if (current.enrolledPrograms.isEmpty() && current.freePrograms.isEmpty() && current.otherPrograms.isEmpty()) {
                        current.copy(
                            enrolledPrograms = CourseFallbackDataProvider.getFallbackEnrolledPrograms(userClassName, group),
                            freePrograms = CourseFallbackDataProvider.getFallbackFreePrograms(userClassName, group),
                            otherPrograms = CourseFallbackDataProvider.getFallbackOtherPrograms(userClassName, group),
                            isProgramsLoading = false
                        )
                    } else {
                        current.copy(isProgramsLoading = false)
                    }
                }
            }
        }
    }

    fun enrollInCourse(
        program: OtherProgram,
        context: android.content.Context? = null,
        onEnrollmentSuccess: ((EnrolledProgram) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    enrollingProgramId = program.id,
                    enrollmentErrorMessage = null
                )
            }

            val userId = sessionManager.getUserId() ?: ""
            val isFreeCourse = program.is_free == true
            
            var result = if (isFreeCourse) {
                repository.enrollInFreeProgram(program.id)
            } else {
                repository.generateFreeTrialEnrolment(program.id, userId)
            }

            // Fallback: If free enrollment failed with "not free", try trial enrolment
            if (result.isFailure && isFreeCourse) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.contains("not free", ignoreCase = true) || errMsg.contains("400", ignoreCase = true)) {
                    result = repository.generateFreeTrialEnrolment(program.id, userId)
                }
            } else if (result.isFailure && !isFreeCourse) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                if (errMsg.contains("free", ignoreCase = true) || errMsg.contains("trial", ignoreCase = true) || errMsg.contains("400", ignoreCase = true)) {
                    result = repository.enrollInFreeProgram(program.id)
                }
            }

            result.onSuccess { successMsg ->
                val feedbackText = if (isFreeCourse) "ফ্রি কোর্সে সফলভাবে ভর্তি হয়েছে!" else "৩ দিনের ফ্রি ট্রায়ালে সফলভাবে ভর্তি হয়েছে!"
                if (context != null) {
                    android.widget.Toast.makeText(context, feedbackText, android.widget.Toast.LENGTH_SHORT).show()
                }

                // Refresh programs from server to fetch the latest server-generated enrollment record
                try {
                    val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
                    val userClassName = sessionManager.getUserClassName() ?: "C11"
                    val group = sessionManager.getUserGroup() ?: "Humanities"
                    val vendor = sessionManager.getUserVendor() ?: "BD"

                    val response = repository.getAcademicProgramByEnrollment(
                        batchId = batchId,
                        className = userClassName,
                        group = group,
                        vendor = vendor
                    )
                    val enrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                    val otherAll = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()
                    val freeList = otherAll.filter { it.is_free == true }
                    val paidOtherList = otherAll.filter { it.is_free != true }

                    val newlyEnrolled = enrolled.find { it.id == program.id } ?: program.toEnrolledProgram()

                    _uiState.update {
                        it.copy(
                            enrolledPrograms = if (enrolled.isNotEmpty()) enrolled else (it.enrolledPrograms + newlyEnrolled).distinctBy { p -> p.id },
                            freePrograms = freeList,
                            otherPrograms = paidOtherList,
                            enrollingProgramId = null,
                            enrollmentSuccessMessage = feedbackText,
                            enrollmentErrorMessage = null
                        )
                    }

                    // Open the course
                    openCourse(newlyEnrolled)
                    onEnrollmentSuccess?.invoke(newlyEnrolled)
                } catch (_: Exception) {
                    val fallbackEnrolled = program.toEnrolledProgram()
                    _uiState.update {
                        it.copy(
                            enrolledPrograms = (it.enrolledPrograms + fallbackEnrolled).distinctBy { p -> p.id },
                            enrollingProgramId = null,
                            enrollmentSuccessMessage = feedbackText,
                            enrollmentErrorMessage = null
                        )
                    }
                    openCourse(fallbackEnrolled)
                    onEnrollmentSuccess?.invoke(fallbackEnrolled)
                }
            }.onFailure { error ->
                val reason = error.localizedMessage ?: "সার্ভার রেসপন্স দেয়নি"
                val errText = "ভর্তি হতে সমস্যা হয়েছে: $reason"
                if (context != null) {
                    android.widget.Toast.makeText(context, errText, android.widget.Toast.LENGTH_LONG).show()
                }
                _uiState.update {
                    it.copy(
                        enrollingProgramId = null,
                        enrollmentErrorMessage = errText
                    )
                }
            }
        }
    }

    fun selectLesson(lesson: StudentLessonItem) {
        val hasDirectStream = lesson.candidateStreamUrls.any { it.isNotBlank() && it != "null" }
        val candidateIds = listOfNotNull(
            lesson.content_id?.takeIf { it.isNotBlank() },
            lesson.live_class?.id?.takeIf { it.isNotBlank() },
            lesson.id.takeIf { it.isNotBlank() },
            lesson.session_id?.takeIf { it.isNotBlank() }
        ).distinct()
        val needsFetch = candidateIds.isNotEmpty() && (!hasDirectStream || lesson.attachments.isNullOrEmpty())

        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                isLessonDetailLoading = needsFetch
            )
        }

        android.util.Log.d("LectureDebug", "selectLesson: ${lesson.title}, candidateIds: $candidateIds, hasDirectStream: $hasDirectStream, needsFetch: $needsFetch")

        if (candidateIds.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    var liveClassData: AcademicProgramLiveClassItem? = null
                    var foundWorkingId: String? = null

                    // Try candidate IDs until we find one that returns valid live class details
                    for (cid in candidateIds) {
                        val details = repository.getLiveClassDetails(cid)
                        if (details != null) {
                            if (liveClassData == null) {
                                liveClassData = details
                                foundWorkingId = cid
                            }
                            // If this candidate ID gave us playback_url, prioritize it!
                            if (!details.playback_url.isNullOrBlank()) {
                                liveClassData = details
                                foundWorkingId = cid
                                break
                            }
                        }
                    }

                    android.util.Log.d("LectureDebug", "api response (chosen id=$foundWorkingId): $liveClassData")
                    android.util.Log.d("LectureDebug", "playback_url: ${liveClassData?.playback_url}")

                    var pbUrl = liveClassData?.playback_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.recording_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.stream_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.video_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.hls_url?.takeIf { it.isNotBlank() && it != "null" }

                    // If liveClassData didn't have playback_url directly, check topics videos
                    if (pbUrl.isNullOrBlank()) {
                        val topicIds = liveClassData?.topics?.mapNotNull { it.id }?.filter { it.isNotBlank() }
                            ?: lesson.topics?.mapNotNull { it.id }?.filter { it.isNotBlank() }
                            ?: emptyList()
                        val chapterId = lesson.chapter_id ?: _uiState.value.selectedChapterId
                        if (topicIds.isNotEmpty() || chapterId.isNotBlank()) {
                            try {
                                val topicsWithVideos = repository.getTopics(chapterId, topicIds.ifEmpty { null })
                                val topicVideoUrl = topicsWithVideos.firstNotNullOfOrNull { top ->
                                    top.videos?.data?.firstNotNullOfOrNull { v ->
                                        v.playback_url?.takeIf { it.isNotBlank() && it != "null" }
                                    }
                                }
                                if (!topicVideoUrl.isNullOrBlank()) {
                                    pbUrl = topicVideoUrl
                                    android.util.Log.d("LectureDebug", "Found playback_url from topics: $pbUrl")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("LectureDebug", "Failed to fetch topic videos: ${e.message}")
                            }
                        }
                    }

                    if (liveClassData != null || !pbUrl.isNullOrBlank()) {
                        val newAttachments = mutableListOf<LessonAttachmentItem>()
                        liveClassData?.study_materials?.forEach { mat ->
                            if (!mat.file_url.isNullOrBlank()) {
                                newAttachments.add(
                                    LessonAttachmentItem(
                                        id = mat.id,
                                        title = mat.name ?: "লেকচার স্লাইড (PDF)",
                                        url = mat.file_url,
                                        file_type = "pdf"
                                    )
                                )
                            }
                        }
                        lesson.attachments?.let { newAttachments.addAll(it) }

                        val primarySlideUrl = liveClassData?.study_materials?.firstNotNullOfOrNull { it.file_url?.takeIf { u -> u.isNotBlank() } }
                            ?: lesson.slide_url
                            ?: lesson.live_class?.slide_url
                            ?: newAttachments.firstOrNull { it.downloadUrl?.contains(".pdf", ignoreCase = true) == true }?.downloadUrl

                        val subjectNameResolved = liveClassData?.subject?.display_bn
                            ?: liveClassData?.subject?.display
                            ?: lesson.subject_name
                            ?: lesson.live_class?.subject_name

                        val chapterIdResolved = lesson.chapter_id?.takeIf { it.isNotBlank() }
                            ?: liveClassData?.chapter?.id?.takeIf { it.isNotBlank() }

                        val chapterNameResolved = liveClassData?.chapter?.name
                            ?: lesson.chapter_name
                            ?: lesson.live_class?.chapter_name

                        val updatedLiveClass = (lesson.live_class ?: LiveClassDetails()).copy(
                            id = liveClassData?.id ?: lesson.live_class?.id ?: foundWorkingId,
                            playback_url = pbUrl ?: lesson.live_class?.playback_url,
                            recording_url = pbUrl ?: lesson.live_class?.recording_url,
                            stream_url = pbUrl ?: lesson.live_class?.stream_url,
                            video_url = pbUrl ?: lesson.live_class?.video_url,
                            slide_url = primarySlideUrl,
                            attachments = newAttachments.distinctBy { it.downloadUrl },
                            start_time = liveClassData?.start_time ?: lesson.live_class?.start_time,
                            end_time = liveClassData?.end_time ?: lesson.live_class?.end_time,
                            chapter_id = chapterIdResolved,
                            chapter_name = chapterNameResolved,
                            subject_name = subjectNameResolved,
                            teacher = liveClassData?.teacher ?: liveClassData?.instructor ?: lesson.live_class?.teacher,
                            topics = if (!liveClassData?.topics.isNullOrEmpty()) liveClassData.topics else lesson.live_class?.topics
                        )

                        val updatedLesson = lesson.copy(
                            title = liveClassData?.title ?: lesson.title,
                            slide_url = primarySlideUrl,
                            subject_name = subjectNameResolved,
                            chapter_name = chapterNameResolved,
                            chapter_id = chapterIdResolved,
                            live_class = updatedLiveClass,
                            attachments = newAttachments.distinctBy { it.downloadUrl },
                            topics = if (!liveClassData?.topics.isNullOrEmpty()) liveClassData.topics else lesson.topics
                        )

                        var currentLessonState = updatedLesson
                        android.util.Log.d("LectureDebug", "resolved video url: ${currentLessonState.resolvedVideoUrl}")
                        android.util.Log.d("LectureDebug", "resolved slide url: ${currentLessonState.resolvedSlideUrl}, attachments: ${currentLessonState.allAttachments.size}")

                        // 1. Fetch Teacher Details if teacher_id exists
                        val teacherId = liveClassData?.teacher?.id
                        if (!teacherId.isNullOrBlank()) {
                            try {
                                val fullTeacher = repository.getTeacherDetails(teacherId)
                                if (fullTeacher != null) {
                                    val withTeacher = currentLessonState.live_class?.copy(teacher = fullTeacher)
                                    currentLessonState = currentLessonState.copy(live_class = withTeacher)
                                }
                            } catch (_: Exception) {}
                        }

                        // 2. Fetch Animated Topic Videos
                        var topicVideos: List<TopicFullItem> = emptyList()
                        val finalTopicIds = currentLessonState.topics?.mapNotNull { it.id }?.filter { it.isNotBlank() }
                            ?: currentLessonState.live_class?.topics?.mapNotNull { it.id }?.filter { it.isNotBlank() }
                            ?: emptyList()
                        val finalChapId = currentLessonState.chapter_id ?: _uiState.value.selectedChapterId
                        if (finalTopicIds.isNotEmpty() && finalChapId.isNotBlank()) {
                            try {
                                topicVideos = repository.getTopics(finalChapId, finalTopicIds)
                            } catch (e: Exception) {
                                android.util.Log.w("LectureDebug", "Failed to fetch topic videos: ${e.message}")
                            }
                        }

                        val cur = _uiState.value.selectedLesson
                        if (cur == null || cur.id == lesson.id || cur.content_id == lesson.content_id || candidateIds.contains(cur.live_class?.id)) {
                            _uiState.update { 
                                it.copy(
                                    selectedLesson = currentLessonState,
                                    selectedLessonTopicVideos = topicVideos,
                                    isLessonDetailLoading = false
                                ) 
                            }
                        }
                    } else {
                        _uiState.update { it.copy(isLessonDetailLoading = false) }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LectureDebug", "Error in selectLesson: ${e.message}", e)
                    _uiState.update { it.copy(isLessonDetailLoading = false) }
                }
            }
        } else {
            _uiState.update { it.copy(isLessonDetailLoading = false) }
        }
    }

    /**
     * Reloads the currently selected lesson fresh from server (for slides, live updates, or recordings).
     */
    fun reloadSelectedLesson() {
        val current = _uiState.value.selectedLesson
        val currentChapterId = _uiState.value.selectedChapterId
        val currentLessonId = current?.id

        if (current != null) {
            selectLesson(current)
        }

        if (currentChapterId.isNotBlank()) {
            viewModelScope.launch {
                loadLessonsForChapter(currentChapterId)
                if (!currentLessonId.isNullOrBlank()) {
                    val updated = _uiState.value.lessons.find { it.id == currentLessonId }
                    if (updated != null) {
                        _uiState.update { it.copy(selectedLesson = updated) }
                    }
                }
            }
        }
    }

    /**
     * Switches the active program, clears stale cached data, and reloads subjects fresh.
     */
    fun switchProgram(
        newProgramId: String,
        newProgramTitle: String? = null,
        batchId: String? = null,
        classCode: String? = null,
        targetPhaseId: String? = null
    ) {
        chaptersCache.clear()
        lessonsCache.clear()
        val title = if (!newProgramTitle.isNullOrBlank()) newProgramTitle else "এইচএসসি কোর্স"
        sessionManager.saveActiveProgram(
            programId = newProgramId,
            titleBn = title,
            batchId = batchId,
            classCode = classCode
        )
        _uiState.update {
            it.copy(
                programId = newProgramId,
                programTitle = title,
                phases = emptyList(),
                selectedPhase = null,
                activePhaseId = targetPhaseId ?: "",
                activePhaseTitle = "",
                subjects = emptyList(),
                selectedSubjectCode = "",
                selectedSubjectTitle = "",
                selectedSubjectColor = "",
                chapters = emptyList(),
                selectedChapterId = "",
                selectedChapterName = "",
                selectedChapterStatus = "",
                lessons = emptyList(),
                selectedLesson = null,
                isSubjectsLoading = true,
                subjectsErrorMessage = null,
                lessonsDiagnosticInfo = null
            )
        }
        loadSubjects(forceRefresh = true, targetPhaseId = targetPhaseId)
    }

    fun loadSubjects(forceRefresh: Boolean = false, targetPhaseId: String? = null) {
        viewModelScope.launch {
            val currentProgId = _uiState.value.programId
            val programId = if (currentProgId.isNotBlank()) currentProgId else (sessionManager.getActiveProgramId() ?: "")
            val currentProgTitle = _uiState.value.programTitle
            val programTitle = if (currentProgTitle.isNotBlank()) currentProgTitle else (sessionManager.getActiveProgramTitleBn() ?: "এইচএসসি কোর্স")

            val oldProgramId = _uiState.value.programId
            val isProgramChanged = oldProgramId.isNotBlank() && oldProgramId != programId
            val activeTargetPhaseId = if (!targetPhaseId.isNullOrBlank()) targetPhaseId else if (isProgramChanged) "" else _uiState.value.activePhaseId
            
            val prog = _uiState.value.enrolledPrograms.find { it.id == programId }
                ?: _uiState.value.freePrograms.find { it.id == programId }?.toEnrolledProgram()
                ?: _uiState.value.otherPrograms.find { it.id == programId }?.toEnrolledProgram()
            // DISABLED: Animated lessons disabled per user request
            val hasAnimated = false

            _uiState.update { 
                it.copy(
                    programId = programId,
                    programTitle = programTitle,
                    hasAnimatedVideo = hasAnimated,
                    isSubjectsLoading = true,
                    subjectsErrorMessage = null,
                    phases = if (isProgramChanged) emptyList() else it.phases,
                    selectedPhase = if (isProgramChanged) null else it.selectedPhase,
                    activePhaseId = activeTargetPhaseId,
                    activePhaseTitle = if (isProgramChanged) "" else it.activePhaseTitle,
                    subjects = if (isProgramChanged) emptyList() else it.subjects,
                    chapters = if (isProgramChanged) emptyList() else it.chapters,
                    lessons = if (isProgramChanged) emptyList() else it.lessons
                )
            }

            if (programId.isBlank()) {
                _uiState.update { 
                    it.copy(
                        isSubjectsLoading = false,
                        subjectsErrorMessage = "কোনো সক্রিয় কোর্স পাওয়া যায়নি। অনুগ্রহ করে হোম পেজ থেকে একটি কোর্স নির্বাচন করুন।"
                    )
                }
                return@launch
            }

            try {
                // 1. Fetch Phases
                var currentPhaseId = if (isProgramChanged) "" else _uiState.value.activePhaseId
                var phasesList = if (isProgramChanged) emptyList() else _uiState.value.phases

                if (phasesList.isEmpty() || currentPhaseId.isBlank() || isProgramChanged || forceRefresh) {
                    try {
                        val fetchedPhases = repository.getProgramPhases(programId).map { 
                            it.copy(has_enrolment = true) 
                        }
                        phasesList = fetchedPhases
                        val activePhase = if (!targetPhaseId.isNullOrBlank()) {
                            fetchedPhases.find { it.id == targetPhaseId }
                        } else null
                            ?: fetchedPhases.firstOrNull { it.is_current == true }
                            ?: fetchedPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                            ?: fetchedPhases.firstOrNull { it.has_enrolment == true && !it.status.equals("COMPLETED", true) }
                            ?: fetchedPhases.firstOrNull { !it.status.equals("COMPLETED", true) }
                            ?: fetchedPhases.firstOrNull()

                        currentPhaseId = activePhase?.id ?: ""
                        _uiState.update {
                            it.copy(
                                programId = programId,
                                phases = phasesList,
                                selectedPhase = activePhase,
                                activePhaseId = currentPhaseId,
                                activePhaseTitle = activePhase?.title ?: ""
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching phases for programId=$programId: ${e.message}", e)
                    }
                }

                // 2. Fetch Academic Subjects
                var rawSubjects: List<AcademicSubjectItem> = emptyList()
                var progressBars: List<SubjectProgressBarItem> = emptyList()

                if (currentPhaseId.isNotBlank()) {
                    try {
                        val academicProgram = repository.getAcademicSubjects(programId, currentPhaseId)
                        rawSubjects = academicProgram?.subjects ?: emptyList()
                        progressBars = academicProgram?.subjects_progress_bar ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching subjects with phaseId=$currentPhaseId: ${e.message}", e)
                    }
                }

                if (rawSubjects.isEmpty()) {
                    try {
                        val academicProgram = repository.getAcademicSubjects(programId, null)
                        rawSubjects = academicProgram?.subjects ?: emptyList()
                        progressBars = academicProgram?.subjects_progress_bar ?: emptyList()
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching subjects fallback: ${e.message}", e)
                    }
                }

                val subjectWithProgressList = rawSubjects.map { subject ->
                    val pb = progressBars.find { it.code.equals(subject.code, ignoreCase = true) }
                    SubjectWithProgress(subject = subject, progressBar = pb)
                }

                _uiState.update {
                    it.copy(
                        subjects = subjectWithProgressList,
                        isSubjectsLoading = false,
                        subjectsErrorMessage = if (subjectWithProgressList.isEmpty()) "কোনো বিষয় পাওয়া যায়নি" else null
                    )
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Fatal error in loadSubjects: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isSubjectsLoading = false,
                        subjectsErrorMessage = "বিষয় লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    )
                }
            }
        }
    }

    fun selectSubject(subjectCode: String, subjectTitle: String, subjectColor: String) {
        _uiState.update {
            it.copy(
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle,
                selectedSubjectColor = subjectColor
            )
        }
    }

    fun loadChaptersForSubject(
        subjectCode: String,
        subjectTitle: String? = null,
        subjectColor: String? = null,
        phaseId: String? = null
    ) {
        val currentProgId = _uiState.value.programId
        val progId = if (currentProgId.isNotBlank()) currentProgId else (sessionManager.getActiveProgramId() ?: "")
        val targetPhaseId = phaseId ?: _uiState.value.activePhaseId
        
        val matchedPhase = if (targetPhaseId.isNotBlank()) {
            _uiState.value.phases.find { it.id == targetPhaseId }
        } else {
            _uiState.value.selectedPhase
                ?: _uiState.value.phases.firstOrNull { it.is_current == true }
                ?: _uiState.value.phases.firstOrNull()
        }

        val effectivePhaseId = matchedPhase?.id ?: targetPhaseId

        val cacheKey = "${progId}_${subjectCode}_${effectivePhaseId}"
        val cachedChapters = chaptersCache[cacheKey]

        _uiState.update {
            it.copy(
                programId = progId,
                selectedSubjectCode = subjectCode,
                selectedSubjectTitle = subjectTitle ?: it.selectedSubjectTitle,
                selectedSubjectColor = subjectColor ?: it.selectedSubjectColor,
                selectedPhase = matchedPhase,
                activePhaseId = effectivePhaseId,
                activePhaseTitle = matchedPhase?.title ?: it.activePhaseTitle,
                chapters = cachedChapters ?: emptyList(),
                isChaptersLoading = cachedChapters == null,
                chaptersErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                var currentPhases = _uiState.value.phases
                var currentEffectivePhaseId = effectivePhaseId

                if (currentPhases.isEmpty() || _uiState.value.programId != progId) {
                    try {
                        val fetchedPhases = repository.getProgramPhases(progId)
                        if (fetchedPhases.isNotEmpty()) {
                            currentPhases = fetchedPhases
                            val activePhase = if (targetPhaseId.isNotBlank()) {
                                fetchedPhases.find { it.id == targetPhaseId }
                            } else {
                                fetchedPhases.firstOrNull { it.is_current == true }
                                    ?: fetchedPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                                    ?: fetchedPhases.firstOrNull()
                            }

                            currentEffectivePhaseId = activePhase?.id ?: targetPhaseId
                            _uiState.update {
                                it.copy(
                                    programId = progId,
                                    phases = fetchedPhases,
                                    selectedPhase = activePhase,
                                    activePhaseId = currentEffectivePhaseId,
                                    activePhaseTitle = activePhase?.title ?: ""
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CourseViewModel", "Error fetching phases in loadChaptersForSubject: ${e.message}", e)
                    }
                }

                var chaptersList = emptyList<AcademicChapterItem>()
                var queryError: Exception? = null

                // 1. If phaseId / effectivePhaseId is set, fetch phase/quarter-wise chapters strictly for this phase
                if (currentEffectivePhaseId.isNotBlank()) {
                    try {
                        chaptersList = repository.getPhaseWiseChapters(progId, currentEffectivePhaseId, subjectCode)
                    } catch (e: Exception) {
                        queryError = e
                        Log.e("CourseViewModel", "Error fetching phase-wise chapters for phase=$currentEffectivePhaseId: ${e.message}", e)
                    }
                }

                // 2. Only if the program has NO phases at all, try query without phase_id
                if (chaptersList.isEmpty() && currentPhases.isEmpty()) {
                    try {
                        val fallbackList = repository.getAcademicProgramChaptersFallback(progId, subjectCode)
                        if (fallbackList.isNotEmpty()) {
                            chaptersList = fallbackList
                        }
                    } catch (e: Exception) {
                        queryError = e
                        Log.e("CourseViewModel", "Error fetching academic program chapters fallback: ${e.message}", e)
                    }
                }

                if (chaptersList.isNotEmpty()) {
                    val finalCacheKey = "${progId}_${subjectCode}_${currentEffectivePhaseId}"
                    chaptersCache[finalCacheKey] = chaptersList
                }

                val finalError = if (chaptersList.isEmpty()) {
                    if (queryError != null) "অধ্যায় লোড করতে সমস্যা হয়েছে: ${queryError.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    else null
                } else null

                _uiState.update {
                    it.copy(
                        chapters = chaptersList,
                        isChaptersLoading = false,
                        chaptersErrorMessage = finalError
                    )
                }

                // Eagerly prefetch all lessons for this phase in background so chapter opening is instant (0ms)
                if (currentEffectivePhaseId.isNotBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
                            repository.fetchAllLessonsForProgram(progId, currentEffectivePhaseId, batchId)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Fatal error in loadChaptersForSubject: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chaptersErrorMessage = "অধ্যায় লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    )
                }
            }
        }
    }

    fun onSelectPhaseTab(phase: PhaseItem) {
        val currentSubjectCode = _uiState.value.selectedSubjectCode
        _uiState.update {
            it.copy(
                selectedPhase = phase,
                activePhaseId = phase.id,
                activePhaseTitle = phase.title ?: "",
                isChaptersLoading = true,
                chapters = emptyList(),
                chaptersErrorMessage = null
            )
        }
        if (currentSubjectCode.isNotBlank()) {
            loadChaptersForSubject(
                subjectCode = currentSubjectCode,
                subjectTitle = _uiState.value.selectedSubjectTitle,
                subjectColor = _uiState.value.selectedSubjectColor,
                phaseId = phase.id
            )
        }
    }

    fun selectChapter(chapterId: String, chapterName: String, chapterStatus: String = "") {
        val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
        val matchingChapter = _uiState.value.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val effChapterId = chapterId.ifBlank { matchingChapter?.chapter_id ?: matchingChapter?.id ?: "" }
        val effChapterName = chapterName.ifBlank { matchingChapter?.effectiveName ?: "" }
        val subjectTitle = _uiState.value.selectedSubjectTitle

        val candidateChapterIds = listOfNotNull(
            effChapterId,
            chapterId,
            matchingChapter?.id,
            matchingChapter?.chapter_id
        ).filter { it.isNotBlank() }.distinct()

        val otherChapters = _uiState.value.chapters.filter { ch ->
            val cid1 = ch.id.trim()
            val cid2 = (ch.chapter_id ?: "").trim()
            !candidateChapterIds.contains(cid1) && !candidateChapterIds.contains(cid2)
        }
        val otherChapterIds = otherChapters.flatMap { 
            listOfNotNull(it.id.takeIf { s -> s.isNotBlank() }, it.chapter_id?.takeIf { s -> s.isNotBlank() })
        }.distinct()
        val otherChapterNames = otherChapters.mapNotNull { it.effectiveName.takeIf { s -> s.isNotBlank() } }
        val chapterNoStr = matchingChapter?.effectiveNo?.toString()

        val cachedLessons = LessonCacheManager.findLessonsForChapter(
            candidateChapterIds = candidateChapterIds,
            chapterName = effChapterName,
            subjectTitle = subjectTitle,
            otherChapterIds = otherChapterIds,
            otherChapterNames = otherChapterNames,
            chapterNo = chapterNoStr,
            programId = progId
        ).ifEmpty {
            lessonsCache["${progId}_${effChapterId}"] ?: emptyList()
        }

        _uiState.update {
            it.copy(
                selectedChapterId = effChapterId,
                selectedChapterName = effChapterName,
                selectedChapterStatus = chapterStatus,
                lessons = cachedLessons,
                isLessonsLoading = cachedLessons.isEmpty(),
                lessonsErrorMessage = null,
                lessonsDiagnosticInfo = null
            )
        }
    }

    fun loadLessonsForChapter(
        chapterId: String,
        altChapterId: String? = null,
        chapterName: String? = null,
        chapterStatus: String? = null
    ) {
        val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
        val matchingChapter = _uiState.value.chapters.firstOrNull { it.id == chapterId || it.chapter_id == chapterId }
        val effChapterId = chapterId.ifBlank { altChapterId ?: matchingChapter?.chapter_id ?: matchingChapter?.id ?: "" }
        val effChapterName = chapterName ?: matchingChapter?.chapter_name ?: matchingChapter?.effectiveName
        val subjectTitle = _uiState.value.selectedSubjectTitle

        val candidateChapterIds = listOfNotNull(
            effChapterId,
            chapterId,
            altChapterId,
            matchingChapter?.id,
            matchingChapter?.chapter_id
        ).filter { it.isNotBlank() }.distinct()

        // Chapters in this quarter other than the selected chapter
        val otherChapters = _uiState.value.chapters.filter { ch ->
            val cid1 = ch.id.trim()
            val cid2 = (ch.chapter_id ?: "").trim()
            !candidateChapterIds.contains(cid1) && !candidateChapterIds.contains(cid2)
        }
        val otherChapterIds = otherChapters.flatMap { 
            listOfNotNull(it.id.takeIf { s -> s.isNotBlank() }, it.chapter_id?.takeIf { s -> s.isNotBlank() })
        }.distinct()
        val otherChapterNames = otherChapters.mapNotNull { it.effectiveName.takeIf { s -> s.isNotBlank() } }
        val chapterNoStr = matchingChapter?.effectiveNo?.toString()

        val cachedFromManager = LessonCacheManager.findLessonsForChapter(
            candidateChapterIds = candidateChapterIds,
            chapterName = effChapterName,
            subjectTitle = subjectTitle,
            otherChapterIds = otherChapterIds,
            otherChapterNames = otherChapterNames,
            chapterNo = chapterNoStr,
            programId = progId
        )
        val initialCached = if (cachedFromManager.isNotEmpty()) {
            cachedFromManager
        } else {
            lessonsCache["${progId}_${effChapterId}"]?.let { rawList ->
                LessonCacheManager.filterLessons(
                    lessons = rawList,
                    candidateChapterIds = candidateChapterIds,
                    chapterName = effChapterName,
                    subjectTitle = subjectTitle,
                    otherChapterIds = otherChapterIds,
                    otherChapterNames = otherChapterNames,
                    chapterNo = chapterNoStr,
                    programId = progId
                )
            }
        }

        val hasCurrentLessons = _uiState.value.selectedChapterId == effChapterId && _uiState.value.lessons.isNotEmpty()
        val effectiveInitial = initialCached ?: (if (hasCurrentLessons) _uiState.value.lessons else emptyList())

        _uiState.update {
            it.copy(
                selectedChapterId = effChapterId,
                selectedChapterName = effChapterName ?: it.selectedChapterName,
                selectedChapterStatus = chapterStatus ?: matchingChapter?.status ?: it.selectedChapterStatus,
                lessons = effectiveInitial,
                isLessonsLoading = effectiveInitial.isEmpty(),
                lessonsErrorMessage = null,
                lessonsDiagnosticInfo = null
            )
        }

        viewModelScope.launch {
            try {
                val phaseId = _uiState.value.activePhaseId.ifBlank {
                    _uiState.value.phases.firstOrNull { it.is_current == true }?.id
                        ?: _uiState.value.phases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }?.id
                        ?: _uiState.value.phases.firstOrNull()?.id
                        ?: ""
                }

                val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
                val allProgramLessons = repository.fetchAllLessonsForProgram(progId, phaseId.ifBlank { null }, batchId)
                
                var matched = LessonCacheManager.filterLessons(
                    lessons = allProgramLessons,
                    candidateChapterIds = candidateChapterIds,
                    chapterName = effChapterName,
                    subjectTitle = subjectTitle,
                    otherChapterIds = otherChapterIds,
                    otherChapterNames = otherChapterNames,
                    chapterNo = chapterNoStr,
                    programId = progId
                )

                if (matched.isEmpty() && effectiveInitial.isNotEmpty()) {
                    matched = effectiveInitial
                }

                if (matched.isNotEmpty()) {
                    lessonsCache["${progId}_${effChapterId}"] = matched
                    LessonCacheManager.saveLessons(matched, programId = progId)
                }

                val completedSet = completedItemRepository?.completedIdsState?.value ?: sessionManager.getCompletedLessonIds()
                val enrichedMatched = LessonCacheManager.enrichWithCompletedState(matched, completedSet)

                _uiState.update {
                    it.copy(
                        lessons = enrichedMatched,
                        isLessonsLoading = false,
                        lessonsErrorMessage = if (enrichedMatched.isEmpty()) "এই অধ্যায়ে কোনো ক্লাস পাওয়া যায়নি" else null,
                        lessonsDiagnosticInfo = null
                    )
                }
            } catch (e: Exception) {
                val completedSet = completedItemRepository?.completedIdsState?.value ?: sessionManager.getCompletedLessonIds()
                val enrichedInitial = LessonCacheManager.enrichWithCompletedState(effectiveInitial, completedSet)
                _uiState.update {
                    it.copy(
                        lessons = enrichedInitial,
                        isLessonsLoading = false,
                        lessonsErrorMessage = if (enrichedInitial.isEmpty()) "ক্লাস লোড করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}" else null
                    )
                }
            }
        }
    }
}

class CourseViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CourseViewModel::class.java)) {
            return CourseViewModel(apiService, sessionManager, completedItemRepository = completedItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
