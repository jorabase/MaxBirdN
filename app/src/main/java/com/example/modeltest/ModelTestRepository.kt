package com.example.modeltest

import android.util.Log
import com.example.api.GraphQlQuery
import com.example.api.ShikhoApiService
import com.example.api.StudentLessonItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for Model Test & Revision type courses.
 * Interacts with Shikho GraphQL API using SubjectSpecificModelTestsOrLiveClass query.
 */
class ModelTestRepository(
    private val apiService: ShikhoApiService
) {
    /**
     * Executes SubjectSpecificModelTestsOrLiveClass GraphQL query to fetch:
     * - Model tests when contentType = "ModelTest"
     * - Live/recorded classes when contentType = "LiveClass"
     */
    suspend fun getSubjectSpecificLessons(
        programId: String,
        phaseId: String,
        subjectId: String,
        contentType: String // "ModelTest" or "LiveClass"
    ): Result<List<StudentLessonItem>> = withContext(Dispatchers.IO) {
        val query = """
            query SubjectSpecificModelTestsOrLiveClass(${'$'}program_id: String!, ${'$'}content_type: LessonContentTypeEnum!, ${'$'}phase_id: String!, ${'$'}subject_id: String!) {
              studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, content_type: ${'$'}content_type, subject_id: ${'$'}subject_id) {
                data {
                  access_level
                  title
                  id
                  content_id
                  content_type
                  program_id
                  user_activity_state
                  start_time
                  end_time
                  subject_name
                  model_test {
                    result_publish_time
                  }
                  live_class {
                    chapter_id
                    chapter_name
                    end_time
                    is_on_going
                    start_time
                    subject_name
                    subject_id
                    id
                    type
                  }
                  model_test {
                    type
                    exam_category
                  }
                  phase_id
                }
              }
            }
        """.trimIndent()

        val variables = mapOf(
            "program_id" to programId,
            "phase_id" to phaseId,
            "subject_id" to subjectId,
            "content_type" to contentType
        )

        try {
            val q = GraphQlQuery(
                operationName = "SubjectSpecificModelTestsOrLiveClass",
                query = query,
                variables = variables
            )
            val response = apiService.getStudentLessons(q)
            if (response.errors != null && response.errors.isNotEmpty()) {
                val errorMsg = response.errors.firstOrNull()?.message ?: "GraphQL Error"
                Log.e("ModelTestRepository", "GraphQL error in getSubjectSpecificLessons: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }
            val lessons = response.data?.studentSpecificLessons?.data ?: emptyList()
            Log.d("ModelTestRepository", "Fetched ${lessons.size} items for contentType=$contentType, subjectId=$subjectId, phaseId=$phaseId, programId=$programId")
            Result.success(lessons)
        } catch (e: Exception) {
            Log.e("ModelTestRepository", "Error loading $contentType for subject $subjectId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
