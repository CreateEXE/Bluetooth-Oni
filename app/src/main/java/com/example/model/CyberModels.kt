package com.example.model

data class GattServiceInfo(
    val uuid: String,
    val name: String,
    val isPrimary: Boolean,
    val characteristics: List<GattCharacteristicInfo>
)

data class GattCharacteristicInfo(
    val uuid: String,
    val name: String,
    val properties: List<String>,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canNotify: Boolean,
    val readValue: String? = null,
    val readBytesHex: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PortProbeResult(
    val port: Int,
    val serviceName: String,
    val isOpen: Boolean,
    val latencyMs: Long = 0,
    val banner: String? = null
)

data class BeaconDecodedData(
    val protocol: String, // "Apple AirTag / FindMy", "iBeacon", "Google Fast Pair", "Eddystone", "Custom BLE"
    val companyName: String,
    val companyIdHex: String,
    val uuid: String? = null,
    val major: Int? = null,
    val minor: Int? = null,
    val txPowerCalibrated: Int? = null,
    val flags: List<String> = emptyList(),
    val rawHexPayload: String = "",
    val hexMatrixLines: List<HexLine> = emptyList()
)

data class HexLine(
    val offsetHex: String,
    val bytesHex: String,
    val asciiPreview: String
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    CRITICAL,
    DATA
}

data class CyberLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)
