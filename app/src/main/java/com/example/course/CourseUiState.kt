package com.example.course

import com.example.api.*

data class SubjectWithProgress(
    val subject: AcademicSubjectItem,
    val progressBar: SubjectProgressBarItem?
) {
    val progressPercentage: Int
        get() = progressBar?.percentage?.toInt() ?: 0

    val completedChapters: Int
        get() = progressBar?.completed_chapters ?: 0

    val totalChapters: Int
        get() = progressBar?.total_chapters ?: 0
}

data class CourseUiState(
    // Enrolled & Available Programs lists
    val enrolledPrograms: List<EnrolledProgram> = emptyList(),
    val freePrograms: List<OtherProgram> = emptyList(),
    val otherPrograms: List<OtherProgram> = emptyList(),
    val isProgramsLoading: Boolean = false,
    val selectedCourseProgram: EnrolledProgram? = null,
    val enrollingProgramId: String? = null,
    val enrollmentSuccessMessage: String? = null,
    val enrollmentErrorMessage: String? = null,
    val pendingEnrollmentProgram: OtherProgram? = null,

    // Active Program Info
    val programId: String = "",
    val programTitle: String = "",
    val hasAnimatedVideo: Boolean = false,
    val activePhaseId: String = "",
    val activePhaseTitle: String = "",
    val phases: List<PhaseItem> = emptyList(),
    val selectedPhase: PhaseItem? = null,

    // Tier 1: Subjects
    val subjects: List<SubjectWithProgress> = emptyList(),
    val isSubjectsLoading: Boolean = false,
    val subjectsErrorMessage: String? = null,

    // Tier 2: Selected Subject & Chapters
    val selectedSubjectCode: String = "",
    val selectedSubjectTitle: String = "",
    val selectedSubjectColor: String = "",
    val chapters: List<AcademicChapterItem> = emptyList(),
    val isChaptersLoading: Boolean = false,
    val chaptersErrorMessage: String? = null,

    // Tier 3: Selected Chapter & Lessons
    val selectedChapterId: String = "",
    val selectedChapterName: String = "",
    val selectedChapterStatus: String = "",
    val lessons: List<StudentLessonItem> = emptyList(),
    val isLessonsLoading: Boolean = false,
    val lessonsErrorMessage: String? = null,
    val lessonsDiagnosticInfo: String? = null,

    // Tier 4: Selected Lesson Detail
    val selectedLesson: StudentLessonItem? = null,
    val isLessonDetailLoading: Boolean = false,
    val selectedLessonTopicVideos: List<com.example.api.TopicFullItem> = emptyList()
)
