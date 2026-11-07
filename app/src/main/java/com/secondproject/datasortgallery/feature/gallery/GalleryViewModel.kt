package com.secondproject.datasortgallery.feature.gallery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondproject.datasortgallery.core.media.BulkOperationState
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

    private val _bulkOperationState = MutableStateFlow<BulkOperationState?>(null)
    val bulkOperationState: StateFlow<BulkOperationState?> = _bulkOperationState.asStateFlow()

    private var bulkQueue: MutableList<MediaEntity> = mutableListOf()
    private var bulkCurrentBatchItems: List<MediaEntity> = emptyList()
    private var bulkBatchSize: Int = 500

    private var queuedTrashItems: List<MediaEntity> = emptyList()
    private var queuedTrashTarget: Boolean = true
    private var queuedDeleteItems: List<MediaEntity> = emptyList()

    fun startBulkTrash(items: List<MediaEntity>, batchSize: Int = 500) {
        if (items.isEmpty()) return
        bulkBatchSize = batchSize
        val total = items.size
        val totalBytes = items.sumOf { it.size }
        val batches = (total + batchSize - 1) / batchSize

        bulkQueue = items.toMutableList()
        _bulkOperationState.value = BulkOperationState(
            operationType = MediaActionType.TRASH,
            totalFiles = total,
            totalBatches = batches,
            totalBytesToRecover = totalBytes,
            currentBatch = 1,
            isRunning = true,
            isCompleted = false,
            isCancelled = false
        )
        processNextBulkBatch()
    }

    fun startBulkPermanentDelete(items: List<MediaEntity>, batchSize: Int = 500) {
        if (items.isEmpty()) return
        bulkBatchSize = batchSize
        val total = items.size
        val totalBytes = items.sumOf { it.size }
        val batches = (total + batchSize - 1) / batchSize

        bulkQueue = items.toMutableList()
        _bulkOperationState.value = BulkOperationState(
            operationType = MediaActionType.PERMANENT_DELETE,
            totalFiles = total,
            totalBatches = batches,
            totalBytesToRecover = totalBytes,
            currentBatch = 1,
            isRunning = true,
            isCompleted = false,
            isCancelled = false
        )
        processNextBulkBatch()
    }

    private fun processNextBulkBatch() {
        val currentState = _bulkOperationState.value ?: return
        if (currentState.isCancelled) return

        if (bulkQueue.isEmpty()) {
            _bulkOperationState.value = currentState.copy(
                isRunning = false,
                isCompleted = true
            )
            refreshMedia()
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val batch = bulkQueue.take(bulkBatchSize)
                bulkQueue = bulkQueue.drop(bulkBatchSize).toMutableList()
                bulkCurrentBatchItems = batch

                val result = when (currentState.operationType) {
                    MediaActionType.TRASH -> mediaStoreManager.prepareTrashRequest(batch, true)
                    MediaActionType.PERMANENT_DELETE -> mediaStoreManager.prepareDeleteRequest(batch)
                    MediaActionType.RESTORE -> mediaStoreManager.prepareTrashRequest(batch, false)
                }

                when (result) {
                    is MediaOperationResult.RequiresConsent -> {
                        _pendingConsent.value = result.request
                    }
                    is MediaOperationResult.Completed -> {
                        if (currentState.operationType == MediaActionType.PERMANENT_DELETE) {
                            mediaRepository.deleteMultipleMediaChunked(result.targetUniqueIds)
                        } else {
                            mediaRepository.toggleTrashBatch(result.targetUniqueIds, currentState.operationType == MediaActionType.TRASH)
                        }
                        val newlyProcessed = result.targetUniqueIds.size
                        val newlyRecovered = batch.filter { result.targetUniqueIds.contains(it.uniqueId) }.sumOf { it.size }
                        val nextBatchNum = (currentState.currentBatch + 1).coerceAtMost(currentState.totalBatches)
                        _bulkOperationState.value = currentState.copy(
                            processedFiles = currentState.processedFiles + newlyProcessed,
                            recoveredBytes = currentState.recoveredBytes + newlyRecovered,
                            currentBatch = nextBatchNum
                        )
                        processNextBulkBatch()
                    }
                    is MediaOperationResult.Error -> {
                        val nextBatchNum = (currentState.currentBatch + 1).coerceAtMost(currentState.totalBatches)
                        _bulkOperationState.value = currentState.copy(
                            failedFiles = currentState.failedFiles + batch.size,
                            currentBatch = nextBatchNum,
                            errorMessage = result.message
                        )
                        processNextBulkBatch()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing bulk batch", e)
                _bulkOperationState.value = currentState.copy(
                    failedFiles = currentState.failedFiles + bulkCurrentBatchItems.size,
                    errorMessage = e.localizedMessage
                )
                processNextBulkBatch()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun cancelBulkOperation() {
        bulkQueue.clear()
        _pendingConsent.value = null
        _bulkOperationState.value = _bulkOperationState.value?.copy(
            isRunning = false,
            isCancelled = true,
            errorMessage = "Operation cancelled by user"
        )
    }

    fun dismissBulkOperation() {
        _bulkOperationState.value = null
    }

    fun trashMedia(items: List<MediaEntity>, trash: Boolean = true) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val batch = items.take(MediaStoreManager.MAX_BATCH_SIZE)
                queuedTrashItems = items.drop(MediaStoreManager.MAX_BATCH_SIZE)
                queuedTrashTarget = trash

                when (val result = mediaStoreManager.prepareTrashRequest(batch, trash)) {
                    is MediaOperationResult.RequiresConsent -> {
                        _pendingConsent.value = result.request
                    }
                    is MediaOperationResult.Completed -> {
                        mediaRepository.toggleTrashBatch(result.targetUniqueIds, trash)
                        if (queuedTrashItems.isNotEmpty()) {
                            val nextBatch = queuedTrashItems
                            queuedTrashItems = emptyList()
                            trashMedia(nextBatch, trash)
                        } else {
                            val actionDesc = if (trash) "Moved to Recycle Bin" else "Restored"
                            _operationFeedback.value = "$actionDesc ${result.targetUniqueIds.size} item(s)"
                        }
                    }
                    is MediaOperationResult.Error -> {
                        queuedTrashItems = emptyList()
                        _operationFeedback.value = "Failed: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in trashMedia", e)
                queuedTrashItems = emptyList()
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
                val batch = items.take(MediaStoreManager.MAX_BATCH_SIZE)
                queuedDeleteItems = items.drop(MediaStoreManager.MAX_BATCH_SIZE)

                when (val result = mediaStoreManager.prepareDeleteRequest(batch)) {
                    is MediaOperationResult.RequiresConsent -> {
                        _pendingConsent.value = result.request
                    }
                    is MediaOperationResult.Completed -> {
                        mediaRepository.deleteMultipleMediaChunked(result.targetUniqueIds)
                        if (queuedDeleteItems.isNotEmpty()) {
                            val nextBatch = queuedDeleteItems
                            queuedDeleteItems = emptyList()
                            deletePermanently(nextBatch)
                        } else {
                            _operationFeedback.value = "Permanently deleted ${result.targetUniqueIds.size} item(s)"
                        }
                    }
                    is MediaOperationResult.Error -> {
                        queuedDeleteItems = emptyList()
                        _operationFeedback.value = "Failed to delete: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in deletePermanently", e)
                queuedDeleteItems = emptyList()
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
                    }
                    MediaActionType.RESTORE -> {
                        mediaRepository.toggleTrashBatch(request.targetUniqueIds, false)
                    }
                    MediaActionType.PERMANENT_DELETE -> {
                        mediaRepository.deleteMultipleMediaChunked(request.targetUniqueIds)
                    }
                }

                val bulkState = _bulkOperationState.value
                if (bulkState != null && bulkState.isRunning) {
                    val newlyProcessed = request.targetUniqueIds.size
                    val newlyRecovered = bulkCurrentBatchItems.filter { request.targetUniqueIds.contains(it.uniqueId) }.sumOf { it.size }
                    val nextBatchNum = (bulkState.currentBatch + 1).coerceAtMost(bulkState.totalBatches)
                    _bulkOperationState.value = bulkState.copy(
                        processedFiles = bulkState.processedFiles + newlyProcessed,
                        recoveredBytes = bulkState.recoveredBytes + newlyRecovered,
                        currentBatch = nextBatchNum
                    )
                    processNextBulkBatch()
                } else if (queuedTrashItems.isNotEmpty()) {
                    val nextBatch = queuedTrashItems
                    queuedTrashItems = emptyList()
                    _operationFeedback.value = "Moved ${request.targetUniqueIds.size} items. Processing next batch..."
                    trashMedia(nextBatch, queuedTrashTarget)
                } else if (queuedDeleteItems.isNotEmpty()) {
                    val nextBatch = queuedDeleteItems
                    queuedDeleteItems = emptyList()
                    _operationFeedback.value = "Deleted ${request.targetUniqueIds.size} items. Processing next batch..."
                    deletePermanently(nextBatch)
                } else {
                    val actionName = when (request.actionType) {
                        MediaActionType.TRASH -> "Moved to Recycle Bin"
                        MediaActionType.RESTORE -> "Restored"
                        MediaActionType.PERMANENT_DELETE -> "Permanently deleted"
                    }
                    _operationFeedback.value = "$actionName ${request.targetUniqueIds.size} item(s)"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error confirming consent action", e)
                _operationFeedback.value = "Error finalizing action: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun onConsentDenied() {
        queuedTrashItems = emptyList()
        queuedDeleteItems = emptyList()
        _pendingConsent.value = null

        val bulkState = _bulkOperationState.value
        if (bulkState != null && bulkState.isRunning) {
            bulkQueue.clear()
            _bulkOperationState.value = bulkState.copy(
                isRunning = false,
                isCancelled = true,
                errorMessage = "Consent was denied by user for batch ${bulkState.currentBatch}."
            )
        } else {
            _operationFeedback.value = "Action cancelled"
        }
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
