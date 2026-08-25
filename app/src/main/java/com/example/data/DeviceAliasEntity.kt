package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_aliases")
data class DeviceAliasEntity(
    @PrimaryKey val macAddress: String,
    val customName: String
)
