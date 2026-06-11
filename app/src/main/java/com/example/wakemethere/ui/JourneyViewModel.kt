package com.example.wakemethere.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakemethere.data.DestinationRepository
import com.example.wakemethere.data.local.AppDatabase
import com.example.wakemethere.data.model.Destination
import com.example.wakemethere.service.LocationService
import com.example.wakemethere.util.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class JourneyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DestinationRepository
    val savedDestinations: StateFlow<List<Destination>>

    init {
        val dao = AppDatabase.getDatabase(application).destinationDao()
        repository = DestinationRepository(dao)
        savedDestinations = repository.allDestinations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Poll for ETA updates from Service
        viewModelScope.launch {
            while(true) {
                if (LocationService.lastETA > 0) {
                    setETAs(LocationService.lastETA)
                }
                delay(2000)
            }
        }
    }


    private val _thresholdMinutes = mutableStateOf(15)
    val thresholdMinutes: State<Int> = _thresholdMinutes

    private val _selectedLocation = mutableStateOf<LatLng?>(null)
    val selectedLocation: State<LatLng?> = _selectedLocation

    private val _destinationName = mutableStateOf("")
    val destinationName: State<String> = _destinationName

    private val _startETA = mutableStateOf(0)
    val startETA: State<Int> = _startETA

    private val _currentETA = mutableStateOf(0)
    val currentETA: State<Int> = _currentETA

    fun setThreshold(minutes: Int) {
        _thresholdMinutes.value = minutes
    }

    fun setDestination(latLng: LatLng, name: String) {
        _selectedLocation.value = latLng
        _destinationName.value = name
    }

    fun setETAs(eta: Int) {
        if (_startETA.value == 0 || eta > _startETA.value) {
            _startETA.value = eta
        }
        _currentETA.value = eta
    }

    fun resetETAs() {
        _startETA.value = 0
        _currentETA.value = 0
    }

    fun saveDestination() {
        val latLng = _selectedLocation.value
        val name = _destinationName.value
        if (latLng != null && name.isNotEmpty()) {
            viewModelScope.launch {
                repository.insert(Destination(name = name, latitude = latLng.latitude, longitude = latLng.longitude))
            }
        }
    }
    
    fun deleteDestination(destination: Destination) {
        viewModelScope.launch {
            repository.delete(destination)
        }
    }
}
