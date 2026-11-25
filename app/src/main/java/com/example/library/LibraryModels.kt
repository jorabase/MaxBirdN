package com.example.library

import com.example.database.DownloadedItemEntity

data class TtsModelPackInfo(
    val lang: String, // "bn" or "en"
    val name: String,
    val url: String,
    val version: Int = 1,
    val sizeMb: Int = 35
)

data class LibraryHtmlFile(
    val id: String,
    val chapterId: String,
    val classTitle: String,
    val subjectTitle: String,
    val chapterTitle: String,
    val title: String,
    val description: String = "",
    val fileUrl: String,
    val fileSizeBytes: Long = 0L,
    val version: Int = 1,
    val order: Int = 0
) {
    /** Stable primary key used inside Room's `downloaded_items` table so books appear in Downloads too. */
    val downloadEntityId: String
        get() = "lib_html_${id.trim()}"

    val downloadSubtitle: String
        get() = listOf(classTitle, subjectTitle, chapterTitle)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
}

data class LibraryChapter(
    val id: String,
    val subjectId: String,
    val chapterNumber: Int = 0,
    val title: String,
    val subtitle: String = "",
    val order: Int = 0,
    val files: List<LibraryHtmlFile> = emptyList()
)

data class LibrarySubject(
    val id: String,
    val classId: String,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String = "",
    val colorHex: String = "",
    val order: Int = 0,
    val chapters: List<LibraryChapter> = emptyList()
) {
    val totalFilesCount: Int
        get() = chapters.sumOf { it.files.size }
}

data class LibraryClassGroup(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val badge: String = "",
    val colorHex: String = "",
    val order: Int = 0,
    val subjects: List<LibrarySubject> = emptyList()
) {
    val totalChaptersCount: Int
        get() = subjects.sumOf { it.chapters.size }

    val totalFilesCount: Int
        get() = subjects.sumOf { it.totalFilesCount }
}

data class LibraryIndex(
    val updatedAt: String = "",
    val r2BaseUrl: String = "",
    val ttsModels: Map<String, TtsModelPackInfo> = emptyMap(),
    val classes: List<LibraryClassGroup> = emptyList()
) {
    val allFiles: List<LibraryHtmlFile>
        get() = classes.flatMap { cls ->
            cls.subjects.flatMap { sub ->
                sub.chapters.flatMap { ch -> ch.files }
            }
        }
}

sealed interface LibraryNavLevel {
    data object Classes : LibraryNavLevel
    data class Subjects(val classGroup: LibraryClassGroup) : LibraryNavLevel
    data class Chapters(val classGroup: LibraryClassGroup, val subject: LibrarySubject) : LibraryNavLevel
    data class Files(
        val classGroup: LibraryClassGroup,
        val subject: LibrarySubject,
        val chapter: LibraryChapter
    ) : LibraryNavLevel
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val r2BaseUrl: String = "",
    val index: LibraryIndex? = null,
    val searchQuery: String = "",
    val selectedClassId: String? = null,
    val selectedSubjectId: String? = null,
    val selectedChapterId: String? = null,
    val downloadedById: Map<String, DownloadedItemEntity> = emptyMap(),
    val downloadedVersions: Map<String, Int> = emptyMap(),
    val isOfflineSnapshot: Boolean = false
) {
    val activeNavLevel: LibraryNavLevel
        get() {
            val classes = index?.classes.orEmpty()
            val cls = selectedClassId?.let { id -> classes.firstOrNull { it.id == id } }
                ?: return LibraryNavLevel.Classes
            val sub = selectedSubjectId?.let { id -> cls.subjects.firstOrNull { it.id == id } }
                ?: return LibraryNavLevel.Subjects(cls)
            val ch = selectedChapterId?.let { id -> sub.chapters.firstOrNull { it.id == id } }
                ?: return LibraryNavLevel.Chapters(cls, sub)
            return LibraryNavLevel.Files(cls, sub, ch)
        }
}
