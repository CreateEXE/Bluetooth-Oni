package com.example.model

data class TrackedDevice(
    val macAddress: String,
    val name: String,
    val rssi: Int,
    val distanceMeters: Double,
    val majorDeviceClass: Int,
    val isConnectable: Boolean,
    val deviceCategory: DeviceCategory,
    val vendor: String = "Generic",
    val txPower: Int = -59,
    val customAlias: String? = null
) {
    val displayName: String
        get() = customAlias?.takeIf { it.isNotBlank() } ?: name.ifBlank { "Unknown (${macAddress.takeLast(5)})" }
}
