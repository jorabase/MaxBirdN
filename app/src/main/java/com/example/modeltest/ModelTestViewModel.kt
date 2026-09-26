package com.example.modeltest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.StudentLessonItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModelTestTab {
    MODEL_TEST,
    CLASS
}

data class ModelTestUiState(
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
    val subjectColor: String = ""
) {
    val currentErrorMessage: String?
        get() = when (selectedTab) {
            ModelTestTab.MODEL_TEST -> modelTestsError
            ModelTestTab.CLASS -> classesError
        }
}

class ModelTestViewModel(
    private val repository: ModelTestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelTestUiState())
    val uiState: StateFlow<ModelTestUiState> = _uiState.asStateFlow()

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

    fun refreshCurrentTab() {
        when (_uiState.value.selectedTab) {
            ModelTestTab.MODEL_TEST -> loadModelTests(forceRefresh = true)
            ModelTestTab.CLASS -> loadClasses(forceRefresh = true)
        }
    }
}
