package com.example.util

import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.model.LocationIntegrityLevel
import com.example.model.LocationIntegrityReport
import java.net.NetworkInterface

object LocationIntegrityAudit {

    fun audit(context: Context, location: Location?): LocationIntegrityReport {
        // 1. VPN Detection
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var isVpn = false
        var vpnInterface: String? = null

        try {
            val activeNetwork = connectivityManager?.activeNetwork
            if (activeNetwork != null) {
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    isVpn = true
                }
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isUp && (
                    iface.name.startsWith("tun") ||
                    iface.name.startsWith("ppp") ||
                    iface.name.startsWith("wg") ||
                    iface.name.startsWith("tap") ||
                    iface.name.startsWith("p2p")
                )) {
                    isVpn = true
                    vpnInterface = iface.name
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. System Proxy Detection
        val httpProxy = System.getProperty("http.proxyHost")
        val httpsProxy = System.getProperty("https.proxyHost")
        val httpPort = System.getProperty("http.proxyPort")
        val isProxy = !httpProxy.isNullOrBlank() || !httpsProxy.isNullOrBlank()
        val proxyDetails = if (isProxy) "${httpProxy ?: httpsProxy}:$httpPort" else null

        // 3. Emulator Detection
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu")

        // 4. Mock Location Detection
        var isMock = false
        if (location != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                isMock = location.isMock
            } else {
                @Suppress("DEPRECATION")
                isMock = location.isFromMockProvider
            }
        }

        // 5. Metrics & Anomaly Analysis
        val warnings = mutableListOf<String>()
        val provider = location?.provider ?: "none"
        val accuracy = location?.accuracy ?: 999f
        val now = System.currentTimeMillis()
        val ageSeconds = if (location != null && location.time > 0) {
            ((now - location.time) / 1000L).coerceAtLeast(0L)
        } else 0L

        if (isMock) {
            warnings.add("MOCK LOCATION ACTIVE: System reports GPS coordinates are simulated by a third-party spoofer app or ADB.")
        }
        if (isVpn) {
            warnings.add("VPN TUNNEL ACTIVE (${vpnInterface ?: "TRANSPORT_VPN"}): Network traffic & GeoIP routing are encrypted and cloaked.")
        }
        if (isProxy) {
            warnings.add("HTTP PROXY CONFIGURED ($proxyDetails): System traffic is routed through an intermediary proxy server.")
        }
        if (isEmulator) {
            warnings.add("CLOUD/EMULATOR RUNTIME: Device is running inside a virtualized container. Hardware GNSS satellites are simulated.")
        }
        if (accuracy > 50f) {
            warnings.add("COARSE ACCURACY (±${String.format("%.1f", accuracy)}m): Poor satellite fix or indoor Wi-Fi/Cellular trilateration.")
        }
        if (ageSeconds > 60L) {
            warnings.add("STALE FIX: Last recorded position is ${ageSeconds}s old. Satellite telemetry may not reflect real-time motion.")
        }

        // Determine Level
        val level = when {
            location == null -> LocationIntegrityLevel.NO_FIX
            isMock -> LocationIntegrityLevel.SUSPICIOUS_MOCK
            isEmulator -> LocationIntegrityLevel.EMULATOR_VIRTUAL
            isVpn -> LocationIntegrityLevel.VPN_CLOAKED
            accuracy > 50f -> LocationIntegrityLevel.COARSE_CELLULAR
            else -> LocationIntegrityLevel.AUTHENTIC_GNSS
        }

        val summary = when (level) {
            LocationIntegrityLevel.AUTHENTIC_GNSS -> "HARDWARE GNSS VERIFIED: High-precision satellite lock without detected cloaking or spoofing."
            LocationIntegrityLevel.SUSPICIOUS_MOCK -> "LOCATION SPOOFED: Android OS indicates GPS coordinates are being mocked by software."
            LocationIntegrityLevel.EMULATOR_VIRTUAL -> "VIRTUAL EMULATOR FIX: Running in browser/cloud emulator environment with synthetic GPS."
            LocationIntegrityLevel.VPN_CLOAKED -> "VPN NETWORK ACTIVE: Physical GNSS fix present, but network layer is routed via VPN."
            LocationIntegrityLevel.COARSE_CELLULAR -> "DEGRADED ACCURACY: Relying on coarse cell tower/Wi-Fi beacons. Move outdoors for satellite line-of-sight."
            LocationIntegrityLevel.NO_FIX -> "NO GPS FIX ACQUIRED: Awaiting initial GNSS or network provider positioning."
        }

        return LocationIntegrityReport(
            isVpnActive = isVpn,
            vpnInterfaceName = vpnInterface,
            isMockLocation = isMock,
            isProxyActive = isProxy,
            proxyDetails = proxyDetails,
            locationProvider = provider,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            accuracyMeters = accuracy,
            altitudeMeters = location?.altitude ?: 0.0,
            speedMps = location?.speed ?: 0f,
            bearingDegrees = location?.bearing ?: 0f,
            locationAgeSeconds = ageSeconds,
            isEmulator = isEmulator,
            integrityLevel = level,
            anomalyWarnings = warnings,
            diagnosticSummary = summary
        )
    }
}
