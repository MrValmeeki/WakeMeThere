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
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.wakemethere.service.LocationService
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.ui.navigation.Screen
import com.example.wakemethere.util.RouteHelper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

private const val MIN_SEARCH_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 250L
private const val SEARCH_RESULT_ZOOM = 15f
private const val MAX_GEOCODER_RESULTS = 5

private data class SearchSuggestion(
    val title: String,
    val subtitle: String? = null,
    val placeId: String? = null,
    val latLng: LatLng? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(navController: NavController, viewModel: JourneyViewModel) {
    val context = LocalContext.current
    val placesClient = remember { Places.createClient(context) }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var isPlacesSearchAvailable by remember { mutableStateOf(true) }
    var autocompleteSessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }
    val selectedLocation by viewModel.selectedLocation
    val destinationName by viewModel.destinationName
    var isSaved by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeErrorMessage by remember { mutableStateOf<String?>(null) }

    val defaultPos = LatLng(1.35, 103.87) // Singapore default
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPos, 10f)
    }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }

    // Centering on current location on start
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
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(userLatLng, 15f)
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MapScreen", "Permission denied even after check", e)
            }
        }
    }

    LaunchedEffect(userLocation, selectedLocation) {
        val start = userLocation
        val destination = selectedLocation

        if (start != null && destination != null) {
            val result = com.example.wakemethere.util.RouteHelper.fetchShortestDrivingRoute(context, start, destination)
            if (result != null && result.points.isNotEmpty()) {
                routePoints = result.points
                routeErrorMessage = null
            } else {
                routePoints = listOf(start, destination)
                routeErrorMessage = result?.errorDetail ?: "Unknown routing error occurred."
                Toast.makeText(context, "Road path failed. Using straight line.", Toast.LENGTH_SHORT).show()
            }
        } else {
            routePoints = emptyList()
            routeErrorMessage = null
        }
    }


    LaunchedEffect(searchQuery, isSearchActive, autocompleteSessionToken) {
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

        if (!isPlacesSearchAvailable) {
            val geocoderSuggestions = geocodeLocationName(context, query)
            if (searchQuery.trim() == query && isSearchActive) {
                suggestions = geocoderSuggestions
                searchError = if (geocoderSuggestions.isEmpty()) {
                    "No matching places found."
                } else {
                    null
                }
                isSearching = false
            }
            return@LaunchedEffect
        }

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(autocompleteSessionToken)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                if (searchQuery.trim() == query && isSearchActive) {
                    val placesSuggestions = response.autocompletePredictions.map { prediction ->
                        prediction.toSearchSuggestion()
                    }

                    if (placesSuggestions.isNotEmpty()) {
                        suggestions = placesSuggestions
                        searchError = null
                        isSearching = false
                    } else {
                        scope.launch {
                            val geocoderSuggestions = geocodeLocationName(context, query)
                            if (searchQuery.trim() == query && isSearchActive) {
                                suggestions = geocoderSuggestions
                                searchError = if (geocoderSuggestions.isEmpty()) {
                                    "No matching places found."
                                } else {
                                    null
                                }
                                isSearching = false
                            }
                        }
                    }
                }
            }
            .addOnFailureListener {
                if (searchQuery.trim() == query && isSearchActive) {
                    if (it.isPlacesAccessDenied()) {
                        isPlacesSearchAvailable = false
                    }

                    scope.launch {
                        val geocoderSuggestions = geocodeLocationName(context, query)
                        if (searchQuery.trim() == query && isSearchActive) {
                            suggestions = geocoderSuggestions
                            searchError = if (geocoderSuggestions.isEmpty()) {
                                it.toSearchErrorMessage()
                            } else {
                                null
                            }
                            isSearching = false
                        }
                    }
                }
                Log.e("MapScreen", "Places API autocomplete error", it)
            }
    }

    fun selectDestination(latLng: LatLng, name: String) {
        viewModel.setDestination(latLng, name)
        searchQuery = name
        suggestions = emptyList()
        searchError = null
        isSearching = false
        isSearchActive = false
        isSaved = false
        autocompleteSessionToken = AutocompleteSessionToken.newInstance()
        cameraPositionState.move(
            CameraUpdateFactory.newLatLngZoom(latLng, SEARCH_RESULT_ZOOM)
        )
    }

    fun selectSuggestion(suggestion: SearchSuggestion) {
        suggestion.latLng?.let {
            selectDestination(it, suggestion.title)
            return
        }

        val placeId = suggestion.placeId ?: return
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION
        )
        val request = FetchPlaceRequest.newInstance(placeId, placeFields)

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                val latLng = place.location

                if (latLng != null) {
                    val name = place.displayName ?: place.formattedAddress ?: "Destination"
                    selectDestination(latLng, name)
                } else {
                    searchError = "That place has no map location. Try another result."
                }
            }
            .addOnFailureListener {
                searchError = it.toPlaceDetailsErrorMessage()
                Log.e("MapScreen", "Places API fetch place error", it)
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Adjust map padding so UI buttons (My Location) are below the SearchBar
        // AND above the bottom card if it's visible.
        val topPadding = if (isSearchActive) 0.dp else 100.dp
        val bottomPadding = if (selectedLocation != null && !isSearchActive) 200.dp else 16.dp
        
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            onMapClick = {
                viewModel.setDestination(it, "Dropped Pin")
                isSaved = false
            }
        ) {
            selectedLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = destinationName
                )
                
                userLocation?.let { start ->
                    Polyline(
                        points = routePoints.ifEmpty { listOf(start, it) },
                        color = Color.Blue,
                        width = 8f
                    )
                }
            }
        }

        // Material 3 SearchBar
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
                onQueryChange = { query ->
                    searchQuery = query
                },
                onSearch = {
                    suggestions.firstOrNull()?.let(::selectSuggestion) ?: run {
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isSearching) {
                        item {
                            ListItem(
                                headlineContent = { Text("Searching places...") },
                                leadingContent = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            )
                        }
                    }

                    searchError?.let { message ->
                        item {
                            ListItem(
                                headlineContent = { Text(message) }
                            )
                        }
                    }

                    items(suggestions) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.title) },
                            supportingContent = {
                                suggestion.subtitle?.let { Text(it) }
                            },
                            modifier = Modifier.clickable { selectSuggestion(suggestion) }
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

private fun Exception.toSearchErrorMessage(): String {
    val apiException = this as? ApiException
    return when (apiException?.statusCode) {
        9011 -> "Places access denied. Enable Places API (New) for this key and check package/SHA-1 restrictions."
        else -> "Search failed: ${apiException?.statusCode ?: "unknown"} ${message ?: "Check internet and API key."}"
    }
}

private fun Exception.isPlacesAccessDenied(): Boolean {
    return (this as? ApiException)?.statusCode == 9011
}

private fun Exception.toPlaceDetailsErrorMessage(): String {
    val apiException = this as? ApiException
    return when (apiException?.statusCode) {
        9011 -> "Place details access denied. Check Places API (New) and API key restrictions."
        else -> "Could not open that place: ${apiException?.statusCode ?: "unknown"} ${message ?: "Try another result."}"
    }
}

private fun AutocompletePrediction.toSearchSuggestion(): SearchSuggestion {
    val title = getPrimaryText(null).toString()
    val subtitle = getSecondaryText(null).toString().takeIf { it.isNotBlank() }

    return SearchSuggestion(
        title = title.ifBlank { getFullText(null).toString() },
        subtitle = subtitle,
        placeId = placeId
    )
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
