package com.melodrive

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.melodrive.ui.screens.NowPlayingViewModel
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.melodrive.service.MusicRepository
import com.melodrive.service.MusicService
import com.melodrive.ui.screens.LibraryScreen
import com.melodrive.ui.screens.NowPlayingScreen
import com.melodrive.ui.screens.StreamScreen
import com.melodrive.ui.theme.MeloDriveTheme

class MainActivity : ComponentActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            mediaController = MediaControllerCompat(this@MainActivity, mediaBrowser.sessionToken)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MusicService::class.java),
            connectionCallback,
            null,
        )

        enableEdgeToEdge()
        setContent {
            MeloDriveTheme {
                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        Column {
                            val npVm: NowPlayingViewModel = viewModel()
                            val npState by npVm.state.collectAsState()

                            AnimatedVisibility(
                                visible = currentRoute != "now_playing" && npState.title.isNotEmpty(),
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it }
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        navController.navigate("now_playing") { launchSingleTop = true }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = npState.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = npState.artist,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(onClick = { npVm.togglePlayPause() }) {
                                            Icon(
                                                if (npState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "play/pause"
                                            )
                                        }
                                    }
                                }
                            }
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == "library",
                                    onClick = { navController.navigate("library") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.LibraryMusic, null) },
                                    label = { Text("Library") },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "stream",
                                    onClick = { navController.navigate("stream") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Search, null) },
                                    label = { Text("Stream") },
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "now_playing",
                                    onClick = { navController.navigate("now_playing") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.MusicNote, null) },
                                    label = { Text("Now Playing") },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        NavHost(navController = navController, startDestination = "library") {
                            composable("library") {
                                LibraryScreen(onTrackClick = { tracks, index ->
                                    val selectedTrack = tracks[index]
                                    MusicRepository.addToMainBufferAndMoveToFront(selectedTrack)
                                    mediaController?.transportControls
                                        ?.playFromMediaId(selectedTrack.id, null)
                                    navController.navigate("now_playing") { launchSingleTop = true }
                                })
                            }
                            composable("stream") {
                                StreamScreen(onTracksClick = { tracks ->
                                    if (tracks.isNotEmpty()) {
                                        val selectedTrack = tracks.first()
                                        MusicRepository.addAllToMainBuffer(tracks)
                                        MusicRepository.addToMainBufferAndMoveToFront(selectedTrack)
                                        mediaController?.transportControls
                                            ?.playFromMediaId(selectedTrack.id, null)
                                        navController.navigate("now_playing") { launchSingleTop = true }
                                    }
                                })
                            }
                            composable("now_playing") {
                                NowPlayingScreen(onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        mediaBrowser.disconnect()
        super.onStop()
    }
}
