package com.example.liveclass.poll

import java.util.UUID

/**
 * Represents a live Poll/Quiz session in a 100ms live class.
 */
data class LivePollItem(
    val pollId: String,
    val title: String,
    val state: String = "started", // "started" or "stopped"
    val type: String = "quiz", // "quiz" or "poll"
    val createdBy: String = "",
    val teacherName: String = "শিক্ষক",
    val createdAt: Long = System.currentTimeMillis() / 1000,
    val startedAt: Long = System.currentTimeMillis() / 1000,
    val stoppedAt: Long = 0,
    val durationSeconds: Int = 0,
    val questionsCount: Int = 1,
    val questions: List<LivePollQuestion> = emptyList(),
    val userResponse: LivePollUserResponse? = null,
    val leaderboard: List<LivePollLeaderboardEntry> = emptyList(),
    val totalResponses: Int = 0,
    val votedUsers: Int = 0,
    val correctUsers: Int = 0
) {
    val isEnded: Boolean get() = state.equals("stopped", ignoreCase = true) || state.equals("ended", ignoreCase = true)
    val isLive: Boolean get() = !isEnded
}

data class LivePollQuestion(
    val index: Int = 1,
    val text: String,
    val type: String = "single-choice",
    val weight: Int = 10,
    val durationSeconds: Int = 0,
    val options: List<LivePollOption> = emptyList(),
    val correctOptionIndex: Int? = null // 1-based (1 = A, 2 = B, 3 = C, 4 = D)
)

data class LivePollOption(
    val index: Int, // 1-based index
    val text: String, // "A", "B", "C", "D" or full text
    val votesCount: Int = 0
)

data class LivePollUserResponse(
    val pollId: String,
    val questionIndex: Int = 1,
    val selectedOptionIndex: Int, // 1-based index
    val durationMs: Long = 0L,
    val isCorrect: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class LivePollLeaderboardEntry(
    val position: Int,
    val peerId: String = "",
    val userId: String = "",
    val userName: String,
    val score: Int = 10,
    val totalScore: Int = 10,
    val durationSeconds: Double,
    val correctResponses: Int = 1,
    val totalResponses: Int = 1
)

data class LivePollSummary(
    val rank: Int,
    val points: Int,
    val timeTakenSeconds: Double,
    val correctAnswersText: String
)
