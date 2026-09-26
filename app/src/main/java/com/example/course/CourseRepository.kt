package com.example.course

import com.example.api.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Encapsulates GraphQL query definitions and remote data fetching methods for Course and Lesson flows.
 */
class CourseRepository(
    private val apiService: ShikhoApiService
) {

    suspend fun getAcademicProgramByEnrollment(
        batchId: String? = null,
        className: String,
        group: String? = null,
        vendor: String = "BD"
    ): AcademicProgramResponse {
        // C5-C8 এর ক্ষেত্রে group সবসময় "None" হতে হবে, C9-C12 এর জন্য Humanities/Science/Business_Studies
        val classUpper = className.uppercase()
        val formattedGroup = if (classUpper in listOf("C5", "C6", "C7", "C8", "C05", "C06", "C07", "C08")) {
            "None"
        } else {
            when (group?.lowercase()) {
                "humanities", "arts", "hum" -> "Humanities"
                "science", "sci" -> "Science"
                "business_studies", "businessstudies", "business", "commerce", "bus" -> "BusinessStudies"
                else -> "None"
            }
        }

        val query = GraphQlQuery(
            operationName = "GetAcademicProgram",
            query = """
                query GetAcademicProgram(${'$'}batch_id: String, ${'$'}className: AcademicProgramClassEnum, ${'$'}group: StudyGroupTypeEnum, ${'$'}vendor: VendorEnum) {
                  listAcademicProgramByEnrollment(batch_id: ${'$'}batch_id, class: ${'$'}className, group: ${'$'}group, vendor: ${'$'}vendor) {
                    enrolled_programs {
                      id
                      classes
                      title_bn
                      facebook_group_url
                      banner_url
                      color
                      course_feature_list
                      has_animated_video
                      is_free
                      phase_pricing
                      trial_enabled
                      trial_duration
                      serial
                      subjects {
                        code
                        display
                        display_bn
                        color_code
                        icon
                      }
                      quarter_discount_price
                      full_program_discount_price
                      enrollment_details {
                        expiry_date
                        type
                        created_at
                        batch_id
                        is_on_installment
                        is_active
                        trial_end_date
                        consumable_resources
                        is_qr
                        tag
                      }
                    }
                    other_programs {
                      id
                      classes
                      title_bn
                      facebook_group_url
                      banner_url
                      phase_pricing
                      is_free
                      has_animated_video
                      full_program_discount_price
                      pricing {
                        sale_price_quarterly
                        sale_price_full
                      }
                      trial_enabled
                      trial_duration
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "batch_id" to batchId,
                "className" to className,
                "group" to formattedGroup,
                "vendor" to vendor
            )
        )
        return apiService.getAcademicProgram(query)
    }

    suspend fun getAcademicProgramByEnrollment(className: String): AcademicProgramResponse {
        return getAcademicProgramByEnrollment(null, className, null, "BD")
    }

    suspend fun generateFreeTrialEnrolment(programId: String, userId: String): Result<String> {
        return try {
            val query = GraphQlQuery(
                operationName = "AvailTrial",
                query = """
                    mutation AvailTrial(${'$'}program_id: String = "" , ${'$'}user_id: String = "" ) {
                      generateFreeTrialEnrolment(program_id: ${'$'}program_id, user_id: ${'$'}user_id) {
                        message
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "program_id" to programId,
                    "user_id" to userId
                )
            )
            val response = apiService.availTrial(query)
            if (!response.errors.isNullOrEmpty()) {
                val errMsg = response.errors.mapNotNull { it.message }.joinToString(", ")
                Result.failure(Exception(errMsg.ifBlank { "সার্ভার থেকে ত্রুটি এসেছে" }))
            } else {
                val msg = response.data?.generateFreeTrialEnrolment?.message ?: response.message ?: "success"
                Result.success(msg)
            }
        } catch (e: retrofit2.HttpException) {
            val errorJson = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            if (errorJson != null && (errorJson.contains("already enrolled", ignoreCase = true) || errorJson.contains("success", ignoreCase = true))) {
                Result.success("success")
            } else {
                val parsedMsg = try {
                    val obj = org.json.JSONObject(errorJson ?: "")
                    obj.optString("message", e.message())
                } catch (_: Exception) {
                    e.message()
                }
                Result.failure(Exception(parsedMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enrollInFreeProgram(programId: String): Result<String> {
        return try {
            val query = GraphQlQuery(
                operationName = "EnrollInFreeProgram",
                query = """
                    mutation EnrollInFreeProgram(${'$'}academic_program_id: String!) {
                      enrollInFreeProgram(academic_program_id: ${'$'}academic_program_id) {
                        message
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "academic_program_id" to programId
                )
            )
            val response = apiService.enrollInFreeProgram(query)
            if (!response.errors.isNullOrEmpty()) {
                val errMsg = response.errors.mapNotNull { it.message }.joinToString(", ")
                Result.failure(Exception(errMsg.ifBlank { "সার্ভার থেকে ত্রুটি এসেছে" }))
            } else {
                val msg = response.data?.enrollInFreeProgram?.message ?: response.message ?: "success"
                Result.success(msg)
            }
        } catch (e: retrofit2.HttpException) {
            val errorJson = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            if (errorJson != null && errorJson.contains("already enrolled", ignoreCase = true)) {
                Result.success("user already enrolled in this academic program")
            } else {
                val parsedMsg = try {
                    val obj = org.json.JSONObject(errorJson ?: "")
                    obj.optString("message", e.message())
                } catch (_: Exception) {
                    e.message()
                }
                Result.failure(Exception(parsedMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProgramPhases(programId: String): List<PhaseItem> {
        val phaseQuery = GraphQlQuery(
            operationName = "ProgramPhasesByStudent",
            query = """
                query ProgramPhasesByStudent(${'$'}program_id: String!) {
                  programPhasesByStudent(program_id: ${'$'}program_id) {
                    data {
                      id
                      academic_program_id
                      title
                      status
                      is_current
                      has_enrolment
                      has_free_trial_enrolment
                      course_progress_percentage
                      start_date
                      end_date
                      type
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("program_id" to programId)
        )
        val phaseRes = apiService.getProgramPhases(phaseQuery)
        return phaseRes.data?.programPhasesByStudent?.data ?: emptyList()
    }

    suspend fun getAcademicSubjects(programId: String, phaseId: String? = null): AcademicProgramDetail? {
        val query = if (!phaseId.isNullOrBlank()) {
            GraphQlQuery(
                operationName = "GetAcademicSubjects",
                query = """
                    query GetAcademicSubjects(${'$'}programId: String!, ${'$'}phase_id: String!) {
                      academicProgram(id: ${'$'}programId, show_subject_progress_bar: true, phase_id: ${'$'}phase_id) {
                        subjects {
                          code
                          color_code
                          display_bn
                          icon
                        }
                        subjects_progress_bar {
                          completed_chapters
                          total_chapters
                          code
                          percentage
                        }
                        trial_subject_list
                      }
                    }
                """.trimIndent(),
                variables = mapOf(
                    "programId" to programId,
                    "phase_id" to phaseId
                )
            )
        } else {
            GraphQlQuery(
                operationName = "GetAcademicSubjects",
                query = """
                    query GetAcademicSubjects(${'$'}programId: String!) {
                      academicProgram(id: ${'$'}programId, show_subject_progress_bar: true) {
                        subjects {
                          code
                          color_code
                          display_bn
                          icon
                        }
                        subjects_progress_bar {
                          completed_chapters
                          total_chapters
                          code
                          percentage
                        }
                        trial_subject_list
                      }
                    }
                """.trimIndent(),
                variables = mapOf("programId" to programId)
            )
        }
        val response = apiService.getAcademicSubjects(query)
        return response.data?.academicProgram
    }

    suspend fun getPhaseWiseChapters(programId: String, phaseId: String, subjectCode: String): List<AcademicChapterItem> {
        val chaptersQuery = GraphQlQuery(
            operationName = "PhaseWiseChapters",
            query = """
                query PhaseWiseChapters(${'$'}program_id: String!, ${'$'}phase_id: String!, ${'$'}subject_id: String!) {
                  listAcademicProgramChapters(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, subject_id: ${'$'}subject_id, show_chapter_progress_bar: true) {
                    data {
                      id
                      batch_id
                      chapter_id
                      chapter_name
                      chapter_no
                      program_id
                      status
                      subject_icon
                      class_counter
                      exam_counter
                      chapters_progress_percentage
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "program_id" to programId,
                "phase_id" to phaseId,
                "subject_id" to subjectCode
            )
        )
        val response = apiService.getPhaseWiseChapters(chaptersQuery)
        return response.data?.listAcademicProgramChapters?.data ?: emptyList()
    }

    suspend fun getChaptersBySubjectCode(subjectCode: String): List<AcademicChapterItem> {
        val getChaptersQuery = GraphQlQuery(
            operationName = "GetChapters",
            query = """
                query GetChapters(${'$'}subject_code: String!) {
                  chapters(subject_code: ${'$'}subject_code, filter: { limit: 100 } ) {
                    data {
                      id
                      name
                      no
                      all_topics_free
                      topics {
                        meta {
                          count
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("subject_code" to subjectCode)
        )
        val res = apiService.getPhaseWiseChapters(getChaptersQuery)
        return res.data?.chapters?.data ?: emptyList()
    }

    suspend fun getAcademicProgramChaptersFallback(programId: String, subjectCode: String): List<AcademicChapterItem> {
        val fallbackQuery = GraphQlQuery(
            operationName = "AcademicProgramChapters",
            query = """
                query AcademicProgramChapters(${'$'}program_id: String!, ${'$'}subject_id: String!) {
                  listAcademicProgramChapters(program_id: ${'$'}program_id, subject_id: ${'$'}subject_id, show_chapter_progress_bar: true) {
                    data {
                      id
                      chapter_id
                      chapter_name
                      chapter_no
                      status
                      class_counter
                      exam_counter
                      chapters_progress_percentage
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf(
                "program_id" to programId,
                "subject_id" to subjectCode
            )
        )
        val fallbackRes = apiService.getPhaseWiseChapters(fallbackQuery)
        return fallbackRes.data?.listAcademicProgramChapters?.data ?: emptyList()
    }

    suspend fun fetchFallbackHierarchyChapters(subjectCode: String, selectedSubjectTitle: String?): List<AcademicChapterItem> {
        try {
            val query = GraphQlQuery(
                operationName = "GetSubjectHierarchyWithQuestionCounts",
                query = """
                    query GetSubjectHierarchyWithQuestionCounts(${'$'}class: ClassEnum, ${'$'}group: StudyGroupTypeEnum) {
                      subjectHierarchyWithQuestionCounts(class: ${'$'}class, group: ${'$'}group) {
                        data {
                          chapters {
                            id
                            name
                            no
                            should_render
                            total_active_questions
                          }
                          should_render
                          display_bn
                          display
                          code
                          icon
                          color_code
                          total_active_questions
                        }
                      }
                    }
                """.trimIndent(),
                variables = mapOf("class" to "HSC", "group" to "Humanities")
            )
            val res = apiService.getSubjectHierarchyWithQuestionCounts(query)
            val subjectsData = res.data?.subjectHierarchyWithQuestionCounts?.data ?: emptyList()
            val matchedSubject = subjectsData.find { it.code == subjectCode }
                ?: subjectsData.find { it.display_bn?.trim() == selectedSubjectTitle?.trim() }

            return matchedSubject?.chapters?.filter { it.should_render != false }?.map { ch ->
                AcademicChapterItem(
                    id = ch.id,
                    chapter_id = ch.id,
                    chapter_name = ch.name,
                    chapter_no = ch.no,
                    status = "IN_PROGRESS"
                )
            } ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun fetchAllLessonsForProgram(
        programId: String,
        phaseId: String? = null,
        batchId: String? = null
    ): List<StudentLessonItem> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Dhaka"))
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startCal = (cal.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
        val endCal = (cal.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
        val startDate = dateFormat.format(startCal.time)
        val endDate = dateFormat.format(endCal.time)

        val fragment = """
            id
            title
            content_id
            content_type
            access_level
            start_time
            end_time
            subject_id
            subject_name
            batch_id
            chapter_id
            icon
            color_code
            user_activity_state
            live_class {
              id
              playback_url
              start_time
              end_time
            }
        """.trimIndent()

        val minimalFragment = """
            id
            title
            content_id
            content_type
            access_level
            start_time
            end_time
            subject_id
            subject_name
            batch_id
            chapter_id
            icon
            color_code
            user_activity_state
        """.trimIndent()

        suspend fun runQuery(
            opName: String,
            queryStr: (String) -> String,
            variables: Map<String, Any?>
        ): List<StudentLessonItem> {
            try {
                val q = GraphQlQuery(
                    operationName = opName,
                    query = queryStr(fragment),
                    variables = variables
                )
                val res = apiService.getStudentLessons(q)
                val data = res.data?.studentSpecificLessons?.data
                if (!data.isNullOrEmpty()) return data
            } catch (e: Exception) {
                val errBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                android.util.Log.w("CourseRepository", "$opName (rich) failed: ${e.message}, body: $errBody")
            }

            // Fallback to minimal fields
            try {
                val qMin = GraphQlQuery(
                    operationName = opName,
                    query = queryStr(minimalFragment),
                    variables = variables
                )
                val res = apiService.getStudentLessons(qMin)
                return res.data?.studentSpecificLessons?.data ?: emptyList()
            } catch (e: Exception) {
                val errBody = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                android.util.Log.w("CourseRepository", "$opName (minimal) failed: ${e.message}, body: $errBody")
            }
            return emptyList()
        }

        // 1. Primary fast query: Phase-wise (most programs in Shikho)
        if (!phaseId.isNullOrBlank()) {
            val listPhase = runQuery(
                opName = "GetUpcomingLessonsPhaseWise",
                queryStr = { f ->
                    """
                        query GetUpcomingLessonsPhaseWise(${'$'}program_id: String!, ${'$'}phase_id: String!) {
                          studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id) {
                            data { $f }
                          }
                        }
                    """.trimIndent()
                },
                variables = mapOf("program_id" to programId, "phase_id" to phaseId)
            )
            if (listPhase.isNotEmpty()) {
                LessonCacheManager.saveLessons(listPhase, programId = programId)
                return listPhase
            }
        }

        // 2. Batch-wise query if batchId available
        if (!batchId.isNullOrBlank()) {
            val listBatch = runQuery(
                opName = "GetStudentSpecificLessonsWithBatch",
                queryStr = { f ->
                    """
                        query GetStudentSpecificLessonsWithBatch(${'$'}program_id: String!, ${'$'}batch_id: String!) {
                          studentSpecificLessons(program_id: ${'$'}program_id, batch_id: ${'$'}batch_id) {
                            data { $f }
                          }
                        }
                    """.trimIndent()
                },
                variables = mapOf("program_id" to programId, "batch_id" to batchId)
            )
            if (listBatch.isNotEmpty()) {
                LessonCacheManager.saveLessons(listBatch, programId = programId)
                return listBatch
            }
        }

        // 3. Program-level general query
        val listAll = runQuery(
            opName = "GetUpcomingLessons",
            queryStr = { f ->
                """
                    query GetUpcomingLessons(${'$'}program_id: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id) {
                        data { $f }
                      }
                    }
                """.trimIndent()
            },
            variables = mapOf("program_id" to programId)
        )
        if (listAll.isNotEmpty()) {
            LessonCacheManager.saveLessons(listAll, programId = programId)
            return listAll
        }

        // 4. Date-range fallback query if earlier queries returned empty
        if (!phaseId.isNullOrBlank()) {
            val listPhaseDate = runQuery(
                opName = "GetStudentLessonsPhaseDate",
                queryStr = { f ->
                    """
                        query GetStudentLessonsPhaseDate(${'$'}program_id: String!, ${'$'}phase_id: String!, ${'$'}start_date: String!, ${'$'}end_date: String!) {
                          studentSpecificLessons(program_id: ${'$'}program_id, phase_id: ${'$'}phase_id, start_date: ${'$'}start_date, end_date: ${'$'}end_date) {
                            data { $f }
                          }
                        }
                    """.trimIndent()
                },
                variables = mapOf(
                    "program_id" to programId,
                    "phase_id" to phaseId,
                    "start_date" to startDate,
                    "end_date" to endDate
                )
            )
            if (listPhaseDate.isNotEmpty()) {
                LessonCacheManager.saveLessons(listPhaseDate, programId = programId)
                return listPhaseDate
            }
        }

        val listDateOnly = runQuery(
            opName = "GetStudentLessonsDateOnly",
            queryStr = { f ->
                """
                    query GetStudentLessonsDateOnly(${'$'}program_id: String!, ${'$'}start_date: String!, ${'$'}end_date: String!) {
                      studentSpecificLessons(program_id: ${'$'}program_id, start_date: ${'$'}start_date, end_date: ${'$'}end_date) {
                        data { $f }
                      }
                    }
                """.trimIndent()
            },
            variables = mapOf(
                "program_id" to programId,
                "start_date" to startDate,
                "end_date" to endDate
            )
        )
        if (listDateOnly.isNotEmpty()) {
            LessonCacheManager.saveLessons(listDateOnly, programId = programId)
            return listDateOnly
        }

        return emptyList()
    }

    suspend fun fetchLessonsWithPhase(
        chapterId: String,
        programId: String,
        phaseId: String,
        chapterName: String? = null,
        batchId: String? = null,
        subjectTitle: String? = null
    ): List<StudentLessonItem> {
        val allLessons = fetchAllLessonsForProgram(programId, phaseId, batchId)
        if (chapterId.isBlank() && chapterName.isNullOrBlank()) return allLessons
        return LessonCacheManager.filterLessons(allLessons, listOf(chapterId), chapterName, subjectTitle, programId = programId)
    }

    suspend fun fetchLessonsStandard(
        chapterId: String,
        programId: String,
        chapterName: String? = null,
        batchId: String? = null,
        subjectTitle: String? = null
    ): List<StudentLessonItem> {
        val allLessons = fetchAllLessonsForProgram(programId, null, batchId)
        if (chapterId.isBlank() && chapterName.isNullOrBlank()) return allLessons
        return LessonCacheManager.filterLessons(allLessons, listOf(chapterId), chapterName, subjectTitle, programId = programId)
    }

    suspend fun fetchChapterLessons(
        chapterId: String,
        altChapterId: String? = null,
        chapterName: String? = null,
        subjectTitle: String? = null,
        programId: String? = null,
        phaseId: String? = null,
        batchId: String? = null
    ): List<StudentLessonItem> {
        val collectedLessons = mutableListOf<StudentLessonItem>()

        // 1. Fetch scheduled or live lessons for this program / phase
        val chapterIdsToTry = listOfNotNull(chapterId.ifBlank { null }, altChapterId?.ifBlank { null }).distinct()
        if (!programId.isNullOrBlank()) {
            for (chId in chapterIdsToTry) {
                try {
                    val programLessons = if (!phaseId.isNullOrBlank()) {
                        fetchLessonsWithPhase(chId, programId, phaseId, chapterName, batchId, subjectTitle)
                    } else {
                        fetchLessonsStandard(chId, programId, chapterName, batchId, subjectTitle)
                    }
                    if (programLessons.isNotEmpty()) {
                        collectedLessons.addAll(programLessons)
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CourseRepository", "Error fetching program lessons for chapter $chId: ${e.message}")
                }
            }
        }

        // 2. Deduplicate
        val distinct = collectedLessons.distinctBy { it.id.ifBlank { "${it.content_id}_${it.title}" } }
        if (distinct.isNotEmpty()) {
            LessonCacheManager.saveLessons(distinct, programId = programId)
            return distinct
        }

        // 4. Try from local cache
        val cached = LessonCacheManager.findLessonsForChapter(listOfNotNull(chapterId, altChapterId), chapterName, subjectTitle, programId = programId)
        if (cached.isNotEmpty()) return cached

        return emptyList()
    }

    suspend fun getLiveClassDetails(liveClassId: String): AcademicProgramLiveClassItem? {
        if (liveClassId.isBlank()) return null

        // 1. Standard GetAcademicLiveClassDetails (exact schema from Shikho API)
        val standardQueryStr = """
            query GetAcademicLiveClassDetails(${'$'}id: String!) {
              academicProgramLiveClass(id: ${'$'}id) {
                batch_ids
                create_practice_mcq
                chapter {
                  id
                  name
                  no
                }
                class_type
                end_time
                id
                on_going
                playback_url
                start_time
                study_materials {
                  file_url
                  id
                  name
                }
                subject {
                  code
                  color_code
                  display
                  display_bn
                  icon
                }
                teacher {
                  bio
                  id
                  marketing_avatar
                  marketing_points
                  name
                  subjects
                  university_degree
                }
                title
                topics {
                  id
                  name
                }
              }
            }
        """.trimIndent()

        try {
            val query = GraphQlQuery(
                operationName = "GetAcademicLiveClassDetails",
                query = standardQueryStr,
                variables = mapOf("id" to liveClassId)
            )
            val res = apiService.getAcademicLiveClassDetails(query)
            android.util.Log.d("LectureDebug", "GetAcademicLiveClassDetails standard response: $res")
            if (!res.errors.isNullOrEmpty()) {
                android.util.Log.w("LectureDebug", "GetAcademicLiveClassDetails errors: ${res.errors}")
            }
            if (res.data?.academicProgramLiveClass != null) {
                return res.data.academicProgramLiveClass
            }
        } catch (e: Exception) {
            android.util.Log.w("LectureDebug", "GetAcademicLiveClassDetails standard failed: ${e.message}")
        }

        // 2. Minimal fallback with playback_url, study_materials and topics
        val minimalQueryStr = """
            query GetAcademicLiveClassDetails(${'$'}id: String!) {
              academicProgramLiveClass(id: ${'$'}id) {
                id
                title
                playback_url
                study_materials {
                  file_url
                  id
                  name
                }
                topics {
                  id
                  name
                }
              }
            }
        """.trimIndent()

        try {
            val query = GraphQlQuery(
                operationName = "GetAcademicLiveClassDetails",
                query = minimalQueryStr,
                variables = mapOf("id" to liveClassId)
            )
            val res = apiService.getAcademicLiveClassDetails(query)
            android.util.Log.d("LectureDebug", "GetAcademicLiveClassDetails minimal response: $res")
            if (res.data?.academicProgramLiveClass != null) {
                return res.data.academicProgramLiveClass
            }
        } catch (e: Exception) {
            android.util.Log.w("LectureDebug", "GetAcademicLiveClassDetails minimal failed: ${e.message}")
        }

        // 3. Ultra-minimal fallback with just id and playback_url (absolute baseline)
        val ultraMinimalQueryStr = """
            query GetAcademicLiveClassDetails(${'$'}id: String!) {
              academicProgramLiveClass(id: ${'$'}id) {
                id
                playback_url
              }
            }
        """.trimIndent()

        try {
            val query = GraphQlQuery(
                operationName = "GetAcademicLiveClassDetails",
                query = ultraMinimalQueryStr,
                variables = mapOf("id" to liveClassId)
            )
            val res = apiService.getAcademicLiveClassDetails(query)
            android.util.Log.d("LectureDebug", "GetAcademicLiveClassDetails ultra-minimal response: $res")
            if (res.data?.academicProgramLiveClass != null) {
                return res.data.academicProgramLiveClass
            }
        } catch (e: Exception) {
            android.util.Log.w("LectureDebug", "GetAcademicLiveClassDetails ultra-minimal failed: ${e.message}")
        }

        // 4. Try ID! type instead of String!
        val idTypeQueryStr = """
            query GetAcademicLiveClassDetails(${'$'}id: ID!) {
              academicProgramLiveClass(id: ${'$'}id) {
                id
                playback_url
                study_materials {
                  id
                  name
                  file_url
                }
              }
            }
        """.trimIndent()

        try {
            val query = GraphQlQuery(
                operationName = "GetAcademicLiveClassDetails",
                query = idTypeQueryStr,
                variables = mapOf("id" to liveClassId)
            )
            val res = apiService.getAcademicLiveClassDetails(query)
            android.util.Log.d("LectureDebug", "GetAcademicLiveClassDetails (ID! type) response: $res")
            if (res.data?.academicProgramLiveClass != null) {
                return res.data.academicProgramLiveClass
            }
        } catch (e: Exception) {
            android.util.Log.w("LectureDebug", "GetAcademicLiveClassDetails ID! type failed: ${e.message}")
        }

        return null
    }

    suspend fun getTeacherDetails(teacherId: String): TeacherItem? {
        val tQuery = GraphQlQuery(
            operationName = "GetTeacherDetails",
            query = """
                query GetTeacherDetails(${'$'}teacher_id: String!) {
                  teacher(teacher_id: ${'$'}teacher_id) {
                    id
                    first_name
                    last_name
                    avatar
                    marketing_avatar
                    university_degree
                    marketing_points
                    bio
                    cover_photo
                    subjects_taken {
                      code
                      icon
                      display_bn
                    }
                    color_code
                    teacher_experience
                    total_students_taught
                    consumed_video_hours
                  }
                }
            """.trimIndent(),
            variables = mapOf("teacher_id" to teacherId)
        )
        val tRes = apiService.getTeacherDetails(tQuery)
        return tRes.data?.teacher
    }

    suspend fun getTopics(chapterId: String, topicIds: List<String>? = null): List<TopicFullItem> {
        val topQuery = if (topicIds.isNullOrEmpty()) {
            GraphQlQuery(
                operationName = "GetTopics",
                query = """
                    query GetTopics(${'$'}chapter_id: String) {
                      topics(chapter_id: ${'$'}chapter_id, filter: { limit: 150 } ) {
                        data {
                          id
                          no
                          name
                          description
                          subscription_type
                          session {
                            progress
                          }
                          videos {
                            data {
                              id
                              playback_url
                              video_thumbnail_url
                              category
                            }
                          }
                          header {
                            chapter_id
                            chapter_name
                          }
                        }
                      }
                    }
                """.trimIndent(),
                variables = if (chapterId.isNotBlank()) mapOf("chapter_id" to chapterId) else emptyMap()
            )
        } else {
            val queryVars = mutableMapOf<String, Any>("topic_ids" to topicIds)
            if (chapterId.isNotBlank()) {
                queryVars["chapter_id"] = chapterId
            }
            GraphQlQuery(
                operationName = "GetTopics",
                query = """
                    query GetTopics(${'$'}chapter_id: String, ${'$'}topic_ids: [String]) {
                      topics(chapter_id: ${'$'}chapter_id, topic_ids: ${'$'}topic_ids, filter: { limit: 150 } ) {
                        data {
                          id
                          no
                          name
                          description
                          subscription_type
                          session {
                            progress
                          }
                          videos {
                            data {
                              id
                              playback_url
                              video_thumbnail_url
                              category
                            }
                          }
                          header {
                            chapter_id
                            chapter_name
                          }
                        }
                      }
                    }
                """.trimIndent(),
                variables = queryVars
            )
        }
        val topRes = apiService.getTopics(topQuery)
        return topRes.data?.topics?.data ?: emptyList()
    }
}
