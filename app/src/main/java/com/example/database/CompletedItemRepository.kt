package com.example.database

import com.example.auth.SessionManager
import com.example.course.LessonCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompletedItemRepository(
    private val completedItemDao: CompletedItemDao,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _completedIdsState = MutableStateFlow<Set<String>>(emptySet())
    val completedIdsState: StateFlow<Set<String>> = _completedIdsState.asStateFlow()

    init {
        scope.launch {
            // Load initial session manager saved IDs into state
            val sessionIds = sessionManager.getCompletedLessonIds()
            val initialDbIds = try { completedItemDao.getCompletedItemIdsList() } catch (_: Exception) { emptyList() }
            val combined = (sessionIds + initialDbIds).filter { it.isNotBlank() }.toSet()
            _completedIdsState.value = combined

            // Observe room db changes reactively
            try {
                completedItemDao.getAllCompletedItemIds().collect { dbIds ->
                    val updatedSet = (_completedIdsState.value + dbIds + sessionManager.getCompletedLessonIds()).filter { it.isNotBlank() }.toSet()
                    _completedIdsState.value = updatedSet
                    // Sync every DB item into SessionManager and LessonCacheManager
                    updatedSet.forEach { id ->
                        sessionManager.markLessonCompleted(id)
                        LessonCacheManager.markLessonCompletedInCache(id)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CompletedItemRepo", "Error observing completed items: ${e.message}")
            }
        }
    }

    val allCompletedItems: Flow<List<CompletedItemEntity>> = completedItemDao.getAllCompletedItems()

    suspend fun markCompleted(
        itemId: String,
        itemType: String = "LESSON",
        title: String = "",
        subjectId: String = "",
        programId: String = "",
        chapterId: String = ""
    ) {
        if (itemId.isBlank()) return
        val entity = CompletedItemEntity(
            itemId = itemId,
            itemType = itemType,
            title = title,
            subjectId = subjectId,
            programId = programId,
            chapterId = chapterId,
            completedAt = System.currentTimeMillis()
        )
        try {
            completedItemDao.markCompleted(entity)
        } catch (e: Exception) {
            android.util.Log.e("CompletedItemRepo", "Error writing entity to Room DB: ${e.message}")
        }
        sessionManager.markLessonCompleted(itemId)
        LessonCacheManager.markLessonCompletedInCache(itemId)

        val currentSet = _completedIdsState.value.toMutableSet()
        currentSet.add(itemId)
        _completedIdsState.value = currentSet
    }

    suspend fun isItemCompleted(itemId: String): Boolean {
        if (itemId.isBlank()) return false
        if (_completedIdsState.value.contains(itemId)) return true
        if (sessionManager.isLessonCompleted(itemId)) return true
        return try { completedItemDao.isItemCompleted(itemId) } catch (_: Exception) { false }
    }

    fun isCompletedSync(itemId: String?): Boolean {
        if (itemId.isNullOrBlank()) return false
        return _completedIdsState.value.contains(itemId) || sessionManager.isLessonCompleted(itemId)
    }
}
