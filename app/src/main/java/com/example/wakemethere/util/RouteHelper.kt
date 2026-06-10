package com.example.wakemethere.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RouteHelper {

    data class RouteResult(
        val points: List<LatLng>,
        val distanceMeters: Int,
        val durationSeconds: Int,
        val errorDetail: String? = null
    )

    suspend fun fetchShortestDrivingRoute(
        context: Context,
        start: LatLng,
        destination: LatLng
    ): RouteResult? = withContext(Dispatchers.IO) {
        val apiKey = try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            null
        } ?: return@withContext null

        val urlString = "https://routes.googleapis.com/directions/v2:computeRoutes"

        val requestBody = JSONObject().apply {
            put("origin", JSONObject().apply {
                put("location", JSONObject().apply {
                    put("latLng", JSONObject().apply {
                        put("latitude", start.latitude)
                        put("longitude", start.longitude)
                    })
                })
            })
            put("destination", JSONObject().apply {
                put("location", JSONObject().apply {
                    put("latLng", JSONObject().apply {
                        put("latitude", destination.latitude)
                        put("longitude", destination.longitude)
                    })
                })
            })
            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_UNAWARE")
        }

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-Api-Key", apiKey)
            connection.setRequestProperty("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline")
            connection.doOutput = true

            connection.outputStream.use { os ->
                val input = requestBody.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("RouteHelper", "Routes API Error Code: $responseCode. Message: $errorText")
                
                val userFriendlyMessage = when(responseCode) {
                    403 -> "403 Forbidden: Ensure 'Routes API' is enabled and your API Key has no restrictions preventing its use."
                    401 -> "401 Unauthorized: Invalid API Key."
                    else -> "Routes API Error: $responseCode - $errorText"
                }
                return@withContext RouteResult(emptyList(), 0, 0, userFriendlyMessage)
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val routes = json.optJSONArray("routes")
            
            if (routes == null || routes.length() == 0) {
                return@withContext RouteResult(emptyList(), 0, 0, "No routes found.")
            }

            val route = routes.getJSONObject(0)
            // Fix for duration and distance in Routes API
            val distance = route.optInt("distanceMeters")
            val durationString = route.optString("duration")
            val duration = durationString.replace("s", "").toDoubleOrNull()?.toInt() ?: 0
            
            val encodedPolyline = route.getJSONObject("polyline").getString("encodedPolyline")
            val points = decodePolyline(encodedPolyline)

            RouteResult(points, distance, duration)
        } catch (e: Exception) {
            Log.e("RouteHelper", "Failed to fetch route", e)
            null
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }
}
