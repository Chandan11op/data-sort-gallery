package com.secondproject.datasortgallery.feature.gallery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondproject.datasortgallery.core.media.MediaActionType
import com.secondproject.datasortgallery.core.media.MediaConsentRequest
import com.secondproject.datasortgallery.core.media.MediaOperationResult
import com.secondproject.datasortgallery.core.media.MediaStoreManager
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaStoreManager: MediaStoreManager
) : ViewModel() {

    companion object {
        private const val TAG = "GalleryViewModel"
    }

    val mediaList: StateFlow<List<MediaEntity>> = mediaRepository.getAllMedia()
        .catch { e ->
            Log.e(TAG, "Error collecting media list", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _pendingConsent = MutableStateFlow<MediaConsentRequest?>(null)
    val pendingConsent: StateFlow<MediaConsentRequest?> = _pendingConsent.asStateFlow()

    private val _operationFeedback = MutableStateFlow<String?>(null)
    val operationFeedback: StateFlow<String?> = _operationFeedback.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        refreshMedia()
    }

    fun refreshMedia() {
        viewModelScope.launch {
            try {
                mediaRepository.scanAndSyncMedia()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan and sync media", e)
            }
        }
    }

    fun toggleFavorite(uniqueId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                mediaRepository.toggleFavorite(uniqueId, isFavorite)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle favorite", e)
            }
        }
    }

    fun trashMedia(items: List<MediaEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                when (val result = mediaStoreManager.prepareTrashRequest(items, true)) {
                    is MediaOperationResult.RequiresConsent -> {
                        _pendingConsent.value = result.request
                    }
                    is MediaOperationResult.Completed -> {
                        mediaRepository.toggleTrashBatch(result.targetUniqueIds, true)
                        _operationFeedback.value = "Moved ${result.targetUniqueIds.size} item(s) to Recycle Bin"
                    }
                    is MediaOperationResult.Error -> {
                        _operationFeedback.value = "Failed to move to trash: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in trashMedia", e)
                _operationFeedback.value = "Error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deletePermanently(items: List<MediaEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                when (val result = mediaStoreManager.prepareDeleteRequest(items)) {
                    is MediaOperationResult.RequiresConsent -> {
                        _pendingConsent.value = result.request
                    }
                    is MediaOperationResult.Completed -> {
                        mediaRepository.deleteMultipleMediaChunked(result.targetUniqueIds)
                        _operationFeedback.value = "Permanently deleted ${result.targetUniqueIds.size} item(s)"
                    }
                    is MediaOperationResult.Error -> {
                        _operationFeedback.value = "Failed to delete: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in deletePermanently", e)
                _operationFeedback.value = "Error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun onConsentApproved() {
        val request = _pendingConsent.value ?: return
        _pendingConsent.value = null
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                when (request.actionType) {
                    MediaActionType.TRASH -> {
                        mediaRepository.toggleTrashBatch(request.targetUniqueIds, true)
                        _operationFeedback.value = "Moved ${request.targetUniqueIds.size} item(s) to Recycle Bin"
                    }
                    MediaActionType.RESTORE -> {
                        mediaRepository.toggleTrashBatch(request.targetUniqueIds, false)
                        _operationFeedback.value = "Restored ${request.targetUniqueIds.size} item(s)"
                    }
                    MediaActionType.PERMANENT_DELETE -> {
                        mediaRepository.deleteMultipleMediaChunked(request.targetUniqueIds)
                        _operationFeedback.value = "Permanently deleted ${request.targetUniqueIds.size} item(s)"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error confirming consent action", e)
                _operationFeedback.value = "Error finalizing deletion: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun onConsentDenied() {
        _pendingConsent.value = null
        _operationFeedback.value = "Deletion cancelled"
    }

    fun clearFeedback() {
        _operationFeedback.value = null
    }

    fun deleteMedia(uniqueId: String) {
        viewModelScope.launch {
            val item = mediaRepository.getMediaById(uniqueId)
            if (item != null) {
                trashMedia(listOf(item))
            } else {
                mediaRepository.toggleTrash(uniqueId, true)
            }
        }
    }

    fun deleteMultipleMedia(uniqueIds: List<String>) {
        if (uniqueIds.isEmpty()) return
        viewModelScope.launch {
            val items = mediaRepository.getMediaByIds(uniqueIds)
            if (items.isNotEmpty()) {
                trashMedia(items)
            } else {
                mediaRepository.toggleTrashBatch(uniqueIds, true)
            }
        }
    }
}
