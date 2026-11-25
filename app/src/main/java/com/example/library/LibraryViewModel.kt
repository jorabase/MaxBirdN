package com.example.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.DownloadedItemEntity
import com.example.download.AppFileDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val downloadManager = AppFileDownloadManager.getInstance(appContext)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        // Immediately seed with cached index for instant cold-open display
        val cached = LibraryRepository.getCachedIndexSync(appContext)
        val cachedR2 = LibraryRepository.getCachedR2BaseUrlSync(appContext)
        val versions = LibraryRepository.getAllDownloadedVersions(appContext)
        if (cached != null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    r2BaseUrl = cachedR2,
                    index = cached,
                    downloadedVersions = versions,
                    isOfflineSnapshot = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    r2BaseUrl = cachedR2,
                    downloadedVersions = versions
                )
            }
        }

        observeDownloads()
        loadLibrary(forceRefresh = false)
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadManager.getAllDownloads().collectLatest { list ->
                val htmlMap = list
                    .filter { it.fileType.equals(DownloadedItemEntity.FILE_TYPE_HTML_BOOK, ignoreCase = true) }
                    .associateBy { it.id }
                val versions = LibraryRepository.getAllDownloadedVersions(appContext)
                _uiState.update {
                    it.copy(
                        downloadedById = htmlMap,
                        downloadedVersions = versions
                    )
                }
            }
        }
    }

    fun loadLibrary(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasExisting = _uiState.value.index != null
            _uiState.update {
                it.copy(
                    isLoading = !hasExisting,
                    isRefreshing = hasExisting && forceRefresh,
                    errorMessage = if (forceRefresh) null else it.errorMessage
                )
            }

            val result = LibraryRepository.fetchLibraryIndex(appContext, forceRefresh = forceRefresh)
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    r2BaseUrl = result.r2BaseUrl,
                    index = result.index ?: current.index,
                    isOfflineSnapshot = result.isFromOfflineCache,
                    errorMessage = result.errorMessage,
                    downloadedVersions = LibraryRepository.getAllDownloadedVersions(appContext)
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectClass(classId: String) {
        _uiState.update {
            it.copy(
                selectedClassId = classId,
                selectedSubjectId = null,
                selectedChapterId = null,
                searchQuery = ""
            )
        }
    }

    fun selectSubject(subjectId: String) {
        _uiState.update {
            it.copy(
                selectedSubjectId = subjectId,
                selectedChapterId = null,
                searchQuery = ""
            )
        }
    }

    fun selectChapter(chapterId: String) {
        _uiState.update {
            it.copy(
                selectedChapterId = chapterId,
                searchQuery = ""
            )
        }
    }

    fun jumpToRoot() {
        _uiState.update {
            it.copy(
                selectedClassId = null,
                selectedSubjectId = null,
                selectedChapterId = null
            )
        }
    }

    fun jumpToClassLevel() {
        _uiState.update {
            it.copy(
                selectedSubjectId = null,
                selectedChapterId = null
            )
        }
    }

    fun jumpToSubjectLevel() {
        _uiState.update {
            it.copy(selectedChapterId = null)
        }
    }

    /**
     * Navigates up one folder level inside the Library tab.
     * Returns `true` if handled internally, or `false` if already at root (Classes level).
     */
    fun navigateBack(): Boolean {
        val state = _uiState.value
        return when {
            state.selectedChapterId != null -> {
                _uiState.update { it.copy(selectedChapterId = null) }
                true
            }
            state.selectedSubjectId != null -> {
                _uiState.update { it.copy(selectedSubjectId = null) }
                true
            }
            state.selectedClassId != null -> {
                _uiState.update { it.copy(selectedClassId = null) }
                true
            }
            else -> false
        }
    }

    fun downloadFile(file: LibraryHtmlFile, forceRedownload: Boolean = false) {
        LibraryRepository.downloadHtmlFile(appContext, file, forceRedownload = forceRedownload)
    }

    fun downloadEntireChapter(chapter: LibraryChapter): Int {
        return LibraryRepository.downloadChapterFiles(appContext, chapter, _uiState.value.downloadedById)
    }

    fun deleteDownloadedFile(file: LibraryHtmlFile) {
        LibraryRepository.deleteDownloadedHtmlFile(appContext, file.downloadEntityId)
    }

    fun saveCustomR2BaseUrl(url: String) {
        LibraryRepository.saveCustomR2BaseUrl(appContext, url)
        loadLibrary(forceRefresh = true)
    }
}
