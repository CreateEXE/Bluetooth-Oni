package com.example.model

data class TrackedDevice(
    val macAddress: String,
    val name: String,
    val rssi: Int,
    val distanceMeters: Double,
    val majorDeviceClass: Int,
    val isConnectable: Boolean,
    val deviceCategory: DeviceCategory,
    val customAlias: String? = null
) {
    val displayName: String
        get() = customAlias?.takeIf { it.isNotBlank() } ?: name.ifBlank { "Unknown Device" }
}
