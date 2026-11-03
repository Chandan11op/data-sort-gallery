package com.secondproject.datasortgallery.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val mediaList: StateFlow<List<MediaEntity>> = mediaRepository.getAllMedia()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshMedia()
    }

    fun refreshMedia() {
        viewModelScope.launch {
            try {
                mediaRepository.scanAndSyncMedia()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            mediaRepository.toggleFavorite(id, isFavorite)
        }
    }

    fun deleteMedia(id: Long) {
        viewModelScope.launch {
            mediaRepository.toggleTrash(id, true)
        }
    }

    fun deleteMultipleMedia(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                mediaRepository.toggleTrash(id, true)
            }
        }
    }
}
