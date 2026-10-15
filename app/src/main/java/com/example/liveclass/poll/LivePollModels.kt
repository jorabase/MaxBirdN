package com.example.liveclass.poll

/**
 * Represents a real 100ms Poll/Quiz session in a live class.
 */
data class LivePollItem(
    val pollId: String,
    val title: String,
    val state: String = "started", // "started" or "stopped"
    val type: String = "quiz", // "quiz" or "poll"
    val createdBy: String = "",
    val startedBy: String = "",
    val stoppedBy: String = "",
    val teacherName: String = "",
    val roomId: String = "",
    val sessionId: String = "",
    val anonymous: Boolean = false,
    val visibility: Boolean = true,
    val locked: Boolean = true,
    val mode: String = "",
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val stoppedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val durationSeconds: Int = 0,
    val questionsCount: Int = 1,
    val questions: List<LivePollQuestion> = emptyList(),
    val userResponse: LivePollUserResponse? = null,
    val leaderboard: List<LivePollLeaderboardEntry> = emptyList(),
    val totalResponses: Int = 0,
    val votedUsers: Int = 0,
    val maxUsers: Int = 0,
    val totalUsers: Int = 0,
    val correctUsers: Int = 0,
    val avgTimeMs: Double = 0.0,
    val avgScore: Double = 0.0
) {
    val isEnded: Boolean
        get() = state.equals("stopped", ignoreCase = true) || state.equals("ended", ignoreCase = true)

    val isLive: Boolean
        get() = state.equals("started", ignoreCase = true)
}

data class LivePollQuestion(
    val index: Int = 1,
    val text: String,
    val type: String = "single-choice",
    val skippable: Boolean = false,
    val once: Boolean = false,
    val weight: Int = 10,
    val negative: Boolean = false,
    val durationSeconds: Int = 0,
    val options: List<LivePollOption> = emptyList(),
    val correctOptionIndex: Int? = null, // 1-based (1 = A, 2 = B, 3 = C, 4 = D)
    val answerHidden: Boolean = false,
    val totalVotes: Int = 0,
    val correctVotes: Int = 0,
    val skippedVotes: Int = 0
)

data class LivePollOption(
    val index: Int, // 1-based index
    val text: String, // "A", "B", "C", "D" or full option text
    val weight: Int = 0,
    val votesCount: Int = 0
)

data class LivePollUserResponse(
    val responseId: String = "",
    val pollId: String,
    val questionIndex: Int = 1,
    val selectedOptionIndex: Int, // 1-based index
    val durationMs: Long = 0L,
    val isCorrect: Boolean = false,
    val skipped: Boolean = false,
    val update: Boolean = false,
    val peerId: String = "",
    val userId: String = "",
    val userName: String = ""
)

data class LivePollLeaderboardEntry(
    val position: Int,
    val questionIndex: Int = 0,
    val peerId: String = "",
    val userId: String = "",
    val userName: String,
    val score: Int = 0,
    val totalScore: Int = 10,
    val durationMs: Long = 0L,
    val durationSeconds: Double = 0.0,
    val correctResponses: Int = 0,
    val totalResponses: Int = 1
)
