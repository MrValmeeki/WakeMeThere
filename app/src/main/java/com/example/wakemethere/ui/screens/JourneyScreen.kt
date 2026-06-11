package com.example.wakemethere.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.wakemethere.service.LocationService
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.ui.navigation.Screen
import com.example.wakemethere.util.LatLng
import com.example.wakemethere.util.RouteHelper
import com.example.wakemethere.util.rememberMapViewWithLifecycle
import com.google.android.gms.location.LocationServices
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JourneyScreen(navController: NavController, viewModel: JourneyViewModel) {
    val context = LocalContext.current
    val destinationName by viewModel.destinationName
    val selectedLocation by viewModel.selectedLocation
    val currentETA by viewModel.currentETA
    val startETA by viewModel.startETA
    
    var showLiveMap by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isRouteLoading by remember { mutableStateOf(false) }
    
    val liveMapView = rememberMapViewWithLifecycle()

    // Progress Calculation
    val progress = remember(currentETA, startETA) {
        if (startETA <= 0) 0f 
        else if (currentETA <= 0) 1f
        else (1.0f - (currentETA.toFloat() / startETA.toFloat())).coerceIn(0f, 1f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = LinearOutSlowInEasing),
        label = "progress"
    )

    // Permission check for Live Map
    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        userLocation = LatLng(it.latitude, it.longitude)
                    }
                }
            } catch (e: SecurityException) { }
        }
    }

    LaunchedEffect(userLocation, selectedLocation) {
        val start = userLocation
        val destination = selectedLocation

        routePoints = if (start != null && destination != null) {
            isRouteLoading = true
            val result = RouteHelper.fetchShortestDrivingRoute(start, destination)
            isRouteLoading = false
            result?.points ?: listOf(start, destination)
        } else {
            isRouteLoading = false
            emptyList()
        }
    }

    // Globe Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "globe")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Stripe offset animation
    val stripeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 30.dp.value,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stripes"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Tracking Journey",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = destinationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = { showLiveMap = true }) {
                Icon(Icons.Default.Map, contentDescription = "View Map", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Globe Animation
        val globeTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = globeTrackColor,
                    radius = 120.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )
            }

            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .rotate(rotation),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = this.center
                    drawCircle(color = Color.White.copy(alpha = 0.2f), radius = 35.dp.toPx(), center = c.plus(Offset(-25f, -15f)))
                    drawCircle(color = Color.White.copy(alpha = 0.15f), radius = 30.dp.toPx(), center = c.plus(Offset(30f, 25f)))
                }
            }

            val angleRad = Math.toRadians(rotation.toDouble())
            val orbitRadius = 120.dp
            Box(
                modifier = Modifier.offset(
                    x = (orbitRadius.value * cos(angleRad)).dp,
                    y = (orbitRadius.value * sin(angleRad)).dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AirplanemodeActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp).rotate(rotation + 180f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.35f))

        // ETA Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (currentETA > 0) "$currentETA" else "--",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "MINUTES TO ARRIVAL",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        JourneyProgressBar(progress = animatedProgress, stripeOffset = stripeOffset)

        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = {
                val intent = Intent(context, LocationService::class.java).apply { action = LocationService.ACTION_STOP }
                context.startService(intent)
                navController.popBackStack(Screen.Home.route, inclusive = false)
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("STOP JOURNEY", fontWeight = FontWeight.ExtraBold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }

    // Live Map Dialog
    if (showLiveMap) {
        Dialog(
            onDismissRequest = { showLiveMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { liveMapView },
                        modifier = Modifier.fillMaxSize(),
                        update = { mv ->
                            mv.getMapAsync { map ->
                                if (map.style == null) {
                                    map.setStyle(Style.Builder().fromUri("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")) { style ->
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

                                // Handle elements only after style is ready
                                map.getStyle { style ->
                                    map.clear()
                                    
                                    val visiblePoints = routePoints.ifEmpty {
                                        listOfNotNull(userLocation, selectedLocation)
                                    }

                                    if (visiblePoints.size > 1) {
                                        val boundsBuilder = LatLngBounds.Builder()
                                        visiblePoints.forEach { boundsBuilder.include(MapLibreLatLng(it.latitude, it.longitude)) }
                                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120))
                                    } else {
                                        userLocation?.let {
                                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapLibreLatLng(it.latitude, it.longitude), 15.0))
                                        }
                                    }

                                    selectedLocation?.let { dest ->
                                        map.addMarker(MarkerOptions()
                                            .position(MapLibreLatLng(dest.latitude, dest.longitude))
                                            .title(destinationName))
                                        
                                        if (routePoints.size >= 2) {
                                            map.addPolyline(PolylineOptions()
                                                .addAll(routePoints.map { MapLibreLatLng(it.latitude, it.longitude) })
                                                .color(android.graphics.Color.parseColor("#1B1B1B"))
                                                .width(5f))
                                        }
                                    }
                                }
                            }
                        }
                    )

                    if (isRouteLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                    IconButton(
                        onClick = { showLiveMap = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.White, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Map")
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyProgressBar(progress: Float, stripeOffset: Float) {
    val accentColor = MaterialTheme.colorScheme.primary // ScooterYellow
    val darkAccent = MaterialTheme.colorScheme.secondary // DarkCharcoal
    val trackColor = darkAccent.copy(alpha = 0.15f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Progress",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val handleSize = 44.dp
            val endIconSize = 42.dp
            val handleOffset = (maxWidth - handleSize) * progress.coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(trackColor)
                    .align(Alignment.CenterStart)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stripeSpacing = 18.dp.toPx()
                    val stripeStroke = 3.dp.toPx()
                    val totalWidth = stripeSpacing
                    
                    var x = -size.height + (stripeOffset % totalWidth)

                    while (x < size.width + size.height) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(x, size.height),
                            end = Offset(x + size.height * 0.65f, 0f),
                            strokeWidth = stripeStroke
                        )
                        x += stripeSpacing
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor)
                )
            }

            Surface(
                modifier = Modifier
                    .offset(x = handleOffset)
                    .size(handleSize)
                    .align(Alignment.CenterStart),
                shape = RoundedCornerShape(13.dp),
                color = accentColor,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp
            ) {
                Icon(
                    Icons.Default.DirectionsBus,
                    contentDescription = "Journey progress",
                    tint = darkAccent,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .size(endIconSize)
                    .align(Alignment.CenterEnd),
                shape = RoundedCornerShape(13.dp),
                color = darkAccent,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Destination",
                    tint = accentColor,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
