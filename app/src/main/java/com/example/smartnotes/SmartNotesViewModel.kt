package com.example.smartnotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.AcademicChapterItem
import com.example.api.AttachmentDataItem
import com.example.api.ShikhoApiService
import com.example.api.TaggableResourceItem
import com.example.auth.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SmartNotesUiState(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val subjectColor: String = "",
    val phaseId: String = "",
    val programId: String = "",
    val selectedTab: Int = 0, // 0: সাবজেক্ট রিসোর্সেস, 1: চ্যাপ্টার রিসোর্সেস
    
    // Subject-level resources
    val isSubjectResourcesLoading: Boolean = false,
    val subjectResources: List<TaggableResourceItem> = emptyList(),
    
    // Chapter list for Tab 2
    val isChaptersLoading: Boolean = false,
    val chapters: List<AcademicChapterItem> = emptyList(),
    
    // Chapter-level resources (when viewing a specific chapter)
    val selectedChapterId: String = "",
    val selectedChapterName: String = "",
    val isChapterResourcesLoading: Boolean = false,
    val chapterResources: List<TaggableResourceItem> = emptyList(),
    val directAttachmentsMap: Map<String, AttachmentDataItem> = emptyMap(),
    
    // PDF Opening state
    val isOpeningPdf: Boolean = false,
    val openingTagId: String? = null,
    val activeAttachment: AttachmentDataItem? = null,
    val selectedTagAttachments: List<AttachmentDataItem>? = null,
    val selectedTagTitle: String? = null,
    
    val errorMessage: String? = null
)

sealed class SmartNotesUiEvent {
    data class OpenPdfUrl(val url: String, val title: String) : SmartNotesUiEvent()
    data class ShowToast(val message: String) : SmartNotesUiEvent()
}

class SmartNotesViewModel(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager,
    private val repository: SmartNotesRepository = SmartNotesRepository(apiService)
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartNotesUiState())
    val uiState: StateFlow<SmartNotesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SmartNotesUiEvent>()
    val events: SharedFlow<SmartNotesUiEvent> = _events.asSharedFlow()

    private var loadSubjectJob: Job? = null
    private var loadChaptersJob: Job? = null
    private var loadChapterResourcesJob: Job? = null

    fun initialize(
        subjectCode: String,
        subjectTitle: String,
        subjectColor: String?,
        phaseId: String?,
        programId: String? = null
    ) {
        val activeProgId = programId?.takeIf { it.isNotBlank() }
            ?: _uiState.value.programId.takeIf { it.isNotBlank() }
            ?: sessionManager.getActiveProgramId()
            ?: ""
        val effectivePhaseId = phaseId ?: ""
        
        _uiState.update {
            it.copy(
                subjectCode = subjectCode,
                subjectTitle = subjectTitle,
                subjectColor = subjectColor ?: "#2563EB",
                phaseId = effectivePhaseId,
                programId = activeProgId
            )
        }

        loadSubjectResources(subjectCode, effectivePhaseId)
        loadChapters(subjectCode, effectivePhaseId)
    }

    fun setSelectedTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun loadSubjectResources(subjectCode: String, phaseId: String?) {
        loadSubjectJob?.cancel()
        loadSubjectJob = viewModelScope.launch {
            _uiState.update { it.copy(isSubjectResourcesLoading = true, errorMessage = null) }
            try {
                val rawResources = repository.listSubjectTaggableResources(subjectCode, phaseId)
                val progId = _uiState.value.programId.ifBlank { sessionManager.getActiveProgramId() ?: "" }
                val validResources = repository.filterResourcesWithPdf(
                    subjectCode = subjectCode,
                    phaseId = phaseId,
                    programId = progId,
                    chapterIds = emptyList(),
                    isSubjectSpecific = true,
                    resources = rawResources
                )
                _uiState.update {
                    it.copy(
                        isSubjectResourcesLoading = false,
                        subjectResources = validResources
                    )
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error loading subject resources: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isSubjectResourcesLoading = false,
                        errorMessage = com.example.utils.AppLang.tr("Failed to load resources", "রিসোর্স লোড করতে সমস্যা হয়েছে")
                    )
                }
            }
        }
    }

    fun loadChapters(subjectCode: String, phaseId: String?) {
        loadChaptersJob?.cancel()
        loadChaptersJob = viewModelScope.launch {
            _uiState.update { it.copy(isChaptersLoading = true) }
            try {
                val progId = _uiState.value.programId.ifBlank {
                    sessionManager.getActiveProgramId() ?: ""
                }
                val chaptersList = repository.getSubjectChapters(
                    programId = progId,
                    phaseId = phaseId,
                    subjectCode = subjectCode,
                    subjectTitle = _uiState.value.subjectTitle
                )
                _uiState.update {
                    it.copy(
                        isChaptersLoading = false,
                        chapters = chaptersList
                    )
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error loading chapters: ${e.message}", e)
                _uiState.update {
                    it.copy(isChaptersLoading = false)
                }
            }
        }
    }

    fun loadChapterResources(
        chapterId: String,
        phaseId: String?,
        chapterName: String? = null,
        subjectCode: String? = null,
        altChapterId: String? = null,
        programId: String? = null
    ) {
        loadChapterResourcesJob?.cancel()
        loadChapterResourcesJob = viewModelScope.launch {
            val effectiveSubCode = subjectCode?.ifBlank { null }
                ?: _uiState.value.subjectCode.ifBlank { null }
                ?: ""
            val effectiveProgId = programId?.ifBlank { null }
                ?: _uiState.value.programId.ifBlank { null }
                ?: sessionManager.getActiveProgramId() ?: ""
            val effectivePhaseId = phaseId?.ifBlank { null }
                ?: _uiState.value.phaseId.ifBlank { null }

            // Find matching chapter from loaded chapters to discover altChapterId
            var matchedChapter = _uiState.value.chapters.firstOrNull {
                it.id == chapterId || it.chapter_id == chapterId ||
                (!chapterName.isNullOrBlank() && (it.effectiveName.equals(chapterName, ignoreCase = true) || it.chapter_name.equals(chapterName, ignoreCase = true) || it.name.equals(chapterName, ignoreCase = true)))
            }

            if (matchedChapter == null && _uiState.value.chapters.isEmpty() && effectiveSubCode.isNotBlank()) {
                try {
                    val chaptersList = repository.getSubjectChapters(
                        programId = effectiveProgId,
                        phaseId = effectivePhaseId,
                        subjectCode = effectiveSubCode,
                        subjectTitle = _uiState.value.subjectTitle
                    )
                    if (chaptersList.isNotEmpty()) {
                        _uiState.update { it.copy(chapters = chaptersList) }
                        matchedChapter = chaptersList.firstOrNull {
                            it.id == chapterId || it.chapter_id == chapterId ||
                            (!chapterName.isNullOrBlank() && (it.effectiveName.equals(chapterName, ignoreCase = true) || it.chapter_name.equals(chapterName, ignoreCase = true) || it.name.equals(chapterName, ignoreCase = true)))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmartNotesVM", "Error loading fallback chapters: ${e.message}")
                }
            }

            val resolvedAltChapterId = altChapterId?.ifBlank { null }
                ?: matchedChapter?.let { if (it.id == chapterId) it.chapter_id else it.id }

            _uiState.update {
                it.copy(
                    selectedChapterId = chapterId,
                    selectedChapterName = chapterName ?: matchedChapter?.effectiveName ?: it.selectedChapterName,
                    isChapterResourcesLoading = true,
                    chapterResources = emptyList(),
                    directAttachmentsMap = emptyMap(),
                    errorMessage = null
                )
            }

            try {
                val candidateIds = listOfNotNull(chapterId.takeIf { it.isNotBlank() }, resolvedAltChapterId?.takeIf { it.isNotBlank() })

                // 1. Try listing taggable resources
                val rawResources = repository.listChapterTaggableResources(
                    chapterId = chapterId,
                    phaseId = effectivePhaseId,
                    subjectId = effectiveSubCode,
                    altChapterId = resolvedAltChapterId
                )

                val validTagResources = if (rawResources.isNotEmpty()) {
                    repository.filterResourcesWithPdf(
                        subjectCode = effectiveSubCode,
                        phaseId = effectivePhaseId,
                        programId = effectiveProgId,
                        chapterIds = candidateIds,
                        isSubjectSpecific = false,
                        resources = rawResources
                    )
                } else {
                    emptyList()
                }

                var finalResources = validTagResources
                val newDirectMap = mutableMapOf<String, AttachmentDataItem>()

                // 2. If no valid taggable resources found, try fetching direct attachments
                if (finalResources.isEmpty()) {
                    val directAttachments = repository.getResourceAttachmentsOfChapter(
                        subjectId = effectiveSubCode,
                        moduleId = effectiveProgId,
                        phaseId = effectivePhaseId,
                        chapterIds = candidateIds,
                        resourceTypeTagIds = null,
                        isSubjectSpecific = false
                    ).filter { !it.url.isNullOrBlank() }

                    if (directAttachments.isNotEmpty()) {
                        finalResources = directAttachments.map { att ->
                            val attId = att.id ?: java.util.UUID.randomUUID().toString()
                            newDirectMap[attId] = att
                            TaggableResourceItem(
                                id = attId,
                                title = att.title ?: com.example.utils.AppLang.tr("Chapter e-Book / Notes", "অধ্যায় ই-বুক / নোট"),
                                icon_url = "https://cdn.shikho.com/resources/icons/ebook.svg",
                                is_chapter_resource = true,
                                is_subject_resource = false
                            )
                        }
                    }
                }

                // 3. If still empty, check chapter lessons for slide / PDF attachments
                if (finalResources.isEmpty() && effectiveProgId.isNotBlank()) {
                    val lessonAttachments = repository.getChapterLessonAttachments(
                        programId = effectiveProgId,
                        phaseId = effectivePhaseId,
                        chapterId = chapterId,
                        altChapterId = resolvedAltChapterId
                    ).filter { !it.url.isNullOrBlank() }

                    if (lessonAttachments.isNotEmpty()) {
                        finalResources = lessonAttachments.map { att ->
                            val attId = att.id ?: java.util.UUID.randomUUID().toString()
                            newDirectMap[attId] = att
                            TaggableResourceItem(
                                id = attId,
                                title = att.title ?: com.example.utils.AppLang.tr("Lecture Sheet & Slides", "লেকচার শিট ও স্লাইড"),
                                icon_url = "https://cdn.shikho.com/resources/icons/ebook.svg",
                                is_chapter_resource = true,
                                is_subject_resource = false
                            )
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isChapterResourcesLoading = false,
                        chapterResources = finalResources,
                        directAttachmentsMap = newDirectMap
                    )
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error loading chapter resources: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isChapterResourcesLoading = false,
                        errorMessage = com.example.utils.AppLang.tr("Failed to load chapter resources", "অধ্যায়ের রিসোর্স লোড করতে ব্যর্থ হয়েছে")
                    )
                }
            }
        }
    }

    fun openResourcePdf(
        programId: String? = null,
        phaseId: String? = null,
        subjectCode: String,
        chapterId: String? = null,
        tagId: String,
        isSubjectSpecific: Boolean,
        resourceTitle: String = com.example.utils.AppLang.tr("PDF Notes", "পিডিএফ নোট"),
        onUrlReady: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            // Check if we have this attachment directly cached in directAttachmentsMap
            val cachedDirect = _uiState.value.directAttachmentsMap[tagId]
            if (cachedDirect?.url != null) {
                _uiState.update {
                    it.copy(
                        isOpeningPdf = false,
                        openingTagId = null,
                        selectedTagAttachments = listOf(cachedDirect),
                        selectedTagTitle = resourceTitle
                    )
                }
                return@launch
            }

            val effectiveProgId = programId?.ifBlank { null }
                ?: _uiState.value.programId.ifBlank { null }
                ?: sessionManager.getActiveProgramId() ?: ""
            val effectivePhaseId = phaseId?.ifBlank { null }
                ?: _uiState.value.phaseId.ifBlank { null }

            _uiState.update {
                it.copy(
                    isOpeningPdf = true,
                    openingTagId = tagId,
                    errorMessage = null
                )
            }

            try {
                val chapterIds = if (isSubjectSpecific || chapterId.isNullOrBlank()) {
                    emptyList()
                } else {
                    val candidateIds = mutableListOf<String>()
                    if (chapterId.isNotBlank()) candidateIds.add(chapterId)
                    val matchedChapter = _uiState.value.chapters.firstOrNull {
                        it.id == chapterId || it.chapter_id == chapterId
                    }
                    val resolvedAltChapterId = matchedChapter?.let { if (it.id == chapterId) it.chapter_id else it.id }
                    if (!resolvedAltChapterId.isNullOrBlank() && resolvedAltChapterId != chapterId) {
                        candidateIds.add(resolvedAltChapterId)
                    }
                    candidateIds
                }

                val attachments = repository.getResourceAttachmentsOfChapter(
                    subjectId = subjectCode,
                    moduleId = effectiveProgId,
                    phaseId = effectivePhaseId,
                    chapterIds = chapterIds,
                    resourceTypeTagIds = listOf(tagId),
                    isSubjectSpecific = isSubjectSpecific
                ).filter { !it.url.isNullOrBlank() }

                if (attachments.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            isOpeningPdf = false,
                            openingTagId = null,
                            selectedTagAttachments = attachments,
                            selectedTagTitle = resourceTitle
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isOpeningPdf = false,
                            openingTagId = null
                        )
                    }
                    _events.emit(SmartNotesUiEvent.ShowToast(com.example.utils.AppLang.tr("No PDF found for this resource", "এই রিসোর্সের কোন পিডিএফ পাওয়া যায়নি")))
                }
            } catch (e: Exception) {
                Log.e("SmartNotesVM", "Error opening resource PDF: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isOpeningPdf = false,
                        openingTagId = null,
                        errorMessage = com.example.utils.AppLang.tr(
                            "Could not load PDF: ${e.localizedMessage}",
                            "পিডিএফ লোড করা যায়নি: ${e.localizedMessage}"
                        )
                    )
                }
                _events.emit(SmartNotesUiEvent.ShowToast(com.example.utils.AppLang.tr("Error loading PDF", "পিডিএফ লোড করতে ত্রুটি ঘটেছে")))
            }
        }
    }

    fun dismissTagAttachments() {
        _uiState.update {
            it.copy(
                selectedTagAttachments = null,
                selectedTagTitle = null
            )
        }
    }

    fun selectAttachment(attachment: AttachmentDataItem) {
        _uiState.update {
            it.copy(
                activeAttachment = attachment
            )
        }
    }

    fun dismissActiveAttachment() {
        _uiState.update { it.copy(activeAttachment = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

class SmartNotesViewModelFactory(
    private val apiService: ShikhoApiService,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmartNotesViewModel::class.java)) {
            return SmartNotesViewModel(apiService, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
