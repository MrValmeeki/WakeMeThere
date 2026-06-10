package com.example.wakemethere.data

import com.example.wakemethere.data.local.DestinationDao
import com.example.wakemethere.data.model.Destination
import kotlinx.coroutines.flow.Flow

class DestinationRepository(private val destinationDao: DestinationDao) {
    val allDestinations: Flow<List<Destination>> = destinationDao.getAllDestinations()

    suspend fun insert(destination: Destination) {
        destinationDao.insertDestination(destination)
    }

    suspend fun delete(destination: Destination) {
        destinationDao.deleteDestination(destination)
    }
}
