package com.example.wakemethere

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.Scaffold
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.ui.navigation.NavGraph
import com.example.wakemethere.ui.theme.WakeMeThereTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val viewModel: JourneyViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (fineLocationGranted || coarseLocationGranted) {
            // Foreground location granted, now request background if on Android 10+
            checkAndRequestBackgroundPermission()
        } else {
            Toast.makeText(this, "Location permission is required for this app to work.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // You should ideally show an explanation before requesting this
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        MapLibre.getInstance(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            WakeMeThereTheme {
                val navController = rememberNavController()
                Scaffold { innerPadding ->
                    NavGraph(navController = navController, viewModel = viewModel)
                }
            }
        }

    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Background location permission needs to be requested separately on Android 10+
        // and usually after the user has granted foreground location.
        // For now, let's start with foreground.

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
