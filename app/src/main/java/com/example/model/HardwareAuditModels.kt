package com.example.model

data class SensorItemInfo(
    val name: String,
    val vendor: String,
    val typeString: String,
    val typeCode: Int,
    val powerMa: Float,
    val maxRange: Float,
    val resolution: Float,
    val minDelayUs: Int,
    val isWakeUp: Boolean
)

data class RadioSpecInfo(
    val title: String,
    val category: String,
    val frequencyBand: String,
    val standard: String,
    val isSupported: Boolean,
    val details: String
)

data class HardwareAuditReport(
    val deviceModel: String,
    val socManufacturer: String,
    val androidVersion: String,
    val totalPhysicalSensors: Int,
    val sensors: List<SensorItemInfo>,
    val radioSpecs: List<RadioSpecInfo>
)
