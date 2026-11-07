package com.secondproject.datasortgallery.feature.videos

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.secondproject.datasortgallery.core.media.MediaStoreManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.secondproject.datasortgallery.core.designsystem.components.BulkOperationProgressDialog
import com.secondproject.datasortgallery.core.media.BulkOperationState
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import com.secondproject.datasortgallery.feature.gallery.GalleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VideoSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    DURATION_DESC("Longest First"),
    DURATION_ASC("Shortest First")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val mediaList by viewModel.mediaList.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    val bulkState by viewModel.bulkOperationState.collectAsState()
    val feedback by viewModel.operationFeedback.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Screen State
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(VideoSortOrder.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Selection Mode
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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

    // Filter and Sort Videos
    val rawVideos = remember(mediaList) {
        mediaList.filter { it.mediaType == MediaType.VIDEO }
    }

    val displayedVideos = remember(rawVideos, searchQuery, sortOrder) {
        val filtered = if (searchQuery.isBlank()) {
            rawVideos
        } else {
            rawVideos.filter { it.filename.contains(searchQuery, ignoreCase = true) }
        }

        when (sortOrder) {
            VideoSortOrder.DATE_DESC -> filtered.sortedByDescending { it.dateAdded }
            VideoSortOrder.DATE_ASC -> filtered.sortedBy { it.dateAdded }
            VideoSortOrder.NAME_ASC -> filtered.sortedBy { it.filename.lowercase(Locale.ROOT) }
            VideoSortOrder.NAME_DESC -> filtered.sortedByDescending { it.filename.lowercase(Locale.ROOT) }
            VideoSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.size }
            VideoSortOrder.SIZE_ASC -> filtered.sortedBy { it.size }
            VideoSortOrder.DURATION_DESC -> filtered.sortedByDescending { it.duration }
            VideoSortOrder.DURATION_ASC -> filtered.sortedBy { it.duration }
        }
    }

    // Video Player State - using rememberSaveable so rotation NEVER exits the video
    var playingVideoUniqueId by rememberSaveable { mutableStateOf<String?>(null) }
    val playingVideoIndex = remember(playingVideoUniqueId, displayedVideos) {
        if (playingVideoUniqueId != null) displayedVideos.indexOfFirst { it.uniqueId == playingVideoUniqueId } else -1
    }

    // Launch Built-In Video Player if an item was clicked
    if (playingVideoIndex in displayedVideos.indices) {
        VideoPlayerScreen(
            videoList = displayedVideos,
            initialIndex = playingVideoIndex,
            onBackClick = { playingVideoUniqueId = null },
            onCurrentVideoChanged = { currentVideo ->
                playingVideoUniqueId = currentVideo.uniqueId
            },
            onFavoriteClick = { id, isFav -> viewModel.toggleFavorite(id, isFav) },
            onDeleteClick = { id -> viewModel.deleteMedia(id) }
        )
        return
    }

    // Bulk Progress Dialog
    bulkState?.let { state ->
        BulkOperationProgressDialog(
            state = state,
            onCancel = { viewModel.cancelBulkOperation() },
            onDismiss = {
                viewModel.dismissBulkOperation()
                isSelectionMode = false
                selectedIds.clear()
            }
        )
    }

    // Multi-Selection Delete Confirmation
    if (showDeleteConfirmDialog && selectedIds.isNotEmpty()) {
        val itemsToDelete = remember(selectedIds) {
            displayedVideos.filter { selectedIds.contains(it.uniqueId) }
        }
        val totalBytes = itemsToDelete.sumOf { it.size }
        val batchCount = (itemsToDelete.size + MediaStoreManager.MEDIASTORE_BATCH_SIZE - 1) / MediaStoreManager.MEDIASTORE_BATCH_SIZE

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Selected Videos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Move ${itemsToDelete.size} video(s) (${BulkOperationState.formatBytes(totalBytes)}) to the Recycle Bin?",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Processed in $batchCount safe batch(es).\n• Android system confirmation will appear for consent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.startBulkTrash(itemsToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Selection Mode Top App Bar
                TopAppBar(
                    title = { Text("${selectedIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selectedIds.size == displayedVideos.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(displayedVideos.map { it.uniqueId })
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                )
            } else if (isSearchActive) {
                // Search Top App Bar
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search videos...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
                // Normal Top App Bar
                TopAppBar(
                    title = { Text("Videos (${displayedVideos.size})") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search Action
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }

                        // Grid / List Toggle Action
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "List View" else "Grid View"
                            )
                        }

                        // Sort Menu Action
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                VideoSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.displayName) },
                                        trailingIcon = {
                                            if (sortOrder == order) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            sortOrder = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Multi-Selection Trigger Action
                        IconButton(onClick = { isSelectionMode = true }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Select Mode")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (displayedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No Videos Match \"$searchQuery\"" else "No Videos Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try a different search query" else "Your device has no video files or scanning is running.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(displayedVideos, key = { it.uniqueId }) { video ->
                    val isSelected = selectedIds.contains(video.uniqueId)
                    VideoGridItem(
                        video = video,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedIds.remove(video.uniqueId)
                                else selectedIds.add(video.uniqueId)
                            } else {
                                playingVideoUniqueId = video.uniqueId
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedIds.add(video.uniqueId)
                            }
                        }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(displayedVideos, key = { it.uniqueId }) { video ->
                    val isSelected = selectedIds.contains(video.uniqueId)
                    VideoListItem(
                        video = video,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedIds.remove(video.uniqueId)
                                else selectedIds.add(video.uniqueId)
                            } else {
                                playingVideoUniqueId = video.uniqueId
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedIds.add(video.uniqueId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoGridItem(
    video: MediaEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.contentUri,
                contentDescription = video.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration Badge (Bottom-Right)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Play Icon (Center)
            if (!isSelectionMode) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(4.dp)
                )
            }

            // Selection Checkbox
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListItem(
    video: MediaEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail with Duration
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = video.contentUri,
                    contentDescription = video.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Metadata Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = BulkOperationState.formatBytes(video.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (video.resolution.isNotBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = video.resolution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val formattedDate = remember(video.dateAdded) {
                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(video.dateAdded))
                }
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Selection Checkbox
            if (isSelectionMode) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
