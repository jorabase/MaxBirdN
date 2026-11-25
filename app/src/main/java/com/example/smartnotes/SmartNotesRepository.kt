package com.example.smartnotes

import android.util.Log
import com.example.api.*
import com.example.course.CourseRepository
import java.util.UUID

class SmartNotesRepository(
    private val apiService: ShikhoApiService,
    private val courseRepository: CourseRepository = CourseRepository(apiService)
) {

    companion object {
        const val QUERY_LIST_TAGGABLE_RESOURCES = """
query ListTaggableResources(
  ${'$'}is_chapter_resource: Boolean, 
  ${'$'}is_subject_resource: Boolean, 
  ${'$'}subject_id: String, 
  ${'$'}chapter_id: String, 
  ${'$'}phase_id: String
) {
  listTaggableResourceType(
    is_chapter_resource: ${'$'}is_chapter_resource, 
    is_subject_resource: ${'$'}is_subject_resource, 
    subject_id: ${'$'}subject_id, 
    chapter_id: ${'$'}chapter_id, 
    phase_id: ${'$'}phase_id
  ) {
    data {
      id
      title
      icon_url
      is_chapter_resource
      is_subject_resource
    }
  }
}
"""

        const val QUERY_RESOURCE_ATTACHMENTS_OF_CHAPTER = """
query ResourceAttachmentsOfChapter(
  ${'$'}subject_id: String!, 
  ${'$'}module_id: String!, 
  ${'$'}phase_id: String, 
  ${'$'}module_name: AttachmentModuleEnum!, 
  ${'$'}chapter_ids: [String]!, 
  ${'$'}resource_type_tag_ids: [String], 
  ${'$'}is_subject_specific: Boolean
) {
  attachmentList(
    module_id: ${'$'}module_id, 
    subject_id: ${'$'}subject_id, 
    phase_id: ${'$'}phase_id, 
    module_name: ${'$'}module_name, 
    chapter_ids: ${'$'}chapter_ids, 
    resource_type_tag_ids: ${'$'}resource_type_tag_ids, 
    is_subject_specific: ${'$'}is_subject_specific
  ) {
    data {
      id
      description
      title
      url
    }
  }
}
"""
    }

    suspend fun listSubjectTaggableResources(
        subjectId: String,
        phaseId: String?
    ): List<TaggableResourceItem> {
        val variables = mutableMapOf<String, Any?>(
            "is_subject_resource" to true,
            "subject_id" to subjectId
        )
        if (!phaseId.isNullOrBlank()) {
            variables["phase_id"] = phaseId
        }

        val query = GraphQlQuery(
            operationName = "ListTaggableResources",
            query = QUERY_LIST_TAGGABLE_RESOURCES.trimIndent(),
            variables = variables
        )

        try {
            val response = apiService.listTaggableResources(query)
            val list = response.data?.listTaggableResourceType?.data
            if (!list.isNullOrEmpty()) return list
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching subject taggable resources: ${e.message}", e)
        }

        // Fallback without phase_id
        if (!phaseId.isNullOrBlank()) {
            val fallbackVars = mapOf<String, Any?>(
                "is_subject_resource" to true,
                "subject_id" to subjectId
            )
            try {
                val res = apiService.listTaggableResources(
                    GraphQlQuery("ListTaggableResources", QUERY_LIST_TAGGABLE_RESOURCES.trimIndent(), fallbackVars)
                )
                val list = res.data?.listTaggableResourceType?.data
                if (!list.isNullOrEmpty()) return list
            } catch (e: Exception) {
                Log.e("SmartNotesRepo", "Error fetching subject fallback: ${e.message}")
            }
        }

        return emptyList()
    }

    suspend fun listChapterTaggableResources(
        chapterId: String,
        phaseId: String?,
        subjectId: String? = null,
        altChapterId: String? = null
    ): List<TaggableResourceItem> {
        val candidateChapterIds = listOfNotNull(
            chapterId.takeIf { it.isNotBlank() },
            altChapterId?.takeIf { it.isNotBlank() && it != chapterId }
        )

        for (chId in candidateChapterIds) {
            // Attempt 1: with subject_id, chapter_id, phase_id
            val v1 = mutableMapOf<String, Any?>(
                "is_chapter_resource" to true,
                "chapter_id" to chId
            )
            if (!subjectId.isNullOrBlank()) v1["subject_id"] = subjectId
            if (!phaseId.isNullOrBlank()) v1["phase_id"] = phaseId

            try {
                val res1 = apiService.listTaggableResources(
                    GraphQlQuery("ListTaggableResources", QUERY_LIST_TAGGABLE_RESOURCES.trimIndent(), v1)
                )
                val data1 = res1.data?.listTaggableResourceType?.data
                if (!data1.isNullOrEmpty()) return data1
            } catch (e: Exception) {
                Log.e("SmartNotesRepo", "Error query chapter tags v1: ${e.message}")
            }

            // Attempt 2: without phase_id
            if (!phaseId.isNullOrBlank()) {
                val v2 = mutableMapOf<String, Any?>(
                    "is_chapter_resource" to true,
                    "chapter_id" to chId
                )
                if (!subjectId.isNullOrBlank()) v2["subject_id"] = subjectId

                try {
                    val res2 = apiService.listTaggableResources(
                        GraphQlQuery("ListTaggableResources", QUERY_LIST_TAGGABLE_RESOURCES.trimIndent(), v2)
                    )
                    val data2 = res2.data?.listTaggableResourceType?.data
                    if (!data2.isNullOrEmpty()) return data2
                } catch (e: Exception) {
                    Log.e("SmartNotesRepo", "Error query chapter tags v2: ${e.message}")
                }
            }

            // Attempt 3: without subject_id
            val v3 = mutableMapOf<String, Any?>(
                "is_chapter_resource" to true,
                "chapter_id" to chId
            )
            try {
                val res3 = apiService.listTaggableResources(
                    GraphQlQuery("ListTaggableResources", QUERY_LIST_TAGGABLE_RESOURCES.trimIndent(), v3)
                )
                val data3 = res3.data?.listTaggableResourceType?.data
                if (!data3.isNullOrEmpty()) return data3
            } catch (e: Exception) {
                Log.e("SmartNotesRepo", "Error query chapter tags v3: ${e.message}")
            }
        }

        return emptyList()
    }

    suspend fun getResourceAttachmentsOfChapter(
        subjectId: String,
        moduleId: String,
        phaseId: String?,
        chapterIds: List<String>,
        resourceTypeTagIds: List<String>?,
        isSubjectSpecific: Boolean
    ): List<AttachmentDataItem> {
        val nonBlankChapterIds = chapterIds.filter { it.isNotBlank() }
        val actualSubjectId = if (isSubjectSpecific) subjectId else ""

        val variables = mutableMapOf<String, Any?>(
            "subject_id" to actualSubjectId,
            "module_id" to moduleId,
            "module_name" to "AcademicProgram",
            "chapter_ids" to nonBlankChapterIds,
            "resource_type_tag_ids" to (resourceTypeTagIds ?: emptyList<String>()),
            "is_subject_specific" to isSubjectSpecific
        )
        if (!phaseId.isNullOrBlank()) {
            variables["phase_id"] = phaseId
        }

        val query = GraphQlQuery(
            operationName = "ResourceAttachmentsOfChapter",
            query = QUERY_RESOURCE_ATTACHMENTS_OF_CHAPTER.trimIndent(),
            variables = variables
        )

        try {
            val response = apiService.getResourceAttachmentsOfChapter(query)
            val list = response.data?.attachmentList?.data
            if (!list.isNullOrEmpty()) return list
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching resource attachments: ${e.message}", e)
        }

        // Fallback without phase_id
        if (!phaseId.isNullOrBlank()) {
            val fallbackVars = mutableMapOf<String, Any?>(
                "subject_id" to actualSubjectId,
                "module_id" to moduleId,
                "module_name" to "AcademicProgram",
                "chapter_ids" to nonBlankChapterIds,
                "resource_type_tag_ids" to (resourceTypeTagIds ?: emptyList<String>()),
                "is_subject_specific" to isSubjectSpecific
            )
            try {
                val fbRes = apiService.getResourceAttachmentsOfChapter(
                    GraphQlQuery("ResourceAttachmentsOfChapter", QUERY_RESOURCE_ATTACHMENTS_OF_CHAPTER.trimIndent(), fallbackVars)
                )
                val list = fbRes.data?.attachmentList?.data
                if (!list.isNullOrEmpty()) return list
            } catch (e: Exception) {
                Log.e("SmartNotesRepo", "Error fetching fallback attachments: ${e.message}")
            }
        }

        return emptyList()
    }

    suspend fun getChapterLessonAttachments(
        programId: String,
        phaseId: String?,
        chapterId: String,
        altChapterId: String? = null
    ): List<AttachmentDataItem> {
        return try {
            val allLessons = courseRepository.fetchAllLessonsForProgram(programId, phaseId)
            val chapterLessons = allLessons.filter { lesson ->
                lesson.chapter_id == chapterId ||
                (!altChapterId.isNullOrBlank() && lesson.chapter_id == altChapterId)
            }
            val attachmentsList = mutableListOf<AttachmentDataItem>()
            for (lesson in chapterLessons) {
                val slideUrl: String? = lesson.resolvedSlideUrl
                if (!slideUrl.isNullOrBlank()) {
                    attachmentsList.add(
                        AttachmentDataItem(
                            id = lesson.id.ifBlank { UUID.randomUUID().toString() },
                            title = lesson.title ?: com.example.utils.AppLang.tr("Lecture Slide & Notes", "লেকচার স্লাইড ও নোট"),
                            description = com.example.utils.AppLang.tr("Class notes & lecture slides", "ক্লাস নোট ও লেকচার স্লাইড"),
                            url = slideUrl
                        )
                    )
                }
                lesson.allAttachments.forEach { att ->
                    val dlUrl = att.downloadUrl
                    if (!dlUrl.isNullOrBlank()) {
                        attachmentsList.add(
                            AttachmentDataItem(
                                id = att.id ?: UUID.randomUUID().toString(),
                                title = att.displayTitle.ifBlank { lesson.title ?: com.example.utils.AppLang.tr("Lecture Sheet", "লেকচার শিট") },
                                description = com.example.utils.AppLang.tr("Lecture resource", "লেকচার রিসোর্স"),
                                url = dlUrl
                            )
                        )
                    }
                }
            }
            attachmentsList.distinctBy { it.url }
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching chapter lesson attachments: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSubjectChapters(
        programId: String,
        phaseId: String?,
        subjectCode: String,
        subjectTitle: String = ""
    ): List<AcademicChapterItem> {
        return try {
            if (!phaseId.isNullOrBlank() && programId.isNotBlank()) {
                val list = courseRepository.getPhaseWiseChapters(programId, phaseId, subjectCode)
                if (list.isNotEmpty()) return list
            }
            if (programId.isNotBlank()) {
                val list = courseRepository.getAcademicProgramChaptersFallback(programId, subjectCode)
                if (list.isNotEmpty()) return list
            }
            val byCode = courseRepository.getChaptersBySubjectCode(subjectCode)
            if (byCode.isNotEmpty()) return byCode
            courseRepository.fetchFallbackHierarchyChapters(subjectCode, subjectTitle)
        } catch (e: Exception) {
            Log.e("SmartNotesRepo", "Error fetching subject chapters: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun filterResourcesWithPdf(
        subjectCode: String,
        phaseId: String?,
        programId: String,
        chapterIds: List<String>?,
        isSubjectSpecific: Boolean,
        resources: List<TaggableResourceItem>
    ): List<TaggableResourceItem> {
        if (resources.isEmpty()) return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            resources.mapNotNull { res ->
                val tagId = res.id ?: return@mapNotNull null
                try {
                    val attachments = getResourceAttachmentsOfChapter(
                        subjectId = subjectCode,
                        moduleId = programId,
                        phaseId = phaseId,
                        chapterIds = chapterIds ?: emptyList(),
                        resourceTypeTagIds = listOf(tagId),
                        isSubjectSpecific = isSubjectSpecific
                    )
                    if (attachments.any { !it.url.isNullOrBlank() }) res else null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
