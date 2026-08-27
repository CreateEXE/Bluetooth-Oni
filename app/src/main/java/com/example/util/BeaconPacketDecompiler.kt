package com.example.util

import com.example.model.BeaconDecodedData
import com.example.model.HexLine

object BeaconPacketDecompiler {

    private val COMPANY_IDENTIFIERS = mapOf(
        0x004C to "Apple, Inc.",
        0x00E0 to "Google LLC",
        0x0006 to "Microsoft",
        0x0075 to "Samsung Electronics",
        0x012D to "Sony Corporation",
        0x038F to "Xiaomi Communications",
        0x0059 to "Nordic Semiconductor",
        0x02E5 to "Espressif Systems",
        0x0087 to "Garmin International",
        0x009E to "Bose Corporation",
        0x000A to "Qualcomm Technologies",
        0x00D2 to "Dialog Semiconductor",
        0x0157 to "Anhui Huami (Amazfit)",
        0x0171 to "Amazon Lab126",
        0x02B0 to "Tile, Inc.",
        0x0499 to "Ruuvi Innovations",
        0x052B to "Chipolo d.o.o."
    )

    fun decompile(bytes: ByteArray?, macAddress: String, rssi: Int): BeaconDecodedData {
        if (bytes == null || bytes.isEmpty()) {
            return generateGenericData(macAddress, rssi)
        }

        val rawHex = bytes.joinToString("") { String.format("%02X", it) }
        val hexMatrix = formatHexMatrix(bytes)
        val flags = mutableListOf<String>()

        var protocol = "Standard BLE Advertisement"
        var companyName = "Generic Bluetooth Device"
        var companyIdHex = "0x0000"
        var uuid: String? = null
        var major: Int? = null
        var minor: Int? = null
        var txPower: Int? = null

        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0 || offset + length >= bytes.size) break

            val type = bytes[offset + 1].toInt() and 0xFF
            val dataOffset = offset + 2
            val dataLength = length - 1

            when (type) {
                0x01 -> { // Flags
                    if (dataLength >= 1) {
                        val flagByte = bytes[dataOffset].toInt() and 0xFF
                        if ((flagByte and 0x01) != 0) flags.add("LE Limited Discoverable")
                        if ((flagByte and 0x02) != 0) flags.add("LE General Discoverable")
                        if ((flagByte and 0x04) != 0) flags.add("BR/EDR Not Supported")
                        if ((flagByte and 0x08) != 0) flags.add("Simultaneous LE+BR/EDR Controller")
                    }
                }
                0xFF -> { // Manufacturer Specific Data
                    if (dataLength >= 2) {
                        val cId = ((bytes[dataOffset + 1].toInt() and 0xFF) shl 8) or (bytes[dataOffset].toInt() and 0xFF)
                        companyIdHex = String.format("0x%04X", cId)
                        companyName = COMPANY_IDENTIFIERS[cId] ?: "Company ID: $companyIdHex"

                        if (cId == 0x004C) { // Apple
                            if (dataLength >= 3) {
                                val appleType = bytes[dataOffset + 2].toInt() and 0xFF
                                when (appleType) {
                                    0x02 -> { // iBeacon
                                        protocol = "Apple iBeacon"
                                        if (dataLength >= 23) {
                                            val uuidBytes = bytes.copyOfRange(dataOffset + 4, dataOffset + 20)
                                            uuid = formatUuid(uuidBytes)
                                            major = ((bytes[dataOffset + 20].toInt() and 0xFF) shl 8) or (bytes[dataOffset + 21].toInt() and 0xFF)
                                            minor = ((bytes[dataOffset + 22].toInt() and 0xFF) shl 8) or (bytes[dataOffset + 23].toInt() and 0xFF)
                                            if (dataLength >= 24) {
                                                txPower = bytes[dataOffset + 24].toInt()
                                            }
                                        }
                                    }
                                    0x12 -> { // Apple FindMy / AirTag network
                                        protocol = "Apple AirTag / FindMy Network"
                                        flags.add("FindMy Encrypted Broadcast")
                                        if (dataLength >= 5) {
                                            val status = bytes[dataOffset + 4].toInt() and 0xFF
                                            flags.add("AirTag Status: 0x${String.format("%02X", status)}")
                                        }
                                    }
                                    0x10 -> { // Apple NearbyAction
                                        protocol = "Apple Nearby Proximity Beacon"
                                    }
                                    0x07 -> { // AirPods / Audio pairing
                                        protocol = "Apple Audio Accessory (AirPods / Beats)"
                                    }
                                    else -> {
                                        protocol = "Apple Proprietary (0x${String.format("%02X", appleType)})"
                                    }
                                }
                            }
                        } else if (cId == 0x00E0) { // Google
                            if (dataLength >= 4 && (bytes[dataOffset + 2].toInt() and 0xFF) == 0x00) {
                                protocol = "Google Fast Pair Service"
                                val modelId = String.format("%02X%02X%02X", bytes[dataOffset + 3], bytes[dataOffset + 4], if (dataLength >= 5) bytes[dataOffset + 5] else 0)
                                flags.add("Fast Pair Model: $modelId")
                            }
                        } else if (cId == 0x0006) { // Microsoft
                            protocol = "Microsoft Swift Pair / Beacon"
                        } else if (cId == 0x0075) { // Samsung
                            protocol = "Samsung SmartThings / SmartTag"
                        }
                    }
                }
                0x03, 0x02 -> { // 16-bit Service UUIDs
                    if (dataLength >= 2) {
                        val sUuid = ((bytes[dataOffset + 1].toInt() and 0xFF) shl 8) or (bytes[dataOffset].toInt() and 0xFF)
                        if (sUuid == 0xFEAA) {
                            protocol = "Google Eddystone Beacon"
                        } else if (sUuid == 0x180F) {
                            flags.add("Standard Battery Service 0x180F")
                        } else if (sUuid == 0x180D) {
                            flags.add("Heart Rate Service 0x180D")
                        }
                    }
                }
            }

            offset += length + 1
        }

        return BeaconDecodedData(
            protocol = protocol,
            companyName = companyName,
            companyIdHex = companyIdHex,
            uuid = uuid,
            major = major,
            minor = minor,
            txPowerCalibrated = txPower,
            flags = flags,
            rawHexPayload = rawHex,
            hexMatrixLines = hexMatrix
        )
    }

    private fun generateGenericData(macAddress: String, rssi: Int): BeaconDecodedData {
        val fakeBytes = (macAddress.replace(":", "") + "0000").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return BeaconDecodedData(
            protocol = "Standard BLE Node",
            companyName = OuiLookup.getProfile(macAddress).name,
            companyIdHex = "0x0000",
            flags = listOf("Unicast Signal Node", "RSSI: $rssi dBm"),
            rawHexPayload = fakeBytes.joinToString("") { String.format("%02X", it) },
            hexMatrixLines = formatHexMatrix(fakeBytes)
        )
    }

    private fun formatHexMatrix(bytes: ByteArray): List<HexLine> {
        val lines = mutableListOf<HexLine>()
        val chunkSize = 16
        for (i in bytes.indices step chunkSize) {
            val end = minOf(i + chunkSize, bytes.size)
            val chunk = bytes.copyOfRange(i, end)
            
            val offsetHex = String.format("%04X", i)
            val bytesHex = chunk.joinToString(" ") { String.format("%02X", it) }
            val asciiPreview = chunk.map { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")

            lines.add(HexLine(offsetHex, bytesHex.padEnd(chunkSize * 3 - 1), asciiPreview))
        }
        return lines
    }

    private fun formatUuid(bytes: ByteArray): String {
        if (bytes.size != 16) return bytes.joinToString("") { String.format("%02X", it) }
        val hex = bytes.joinToString("") { String.format("%02X", it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
