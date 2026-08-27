package com.example.util

object OuiLookup {
    data class VendorProfile(val name: String, val txPower: Int)

    // Common OUI prefixes mapped to Manufacturer and their estimated BLE TxPower
    // The first 3 octets of MAC (e.g., "00:1A:22")
    private val database = mapOf(
        // Apple
        "00:03:93" to VendorProfile("Apple", -65),
        "00:16:CB" to VendorProfile("Apple", -65),
        "00:17:F2" to VendorProfile("Apple", -65),
        "00:1B:63" to VendorProfile("Apple", -65),
        "00:1C:B3" to VendorProfile("Apple", -65),
        "00:1D:4F" to VendorProfile("Apple", -65),
        "00:1E:52" to VendorProfile("Apple", -65),
        "00:1F:5B" to VendorProfile("Apple", -65),
        "4C:00:02" to VendorProfile("Apple", -65), // iBeacon identifier (sometimes used)
        
        // Samsung
        "00:15:99" to VendorProfile("Samsung", -59),
        "00:12:36" to VendorProfile("Samsung", -59),
        "CC:F3:A5" to VendorProfile("Samsung", -59),

        // Sony
        "00:01:4A" to VendorProfile("Sony", -55),
        "00:13:A9" to VendorProfile("Sony", -55),
        "F8:D0:AC" to VendorProfile("Sony", -55),

        // Microsoft (Xbox etc)
        "00:1D:D8" to VendorProfile("Microsoft", -50),
        "00:50:F2" to VendorProfile("Microsoft", -50),
        
        // Nintendo
        "00:1F:32" to VendorProfile("Nintendo", -55),
        "00:25:A0" to VendorProfile("Nintendo", -55),
        "98:B6:E9" to VendorProfile("Nintendo", -55),
        "04:03:D6" to VendorProfile("Nintendo", -55),
        
        // Bose
        "00:0C:8A" to VendorProfile("Bose", -60),
        "04:52:F3" to VendorProfile("Bose", -60),
        
        // Tile (Trackers)
        "C0:28:8D" to VendorProfile("Tile", -50)
    )

    fun getProfile(macAddress: String): VendorProfile {
        if (macAddress.length >= 8) {
            val prefix = macAddress.substring(0, 8).uppercase()
            return database[prefix] ?: VendorProfile("Generic Device", -59)
        }
        return VendorProfile("Generic Device", -59)
    }
}
