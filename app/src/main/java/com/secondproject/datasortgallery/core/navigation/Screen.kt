package com.secondproject.datasortgallery.core.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Permissions : Screen("permissions")
    object Main : Screen("main")

    // Bottom Navigation Tabs
    object Gallery : Screen("gallery")
    object Albums : Screen("albums")
    object People : Screen("people")
    object Explore : Screen("explore")
    object Settings : Screen("settings")

    // Explore Sub-Features
    object Places : Screen("places")
    object Duplicates : Screen("duplicates")
    object Videos : Screen("videos")
    object Audio : Screen("audio")
    object RecentlyAdded : Screen("recently_added")
    object Screenshots : Screen("screenshots")
    object Downloads : Screen("downloads")
    object LargeFiles : Screen("large_files")
    object Favorites : Screen("favorites")
    object RecycleBin : Screen("recycle_bin")
    object Search : Screen("search")
    object Profile : Screen("profile")
}