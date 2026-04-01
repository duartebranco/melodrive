package com.melodrive.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.melodrive.library.Album
import com.melodrive.library.Artist
import com.melodrive.library.Folder
import com.melodrive.library.LibraryViewModel
import com.melodrive.model.Track

@Composable
fun LibraryScreen(
    onTrackClick: (List<Track>, Int) -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        vm.setFolder(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { folderPicker.launch(null) }) {
                Icon(
                    Icons.Default.FolderOpen, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.folderUri != null) "Change" else "Pick folder",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.folderUri == null -> PickFolderHint { folderPicker.launch(null) }
            state.tracks.isEmpty() -> EmptyLibraryHint()
            else -> {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("Songs") })
                    Tab(
                        selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("Albums") })
                    Tab(
                        selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        text = { Text("Artists") })
                    Tab(
                        selected = selectedTab == 3, onClick = { selectedTab = 3 },
                        text = { Text("Folders") })
                }
                when (selectedTab) {
                    0 -> SongList(tracks = state.tracks, onTrackClick = onTrackClick)
                    1 -> AlbumList(albums = state.albums, onAlbumClick = { album ->
                        if (album.tracks.isNotEmpty()) onTrackClick(album.tracks, 0)
                    })

                    2 -> ArtistList(artists = state.artists, onArtistClick = { artist ->
                        if (artist.tracks.isNotEmpty()) onTrackClick(artist.tracks, 0)
                    })

                    3 -> FolderList(folders = state.folders, onFolderClick = { folder ->
                        if (folder.tracks.isNotEmpty()) onTrackClick(folder.tracks, 0)
                    })
                }
            }
        }
    }
}

@Composable
private fun SongList(tracks: List<Track>, onTrackClick: (List<Track>, Int) -> Unit) {
    LazyColumn {
        itemsIndexed(tracks) { index, track ->
            TrackRow(track = track, onClick = { onTrackClick(tracks, index) })
        }
    }
}

@Composable
private fun AlbumList(albums: List<Album>, onAlbumClick: (Album) -> Unit) {
    LazyColumn {
        items(albums, key = { it.name }) { album ->
            AlbumRow(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onArtistClick: (Artist) -> Unit) {
    LazyColumn {
        items(artists, key = { it.name }) { artist ->
            ArtistRow(artist = artist, onClick = { onArtistClick(artist) })
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onFolderClick: (Folder) -> Unit) {
    LazyColumn {
        items(folders, key = { it.name }) { folder ->
            FolderRow(folder = folder, onClick = { onFolderClick(folder) })
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkThumbnail(uri = track.artworkUri, fallback = Icons.Default.MusicNote)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
            )
            if (track.artist.isNotEmpty()) {
                Text(
                    track.artist, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkThumbnail(uri = album.artworkUri, fallback = Icons.Default.Album)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.name, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
            )
            Text(
                text = buildString {
                    if (album.artist.isNotEmpty()) append("${album.artist} · ")
                    append("${album.tracks.size} songs")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkThumbnail(uri = artist.artworkUri, fallback = Icons.Default.Person)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                artist.name, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
            )
            Text(
                text = "${artist.tracks.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FolderRow(folder: Folder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(48.dp).padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.name, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1
            )
            Text(
                text = "${folder.tracks.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ArtworkThumbnail(
    uri: Uri?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector,
) {
    if (uri != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(fallback),
        )
    } else {
        Icon(
            imageVector = fallback,
            contentDescription = null,
            modifier = Modifier.size(48.dp).padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PickFolderHint(onPick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FolderOpen, null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "no folder selected", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            TextButton(onClick = onPick, modifier = Modifier.padding(top = 8.dp)) {
                Text("pick a folder", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EmptyLibraryHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "no audio files found", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
