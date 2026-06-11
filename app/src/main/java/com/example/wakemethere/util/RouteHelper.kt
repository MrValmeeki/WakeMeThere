package com.example.wakemethere.util

import android.util.Log
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

    /**
     * Fetches the shortest driving route using OSRM (Open Source Routing Machine).
     */
    suspend fun fetchShortestDrivingRoute(
        start: LatLng,
        destination: LatLng
    ): RouteResult? = withContext(Dispatchers.IO) {
        // OSRM expects {longitude},{latitude}
        // Use http as fallback if https has issues on some devices
        val baseUrl = "https://router.project-osrm.org/route/v1/driving/"
        val coordinates = "${start.longitude},${start.latitude};${destination.longitude},${destination.latitude}"
        val params = "?overview=full&geometries=polyline"
        val urlString = baseUrl + coordinates + params

        Log.d("RouteHelper", "Fetching route: $urlString")

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "WakeMeThere-Android-App")

            val responseCode = connection.responseCode
            Log.d("RouteHelper", "Response Code: $responseCode")

            if (responseCode != 200) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("RouteHelper", "OSRM API Error: $responseCode - $errorText")
                return@withContext RouteResult(emptyList(), 0, 0, "OSRM Error: $responseCode")
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val code = json.optString("code")
            
            if (code != "Ok") {
                Log.e("RouteHelper", "OSRM returned code: $code")
                return@withContext RouteResult(emptyList(), 0, 0, "No routes found (Code: $code).")
            }

            val routes = json.optJSONArray("routes")
            if (routes == null || routes.length() == 0) {
                Log.e("RouteHelper", "OSRM returned no routes")
                return@withContext RouteResult(emptyList(), 0, 0, "No routes found.")
            }

            val route = routes.getJSONObject(0)
            val distance = route.optDouble("distance").toInt()
            val duration = route.optDouble("duration").toInt()
            
            val encodedPolyline = route.optString("geometry")
            Log.d("RouteHelper", "Encoded polyline length: ${encodedPolyline.length}")
            
            val points = if (encodedPolyline.isNotEmpty()) decodePolyline(encodedPolyline) else emptyList()
            Log.d("RouteHelper", "Decoded ${points.size} points")

            RouteResult(points, distance, duration)
        } catch (e: Exception) {
            Log.e("RouteHelper", "Failed to fetch route from OSRM", e)
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
