package com.example.modeltest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity to store offline/pending MCQ answer submissions for Model Test.
 * When the user is offline or connection drops, selected answers are cached here
 * and automatically synced when connection is restored.
 */
@Entity(tableName = "offline_model_test_answers")
data class OfflineMcqAnswerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val questionId: String,
    val selectedOptionIndex: Int,
    val isTimeout: Boolean = false,
    val isFinalSubmitted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entity to preserve in-progress exam session state during Android app lifecycle changes (onPause, phone lock, etc.)
 */
@Entity(tableName = "active_model_test_state")
data class ActiveModelTestSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val modelTestId: String,
    val isPractice: Boolean,
    val currentQuestionIndex: Int,
    val remainingSeconds: Long,
    val answersJson: String, // Map of questionId -> selectedOption
    val lastUpdated: Long = System.currentTimeMillis()
)
