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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.melodrive.service.MusicRepository
import com.melodrive.service.MusicService
import com.melodrive.ui.screens.LibraryScreen
import com.melodrive.ui.screens.NowPlayingScreen
import com.melodrive.ui.screens.SearchScreen
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
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == "library",
                                onClick = { navController.navigate("library") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.LibraryMusic, null) },
                                label = { Text("Library") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == "search",
                                onClick = { navController.navigate("search") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Search, null) },
                                label = { Text("Search") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == "now_playing",
                                onClick = { navController.navigate("now_playing") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.MusicNote, null) },
                                label = { Text("Now Playing") },
                            )
                        }
                    },
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        NavHost(navController = navController, startDestination = "library") {
                            composable("library") {
                                LibraryScreen(onTrackClick = { tracks, index ->
                                    // local tracks — set playback queue to the library list
                                    MusicRepository.setPlaybackQueue(tracks)
                                    mediaController?.transportControls
                                        ?.playFromMediaId(tracks[index].id, null)
                                    navController.navigate("now_playing") { launchSingleTop = true }
                                })
                            }
                            composable("search") {
                                SearchScreen(onTrackClick = { track ->
                                    // youtube track — only update playback queue, never localTracks
                                    MusicRepository.setPlaybackQueue(listOf(track))
                                    mediaController?.transportControls
                                        ?.playFromMediaId(track.id, null)
                                    navController.navigate("now_playing") { launchSingleTop = true }
                                })
                            }
                            composable("now_playing") {
                                NowPlayingScreen()
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
