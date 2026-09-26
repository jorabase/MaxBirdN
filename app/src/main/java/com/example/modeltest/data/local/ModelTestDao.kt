package com.example.modeltest.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelTestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswer(answer: OfflineMcqAnswerEntity): Long

    @Query("SELECT * FROM offline_model_test_answers WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPendingAnswersForSession(sessionId: String): List<OfflineMcqAnswerEntity>

    @Query("DELETE FROM offline_model_test_answers WHERE id = :id")
    suspend fun deleteAnswerById(id: Long)

    @Query("DELETE FROM offline_model_test_answers WHERE sessionId = :sessionId")
    suspend fun deleteAnswersBySession(sessionId: String)

    // Active session lifecycle state
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveSession(session: ActiveModelTestSessionEntity)

    @Query("SELECT * FROM active_model_test_state WHERE sessionId = :sessionId")
    suspend fun getActiveSession(sessionId: String): ActiveModelTestSessionEntity?

    @Query("DELETE FROM active_model_test_state WHERE sessionId = :sessionId")
    suspend fun clearActiveSession(sessionId: String)
}
