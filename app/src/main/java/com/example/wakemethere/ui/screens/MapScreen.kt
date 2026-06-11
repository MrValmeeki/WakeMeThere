package com.example.wakemethere.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.wakemethere.service.LocationService
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.ui.navigation.Screen
import com.example.wakemethere.util.LatLng
import com.example.wakemethere.util.RouteHelper
import com.example.wakemethere.util.rememberMapViewWithLifecycle
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import java.io.IOException
import java.util.Locale

private const val MIN_SEARCH_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 250L
private const val SEARCH_RESULT_ZOOM = 15.0
private const val MAX_GEOCODER_RESULTS = 5

private data class SearchSuggestion(
    val title: String,
    val subtitle: String? = null,
    val latLng: LatLng? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(navController: NavController, viewModel: JourneyViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    
    val selectedLocation by viewModel.selectedLocation
    val destinationName by viewModel.destinationName
    var isSaved by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    
    val mapView = rememberMapViewWithLifecycle()
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var destinationMarker by remember { mutableStateOf<org.maplibre.android.annotations.Marker?>(null) }
    var routePolyline by remember { mutableStateOf<org.maplibre.android.annotations.Polyline?>(null) }

    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val userLatLng = LatLng(it.latitude, it.longitude)
                        userLocation = userLatLng
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                MapLibreLatLng(it.latitude, it.longitude), 
                                14.0
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MapScreen", "Permission denied", e)
            }
        }
    }

    LaunchedEffect(userLocation, selectedLocation) {
        val start = userLocation
        val destination = selectedLocation

        if (start != null && destination != null) {
            val result = RouteHelper.fetchShortestDrivingRoute(start, destination)
            if (result != null && result.points.isNotEmpty()) {
                routePoints = result.points
            } else {
                routePoints = listOf(start, destination)
                Toast.makeText(context, "Road path failed. Using straight line.", Toast.LENGTH_SHORT).show()
            }
        } else {
            routePoints = emptyList()
        }
    }

    LaunchedEffect(searchQuery, isSearchActive) {
        val query = searchQuery.trim()

        if (!isSearchActive || query.length < MIN_SEARCH_QUERY_LENGTH) {
            suggestions = emptyList()
            searchError = null
            isSearching = false
            return@LaunchedEffect
        }

        delay(SEARCH_DEBOUNCE_MS)
        isSearching = true
        searchError = null

        val geocoderSuggestions = geocodeLocationName(context, query)
        if (searchQuery.trim() == query && isSearchActive) {
            suggestions = geocoderSuggestions
            searchError = if (geocoderSuggestions.isEmpty()) "No matching places found." else null
            isSearching = false
        }
    }

    LaunchedEffect(selectedLocation) {
        val map = mapInstance ?: return@LaunchedEffect
        val loc = selectedLocation ?: run {
            destinationMarker?.let { map.removeMarker(it) }
            destinationMarker = null
            return@LaunchedEffect
        }

        // Move Camera
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                MapLibreLatLng(loc.latitude, loc.longitude),
                SEARCH_RESULT_ZOOM
            )
        )

        // Update Marker
        destinationMarker?.let { map.removeMarker(it) }
        destinationMarker = map.addMarker(
            MarkerOptions()
                .position(MapLibreLatLng(loc.latitude, loc.longitude))
                .title(destinationName)
        )
    }

    LaunchedEffect(routePoints) {
        val map = mapInstance ?: return@LaunchedEffect
        routePolyline?.let { map.removePolyline(it) }
        
        if (routePoints.size >= 2) {
            val mapLibrePoints = routePoints.map { MapLibreLatLng(it.latitude, it.longitude) }
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(mapLibrePoints)
                    .color(android.graphics.Color.parseColor("#1B1B1B"))
                    .width(5f)
            )
        } else {
            routePolyline = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                mv.getMapAsync { map ->
                    if (mapInstance == null) {
                        mapInstance = map
                        map.addOnMapClickListener { point ->
                            val clickedLatLng = LatLng(point.latitude, point.longitude)
                            selectDestination(viewModel, clickedLatLng, "Dropped Pin")
                            isSaved = false
                            true
                        }
                    }
                    
                    if (map.style == null) {
                        map.setStyle(Style.Builder().fromUri("https://basemaps.cartocdn.com/gl/positron-gl-style/style.json")) { style ->
                            if (hasLocationPermission) {
                                val locationComponent = map.locationComponent
                                val options = LocationComponentActivationOptions.builder(context, style)
                                    .useDefaultLocationEngine(true)
                                    .build()
                                locationComponent?.activateLocationComponent(options)
                                locationComponent?.isLocationComponentEnabled = true
                            }
                        }
                    }
                }
            }
        )

        // UI components (SearchBar, Card) same as before...
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = if (isSearchActive) 0.dp else 16.dp)
                .zIndex(3f)
                .align(Alignment.TopCenter)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    suggestions.firstOrNull()?.let { 
                        selectDestination(viewModel, it.latLng!!, it.title)
                        searchQuery = it.title
                        isSearchActive = false
                    } ?: run {
                        isSearchActive = false
                    }
                },
                active = isSearchActive,
                onActiveChange = { isSearchActive = it },
                placeholder = { Text("Search destination...") },
                leadingIcon = { 
                    if (isSearchActive) {
                        IconButton(onClick = { isSearchActive = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null) 
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            suggestions = emptyList()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (isSearching) {
                        item {
                            ListItem(
                                headlineContent = { Text("Searching places...") },
                                leadingContent = { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                            )
                        }
                    }
                    searchError?.let { message ->
                        item { ListItem(headlineContent = { Text(message) }) }
                    }
                    items(suggestions) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.title) },
                            supportingContent = { suggestion.subtitle?.let { Text(it) } },
                            modifier = Modifier.clickable { 
                                suggestion.latLng?.let { 
                                    selectDestination(viewModel, it, suggestion.title)
                                    searchQuery = suggestion.title
                                    isSearchActive = false
                                } 
                            }
                        )
                    }
                }
            }
        }

        if (selectedLocation != null && !isSearchActive) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = destinationName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Lat: ${String.format("%.4f", selectedLocation?.latitude)}, Lng: ${String.format("%.4f", selectedLocation?.longitude)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            viewModel.saveDestination()
                            isSaved = true
                        }) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Save",
                                tint = if (isSaved) Color.Red else Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.resetETAs()
                            val intent = Intent(context, LocationService::class.java).apply {
                                action = LocationService.ACTION_START
                                putExtra(LocationService.EXTRA_LAT, selectedLocation?.latitude)
                                putExtra(LocationService.EXTRA_LNG, selectedLocation?.longitude)
                                putExtra(LocationService.EXTRA_THRESHOLD, viewModel.thresholdMinutes.value)
                            }
                            context.startForegroundService(intent)
                            navController.navigate(Screen.Journey.route)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Journey")
                    }
                }
            }
        }
    }
}


private suspend fun geocodeLocationName(context: Context, query: String): List<SearchSuggestion> {
    return withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext emptyList()

        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocationName(query, MAX_GEOCODER_RESULTS)
                .orEmpty()

            addresses.mapNotNull { address ->
                if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null

                val fullAddress = address.getAddressLine(0)
                val title = listOfNotNull(
                    address.featureName,
                    address.locality,
                    address.subAdminArea,
                    address.adminArea,
                    address.countryName,
                    fullAddress
                ).firstOrNull { it.isNotBlank() } ?: query

                SearchSuggestion(
                    title = title,
                    subtitle = fullAddress?.takeIf { it.isNotBlank() && it != title },
                    latLng = LatLng(address.latitude, address.longitude)
                )
            }.distinctBy {
                "${it.title}|${it.latLng?.latitude}|${it.latLng?.longitude}"
            }
        } catch (e: IOException) {
            Log.e("MapScreen", "Geocoder search failed", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e("MapScreen", "Invalid geocoder search query", e)
            emptyList()
        }
    }
}

// Helper function to set destination and update UI state
private fun selectDestination(viewModel: JourneyViewModel, latLng: LatLng, name: String) {
    // Update the ViewModel with the new destination
    viewModel.setDestination(latLng, name)
    // No additional UI state needed here as composable observes ViewModel's destinationName
}
