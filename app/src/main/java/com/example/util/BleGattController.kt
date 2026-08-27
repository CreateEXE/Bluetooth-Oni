package com.example.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.example.model.CyberLogEntry
import com.example.model.GattCharacteristicInfo
import com.example.model.GattServiceInfo
import com.example.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class BleGattController(private val context: Context) {

    private var activeGatt: BluetoothGatt? = null
    private var isConnected = false

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services = _services.asStateFlow()

    private val _connectionState = MutableStateFlow("DISCONNECTED")
    val connectionState = _connectionState.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<CyberLogEntry>>(emptyList())
    val terminalLogs = _terminalLogs.asStateFlow()

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = CyberLogEntry(tag = tag, message = message, level = level)
        _terminalLogs.update { current ->
            (listOf(entry) + current).take(200) // Keep latest 200 logs
        }
    }

    fun clearLogs() {
        _terminalLogs.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String, adapter: BluetoothAdapter?) {
        disconnect()
        log("SYS", "Initializing GATT connection pipeline to $deviceAddress...", LogLevel.INFO)
        _connectionState.value = "CONNECTING..."

        val device: BluetoothDevice? = try {
            adapter?.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            log("ERR", "Invalid MAC address format: ${e.message}", LogLevel.CRITICAL)
            _connectionState.value = "FAILED"
            return
        }

        if (device == null) {
            log("ERR", "Target device node unreachable in Bluetooth registry.", LogLevel.CRITICAL)
            _connectionState.value = "FAILED"
            return
        }

        activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (activeGatt != null) {
            log("SYS", "Disconnecting active GATT session...", LogLevel.WARNING)
            try {
                activeGatt?.disconnect()
                activeGatt?.close()
            } catch (e: Exception) {
                // Ignore
            }
            activeGatt = null
        }
        isConnected = false
        _connectionState.value = "DISCONNECTED"
        _services.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuidStr: String, charUuidStr: String) {
        val gatt = activeGatt ?: run {
            log("ERR", "No active GATT session.", LogLevel.CRITICAL)
            return
        }

        try {
            val service = gatt.getService(UUID.fromString(serviceUuidStr))
            val char = service?.getCharacteristic(UUID.fromString(charUuidStr))
            if (char != null) {
                log("GATT", "Reading characteristic $charUuidStr...", LogLevel.INFO)
                gatt.readCharacteristic(char)
            } else {
                log("ERR", "Characteristic not found.", LogLevel.CRITICAL)
            }
        } catch (e: Exception) {
            log("ERR", "Read error: ${e.message}", LogLevel.CRITICAL)
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(serviceUuidStr: String, charUuidStr: String, payloadStr: String) {
        val gatt = activeGatt ?: run {
            log("ERR", "No active GATT session.", LogLevel.CRITICAL)
            return
        }

        try {
            val service = gatt.getService(UUID.fromString(serviceUuidStr))
            val char = service?.getCharacteristic(UUID.fromString(charUuidStr))
            if (char != null) {
                log("HACK", "Injecting payload [$payloadStr] into characteristic...", LogLevel.WARNING)
                val bytes = payloadStr.toByteArray(Charsets.UTF_8)
                char.value = bytes
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val success = gatt.writeCharacteristic(char)
                if (success) {
                    log("TX", "Payload transmission initiated.", LogLevel.SUCCESS)
                } else {
                    log("ERR", "Payload transmission rejected by device controller.", LogLevel.CRITICAL)
                }
            }
        } catch (e: Exception) {
            log("ERR", "Write error: ${e.message}", LogLevel.CRITICAL)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                _connectionState.value = "CONNECTED (LINK ESTABLISHED)"
                log("NET", ">> GATT link established! Status: $status", LogLevel.SUCCESS)
                log("DISC", "Executing automated service discovery sequence...", LogLevel.INFO)
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                _connectionState.value = "DISCONNECTED"
                log("NET", "<< Connection terminated by remote host or signal loss. Status: $status", LogLevel.WARNING)
                _services.value = emptyList()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                log("DISC", "Discovered ${gatt.services.size} GATT Services on target node!", LogLevel.SUCCESS)
                val mappedServices = gatt.services.map { service ->
                    val serviceUuid = service.uuid.toString()
                    val serviceName = getStandardServiceName(serviceUuid)
                    
                    val chars = service.characteristics.map { char ->
                        val charUuid = char.uuid.toString()
                        val charName = getStandardCharacteristicName(charUuid)
                        val props = getCharacteristicProperties(char.properties)
                        val canRead = (char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                        val canWrite = (char.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                        val canNotify = (char.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                        GattCharacteristicInfo(
                            uuid = charUuid,
                            name = charName,
                            properties = props,
                            canRead = canRead,
                            canWrite = canWrite,
                            canNotify = canNotify
                        )
                    }

                    GattServiceInfo(
                        uuid = serviceUuid,
                        name = serviceName,
                        isPrimary = service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY,
                        characteristics = chars
                    )
                }

                _services.value = mappedServices

                // Automatically probe known read characteristics
                mappedServices.forEach { svc ->
                    svc.characteristics.forEach { ch ->
                        if (ch.canRead) {
                            try {
                                val s = gatt.getService(UUID.fromString(svc.uuid))
                                val c = s?.getCharacteristic(UUID.fromString(ch.uuid))
                                if (c != null) {
                                    gatt.readCharacteristic(c)
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
            } else {
                log("ERR", "Service discovery failed with status $status", LogLevel.CRITICAL)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                val valueBytes = characteristic.value ?: byteArrayOf()
                val hexString = valueBytes.joinToString(" ") { String.format("%02X", it) }
                val asciiString = String(valueBytes, Charsets.UTF_8).filter { it.code in 32..126 }
                
                val displayVal = when {
                    characteristic.uuid.toString().contains("00002a19", ignoreCase = true) && valueBytes.isNotEmpty() -> {
                        "${valueBytes[0].toInt() and 0xFF}% Battery"
                    }
                    asciiString.isNotBlank() -> "\"$asciiString\" (Hex: $hexString)"
                    else -> "Hex: $hexString"
                }

                log("DATA", "[READ ${characteristic.uuid.toString().take(8)}] => $displayVal", LogLevel.DATA)

                _services.update { current ->
                    current.map { svc ->
                        svc.copy(characteristics = svc.characteristics.map { ch ->
                            if (ch.uuid.equals(characteristic.uuid.toString(), ignoreCase = true)) {
                                ch.copy(readValue = displayVal, readBytesHex = hexString, lastUpdated = System.currentTimeMillis())
                            } else ch
                        })
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("TX", "Write ACK received from characteristic ${characteristic?.uuid.toString().take(8)}!", LogLevel.SUCCESS)
            } else {
                log("ERR", "Write failed with status $status", LogLevel.CRITICAL)
            }
        }
    }

    private fun getCharacteristicProperties(props: Int): List<String> {
        val list = mutableListOf<String>()
        if ((props and BluetoothGattCharacteristic.PROPERTY_READ) != 0) list.add("READ")
        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) list.add("WRITE")
        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) list.add("WRITE_NO_RESP")
        if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) list.add("NOTIFY")
        if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) list.add("INDICATE")
        if ((props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) list.add("AUTH_WRITE")
        return list
    }

    private fun getStandardServiceName(uuid: String): String {
        val u = uuid.lowercase()
        return when {
            u.contains("00001800-0000-1000-8000-00805f9b34fb") -> "Generic Access Service"
            u.contains("00001801-0000-1000-8000-00805f9b34fb") -> "Generic Attribute Service"
            u.contains("0000180a-0000-1000-8000-00805f9b34fb") -> "Device Information Service"
            u.contains("0000180f-0000-1000-8000-00805f9b34fb") -> "Battery Level Service"
            u.contains("0000180d-0000-1000-8000-00805f9b34fb") -> "Heart Rate Sensor"
            u.contains("00001812-0000-1000-8000-00805f9b34fb") -> "Human Interface Device (HID)"
            u.contains("00001802-0000-1000-8000-00805f9b34fb") -> "Immediate Alert Service"
            u.contains("00001803-0000-1000-8000-00805f9b34fb") -> "Link Loss Service"
            u.contains("00001804-0000-1000-8000-00805f9b34fb") -> "Tx Power Service"
            u.contains("0000feaa-0000-1000-8000-00805f9b34fb") -> "Google Eddystone Service"
            u.contains("0000fe2c-0000-1000-8000-00805f9b34fb") -> "Fast Pair Service"
            u.contains("0000fd6f-0000-1000-8000-00805f9b34fb") -> "Exposure Notification"
            else -> "Vendor Custom Service (${uuid.take(8)})"
        }
    }

    private fun getStandardCharacteristicName(uuid: String): String {
        val u = uuid.lowercase()
        return when {
            u.contains("00002a00-0000-1000-8000-00805f9b34fb") -> "Device Name"
            u.contains("00002a01-0000-1000-8000-00805f9b34fb") -> "Appearance Icon Code"
            u.contains("00002a19-0000-1000-8000-00805f9b34fb") -> "Battery Level Percentage"
            u.contains("00002a24-0000-1000-8000-00805f9b34fb") -> "Model Number String"
            u.contains("00002a25-0000-1000-8000-00805f9b34fb") -> "Serial Number String"
            u.contains("00002a26-0000-1000-8000-00805f9b34fb") -> "Firmware Revision"
            u.contains("00002a27-0000-1000-8000-00805f9b34fb") -> "Hardware Revision"
            u.contains("00002a28-0000-1000-8000-00805f9b34fb") -> "Software Revision"
            u.contains("00002a29-0000-1000-8000-00805f9b34fb") -> "Manufacturer Name"
            u.contains("00002a07-0000-1000-8000-00805f9b34fb") -> "Tx Power Level"
            u.contains("00002a06-0000-1000-8000-00805f9b34fb") -> "Alert Level Trigger"
            else -> "Characteristic (${uuid.take(8)})"
        }
    }
}
