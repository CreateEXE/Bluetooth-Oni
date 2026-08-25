package com.example.model

data class AlertSettings(
    val enabled: Boolean = false,
    val distanceThresholdMeters: Double = 5.0,
    val alertType: AlertType = AlertType.IN_RANGE,
    val isHostageMode: Boolean = false // Security perimeter toggle (Killswitch)
)

enum class AlertType {
    IN_RANGE,
    OUT_OF_RANGE
}
