package com.example

import com.example.util.BeaconPacketDecompiler
import com.example.util.GeoUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testDistanceCalculation() {
        val distanceClose = GeoUtils.calculateDistance(-59, -59)
        assertEquals(1.0, distanceClose, 0.1)

        val distanceFar = GeoUtils.calculateDistance(-85, -59)
        assertTrue(distanceFar > 1.0)
    }

    @Test
    fun testBeaconPacketDecompiler() {
        // Test iBeacon payload
        val appleIBeaconPayload = byteArrayOf(
            0x02, 0x01, 0x06, // Flags
            0x1A, 0xFF.toByte(), 0x4C, 0x00, // Apple Company ID
            0x02, 0x15, // iBeacon type
            // 16-byte UUID
            0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
            0x88.toByte(), 0x99.toByte(), 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
            0x00, 0x01, // Major: 1
            0x00, 0x02, // Minor: 2
            0xC5.toByte() // Measured Power
        )

        val decoded = BeaconPacketDecompiler.decompile(appleIBeaconPayload, "AA:BB:CC:DD:EE:FF", -60)
        assertEquals("Apple, Inc.", decoded.companyName)
        assertEquals("Apple iBeacon", decoded.protocol)
        assertEquals(1, decoded.major)
        assertEquals(2, decoded.minor)
        assertTrue(decoded.hexMatrixLines.isNotEmpty())
    }

    @Test
    fun testGenericBeaconFallback() {
        val decoded = BeaconPacketDecompiler.decompile(null, "11:22:33:44:55:66", -75)
        assertNotNull(decoded)
        assertTrue(decoded.rawHexPayload.isNotEmpty())
    }

    @Test
    fun testUserSettingsPersistenceDefaults() {
        val entity = com.example.data.UserSettingsEntity(
            id = 1,
            hapticFeedback = true,
            flashlightAlert = false,
            backgroundScanning = true,
            sonarSoundEnabled = true,
            sonarEpicenterAutoFollow = true,
            sonarSweepAnimation = true,
            geigerAudioEnabled = false
        )
        assertEquals(1, entity.id)
        assertTrue(entity.backgroundScanning)
        assertTrue(entity.sonarEpicenterAutoFollow)
    }
}

