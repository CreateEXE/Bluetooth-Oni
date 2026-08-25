package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceLocationDao {
    @Insert
    suspend fun insertLocation(location: DeviceLocationEntity)

    @Query("SELECT * FROM device_locations WHERE macAddress = :macAddress ORDER BY timestamp DESC LIMIT 50")
    fun getLocationsForDevice(macAddress: String): Flow<List<DeviceLocationEntity>>

    @Query("DELETE FROM device_locations WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteOldLocations(olderThanTimestamp: Long)
}
