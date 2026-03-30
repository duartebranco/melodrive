package com.melodrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
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
import com.melodrive.ui.screens.LibraryScreen
import com.melodrive.ui.screens.SearchScreen
import com.melodrive.ui.theme.MeloDriveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                                label = { Text("Library") },
                            )
                            NavigationBarItem(
                                selected = currentRoute == "search",
                                onClick = { navController.navigate("search") { launchSingleTop = true } },
                                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                label = { Text("Search") },
                            )
                        }
                    },
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        NavHost(navController = navController, startDestination = "library") {
                            composable("library") {
                                LibraryScreen(onTrackClick = { _, _ -> })
                            }
                            composable("search") {
                                SearchScreen(onTrackClick = { _ -> })
                            }
                        }
                    }
                }
            }
        }
    }
}
