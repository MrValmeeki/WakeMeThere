package com.example.wakemethere.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Map : Screen("map")
    object Journey : Screen("journey")
    object SavedDestinations : Screen("saved_destinations")
}

