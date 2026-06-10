package com.example.wakemethere.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.wakemethere.R
import com.example.wakemethere.ui.JourneyViewModel
import com.example.wakemethere.util.AlarmHelper
import com.example.wakemethere.util.LocationHelper
import com.google.android.gms.location.*
import java.util.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var alarmHelper: AlarmHelper

    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var thresholdMinutes: Int = 15
    private var isAlarmTriggered = false

    companion object {
        const val CHANNEL_ID = "LocationServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        const val EXTRA_THRESHOLD = "EXTRA_THRESHOLD"
        
        // Static instance for simplicity to communicate with ViewModel
        // In a real app, use a Repository or LocalBroadcastManager
        var lastETA: Int = 0
            private set
    }


    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        alarmHelper = AlarmHelper(this)
        createNotificationChannel()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isAlarmTriggered) return
                
                for (location in locationResult.locations) {
                    val distance = LocationHelper.calculateDistance(
                        location.latitude, location.longitude,
                        destLat, destLng
                    )
                    val eta = LocationHelper.estimateTime(distance)
                    lastETA = eta
                    
                    if (eta <= thresholdMinutes) {
                        triggerAlarm()
                    } else {
                        updateNotification("Tracking: ETA ${eta} min ($distance m)")
                    }

                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                destLat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                destLng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                thresholdMinutes = intent.getIntExtra(EXTRA_THRESHOLD, 15)
                startTracking()
            }
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun triggerAlarm() {
        isAlarmTriggered = true
        alarmHelper.startAlarm()
        updateNotification("WAKE UP! You are near your destination.")
        // Here we could also launch an activity
    }

    private fun startTracking() {
        val notification = createNotification("Starting journey tracking...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (unlikely: SecurityException) {
            // Handle error
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        alarmHelper.stopAlarm()
        stopForeground(true)
        stopSelf()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WakeMeThere")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
