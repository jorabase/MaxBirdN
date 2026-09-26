package com.example.modeltest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ModelTestViewModelFactory(
    private val repository: ModelTestRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelTestViewModel::class.java)) {
            return ModelTestViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
