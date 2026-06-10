package com.example.wakemethere.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.ui.navigation.Screen

@Composable
fun HomeScreen(navController: NavController, viewModel: JourneyViewModel) {
    val threshold by viewModel.thresholdMinutes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WakeMeThere",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Wake me up $threshold minutes before arrival",
            style = MaterialTheme.typography.titleMedium
        )
        
        Slider(
            value = threshold.toFloat(),
            onValueChange = { viewModel.setThreshold(it.toInt()) },
            valueRange = 5f..60f,
            steps = 10,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { navController.navigate(Screen.Map.route) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Set Destination")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { navController.navigate(Screen.SavedDestinations.route) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Saved Destinations")
        }
    }
}
