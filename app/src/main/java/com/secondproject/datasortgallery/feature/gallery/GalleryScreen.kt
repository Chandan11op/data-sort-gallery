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
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.data.local.entity.MediaType

@Composable
fun GalleryScreen(
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val mediaList by viewModel.mediaList.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    var selectedMedia by remember { mutableStateOf<MediaEntity?>(null) }

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
        MediaViewerScreen(
            media = selectedMedia!!,
            onBackClick = { selectedMedia = null },
            onFavoriteClick = { id, isFav -> viewModel.toggleFavorite(id, isFav) },
            onDeleteClick = { id -> viewModel.deleteMedia(id) }
        )
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
                        onClick = { selectedMedia = media }
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
