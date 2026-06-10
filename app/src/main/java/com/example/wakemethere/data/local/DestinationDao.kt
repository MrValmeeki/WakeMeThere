package com.example.wakemethere.data.local

import androidx.room.*
import com.example.wakemethere.data.model.Destination
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations ORDER BY timestamp DESC")
    fun getAllDestinations(): Flow<List<Destination>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDestination(destination: Destination)

    @Delete
    suspend fun deleteDestination(destination: Destination)

    @Query("DELETE FROM destinations")
    suspend fun deleteAll()
}
