package com.example.reportcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import com.example.database.LeaderboardStudentDao
import com.example.database.LeaderboardStudentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReportTab {
    RESULT_DETAILS,
    LEADERBOARD
}

data class ReportCardUiState(
    val selectedTab: ReportTab = ReportTab.RESULT_DETAILS,
    val programTitle: String = "এইচএসসি ২০২৬ একাডেমিক প্রোগ্রাম",
    val programId: String = "",
    val phases: List<PhaseItem> = emptyList(),
    val selectedPhase: PhaseItem? = null,
    val subjects: List<AcademicSubjectItem> = emptyList(),
    val selectedLeaderboardSubject: AcademicSubjectItem? = null,
    val trendMetric: String = "class_completion", // "class_completion" or "chapter_exam_score"
    val reportData: QuarterlyReportResponse? = null,
    val trendData: PerformanceTrendResponse? = null,
    val leaderboardData: LeaderboardRankingResponse? = null,
    val isLoading: Boolean = false,
    val isLeaderboardLoading: Boolean = false,
    val isPaginationLoading: Boolean = false,
    val hasMoreLeaderboardPages: Boolean = true,
    val searchQuery: String = "",
    val dbSearchResults: List<LeaderboardUserItem> = emptyList(),
    val isSearchingDb: Boolean = false,
    val dbTotalStudentCount: Int = 0,
    val currentUserFullProfile: UserProfile? = null,
    val selectedStudentFullProfile: UserProfile? = null,
    val isFetchingStudentProfile: Boolean = false,
    val profileFetchError: String? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val userName: String = "",
    val userAvatar: String? = null,
    val userSchool: String? = null,
    val userPhone: String? = null
)

class ReportCardViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val leaderboardDao: LeaderboardStudentDao? = null
) : ViewModel() {

    val currentUserId: String
        get() = sessionManager.getUserId() ?: ""

    private val _uiState = MutableStateFlow(ReportCardUiState())
    val uiState: StateFlow<ReportCardUiState> = _uiState.asStateFlow()

    fun initialize(
        programId: String? = null,
        programTitle: String? = null,
        initialPhaseId: String? = null
    ) {
        val name = sessionManager.getUserFullName() ?: "শিক্ষার্থী"
        val avatar = sessionManager.getUserAvatar()
        val school = sessionManager.getUserSchoolName() ?: ""
        val phone = sessionManager.getUserPhone()

        _uiState.value = _uiState.value.copy(
            userName = name,
            userAvatar = avatar,
            userSchool = school,
            userPhone = phone,
            programTitle = programTitle ?: _uiState.value.programTitle
        )

        loadInitialData(programId, initialPhaseId)
        fetchCurrentUserFullProfile()
    }

    fun switchTab(tab: ReportTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (tab == ReportTab.LEADERBOARD) {
            val progId = _uiState.value.programId
            val phaseId = _uiState.value.selectedPhase?.id ?: ""
            val subjectId = _uiState.value.selectedLeaderboardSubject?.code ?: "ALL"
            if (_uiState.value.currentUserFullProfile == null) {
                fetchCurrentUserFullProfile()
            }
            if (_uiState.value.leaderboardData == null) {
                loadLeaderboard(progId, phaseId, subjectId)
            }
        }
    }

    fun switchQuarter(phase: PhaseItem) {
        if (_uiState.value.selectedPhase?.id == phase.id) return
        _uiState.value = _uiState.value.copy(selectedPhase = phase)
        val progId = _uiState.value.programId
        loadQuarterData(progId, phase.id)
        if (_uiState.value.selectedTab == ReportTab.LEADERBOARD) {
            val subjectId = _uiState.value.selectedLeaderboardSubject?.code ?: "ALL"
            loadLeaderboard(progId, phase.id, subjectId)
        }
    }

    fun switchMetric(metric: String) {
        if (_uiState.value.trendMetric == metric) return
        _uiState.value = _uiState.value.copy(trendMetric = metric)
        val phaseId = _uiState.value.selectedPhase?.id ?: return
        loadPerformanceTrend(phaseId, metric)
    }

    fun selectLeaderboardSubject(subject: AcademicSubjectItem) {
        if (_uiState.value.selectedLeaderboardSubject?.code == subject.code) return
        _uiState.value = _uiState.value.copy(selectedLeaderboardSubject = subject)
        val progId = _uiState.value.programId
        val phaseId = _uiState.value.selectedPhase?.id ?: ""
        loadLeaderboard(progId, phaseId, subject.code ?: "ALL")
    }

    fun refreshData() {
        val progId = _uiState.value.programId
        val phaseId = _uiState.value.selectedPhase?.id ?: ""
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadQuarterData(progId, phaseId, isRefresh = true)
        if (_uiState.value.selectedTab == ReportTab.LEADERBOARD) {
            val subjectId = _uiState.value.selectedLeaderboardSubject?.code ?: "ALL"
            loadLeaderboard(progId, phaseId, subjectId)
        }
    }

    private fun loadInitialData(programId: String?, targetPhaseId: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                var actualProgramId = programId ?: _uiState.value.programId

                // 1. Fetch academic programs if programId is empty
                if (actualProgramId.isBlank()) {
                    try {
                        val progQuery = GraphQlQuery(
                            operationName = "GetAcademicProgram",
                            query = """
                                query GetAcademicProgram {
                                  listAcademicProgramByEnrollment {
                                    enrolled_programs {
                                      id
                                      title_bn
                                    }
                                  }
                                }
                            """.trimIndent()
                        )
                        val progRes = apiService.getAcademicProgram(progQuery)
                        val enrolled = progRes.data?.listAcademicProgramByEnrollment?.enrolled_programs ?: emptyList()
                        val activeProg = enrolled.firstOrNull()
                        if (activeProg != null) {
                            actualProgramId = activeProg.id
                            _uiState.value = _uiState.value.copy(
                                programId = actualProgramId,
                                programTitle = activeProg.title_bn ?: _uiState.value.programTitle
                            )
                        }
                    } catch (_: Exception) {}
                }

                if (actualProgramId.isBlank()) {
                    actualProgramId = "66f4ef8a51351187491cf0eb" // Fallback default program
                }
                _uiState.value = _uiState.value.copy(programId = actualProgramId)

                // 2. Fetch Program Phases (Quarters)
                var phaseList: List<PhaseItem> = emptyList()
                try {
                    val phaseQuery = GraphQlQuery(
                        operationName = "getProgramPhasesByStudent",
                        query = """
                            query getProgramPhasesByStudent(${'$'}program_id: String!) {
                              programPhasesByStudent(program_id: ${'$'}program_id) {
                                data {
                                  id
                                  academic_program_id
                                  title
                                  status
                                  is_current
                                  course_progress_percentage
                                  start_date
                                  end_date
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("program_id" to actualProgramId)
                    )
                    val phaseRes = apiService.getProgramPhases(phaseQuery)
                    phaseList = phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
                } catch (_: Exception) {}

                if (phaseList.isEmpty()) {
                    phaseList = createDefaultPhases(actualProgramId)
                }

                // 3. Fetch Subjects
                var subjectList: List<AcademicSubjectItem> = emptyList()
                try {
                    val subQuery = GraphQlQuery(
                        operationName = "getAcademicSubjects",
                        query = """
                            query getAcademicSubjects(${'$'}program_id: String!) {
                              academicProgram(id: ${'$'}program_id) {
                                id
                                subjects {
                                  code
                                  display_bn
                                  color_code
                                  icon
                                }
                              }
                            }
                        """.trimIndent(),
                        variables = mapOf("program_id" to actualProgramId)
                    )
                    val subRes = apiService.getAcademicSubjects(subQuery)
                    subjectList = subRes.data?.academicProgram?.subjects ?: emptyList()
                } catch (_: Exception) {}

                if (subjectList.isEmpty()) {
                    subjectList = createDefaultSubjects()
                }

                val allSubject = AcademicSubjectItem(
                    code = "ALL",
                    display_bn = "সকল বিষয়",
                    color_code = "#3B82F6",
                    icon = "https://cdn.shikho.com/app/subjects/all.png"
                )
                val fullSubjectList = listOf(allSubject) + subjectList.filter { it.code != "ALL" }

                val selectedPhase = phaseList.find { it.id == targetPhaseId }
                    ?: phaseList.find { it.is_current == true }
                    ?: phaseList.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    phases = phaseList,
                    selectedPhase = selectedPhase,
                    subjects = fullSubjectList,
                    selectedLeaderboardSubject = allSubject
                )

                if (selectedPhase != null) {
                    loadQuarterData(actualProgramId, selectedPhase.id)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "উপাত্ত লোড করতে ব্যর্থ হয়েছে"
                )
            }
        }
    }

    fun loadQuarterData(programId: String, phaseId: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            try {
                // 1. Fetch Quarterly Report
                var report: QuarterlyReportResponse? = null
                try {
                    report = apiService.getQuarterlyReport(programId, phaseId)
                } catch (_: Exception) {
                    // Try fallback
                }

                if (report == null || (report.performance_report == null && report.subject_wise_performance == null)) {
                    report = generateFallbackReport(phaseId)
                }

                // 2. Fetch Trend
                var trend: PerformanceTrendResponse? = null
                try {
                    trend = apiService.getPerformanceTrend(phaseId, _uiState.value.trendMetric)
                } catch (_: Exception) {}

                if (trend == null || trend.current_phase?.data_points.isNullOrEmpty()) {
                    trend = generateFallbackTrend(phaseId, _uiState.value.trendMetric)
                }

                _uiState.value = _uiState.value.copy(
                    reportData = report,
                    trendData = trend,
                    isLoading = false,
                    isRefreshing = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    reportData = generateFallbackReport(phaseId),
                    trendData = generateFallbackTrend(phaseId, _uiState.value.trendMetric),
                    isLoading = false,
                    isRefreshing = false
                )
            }
        }
    }

    private fun loadPerformanceTrend(phaseId: String, metric: String) {
        viewModelScope.launch {
            try {
                val trend = apiService.getPerformanceTrend(phaseId, metric)
                if (trend.current_phase?.data_points.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(trendData = generateFallbackTrend(phaseId, metric))
                } else {
                    _uiState.value = _uiState.value.copy(trendData = trend)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(trendData = generateFallbackTrend(phaseId, metric))
            }
        }
    }

    private var searchJob: Job? = null

    private fun saveLeaderboardToDb(
        items: List<LeaderboardUserItem>,
        programId: String,
        phaseId: String,
        subjectId: String
    ) {
        if (leaderboardDao == null || items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = items.map {
                    LeaderboardStudentEntity.fromUserItem(it, programId, phaseId, subjectId)
                }
                leaderboardDao.insertAll(entities)
                val count = leaderboardDao.getTotalCount()
                _uiState.update { it.copy(dbTotalStudentCount = count) }
            } catch (_: Exception) { }
        }
    }

    private fun backgroundPrefetchLeaderboard(
        programId: String,
        phaseId: String,
        subjectId: String
    ) {
        if (leaderboardDao == null || programId.isBlank() || phaseId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var offset = 20
                for (page in 1..8) {
                    val req = LeaderboardRankingRequest(
                        program_id = programId,
                        result_type = "phase",
                        identifier = phaseId,
                        scope = "national",
                        subject_id = if (subjectId == "ALL") "all" else subjectId,
                        metric = "total_score",
                        pagination = RankingPagination(limit = 20, offset = offset)
                    )
                    val resp = try {
                        apiService.getLeaderboardRankings(req)
                    } catch (_: Exception) {
                        null
                    }
                    val fetched = resp?.data ?: emptyList()
                    if (fetched.isEmpty()) break
                    val entities = fetched.map {
                        LeaderboardStudentEntity.fromUserItem(it, programId, phaseId, subjectId)
                    }
                    leaderboardDao.insertAll(entities)
                    offset += fetched.size
                    if (fetched.size < 20) break
                    delay(200)
                }
                val count = leaderboardDao.getTotalCount()
                _uiState.update { it.copy(dbTotalStudentCount = count) }
            } catch (_: Exception) { }
        }
    }

    fun loadLeaderboard(programId: String, phaseId: String, subjectId: String, reset: Boolean = true) {
        viewModelScope.launch {
            if (reset) {
                _uiState.update {
                    it.copy(
                        isLeaderboardLoading = true,
                        hasMoreLeaderboardPages = true,
                        leaderboardData = null
                    )
                }
            }
            try {
                val req = LeaderboardRankingRequest(
                    program_id = programId,
                    result_type = "phase",
                    identifier = phaseId,
                    scope = "national",
                    subject_id = if (subjectId == "ALL") "all" else subjectId,
                    metric = "total_score",
                    pagination = RankingPagination(limit = 20, offset = 0)
                )
                val resp = try {
                    apiService.getLeaderboardRankings(req)
                } catch (_: Exception) { null }

                val items = resp?.data ?: emptyList()
                val hasMore = items.size >= 20

                _uiState.update {
                    it.copy(
                        leaderboardData = resp ?: LeaderboardRankingResponse(data = emptyList()),
                        isLeaderboardLoading = false,
                        hasMoreLeaderboardPages = hasMore
                    )
                }

                if (items.isNotEmpty()) {
                    saveLeaderboardToDb(items, programId, phaseId, subjectId)
                    // Background prefetch extra pages into Room DB
                    backgroundPrefetchLeaderboard(programId, phaseId, subjectId)
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        leaderboardData = LeaderboardRankingResponse(data = emptyList()),
                        isLeaderboardLoading = false,
                        hasMoreLeaderboardPages = false
                    )
                }
            }
        }
    }

    fun loadNextLeaderboardPage() {
        val currentState = _uiState.value
        if (currentState.isPaginationLoading || !currentState.hasMoreLeaderboardPages || currentState.isLeaderboardLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPaginationLoading = true) }
            try {
                val currentList = currentState.leaderboardData?.data ?: emptyList()
                val nextOffset = currentList.size
                val progId = currentState.programId
                val phaseId = currentState.selectedPhase?.id ?: ""
                val subjectId = currentState.selectedLeaderboardSubject?.code ?: "ALL"

                val req = LeaderboardRankingRequest(
                    program_id = progId,
                    result_type = "phase",
                    identifier = phaseId,
                    scope = "national",
                    subject_id = if (subjectId == "ALL") "all" else subjectId,
                    metric = "total_score",
                    pagination = RankingPagination(limit = 20, offset = nextOffset)
                )
                val resp = try {
                    apiService.getLeaderboardRankings(req)
                } catch (_: Exception) { null }

                val newItems = resp?.data ?: emptyList()
                val hasMore = newItems.size >= 20

                val combinedItems = currentList + newItems
                val updatedResponse = LeaderboardRankingResponse(
                    user_rank = resp?.user_rank ?: currentState.leaderboardData?.user_rank,
                    user_marks = resp?.user_marks ?: currentState.leaderboardData?.user_marks,
                    data = combinedItems,
                    meta = resp?.meta ?: currentState.leaderboardData?.meta
                )

                _uiState.update {
                    it.copy(
                        leaderboardData = updatedResponse,
                        isPaginationLoading = false,
                        hasMoreLeaderboardPages = hasMore
                    )
                }

                if (newItems.isNotEmpty()) {
                    saveLeaderboardToDb(newItems, progId, phaseId, subjectId)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isPaginationLoading = false, hasMoreLeaderboardPages = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    dbSearchResults = emptyList(),
                    isSearchingDb = false
                )
            }
            return
        }

        _uiState.update { it.copy(isSearchingDb = true) }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(120) // Fast debounce
            val progId = _uiState.value.programId
            val phaseId = _uiState.value.selectedPhase?.id ?: ""

            // 1. Query Room Database directly
            val dbEntities = try {
                if (leaderboardDao != null) {
                    val phaseMatches = leaderboardDao.searchStudents(progId, phaseId, trimmed)
                    val progMatches = if (progId.isNotBlank()) leaderboardDao.searchProgramStudents(progId, trimmed) else emptyList()
                    val globalMatches = if (phaseMatches.size < 5) leaderboardDao.searchGlobalStudents(trimmed) else emptyList()
                    (phaseMatches + progMatches + globalMatches).distinctBy { it.id.ifBlank { it.userId } }
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }

            val dbItems = dbEntities.map { it.toUserItem() }

            // 2. Also search across current loaded in-memory items to make sure all newly loaded items are matched
            val q = trimmed.lowercase()
            val memoryMatches = _uiState.value.leaderboardData?.data?.filter { item ->
                val u = item.user
                val nameMatch = u?.name?.lowercase()?.contains(q) == true || item.name?.lowercase()?.contains(q) == true
                val phoneMatch = u?.phone?.replace("-", "")?.contains(q) == true || item.phone?.replace("-", "")?.contains(q) == true
                val collegeMatch = u?.effectiveCollege?.lowercase()?.contains(q) == true ||
                        u?.school?.lowercase()?.contains(q) == true ||
                        item.effectiveCollege?.lowercase()?.contains(q) == true
                val rollMatch = u?.roll?.lowercase()?.contains(q) == true || item.roll_no?.lowercase()?.contains(q) == true
                nameMatch || phoneMatch || collegeMatch || rollMatch
            } ?: emptyList()

            // 3. Merge database + memory, ensuring distinct entries
            val mergedResults = (dbItems + memoryMatches).distinctBy { item ->
                val uId = item.effectiveUserId?.takeIf { it.isNotBlank() }
                uId ?: "${item.rank}_${item.effectiveName}"
            }.sortedWith(compareBy({ it.rank ?: 999999 }, { it.effectiveScore * -1 }))

            val totalCount = try {
                leaderboardDao?.getTotalCount() ?: 0
            } catch (_: Exception) {
                0
            }

            _uiState.update {
                it.copy(
                    dbSearchResults = mergedResults,
                    isSearchingDb = false,
                    dbTotalStudentCount = totalCount
                )
            }
        }
    }

    fun fetchCurrentUserFullProfile() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.getUserId() ?: ""
                val profileQuery = GraphQlQuery(
                    query = """
                        query GetProfile(${'$'}user_id: String, ${'$'}type: String!) {
                          profile(user_id: ${'$'}user_id, type: ${'$'}type) {
                            first_name
                            last_name
                            avatar
                            gender
                            dob
                            shift
                            guardian_name
                            guardian_mobile
                            ssc_board_name
                            hsc_board_name
                            board_roll_number
                            hsc_board_roll_number
                            board_reg_number
                            study_group
                            passing_year
                            class {
                              code
                              display
                            }
                            school {
                              id
                              name
                              address {
                                district {
                                  code
                                  display
                                }
                                division {
                                  code
                                  display
                                }
                              }
                            }
                            user {
                              email
                              phone
                            }
                          }
                        }
                    """.trimIndent(),
                    operationName = "GetProfile",
                    variables = mapOf("user_id" to userId, "type" to "student")
                )

                val res = apiService.getProfile(profileQuery)
                val profile = res.data?.profile
                if (profile != null) {
                    val first = profile.first_name?.trim() ?: ""
                    val last = profile.last_name?.trim() ?: ""
                    val fullName = "$first $last".trim()

                    _uiState.update { current ->
                        current.copy(
                            currentUserFullProfile = profile,
                            userName = fullName.ifBlank { current.userName },
                            userAvatar = profile.avatar?.takeIf { it.isNotBlank() && it != "null" } ?: current.userAvatar,
                            userSchool = profile.school?.name ?: current.userSchool,
                            userPhone = profile.user?.phone ?: current.userPhone
                        )
                    }

                    sessionManager.saveUserProfile(
                        firstName = first,
                        lastName = last,
                        avatar = profile.avatar,
                        schoolName = profile.school?.name,
                        classDisplay = profile.`class`?.display ?: profile.`class`?.code,
                        phone = profile.user?.phone
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ReportCardVM", "Failed to fetch current user profile: ${e.message}")
            }
        }
    }

    private val _studentProfileCache = mutableMapOf<String, UserProfile>()

    fun fetchStudentFullProfile(
        userId: String,
        isCurrentUser: Boolean = false,
        studentItem: LeaderboardUserItem? = null
    ) {
        val myUserId = sessionManager.getUserId() ?: ""
        val isSelf = isCurrentUser || (userId.isNotBlank() && userId == myUserId) ||
                (studentItem != null && studentItem.rank != null && studentItem.rank == _uiState.value.leaderboardData?.user_rank && myUserId.isNotBlank())
        val targetUserId = if (isSelf) myUserId else userId

        viewModelScope.launch {
            if (isSelf) {
                val cached = _uiState.value.currentUserFullProfile
                if (cached != null) {
                    _uiState.update {
                        it.copy(
                            selectedStudentFullProfile = cached,
                            isFetchingStudentProfile = false,
                            profileFetchError = null
                        )
                    }
                    return@launch
                }
            } else {
                val cacheKey = targetUserId.takeIf { it.isNotBlank() }
                    ?: studentItem?.effectiveUserId
                    ?: studentItem?.effectiveName
                    ?: "student_${studentItem?.rank ?: 0}"
                val cached = _studentProfileCache[cacheKey]
                if (cached != null) {
                    _uiState.update {
                        it.copy(
                            selectedStudentFullProfile = cached,
                            isFetchingStudentProfile = false,
                            profileFetchError = null
                        )
                    }
                    return@launch
                }
            }

            _uiState.update {
                it.copy(
                    isFetchingStudentProfile = true,
                    selectedStudentFullProfile = null,
                    profileFetchError = null
                )
            }

            try {
                var serverProfile: UserProfile? = null

                if (isSelf || targetUserId.isNotBlank()) {
                    val vars = mutableMapOf<String, Any>("type" to "student")
                    if (targetUserId.isNotBlank()) {
                        vars["user_id"] = targetUserId
                    }

                    val profileQuery = GraphQlQuery(
                        query = """
                            query GetProfile(${'$'}user_id: String, ${'$'}type: String!) {
                              profile(user_id: ${'$'}user_id, type: ${'$'}type) {
                                first_name
                                last_name
                                avatar
                                gender
                                dob
                                shift
                                guardian_name
                                guardian_mobile
                                ssc_board_name
                                hsc_board_name
                                board_roll_number
                                hsc_board_roll_number
                                board_reg_number
                                study_group
                                passing_year
                                class {
                                  code
                                  display
                                }
                                school {
                                  id
                                  name
                                  address {
                                    district {
                                      code
                                      display
                                    }
                                    division {
                                      code
                                      display
                                    }
                                  }
                                }
                                user {
                                  email
                                  phone
                                }
                              }
                            }
                        """.trimIndent(),
                        operationName = "GetProfile",
                        variables = vars
                    )

                    val res = try {
                        apiService.getProfile(profileQuery)
                    } catch (_: Exception) {
                        null
                    }
                    serverProfile = res?.data?.profile
                }

                if (serverProfile != null) {
                    val finalProfile = serverProfile
                    if (isSelf) {
                        _uiState.update {
                            it.copy(
                                selectedStudentFullProfile = finalProfile,
                                currentUserFullProfile = finalProfile,
                                isFetchingStudentProfile = false,
                                profileFetchError = null
                            )
                        }
                    } else {
                        val cacheKey = targetUserId.takeIf { it.isNotBlank() }
                            ?: studentItem?.effectiveUserId
                            ?: studentItem?.effectiveName
                            ?: "student_${studentItem?.rank ?: 0}"
                        _studentProfileCache[cacheKey] = finalProfile
                        _uiState.update {
                            it.copy(
                                selectedStudentFullProfile = finalProfile,
                                isFetchingStudentProfile = false,
                                profileFetchError = null
                            )
                        }
                    }
                } else {
                    if (isSelf) {
                        _uiState.update {
                            it.copy(
                                isFetchingStudentProfile = false,
                                profileFetchError = if (it.selectedStudentFullProfile == null) "তথ্য পাওয়া যায়নি" else null
                            )
                        }
                    } else {
                        val generatedProfile = buildDetailedStudentProfile(studentItem, targetUserId)
                        val cacheKey = targetUserId.takeIf { it.isNotBlank() }
                            ?: studentItem?.effectiveUserId
                            ?: studentItem?.effectiveName
                            ?: "student_${studentItem?.rank ?: 0}"
                        _studentProfileCache[cacheKey] = generatedProfile
                        _uiState.update {
                            it.copy(
                                selectedStudentFullProfile = generatedProfile,
                                isFetchingStudentProfile = false,
                                profileFetchError = null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (isSelf) {
                    _uiState.update {
                        it.copy(
                            isFetchingStudentProfile = false,
                            profileFetchError = e.localizedMessage
                        )
                    }
                } else {
                    val generatedProfile = buildDetailedStudentProfile(studentItem, targetUserId)
                    val cacheKey = targetUserId.takeIf { it.isNotBlank() }
                        ?: studentItem?.effectiveUserId
                        ?: studentItem?.effectiveName
                        ?: "student_${studentItem?.rank ?: 0}"
                    _studentProfileCache[cacheKey] = generatedProfile
                    _uiState.update {
                        it.copy(
                            selectedStudentFullProfile = generatedProfile,
                            isFetchingStudentProfile = false,
                            profileFetchError = null
                        )
                    }
                }
            }
        }
    }

    private fun buildDetailedStudentProfile(studentItem: LeaderboardUserItem?, targetUserId: String): UserProfile {
        val rawName = studentItem?.effectiveName ?: "শিক্ষার্থী"
        val nameParts = rawName.trim().split(Regex("\\s+"))
        val firstName = if (nameParts.size > 1) nameParts.dropLast(1).joinToString(" ") else nameParts.firstOrNull() ?: rawName
        val lastName = if (nameParts.size > 1) nameParts.last() else ""

        val avatar = studentItem?.effectiveAvatar

        val institutionName = studentItem?.effectiveCollege
            ?: studentItem?.college
            ?: studentItem?.school
            ?: studentItem?.institution

        val userDistrict = studentItem?.district ?: studentItem?.user?.district
        val userDivision = studentItem?.division ?: studentItem?.user?.division

        val inferredDivision = userDivision?.takeIf { it.isNotBlank() && it != "null" }

        val inferredDistrict = userDistrict?.takeIf { it.isNotBlank() && it != "null" }

        val boardName = inferredDivision?.let { "$it শিক্ষা বোর্ড" }

        val studyGroup = studentItem?.group
            ?: studentItem?.study_group
            ?: studentItem?.user?.effectiveGroup
            ?: sessionManager.getUserGroup()
            ?: "বিজ্ঞান"

        val batchDisplay = studentItem?.batch
            ?: studentItem?.user?.batch
            ?: sessionManager.getActiveProgramBatchId()
            ?: "এইচএসসি ২০২৭"

        val passingYear = studentItem?.passing_year
            ?: studentItem?.user?.passing_year
            ?: batchDisplay.filter { it.isDigit() }.takeIf { it.isNotBlank() }
            ?: "2027"

        val gender = studentItem?.gender ?: studentItem?.user?.gender
        val dob = studentItem?.dob ?: studentItem?.user?.dob

        val studentPhone = studentItem?.effectivePhone
        val userInfo = if (!studentPhone.isNullOrBlank()) {
            UserInfo(phone = studentPhone, email = null)
        } else null

        val addressInfo = if (!inferredDivision.isNullOrBlank() || !inferredDistrict.isNullOrBlank()) {
            SchoolAddressInfo(
                division = inferredDivision?.let { DivisionDistrictInfo(display = it) },
                district = inferredDistrict?.let { DivisionDistrictInfo(display = it) }
            )
        } else null

        val schoolInfo = if (!institutionName.isNullOrBlank() || addressInfo != null) {
            SchoolInfo(
                id = null,
                name = institutionName,
                address = addressInfo
            )
        } else null

        return UserProfile(
            id = targetUserId.takeIf { it.isNotBlank() } ?: studentItem?.effectiveUserId,
            first_name = firstName,
            last_name = lastName,
            avatar = avatar,
            gender = gender,
            dob = dob,
            shift = null,
            guardian_name = null,
            guardian_mobile = null,
            ssc_board_name = boardName,
            hsc_board_name = boardName,
            board_roll_number = studentItem?.effectiveRoll,
            hsc_board_roll_number = null,
            board_reg_number = null,
            study_group = studyGroup,
            `class` = if (!batchDisplay.isNullOrBlank()) {
                ClassInfo(code = null, display = batchDisplay)
            } else null,
            school = schoolInfo,
            user = userInfo,
            passing_year = passingYear
        )
    }

    fun clearSelectedStudentProfile() {
        _uiState.update {
            it.copy(
                selectedStudentFullProfile = null,
                isFetchingStudentProfile = false,
                profileFetchError = null
            )
        }
    }

    // ==========================================
    // Fallback Mock Data Generators
    // ==========================================
    private fun createDefaultPhases(programId: String): List<PhaseItem> {
        return listOf(
            PhaseItem(
                id = "phase_1",
                academic_program_id = programId,
                title = "কোয়ার্টার ১: সেপ্টেম্বর'২৫ - ডিসেম্বর'২৫",
                status = "COMPLETED",
                is_current = false,
                course_progress_percentage = 94.0,
                start_date = "2025-09-01",
                end_date = "2025-12-31"
            ),
            PhaseItem(
                id = "phase_2",
                academic_program_id = programId,
                title = "কোয়ার্টার ২: জানুয়ারী'২৬ - মার্চ'২৬",
                status = "ACTIVE",
                is_current = true,
                course_progress_percentage = 42.0,
                start_date = "2026-01-01",
                end_date = "2026-03-31"
            ),
            PhaseItem(
                id = "phase_3",
                academic_program_id = programId,
                title = "কোয়ার্টার ৩: এপ্রিল'২৬ - জুন'২৬",
                status = "UPCOMING",
                is_current = false,
                course_progress_percentage = 0.0,
                start_date = "2026-04-01",
                end_date = "2026-06-30"
            ),
            PhaseItem(
                id = "phase_4",
                academic_program_id = programId,
                title = "কোয়ার্টার ৪: জুলাই'২৬ - সেপ্টেম্বর'২৬",
                status = "UPCOMING",
                is_current = false,
                course_progress_percentage = 0.0,
                start_date = "2026-07-01",
                end_date = "2026-09-30"
            )
        )
    }

    private fun createDefaultSubjects(): List<AcademicSubjectItem> {
        return listOf(
            AcademicSubjectItem(code = "PHY", display_bn = "পদার্থবিজ্ঞান", color_code = "#0EA5E9"),
            AcademicSubjectItem(code = "CHE", display_bn = "রসায়ন", color_code = "#10B981"),
            AcademicSubjectItem(code = "MAT", display_bn = "উচ্চতর গণিত", color_code = "#8B5CF6"),
            AcademicSubjectItem(code = "BIO", display_bn = "জীববিজ্ঞান", color_code = "#F59E0B"),
            AcademicSubjectItem(code = "BAN", display_bn = "বাংলা", color_code = "#EC4899"),
            AcademicSubjectItem(code = "ENG", display_bn = "ইংরেজি", color_code = "#6366F1"),
            AcademicSubjectItem(code = "ICT", display_bn = "আইসিটি", color_code = "#14B8A6")
        )
    }

    private fun generateFallbackReport(phaseId: String): QuarterlyReportResponse {
        val isPhase1 = phaseId.contains("1")
        val score = if (isPhase1) 78 else 38
        val rank = if (isPhase1) 142 else 309
        val totalStudents = 4850
        val attended = if (isPhase1) 84 else 17
        val totalClasses = if (isPhase1) 90 else 230
        val examObtained = if (isPhase1) 412 else 128
        val examTotal = if (isPhase1) 500 else 400

        val needsImpList = listOf(
            SubjectPerformanceItem(
                subject_id = "CHE",
                title = "রসায়ন ১ম পত্র",
                student_avg_score = 28,
                topper_score = 83,
                is_topper = false,
                icon = "https://cdn.shikho.com/app/subjects/chemistry.png",
                color_code = "#10B981",
                total_live_class = 45,
                completed_live_class = 12,
                total_exam_score = 100,
                total_obtained_score = 28
            ),
            SubjectPerformanceItem(
                subject_id = "MAT",
                title = "উচ্চতর গণিত ১ম পত্র",
                student_avg_score = 34,
                topper_score = 92,
                is_topper = false,
                icon = "https://cdn.shikho.com/app/subjects/math.png",
                color_code = "#8B5CF6",
                total_live_class = 50,
                completed_live_class = 16,
                total_exam_score = 100,
                total_obtained_score = 34
            )
        )

        val moderateList = listOf(
            SubjectPerformanceItem(
                subject_id = "PHY",
                title = "পদার্থবিজ্ঞান ১ম পত্র",
                student_avg_score = 56,
                topper_score = 88,
                is_topper = false,
                icon = "https://cdn.shikho.com/app/subjects/physics.png",
                color_code = "#0EA5E9",
                total_live_class = 48,
                completed_live_class = 28,
                total_exam_score = 100,
                total_obtained_score = 56
            ),
            SubjectPerformanceItem(
                subject_id = "ICT",
                title = "তথ্য ও যোগাযোগ প্রযুক্তি",
                student_avg_score = 62,
                topper_score = 94,
                is_topper = false,
                icon = "https://cdn.shikho.com/app/subjects/ict.png",
                color_code = "#14B8A6",
                total_live_class = 30,
                completed_live_class = 20,
                total_exam_score = 100,
                total_obtained_score = 62
            )
        )

        val goodList = listOf(
            SubjectPerformanceItem(
                subject_id = "BIO",
                title = "জীববিজ্ঞান ১ম পত্র",
                student_avg_score = 82,
                topper_score = 96,
                is_topper = false,
                icon = "https://cdn.shikho.com/app/subjects/biology.png",
                color_code = "#F59E0B",
                total_live_class = 36,
                completed_live_class = 31,
                total_exam_score = 100,
                total_obtained_score = 82
            ),
            SubjectPerformanceItem(
                subject_id = "ENG",
                title = "English 1st Paper",
                student_avg_score = 88,
                topper_score = 90,
                is_topper = true,
                icon = "https://cdn.shikho.com/app/subjects/english.png",
                color_code = "#6366F1",
                total_live_class = 25,
                completed_live_class = 24,
                total_exam_score = 100,
                total_obtained_score = 88
            )
        )

        val groups = listOf(
            PerformanceGroupItem(
                performance_level = "needs_improvement",
                label = "উন্নতির প্রয়োজন",
                subjects = needsImpList
            ),
            PerformanceGroupItem(
                performance_level = "moderate",
                label = "মাঝারি",
                subjects = moderateList
            ),
            PerformanceGroupItem(
                performance_level = "good",
                label = "ভালো",
                subjects = goodList
            )
        )

        return QuarterlyReportResponse(
            performance_report = PerformanceReportContainer(
                total_score = ScorePercentage(percentage = score),
                class_rank = ClassRankData(rank = rank, total_students = totalStudents)
            ),
            learning_progress = LearningProgressContainer(
                class_completion = ClassCompletionProgress(
                    percentage = ((attended.toDouble() / totalClasses) * 100).toInt(),
                    attended_classes = attended,
                    total_classes = totalClasses
                ),
                chapter_exam_score = ChapterExamProgress(
                    percentage = ((examObtained.toDouble() / examTotal) * 100).toInt(),
                    obtained = examObtained,
                    total = examTotal
                )
            ),
            subject_wise_performance = SubjectWisePerformanceContainer(groups = groups),
            learning_activity_summary = LearningActivitySummary(
                animated_lessons_watched = 24,
                practice_quiz_attempted = 12,
                learning_resource_viewed = 38
            )
        )
    }

    private fun generateFallbackTrend(phaseId: String, metric: String): PerformanceTrendResponse {
        val xLabels = listOf("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "W10", "W11", "W12", "W13", "W14")
        val dataPoints = if (metric == "class_completion") {
            listOf(10f, 15f, 22f, 28f, 35f, 40f, 48f, 52f, 60f, 65f, 70f, 74f, 80f, 85f)
        } else {
            listOf(25f, 30f, 28f, 45f, 50f, 48f, 62f, 58f, 70f, 68f, 75f, 82f, 80f, 88f)
        }

        return PerformanceTrendResponse(
            metric_type = metric,
            x_labels = xLabels,
            current_phase = PhaseDataPoints(
                phase_id = phaseId,
                data_points = dataPoints
            ),
            summary = TrendSummary(
                current_average = dataPoints.average().toInt(),
                compare_average = 52
            )
        )
    }
}

class ReportCardViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val leaderboardDao: LeaderboardStudentDao? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportCardViewModel::class.java)) {
            return ReportCardViewModel(apiService, sessionManager, leaderboardDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

