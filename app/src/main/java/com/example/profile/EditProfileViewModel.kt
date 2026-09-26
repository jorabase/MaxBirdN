package com.example.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.auth.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val nameChangeWarning: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),

    // Account
    val userId: String = "",
    val phone: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val userClassDisplay: String = "",
    val userClassCode: String = "",
    val userGroup: String = "",
    val passingYear: String = "",

    // Editable fields
    val firstName: String = "",
    val gender: String = "Male",
    val dobIso: String = "",
    val dobDisplay: String = "",
    val guardianName: String = "",
    val guardianMobile: String = "",

    val sscBoardName: String = "",
    val sscRollNumber: String = "",
    val boardRegNumber: String = "",
    val hscBoardName: String = "",
    val hscRollNumber: String = "",
    val shift: String = "NA",
    val otherTutoringSources: List<String> = listOf("NA"),

    // Original snapshots (change detection)
    val originalFirstName: String = "",
    val originalAvatarUrl: String? = null,
    val originalSchoolId: String = "",
    val originalGender: String = "",
    val originalDobIso: String = "",
    val originalGuardianName: String = "",
    val originalGuardianMobile: String = "",
    val originalSscBoard: String = "",
    val originalSscRoll: String = "",
    val originalRegNo: String = "",
    val originalHscBoard: String = "",
    val originalHscRoll: String = "",
    val originalShift: String = "",

    // Institution
    val selectedDivisionCode: String = "",
    val selectedDivisionName: String = "",
    val selectedDistrictCode: String = "",
    val selectedDistrictName: String = "",
    val selectedSchoolId: String = "",
    val selectedSchoolName: String = "",

    val divisions: List<AddressItem> = emptyList(),
    val districts: List<AddressItem> = emptyList(),
    val isDistrictsLoading: Boolean = false,

    // School search
    val schoolSearchResults: List<SchoolItem> = emptyList(),
    val isSchoolSearching: Boolean = false,
    val showSchoolSearchDialog: Boolean = false
) {
    val isNameChanged: Boolean
        get() = firstName.trim() != originalFirstName.trim() && firstName.trim().isNotBlank()

    val isSchoolChanged: Boolean
        get() = selectedSchoolId.isNotBlank() && selectedSchoolId != originalSchoolId

    val hasUnsavedChanges: Boolean
        get() = isNameChanged ||
                isSchoolChanged ||
                (avatarUrl ?: "") != (originalAvatarUrl ?: "") ||
                gender != originalGender ||
                dobIso != originalDobIso ||
                guardianName.trim() != originalGuardianName.trim() ||
                guardianMobile.trim() != originalGuardianMobile.trim() ||
                sscBoardName != originalSscBoard ||
                sscRollNumber.trim() != originalSscRoll.trim() ||
                boardRegNumber.trim() != originalRegNo.trim() ||
                hscBoardName != originalHscBoard ||
                hscRollNumber.trim() != originalHscRoll.trim() ||
                shift != originalShift

    /** C11/C12/HSC/ADMISSION হলে HSC section দেখাব */
    val isHscRelevant: Boolean
        get() = userClassCode.trim().uppercase() in listOf("C11", "C12", "HSC", "ADMISSION")
}

class EditProfileViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var schoolSearchJob: Job? = null

    val educationBoards = listOf(
        "Dhaka", "Chattogram", "Rajshahi", "Cumilla",
        "Jessore", "Barishal", "Sylhet", "Dinajpur",
        "Mymensingh", "Madrasah", "Technical", "Other"
    )

    init { loadInitialData() }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, fieldErrors = emptyMap()) }
            try {
                val userId = sessionManager.getUserId() ?: ""

                val profileQuery = GraphQlQuery(
                    query = """
                        query GetProfile(${'$'}user_id: String, ${'$'}type: String!) {
                          profile(user_id: ${'$'}user_id, type: ${'$'}type) {
                            first_name last_name avatar gender dob shift
                            guardian_name guardian_mobile
                            ssc_board_name hsc_board_name
                            board_roll_number hsc_board_roll_number board_reg_number
                            study_group passing_year other_tutoring_source
                            class { code display }
                            school {
                              id name
                              address {
                                district { code display }
                                division { code display }
                              }
                            }
                            user { email phone }
                          }
                        }
                    """.trimIndent(),
                    operationName = "GetProfile",
                    variables = mapOf("user_id" to userId, "type" to "student")
                )

                val profileRes = apiService.getProfile(profileQuery)
                val profile = profileRes.data?.profile

                val divisionsRes = try {
                    apiService.getAddress(countryCode = "BD")
                } catch (_: Exception) { null }
                val divisionsList = divisionsRes?.body ?: emptyList()

                val rawDob = profile?.dob ?: ""
                val formattedDob = formatIsoDateToDisplay(rawDob)

                val genderVal = when (profile?.gender?.trim()?.uppercase()) {
                    "FEMALE", "F", "FEMALE_", "2" -> "Female"
                    else -> "Male"
                }

                val school = profile?.school
                val schoolAddress = school?.address
                val initDivCode = schoolAddress?.division?.code ?: ""
                val initDivName = schoolAddress?.division?.display ?: ""
                val initDistCode = schoolAddress?.district?.code ?: ""
                val initDistName = schoolAddress?.district?.display ?: ""
                val initSchoolId = school?.id ?: ""
                val initSchoolName = school?.name ?: ""

                val firstName = profile?.first_name ?: sessionManager.getUserFirstName() ?: ""
                val sscBoard = profile?.ssc_board_name?.ifBlank { "" } ?: ""
                val hscBoard = profile?.hsc_board_name?.ifBlank { "" } ?: ""
                val sscRoll = profile?.board_roll_number ?: ""
                val hscRoll = profile?.hsc_board_roll_number ?: ""
                val regNo = profile?.board_reg_number ?: ""
                val gName = profile?.guardian_name ?: ""
                val gMobile = profile?.guardian_mobile ?: ""
                val shiftVal = profile?.shift?.ifBlank { "NA" } ?: "NA"
                val otherSources = profile?.other_tutoring_source?.takeIf { it.isNotEmpty() } ?: listOf("NA")

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        userId = userId,
                        phone = profile?.user?.phone ?: "",
                        email = profile?.user?.email ?: "",
                        avatarUrl = profile?.avatar ?: sessionManager.getUserAvatar(),
                        userClassDisplay = profile?.`class`?.display
                            ?: sessionManager.getUserClassDisplay() ?: "",
                        userClassCode = profile?.`class`?.code
                            ?: sessionManager.getUserClassName() ?: "C11",
                        userGroup = profile?.study_group
                            ?: sessionManager.getUserGroup() ?: "",
                        passingYear = profile?.passing_year ?: "",

                        firstName = firstName,
                        originalFirstName = firstName,
                        gender = genderVal,
                        originalGender = genderVal,
                        dobIso = rawDob,
                        originalDobIso = rawDob,
                        dobDisplay = formattedDob,
                        guardianName = gName,
                        originalGuardianName = gName,
                        guardianMobile = gMobile,
                        originalGuardianMobile = gMobile,

                        sscBoardName = sscBoard,
                        originalSscBoard = sscBoard,
                        sscRollNumber = sscRoll,
                        originalSscRoll = sscRoll,
                        boardRegNumber = regNo,
                        originalRegNo = regNo,
                        hscBoardName = hscBoard,
                        originalHscBoard = hscBoard,
                        hscRollNumber = hscRoll,
                        originalHscRoll = hscRoll,
                        shift = shiftVal,
                        originalShift = shiftVal,
                        otherTutoringSources = otherSources,

                        selectedDivisionCode = initDivCode,
                        selectedDivisionName = initDivName,
                        selectedDistrictCode = initDistCode,
                        selectedDistrictName = initDistName,
                        selectedSchoolId = initSchoolId,
                        originalSchoolId = initSchoolId,
                        selectedSchoolName = initSchoolName,

                        originalAvatarUrl = profile?.avatar ?: sessionManager.getUserAvatar(),

                        divisions = divisionsList
                    )
                }

                if (initDivCode.isNotBlank()) loadDistricts(initDivCode)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "প্রোফাইল তথ্য লোড করা যায়নি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    )
                }
            }
        }
    }

    // ===== Field setters =====
    fun onAvatarSelected(url: String?) = _uiState.update { it.copy(avatarUrl = url) }
    fun onAvatarRemoved() = _uiState.update { it.copy(avatarUrl = null) }
    fun onFirstNameChange(name: String) = _uiState.update { it.copy(firstName = name) }
    fun onGenderChange(g: String) = _uiState.update { it.copy(gender = g) }
    fun onGuardianNameChange(n: String) = _uiState.update { it.copy(guardianName = n) }
    fun onGuardianMobileChange(m: String) = _uiState.update { it.copy(guardianMobile = m) }
    fun onSscBoardChange(b: String) = _uiState.update { it.copy(sscBoardName = b) }
    fun onSscRollChange(r: String) = _uiState.update { it.copy(sscRollNumber = r) }
    fun onBoardRegChange(r: String) = _uiState.update { it.copy(boardRegNumber = r) }
    fun onHscBoardChange(b: String) = _uiState.update { it.copy(hscBoardName = b) }
    fun onHscRollChange(r: String) = _uiState.update { it.copy(hscRollNumber = r) }
    fun onShiftChange(s: String) = _uiState.update { it.copy(shift = s) }

    fun onDobSelected(year: Int, monthZeroIndexed: Int, dayOfMonth: Int) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroIndexed)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dispFmt = SimpleDateFormat("dd MMMM yyyy", Locale.US)
        _uiState.update {
            it.copy(dobIso = isoFmt.format(cal.time), dobDisplay = dispFmt.format(cal.time))
        }
    }

    fun onSelectDivision(item: AddressItem) {
        val code = item.code ?: ""
        val name = item.display ?: ""
        _uiState.update {
            it.copy(
                selectedDivisionCode = code,
                selectedDivisionName = name,
                selectedDistrictCode = "",
                selectedDistrictName = "",
                selectedSchoolId = "",
                selectedSchoolName = "",
                districts = emptyList()
            )
        }
        if (code.isNotBlank()) loadDistricts(code)
    }

    fun onSelectDistrict(item: AddressItem) {
        _uiState.update {
            it.copy(
                selectedDistrictCode = item.code ?: "",
                selectedDistrictName = item.display ?: "",
                selectedSchoolId = "",
                selectedSchoolName = ""
            )
        }
    }

    private fun loadDistricts(divisionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDistrictsLoading = true) }
            try {
                val res = apiService.getAddress(countryCode = "", divisionId = divisionId)
                _uiState.update {
                    it.copy(districts = res.body ?: emptyList(), isDistrictsLoading = false)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isDistrictsLoading = false) }
            }
        }
    }

    fun setSchoolSearchDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSchoolSearchDialog = visible) }
        if (visible) searchSchools("")
    }

    fun searchSchools(query: String) {
        schoolSearchJob?.cancel()
        schoolSearchJob = viewModelScope.launch {
            delay(200)
            _uiState.update { it.copy(isSchoolSearching = true) }
            try {
                val st = _uiState.value
                val q = GraphQlQuery(
                    query = """
                        query GetSchools(${'$'}district: String, ${'$'}division: String, ${'$'}limit: Int, ${'$'}offset: Int, ${'$'}name: String) {
                          searchSchoolV1(district: ${'$'}district, division: ${'$'}division, limit: ${'$'}limit, offset: ${'$'}offset, name: ${'$'}name) {
                            data { id name }
                          }
                        }
                    """.trimIndent(),
                    operationName = "GetSchools",
                    variables = mapOf(
                        "district" to st.selectedDistrictCode.ifBlank { null },
                        "division" to st.selectedDivisionCode.ifBlank { null },
                        "limit" to 100, "offset" to 0,
                        "name" to query.ifBlank { null }
                    )
                )
                val res = apiService.getSchools(q)
                _uiState.update {
                    it.copy(
                        schoolSearchResults = res.data?.searchSchoolV1?.data ?: emptyList(),
                        isSchoolSearching = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSchoolSearching = false) }
            }
        }
    }

    fun onSelectSchool(school: SchoolItem) {
        _uiState.update {
            it.copy(
                selectedSchoolId = school.id ?: "",
                selectedSchoolName = school.name ?: "",
                showSchoolSearchDialog = false
            )
        }
    }

    fun clearNameChangeWarning() {
        _uiState.update { it.copy(nameChangeWarning = null) }
    }

    // ===== Save =====
    fun saveProfile(onSuccess: () -> Unit) {
        val state = _uiState.value

        // 1) Client-side validation
        val validationErrors = validate(state)
        if (validationErrors.isNotEmpty()) {
            _uiState.update {
                it.copy(fieldErrors = validationErrors, errorMessage = "কিছু তথ্য সঠিক নয়, চেক করুন")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    successMessage = null,
                    nameChangeWarning = null,
                    fieldErrors = emptyMap()
                )
            }

            try {
                var nameChangeFailed = false
                var nameErrorMsg: String? = null

                // ===== Step 1: Name change (only if changed) =====
                if (state.isNameChanged) {
                    try {
                        val nameMutation = GraphQlQuery(
                            query = """
                                mutation UpdateProfile(${'$'}first_name: String, ${'$'}type: PrimaryUserTypeEnum!) {
                                  updateProfile(first_name: ${'$'}first_name, type: ${'$'}type) {
                                    first_name
                                  }
                                }
                            """.trimIndent(),
                            operationName = "UpdateProfile",
                            variables = mapOf(
                                "first_name" to state.firstName.trim(),
                                "type" to "student"
                            )
                        )
                        val nameRes = apiService.updateProfile(nameMutation)
                        if (nameRes.errors?.isNotEmpty() == true) {
                            nameChangeFailed = true
                            nameErrorMsg = nameRes.errors.first().message
                        }
                    } catch (e: retrofit2.HttpException) {
                        nameChangeFailed = true
                        nameErrorMsg = extractServerMessage(e)
                    }
                }

                // ===== Step 2: All other fields =====
                val otherMutation = GraphQlQuery(
                    query = """
                        mutation UpdateProfileWithoutUseName(
                          ${'$'}avatar: String,
                          ${'$'}dob: String,
                          ${'$'}gender: GenderTypeEnum,
                          ${'$'}shift: ShiftEnum,
                          ${'$'}ssc_board_name: String,
                          ${'$'}hsc_board_name: String,
                          ${'$'}board_roll_number: String,
                          ${'$'}hsc_board_roll_number: String,
                          ${'$'}board_reg_number: String,
                          ${'$'}other_tutoring_source: [OtherTutoringSourceEnum],
                          ${'$'}guardian_name: String,
                          ${'$'}guardian_mobile: String
                        ) {
                          updateProfile(
                            type: student,
                            avatar: ${'$'}avatar,
                            dob: ${'$'}dob,
                            gender: ${'$'}gender,
                            shift: ${'$'}shift,
                            ssc_board_name: ${'$'}ssc_board_name,
                            hsc_board_name: ${'$'}hsc_board_name,
                            board_roll_number: ${'$'}board_roll_number,
                            hsc_board_roll_number: ${'$'}hsc_board_roll_number,
                            board_reg_number: ${'$'}board_reg_number,
                            other_tutoring_source: ${'$'}other_tutoring_source,
                            guardian_name: ${'$'}guardian_name,
                            guardian_mobile: ${'$'}guardian_mobile
                          ) {
                            id first_name avatar dob gender
                            guardian_name guardian_mobile
                            ssc_board_name hsc_board_name
                            board_roll_number hsc_board_roll_number board_reg_number
                            shift
                            school { id name }
                          }
                        }
                    """.trimIndent(),
                    operationName = "UpdateProfileWithoutUseName",
                    variables = mapOf(
                        "avatar" to state.avatarUrl?.ifBlank { null },
                        "dob" to state.dobIso.ifBlank { null },
                        "gender" to state.gender,
                        "shift" to state.shift.ifBlank { "NA" },
                        "ssc_board_name" to state.sscBoardName.ifBlank { null },
                        "hsc_board_name" to state.hscBoardName.ifBlank { null },
                        "board_roll_number" to state.sscRollNumber.ifBlank { null },
                        "hsc_board_roll_number" to state.hscRollNumber.ifBlank { null },
                        "board_reg_number" to state.boardRegNumber.ifBlank { null },
                        "other_tutoring_source" to state.otherTutoringSources.ifEmpty { listOf("NA") },
                        "guardian_name" to state.guardianName.ifBlank { null },
                        "guardian_mobile" to state.guardianMobile.ifBlank { null }
                    )
                )

                val otherRes = apiService.updateProfile(otherMutation)
                if (otherRes.errors?.isNotEmpty() == true) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = otherRes.errors.first().message ?: "প্রোফাইল আপডেট করা যায়নি"
                        )
                    }
                    return@launch
                }

                // ===== Step 3: School change =====
                if (state.isSchoolChanged) {
                    try {
                        val schoolMutation = GraphQlQuery(
                            query = """
                                mutation UpdateUserSchool(${'$'}school_id: String, ${'$'}type: PrimaryUserTypeEnum!) {
                                  updateProfile(school_id: ${'$'}school_id, type: ${'$'}type) {
                                    school { id name }
                                  }
                                }
                            """.trimIndent(),
                            operationName = "UpdateUserSchool",
                            variables = mapOf(
                                "school_id" to state.selectedSchoolId,
                                "type" to "student"
                            )
                        )
                        apiService.updateUserSchool(schoolMutation)
                    } catch (_: Exception) { /* non-fatal */ }
                }

                // ===== Step 4: Local cache =====
                sessionManager.saveUserProfile(
                    firstName = state.firstName.ifBlank { sessionManager.getUserFirstName() },
                    lastName = null,
                    avatar = state.avatarUrl ?: sessionManager.getUserAvatar(),
                    schoolName = state.selectedSchoolName.ifBlank { sessionManager.getUserSchoolName() },
                    classDisplay = state.userClassDisplay.ifBlank { sessionManager.getUserClassDisplay() }
                )
                sessionManager.setAccountComplete(true)
                sessionManager.setJustSignedUp(false)

                // ===== Done =====
                if (nameChangeFailed) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "অন্যান্য তথ্য সফলভাবে সংরক্ষিত হয়েছে",
                            nameChangeWarning = nameErrorMsg
                                ?: "নাম পরিবর্তন করা যায়নি — অনুগ্রহ করে পরে চেষ্টা করুন"
                        )
                    }
                    delay(1800)
                } else {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "প্রোফাইল সফলভাবে আপডেট হয়েছে"
                        )
                    }
                    delay(600)
                }
                onSuccess()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "সংরক্ষণ ব্যর্থ: ${
                            extractServerMessage(e) ?: e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"
                        }"
                    )
                }
            }
        }
    }

    private fun validate(state: EditProfileUiState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val name = state.firstName.trim()
        when {
            name.isBlank() -> errors["firstName"] = "নাম খালি রাখা যাবে না"
            name.length < 2 -> errors["firstName"] = "নাম অন্তত ২ অক্ষরের হতে হবে"
            name.length > 50 -> errors["firstName"] = "নাম অনেক বড় (সর্বোচ্চ ৫০ অক্ষর)"
        }
        val gMobile = state.guardianMobile.trim()
        if (gMobile.isNotBlank() && !gMobile.matches(Regex("^01\\d{9}$"))) {
            errors["guardianMobile"] = "সঠিক বাংলাদেশি নম্বর দিন (01XXXXXXXXX)"
        }
        val roll = state.sscRollNumber.trim()
        if (roll.isNotBlank() && !roll.matches(Regex("^\\d{1,10}$"))) {
            errors["sscRoll"] = "রোল শুধু সংখ্যা, সর্বোচ্চ ১০ ডিজিট"
        }
        val reg = state.boardRegNumber.trim()
        if (reg.isNotBlank() && !reg.matches(Regex("^\\d{1,12}$"))) {
            errors["regNo"] = "রেজিস্ট্রেশন শুধু সংখ্যা, সর্বোচ্চ ১২ ডিজিট"
        }
        val hRoll = state.hscRollNumber.trim()
        if (hRoll.isNotBlank() && !hRoll.matches(Regex("^\\d{1,10}$"))) {
            errors["hscRoll"] = "এইচএসসি রোল শুধু সংখ্যা"
        }
        return errors
    }

    private fun extractServerMessage(e: Exception): String? {
        return when (e) {
            is retrofit2.HttpException -> {
                try {
                    val body = e.response()?.errorBody()?.string()
                    val msg = JSONObject(body ?: "").optString("message", "")
                    when {
                        msg.contains("60 days", true) || msg.contains("wait 60", true) ->
                            "৬০ দিনের মধ্যে নাম পরিবর্তন করা যাবে না। অনুগ্রহ করে পরে চেষ্টা করো।"
                        msg.isNotBlank() -> msg
                        else -> "HTTP ${e.code()}"
                    }
                } catch (_: Exception) { "HTTP ${e.code()}" }
            }
            else -> e.localizedMessage
        }
    }

    private fun formatIsoDateToDisplay(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val d = p.parse(iso.take(19)) ?: return iso
            SimpleDateFormat("dd MMMM yyyy", Locale.US).format(d)
        } catch (_: Exception) { iso }
    }
}

class EditProfileViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditProfileViewModel::class.java)) {
            return EditProfileViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
