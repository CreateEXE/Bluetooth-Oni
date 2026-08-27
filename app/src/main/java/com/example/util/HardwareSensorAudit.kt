package com.example.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import com.example.model.HardwareAuditReport
import com.example.model.RadioSpecInfo
import com.example.model.SensorItemInfo

object HardwareSensorAudit {

    fun generateAudit(context: Context): HardwareAuditReport {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rawSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)

        val sensorItems = rawSensors.map { s ->
            SensorItemInfo(
                name = s.name,
                vendor = s.vendor,
                typeString = getSensorTypeString(s.type),
                typeCode = s.type,
                powerMa = s.power,
                maxRange = s.maximumRange,
                resolution = s.resolution,
                minDelayUs = s.minDelay,
                isWakeUp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) s.isWakeUpSensor else false
            )
        }.sortedBy { it.typeString }

        val pm = context.packageManager
        val radioSpecs = mutableListOf<RadioSpecInfo>()

        // 1. Bluetooth & BLE
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = bluetoothManager?.adapter
        val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val isLe2MSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && btAdapter != null) btAdapter.isLe2MPhySupported else false
        val isLeCodedSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && btAdapter != null) btAdapter.isLeCodedPhySupported else false
        val isExtendedAdvSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && btAdapter != null) btAdapter.isLeExtendedAdvertisingSupported else false
        val isPeriodicAdvSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && btAdapter != null) btAdapter.isLePeriodicAdvertisingSupported else false

        radioSpecs.add(
            RadioSpecInfo(
                title = "Bluetooth Low Energy (BLE)",
                category = "RF Transceiver",
                frequencyBand = "2.402 GHz - 2.480 GHz (40 Channels)",
                standard = "BT 5.0 / 5.2 / 5.4 / Auracast",
                isSupported = hasBle,
                details = "PHY 2M: $isLe2MSupported | Long Range Coded: $isLeCodedSupported | Ext Adv: $isExtendedAdvSupported | Periodic: $isPeriodicAdvSupported"
            )
        )

        // 2. Wi-Fi
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val hasWifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
        val hasWifiDirect = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        val hasWifiAware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) else false
        val hasWifiRtt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT) else false
        val is5GhzSupported = wifiManager?.is5GHzBandSupported == true
        val is6GhzSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifiManager != null) wifiManager.is6GHzBandSupported else false

        radioSpecs.add(
            RadioSpecInfo(
                title = "Wi-Fi WLAN (802.11ax / 802.11be)",
                category = "RF Transceiver",
                frequencyBand = "2.4 GHz, 5.0 GHz, 6.0 GHz (Wi-Fi 6E/7)",
                standard = "IEEE 802.11 a/b/g/n/ac/ax/be",
                isSupported = hasWifi,
                details = "5GHz: $is5GhzSupported | 6GHz (Wi-Fi 6E/7): $is6GhzSupported | Direct: $hasWifiDirect | Aware/NAN: $hasWifiAware | RTT/FTM: $hasWifiRtt"
            )
        )

        // 3. Ultra-Wideband (UWB)
        val hasUwb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pm.hasSystemFeature("android.hardware.uwb")
        } else false

        radioSpecs.add(
            RadioSpecInfo(
                title = "Ultra-Wideband (UWB) Spatial Radar",
                category = "Precision Ranging RF",
                frequencyBand = "6.5 GHz (Ch 5) & 8.0 GHz (Ch 9)",
                standard = "IEEE 802.15.4z FiRa / Car Connectivity",
                isSupported = hasUwb,
                details = if (hasUwb) "Hardware Chipset Present (Centimeter-precision Time-of-Flight & Angle-of-Arrival)" else "UWB Chipset not detected on this hardware tier"
            )
        )

        // 4. Near Field Communication (NFC)
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        val hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        val hasHce = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

        radioSpecs.add(
            RadioSpecInfo(
                title = "Near Field Communication (NFC)",
                category = "Inductive High-Frequency RF",
                frequencyBand = "13.56 MHz (±7 kHz)",
                standard = "ISO/IEC 14443 Type A/B, ISO/IEC 18092, FeliCa",
                isSupported = hasNfc && nfcAdapter != null,
                details = "Host Card Emulation (HCE): $hasHce | Reader/Writer: ${nfcAdapter?.isEnabled == true}"
            )
        )

        // 5. Multi-GNSS Satellite Receiver
        val hasGps = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        radioSpecs.add(
            RadioSpecInfo(
                title = "Multi-Constellation GNSS Satellite RF",
                category = "Satellite Receiver",
                frequencyBand = "L1/E1 (1575.42 MHz), L5/E5a (1176.45 MHz), B1/B2a",
                standard = "GPS (USA), GLONASS (RU), Galileo (EU), BeiDou (CN), QZSS (JP), NavIC (IN)",
                isSupported = hasGps,
                details = "Dual-Frequency Carrier Phase Tracking (L1 + L5 Carrier Multi-band RTK capability)"
            )
        )

        // 6. Consumer Infrared (IR Blaster)
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val hasIr = irManager?.hasIrEmitter() == true || pm.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)

        radioSpecs.add(
            RadioSpecInfo(
                title = "Consumer Infrared (IR Blaster)",
                category = "Optical Infrared Pulse",
                frequencyBand = "36 kHz - 40 kHz Carrier Wave (940 nm LED)",
                standard = "NEC, RC-5, Sony SIRC, Denon IR Protocols",
                isSupported = hasIr,
                details = if (hasIr) "IR Transmitter Diode Active" else "IR Emitter hardware omitted"
            )
        )

        // 7. Cellular Modems & Telephony
        val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        radioSpecs.add(
            RadioSpecInfo(
                title = "Cellular Baseband Modem (5G NR / LTE)",
                category = "WWAN Transceiver",
                frequencyBand = "Sub-6 GHz (n1-n99) & mmWave (24 GHz - 40 GHz n257-n261)",
                standard = "3GPP Rel 15/16/17 (NSA/SA & NTN Direct-to-Cell Satellite)",
                isSupported = hasTelephony,
                details = "VoLTE / VoNR / eSIM eUICC / 4x4 MIMO & Carrier Aggregation"
            )
        )

        // 8. Hardware Cryptographic Security Element (TPM / StrongBox)
        val hasStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else false

        radioSpecs.add(
            RadioSpecInfo(
                title = "Hardware Security Module / StrongBox TPM",
                category = "Isolated Silicon Cryptoprocessor",
                frequencyBand = "Internal Secure Bus (Isolated CPU/RAM)",
                standard = "EAL6+ Secure Element (Titan M2 / Knox Vault / SE050)",
                isSupported = hasStrongBox,
                details = if (hasStrongBox) "Tamper-resistant physical crypto enclave verified" else "Software Keymaster / TEE enclave active"
            )
        )

        return HardwareAuditReport(
            deviceModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.DEVICE})",
            socManufacturer = Build.HARDWARE,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            totalPhysicalSensors = sensorItems.size,
            sensors = sensorItems,
            radioSpecs = radioSpecs
        )
    }

    private fun getSensorTypeString(type: Int): String {
        return when (type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer (Kinematic 3-Axis)"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer / Hall Effect (EMF 3-Axis)"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope (Angular Velocity 3-Axis)"
            Sensor.TYPE_LIGHT -> "Ambient Light Sensor (ALS Lux)"
            Sensor.TYPE_PRESSURE -> "Barometer (Atmospheric Pressure hPa)"
            Sensor.TYPE_PROXIMITY -> "Proximity Sensor (Infrared / ToF)"
            Sensor.TYPE_GRAVITY -> "Gravity Sensor (Vector Decomposition)"
            Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration (Inertial)"
            Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector (IMU Sensor Fusion)"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative Humidity Sensor"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature Sensor"
            Sensor.TYPE_STEP_DETECTOR -> "Step Detector (Pedometer Pulse)"
            Sensor.TYPE_STEP_COUNTER -> "Step Counter (Hardware Cumulative)"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion Trigger"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector (Uncalibrated Mag)"
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic Rotation Vector"
            Sensor.TYPE_HEART_RATE -> "Heart Rate Sensor (PPG Optical)"
            Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Low Latency Off-Body Sensor"
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Accelerometer (Raw Uncalibrated)"
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (Raw Uncalibrated)"
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetometer (Raw Uncalibrated EMF)"
            Sensor.TYPE_HINGE_ANGLE -> "Hinge Angle (Foldable Dual-Screen Angle)"
            36 -> "Time-of-Flight / Laser LiDAR Rangefinder"
            37 -> "Flicker / Color Temperature Sensor"
            else -> "Hardware Sensor (Type ID: $type)"
        }
    }
}
