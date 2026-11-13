package com.example.quiz

import com.example.api.*

enum class FeedbackFilter(val labelBn: String) {
    ALL("সব উত্তর"),
    CORRECT("সঠিক"),
    INCORRECT("ভুল"),
    UNANSWERED("উত্তরহীন")
}

data class PracticeQuizUiState(
    // Step 1: Chapter selection
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val subjectColorHex: String? = null,
    val subjectIcon: String? = null,
    val totalActiveQuestionsInSubject: Int = 0,
    val chapters: List<HierarchyChapterItem> = emptyList(),
    val allSubjectChapters: List<HierarchyChapterItem> = emptyList(),
    val selectedChapterIds: Set<String> = emptySet(),
    val targetChapterId: String? = null,
    val targetChapterName: String? = null,
    val isSingleChapterMode: Boolean = false,
    val isChaptersLoading: Boolean = false,
    val chaptersError: String? = null,

    // Step 2: Question count & Summary BottomSheet
    val availableQuestionCounts: List<Int> = listOf(10, 20),
    val selectedQuestionCount: Int = 10,
    val showSummarySheet: Boolean = false,
    val isStartingQuiz: Boolean = false,
    val startQuizError: String? = null,
    val practiceLimits: CustomPracticeLimits? = null,

    // Step 3: Active Quiz Player
    val session: PracticeQuizSessionItem? = null,
    val sessionId: String = "",
    val currentQuestionIndex: Int = 0,
    val userAnswers: Map<String, String> = emptyMap(), // questionId -> "A", "B", "C", "D"
    val remainingSeconds: Long = 0L,
    val initialTotalSeconds: Long = 600L,
    val elapsedTimeSeconds: Long = 0L,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val isQuizFinished: Boolean = false,

    // Step 4: Result
    val resultSummary: QuizResultSummaryPayload? = null,
    val isResultLoading: Boolean = false,
    val resultError: String? = null,

    // Step 5: Feedback / Solutions
    val feedbackSession: PracticeQuizSessionItem? = null,
    val isFeedbackLoading: Boolean = false,
    val feedbackError: String? = null,
    val feedbackFilter: FeedbackFilter = FeedbackFilter.ALL,
    val bookmarkedQuestionIds: Set<String> = emptySet(),
    val bookmarkMessage: String? = null
) {
    val areAllChaptersSelected: Boolean
        get() = chapters.isNotEmpty() && selectedChapterIds.size == chapters.size

    val selectedChaptersCount: Int
        get() = selectedChapterIds.size

    val answeredQuestionsCount: Int
        get() = userAnswers.size

    val currentQuestion: PracticeQuizQuestionItem?
        get() = session?.questions?.getOrNull(currentQuestionIndex)

    val totalQuestionsCount: Int
        get() = session?.questions?.size ?: selectedQuestionCount

    val timeRemainingFormatted: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val isUrgentTimer: Boolean
        get() = remainingSeconds in 1..60
}
