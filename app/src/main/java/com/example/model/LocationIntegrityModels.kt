package com.example.model

enum class LocationIntegrityLevel {
    AUTHENTIC_GNSS,
    SUSPICIOUS_MOCK,
    VPN_CLOAKED,
    COARSE_CELLULAR,
    EMULATOR_VIRTUAL,
    NO_FIX
}

data class LocationIntegrityReport(
    val isVpnActive: Boolean = false,
    val vpnInterfaceName: String? = null,
    val isMockLocation: Boolean = false,
    val isProxyActive: Boolean = false,
    val proxyDetails: String? = null,
    val locationProvider: String = "unknown",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val locationAgeSeconds: Long = 0L,
    val isEmulator: Boolean = false,
    val integrityLevel: LocationIntegrityLevel = LocationIntegrityLevel.NO_FIX,
    val anomalyWarnings: List<String> = emptyList(),
    val diagnosticSummary: String = ""
)

data class IntegrityBadgeStyle(
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val borderColor: androidx.compose.ui.graphics.Color,
    val title: String,
    val tintColor: androidx.compose.ui.graphics.Color
)
