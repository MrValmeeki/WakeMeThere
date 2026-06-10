package com.example.wakemethere.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.ui.navigation.Screen
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDestinationsScreen(navController: NavController, viewModel: JourneyViewModel) {
    val savedDestinations by viewModel.savedDestinations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Destinations") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (savedDestinations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No saved destinations yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(savedDestinations) { destination ->
                    ListItem(
                        headlineContent = { Text(destination.name) },
                        supportingContent = {
                            Text("Lat: ${String.format("%.4f", destination.latitude)}, Lng: ${String.format("%.4f", destination.longitude)}")
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteDestination(destination) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        },
                        modifier = Modifier.clickable {
                            viewModel.setDestination(
                                LatLng(destination.latitude, destination.longitude),
                                destination.name
                            )
                            navController.navigate(Screen.Map.route)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
