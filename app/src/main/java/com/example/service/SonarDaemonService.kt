package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.CyberLogEntity
import com.example.model.DeviceCategory
import com.example.model.SignalType
import com.example.model.TrackedDevice
import com.example.util.BleUtils
import com.example.util.GeoUtils
import com.example.util.OuiLookup
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SonarDaemonService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationManager: NotificationManager? = null
    private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null

    private val discoveredDevices = mutableMapOf<String, TrackedDevice>()

    companion object {
        const val CHANNEL_ID = "spectre_sonar_daemon_channel"
        const val NOTIFICATION_ID = 1337
        const val ACTION_START = "ACTION_START_DAEMON"
        const val ACTION_STOP = "ACTION_STOP_DAEMON"

        private val _isDaemonRunning = MutableStateFlow(false)
        val isDaemonRunning = _isDaemonRunning.asStateFlow()

        private val _daemonDeviceCount = MutableStateFlow(0)
        val daemonDeviceCount = _daemonDeviceCount.asStateFlow()

        private val _daemonStatusText = MutableStateFlow("Daemon Idle")
        val daemonStatusText = _daemonStatusText.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SonarDaemonService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SonarDaemonService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownDaemon()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundDaemon()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spectre Sonar Tracking Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tactical background BLE & Wi-Fi Sonar scanning with persistent epicenter tracking"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundDaemon() {
        _isDaemonRunning.value = true
        _daemonStatusText.value = "SONAR HARVESTING ACTIVE"

        val initialNotification = buildNotification("Initializing RF sensor mesh...", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, initialNotification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        startBleScanner()
        startLocationTracking()

        // Background persistence and stats loop
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.cyberLogDao().insertLog(
                CyberLogEntity(
                    tag = "DAEMON",
                    message = "CTOS Spectre Sonar Daemon initialized in foreground mode.",
                    level = "SUCCESS"
                )
            )

            while (isActive) {
                delay(3000)
                val count = discoveredDevices.size
                _daemonDeviceCount.value = count
                val nearest = discoveredDevices.values.minByOrNull { it.distanceMeters }
                val statusMessage = if (nearest != null) {
                    "${discoveredDevices.size} Nodes • Nearest: ${nearest.displayName} (~${String.format("%.1f", nearest.distanceMeters)}m)"
                } else {
                    "${discoveredDevices.size} Nodes Tracked • Sonar Epicenter Online"
                }
                _daemonStatusText.value = statusMessage
                updateNotification(statusMessage, count)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanner() {
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            val scanner = adapter?.bluetoothLeScanner
            if (scanner != null) {
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .build()
                scanner.startScan(null, scanSettings, daemonScanCallback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val daemonScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            val rawName = result.scanRecord?.deviceName ?: device.name
            val finalName = if (!rawName.isNullOrBlank()) rawName else "Node (${address.takeLast(5)})"
            val vendorProfile = OuiLookup.getProfile(address)
            val distance = GeoUtils.calculateDistance(result.rssi, vendorProfile.txPower)
            val majorClass = device.bluetoothClass?.majorDeviceClass ?: 0
            val isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else false

            val trackable = TrackedDevice(
                macAddress = address,
                name = finalName,
                rssi = result.rssi,
                distanceMeters = distance,
                majorDeviceClass = majorClass,
                isConnectable = isConnectable,
                deviceCategory = BleUtils.categorizeDevice(result.scanRecord?.serviceUuids, majorClass),
                vendor = vendorProfile.name,
                txPower = vendorProfile.txPower,
                signalType = SignalType.BLUETOOTH,
                lastSeenTimestamp = System.currentTimeMillis(),
                rawScanRecord = result.scanRecord?.bytes,
                serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
            )

            discoveredDevices[address] = trackable
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8000)
                .setMinUpdateIntervalMillis(4000)
                .build()
            fusedLocationClient?.requestLocationUpdates(locationRequest, daemonLocationCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val daemonLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // Updated location handles epicenter calculations in background
        }
    }

    private fun buildNotification(contentText: String, nodeCount: Int): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingLaunchIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SonarDaemonService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CTOS // SPECTRE DAEMON ACTIVE")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingLaunchIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_delete, "STOP DAEMON", pendingStopIntent)
            .build()
    }

    private fun updateNotification(contentText: String, nodeCount: Int) {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(contentText, nodeCount))
    }

    @SuppressLint("MissingPermission")
    private fun shutdownDaemon() {
        _isDaemonRunning.value = false
        _daemonStatusText.value = "DAEMON TERMINATED"
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(daemonScanCallback)
            fusedLocationClient?.removeLocationUpdates(daemonLocationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.cyberLogDao().insertLog(
                    CyberLogEntity(
                        tag = "DAEMON",
                        message = "Spectre Daemon stopped by operator.",
                        level = "WARNING"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        shutdownDaemon()
        super.onDestroy()
    }
}
