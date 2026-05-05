package com.ioristudios.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ioristudios.music.ui.components.BottomNavBar
import com.ioristudios.music.ui.library.LibraryScreen
import com.ioristudios.music.ui.nowplaying.NowPlayingScreen
import com.ioristudios.music.ui.playlists.PlaylistDetailScreen
import com.ioristudios.music.ui.playlists.PlaylistsScreen
import com.ioristudios.music.ui.theme.MusicTheme
import com.ioristudios.music.ui.theme.SurfaceDark
import com.ioristudios.music.ui.util.AnimDuration
import com.ioristudios.music.ui.VolumeViewModel
import com.ioristudios.music.ui.components.VolumeBoostControl
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MusicAppRoot() {
    MusicTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: "library"

        // Determine if bottom nav should show
        val showBottomNav = currentRoute in listOf("library", "now_playing", "playlists")

        // Back handler to ensure user goes to Library before exiting
        BackHandler(enabled = currentRoute != "library") {
            navController.navigate("library") {
                popUpTo("library") { inclusive = true }
            }
        }

        Scaffold(
            containerColor = SurfaceDark,
            bottomBar = {
                if (showBottomNav) {
                    BottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo("library") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "library",
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { it / 4 },
                            animationSpec = tween(AnimDuration.MEDIUM, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(AnimDuration.MEDIUM))
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -it / 4 },
                            animationSpec = tween(AnimDuration.MEDIUM, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(AnimDuration.FAST))
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -it / 4 },
                            animationSpec = tween(AnimDuration.MEDIUM, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(AnimDuration.MEDIUM))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it / 4 },
                            animationSpec = tween(AnimDuration.MEDIUM, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(AnimDuration.FAST))
                    }
                ) {
                    composable("library") {
                        LibraryScreen(
                            onSongClick = {
                                navController.navigate("now_playing") {
                                    popUpTo("library") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    composable("now_playing") {
                        NowPlayingScreen()
                    }

                    composable("playlists") {
                        PlaylistsScreen(
                            onPlaylistClick = { playlist ->
                                navController.navigate("playlist_detail/${playlist.id}")
                            }
                        )
                    }

                    composable(
                        route = "playlist_detail/{playlistId}",
                        arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 1L
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Global Volume Bar Overlay
                val volumeViewModel: VolumeViewModel = viewModel()
                val isVolumeVisible by volumeViewModel.isVisible.collectAsState()
                val volumePercent by volumeViewModel.volumePercent.collectAsState()

                AnimatedVisibility(
                    visible = isVolumeVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    VolumeBoostControl(
                        volumePercent = volumePercent,
                        onVolumeChange = { volumeViewModel.setVolume(it) },
                        isFloating = true
                    )
                }
            }
        }
    }
}
