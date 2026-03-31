package com.melodrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun NowPlayingScreen(vm: NowPlayingViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    DisposableEffect(Unit) {
        vm.connect()
        onDispose { /* keep connected while app is open */ }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (state.artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(state.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // title and artist
        Text(
            text = state.title.ifEmpty { "nothing playing" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (state.artist.isNotEmpty()) {
            Text(
                text = state.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        // seek bar
        if (state.durationMs > 0) {
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = vm::seekTo,
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.weight(1f))

        // transport controls
        TransportControls(
            isPlaying = state.isPlaying,
            onTogglePlayPause = vm::togglePlayPause,
            onSkipPrevious = vm::skipPrevious,
            onSkipNext = vm::skipNext,
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableFloatStateOf(-1f) }
    val progress = if (dragging >= 0f) dragging
    else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Column {
        Slider(
            value = progress,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                onSeek((dragging * durationMs).toLong())
                dragging = -1f
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMs(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
) {
    val viewModel: NowPlayingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = { viewModel.toggleRepeatMode() }, modifier = Modifier.size(48.dp)) {
            val icon = if (state.repeatMode == android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE) {
                Icons.Default.RepeatOne
            } else {
                Icons.Default.Repeat
            }
            val tint = if (state.repeatMode == android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primary
            }
            Icon(
                imageVector = icon,
                contentDescription = "repeat",
                modifier = Modifier.size(28.dp),
                tint = tint,
            )
        }

        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "previous",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // large play/pause button
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "pause" else "play",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "next",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
