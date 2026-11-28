package com.example.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isRoutineLoading: Boolean = false,
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
    val activeProgram: EnrolledProgram? = null,
    val showCourseSwitcher: Boolean = false,
    val weeklyRoutine: List<StudentLessonItem> = emptyList(),
    val userProfile: UserProfile? = null,
    val userName: String = "",
    val userFirstName: String = "",
    val userAvatar: String? = null,
    val userClass: String = "",
    val userGroup: String = "",
    val userSchool: String = "",
    val isPremium: Boolean = false,
    val errorMessage: String? = null,
    // Subject Filter / Customizer State
    val showSubjectFilterDialog: Boolean = false,
    val courseSubjects: List<AcademicSubjectItem> = emptyList(),
    val isCourseSubjectsLoading: Boolean = false,
    val selectedSubjectCodes: Set<String> = emptySet(),
    val isSavingSubjectFilter: Boolean = false,
    val programPhases: List<PhaseItem> = emptyList(),
    // Subjects available for standalone Features (Practice Quiz & Animated Lessons) on Home Screen
    val featureSubjects: List<AcademicSubjectItem> = emptyList(),
    val isFeatureSubjectsLoading: Boolean = false,
    // Dynamic In-App Notices (Admin Managed)
    val notices: List<com.example.notice.AppNotice> = emptyList(),
    val showNoticePopup: Boolean = false
) {
    /**
     * Filtered weekly routine containing only lessons matching the selected subjects.
     * By default, all subjects are selected. If a subject is unselected in the subject filter,
     * its lessons will be excluded here.
     */
    val filteredWeeklyRoutine: List<StudentLessonItem>
        get() {
            if (selectedSubjectCodes.isEmpty() && courseSubjects.isNotEmpty()) {
                return emptyList()
            }
            if (selectedSubjectCodes.isEmpty()) return weeklyRoutine

            val filtered = weeklyRoutine.filter { lesson ->
                val code = lesson.subject_id ?: lesson.live_class?.subject_id ?: ""
                val name = lesson.subject_name ?: lesson.live_class?.subject_name ?: ""
                selectedSubjectCodes.any { selected ->
                    selected.equals(code, ignoreCase = true) ||
                    selected.equals(name, ignoreCase = true) ||
                    courseSubjects.any { sub -> 
                        (sub.code.equals(selected, ignoreCase = true) || (sub.display_bn != null && sub.display_bn.equals(selected, ignoreCase = true))) &&
                        (sub.code.equals(code, ignoreCase = true) || (sub.display_bn != null && (name.contains(sub.display_bn, ignoreCase = true) || code.contains(sub.code ?: "", ignoreCase = true))))
                    }
                }
            }
            return filtered
        }
}

class HomeViewModel(
    application: Application,
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            userName = sessionManager.getUserFullName() ?: com.example.utils.AppLang.tr("Student", "শিক্ষার্থী"),
            userFirstName = sessionManager.getUserFirstName() ?: (sessionManager.getUserFullName()?.split(" ")?.firstOrNull() ?: com.example.utils.AppLang.tr("Student", "শিক্ষার্থী")),
            userAvatar = sessionManager.getUserAvatar(),
            userClass = sessionManager.getUserClassDisplay() ?: com.example.syllabus.formatClassDisplayName(sessionManager.getUserClassName()),
            userGroup = sessionManager.getUserGroup().orEmpty(),
            userSchool = sessionManager.getUserSchoolName() ?: ""
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val wallpaperConfigFlow: StateFlow<com.example.ui.theme.HeaderWallpaperConfig> = sessionManager.headerWallpaperFlow

    init {
        val hasToken = !sessionManager.getAccessToken().isNullOrBlank()
        val isActivated = !com.example.security.DeviceActivationRepository.isConfigured() ||
                com.example.security.DeviceActivationRepository.isDeviceActivated(getApplication())
        if (hasToken && isActivated) {
            loadData()
        }
        completedItemRepository?.let { repo ->
            viewModelScope.launch {
                repo.completedIdsState.collect { completedSet ->
                    if (_uiState.value.weeklyRoutine.isNotEmpty()) {
                        val enriched = com.example.course.LessonCacheManager.enrichWithCompletedState(_uiState.value.weeklyRoutine, completedSet)
                        _uiState.value = _uiState.value.copy(weeklyRoutine = enriched)
                    }
                }
            }
        }
    }

    fun setCourseSwitcherVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showCourseSwitcher = visible)
    }

    fun switchActiveCourse(program: EnrolledProgram) {
        // Cancel all previous course alarms immediately
        com.example.notification.ClassAlarmScheduler.cancelAllAlarms(getApplication())
        sessionManager.saveActiveProgram(
            programId = program.id,
            titleBn = program.title_bn,
            batchId = program.enrollment_details?.batch_id,
            classCode = program.classes?.firstOrNull(),
            phaseId = null
        )

        // Ensure newly selected course is synced while keeping all other active enrolled courses subscribed
        com.example.notification.ShikhoNotificationManager.updateProgramAndPhase(
            context = getApplication(),
            newProgramId = program.id,
            newPhaseId = null,
            courseTitleBn = program.title_bn
        )

        // Load saved subject filter for this course if any
        val savedSubjects = sessionManager.getSelectedSubjectCodes(program.id) ?: emptySet()
        val initialSubjects = program.subjects?.map { 
            AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
        } ?: emptyList()

        _uiState.value = _uiState.value.copy(
            activeProgram = program,
            showCourseSwitcher = false,
            weeklyRoutine = emptyList(),
            programPhases = emptyList(),
            selectedSubjectCodes = savedSubjects,
            courseSubjects = initialSubjects,
            featureSubjects = initialSubjects
        )
        fetchWeeklyRoutine(program)
        loadCourseSubjects(program)
    }

    fun openSubjectFilterDialog() {
        val program = _uiState.value.activeProgram ?: return
        val saved = sessionManager.getSelectedSubjectCodes(program.id) ?: emptySet()
        _uiState.value = _uiState.value.copy(
            showSubjectFilterDialog = true,
            selectedSubjectCodes = saved
        )
        loadCourseSubjects(program)
    }

    fun dismissSubjectFilterDialog() {
        _uiState.value = _uiState.value.copy(showSubjectFilterDialog = false)
    }

    fun toggleSubjectSelection(subjectCode: String) {
        val current = _uiState.value.selectedSubjectCodes.toMutableSet()
        if (current.contains(subjectCode)) {
            current.remove(subjectCode)
        } else {
            current.add(subjectCode)
        }
        _uiState.value = _uiState.value.copy(selectedSubjectCodes = current)
    }

    fun selectAllSubjects() {
        val allCodes = _uiState.value.courseSubjects.mapNotNull { it.code }.toSet()
        _uiState.value = _uiState.value.copy(selectedSubjectCodes = allCodes)
    }

    fun clearAllSubjectSelection() {
        _uiState.value = _uiState.value.copy(selectedSubjectCodes = emptySet())
    }

    fun saveSubjectFilter() {
        val program = _uiState.value.activeProgram ?: return
        val selected = _uiState.value.selectedSubjectCodes
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingSubjectFilter = true)
            // 1. Save locally to SessionManager for instant persistence
            sessionManager.saveSelectedSubjectCodes(program.id, selected)

            // 2. Optionally sync with backend if mutation is supported
            try {
                if (selected.isNotEmpty()) {
                    val upsertQuery = GraphQlQuery(
                        operationName = "UpsertUserPrioritySubjects",
                        query = """
                            mutation UpsertUserPrioritySubjects(${'$'}program_id: String!, ${'$'}subjects: [String]!) {
                              upsertUserPrioritySubjects(academic_program_id: ${'$'}program_id, subjects: ${'$'}subjects) {
                                id
                                academic_program_id
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf(
                            "program_id" to program.id,
                            "subjects" to selected.toList()
                        )
                    )
                    apiService.upsertPrioritySubjects(upsertQuery)
                }
            } catch (_: Exception) {
                // Ignore API error and rely on local storage
            } finally {
                _uiState.value = _uiState.value.copy(
                    isSavingSubjectFilter = false,
                    showSubjectFilterDialog = false
                )
            }
        }
    }

    fun loadCourseSubjects(program: EnrolledProgram, phaseIdOverride: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCourseSubjectsLoading = true,
                isFeatureSubjectsLoading = true
            )
            val subjectsList = mutableListOf<AcademicSubjectItem>()

            // 1. Start with subjects already attached to EnrolledProgram if any
            program.subjects?.let { subs ->
                subjectsList.addAll(subs.map {
                    AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
                })
            }

            val resolvedPhaseId = phaseIdOverride
                ?: _uiState.value.programPhases.firstOrNull { it.is_current == true }?.id
                ?: _uiState.value.programPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }?.id
                ?: _uiState.value.programPhases.firstOrNull()?.id
                ?: sessionManager.getActiveProgramPhaseId()

            var fetchedFromApi: List<AcademicSubjectItem>? = null

            // 2a. Try fetching full subjects for this course with phase_id (matches CourseRepository)
            if (!resolvedPhaseId.isNullOrBlank()) {
                try {
                    val phaseSubjectsQuery = GraphQlQuery(
                        operationName = "GetAcademicSubjects",
                        query = """
                            query GetAcademicSubjects(${'$'}programId: String!, ${'$'}phase_id: String) {
                              academicProgram(id: ${'$'}programId, show_subject_progress_bar: true, phase_id: ${'$'}phase_id) {
                                subjects {
                                  code
                                  color_code
                                  display_bn
                                  icon
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("programId" to program.id, "phase_id" to resolvedPhaseId)
                    )
                    val res = apiService.getAcademicSubjects(phaseSubjectsQuery)
                    val list = res.data?.academicProgram?.subjects
                    if (!list.isNullOrEmpty()) {
                        fetchedFromApi = list
                    }
                } catch (_: Exception) {}
            }

            // 2b. Fallback: Try fetching full subjects for this course without phase_id
            if (fetchedFromApi.isNullOrEmpty()) {
                try {
                    val subjectsQuery = GraphQlQuery(
                        operationName = "GetAcademicSubjects",
                        query = """
                            query GetAcademicSubjects(${'$'}programId: String!) {
                              academicProgram(id: ${'$'}programId, show_subject_progress_bar: true) {
                                subjects {
                                  code
                                  color_code
                                  display_bn
                                  icon
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("programId" to program.id)
                    )
                    val res = apiService.getAcademicSubjects(subjectsQuery)
                    val list = res.data?.academicProgram?.subjects
                    if (!list.isNullOrEmpty()) {
                        fetchedFromApi = list
                    }
                } catch (_: Exception) {}
            }

            if (!fetchedFromApi.isNullOrEmpty()) {
                subjectsList.clear()
                subjectsList.addAll(fetchedFromApi)
            }

            // 3. Try fallback to UserPrioritySubjects for this specific program if still empty
            if (subjectsList.isEmpty()) {
                try {
                    val pQuery = GraphQlQuery(
                        operationName = "UserPrioritySubjects",
                        query = """
                            query UserPrioritySubjects(${'$'}academic_program_id: String!) {
                              userPrioritySubjects(academic_program_id: ${'$'}academic_program_id) {
                                subjects {
                                  code
                                  color_code
                                  display
                                  display_bn
                                  icon
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("academic_program_id" to program.id)
                    )
                    val pRes = apiService.getPrioritySubjects(pQuery)
                    val pSubs = pRes.data?.userPrioritySubjects?.subjects
                    if (!pSubs.isNullOrEmpty()) {
                        pSubs.forEach { p ->
                            subjectsList.add(
                                AcademicSubjectItem(
                                    code = p.code,
                                    color_code = p.color_code,
                                    display_bn = p.display_bn ?: p.display,
                                    icon = p.icon
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // Guard against race conditions if user switched to a different course while loading
            val currentActiveId = _uiState.value.activeProgram?.id
            if (currentActiveId != null && currentActiveId != program.id) {
                return@launch
            }

            // Deduplicate by code / display_bn and filter out blank entries
            val distinctSubjects = subjectsList
                .filter { !it.code.isNullOrBlank() || !it.display_bn.isNullOrBlank() }
                .distinctBy { it.code ?: it.display_bn }

            // Load saved preference; if user has not customized yet (savedSelection == null), default ALL subjects selected!
            val savedSelection = sessionManager.getSelectedSubjectCodes(program.id)
            val allCodes = distinctSubjects.mapNotNull { it.code ?: it.display_bn }.toSet()
            val effectiveSelection = savedSelection ?: if (_uiState.value.selectedSubjectCodes.isNotEmpty()) _uiState.value.selectedSubjectCodes else allCodes

            _uiState.value = _uiState.value.copy(
                courseSubjects = distinctSubjects,
                featureSubjects = distinctSubjects,
                isCourseSubjectsLoading = false,
                isFeatureSubjectsLoading = false,
                selectedSubjectCodes = effectiveSelection
            )
        }
    }

    fun loadFeatureSubjects(forceRefresh: Boolean = false) {
        val active = _uiState.value.activeProgram
        if (active != null) {
            if (forceRefresh || _uiState.value.courseSubjects.isEmpty()) {
                loadCourseSubjects(active)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                featureSubjects = emptyList(),
                isFeatureSubjectsLoading = false
            )
        }
    }

    fun clearForLogout() {
        _uiState.value = HomeUiState(isLoading = false)
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            try {
                val j1 = viewModelScope.launch { fetchUserProfile() }
                j1.join()
                val j2 = viewModelScope.launch { fetchAcademicPrograms() }
                j2.join()

                // 3. Fetch dynamic in-app announcements / notices from Admin (Supabase)
                fetchAppNotices(forceRefresh = isRefresh)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.localizedMessage ?: com.example.utils.AppLang.tr("Failed to load courses", "কোর্স লোড করতে সমস্যা হয়েছে")
                )
            }
        }
    }

    fun dismissNoticePopup(dontShowAgainToday: Boolean = false) {
        val notices = _uiState.value.notices
        com.example.notice.AppNoticeRepository.markPopupDismissed(getApplication(), notices, dontShowAgainToday)
        _uiState.value = _uiState.value.copy(showNoticePopup = false)
    }

    fun refreshNotices() {
        fetchAppNotices(forceRefresh = true)
    }

    private fun fetchAppNotices(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                val notices = com.example.notice.AppNoticeRepository.getActiveNotices(forceRefresh = forceRefresh)
                val shouldShow = com.example.notice.AppNoticeRepository.shouldShowPopup(getApplication(), notices)
                _uiState.value = _uiState.value.copy(
                    notices = notices,
                    showNoticePopup = shouldShow
                )
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to fetch notices: ${e.message}")
            }
        }
    }

    private fun classCodeToBengaliName(code: String?): String {
        return com.example.syllabus.formatClassDisplayName(code)
    }

    private suspend fun fetchUserProfile() {
        try {
            val userId = sessionManager.getUserId() ?: ""
            val profileQuery = GraphQlQuery(
                operationName = "GetProfile",
                query = "query GetProfile(\$user_id: String, \$type: String!) { profile(user_id: \$user_id, type: \$type) { id first_name last_name avatar gender dob study_group passing_year class { code display } school { id name } user { phone email } } }",
                variables = mapOf(
                    "user_id" to userId,
                    "type" to "student"
                )
            )
            val response = apiService.getProfile(profileQuery)
            val profile = response.data?.profile
            if (profile != null) {
                val firstName = profile.first_name?.trim() ?: ""
                val lastName = profile.last_name?.trim() ?: ""
                val fullName = when {
                    firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                    firstName.isNotBlank() -> firstName
                    lastName.isNotBlank() -> lastName
                    else -> ""
                }
                val first = when {
                    firstName.isNotBlank() -> firstName
                    fullName.isNotBlank() -> fullName.split(" ").firstOrNull() ?: fullName
                    else -> com.example.utils.AppLang.tr("Student", "শিক্ষার্থী")
                }

                val effectiveClass = profile.`class`?.code?.takeIf { it.isNotBlank() }
                    ?: sessionManager.getUserClassName()
                val effectiveGroup = profile.study_group?.takeIf { it.isNotBlank() }
                    ?: sessionManager.getUserGroup()
                val effectivePassingYear = com.example.syllabus.extractPassingYear(profile.passing_year)
                    ?: com.example.syllabus.extractPassingYear(sessionManager.getAcademicPassingYear())
                    ?: com.example.syllabus.extractPassingYear(sessionManager.getUserBatchId())
                val currentBatchId = com.example.syllabus.formatSyllabusBatchId(
                    classCode = effectiveClass,
                    passingYear = effectivePassingYear,
                    fallbackBatchLabel = sessionManager.getUserBatchId()
                )
                val effectiveClassDisplay = profile.`class`?.display?.takeIf { it.isNotBlank() }
                    ?: classCodeToBengaliName(effectiveClass)

                sessionManager.saveUserProfile(
                    firstName = firstName.ifBlank { first },
                    lastName = lastName,
                    avatar = profile.avatar,
                    schoolName = profile.school?.name,
                    classDisplay = effectiveClassDisplay,
                    phone = profile.user?.phone ?: sessionManager.getUserPhone()
                )
                sessionManager.saveUserAcademicInfo(
                    batchId = currentBatchId,
                    className = effectiveClass,
                    group = effectiveGroup,
                    vendor = sessionManager.getUserVendor() ?: "BD",
                    passingYear = effectivePassingYear
                )

                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    userName = fullName.ifBlank { first },
                    userFirstName = first,
                    userAvatar = profile.avatar,
                    userClass = effectiveClassDisplay,
                    userGroup = effectiveGroup.orEmpty(),
                    userSchool = profile.school?.name ?: ""
                )
            }
        } catch (_: Exception) {
            // Profile fetch failed; fallback to cached session values
        }
    }

    private suspend fun fetchAcademicPrograms() {
        val className = sessionManager.getUserClassName()?.takeIf { it.isNotBlank() }
        if (className == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            return
        }
        val formattedGroup = com.example.syllabus.normalizeStudyGroupEnum(className, sessionManager.getUserGroup())
        // Always use the user's current syllabus batch (NOT active_program_batch_id of a previously enrolled course)
        val batchId = com.example.syllabus.formatSyllabusBatchId(
            classCode = className,
            passingYear = sessionManager.getAcademicPassingYear(),
            fallbackBatchLabel = sessionManager.getUserBatchId()
        )
        val vendor = sessionManager.getUserVendor() ?: "BD"

        try {
            val response = apiService.getAcademicProgram(
                GraphQlQuery(
                    operationName = "GetAcademicProgram",
                    query = """
                        query GetAcademicProgram(${'$'}batch_id: String, ${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum, ${'$'}vendor: VendorEnum) {
                          listAcademicProgramByEnrollment(batch_id: ${'$'}batch_id, class: ${'$'}className, group: ${'$'}group, vendor: ${'$'}vendor) {
                            enrolled_programs {
                              id classes title_bn banner_url color is_free trial_enabled has_animated_video
                              enrollment_details { batch_id is_active trial_end_date type expiry_date }
                              subjects { code display_bn color_code icon }
                            }
                            other_programs {
                              id classes title_bn banner_url phase_pricing is_free has_animated_video trial_enabled trial_duration
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf(
                        "batch_id" to batchId,
                        "className" to className,
                        "group" to formattedGroup,
                        "vendor" to vendor
                    )
                )
            )

            val enrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
            val others = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()

            val promotedOthers = others.map { it.toEnrolledProgram() }
            val unfilteredAllPrograms = (enrolled + promotedOthers).distinctBy { it.id }

            // Fetch student's real-time course purchase & installment status from Supabase
            val commerceSnapshot = com.example.course.CourseCommerceRepository.fetchCourseCommerceStatus(getApplication())

            // শুধুমাত্র কেনা অথবা কিস্তি চলমান (মেয়াদ থাকা) কোর্সগুলোই হোমে আনলক অবস্থায় শো হবে
            val allPrograms = unfilteredAllPrograms.filter { prog ->
                commerceSnapshot.isCourseActivelyUnlocked(prog.id, prog.title_bn)
            }

            // একটিভ কোর্স নির্ধারণ (হোম স্ক্রিনের কোর্স সুইচারে ইউজারের সিলেক্ট করা কোর্স সবসময় স্থির থাকবে)
            val savedProgramId = sessionManager.getActiveProgramId()
            val savedTitleBn = sessionManager.getActiveProgramTitleBn()
            val currentMemoryId = _uiState.value.activeProgram?.id

            val active = allPrograms.firstOrNull { !savedProgramId.isNullOrBlank() && it.id == savedProgramId }
                ?: allPrograms.firstOrNull { !currentMemoryId.isNullOrBlank() && it.id == currentMemoryId }
                ?: allPrograms.firstOrNull { !savedTitleBn.isNullOrBlank() && it.title_bn == savedTitleBn }
                ?: allPrograms.firstOrNull()

            if (active != null) {
                if (savedProgramId != null && savedProgramId != active.id) {
                    com.example.notification.ClassAlarmScheduler.cancelAllAlarms(getApplication())
                }
                sessionManager.saveActiveProgram(
                    programId = active.id,
                    titleBn = active.title_bn,
                    batchId = active.enrollment_details?.batch_id,
                    classCode = active.classes?.firstOrNull()
                )
            } else if (allPrograms.isEmpty() && unfilteredAllPrograms.isNotEmpty()) {
                com.example.notification.ClassAlarmScheduler.cancelAllAlarms(getApplication())
                com.example.notification.ShikhoNotificationManager.unsubscribeActiveCourseTopics(getApplication())
                sessionManager.clearActiveProgram()
            }

            // Smart Multi-Course Auto-Subscribe + Auto-Unsubscribe Expired Courses (Background)
            // লগইনের সাথে সাথে সব অ্যাক্টিভ এনরোল করা কোর্স ও তাদের অ্যাক্টিভ ফেজ একসাথে সাবস্ক্রাইব হবে
            // এবং মেয়াদ শেষ হওয়া কোর্সগুলো অটোমেটিক আনসাবস্ক্রাইব হয়ে যাবে।
            if (enrolled.isNotEmpty() || allPrograms.isNotEmpty()) {
                syncAllEnrolledCoursesFcmInBackground(
                    enrolledFromShikho = enrolled,
                    unlockedPrograms = allPrograms
                )
            }

            val hasActiveEnrollment = active != null

            val activeSavedSubjects = if (active != null) sessionManager.getSelectedSubjectCodes(active.id) else null
            val activeInitialSubjects = active?.subjects?.map {
                AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
            } ?: emptyList()
            val initialCodes = activeInitialSubjects.mapNotNull { it.code ?: it.display_bn }.toSet()
            val initialSelected = activeSavedSubjects ?: initialCodes

            _uiState.value = _uiState.value.copy(
                enrolledPrograms = allPrograms,
                activeProgram = active,
                weeklyRoutine = if (active == null) emptyList() else _uiState.value.weeklyRoutine,
                selectedSubjectCodes = initialSelected,
                courseSubjects = activeInitialSubjects,
                featureSubjects = activeInitialSubjects,
                isPremium = hasActiveEnrollment,
                isLoading = false,
                isRefreshing = false,
                errorMessage = null
            )

            if (active != null) {
                fetchWeeklyRoutine(active)
                loadCourseSubjects(active)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
        }
    }

    /**
     * Automatically subscribes all active (non-expired) enrolled courses + their current active phases
     * to Shikho's FCM topics in the background, and unsubscribes expired/inactive courses.
     */
    private fun syncAllEnrolledCoursesFcmInBackground(
        enrolledFromShikho: List<EnrolledProgram>,
        unlockedPrograms: List<EnrolledProgram>
    ) {
        val unlockedIds = unlockedPrograms.map { it.id }.toSet()
        val combinedPrograms = (enrolledFromShikho + unlockedPrograms).distinctBy { it.id }

        val initialTargets = combinedPrograms.map { prog ->
            val details = prog.enrollment_details
            val expiredByShikho = com.example.notification.ShikhoNotificationManager.isCourseExpired(
                isActive = details?.is_active,
                expiryDate = details?.expiry_date
            )
            val isEffectiveActive = (prog.id in unlockedIds) && !expiredByShikho
            com.example.notification.ShikhoNotificationManager.EnrolledCourseFcmTarget(
                programId = prog.id,
                titleBn = prog.title_bn?.trim().orEmpty(),
                phaseId = if (prog.id == sessionManager.getActiveProgramId()) {
                    sessionManager.getActiveProgramPhaseId()
                } else null,
                isActive = isEffectiveActive,
                expiryDate = details?.expiry_date
            )
        }

        val activeTargets = initialTargets.filter { it.isActive }
        if (activeTargets.isEmpty()) {
            com.example.notification.ShikhoNotificationManager.syncEnrolledCourses(
                context = getApplication(),
                enrolledCourses = initialTargets
            )
            return
        }

        // Resolve current active Phase ID for EVERY active enrolled course in parallel FIRST
        // (fast 4.5s timeout) so we trigger ONE single complete FCM sync with all active courses + phases!
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedPhasesMap = withTimeoutOrNull(4500L) {
                runCatching {
                    coroutineScope {
                        activeTargets.map { target ->
                            async {
                                val phaseQuery = GraphQlQuery(
                                    operationName = "ProgramPhasesByStudent",
                                    query = """
                                        query ProgramPhasesByStudent(${'$'}program_id: String!, ${'$'}course_progress_percentage: Boolean) {
                                          programPhasesByStudent(program_id: ${'$'}program_id, course_progress_percentage: ${'$'}course_progress_percentage) {
                                            data {
                                              id
                                              is_current
                                              status
                                              title
                                            }
                                          }
                                        }
                                    """.trimIndent(),
                                    variables = mapOf(
                                        "program_id" to target.programId,
                                        "course_progress_percentage" to false
                                    )
                                )
                                val phaseRes = runCatching { apiService.getProgramPhases(phaseQuery) }.getOrNull()
                                val phases = phaseRes?.data?.programPhasesByStudent?.data ?: emptyList()
                                val currentPhase = phases.firstOrNull { it.is_current == true }
                                    ?: phases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                                    ?: phases.firstOrNull()
                                target.programId to currentPhase?.id?.trim()?.takeIf { it.isNotBlank() }
                            }
                        }.awaitAll().toMap()
                    }
                }.getOrNull()
            } ?: emptyMap()

            val fullTargets = initialTargets.map { target ->
                val resolvedPhaseId = resolvedPhasesMap[target.programId] ?: target.phaseId
                if (target.programId == sessionManager.getActiveProgramId() && !resolvedPhaseId.isNullOrBlank()) {
                    sessionManager.saveActiveProgramPhaseId(resolvedPhaseId)
                }
                target.copy(phaseId = resolvedPhaseId)
            }

            com.example.notification.ShikhoNotificationManager.syncEnrolledCourses(
                context = getApplication(),
                enrolledCourses = fullTargets
            )
        }
    }

    private fun fetchWeeklyRoutine(program: EnrolledProgram) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRoutineLoading = true)
            try {
                val programId = program.id

                // ১. কোর্সের ফেজ আনা (কোয়ার্টার ১, ২, ৩ অথবা "ফুল কোর্স")
                val phaseQuery = GraphQlQuery(
                    operationName = "ProgramPhasesByStudent",
                    query = """
                        query ProgramPhasesByStudent(${'$'}program_id: String!, ${'$'}course_progress_percentage: Boolean) {
                          programPhasesByStudent(program_id: ${'$'}program_id, course_progress_percentage: ${'$'}course_progress_percentage) {
                            data {
                              academic_program_id
                              batch_id
                              end_date
                              start_date
                              id
                              has_enrolment
                              has_free_trial_enrolment
                              is_backlog
                              is_current
                              title
                              facebook_group_url
                              syllabus_attachment_url
                              is_last_month
                              is_purchasable
                              type
                              report_exists
                              report_version
                              course_progress_percentage
                              status
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf("program_id" to programId, "course_progress_percentage" to true)
                )
                val phaseRes = runCatching { apiService.getProgramPhases(phaseQuery) }.getOrNull()
                val finalPhases = phaseRes?.data?.programPhasesByStudent?.data ?: emptyList()
                _uiState.value = _uiState.value.copy(programPhases = finalPhases)

                // ২. কারেন্ট / একটিভ ফেজ সিলেক্ট করা
                val currentPhase = finalPhases.firstOrNull { it.is_current == true }
                    ?: finalPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                    ?: finalPhases.firstOrNull()

                if (currentPhase == null || currentPhase.id.isBlank()) {
                    _uiState.value = _uiState.value.copy(weeklyRoutine = emptyList(), isRoutineLoading = false)
                    return@launch
                }

                // শিখো সার্ভারের প্রোগ্রাম ও ফেজ ফায়ারবেস টপিক সাবস্ক্রিপশন সিঙ্ক করা (projects/shikho-tech)
                if (programId == sessionManager.getActiveProgramId() || _uiState.value.activeProgram?.id == programId) {
                    sessionManager.saveActiveProgramPhaseId(currentPhase.id)
                    com.example.notification.ShikhoNotificationManager.updateProgramAndPhase(
                        context = getApplication(),
                        newProgramId = programId,
                        newPhaseId = currentPhase.id,
                        courseTitleBn = program.title_bn
                    )
                    // ফেজ আইডি পাওয়ার সাথে সাথে এই কোর্সের সঠিক বিষয়সমূহ লোড করা
                    loadCourseSubjects(program, currentPhase.id)
                }

                // ৩. অফিসিয়াল অ্যাপের মতো চলতি সপ্তাহের শনিবার থেকে শুক্রবার (৭ দিন) ক্যালকুলেট করা
                val dhakaZone = TimeZone.getTimeZone("Asia/Dhaka")
                val cal = Calendar.getInstance(dhakaZone)
                // বর্তমান দিনের সপ্তাহের শনিবার বের করা (বাংলাদেশ ক্যালেন্ডার অনুযায়ী)
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startCal = cal.clone() as Calendar

                cal.add(Calendar.DAY_OF_MONTH, 6) // শুক্রবার
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endCal = cal.clone() as Calendar

                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val startDate = isoFormat.format(startCal.time)
                val endDate = isoFormat.format(endCal.time)

                // ৪. অফিসিয়াল অ্যাপের মতো একক কুয়েরি পাঠানো
                val routineQuery = GraphQlQuery(
                    operationName = "GetStudentSpecificLessons",
                    query = """
                        query GetStudentSpecificLessons(${'$'}programId: String!, ${'$'}phaseId: String!, ${'$'}startDate: String!, ${'$'}endDate: String!) {
                          studentSpecificLessons(program_id: ${'$'}programId, phase_id: ${'$'}phaseId, start_date: ${'$'}startDate, end_date: ${'$'}endDate) {
                            data {
                              access_level hw_type start_time subject_id subject_name title end_time icon id content_id content_type user_activity_state batch_id chapter_id
                              live_class { chapter_id chapter_name end_time is_on_going start_time subject_name subject_id id type }
                              model_test { result_publish_time type exam_category }
                              topics { id name }
                              color_code phase_id
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf(
                        "programId" to programId,
                        "phaseId" to currentPhase.id,
                        "startDate" to startDate,
                        "endDate" to endDate
                    )
                )

                val routineResponse = runCatching { apiService.getStudentLessons(routineQuery) }.getOrNull()
                val routineLessons = routineResponse?.data?.studentSpecificLessons?.data ?: emptyList()

                com.example.course.LessonCacheManager.saveLessons(routineLessons, programId = programId)
                com.example.notification.ClassAlarmScheduler.schedule7DayClassAlarms(getApplication(), programId, routineLessons)

                // ৫. খালি আসলে খালিই থাকবে, স্প্যাম কুয়েরি হবে না
                _uiState.value = _uiState.value.copy(
                    weeklyRoutine = routineLessons.sortedBy { it.start_time ?: "" },
                    isRoutineLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(weeklyRoutine = emptyList(), isRoutineLoading = false)
            }
        }
    }

    fun fetchMonthlyRoutine(
        year: Int,
        monthZeroIndexed: Int,
        program: EnrolledProgram? = _uiState.value.activeProgram
    ) {
        val targetProgram = program ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRoutineLoading = true)
            try {
                val programId = targetProgram.id
                var phases = _uiState.value.programPhases
                if (phases.isEmpty()) {
                    val phaseQuery = GraphQlQuery(
                        operationName = "ProgramPhasesByStudent",
                        query = """
                            query ProgramPhasesByStudent(${'$'}program_id: String!, ${'$'}course_progress_percentage: Boolean) {
                              programPhasesByStudent(program_id: ${'$'}program_id, course_progress_percentage: ${'$'}course_progress_percentage) {
                                data {
                                  academic_program_id
                                  batch_id
                                  end_date
                                  start_date
                                  id
                                  has_enrolment
                                  has_free_trial_enrolment
                                  is_backlog
                                  is_current
                                  title
                                  facebook_group_url
                                  syllabus_attachment_url
                                  is_last_month
                                  is_purchasable
                                  type
                                  report_exists
                                  report_version
                                  course_progress_percentage
                                  status
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("program_id" to programId, "course_progress_percentage" to true)
                    )
                    val phaseRes = runCatching { apiService.getProgramPhases(phaseQuery) }.getOrNull()
                    phases = phaseRes?.data?.programPhasesByStudent?.data ?: emptyList()
                    _uiState.value = _uiState.value.copy(programPhases = phases)
                }

                val currentPhase = phases.firstOrNull { it.is_current == true }
                    ?: phases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                    ?: phases.firstOrNull()

                if (currentPhase == null || currentPhase.id.isBlank()) {
                    _uiState.value = _uiState.value.copy(weeklyRoutine = emptyList(), isRoutineLoading = false)
                    return@launch
                }

                if (programId == sessionManager.getActiveProgramId() || _uiState.value.activeProgram?.id == programId) {
                    sessionManager.saveActiveProgramPhaseId(currentPhase.id)
                    com.example.notification.ShikhoNotificationManager.updateProgramAndPhase(
                        context = getApplication(),
                        newProgramId = programId,
                        newPhaseId = currentPhase.id,
                        courseTitleBn = _uiState.value.activeProgram?.title_bn
                    )
                }

                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, monthZeroIndexed)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val startCal = cal.clone() as Calendar

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endCal = cal.clone() as Calendar

                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val startDate = isoFormat.format(startCal.time)
                val endDate = isoFormat.format(endCal.time)

                val routineQuery = GraphQlQuery(
                    operationName = "GetStudentSpecificLessons",
                    query = """
                        query GetStudentSpecificLessons(${'$'}programId: String!, ${'$'}phaseId: String!, ${'$'}startDate: String!, ${'$'}endDate: String!) {
                          studentSpecificLessons(program_id: ${'$'}programId, phase_id: ${'$'}phaseId, start_date: ${'$'}startDate, end_date: ${'$'}endDate) {
                            data {
                              access_level hw_type start_time subject_id subject_name title end_time icon id content_id content_type user_activity_state batch_id chapter_id
                              live_class { chapter_id chapter_name end_time is_on_going start_time subject_name subject_id id type }
                              model_test { result_publish_time type exam_category }
                              topics { id name }
                              color_code phase_id
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf(
                        "programId" to programId,
                        "phaseId" to currentPhase.id,
                        "startDate" to startDate,
                        "endDate" to endDate
                    )
                )

                val routineResponse = runCatching { apiService.getStudentLessons(routineQuery) }.getOrNull()
                val routineLessons = routineResponse?.data?.studentSpecificLessons?.data ?: emptyList()
                com.example.course.LessonCacheManager.saveLessons(routineLessons, programId = programId)
                com.example.notification.ClassAlarmScheduler.schedule7DayClassAlarms(getApplication(), programId, routineLessons)
                val completedSet = completedItemRepository?.completedIdsState?.value ?: sessionManager.getCompletedLessonIds()
                val enrichedRoutine = com.example.course.LessonCacheManager.enrichWithCompletedState(routineLessons, completedSet)
                _uiState.value = _uiState.value.copy(
                    weeklyRoutine = enrichedRoutine.sortedBy { it.start_time ?: "" },
                    isRoutineLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(weeklyRoutine = emptyList(), isRoutineLoading = false)
            }
        }
    }
}

class HomeViewModelFactory(
    private val application: android.app.Application,
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val completedItemRepository: com.example.database.CompletedItemRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(application, apiService, sessionManager, completedItemRepository = completedItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
