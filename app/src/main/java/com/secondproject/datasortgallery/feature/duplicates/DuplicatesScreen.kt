package com.secondproject.datasortgallery.feature.duplicates

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.secondproject.datasortgallery.core.designsystem.components.BulkOperationProgressDialog
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import com.secondproject.datasortgallery.feature.gallery.GalleryViewModel
import com.secondproject.datasortgallery.feature.gallery.MediaViewerScreen
import com.secondproject.datasortgallery.feature.videos.VideoPlayerScreen
import java.util.Locale

data class DuplicateGroup(
    val key: String,
    val items: List<MediaEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val mediaList by viewModel.mediaList.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    val feedback by viewModel.operationFeedback.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val bulkState by viewModel.bulkOperationState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCleanAllConfirmation by remember { mutableStateOf(false) }
    var itemToTrash by remember { mutableStateOf<MediaEntity?>(null) }
    var previewMedia by remember { mutableStateOf<MediaEntity?>(null) }

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

    // Group items safely, ignoring empty or invalid files
    val duplicateGroups = remember(mediaList) {
        mediaList.filter { it.size > 0 }
            .groupBy { media ->
                if (!media.sha256Hash.isNullOrBlank()) {
                    "hash_${media.sha256Hash}"
                } else if (media.resolution.isNotBlank()) {
                    "size_res_${media.size}_${media.resolution}"
                } else {
                    "size_name_${media.size}_${media.filename}"
                }
            }
            .filter { it.value.size > 1 }
            .map { (key, items) -> DuplicateGroup(key, items) }
    }

    val totalDuplicatesCount = remember(duplicateGroups) {
        duplicateGroups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
    }

    val totalRecoverableBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { group ->
            group.items.drop(1).sumOf { it.size }
        }
    }

    val totalRecoverableFormatted = remember(totalRecoverableBytes) {
        if (totalRecoverableBytes >= 1024L * 1024L * 1024L) {
            String.format(Locale.US, "%.2f GB", totalRecoverableBytes / (1024.0 * 1024.0 * 1024.0))
        } else {
            String.format(Locale.US, "%.1f MB", totalRecoverableBytes / (1024.0 * 1024.0))
        }
    }

    // Media Preview Overlay (Video Player or Image Viewer)
    if (previewMedia != null) {
        if (previewMedia!!.mediaType == MediaType.VIDEO) {
            VideoPlayerScreen(
                videoList = listOf(previewMedia!!),
                initialIndex = 0,
                onBackClick = { previewMedia = null },
                onFavoriteClick = { id, isFav -> viewModel.toggleFavorite(id, isFav) },
                onDeleteClick = { id -> viewModel.deleteMedia(id) }
            )
        } else {
            MediaViewerScreen(
                media = previewMedia!!,
                onBackClick = { previewMedia = null },
                onFavoriteClick = { id, isFav -> viewModel.toggleFavorite(id, isFav) },
                onDeleteClick = { id -> viewModel.deleteMedia(id) }
            )
        }
        return
    }

    // Bulk Operation Progress Dialog
    bulkState?.let { state ->
        BulkOperationProgressDialog(
            state = state,
            onCancel = { viewModel.cancelBulkOperation() },
            onDismiss = { viewModel.dismissBulkOperation() }
        )
    }

    if (showCleanAllConfirmation) {
        val totalBatches = (totalDuplicatesCount + 499) / 500
        AlertDialog(
            onDismissRequest = { showCleanAllConfirmation = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Delete All Duplicate Files") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to clean all $totalDuplicatesCount duplicate files ($totalRecoverableFormatted)?",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Processed in $totalBatches batch(es) of up to 500 files each for maximum stability.\n" +
                                "• Android system confirmation will appear for each batch.\n" +
                                "• The recommended original in each duplicate group is kept safe.\n" +
                                "• Duplicate copies will be moved to the Recycle Bin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Warning: This action will move $totalDuplicatesCount files to trash.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanAllConfirmation = false
                        val allDuplicatesToDelete = duplicateGroups.flatMap { it.items.drop(1) }
                        viewModel.startBulkTrash(allDuplicatesToDelete)
                    }
                ) {
                    Text("Delete All Duplicate Files")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanAllConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (itemToTrash != null) {
        AlertDialog(
            onDismissRequest = { itemToTrash = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Duplicate") },
            text = {
                Text("Move \"${itemToTrash?.filename}\" to the Recycle Bin?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = itemToTrash!!
                        itemToTrash = null
                        viewModel.trashMedia(listOf(item))
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToTrash = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Cleaner (${duplicateGroups.size} groups)") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (duplicateGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Duplicates Found!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your library is clean and optimized.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Summary Header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Potential Storage Recovery",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = totalRecoverableFormatted,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalDuplicatesCount duplicate items across ${duplicateGroups.size} groups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showCleanAllConfirmation = true },
                            enabled = !isProcessing && totalDuplicatesCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Processing...")
                            } else {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Duplicate Files ($totalDuplicatesCount)")
                            }
                        }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(duplicateGroups, key = { it.key }) { group ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val firstItemSizeKb = group.items.firstOrNull()?.size?.div(1024) ?: 0
                                Text(
                                    text = "Group (${group.items.size} identical files, $firstItemSizeKb KB each)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.items.forEachIndexed { index, media ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { previewMedia = media }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = media.contentUri,
                                                contentDescription = media.filename,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = media.filename,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = if (index == 0) "Recommended Original" else "Duplicate",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = { itemToTrash = media },
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
            }
        }
    }
}
