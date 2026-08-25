package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_locations")
data class DeviceLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macAddress: String,
    val deviceName: String?,
    val deviceCategory: String,
    val distanceMeters: Double,
    val timestamp: Long
)
