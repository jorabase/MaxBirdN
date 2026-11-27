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

    suspend fun getTopics(
        chapterId: String,
        topicIds: List<String>? = null,
        chapterName: String? = null
    ): List<TopicFullItem> {
        return try {
            var list = repository.getTopics(chapterId, topicIds)
            var validVideoTopics = list.filter { topic ->
                val vList = topic.videos?.data
                !vList.isNullOrEmpty() && vList.any { !it.playback_url.isNullOrBlank() }
            }
            if (validVideoTopics.isEmpty() && !chapterName.isNullOrBlank()) {
                val subCode = _uiState.value.selectedSubjectCode
                if (subCode.isNotBlank()) {
                    val masterChapters = runCatching { repository.getChaptersBySubjectCode(subCode) }.getOrNull().orEmpty()
                    val matchedMaster = masterChapters.firstOrNull { ch ->
                        ch.effectiveName.trim().equals(chapterName.trim(), ignoreCase = true) ||
                            ch.effectiveName.contains(chapterName.trim(), ignoreCase = true) ||
                            chapterName.trim().contains(ch.effectiveName.trim(), ignoreCase = true)
                    }
                    val masterId = matchedMaster?.id?.takeIf { it.isNotBlank() && it != chapterId }
                    if (masterId != null) {
                        val retryList = repository.getTopics(masterId, topicIds)
                        val retryValid = retryList.filter { topic ->
                            val vList = topic.videos?.data
                            !vList.isNullOrEmpty() && vList.any { !it.playback_url.isNullOrBlank() }
                        }
                        if (retryValid.isNotEmpty()) {
                            list = retryList
                            validVideoTopics = retryValid
                        }
                    }
                }
            }
            if (validVideoTopics.isNotEmpty()) {
                _uiState.update { it.copy(animatedPlaylist = validVideoTopics) }
            }
            list
        } catch (e: Exception) {
            android.util.Log.e("CourseViewModel", "Error fetching topics for $chapterId", e)
            emptyList()
        }
    }

    fun setAnimatedPlaylist(topics: List<TopicFullItem>) {
        val valid = topics.filter { topic ->
            val vList = topic.videos?.data
            !vList.isNullOrEmpty() && vList.any { !it.playback_url.isNullOrBlank() }
        }
        if (valid.isNotEmpty()) {
            _uiState.update { it.copy(animatedPlaylist = valid) }
        }
    }

    /**
     * Fetches ALL syllabus chapters for Animated Lessons (e.g., all 28 chapters in Bangla 1st Paper)
     * rather than only the 2-3 chapters scheduled in a single live-class phase.
     */
    suspend fun getAllChaptersForAnimatedLessons(
        programId: String,
        phaseId: String,
        subjectCode: String,
        subjectTitle: String? = null
    ): List<AcademicChapterItem> {
        val effectiveTitle = subjectTitle?.takeIf { it.isNotBlank() } ?: _uiState.value.selectedSubjectTitle
        val userClass = sessionManager.getUserClassName()
        val userGroup = sessionManager.getUserGroup()

        return coroutineScope {
            val byCodeDeferred = async {
                runCatching { repository.getChaptersBySubjectCode(subjectCode) }.getOrNull().orEmpty()
            }
            val byHierarchyDeferred = async {
                runCatching {
                    repository.fetchFallbackHierarchyChapters(
                        subjectCode = subjectCode,
                        selectedSubjectTitle = effectiveTitle,
                        userClass = userClass,
                        userGroup = userGroup
                    )
                }.getOrNull().orEmpty()
            }
            val byPhaseDeferred = async {
                runCatching {
                    val effectiveProgId = programId.ifBlank { _uiState.value.programId.ifBlank { "6864d3a806800acba2e27099" } }
                    val effectivePhaseId = phaseId.ifBlank { _uiState.value.activePhaseId.ifBlank { "6864d62506800acba2e27111" } }
                    val phaseList = repository.getPhaseWiseChapters(effectiveProgId, effectivePhaseId, subjectCode)
                    val progList = repository.getAcademicProgramChaptersFallback(effectiveProgId, subjectCode)
                    (phaseList + progList)
                }.getOrNull().orEmpty()
            }

            val byCode = byCodeDeferred.await()
            val byHierarchy = byHierarchyDeferred.await()
            val byPhase = byPhaseDeferred.await()

            val mergedMap = LinkedHashMap<String, AcademicChapterItem>()

            fun addChapters(list: List<AcademicChapterItem>) {
                for (item in list) {
                    val masterId = item.chapter_id?.takeIf { it.isNotBlank() } ?: item.id
                    if (masterId.isBlank()) continue
                    val normalized = item.copy(
                        id = masterId,
                        chapter_id = masterId
                    )
                    if (!mergedMap.containsKey(masterId)) {
                        mergedMap[masterId] = normalized
                    }
                }
            }

            // 1. Primary: Full syllabus chapters by subject_code (e.g., all 28 chapters)
            addChapters(byCode)
            // 2. Secondary: Full hierarchy chapters for the student's class & group
            addChapters(byHierarchy)
            // 3. Tertiary: Any phase/program chapters not already in the map
            addChapters(byPhase)

            mergedMap.values.toList()
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
                val byCode = repository.getChaptersBySubjectCode(subjectCode)
                if (byCode.isNotEmpty()) {
                    byCode
                } else {
                    repository.fetchFallbackHierarchyChapters(
                        subjectCode = subjectCode,
                        selectedSubjectTitle = _uiState.value.selectedSubjectTitle,
                        userClass = sessionManager.getUserClassName(),
                        userGroup = sessionManager.getUserGroup()
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CourseViewModel", "Error fetching chapters for $subjectCode", e)
            try {
                val byCode = repository.getChaptersBySubjectCode(subjectCode)
                if (byCode.isNotEmpty()) byCode
                else repository.fetchFallbackHierarchyChapters(
                    subjectCode = subjectCode,
                    selectedSubjectTitle = _uiState.value.selectedSubjectTitle,
                    userClass = sessionManager.getUserClassName(),
                    userGroup = sessionManager.getUserGroup()
                )
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun getSavedShikhoServerJoinedIds(): Set<String> {
        return try {
            // Clear any legacy local unlock prefs so no course is ever unlocked without Admin approval
            sessionManager.appContext
                .getSharedPreferences("maxbird_native_trial_free_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            val prefs = sessionManager.appContext.getSharedPreferences("maxbird_shikho_server_join_prefs", android.content.Context.MODE_PRIVATE)
            val phoneKey = sessionManager.getNormalizedUserPhone().ifBlank { "default" }
            prefs.getStringSet("joined_ids_$phoneKey", emptySet()) ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun markCourseJoinedOnShikhoServerPrefs(programId: String) {
        if (programId.isBlank()) return
        try {
            val prefs = sessionManager.appContext.getSharedPreferences("maxbird_shikho_server_join_prefs", android.content.Context.MODE_PRIVATE)
            val phoneKey = sessionManager.getNormalizedUserPhone().ifBlank { "default" }
            val current = (prefs.getStringSet("joined_ids_$phoneKey", emptySet()) ?: emptySet()).toMutableSet()
            current.add(programId)
            prefs.edit().putStringSet("joined_ids_$phoneKey", current).apply()
        } catch (_: Exception) {}
    }

    /**
     * শুধুমাত্র মূল Shikho সার্ভারের ডাটাবেজে ৩ দিনের ফ্রি ট্রায়াল (AvailTrial) বা ফ্রি কোর্সে (EnrollInFreeProgram)
     * জয়েন করায় যেন Shikho API থেকে কোর্সটি কাজ করে। আমাদের অ্যাপের ভেতরে কোর্সটি অ্যাডমিন থেকে না কেনা পর্যন্ত লকই থাকবে।
     */
    private suspend fun ensureCourseJoinedOnShikhoServer(
        programId: String,
        isFreeCourse: Boolean
    ): Result<String> {
        if (programId.isBlank()) return Result.failure(Exception(com.example.utils.AppLang.tr("Course ID not found", "কোর্স আইডি পাওয়া যায়নি")))
        val userId = sessionManager.getUserId().orEmpty()
        return if (isFreeCourse) {
            val freeRes = repository.enrollInFreeProgram(programId)
            if (freeRes.isSuccess) {
                markCourseJoinedOnShikhoServerPrefs(programId)
                freeRes
            } else {
                val trialFallback = repository.generateFreeTrialEnrolment(programId, userId)
                if (trialFallback.isSuccess) {
                    markCourseJoinedOnShikhoServerPrefs(programId)
                }
                trialFallback
            }
        } else {
            val trialRes = repository.generateFreeTrialEnrolment(programId, userId)
            if (trialRes.isSuccess) {
                markCourseJoinedOnShikhoServerPrefs(programId)
                trialRes
            } else {
                val freeFallback = repository.enrollInFreeProgram(programId)
                if (freeFallback.isSuccess) {
                    markCourseJoinedOnShikhoServerPrefs(programId)
                    freeFallback
                } else {
                    trialRes
                }
            }
        }
    }

    fun joinCourseOnShikhoServer(
        program: OtherProgram,
        context: android.content.Context? = null
    ) {
        val programId = program.id
        val programTitle = program.title_bn ?: com.example.utils.AppLang.tr("Course", "কোর্স")
        val isFree = program.is_free == true

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    joiningShikhoServerProgramId = programId,
                    enrollmentErrorMessage = null,
                    enrollmentSuccessMessage = null
                )
            }

            try {
                val res = ensureCourseJoinedOnShikhoServer(programId = programId, isFreeCourse = isFree)
                CourseRepository.clearCache()

                if (res.isSuccess) {
                    val updatedJoined = _uiState.value.shikhoServerJoinedIds + programId
                    val msg = if (isFree) {
                        com.example.utils.AppLang.tr(
                            "✅ '$programTitle' added on main server! Tap 'Get Course Access' to activate it in the app via admin.",
                            "✅ '$programTitle' মূল সার্ভারে যুক্ত হয়েছে! এবার কোর্সটি আমাদের অ্যাপে চালু করতে 'কোর্সে এক্সেস পান' বাটনে ক্লিক করে অ্যাডমিন থেকে এক্সেস নিন।"
                        )
                    } else {
                        com.example.utils.AppLang.tr(
                            "✅ 3-day free trial for '$programTitle' added! Tap 'Get Course Access' to purchase via admin.",
                            "✅ '$programTitle' কোর্সে মূল সার্ভারের ৩ দিনের ফ্রি ট্রায়াল যুক্ত হয়েছে! এবার কোর্সটি চালু করতে 'কোর্সে এক্সেস পান' বাটনে ক্লিক করে অ্যাডমিন থেকে কিনুন।"
                        )
                    }
                    if (context != null) {
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                    _uiState.update {
                        it.copy(
                            shikhoServerJoinedIds = updatedJoined,
                            joiningShikhoServerProgramId = null,
                            enrollmentSuccessMessage = msg
                        )
                    }
                    fetchEnrolledPrograms()
                } else {
                    val errMsg = res.exceptionOrNull()?.localizedMessage
                        ?: com.example.utils.AppLang.tr("Already added or could not enroll on main server", "মূল সার্ভারে ইতিমধ্যে যুক্ত আছে অথবা যুক্ত করা যায়নি")
                    if (context != null) {
                        android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                    _uiState.update {
                        it.copy(
                            joiningShikhoServerProgramId = null,
                            enrollmentErrorMessage = errMsg
                        )
                    }
                    fetchEnrolledPrograms()
                }
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: com.example.utils.AppLang.tr("Failed to enroll on main server", "মূল সার্ভারে যুক্ত করতে সমস্যা হয়েছে")
                if (context != null) {
                    android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                }
                _uiState.update {
                    it.copy(
                        joiningShikhoServerProgramId = null,
                        enrollmentErrorMessage = errMsg
                    )
                }
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
        val hasToken = !sessionManager.getAccessToken().isNullOrBlank()
        val isActivated = !com.example.security.DeviceActivationRepository.isConfigured() ||
                com.example.security.DeviceActivationRepository.isDeviceActivated(sessionManager.appContext)
        if (hasToken && isActivated) {
            fetchEnrolledPrograms()
            loadSubjects()
        }
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

    fun openCourse(program: EnrolledProgram, targetPhaseId: String? = null) {
        val snapshot = _uiState.value.commerceSnapshot
        // কঠোর নিয়ম: আমাদের অ্যাপে অ্যাডমিন থেকে কোর্স কেনা / অনুমোদন ছাড়া কোনো কোর্সেই প্রবেশ করা যাবে না
        if (!snapshot.isCourseActivelyUnlocked(program.id, program.title_bn)) {
            val itemState = snapshot.resolveCourseItemState(program.id, program.title_bn)
            if (itemState.effectiveState == CourseAccessState.INSTALLMENT_OVERDUE ||
                itemState.effectiveState == CourseAccessState.APPROVED_TO_BUY ||
                itemState.effectiveState == CourseAccessState.PAYMENT_REJECTED
            ) {
                openPaymentSheetForCourse(program.id, program.title_bn ?: com.example.utils.AppLang.tr("Course", "কোর্স"))
            }
            return
        }

        // যদি মূল Shikho সার্ভারে এখনো এই কোর্সে ৩ দিনের ট্রায়াল বা ফ্রি এনরোলমেন্ট নেওয়া না থাকে, তবে ব্যাকগ্রাউন্ডে নিয়ে নেবে যাতে কোর্সটি কাজ করে
        if (program.id !in _uiState.value.shikhoServerJoinedIds) {
            viewModelScope.launch {
                try {
                    val joinRes = ensureCourseJoinedOnShikhoServer(
                        programId = program.id,
                        isFreeCourse = program.is_free == true
                    )
                    if (joinRes.isSuccess) {
                        CourseRepository.clearCache()
                        _uiState.update { it.copy(shikhoServerJoinedIds = it.shikhoServerJoinedIds + program.id) }
                    }
                } catch (_: Exception) {}
            }
        }

        val title = program.title_bn ?: com.example.utils.AppLang.tr("Program", "প্রোগ্রাম")
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
            classCode = program.classes?.firstOrNull(),
            targetPhaseId = targetPhaseId
        )
    }

    fun closeCourseDetails() {
        _uiState.update { it.copy(selectedCourseProgram = null) }
    }

    fun openPaymentSheetForCourse(programId: String, courseTitle: String) {
        _uiState.update {
            it.copy(
                activePaymentSheetProgramId = programId,
                activePaymentSheetCourseTitle = courseTitle
            )
        }
    }

    fun dismissPaymentSheet() {
        _uiState.update {
            it.copy(
                activePaymentSheetProgramId = null,
                activePaymentSheetCourseTitle = ""
            )
        }
    }

    fun requestCoursePurchaseAccess(
        programId: String,
        programTitle: String,
        bannerUrl: String?,
        isFreeOnShikho: Boolean = false,
        context: android.content.Context? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    requestingAccessProgramId = programId,
                    enrollmentErrorMessage = null,
                    enrollmentSuccessMessage = null
                )
            }

            val classCode = sessionManager.getUserClassName() ?: "C11"
            val groupCode = sessionManager.getUserGroup() ?: "Humanities"
            val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()

            val result = CourseCommerceRepository.requestCoursePurchaseAccess(
                context = sessionManager.appContext,
                programId = programId,
                programTitle = programTitle,
                classCode = classCode,
                groupCode = groupCode,
                batchId = batchId,
                bannerUrl = bannerUrl
            )

            result.onSuccess { msg ->
                if (context != null) {
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
                val updatedSnapshot = CourseCommerceRepository.fetchCourseCommerceStatus(sessionManager.appContext)
                _uiState.update {
                    it.copy(
                        commerceSnapshot = updatedSnapshot,
                        requestingAccessProgramId = null,
                        enrollmentSuccessMessage = msg
                    )
                }
                fetchEnrolledPrograms()
            }.onFailure { err ->
                val errMsg = err.localizedMessage ?: com.example.utils.AppLang.tr("Failed to send request", "রিকোয়েস্ট পাঠাতে সমস্যা হয়েছে")
                if (context != null) {
                    android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                }
                _uiState.update {
                    it.copy(
                        requestingAccessProgramId = null,
                        enrollmentErrorMessage = errMsg
                    )
                }
            }
        }
    }

    fun startCourseFreeTrial(
        programId: String,
        programTitle: String,
        context: android.content.Context? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    startingTrialProgramId = programId,
                    enrollmentErrorMessage = null,
                    enrollmentSuccessMessage = null
                )
            }

            // প্রথমে আমাদের অ্যাডমিন ডাটাবেজে (Supabase) অ্যাডমিনের অনুমোদিত ফ্রি ট্রায়াল চালু করা
            val result = CourseCommerceRepository.startCourseFreeTrial(
                context = sessionManager.appContext,
                programId = programId,
                programTitle = programTitle
            )

            result.onSuccess { msg ->
                // শুধুমাত্র অ্যাডমিন অনুমোদিত ফ্রি ট্রায়াল সফলভাবে চালু হলেই মূল সার্ভারে যুক্ত করা হবে
                try {
                    val targetOther = _uiState.value.otherPrograms.find { it.id == programId }
                    ensureCourseJoinedOnShikhoServer(
                        programId = programId,
                        isFreeCourse = targetOther?.is_free == true
                    )
                } catch (_: Exception) {}

                if (context != null) {
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
                CourseRepository.clearCache()
                val updatedSnapshot = CourseCommerceRepository.fetchCourseCommerceStatus(sessionManager.appContext)
                _uiState.update {
                    it.copy(
                        commerceSnapshot = updatedSnapshot,
                        startingTrialProgramId = null,
                        activePaymentSheetProgramId = null,
                        activePaymentSheetCourseTitle = "",
                        enrollmentSuccessMessage = msg
                    )
                }
                fetchEnrolledPrograms()
            }.onFailure { err ->
                val errMsg = err.localizedMessage ?: com.example.utils.AppLang.tr("Failed to start free trial", "ফ্রি ট্রায়াল চালু করতে সমস্যা হয়েছে")
                if (context != null) {
                    android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                }
                _uiState.update {
                    it.copy(
                        startingTrialProgramId = null,
                        enrollmentErrorMessage = errMsg
                    )
                }
            }
        }
    }

    fun submitCoursePayment(
        programId: String,
        courseTitle: String,
        paymentMethod: String,
        senderMobile: String,
        amount: Double,
        transactionId: String,
        selectedPlan: InstallmentPlanOption,
        context: android.content.Context? = null,
        onPaymentSubmitted: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    submittingPaymentProgramId = programId,
                    enrollmentErrorMessage = null,
                    enrollmentSuccessMessage = null
                )
            }

            val result = CourseCommerceRepository.submitCoursePaymentTransaction(
                context = sessionManager.appContext,
                programId = programId,
                programTitle = courseTitle,
                paymentMethod = paymentMethod,
                senderMobile = senderMobile,
                amount = amount,
                transactionId = transactionId,
                selectedPlan = selectedPlan
            )

            result.onSuccess { msg ->
                if (context != null) {
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
                val updatedSnapshot = CourseCommerceRepository.fetchCourseCommerceStatus(sessionManager.appContext)
                _uiState.update {
                    it.copy(
                        commerceSnapshot = updatedSnapshot,
                        submittingPaymentProgramId = null,
                        activePaymentSheetProgramId = null,
                        activePaymentSheetCourseTitle = "",
                        enrollmentSuccessMessage = msg
                    )
                }
                fetchEnrolledPrograms()
                onPaymentSubmitted?.invoke()
            }.onFailure { err ->
                val errMsg = err.localizedMessage ?: com.example.utils.AppLang.tr("Failed to submit payment", "পেমেন্ট জমা দিতে সমস্যা হয়েছে")
                if (context != null) {
                    android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                }
                _uiState.update {
                    it.copy(
                        submittingPaymentProgramId = null,
                        enrollmentErrorMessage = errMsg
                    )
                }
            }
        }
    }

    fun clearForLogout() {
        chaptersCache.clear()
        lessonsCache.clear()
        _uiState.value = CourseUiState()
    }

    fun fetchEnrolledPrograms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProgramsLoading = true) }
            try {
                if (sessionManager.getNormalizedUserPhone().isBlank() && !sessionManager.getAccessToken().isNullOrBlank()) {
                    try {
                        val uid = sessionManager.getUserId() ?: ""
                        if (uid.isNotBlank()) {
                            val profileQuery = GraphQlQuery(
                                operationName = "GetProfile",
                                query = "query GetProfile(\$user_id: String, \$type: String!) { profile(user_id: \$user_id, type: \$type) { id first_name last_name avatar study_group class { code display } school { id name } user { phone email } } }",
                                variables = mapOf("user_id" to uid, "type" to "student")
                            )
                            val profResp = apiService.getProfile(profileQuery)
                            val profPhone = profResp.data?.profile?.user?.phone
                            if (!profPhone.isNullOrBlank()) {
                                sessionManager.saveUserPhone(profPhone)
                            }
                        }
                    } catch (_: Exception) {}
                }

                val commerceSnapshot = CourseCommerceRepository.fetchCourseCommerceStatus(sessionManager.appContext)

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
                val rawEnrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                val rawOtherAll = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()

                val allCombinedEnrolled = (rawEnrolled + rawOtherAll.map { it.toEnrolledProgram() }).distinctBy { it.id }
                val allCombinedOther = (rawOtherAll + rawEnrolled.map {
                    OtherProgram(
                        id = it.id,
                        classes = it.classes,
                        title_bn = it.title_bn,
                        banner_url = it.banner_url,
                        is_free = it.is_free,
                        has_animated_video = it.has_animated_video,
                        trial_enabled = it.trial_enabled
                    )
                }).distinctBy { it.id }

                // মূল Shikho সার্ভারের ডাটাবেজে যেসব কোর্সে এই নাম্বারটি ইতিমধ্যে যুক্ত আছে (enrolled_programs)
                val rawEnrolledIds = rawEnrolled.map { it.id }.toSet()
                rawEnrolledIds.forEach { markCourseJoinedOnShikhoServerPrefs(it) }
                val joinedOnShikhoServer = rawEnrolledIds + getSavedShikhoServerJoinedIds()

                // ১. শুধুমাত্র যেসব কোর্স স্টুডেন্ট আমাদের অ্যাপে অ্যাডমিন থেকে কিনেছে বা অ্যাডমিন আনলক করেছে সেগুলোই enrolledPrograms এ থাকবে!
                val activeEnrolledList = allCombinedEnrolled.filter { prog ->
                    commerceSnapshot.isCourseActivelyUnlocked(prog.id, prog.title_bn)
                }

                // অ্যাডমিন আনলক করা কোনো কোর্স যদি মূল Shikho সার্ভারে এখনো জয়েন না থাকে, তবে ব্যাকগ্রাউন্ডে অটোমেটিক ৩ দিনের ট্রায়াল/ফ্রি জয়েন করিয়ে নেবে
                val unlockedButNotJoinedOnShikho = activeEnrolledList.filter { it.id !in joinedOnShikhoServer }
                if (unlockedButNotJoinedOnShikho.isNotEmpty()) {
                    viewModelScope.launch {
                        var anyJoined = false
                        for (prog in unlockedButNotJoinedOnShikho) {
                            try {
                                val res = ensureCourseJoinedOnShikhoServer(
                                    programId = prog.id,
                                    isFreeCourse = prog.is_free == true
                                )
                                if (res.isSuccess) anyJoined = true
                            } catch (_: Exception) {}
                        }
                        if (anyJoined) {
                            CourseRepository.clearCache()
                            _uiState.update { state ->
                                state.copy(shikhoServerJoinedIds = state.shikhoServerJoinedIds + getSavedShikhoServerJoinedIds())
                            }
                        }
                    }
                }

                val activeIds = activeEnrolledList.map { it.id }.toSet()

                // ২. বাকি সকল কোর্স লক অবস্থায় (otherPrograms) থাকবে যেখান থেকে স্টুডেন্ট মূল সার্ভারে জয়েন করতে এবং 'কোর্সে এক্সেস পান' রিকোয়েস্ট পাঠিয়ে কিনতে পারবে
                val lockedOrPurchasableList = allCombinedOther.filter { it.id !in activeIds }

                _uiState.update {
                    val currentSelected = it.selectedCourseProgram
                    val validSelected = if (currentSelected != null && currentSelected.id in activeIds) currentSelected else null
                    it.copy(
                        commerceSnapshot = commerceSnapshot,
                        shikhoServerJoinedIds = joinedOnShikhoServer,
                        enrolledPrograms = activeEnrolledList,
                        freePrograms = emptyList(),
                        otherPrograms = lockedOrPurchasableList,
                        selectedCourseProgram = validSelected,
                        isProgramsLoading = false
                    )
                }
            } catch (e: Exception) {
                val commerceSnapshot = CourseCommerceRepository.getCachedSnapshot(sessionManager.appContext)
                val joinedOnShikhoServer = getSavedShikhoServerJoinedIds()
                _uiState.update { current ->
                    current.copy(
                        commerceSnapshot = commerceSnapshot,
                        shikhoServerJoinedIds = joinedOnShikhoServer,
                        isProgramsLoading = false,
                        enrollmentErrorMessage = if (current.enrolledPrograms.isEmpty() && current.otherPrograms.isEmpty()) {
                            com.example.utils.AppLang.tr(
                                "Could not load course list. Please check your internet connection and try again.",
                                "কোর্স তালিকা লোড করা যায়নি। ইন্টারনেট সংযোগ যাচাই করে আবার চেষ্টা করুন।"
                            )
                        } else null
                    )
                }
            }
        }
    }

    fun enrollInCourse(
        program: OtherProgram,
        context: android.content.Context? = null,
        onEnrollmentSuccess: ((EnrolledProgram) -> Unit)? = null
    ) {
        val snapshot = _uiState.value.commerceSnapshot
        val itemState = snapshot.resolveCourseItemState(program.id, program.title_bn)

        when (itemState.effectiveState) {
            CourseAccessState.LOCKED_BLURRED -> {
                requestCoursePurchaseAccess(
                    programId = program.id,
                    programTitle = program.title_bn ?: com.example.utils.AppLang.tr("Course", "কোর্স"),
                    bannerUrl = program.banner_url,
                    context = context
                )
            }
            CourseAccessState.ACCESS_REQUESTED -> {
                if (context != null) {
                    android.widget.Toast.makeText(
                        context,
                        com.example.utils.AppLang.tr(
                            "Your request is pending admin review. Once pricing is set, payment options will appear here.",
                            "আপনার রিকোয়েস্ট অ্যাডমিন প্যানেলে পেন্ডিং আছে। অ্যাডমিন দাম ও কিস্তি নির্ধারণ করলে এখানে কেনার অপশন চালু হবে।"
                        ),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                fetchEnrolledPrograms()
            }
            CourseAccessState.APPROVED_TO_BUY,
            CourseAccessState.PAYMENT_REJECTED,
            CourseAccessState.INSTALLMENT_OVERDUE -> {
                openPaymentSheetForCourse(program.id, program.title_bn ?: com.example.utils.AppLang.tr("Course", "কোর্স"))
            }
            CourseAccessState.PAYMENT_PENDING -> {
                if (context != null) {
                    android.widget.Toast.makeText(
                        context,
                        com.example.utils.AppLang.tr(
                            "Your payment is being verified. The course will unlock once approved by admin.",
                            "আপনার পেমেন্ট যাচাই চলছে। অ্যাডমিন এপ্রুভ করলেই কোর্সটি আনলক হয়ে যাবে।"
                        ),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                fetchEnrolledPrograms()
            }
            CourseAccessState.ACTIVE -> {
                val enrolledProg = program.toEnrolledProgram()
                openCourse(enrolledProg)
                onEnrollmentSuccess?.invoke(enrolledProg)
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
        // If the lesson already has a playable URL, start playing immediately (0ms delay!)
        val needsBlockingWait = !hasDirectStream && candidateIds.isNotEmpty()

        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                isLessonDetailLoading = needsBlockingWait
            )
        }

        android.util.Log.d("LectureDebug", "selectLesson: ${lesson.title}, candidateIds: $candidateIds, hasDirectStream: $hasDirectStream, needsBlockingWait: $needsBlockingWait")

        if (candidateIds.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    var liveClassData: AcademicProgramLiveClassItem? = null
                    var foundWorkingId: String? = null

                    // Query candidate IDs concurrently in parallel for blazing-fast response
                    val deferredList = candidateIds.map { cid ->
                        async { cid to repository.getLiveClassDetails(cid) }
                    }
                    val results = deferredList.awaitAll()
                    for ((cid, details) in results) {
                        if (details != null) {
                            if (liveClassData == null) {
                                liveClassData = details
                                foundWorkingId = cid
                            }
                            if (!details.playback_url.isNullOrBlank()) {
                                liveClassData = details
                                foundWorkingId = cid
                                break
                            }
                        }
                    }

                    android.util.Log.d("LectureDebug", "Resolved live class details (id=$foundWorkingId, hasStream=${!liveClassData?.playback_url.isNullOrBlank()})")

                    var pbUrl = liveClassData?.playback_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.recording_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.stream_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.video_url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.url?.takeIf { it.isNotBlank() && it != "null" }
                        ?: liveClassData?.hls_url?.takeIf { it.isNotBlank() && it != "null" }

                    // IMPORTANT: Never use animated topic videos (getTopics) as playback_url for a lesson in selectLesson!
                    // Topic videos are separate animated lessons shown in the bottom section, not the live class stream.
                    if (liveClassData != null || !pbUrl.isNullOrBlank()) {
                        val newAttachments = mutableListOf<LessonAttachmentItem>()
                        liveClassData?.study_materials?.forEach { mat ->
                            if (!mat.file_url.isNullOrBlank()) {
                                newAttachments.add(
                                    LessonAttachmentItem(
                                        id = mat.id,
                                        title = mat.name ?: com.example.utils.AppLang.tr("Lecture Slide (PDF)", "লেকচার স্লাইড (PDF)"),
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
                            is_on_going = liveClassData?.on_going ?: lesson.live_class?.is_on_going,
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
                        android.util.Log.d("LectureDebug", "Resolved lesson media (hasVideo=${!currentLessonState.resolvedVideoUrl.isNullOrBlank()}, hasSlide=${!currentLessonState.resolvedSlideUrl.isNullOrBlank()}, attachments=${currentLessonState.allAttachments.size})")

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
     * Selects and loads a lesson by its ID directly.
     */
    fun selectLessonById(lessonId: String) {
        val existing = _uiState.value.lessons.find { it.id == lessonId }
            ?: StudentLessonItem(id = lessonId, title = com.example.utils.AppLang.tr("Class Lecture", "ক্লাস লেকচার"))
        selectLesson(existing)
    }

    /**
     * Switches the browsed program inside CourseViewModel without overwriting the Home screen's saved active program.
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
        val title = if (!newProgramTitle.isNullOrBlank()) newProgramTitle else com.example.utils.AppLang.tr("HSC Course", "এইচএসসি কোর্স")
        // NOTE: Do NOT call sessionManager.saveActiveProgram(...) here!
        // sessionManager.saveActiveProgram is strictly reserved for the Home screen's Course Switcher.
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
            val programTitle = if (currentProgTitle.isNotBlank()) currentProgTitle else (sessionManager.getActiveProgramTitleBn() ?: com.example.utils.AppLang.tr("HSC Course", "এইচএসসি কোর্স"))

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
                        subjectsErrorMessage = com.example.utils.AppLang.tr("No active course found. Please select a course from Home.", "কোনো সক্রিয় কোর্স পাওয়া যায়নি। অনুগ্রহ করে হোম পেজ থেকে একটি কোর্স নির্বাচন করুন।")
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
                        val activePhase = (if (!targetPhaseId.isNullOrBlank()) {
                            fetchedPhases.find { it.id == targetPhaseId }
                        } else null)
                            ?: fetchedPhases.firstOrNull { it.is_current == true }
                            ?: fetchedPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                            ?: fetchedPhases.firstOrNull { it.has_enrolment == true && !it.status.equals("COMPLETED", true) }
                            ?: fetchedPhases.firstOrNull { !it.status.equals("COMPLETED", true) }
                            ?: fetchedPhases.firstOrNull()

                        currentPhaseId = activePhase?.id ?: ""
                        val isModelTest = activePhase?.type.equals("ModelTest", ignoreCase = true) ||
                                          fetchedPhases.any { it.type.equals("ModelTest", ignoreCase = true) }
                        _uiState.update {
                            it.copy(
                                programId = programId,
                                phases = phasesList,
                                selectedPhase = activePhase,
                                activePhaseId = currentPhaseId,
                                activePhaseTitle = activePhase?.title ?: "",
                                isModelTestCourse = isModelTest
                            )
                        }
                        if (programId.isNotBlank() && currentPhaseId.isNotBlank() && programId == sessionManager.getActiveProgramId()) {
                            try {
                                sessionManager.saveActiveProgramPhaseId(currentPhaseId)
                                com.example.notification.ShikhoNotificationManager.updateProgramAndPhase(
                                    com.example.ShikhoApp.instance,
                                    programId,
                                    currentPhaseId
                                )
                            } catch (_: Throwable) {}
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
                        subjectsErrorMessage = if (subjectWithProgressList.isEmpty()) com.example.utils.AppLang.tr("No subjects found", "কোনো বিষয় পাওয়া যায়নি") else null
                    )
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Fatal error in loadSubjects: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isSubjectsLoading = false,
                        subjectsErrorMessage = com.example.utils.AppLang.tr(
                            "Failed to load subjects: ${e.localizedMessage ?: "Network error"}",
                            "বিষয় লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                        )
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
                    if (queryError != null) com.example.utils.AppLang.tr(
                        "Failed to load chapters: ${queryError.localizedMessage ?: "Network error"}",
                        "অধ্যায় লোড করতে সমস্যা হয়েছে: ${queryError.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    )
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
                            val batchId = _uiState.value.selectedCourseProgram?.enrollment_details?.batch_id
                                ?: sessionManager.getActiveProgramBatchId()
                                ?: sessionManager.getUserBatchId()
                            repository.fetchAllLessonsForProgram(progId, currentEffectivePhaseId, batchId)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Fatal error in loadChaptersForSubject: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chaptersErrorMessage = com.example.utils.AppLang.tr(
                            "Could not load chapters: ${e.localizedMessage ?: "Network error"}",
                            "অধ্যায় লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                        )
                    )
                }
            }
        }
    }

    fun onSelectPhaseTab(phase: PhaseItem) {
        val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
        if (progId.isNotBlank() && progId == sessionManager.getActiveProgramId()) {
            try {
                sessionManager.saveActiveProgramPhaseId(phase.id)
                com.example.notification.ShikhoNotificationManager.updateProgramAndPhase(
                    com.example.ShikhoApp.instance,
                    progId,
                    phase.id
                )
            } catch (_: Throwable) {}
        }
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

                val batchId = _uiState.value.selectedCourseProgram?.enrollment_details?.batch_id
                    ?: sessionManager.getActiveProgramBatchId()
                    ?: sessionManager.getUserBatchId()
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
                        lessonsErrorMessage = if (enrichedMatched.isEmpty()) com.example.utils.AppLang.tr("No classes found in this chapter", "এই অধ্যায়ে কোনো ক্লাস পাওয়া যায়নি") else null,
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
                        lessonsErrorMessage = if (enrichedInitial.isEmpty()) com.example.utils.AppLang.tr(
                            "Failed to load classes: ${e.localizedMessage ?: "Network error"}",
                            "ক্লাস লোড করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                        ) else null
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
