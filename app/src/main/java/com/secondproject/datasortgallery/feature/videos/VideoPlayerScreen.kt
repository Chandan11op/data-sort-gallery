package com.secondproject.datasortgallery.feature.videos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.secondproject.datasortgallery.data.local.entity.MediaEntity
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoList: List<MediaEntity>,
    initialIndex: Int,
    onBackClick: () -> Unit,
    onCurrentVideoChanged: ((MediaEntity) -> Unit)? = null,
    onDeleteClick: ((String) -> Unit)? = null,
    onFavoriteClick: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Persistent playback state across configuration changes (rotation)
    var currentIndex by rememberSaveable { mutableIntStateOf(initialIndex.coerceIn(0, (videoList.size - 1).coerceAtLeast(0))) }
    val currentVideo = videoList.getOrNull(currentIndex)

    var isPlaying by rememberSaveable { mutableStateOf(true) }
    var currentPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var durationMs by rememberSaveable { mutableLongStateOf(currentVideo?.duration ?: 0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(currentVideo?.isFavorite == true) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Sync active video with parent
    LaunchedEffect(currentVideo) {
        if (currentVideo != null) {
            onCurrentVideoChanged?.invoke(currentVideo)
            isFavorite = currentVideo.isFavorite
        }
    }

    // Hardware volume buttons & system UI cleanup on exit
    DisposableEffect(activity) {
        val originalStream = activity?.volumeControlStream
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose {
            if (originalStream != null) {
                activity.volumeControlStream = originalStream
            }
            // Restore system bars
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Immersive mode: Show/hide system bars (status & navigation bars) synchronously with controls
    LaunchedEffect(showControls) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showControls) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Handle Android system back button
    BackHandler {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBackClick()
    }

    // ExoPlayer single instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Auto-hide controls after 2.5 seconds of inactivity when video is playing and not scrubbing
    LaunchedEffect(showControls, isPlaying, isScrubbing, lastInteractionTime, showSpeedDialog, showDeleteConfirm) {
        if (showControls && isPlaying && !isScrubbing && !showSpeedDialog && !showDeleteConfirm) {
            delay(2500)
            showControls = false
        }
    }

    // Update playback position smoothly
    LaunchedEffect(isPlaying, isBuffering) {
        while (isPlaying && !isBuffering) {
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (exoPlayer.duration > 0) {
                    durationMs = exoPlayer.duration
                }
            }
            delay(250)
        }
    }

    // Load media item only when current video contentUri changes
    LaunchedEffect(currentVideo?.contentUri) {
        if (currentVideo != null) {
            val currentLoadedUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentLoadedUri != currentVideo.contentUri) {
                playbackError = null
                isBuffering = true
                try {
                    val mediaItem = MediaItem.fromUri(Uri.parse(currentVideo.contentUri))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    if (currentPositionMs > 0L) {
                        exoPlayer.seekTo(currentPositionMs)
                    }
                    if (isPlaying) {
                        exoPlayer.play()
                    }
                } catch (e: Exception) {
                    playbackError = "Error loading video: ${e.localizedMessage ?: "File unavailable"}"
                    isBuffering = false
                }
            }
        } else {
            playbackError = "Video not found"
        }
    }

    // Setup player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    playbackError = null
                } else if (playbackState == Player.STATE_ENDED) {
                    if (currentIndex < videoList.size - 1) {
                        currentIndex++
                    } else {
                        isPlaying = false
                        showControls = true
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isPlaying = false
                showControls = true
                playbackError = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED -> "Video codec not supported by this device"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Video file not found or has been moved"
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Permission denied to access this video"
                    else -> error.localizedMessage ?: "Cannot play this video format"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Handle lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.stop()
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && currentVideo != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                lastInteractionTime = System.currentTimeMillis()
            },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Move Video to Recycle Bin") },
            text = { Text("Move \"${currentVideo.filename}\" to the Recycle Bin?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        val deletedId = currentVideo.uniqueId
                        if (videoList.size > 1) {
                            if (currentIndex < videoList.size - 1) {
                                currentIndex++
                            } else {
                                currentIndex--
                            }
                        } else {
                            onBackClick()
                        }
                        onDeleteClick?.invoke(deletedId)
                    }
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    lastInteractionTime = System.currentTimeMillis()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = {
                showSpeedDialog = false
                lastInteractionTime = System.currentTimeMillis()
            },
            title = { Text("Playback Speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = speed
                                    exoPlayer.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = playbackSpeed == speed,
                                onClick = {
                                    playbackSpeed = speed
                                    exoPlayer.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${speed}x" + if (speed == 1.0f) " (Normal)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSpeedDialog = false
                    lastInteractionTime = System.currentTimeMillis()
                }) {
                    Text("Close")
                }
            }
        )
    }

    // Main Player Layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
                lastInteractionTime = System.currentTimeMillis()
            }
    ) {
        // Media3 ExoPlayer Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
        }

        // Error State Card
        if (playbackError != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD212121)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Playback Error",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playbackError ?: "Unable to play video",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onBackClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Back")
                        }
                        if (currentIndex < videoList.size - 1) {
                            Button(onClick = { currentIndex++ }) {
                                Text("Next Video")
                            }
                        }
                    }
                }
            }
        }

        // Controls Overlay with smooth fade in/out
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = false
                    }
            ) {
                // Top App Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.TopCenter)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                ) {
                    IconButton(onClick = {
                        val window = activity?.window
                        if (window != null) {
                            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                            insetsController.show(WindowInsetsCompat.Type.systemBars())
                        }
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = currentVideo?.filename ?: "Video Player",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!currentVideo?.resolution.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = currentVideo!!.resolution,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${currentIndex + 1} of ${videoList.size}",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Favorite Button
                    if (onFavoriteClick != null && currentVideo != null) {
                        IconButton(onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            isFavorite = !isFavorite
                            onFavoriteClick(currentVideo.uniqueId, isFavorite)
                        }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color.Red else Color.White
                            )
                        }
                    }

                    // Delete Button
                    if (onDeleteClick != null && currentVideo != null) {
                        IconButton(onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            showDeleteConfirm = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Playback Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                ) {
                    // Previous Video
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            if (currentIndex > 0) currentIndex--
                        },
                        enabled = currentIndex > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (currentIndex > 0) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Rewind 10s
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play / Pause
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    exoPlayer.seekTo(0)
                                }
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(durationMs))
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Next Video
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            if (currentIndex < videoList.size - 1) currentIndex++
                        },
                        enabled = currentIndex < videoList.size - 1,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = if (currentIndex < videoList.size - 1) Color.White else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Controls Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            lastInteractionTime = System.currentTimeMillis()
                        }
                ) {
                    // Time and Progress Scrubber
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatDuration(currentPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(48.dp)
                        )

                        Slider(
                            value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { fraction ->
                                isScrubbing = true
                                lastInteractionTime = System.currentTimeMillis()
                                val newPos = (fraction * durationMs).toLong()
                                currentPositionMs = newPos
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(currentPositionMs)
                                isScrubbing = false
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Text(
                            text = formatDuration(durationMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(48.dp)
                        )
                    }

                    // Secondary Tools: Mute, Speed, Orientation
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Mute Toggle
                        IconButton(onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        }) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Mute",
                                tint = Color.White
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed Button
                            TextButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    showSpeedDialog = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${playbackSpeed}x",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Fullscreen / Orientation Toggle
                            IconButton(onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                val act = context.findActivity()
                                act?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                                }
                            }) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Orientation",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
