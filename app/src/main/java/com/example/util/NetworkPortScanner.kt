package com.example.util

import com.example.model.PortProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object NetworkPortScanner {

    private val COMMON_TARGET_PORTS = listOf(
        Pair(80, "HTTP Web Interface"),
        Pair(443, "HTTPS Secure Web"),
        Pair(22, "SSH Remote Shell"),
        Pair(23, "Telnet Unencrypted Terminal"),
        Pair(554, "RTSP IP Camera Video Stream"),
        Pair(8080, "HTTP-Proxy / Alt Web"),
        Pair(8443, "HTTPS Alternate Admin"),
        Pair(53, "DNS Resolver"),
        Pair(445, "SMB Windows File Share"),
        Pair(139, "NetBIOS Session Service"),
        Pair(1883, "MQTT IoT Message Broker"),
        Pair(5555, "Android ADB Wireless Debug"),
        Pair(9100, "RAW Network Printer Direct"),
        Pair(3389, "RDP Windows Remote Desktop"),
        Pair(5000, "UPnP / Synology DSM"),
        Pair(8008, "Chromecast / Google Cast API")
    )

    suspend fun scanHost(
        targetIp: String,
        timeoutMs: Int = 450,
        onProgress: (PortProbeResult) -> Unit = {}
    ): List<PortProbeResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            val deferreds = COMMON_TARGET_PORTS.map { (port, service) ->
                async {
                    val result = probePort(targetIp, port, service, timeoutMs)
                    onProgress(result)
                    result
                }
            }
            deferreds.awaitAll()
        }
    }

    private fun probePort(host: String, port: Int, serviceName: String, timeoutMs: Int): PortProbeResult {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val latency = System.currentTimeMillis() - startTime
            
            var banner: String? = null
            try {
                socket.soTimeout = 300
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(256)
                if (inputStream.available() > 0) {
                    val read = inputStream.read(buffer)
                    if (read > 0) {
                        banner = String(buffer, 0, read).trim().replace("\r\n", " ").take(60)
                    }
                }
            } catch (e: Exception) {
                // Ignore banner timeout
            }

            PortProbeResult(
                port = port,
                serviceName = serviceName,
                isOpen = true,
                latencyMs = latency,
                banner = banner
            )
        } catch (e: Exception) {
            PortProbeResult(
                port = port,
                serviceName = serviceName,
                isOpen = false,
                latencyMs = System.currentTimeMillis() - startTime,
                banner = null
            )
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
