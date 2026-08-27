package com.example.model

enum class SignalType {
    BLUETOOTH,
    WIFI,
    EMF
}

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
    val customAlias: String? = null,
    val signalType: SignalType = SignalType.BLUETOOTH,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isSecure: Boolean? = null,
    val rawScanRecord: ByteArray? = null,
    val serviceUuids: List<String> = emptyList(),
    val ipAddress: String? = null,
    val wifiFrequency: Int? = null
) {
    val displayName: String
        get() = customAlias?.takeIf { it.isNotBlank() } ?: name.ifBlank { 
            if (signalType == SignalType.WIFI) "Wi-Fi AP (${macAddress.takeLast(5)})"
            else if (signalType == SignalType.EMF) "EMF Anomaly"
            else "Unknown (${macAddress.takeLast(5)})" 
        }
}
