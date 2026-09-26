package com.example.syllabus

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ইংরেজি ডিজিট → বাংলা ডিজিট
 */
fun convertToBengaliDigits(input: String?): String {
    if (input == null) return ""
    val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return input.map { ch ->
        if (ch in '0'..'9') bengaliDigits[ch - '0'] else ch
    }.joinToString("")
}

data class ChangeSyllabusUiState(
    val isLoadingClasses: Boolean = false,
    val isLoadingBatches: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val classList: List<ClassItem> = emptyList(),
    val selectedClass: ClassItem? = null,
    val selectedGroup: StudyGroupItem? = null,
    val batchOptions: List<BatchOptionItem> = emptyList(),
    val selectedBatch: BatchOptionItem? = null,
    val currentClassCode: String? = null,     // user এখানে already enrolled
    val currentGroupCode: String? = null,
    val currentPassingYear: String? = null,
    val currentBatchLabel: String? = null,
    val errorMessage: String? = null,
    val showConfirmBottomSheet: Boolean = false
) {
    val isGroupRequiredForSelectedClass: Boolean
        get() = selectedClass?.isGroupRequired == true

    val isFormValid: Boolean
        get() {
            if (selectedClass == null) return false
            if (isGroupRequiredForSelectedClass && selectedGroup == null) return false
            if (batchOptions.isNotEmpty() && selectedBatch == null) return false
            return true
        }

    /** Confirmation sheet-এ user-friendly summary */
    val summaryLine: String
        get() = buildList {
            selectedClass?.let { add(it.bengaliClassName()) }
            selectedGroup?.let { add(it.displayNameBn) }
            selectedBatch?.yearString?.takeIf { it.isNotBlank() }?.let {
                add(convertToBengaliDigits(it))
            }
        }.filter { it.isNotBlank() }.joinToString(" • ")

    /** বর্তমান selection আসল selection-এর সমান কি না */
    val isSameAsCurrent: Boolean
        get() {
            if (currentClassCode.isNullOrBlank()) return false
            if (!selectedClass?.code.equals(currentClassCode, ignoreCase = true)) return false
            if (isGroupRequiredForSelectedClass && !currentGroupCode.isNullOrBlank()) {
                if (selectedGroup?.matchesCodeOrName(currentGroupCode) != true) return false
            }
            if (batchOptions.isNotEmpty() && selectedBatch != null) {
                val selectedYear = selectedBatch.yearString.trim()
                val currentYear = currentPassingYear?.trim()
                val selectedLabel = selectedBatch.label?.trim()
                val currentLabel = currentBatchLabel?.trim()

                if (selectedYear.isNotBlank() && !currentYear.isNullOrBlank()) {
                    if (!selectedYear.equals(currentYear, ignoreCase = true)) return false
                } else if (!selectedLabel.isNullOrBlank() && !currentLabel.isNullOrBlank()) {
                    if (!selectedLabel.equals(currentLabel, ignoreCase = true)) return false
                }
            }
            return true
        }
}

class ChangeSyllabusViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeSyllabusUiState(isLoadingClasses = true))
    val uiState: StateFlow<ChangeSyllabusUiState> = _uiState.asStateFlow()

    /**
     * Fallback groups — StudyGroupTypeEnum: "Science", "Humanities", "BusinessStudies"
     */
    val standardStudyGroups = listOf(
        StudyGroupItem(code = "Science", name_bn = "বিজ্ঞান", name_en = "Science"),
        StudyGroupItem(code = "Humanities", name_bn = "মানবিক", name_en = "Humanities"),
        StudyGroupItem(code = "BusinessStudies", name_bn = "ব্যবসায় শিক্ষা", name_en = "Business Studies")
    )

    init { loadClassList() }

    /**
     * Step 1: REST GET /class_list?vendor=BD&type=syllabus
     */
    fun loadClassList() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingClasses = true,
                errorMessage = null
            )
            try {
                val response = apiService.getClassList(vendor = "BD", type = "syllabus")

                // Regex: C{num} — C9V2 বাদ (শুধু real academic classes)
                // is_active == false হলে বাদ, কিন্তু null হলে allow (future-proof)
                val classes = (response.classes ?: response.data ?: emptyList())
                    .filter { item ->
                        item.code.matches(Regex("(?i)C\\d+")) &&
                                item.is_active != false &&
                                item.show_on_boarding != false
                    }
                    .sortedBy { it.serial ?: Int.MAX_VALUE }

                val storedClassCode = sessionManager.getUserClassName()
                val storedGroupRaw = sessionManager.getUserGroup()
                val storedPassingYear = sessionManager.getAcademicPassingYear()
                val storedBatchLabel = sessionManager.getUserBatchId()

                val preselectedClass = classes.find {
                    it.code.equals(storedClassCode, true)
                } ?: classes.firstOrNull()

                val availableGroups = preselectedClass?.groups
                    ?.takeIf { it.isNotEmpty() }
                    ?: standardStudyGroups

                // FIX #1: loose match — "HUM" → "Humanities"
                val preselectedGroup = if (preselectedClass?.isGroupRequired == true) {
                    availableGroups.find { it.matchesCodeOrName(storedGroupRaw) }
                        ?: availableGroups.firstOrNull()
                } else null

                _uiState.value = _uiState.value.copy(
                    isLoadingClasses = false,
                    classList = classes,
                    selectedClass = preselectedClass,
                    selectedGroup = preselectedGroup,
                    currentClassCode = storedClassCode,
                    currentGroupCode = storedGroupRaw,
                    currentPassingYear = storedPassingYear,
                    currentBatchLabel = storedBatchLabel
                )

                // FIX #2: exam year + label দিয়ে batch preselection
                preselectedClass?.code?.let { code ->
                    fetchBatchOptions(
                        classCode = code,
                        preferredLabel = storedBatchLabel,
                        preferredYear = storedPassingYear
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingClasses = false,
                    errorMessage = "ক্লাসের তালিকা লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                )
            }
        }
    }

    /**
     * User নতুন class select করলে — group + batch reload
     */
    fun selectClass(classItem: ClassItem) {
        if (_uiState.value.selectedClass?.code == classItem.code) return

        val availableGroups = classItem.groups
            ?.takeIf { it.isNotEmpty() }
            ?: standardStudyGroups
        val storedGroupRaw = sessionManager.getUserGroup()

        val defaultGroup = if (classItem.isGroupRequired) {
            availableGroups.find { it.matchesCodeOrName(storedGroupRaw) }
                ?: availableGroups.firstOrNull()
        } else null

        _uiState.value = _uiState.value.copy(
            selectedClass = classItem,
            selectedGroup = defaultGroup,
            selectedBatch = null,
            batchOptions = emptyList(),
            errorMessage = null
        )

        fetchBatchOptions(
            classCode = classItem.code,
            preferredLabel = sessionManager.getUserBatchId(),
            preferredYear = sessionManager.getAcademicPassingYear()
        )
    }

    /**
     * Step 2: BatchOptions GraphQL query
     */
    private fun fetchBatchOptions(
        classCode: String,
        preferredLabel: String? = null,
        preferredYear: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBatches = true)
            try {
                val query = GraphQlQuery(
                    operationName = "BatchOptions",
                    query = """
                        query BatchOptions(${'$'}classCode: ClassEnumCommon) {
                          batchOptions(classCode: ${'$'}classCode) {
                            classCode
                            date
                            options {
                              year
                              label
                            }
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf("classCode" to classCode)
                )

                val response = apiService.getBatchOptions(query)
                val options = response.data?.batchOptions?.options ?: emptyList()

                // pickDefaultBatch দিয়ে smart default (SyllabusUtils.kt-এ define করা)
                val selectedBatch = pickDefaultBatch(
                    options = options,
                    storedPassingYear = preferredYear,
                    storedBatchLabel = preferredLabel
                )

                _uiState.value = _uiState.value.copy(
                    isLoadingBatches = false,
                    batchOptions = options,
                    selectedBatch = selectedBatch
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingBatches = false)
            }
        }
    }

    fun selectGroup(group: StudyGroupItem) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun selectBatch(batch: BatchOptionItem) {
        _uiState.value = _uiState.value.copy(selectedBatch = batch)
    }

    fun showConfirmationDialog() {
        val state = _uiState.value
        if (!state.isFormValid) {
            _uiState.value = state.copy(errorMessage = "অনুগ্রহ করে সকল তথ্য সঠিকভাবে নির্বাচন করো")
            return
        }
        if (state.isSameAsCurrent) {
            _uiState.value = state.copy(
                errorMessage = "তুমি ইতিমধ্যে এই সিলেবাসেই আছো। ভিন্ন কিছু নির্বাচন করো।"
            )
            return
        }
        _uiState.value = state.copy(showConfirmBottomSheet = true, errorMessage = null)
    }

    fun dismissConfirmationDialog() {
        _uiState.value = _uiState.value.copy(showConfirmBottomSheet = false)
    }

    /**
     * Steps 3 & 4: ChangeSyllabus mutation + token update + UpdateExamYear +
     * save academic info + restart
     */
    fun applySyllabusChange(context: Context) {
        val state = _uiState.value
        val selectedClass = state.selectedClass ?: return
        val selectedGroup = state.selectedGroup
        val selectedBatch = state.selectedBatch

        viewModelScope.launch {
            _uiState.value = state.copy(
                isSubmitting = true,
                errorMessage = null,
                showConfirmBottomSheet = false
            )

            try {
                // ===== Study group enum resolution =====
                val candidates: List<String?> =
                    if (selectedClass.isGroupRequired && selectedGroup != null) {
                        val normalized = normalizeStudyGroup(selectedGroup.code)
                        listOfNotNull(normalized, selectedGroup.code).distinct()
                    } else listOf(null)

                var lastException: Exception? = null
                var successfulEnumVal: String? = null
                var tokenPayload: AuthTokensPayload? = null

                for (candidate in candidates) {
                    try {
                        val variables = mutableMapOf<String, Any?>("userclass" to selectedClass.code)
                        if (candidate != null) variables["study_group"] = candidate

                        val changeSyllabusQuery = GraphQlQuery(
                            operationName = "ChangeSyllabus",
                            query = """
                                mutation ChangeSyllabus(${'$'}study_group: StudyGroupTypeEnum, ${'$'}userclass: ClassEnumCommon) {
                                  changeSyllabus(study_group: ${'$'}study_group, class: ${'$'}userclass) {
                                    access_token
                                    id_token
                                    refresh_token
                                  }
                                }
                            """.trimIndent(),
                            variables = variables
                        )

                        val changeResponse = apiService.changeSyllabus(changeSyllabusQuery)
                        tokenPayload = changeResponse.data?.changeSyllabus
                        successfulEnumVal = candidate
                        lastException = null
                        break
                    } catch (e: retrofit2.HttpException) {
                        lastException = e
                        // FIX #5: শুধু 400 (bad enum) হলে next candidate try করব,
                        // অন্য HTTP error (401/500/etc) হলে সাথে সাথে fail
                        if (e.code() != 400) throw e
                    }
                    // Network exception / timeout → propagate (mask করা হবে না)
                }

                if (tokenPayload == null && lastException != null) {
                    throw lastException!!
                }

                // ===== 2) Token update =====
                tokenPayload?.let { payload ->
                    if (!payload.access_token.isNullOrBlank()) {
                        sessionManager.updateAuthTokens(
                            accessToken = payload.access_token,
                            refreshToken = payload.refresh_token,
                            idToken = payload.id_token
                        )
                    }
                }

                // ===== 3) UpdateExamYear mutation =====
                val passingYear = selectedBatch?.yearString.orEmpty()
                if (passingYear.isNotBlank()) {
                    try {
                        val updateExamQuery = GraphQlQuery(
                            operationName = "UpdateExamYear",
                            query = """
                                mutation UpdateExamYear(${'$'}passing_year: String, ${'$'}type: PrimaryUserTypeEnum!) {
                                  updateProfile(passing_year: ${'$'}passing_year, type: ${'$'}type) {
                                    passing_year
                                  }
                                }
                            """.trimIndent(),
                            variables = mapOf(
                                "passing_year" to passingYear,
                                "type" to "student"
                            )
                        )
                        apiService.updateExamYear(updateExamQuery)
                    } catch (_: Exception) {
                        // non-fatal — token already updated, exam year failed চুপচাপ ignore
                    }
                }

                // ===== 4) Local persistence =====
                val effectiveBatchId = when {
                    selectedClass.code.equals("C11", true) && passingYear.isNotBlank() -> "HSC $passingYear"
                    selectedClass.code.equals("C12", true) && passingYear.isNotBlank() -> "HSC $passingYear"
                    selectedClass.code.equals("C10", true) && passingYear.isNotBlank() -> "SSC $passingYear"
                    selectedClass.code.equals("C9", true) && passingYear.isNotBlank() -> "SSC $passingYear"
                    else -> selectedBatch?.label ?: passingYear.ifBlank { null }
                }

                sessionManager.saveUserAcademicInfo(
                    batchId = effectiveBatchId,
                    className = selectedClass.code,
                    group = successfulEnumVal ?: selectedGroup?.code,
                    vendor = selectedClass.vendor ?: "BD",
                    passingYear = passingYear.ifBlank { null }
                )

                sessionManager.saveUserProfile(
                    firstName = sessionManager.getUserFirstName(),
                    lastName = "",
                    avatar = sessionManager.getUserAvatar(),
                    schoolName = sessionManager.getUserSchoolName(),
                    // FIX #4: Settings-এর সাথে consistent বাংলা ordinal নাম
                    classDisplay = selectedClass.bengaliClassName()
                )

                sessionManager.clearActiveProgram()

                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    isSuccess = true
                )

                // ===== 5) Proper restart =====
                restartApp(context)

            } catch (e: Exception) {
                val errorDetails = if (e is retrofit2.HttpException) {
                    try {
                        val body = e.response()?.errorBody()?.string()
                        "HTTP ${e.code()}: $body"
                    } catch (_: Exception) {
                        e.localizedMessage
                    }
                } else {
                    e.localizedMessage
                }
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "সিলেবাস পরিবর্তন ব্যর্থ হয়েছে: ${errorDetails ?: "সার্ভার এরর"}"
                )
            }
        }
    }

    /**
     * Smooth in-app restart: Launches a fresh MainActivity task and finishes the current activity,
     * seamlessly reloading all ViewModels, SessionManager state, and new syllabus courses without closing the app.
     */
    private fun restartApp(context: Context) {
        try {
            android.widget.Toast.makeText(context.applicationContext, "সিলেবাস সফলভাবে পরিবর্তন করা হয়েছে! ✨", android.widget.Toast.LENGTH_SHORT).show()

            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)

            if (context is android.app.Activity) {
                context.finish()
            } else if (context is android.content.ContextWrapper && context.baseContext is android.app.Activity) {
                (context.baseContext as android.app.Activity).finish()
            }
        } catch (_: Exception) {
            try {
                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?: Intent(context, MainActivity::class.java)
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                context.startActivity(launchIntent)
            } catch (_: Exception) {}
        }
    }

    private fun normalizeStudyGroup(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "humanities", "hum", "arts" -> "Humanities"
            "science", "sci" -> "Science"
            "businessstudies", "business_studies", "business", "commerce", "bus" -> "BusinessStudies"
            "bcs" -> "BCS"
            "bank" -> "Bank"
            "all" -> "All"
            "others" -> "Others"
            "none" -> "None"
            else -> raw.trim()
        }
    }
}

class ChangeSyllabusViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChangeSyllabusViewModel::class.java)) {
            return ChangeSyllabusViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
