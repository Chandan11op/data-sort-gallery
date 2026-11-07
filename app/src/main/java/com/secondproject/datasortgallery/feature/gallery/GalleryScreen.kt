package com.secondproject.datasortgallery.feature.gallery

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.secondproject.datasortgallery.core.designsystem.components.AppTopBar
import com.secondproject.datasortgallery.core.designsystem.components.EmptyState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType
import androidx.compose.runtime.saveable.rememberSaveable
import com.secondproject.datasortgallery.feature.videos.VideoPlayerScreen
import com.secondproject.datasortgallery.feature.videos.formatDuration

@Composable
fun GalleryScreen(
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val mediaList by viewModel.mediaList.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    var selectedMediaUniqueId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedMedia = remember(selectedMediaUniqueId, mediaList) {
        selectedMediaUniqueId?.let { id -> mediaList.find { it.uniqueId == id } }
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

    if (selectedMedia != null) {
        if (selectedMedia!!.mediaType == MediaType.VIDEO) {
            val videoList = remember(mediaList) { mediaList.filter { it.mediaType == MediaType.VIDEO } }
            val startIndex = videoList.indexOfFirst { it.uniqueId == selectedMedia!!.uniqueId }.coerceAtLeast(0)
            VideoPlayerScreen(
                videoList = videoList.ifEmpty { listOf(selectedMedia!!) },
                initialIndex = startIndex,
                onBackClick = { selectedMediaUniqueId = null },
                onFavoriteClick = { id, isFav -> viewModel.toggleFavorite(id, isFav) },
                onDeleteClick = { id -> viewModel.deleteMedia(id) }
            )
        } else {
            MediaViewerScreen(
                media = selectedMedia!!,
                onBackClick = { selectedMediaUniqueId = null },
                onFavoriteClick = { id, isFav -> viewModel.toggleFavorite(id, isFav) },
                onDeleteClick = { id -> viewModel.deleteMedia(id) }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Gallery (${mediaList.size})"
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (mediaList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                EmptyState(
                    title = "No Media Found",
                    description = "Tap below to scan your device storage for photos and videos.",
                    actionLabel = "Scan Media",
                    onActionClick = { viewModel.refreshMedia() }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(mediaList, key = { it.uniqueId }) { media ->
                    MediaThumbnailItem(
                        media = media,
                        onClick = { selectedMediaUniqueId = media.uniqueId }
                    )
                }
            }
        }
    }
}

@Composable
fun MediaThumbnailItem(
    media: MediaEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = media.contentUri,
                contentDescription = media.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (media.mediaType == MediaType.VIDEO) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = formatDuration(media.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                )
            }
        }
    }
}
