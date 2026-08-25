package com.example.util

import android.bluetooth.BluetoothClass
import android.os.ParcelUuid
import com.example.model.DeviceCategory

object BleUtils {

    // Common 16-bit UUIDs for BLE services (Base UUID: 0000XXXX-0000-1000-8000-00805f9b34fb)
    private const val UUID_HEART_RATE = "180D"
    private const val UUID_BLOOD_PRESSURE = "1810"
    private const val UUID_HEALTH_THERMOMETER = "1809"
    private const val UUID_HID = "1812" // Keyboard, Mouse
    private const val UUID_AUDIO_STREAM_CTRL = "184E"
    private const val UUID_MICROPHONE_CTRL = "184D"
    private const val UUID_VOLUME_CTRL = "1844"
    
    // Some common custom UUID prefixes for cameras (e.g., GoPro, Sony)
    private val CAMERA_UUID_PREFIXES = listOf("FEA6", "FEA0")

    fun categorizeDevice(serviceUuids: List<ParcelUuid>?, majorDeviceClass: Int): DeviceCategory {
        // 1. Check GATT Profile UUIDs first for more precise BLE categorization
        if (serviceUuids != null) {
            for (parcelUuid in serviceUuids) {
                val uuidString = parcelUuid.uuid.toString().uppercase()
                
                // Extract the 16-bit part if it matches the standard base UUID
                val shortUuid = if (uuidString.startsWith("0000") && uuidString.endsWith("-0000-1000-8000-00805F9B34FB")) {
                    uuidString.substring(4, 8)
                } else {
                    ""
                }

                if (shortUuid == UUID_HEART_RATE || shortUuid == UUID_BLOOD_PRESSURE || shortUuid == UUID_HEALTH_THERMOMETER) {
                    return DeviceCategory.HEALTH
                }
                if (shortUuid == UUID_HID) {
                    return DeviceCategory.PERIPHERAL
                }
                if (shortUuid == UUID_AUDIO_STREAM_CTRL || shortUuid == UUID_MICROPHONE_CTRL || shortUuid == UUID_VOLUME_CTRL) {
                    return DeviceCategory.AUDIO_VIDEO
                }
                
                // Check custom camera prefixes
                if (CAMERA_UUID_PREFIXES.any { uuidString.contains(it) }) {
                    return DeviceCategory.CAMERA
                }
            }
        }

        // 2. Fallback to classic Bluetooth Major Device Class if GATT doesn't give a specific answer
        return when (majorDeviceClass) {
            BluetoothClass.Device.Major.PHONE -> DeviceCategory.PHONE
            BluetoothClass.Device.Major.COMPUTER -> DeviceCategory.COMPUTER
            BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceCategory.AUDIO_VIDEO
            BluetoothClass.Device.Major.WEARABLE -> DeviceCategory.WEARABLE
            BluetoothClass.Device.Major.HEALTH -> DeviceCategory.HEALTH
            BluetoothClass.Device.Major.PERIPHERAL -> DeviceCategory.PERIPHERAL
            BluetoothClass.Device.Major.IMAGING -> DeviceCategory.CAMERA
            else -> DeviceCategory.UNCATEGORIZED
        }
    }
}
