package com.example.wakemethere.util

import android.location.Location

object LocationHelper {
    /**
     * Calculates the distance between two points in meters.
     */
    fun calculateDistance(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }

    /**
     * Estimates travel time in minutes based on distance and average speed (m/s).
     * Average speed defaults to 13.8 m/s (~50 km/h).
     */
    fun estimateTime(distanceMeters: Float, averageSpeedMps: Float = 13.8f): Int {
        return (distanceMeters / averageSpeedMps / 60).toInt()
    }
}
