package com.secondproject.datasortgallery.feature.recyclebin

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.secondproject.datasortgallery.core.media.MediaActionType
import com.secondproject.datasortgallery.core.media.MediaConsentRequest
import com.secondproject.datasortgallery.core.media.MediaOperationResult
import com.secondproject.datasortgallery.core.media.MediaStoreManager
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.repository.MediaRepository
import com.secondproject.datasortgallery.feature.gallery.MediaViewerScreen
import com.secondproject.datasortgallery.feature.videos.VideoPlayerScreen
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
class RecycleBinViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaStoreManager: MediaStoreManager
) : ViewModel() {

    companion object {
        private const val TAG = "RecycleBinVM"
    }

    val trashedMedia: StateFlow<List<MediaEntity>> = mediaRepository.getTrashedMedia()
        .catch { e ->
            Log.e(TAG, "Error collecting trashed media", e)
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

    private var queuedRestoreItems: List<MediaEntity> = emptyList()
    private var queuedDeleteItems: List<MediaEntity> = emptyList()

    fun restoreMediaItems(items: List<MediaEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val batch = items.take(MediaStoreManager.MEDIASTORE_BATCH_SIZE)
                queuedRestoreItems = items.drop(MediaStoreManager.MEDIASTORE_BATCH_SIZE)
                when (val result = mediaStoreManager.prepareTrashRequest(batch, false)) {
                    is MediaOperationResult.RequiresConsent -> {
                        _pendingConsent.value = result.request
                    }
                    is MediaOperationResult.Completed -> {
                        mediaRepository.toggleTrashBatch(result.targetUniqueIds, false)
                        if (queuedRestoreItems.isNotEmpty()) {
                            val next = queuedRestoreItems
                            queuedRestoreItems = emptyList()
                            restoreMediaItems(next)
                        } else {
                            _operationFeedback.value = "Restored ${result.targetUniqueIds.size} item(s)"
                        }
                    }
                    is MediaOperationResult.Error -> {
                        queuedRestoreItems = emptyList()
                        _operationFeedback.value = "Failed to restore: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring media", e)
                queuedRestoreItems = emptyList()
                _operationFeedback.value = "Error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun permanentDeleteItems(items: List<MediaEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val batch = items.take(MediaStoreManager.MEDIASTORE_BATCH_SIZE)
                queuedDeleteItems = items.drop(MediaStoreManager.MEDIASTORE_BATCH_SIZE)
                when (val result = mediaStoreManager.prepareDeleteRequest(batch)) {
                    is MediaOperationResult.RequiresConsent -> {
                        _pendingConsent.value = result.request
                    }
                    is MediaOperationResult.Completed -> {
                        mediaRepository.deleteMultipleMediaChunked(result.targetUniqueIds)
                        if (queuedDeleteItems.isNotEmpty()) {
                            val next = queuedDeleteItems
                            queuedDeleteItems = emptyList()
                            permanentDeleteItems(next)
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
                Log.e(TAG, "Error deleting media permanently", e)
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
                    MediaActionType.RESTORE -> {
                        mediaRepository.toggleTrashBatch(request.targetUniqueIds, false)
                        if (queuedRestoreItems.isNotEmpty()) {
                            val next = queuedRestoreItems
                            queuedRestoreItems = emptyList()
                            _operationFeedback.value = "Restored ${request.targetUniqueIds.size} items. Processing next batch..."
                            restoreMediaItems(next)
                        } else {
                            _operationFeedback.value = "Restored ${request.targetUniqueIds.size} item(s)"
                        }
                    }
                    MediaActionType.PERMANENT_DELETE -> {
                        mediaRepository.deleteMultipleMediaChunked(request.targetUniqueIds)
                        if (queuedDeleteItems.isNotEmpty()) {
                            val next = queuedDeleteItems
                            queuedDeleteItems = emptyList()
                            _operationFeedback.value = "Deleted ${request.targetUniqueIds.size} items. Processing next batch..."
                            permanentDeleteItems(next)
                        } else {
                            _operationFeedback.value = "Permanently deleted ${request.targetUniqueIds.size} item(s)"
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing confirmed action", e)
                _operationFeedback.value = "Error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun onConsentDenied() {
        queuedRestoreItems = emptyList()
        queuedDeleteItems = emptyList()
        _pendingConsent.value = null
        _operationFeedback.value = "Operation cancelled"
    }

    fun clearFeedback() {
        _operationFeedback.value = null
    }

    fun restoreMedia(uniqueId: String) {
        viewModelScope.launch {
            val item = mediaRepository.getMediaById(uniqueId)
            if (item != null) {
                restoreMediaItems(listOf(item))
            } else {
                mediaRepository.toggleTrash(uniqueId, false)
            }
        }
    }

    fun permanentDelete(uniqueId: String) {
        viewModelScope.launch {
            val item = mediaRepository.getMediaById(uniqueId)
            if (item != null) {
                permanentDeleteItems(listOf(item))
            } else {
                mediaRepository.deleteMedia(uniqueId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val trashedList by viewModel.trashedMedia.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    val feedback by viewModel.operationFeedback.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var itemToDeletePermanently by remember { mutableStateOf<MediaEntity?>(null) }
    var previewMediaUniqueId by rememberSaveable { mutableStateOf<String?>(null) }
    val previewMedia = remember(previewMediaUniqueId, trashedList) {
        previewMediaUniqueId?.let { id -> trashedList.find { it.uniqueId == id } }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentApproved()
        } else {
            viewModel.onConsentDenied()
        }
    }

    LaunchedEffect(pendingConsent) {
        pendingConsent?.let { request ->
            try {
                consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                viewModel.onConsentDenied()
            }
        }
    }

    LaunchedEffect(feedback) {
        feedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    if (previewMedia != null) {
        if (previewMedia!!.mediaType == com.secondproject.datasortgallery.data.local.entity.MediaType.VIDEO) {
            VideoPlayerScreen(
                videoList = listOf(previewMedia!!),
                initialIndex = 0,
                onBackClick = { previewMediaUniqueId = null }
            )
        } else {
            MediaViewerScreen(
                media = previewMedia!!,
                onBackClick = { previewMediaUniqueId = null },
                onFavoriteClick = { _, _ -> },
                onDeleteClick = { id -> viewModel.permanentDelete(id) }
            )
        }
        return
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Empty Recycle Bin") },
            text = { Text("Permanently delete all ${trashedList.size} items? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashDialog = false
                        viewModel.permanentDeleteItems(trashedList)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (itemToDeletePermanently != null) {
        AlertDialog(
            onDismissRequest = { itemToDeletePermanently = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Permanently Delete") },
            text = { Text("Permanently delete \"${itemToDeletePermanently?.filename}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val item = itemToDeletePermanently!!
                        itemToDeletePermanently = null
                        viewModel.permanentDeleteItems(listOf(item))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeletePermanently = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin (${trashedList.size})") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trashedList.isNotEmpty()) {
                        IconButton(
                            onClick = { showEmptyTrashDialog = true },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Recycle Bin")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (trashedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Recycle Bin is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deleted media items will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(trashedList, key = { it.uniqueId }) { media ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            AsyncImage(
                                model = media.contentUri,
                                contentDescription = media.filename,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable { previewMediaUniqueId = media.uniqueId }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.restoreMediaItems(listOf(media)) },
                                    enabled = !isProcessing
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = "Restore",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { itemToDeletePermanently = media },
                                    enabled = !isProcessing
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
