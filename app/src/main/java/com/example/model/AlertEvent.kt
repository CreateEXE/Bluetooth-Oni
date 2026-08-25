package com.example.model

data class AlertEvent(
    val id: Long = System.currentTimeMillis(),
    val macAddress: String,
    val deviceName: String,
    val message: String,
    val isSecurityBreach: Boolean = false
)
