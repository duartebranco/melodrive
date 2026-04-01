package com.melodrive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.melodrive.model.Track
import com.melodrive.model.TrackSource
import com.melodrive.youtube.StreamViewModel
import com.melodrive.youtube.YtSearchResult
import com.melodrive.youtube.ResultType
import com.melodrive.youtube.YtDlpWrapper
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onTracksClick: (List<Track>) -> Unit,
    vm: StreamViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val searchInteraction = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Stream",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (!state.loading) vm.search() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                interactionSource = searchInteraction,
                modifier = Modifier.weight(1f),
            ) { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = state.query,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = searchInteraction,
                    placeholder = { Text("artist or song name") },
                    trailingIcon = {
                        IconButton(onClick = vm::search, enabled = !state.loading) {
                            Icon(Icons.Default.Search, contentDescription = "search")
                        }
                    },
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(
                        top = 10.dp,
                        bottom = 10.dp,
                    ),
                    container = {
                        OutlinedTextFieldDefaults.ContainerBox(
                            enabled = true,
                            isError = false,
                            interactionSource = searchInteraction,
                            colors = OutlinedTextFieldDefaults.colors(),
                        )
                    },
                )
            }
        }

        when {
            state.error != null -> ErrorHint(state.error!!)
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.results.isEmpty() && state.query.isNotEmpty() -> EmptyResultsHint("no results")
            state.results.isEmpty() && state.query.isEmpty() -> EmptyResultsHint("Never streamed")
            else -> {
                if (state.query.isEmpty()) {
                    Text(
                        text = "Last played",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                ResultList(results = state.results, onTracksClick = onTracksClick, vm = vm)
            }
        }
    }
}

@Composable
private fun ResultList(
    results: List<YtSearchResult>,
    onTracksClick: (List<Track>) -> Unit,
    vm: StreamViewModel,
) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results) { result ->
            ResultRow(
                result = result,
                onClick = {
                    vm.addToHistory(listOf(result))
                    if (result.type == ResultType.SONG) {
                        onTracksClick(listOf(result.toTrack()))
                    } else if (result.type == ResultType.ALBUM) {
                        scope.launch {
                            val songs = YtDlpWrapper.getAlbumSongs(result.videoId)
                            if (songs.isNotEmpty()) {
                                onTracksClick(songs.map { it.toTrack() })
                            }
                        }
                    } else if (result.type == ResultType.ARTIST) {
                        scope.launch {
                            val songs = YtDlpWrapper.getArtistSongs(result.videoId)
                            if (songs.isNotEmpty()) {
                                onTracksClick(songs.shuffled().map { it.toTrack() })
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ResultRow(result: YtSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(result.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            fallback = rememberVectorPainter(Icons.Default.MusicNote),
            error = rememberVectorPainter(Icons.Default.MusicNote),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            val prefix = when (result.type) {
                ResultType.ALBUM -> "Album • "
                ResultType.ARTIST -> "Artist • "
                else -> ""
            }
            Text(
                text = prefix + result.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun YtSearchResult.toTrack() = Track(
    id = videoId,
    title = title,
    artist = artist,
    source = TrackSource.YOUTUBE,
    uri = Uri.parse("https://www.youtube.com/watch?v=$videoId"),
    artworkUri = Uri.parse(thumbnailUrl),
    durationMs = durationSeconds * 1000L,
)

@Composable
private fun ErrorHint(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun EmptyResultsHint(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
