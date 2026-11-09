package com.example.course

import com.example.api.StudentLessonItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton in-memory cache for all academic and routine lessons.
 * Allows instant lookup of lessons across Home, Routine, and Course Chapter views.
 */
object LessonCacheManager {
    private val allLessons = ConcurrentHashMap<String, StudentLessonItem>()

    fun markLessonCompletedInCache(lessonId: String) {
        if (lessonId.isBlank()) return
        allLessons.forEach { (key, item) ->
            if (item.id == lessonId || item.content_id == lessonId || item.live_class?.id == lessonId) {
                allLessons[key] = item.copy(user_activity_state = "COMPLETED")
            }
        }
    }

    fun enrichWithCompletedState(lessons: List<StudentLessonItem>, completedIds: Set<String>): List<StudentLessonItem> {
        if (completedIds.isEmpty()) return lessons
        return lessons.map { item ->
            val isComp = completedIds.contains(item.id) || 
                         completedIds.contains(item.content_id) || 
                         completedIds.contains(item.live_class?.id) ||
                         item.user_activity_state.equals("COMPLETED", ignoreCase = true) ||
                         item.user_activity_state.equals("ATTENDED", ignoreCase = true)
            if (isComp && item.user_activity_state != "COMPLETED") {
                item.copy(user_activity_state = "COMPLETED")
            } else item
        }
    }

    fun saveLessons(lessons: List<StudentLessonItem>, programId: String? = null) {
        if (lessons.isEmpty()) return
        val cleanProg = programId?.trim()?.takeIf { it.isNotBlank() }
        lessons.forEach { item ->
            val updatedItem = if (cleanProg != null && item.program_id.isNullOrBlank()) {
                item.copy(program_id = cleanProg)
            } else item
            val key = updatedItem.id.ifBlank { "${updatedItem.content_id}_${updatedItem.start_time}_${updatedItem.title}" }
            if (key.isNotBlank()) {
                val existing = allLessons[key]
                allLessons[key] = if (existing != null) {
                    updatedItem.copy(
                        title = updatedItem.title?.takeIf { it.isNotBlank() } ?: existing.title,
                        content_id = updatedItem.content_id?.takeIf { it.isNotBlank() } ?: existing.content_id,
                        content_type = updatedItem.content_type?.takeIf { it.isNotBlank() } ?: existing.content_type,
                        hw_type = updatedItem.hw_type?.takeIf { it.isNotBlank() } ?: existing.hw_type,
                        chapter_id = updatedItem.chapter_id?.takeIf { it.isNotBlank() } ?: existing.chapter_id,
                        subject_id = updatedItem.subject_id?.takeIf { it.isNotBlank() } ?: existing.subject_id,
                        subject_name = updatedItem.subject_name?.takeIf { it.isNotBlank() } ?: existing.subject_name,
                        start_time = updatedItem.start_time?.takeIf { it.isNotBlank() } ?: existing.start_time,
                        end_time = updatedItem.end_time?.takeIf { it.isNotBlank() } ?: existing.end_time,
                        live_class = updatedItem.live_class ?: existing.live_class,
                        model_test = updatedItem.model_test ?: existing.model_test,
                        topics = updatedItem.topics?.takeIf { it.isNotEmpty() } ?: existing.topics,
                        phase_id = updatedItem.phase_id?.takeIf { it.isNotBlank() } ?: existing.phase_id,
                        program_id = updatedItem.program_id?.takeIf { it.isNotBlank() } ?: existing.program_id,
                        user_activity_state = if (existing.user_activity_state.equals("COMPLETED", ignoreCase = true)) {
                            existing.user_activity_state
                        } else {
                            updatedItem.user_activity_state?.takeIf { it.isNotBlank() } ?: existing.user_activity_state
                        }
                    )
                } else {
                    updatedItem
                }
            }
        }
    }

    fun getLessonById(id: String): StudentLessonItem? {
        val clean = id.trim()
        if (clean.isBlank()) return null
        allLessons[clean]?.let { return it }
        return allLessons.values.firstOrNull {
            it.id == clean || it.content_id == clean || it.session_id == clean || it.live_class?.id == clean
        }
    }

    fun getAllLessons(): List<StudentLessonItem> {
        return allLessons.values.toList()
    }

    fun findLessonsForChapter(
        candidateChapterIds: List<String>,
        chapterName: String?,
        subjectTitle: String? = null,
        otherChapterIds: List<String> = emptyList(),
        otherChapterNames: List<String> = emptyList(),
        chapterNo: String? = null,
        programId: String? = null
    ): List<StudentLessonItem> {
        return filterLessons(
            lessons = allLessons.values.toList(),
            candidateChapterIds = candidateChapterIds,
            chapterName = chapterName,
            subjectTitle = subjectTitle,
            otherChapterIds = otherChapterIds,
            otherChapterNames = otherChapterNames,
            chapterNo = chapterNo,
            programId = programId
        )
    }

    fun normalizeBengaliText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace("\u09AF\u09BC", "\u09DF") // য় -> য়
            .replace("\u09A1\u09BC", "\u09DC") // ড় -> ড়
            .replace("\u09A2\u09BC", "\u09DD") // ঢ় -> ঢ়
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun cleanChapterTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val normalized = normalizeBengaliText(title)
        val withoutPrefixes = normalized.replace(
            Regex("^(?:অধ্যায়|অধ্যায়|চ্যাপ্টার|Chapter|Ch|পর্ব|পার্ট|[০-৯0-9]+(?:ম|য়|র্থ|ষ্ঠ|তম)?)\\s*([০-৯0-9]+)?[:\\.\\-\\s]*", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        val withoutSuffixes = withoutPrefixes
            .replace(Regex("\\s*\\((?:Quiz|Animated)\\s*HW\\)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        return if (withoutSuffixes.length >= 2) withoutSuffixes else normalized
    }

    private fun extractLessonTitleChapterPrefix(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val normalized = normalizeBengaliText(title)
        val withoutHw = normalized
            .replace(Regex("\\s*\\((?:Quiz|Animated)\\s*HW\\)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        val beforeColon = if (withoutHw.contains(":")) {
            val parts = withoutHw.split(":")
            val first = parts.firstOrNull()?.trim() ?: ""
            // Skip prefixes like "MCQ Solving Class:" or "Lecture 1:"
            if (first.contains("Class", ignoreCase = true) ||
                first.contains("Lecture", ignoreCase = true) ||
                first.contains("ক্লাস", ignoreCase = true) ||
                first.contains("লেকচার", ignoreCase = true)
            ) {
                parts.drop(1).joinToString(":").trim()
            } else {
                first
            }
        } else {
            withoutHw
        }
        return cleanChapterTitle(beforeColon)
    }

    fun filterLessons(
        lessons: List<StudentLessonItem>,
        candidateChapterIds: List<String>,
        chapterName: String?,
        subjectTitle: String? = null,
        otherChapterIds: List<String> = emptyList(),
        otherChapterNames: List<String> = emptyList(),
        chapterNo: String? = null,
        programId: String? = null
    ): List<StudentLessonItem> {
        val cleanCandidateIds = candidateChapterIds.map { it.trim() }.filter { it.isNotBlank() }
        val cleanOtherIds = otherChapterIds.map { it.trim() }.filter { it.isNotBlank() && !cleanCandidateIds.contains(it) }

        val rawName = normalizeBengaliText(chapterName)
        val cleanName = cleanChapterTitle(rawName)

        val cleanOtherNames = otherChapterNames
            .map { cleanChapterTitle(it) }
            .filter { it.isNotBlank() && !it.equals(cleanName, ignoreCase = true) }

        val cleanSubject = normalizeBengaliText(subjectTitle)
            .replace("পত্র", "")
            .replace("১ম", "")
            .replace("২য়", "")
            .replace("1st", "", ignoreCase = true)
            .replace("2nd", "", ignoreCase = true)
            .replace("Paper", "", ignoreCase = true)
            .trim()
        val cleanProgramId = programId?.trim()?.takeIf { it.isNotBlank() }

        fun isSameId(id1: String, id2: String): Boolean {
            if (id1.equals(id2, ignoreCase = true)) return true
            val d1 = id1.replace(Regex("[^0-9]"), "")
            val d2 = id2.replace(Regex("[^0-9]"), "")
            return d1.isNotBlank() && d2.isNotBlank() && d1 == d2
        }

        val matched = lessons.filter { item ->
            // CRITICAL PROGRAM/COURSE ISOLATION CHECK:
            val itemProgId = item.program_id?.trim()?.takeIf { it.isNotBlank() }
            if (cleanProgramId != null && itemProgId != null) {
                if (!isSameId(itemProgId, cleanProgramId)) {
                    return@filter false
                }
            }
            val lTitle = normalizeBengaliText(item.title)
            val lLiveClass = item.live_class
            val lChapterName = normalizeBengaliText(lLiveClass?.chapter_name)
            val lCleanChapterName = cleanChapterTitle(lChapterName)
            val lSubject = normalizeBengaliText(item.subject_name ?: lLiveClass?.subject_name)

            // Collect all chapter IDs associated with this lesson
            val lessonChapterIds = listOfNotNull(
                item.chapter_id?.trim()?.takeIf { it.isNotBlank() },
                lLiveClass?.chapter_id?.trim()?.takeIf { it.isNotBlank() }
            ).distinct()

            // 1. Direct positive chapter ID match
            val isDirectIdMatch = lessonChapterIds.any { id ->
                cleanCandidateIds.any { cid -> isSameId(id, cid) }
            }
            if (isDirectIdMatch) {
                return@filter true
            }

            // 2. Direct negative chapter ID check against other known chapter IDs
            val belongsToOtherChapter = lessonChapterIds.any { id ->
                cleanOtherIds.any { otherId -> isSameId(id, otherId) }
            }
            if (belongsToOtherChapter) {
                return@filter false
            }

            // If both candidate IDs and lesson chapter IDs exist and didn't match,
            // only allow fallback to exact chapter name if the lesson is an Exam/Quiz whose chapter_id
            // might use the master chapter ID namespace while only one candidate ID was passed.
            if (cleanCandidateIds.isNotEmpty() && lessonChapterIds.isNotEmpty() && !item.isExam) {
                return@filter false
            }

            // Subject relevance check
            val subjectMatch = cleanSubject.isBlank() || lSubject.isBlank() ||
                lSubject.contains(cleanSubject, ignoreCase = true) ||
                cleanSubject.contains(lSubject, ignoreCase = true)

            if (!subjectMatch) {
                return@filter false
            }

            // 3. Exact or clean chapter name matching
            var matchesThisChapterName = false
            if (lCleanChapterName.isNotBlank() && cleanName.isNotBlank()) {
                matchesThisChapterName = lCleanChapterName.equals(cleanName, ignoreCase = true) ||
                    (cleanName.length >= 4 && lCleanChapterName.contains(cleanName, ignoreCase = true)) ||
                    (lCleanChapterName.length >= 4 && cleanName.contains(lCleanChapterName, ignoreCase = true))
            } else if (lChapterName.isNotBlank() && rawName.isNotBlank()) {
                matchesThisChapterName = lChapterName.equals(rawName, ignoreCase = true) ||
                    (rawName.length >= 4 && lChapterName.contains(rawName, ignoreCase = true)) ||
                    (lChapterName.length >= 4 && rawName.contains(lChapterName, ignoreCase = true))
            }

            // 4. Negative chapter name check
            val matchesOtherChapterName = cleanOtherNames.any { otherName ->
                otherName.length >= 4 && (
                    lCleanChapterName.contains(otherName, ignoreCase = true) ||
                    otherName.contains(lCleanChapterName, ignoreCase = true)
                )
            }
            if (matchesOtherChapterName && !matchesThisChapterName) {
                return@filter false
            }

            if (matchesThisChapterName) {
                return@filter true
            }

            // 5. Title prefix / chapter name matching for LiveExam and Quiz HW items
            if (cleanName.isNotBlank() && lCleanChapterName.isBlank() && lChapterName.isBlank()) {
                val titlePrefix = extractLessonTitleChapterPrefix(lTitle)
                if (titlePrefix.isNotBlank() && titlePrefix.equals(cleanName, ignoreCase = true)) {
                    return@filter true
                }
                if (cleanName.length >= 4 && (
                        titlePrefix.contains(cleanName, ignoreCase = true) ||
                        lTitle.contains(cleanName, ignoreCase = true)
                    )
                ) {
                    val matchesOtherTitle = cleanOtherNames.any { otherName ->
                        otherName.length >= 4 && lTitle.contains(otherName, ignoreCase = true)
                    }
                    if (!matchesOtherTitle) {
                        return@filter true
                    }
                }
            }

            false
        }

        return matched
            .distinctBy { it.id.ifBlank { "${it.content_id}_${it.start_time}_${it.title}" } }
            .sortedBy { it.start_time ?: "" }
    }
}
