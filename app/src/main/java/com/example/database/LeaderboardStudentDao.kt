package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LeaderboardStudentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<LeaderboardStudentEntity>)

    @Query("""
        SELECT * FROM leaderboard_students
        WHERE programId = :programId
          AND (:phaseId = '' OR :phaseId = 'all' OR phaseId = :phaseId)
          AND (
              LOWER(name) LIKE '%' || LOWER(:query) || '%'
              OR phone LIKE '%' || :query || '%'
              OR LOWER(college) LIKE '%' || LOWER(:query) || '%'
              OR roll LIKE '%' || :query || '%'
              OR LOWER(district) LIKE '%' || LOWER(:query) || '%'
              OR LOWER(division) LIKE '%' || LOWER(:query) || '%'
          )
        ORDER BY rank ASC
        LIMIT 300
    """)
    suspend fun searchStudents(programId: String, phaseId: String, query: String): List<LeaderboardStudentEntity>

    @Query("""
        SELECT * FROM leaderboard_students
        WHERE programId = :programId
          AND (
              LOWER(name) LIKE '%' || LOWER(:query) || '%'
              OR phone LIKE '%' || :query || '%'
              OR LOWER(college) LIKE '%' || LOWER(:query) || '%'
              OR roll LIKE '%' || :query || '%'
              OR LOWER(district) LIKE '%' || LOWER(:query) || '%'
              OR LOWER(division) LIKE '%' || LOWER(:query) || '%'
          )
        ORDER BY rank ASC
        LIMIT 300
    """)
    suspend fun searchProgramStudents(programId: String, query: String): List<LeaderboardStudentEntity>

    @Query("""
        SELECT * FROM leaderboard_students
        WHERE (
            LOWER(name) LIKE '%' || LOWER(:query) || '%'
            OR phone LIKE '%' || :query || '%'
            OR LOWER(college) LIKE '%' || LOWER(:query) || '%'
            OR roll LIKE '%' || :query || '%'
            OR LOWER(district) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(division) LIKE '%' || LOWER(:query) || '%'
        )
        ORDER BY rank ASC
        LIMIT 300
    """)
    suspend fun searchGlobalStudents(query: String): List<LeaderboardStudentEntity>

    @Query("""
        SELECT * FROM leaderboard_students
        WHERE programId = :programId
          AND (:phaseId = '' OR phaseId = :phaseId)
          AND (:subjectId = 'all' OR :subjectId = 'ALL' OR subjectId = :subjectId)
        ORDER BY rank ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getRankings(programId: String, phaseId: String, subjectId: String, limit: Int, offset: Int): List<LeaderboardStudentEntity>

    @Query("SELECT COUNT(*) FROM leaderboard_students WHERE programId = :programId")
    suspend fun getCountByProgram(programId: String): Int

    @Query("SELECT COUNT(*) FROM leaderboard_students")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM leaderboard_students WHERE programId = :programId AND phaseId = :phaseId")
    suspend fun clearForPhase(programId: String, phaseId: String)
}

