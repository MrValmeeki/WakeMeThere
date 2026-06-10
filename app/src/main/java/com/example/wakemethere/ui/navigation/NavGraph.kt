package com.example.wakemethere.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.ui.screens.HomeScreen
import com.example.wakemethere.ui.screens.MapScreen
import com.example.wakemethere.ui.screens.JourneyScreen
import com.example.wakemethere.ui.screens.SavedDestinationsScreen

@Composable
fun NavGraph(navController: NavHostController, viewModel: JourneyViewModel) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {

        composable(Screen.Home.route) {
            HomeScreen(navController, viewModel)
        }
        composable(Screen.Map.route) {
            MapScreen(navController, viewModel)
        }
        composable(Screen.Journey.route) {
            JourneyScreen(navController, viewModel)
        }
        composable(Screen.SavedDestinations.route) {
            SavedDestinationsScreen(navController, viewModel)
        }

    }
}

