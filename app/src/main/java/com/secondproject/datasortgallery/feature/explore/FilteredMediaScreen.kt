package com.secondproject.datasortgallery.feature.explore

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import com.secondproject.datasortgallery.feature.gallery.GalleryViewModel
import com.secondproject.datasortgallery.feature.gallery.MediaViewerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredMediaScreen(
    title: String,
    filterPredicate: (MediaEntity) -> Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val mediaList by viewModel.mediaList.collectAsState()
    val pendingConsent by viewModel.pendingConsent.collectAsState()
    val filtered = remember(mediaList, filterPredicate) { mediaList.filter(filterPredicate) }
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
            TopAppBar(
                title = { Text("$title (${filtered.size})") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(filtered, key = { it.uniqueId }) { media ->
                Card(
                    modifier = Modifier
                        .padding(1.dp)
                        .aspectRatio(1f)
                        .clickable { selectedMedia = media }
                ) {
                    AsyncImage(
                        model = media.contentUri,
                        contentDescription = media.filename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
