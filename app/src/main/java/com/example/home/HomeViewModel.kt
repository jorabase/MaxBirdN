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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val programPhases: List<PhaseItem> = emptyList()
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
            userName = sessionManager.getUserFullName() ?: "শিক্ষার্থী",
            userFirstName = sessionManager.getUserFirstName() ?: (sessionManager.getUserFullName()?.split(" ")?.firstOrNull() ?: "শিক্ষার্থী"),
            userAvatar = sessionManager.getUserAvatar(),
            userClass = sessionManager.getUserClassDisplay() ?: sessionManager.getUserClassName() ?: "একাদশ শ্রেণি",
            userGroup = sessionManager.getUserGroup() ?: "মানবিক",
            userSchool = sessionManager.getUserSchoolName() ?: ""
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val wallpaperConfigFlow: StateFlow<com.example.ui.theme.HeaderWallpaperConfig> = sessionManager.headerWallpaperFlow

    init {
        loadData()
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
            classCode = program.classes?.firstOrNull()
        )
        // Load saved subject filter for this course if any
        val savedSubjects = sessionManager.getSelectedSubjectCodes(program.id) ?: emptySet()
        val initialSubjects = program.subjects?.map { 
            AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
        } ?: emptyList()

        _uiState.value = _uiState.value.copy(
            activeProgram = program,
            showCourseSwitcher = false,
            selectedSubjectCodes = savedSubjects,
            courseSubjects = initialSubjects
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

    fun loadCourseSubjects(program: EnrolledProgram) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCourseSubjectsLoading = true)
            val subjectsList = mutableListOf<AcademicSubjectItem>()
            
            // 1. Start with subjects already attached to EnrolledProgram if any
            program.subjects?.let { subs ->
                subjectsList.addAll(subs.map {
                    AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
                })
            }

            // 2. Try fetching full subjects via GetAcademicSubjects
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
                val fetched = res.data?.academicProgram?.subjects
                if (!fetched.isNullOrEmpty()) {
                    subjectsList.clear()
                    subjectsList.addAll(fetched)
                }
            } catch (_: Exception) {}

            // 3. Try fallback to GetPrioritySubjects or SubjectHierarchy
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
                            subjectsList.add(AcademicSubjectItem(code = p.code, color_code = p.color_code, display_bn = p.display_bn ?: p.display, icon = p.icon))
                        }
                    }
                } catch (_: Exception) {}
            }

            // Deduplicate by code
            val distinctSubjects = subjectsList.distinctBy { it.code ?: it.display_bn }
            
            // Load saved preference; if user has not customized yet (savedSelection == null), default ALL subjects selected!
            val savedSelection = sessionManager.getSelectedSubjectCodes(program.id)
            val allCodes = distinctSubjects.mapNotNull { it.code ?: it.display_bn }.toSet()
            val effectiveSelection = savedSelection ?: if (_uiState.value.selectedSubjectCodes.isNotEmpty()) _uiState.value.selectedSubjectCodes else allCodes

            _uiState.value = _uiState.value.copy(
                courseSubjects = distinctSubjects,
                isCourseSubjectsLoading = false,
                selectedSubjectCodes = effectiveSelection
            )
        }
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            try {
                // 1. Fetch Profile if possible
                fetchUserProfile()

                // 2. Fetch Academic Programs
                fetchAcademicPrograms()
                
                // 3. Fetch Weekly Routine
                val active = _uiState.value.activeProgram
                if (active != null) {
                    fetchWeeklyRoutine(active)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = e.localizedMessage ?: "কোর্স লোড করতে সমস্যা হয়েছে"
                )
            }
        }
    }

    private suspend fun fetchUserProfile() {
        try {
            val userId = sessionManager.getUserId() ?: ""
            val profileQuery = GraphQlQuery(
                operationName = "GetProfile",
                query = "query GetProfile(\$user_id: String, \$type: String!) { profile(user_id: \$user_id, type: \$type) { id first_name last_name avatar gender dob study_group class { code display } school { id name } user { phone email } } }",
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
                    else -> "শিক্ষার্থী"
                }

                val currentBatchId = sessionManager.getUserBatchId()
                val profileClass = profile.`class`?.code ?: sessionManager.getUserClassName() ?: "C11"
                val profileGroup = profile.study_group ?: sessionManager.getUserGroup() ?: "Humanities"

                sessionManager.saveUserProfile(
                    firstName = firstName.ifBlank { first },
                    lastName = lastName,
                    avatar = profile.avatar,
                    schoolName = profile.school?.name,
                    classDisplay = profile.`class`?.display ?: profile.`class`?.code
                )
                sessionManager.saveUserAcademicInfo(
                    batchId = currentBatchId ?: "HSC 2027",
                    className = profileClass,
                    group = profileGroup,
                    vendor = "BD"
                )

                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    userName = fullName.ifBlank { first },
                    userFirstName = first,
                    userAvatar = profile.avatar,
                    userClass = profile.`class`?.display ?: profile.`class`?.code ?: "একাদশ শ্রেণি",
                    userGroup = profileGroup,
                    userSchool = profile.school?.name ?: ""
                )
            }
        } catch (_: Exception) {
            // Profile fetch failed; fallback to cached session values
        }
    }

    private suspend fun fetchAcademicPrograms() {
        val batchId = sessionManager.getActiveProgramBatchId() ?: sessionManager.getUserBatchId()
        val className = sessionManager.getUserClassName() ?: "C11"
        val group = sessionManager.getUserGroup() ?: "Humanities"
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
                        "group" to (if (className.uppercase() in listOf("C5", "C6", "C7", "C8", "C05", "C06", "C07", "C08")) "None" else when(group.lowercase()) {
                            "humanities", "arts", "hum" -> "Humanities"
                            "science", "sci" -> "Science"
                            "business_studies", "businessstudies", "business", "commerce", "bus" -> "BusinessStudies"
                            else -> "None"
                        }),
                        "vendor" to vendor
                    )
                )
            )

            val enrolled = response.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
            val others = response.data?.listAcademicProgramByEnrollment?.other_programs ?: emptyList()

            val promotedOthers = others.map { it.toEnrolledProgram() }
            val allPrograms = (enrolled + promotedOthers).distinctBy { it.id }

            // একটিভ কোর্স নির্ধারণ (ইউজারের সেভ করা কোর্স অথবা প্রথমটি)
            val savedProgramId = sessionManager.getActiveProgramId()
            val active = allPrograms.firstOrNull { it.id == savedProgramId } ?: allPrograms.firstOrNull()

            if (active != null) {
                sessionManager.saveActiveProgram(
                    programId = active.id,
                    titleBn = active.title_bn,
                    batchId = active.enrollment_details?.batch_id,
                    classCode = active.classes?.firstOrNull()
                )
            }

            val hasActiveEnrollment = active?.enrollment_details?.is_active == true ||
                    allPrograms.any { it.enrollment_details?.is_active == true }

            val activeSavedSubjects = if (active != null) sessionManager.getSelectedSubjectCodes(active.id) else null
            val activeInitialSubjects = active?.subjects?.map {
                AcademicSubjectItem(code = it.code, color_code = it.color_code, display_bn = it.display_bn, icon = it.icon)
            } ?: emptyList()
            val initialCodes = activeInitialSubjects.mapNotNull { it.code ?: it.display_bn }.toSet()
            val initialSelected = activeSavedSubjects ?: initialCodes

            _uiState.value = _uiState.value.copy(
                enrolledPrograms = allPrograms,
                activeProgram = active,
                selectedSubjectCodes = initialSelected,
                courseSubjects = activeInitialSubjects,
                isPremium = hasActiveEnrollment,
                isLoading = false,
                isRefreshing = false,
                errorMessage = if (allPrograms.isEmpty()) "কোনো সক্রিয় কোর্স পাওয়া যায়নি" else null
            )

            if (active != null) {
                fetchWeeklyRoutine(active)
                loadCourseSubjects(active)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
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
                val phaseRes = apiService.getProgramPhases(phaseQuery)
                val phases = phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
                val finalPhases = if (phases.isNotEmpty()) phases else com.example.ui.components.defaultCoursePhases
                _uiState.value = _uiState.value.copy(programPhases = finalPhases)

                // ২. কারেন্ট / একটিভ ফেজ সিলেক্ট করা
                val currentPhase = finalPhases.firstOrNull { it.is_current == true }
                    ?: finalPhases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                    ?: finalPhases.firstOrNull()

                if (currentPhase == null || currentPhase.id.isBlank()) {
                    _uiState.value = _uiState.value.copy(weeklyRoutine = emptyList(), isRoutineLoading = false)
                    return@launch
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

                // ৪. অফিসিয়াল অ্যাপের মতো একক কুয়েরি পাঠানো (কোনো অপ্রয়োজনীয় ফলব্যাক লুপ ছাড়া)
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

                val routineResponse = apiService.getStudentLessons(routineQuery)
                val routineLessons = routineResponse.data?.studentSpecificLessons?.data ?: emptyList()

                com.example.course.LessonCacheManager.saveLessons(routineLessons, programId = programId)
                com.example.notification.ClassAlarmScheduler.schedule7DayClassAlarms(getApplication(), programId, routineLessons)

                // ৫. খালি আসলে খালিই থাকবে (যেমন Think AI তে খালি আসে), স্প্যাম কুয়েরি হবে না
                _uiState.value = _uiState.value.copy(
                    weeklyRoutine = routineLessons.sortedBy { it.start_time ?: "" },
                    isRoutineLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRoutineLoading = false)
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
                    val phaseRes = apiService.getProgramPhases(phaseQuery)
                    val fetchedPhases = phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
                    phases = if (fetchedPhases.isNotEmpty()) fetchedPhases else com.example.ui.components.defaultCoursePhases
                    _uiState.value = _uiState.value.copy(programPhases = phases)
                }

                val currentPhase = phases.firstOrNull { it.is_current == true }
                    ?: phases.firstOrNull { it.status.equals("ACTIVE", ignoreCase = true) }
                    ?: phases.firstOrNull()

                if (currentPhase == null || currentPhase.id.isBlank()) {
                    _uiState.value = _uiState.value.copy(weeklyRoutine = emptyList(), isRoutineLoading = false)
                    return@launch
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

                val routineResponse = apiService.getStudentLessons(routineQuery)
                val routineLessons = routineResponse.data?.studentSpecificLessons?.data ?: emptyList()
                com.example.course.LessonCacheManager.saveLessons(routineLessons, programId = programId)
                val completedSet = completedItemRepository?.completedIdsState?.value ?: sessionManager.getCompletedLessonIds()
                val enrichedRoutine = com.example.course.LessonCacheManager.enrichWithCompletedState(routineLessons, completedSet)
                _uiState.value = _uiState.value.copy(
                    weeklyRoutine = enrichedRoutine.sortedBy { it.start_time ?: "" },
                    isRoutineLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRoutineLoading = false)
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
