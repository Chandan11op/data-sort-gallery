package com.secondproject.datasortgallery.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secondproject.datasortgallery.core.designsystem.components.AppBottomBar
import com.secondproject.datasortgallery.feature.albums.AlbumsScreen
import com.secondproject.datasortgallery.feature.audio.AudioScreen
import com.secondproject.datasortgallery.feature.duplicates.DuplicatesScreen
import com.secondproject.datasortgallery.feature.explore.ExploreScreen
import com.secondproject.datasortgallery.feature.explore.FilteredMediaScreen
import com.secondproject.datasortgallery.feature.favorites.FavoritesScreen
import com.secondproject.datasortgallery.feature.gallery.GalleryScreen
import com.secondproject.datasortgallery.feature.people.PeopleScreen
import com.secondproject.datasortgallery.feature.places.PlacesScreen
import com.secondproject.datasortgallery.feature.profile.ProfileScreen
import com.secondproject.datasortgallery.feature.recyclebin.RecycleBinScreen
import com.secondproject.datasortgallery.feature.search.SearchScreen
import com.secondproject.datasortgallery.feature.settings.SettingsScreen
import com.secondproject.datasortgallery.feature.videos.VideosScreen

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            AppBottomBar(
                currentRoute = currentRoute,
                onNavigateToRoute = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Gallery.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Gallery.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Gallery.route) {
                GalleryScreen(
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) }
                )
            }
            composable(Screen.Albums.route) {
                AlbumsScreen(
                    onNavigateToRoute = { route -> navController.navigate(route) }
                )
            }
            composable(Screen.People.route) {
                PeopleScreen()
            }
            composable(Screen.Explore.route) {
                ExploreScreen(
                    onNavigateToRoute = { route -> navController.navigate(route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAccount = { navController.navigate(Screen.Profile.route) }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Videos.route) {
                VideosScreen(onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Audio.route) {
                AudioScreen(onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen(onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Search.route) {
                SearchScreen(onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Duplicates.route) {
                DuplicatesScreen(onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Places.route) {
                PlacesScreen(onBackClick = { navController.popBackStack() })
            }
            composable(Screen.Screenshots.route) {
                FilteredMediaScreen(
                    title = "Screenshots",
                    filterPredicate = { it.relativePath.contains("Screenshot", true) || it.filename.contains("Screenshot", true) },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Downloads.route) {
                FilteredMediaScreen(
                    title = "Downloads",
                    filterPredicate = { it.relativePath.contains("Download", true) },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.LargeFiles.route) {
                FilteredMediaScreen(
                    title = "Large Files (>20MB)",
                    filterPredicate = { it.size > 20L * 1024 * 1024 },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.RecentlyAdded.route) {
                FilteredMediaScreen(
                    title = "Recently Added",
                    filterPredicate = { System.currentTimeMillis() - it.dateAdded < 30L * 24 * 60 * 60 * 1000 },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.RecycleBin.route) {
                RecycleBinScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}
