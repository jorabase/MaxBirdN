package com.example.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.SavedItemDao
import com.example.database.SavedItemEntity
import com.example.database.SavedItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SavedItemFilter(val label: String, val typeKey: String?) {
    ALL("সব (All)", null),
    QUESTIONS("প্রশ্ন (Questions)", SavedItemEntity.TYPE_QUESTION),
    NOTES("নোট (Notes)", SavedItemEntity.TYPE_NOTE),
    CLASSES("ক্লাস (Classes)", SavedItemEntity.TYPE_CLASS),
    OTHERS("অন্যান্য (Others)", "OTHERS")
}

data class SavedUiState(
    val items: List<SavedItemEntity> = emptyList(),
    val currentFilter: SavedItemFilter = SavedItemFilter.ALL,
    val message: String? = null
)

class SavedViewModel(
    private val repository: SavedItemRepository
) : ViewModel() {

    private val _currentFilter = MutableStateFlow(SavedItemFilter.ALL)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SavedUiState> = combine(
        repository.allSavedItems,
        _currentFilter,
        _message
    ) { allItems, filter, msg ->
        val filteredList = when (filter) {
            SavedItemFilter.ALL -> allItems
            SavedItemFilter.QUESTIONS -> allItems.filter { it.type == SavedItemEntity.TYPE_QUESTION }
            SavedItemFilter.NOTES -> allItems.filter { it.type == SavedItemEntity.TYPE_NOTE }
            SavedItemFilter.CLASSES -> allItems.filter { it.type == SavedItemEntity.TYPE_CLASS }
            SavedItemFilter.OTHERS -> allItems.filter { 
                it.type != SavedItemEntity.TYPE_QUESTION && 
                it.type != SavedItemEntity.TYPE_NOTE && 
                it.type != SavedItemEntity.TYPE_CLASS 
            }
        }
        SavedUiState(
            items = filteredList,
            currentFilter = filter,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavedUiState()
    )

    fun setFilter(filter: SavedItemFilter) {
        _currentFilter.value = filter
    }

    fun saveItem(item: SavedItemEntity) {
        viewModelScope.launch {
            try {
                repository.saveItem(item)
                _message.value = "আইটেমটি সংরক্ষিত হয়েছে"
            } catch (e: Exception) {
                _message.value = "সংরক্ষণ করতে সমস্যা হয়েছে: ${e.message}"
            }
        }
    }

    fun deleteSavedItem(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteSavedItemById(id)
                _message.value = "আইটেমটি সংরক্ষণ থেকে মুছে ফেলা হয়েছে"
            } catch (e: Exception) {
                _message.value = "মুছতে সমস্যা হয়েছে: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

class SavedViewModelFactory(
    private val repository: SavedItemRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SavedViewModel::class.java)) {
            return SavedViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
