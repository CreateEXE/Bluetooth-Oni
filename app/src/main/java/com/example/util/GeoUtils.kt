package com.example.util

import kotlin.math.*

object GeoUtils {
    // Estimate distance based on RSSI and txPower (usually -59 for BLE)
    fun calculateDistance(rssi: Int, txPower: Int = -59): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / txPower
        if (ratio < 1.0) {
            return ratio.pow(10.0)
        }
        return (0.89976) * ratio.pow(7.7095) + 0.111
    }

    // Parse BLE appearance if available to map to Major Device Class roughly
    fun getMajorDeviceClassFromAppearance(scanRecordBytes: ByteArray?): Int {
        if (scanRecordBytes == null) return android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED
        // A full parser would go through AD structures finding type 0x19
        // For simplicity, we'll return UNCATEGORIZED and rely on the device class first.
        return android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED
    }
}
