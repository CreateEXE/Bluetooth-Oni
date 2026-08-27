package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.wifi.WifiManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.Priority
import com.example.model.SignalType
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DeviceLocationEntity
import com.example.model.AlertEvent
import com.example.model.AlertSettings
import com.example.model.AlertType
import com.example.model.DeviceCategory
import com.example.model.FilterSettings
import com.example.model.GeneralSettings
import com.example.model.TrackedDevice
import com.example.model.PortProbeResult
import com.example.model.BeaconDecodedData
import com.example.util.BleUtils
import com.example.util.GeoUtils
import com.example.util.AudioSonarSynth
import com.example.util.BeaconPacketDecompiler
import com.example.util.BleGattController
import com.example.util.NetworkPortScanner
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BluetoothTrackerViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    val bleGattController = BleGattController(application)

    private val _cyberTargetDevice = MutableStateFlow<TrackedDevice?>(null)
    val cyberTargetDevice = _cyberTargetDevice.asStateFlow()

    private val _portScanResults = MutableStateFlow<List<PortProbeResult>>(emptyList())
    val portScanResults = _portScanResults.asStateFlow()

    private val _isPortScanning = MutableStateFlow(false)
    val isPortScanning = _isPortScanning.asStateFlow()

    fun selectCyberTarget(device: TrackedDevice?) {
        _cyberTargetDevice.value = device
        if (device != null) {
            bleGattController.log("CYBER", "Target node locked: ${device.displayName} [${device.macAddress}]", com.example.model.LogLevel.SUCCESS)
        }
    }

    fun triggerSonarAudioPing() {
        viewModelScope.launch {
            AudioSonarSynth.playSonarPing()
        }
    }

    fun triggerGeigerTick(intensity: Float = 1.0f) {
        viewModelScope.launch {
            AudioSonarSynth.playGeigerTick(intensity)
        }
    }

    fun triggerCyberTone() {
        viewModelScope.launch {
            AudioSonarSynth.playCyberTone()
        }
    }

    fun triggerFlashlightStrobe(pulseCount: Int = 3) {
        viewModelScope.launch {
            for (i in 0 until pulseCount) {
                toggleFlashlight(true)
                kotlinx.coroutines.delay(80)
                toggleFlashlight(false)
                kotlinx.coroutines.delay(80)
            }
        }
    }

    fun startPortScanOnTarget(targetIpOrHost: String) {
        if (_isPortScanning.value) return
        _isPortScanning.value = true
        _portScanResults.value = emptyList()
        bleGattController.log("PORT", "Launching high-speed TCP socket sweep on $targetIpOrHost...", com.example.model.LogLevel.INFO)

        viewModelScope.launch {
            val results = NetworkPortScanner.scanHost(targetIpOrHost) { probe ->
                if (probe.isOpen) {
                    bleGattController.log("PORT", ">> [OPEN] Port ${probe.port} (${probe.serviceName}) Latency: ${probe.latencyMs}ms ${probe.banner ?: ""}", com.example.model.LogLevel.SUCCESS)
                }
                _portScanResults.update { current -> current + probe }
            }
            val openCount = results.count { it.isOpen }
            bleGattController.log("PORT", "Port sweep completed. Found $openCount open listening services.", if (openCount > 0) com.example.model.LogLevel.SUCCESS else com.example.model.LogLevel.WARNING)
            _isPortScanning.value = false
        }
    }

    fun getDecodedBeacon(device: TrackedDevice): BeaconDecodedData {
        return BeaconPacketDecompiler.decompile(device.rawScanRecord, device.macAddress, device.rssi)
    }

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    
    private val _currentLocation = MutableStateFlow<android.location.Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _emfFieldStrength = MutableStateFlow(0f)
    val emfFieldStrength = _emfFieldStrength.asStateFlow()

    private val _inertialSteps = MutableStateFlow(0)
    val inertialSteps = _inertialSteps.asStateFlow()
    private val database = AppDatabase.getDatabase(application)
    private val locationDao = database.deviceLocationDao()
    private val aliasDao = database.deviceAliasDao()

    private val _aliases = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _devices = MutableStateFlow<Map<String, TrackedDevice>>(emptyMap())
    val devices: StateFlow<List<TrackedDevice>> = MutableStateFlow(emptyList())

    private val _filterSettings = MutableStateFlow<FilterSettings>(FilterSettings())
    val filterSettings = _filterSettings.asStateFlow()

    private val _generalSettings = MutableStateFlow<GeneralSettings>(GeneralSettings())
    val generalSettings = _generalSettings.asStateFlow()

    fun updateFilterSettings(settings: FilterSettings) {
        _filterSettings.value = settings
    }

    fun updateGeneralSettings(settings: GeneralSettings) {
        _generalSettings.value = settings
    }

    private val _userAzimuth = MutableStateFlow(0f)
    val userAzimuth = _userAzimuth.asStateFlow()
    
    private val _userPitch = MutableStateFlow(0f)
    val userPitch = _userPitch.asStateFlow()
    
    private val _userRoll = MutableStateFlow(0f)
    val userRoll = _userRoll.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _trackingDevice = MutableStateFlow<TrackedDevice?>(null)
    val trackingDevice = _trackingDevice.asStateFlow()
    
    private val _deviceAlertSettings = MutableStateFlow<Map<String, AlertSettings>>(emptyMap())
    val deviceAlertSettings = _deviceAlertSettings.asStateFlow()
    
    private val _alertEvents = MutableSharedFlow<AlertEvent>()
    val alertEvents = _alertEvents.asSharedFlow()

    // Used to throttle db inserts
    private val lastInsertTimes = mutableMapOf<String, Long>()
    
    // Hold history for tracked device
    private val _deviceHistory = MutableStateFlow<List<DeviceLocationEntity>>(emptyList())
    val deviceHistory = _deviceHistory.asStateFlow()

    // EMA smoothing for RSSI precision
    private val kalmanFilters = mutableMapOf<String, com.example.util.KalmanFilter>()
    
    // Direction estimation based on movement
    private val _estimatedBearings = MutableStateFlow<Map<String, Float>>(emptyMap())
    val estimatedBearings = _estimatedBearings.asStateFlow()
    private val previousDistances = mutableMapOf<String, Double>()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val rawRssi = result.rssi
            val address = device.address
            
            
            val rawName = result.scanRecord?.deviceName ?: device.name
            val existingDevice = _devices.value[address]
            val finalName = if (!rawName.isNullOrBlank()) {
                rawName
            } else if (existingDevice != null && !existingDevice.name.startsWith("Unknown (")) {
                existingDevice.name
            } else {
                "Unknown (${address.takeLast(5)})"
            }

            // Apply 1D Kalman Filter to smooth RSSI
            val kf = kalmanFilters.getOrPut(address) { com.example.util.KalmanFilter(processNoise = 0.05, measurementNoise = 2.0) }
            val finalRssi = kf.filter(rawRssi.toDouble())

            // Calculate distance based on smoothed RSSI
            
            // Calculate distance based on smoothed RSSI
            val vendorProfile = com.example.util.OuiLookup.getProfile(address)
            val distance = GeoUtils.calculateDistance(finalRssi.toInt(), vendorProfile.txPower)
            
            // Pseudo-Direction Estimation Heuristic (Hot/Cold Trilateration over time)
            val prevDist = previousDistances[address]
            if (prevDist != null) {
                val delta = distance - prevDist
                // If moving significantly closer, the device is likely in the direction the user is currently facing
                if (delta < -0.1) {
                    val currentBearings = _estimatedBearings.value.toMutableMap()
                    val existingBearing = currentBearings[address]
                    val currentAzimuth = _userAzimuth.value
                    
                    val newBearing = if (existingBearing != null) {
                        var diff = currentAzimuth - existingBearing
                        while (diff < -180) diff += 360
                        while (diff > 180) diff -= 360
                        existingBearing + (diff * 0.2f) // Slowly pull bearing towards current facing direction
                    } else {
                        currentAzimuth
                    }
                    currentBearings[address] = newBearing
                    _estimatedBearings.value = currentBearings
                } 
                // If moving away, device is likely behind the user
                else if (delta > 0.15) {
                     val currentBearings = _estimatedBearings.value.toMutableMap()
                     val existingBearing = currentBearings[address]
                     var oppositeAzimuth = _userAzimuth.value + 180f
                     if (oppositeAzimuth > 360f) oppositeAzimuth -= 360f
                     
                     val newBearing = if (existingBearing != null) {
                        var diff = oppositeAzimuth - existingBearing
                        while (diff < -180) diff += 360
                        while (diff > 180) diff -= 360
                        existingBearing + (diff * 0.1f)
                    } else {
                        oppositeAzimuth
                    }
                    currentBearings[address] = newBearing
                    _estimatedBearings.value = currentBearings
                }
            }
            previousDistances[address] = distance

            val rssi = finalRssi.toInt()

            val majorClass = device.bluetoothClass?.majorDeviceClass ?: android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED
            val isConnectable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                result.isConnectable
            } else false
            
            val deviceCategory = BleUtils.categorizeDevice(result.scanRecord?.serviceUuids, majorClass)
            val customAlias = _aliases.value[address]

            val trackable = TrackedDevice(
                macAddress = address,
                name = finalName,
                rssi = rssi,
                distanceMeters = distance,
                majorDeviceClass = majorClass,
                isConnectable = isConnectable,
                deviceCategory = deviceCategory,
                vendor = vendorProfile.name,
                txPower = vendorProfile.txPower,
                customAlias = customAlias,
                latitude = _currentLocation.value?.latitude,
                longitude = _currentLocation.value?.longitude,
                lastSeenTimestamp = System.currentTimeMillis(),
                rawScanRecord = result.scanRecord?.bytes,
                serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
            )

            _devices.update { current ->
                val newMap = current.toMutableMap()
                newMap[address] = trackable
                newMap
            }
            
            checkAlerts(trackable)
            saveLocationToHistory(trackable)
            
            // Update tracking device if it matches
            if (_trackingDevice.value?.macAddress == address) {
                _trackingDevice.value = trackable
            }
        }
    }

    init {
        viewModelScope.launch {
            aliasDao.getAllAliases().collect { list ->
                _aliases.value = list.associate { it.macAddress to it.customName }
            }
        }
        viewModelScope.launch {
            combine(_devices, _filterSettings) { map, filter ->
                map.values.toList().filter { device ->
                    var match = true
                    
                    // Signal Type Filter
                    if (!filter.showBluetooth && device.signalType == SignalType.BLUETOOTH) match = false
                    if (!filter.showWifi && device.signalType == SignalType.WIFI) match = false
                    if (!filter.showEmf && device.signalType == SignalType.EMF) match = false
                    
                    // Naming Filter
                    if (filter.showNamedOnly && device.name.startsWith("Unknown (")) match = false
                    
                    // Wifi Security Filter
                    if (device.signalType == SignalType.WIFI) {
                        if (!filter.showLockedWifi && (device.isSecure == true)) match = false
                        if (!filter.showOpenWifi && (device.isSecure == false)) match = false
                    }
                    
                    // Tracking Filter
                    if (filter.showTrackedOnly && _trackingDevice.value?.macAddress != device.macAddress) match = false
                    
                    // Signal Strength Filter
                    if (device.rssi < filter.minSignalStrength) match = false
                    
                    // "New Only" filter (simplification: seen in last 30 seconds)
                    if (filter.showNewOnly && System.currentTimeMillis() - device.lastSeenTimestamp > 30000) match = false
                    
                    match
                }.sortedBy { it.distanceMeters }
            }.collect { filteredList ->
                (devices as MutableStateFlow).value = filteredList
            }
        }
        startSensors()
    }
    
    fun setAlias(macAddress: String, alias: String) {
        viewModelScope.launch {
            aliasDao.insertAlias(com.example.data.DeviceAliasEntity(macAddress, alias))
        }
    }
    
    private fun saveLocationToHistory(device: TrackedDevice) {
        val now = System.currentTimeMillis()
        val lastInsert = lastInsertTimes[device.macAddress] ?: 0L
        // Save once every 5 seconds per device
        if (now - lastInsert > 5000) {
            lastInsertTimes[device.macAddress] = now
            viewModelScope.launch {
                locationDao.insertLocation(
                    DeviceLocationEntity(
                        macAddress = device.macAddress,
                        deviceName = device.name,
                        deviceCategory = device.deviceCategory.name,
                        distanceMeters = device.distanceMeters,
                        timestamp = now
                    )
                )
            }
        }
    }
    
    // Alert state tracking to avoid spam
    private val alertState = mutableMapOf<String, Boolean>() // true if already alerted
    
    private fun checkAlerts(device: TrackedDevice) {
        // Haptic feedback for tracking
        if (_trackingDevice.value?.macAddress == device.macAddress && _generalSettings.value.hapticFeedback) {
            val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                // Pulse faster as we get closer (hotter/colder)
                val distance = device.distanceMeters
                if (distance < 5.0) {
                    val strength = (255 * (1.0 - (distance / 5.0).coerceIn(0.0, 1.0))).toInt()
                    if (strength > 50) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, strength))
                        } else {
                            vibrator.vibrate(50)
                        }
                    }
                }
            }
        }
        
        // Flashlight alert for tracking
        if (_trackingDevice.value?.macAddress == device.macAddress && _generalSettings.value.flashlightAlert) {
            if (device.distanceMeters < 1.0) {
                toggleFlashlight(true)
                viewModelScope.launch {
                    kotlinx.coroutines.delay(100)
                    toggleFlashlight(false)
                }
            }
        }

        val settings = _deviceAlertSettings.value[device.macAddress] ?: return
        if (!settings.enabled) return
        
        val previouslyAlerted = alertState[device.macAddress] ?: false
        
        if (settings.alertType == AlertType.IN_RANGE && device.distanceMeters <= settings.distanceThresholdMeters) {
            if (!previouslyAlerted) {
                alertState[device.macAddress] = true
                fireAlert(device, "Perimeter Breached: Device is in range! (${String.format("%.1f", device.distanceMeters)}m)", settings.isHostageMode)
            }
        } else if (settings.alertType == AlertType.OUT_OF_RANGE && device.distanceMeters >= settings.distanceThresholdMeters) {
            if (!previouslyAlerted) {
                alertState[device.macAddress] = true
                fireAlert(device, "Perimeter Breached: Device left the safe zone! (${String.format("%.1f", device.distanceMeters)}m)", settings.isHostageMode)
            }
        } else {
            // Reset alert state when condition is no longer met
            alertState[device.macAddress] = false
        }
    }
    
    private fun fireAlert(device: TrackedDevice, message: String, isHostageMode: Boolean) {
        viewModelScope.launch {
            _alertEvents.emit(
                AlertEvent(
                    macAddress = device.macAddress,
                    deviceName = device.name,
                    message = message,
                    isSecurityBreach = isHostageMode
                )
            )
        }
    }

    private fun toggleFlashlight(enabled: Boolean) {
        try {
            val cameraManager = getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enabled)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun updateAlertSettings(macAddress: String, settings: AlertSettings) {
        _deviceAlertSettings.update { current ->
            val newMap = current.toMutableMap()
            newMap[macAddress] = settings
            newMap
        }
        alertState[macAddress] = false
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        
        startLocationUpdates()

        // Start Bluetooth Scan
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner != null) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
        }

        // Start Wi-Fi Scan
        viewModelScope.launch {
            while (_isScanning.value) {
                try {
                    wifiManager.startScan()
                    val results = wifiManager.scanResults
                    results.forEach { result ->
                        processWifiResult(result)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                kotlinx.coroutines.delay(10000) // Scan Wi-Fi every 10 seconds (throttled by OS anyway)
            }
        }
        
        _isScanning.value = true
    }

    private fun processWifiResult(result: android.net.wifi.ScanResult) {
        val address = result.BSSID
        val rssi = result.level
        val ssid = result.SSID
        val capabilities = result.capabilities
        val isSecure = capabilities.contains("WPA") || capabilities.contains("WEP") || capabilities.contains("EAP")
        
        val kf = kalmanFilters.getOrPut(address) { com.example.util.KalmanFilter(processNoise = 0.05, measurementNoise = 2.0) }
        val finalRssi = kf.filter(rssi.toDouble())
        
        // Wi-Fi distance estimation is similar but different. Simplified here.
        val distance = GeoUtils.calculateDistance(finalRssi.toInt(), -40) // Wi-Fi APs are usually stronger
 
        val trackable = TrackedDevice(
            macAddress = address,
            name = if (ssid.isNullOrBlank()) "Hidden Network" else ssid,
            rssi = finalRssi.toInt(),
            distanceMeters = distance,
            majorDeviceClass = 0, // Not applicable
            isConnectable = true,
            deviceCategory = DeviceCategory.WIFI_ROUTER,
            vendor = "Network Infrastructure",
            txPower = -40,
            customAlias = _aliases.value[address],
            signalType = SignalType.WIFI,
            latitude = _currentLocation.value?.latitude,
            longitude = _currentLocation.value?.longitude,
            lastSeenTimestamp = System.currentTimeMillis(),
            isSecure = isSecure
        )

        _devices.update { current ->
            val newMap = current.toMutableMap()
            newMap[address] = trackable
            newMap
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
            result.lastLocation?.let {
                _currentLocation.value = it
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isScanning.value = false
    }

    fun trackDevice(device: TrackedDevice?) {
        _trackingDevice.value = device
        
        // Load history for this device
        if (device != null) {
            viewModelScope.launch {
                locationDao.getLocationsForDevice(device.macAddress).collect { locations ->
                    _deviceHistory.value = locations
                }
            }
        } else {
            _deviceHistory.value = emptyList()
        }
    }
    
    fun fastTrackDeviceByMac(macAddress: String) {
        val device = _devices.value[macAddress]
        if (device != null) {
            trackDevice(device)
        }
    }

    private fun startSensors() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            _emfFieldStrength.value = magnitude
            
            // If EMF is high (> 100 uT), treat it as an electronic device signature
            if (magnitude > 100f) {
                val emfDevice = TrackedDevice(
                    macAddress = "EMF_SCAN",
                    name = "Electronic Interference",
                    rssi = -(magnitude / 2).toInt(),
                    distanceMeters = (100.0 / magnitude).coerceAtMost(10.0),
                    majorDeviceClass = 0,
                    isConnectable = false,
                    deviceCategory = DeviceCategory.ELECTRONIC,
                    vendor = "Magnetic Signature",
                    txPower = -50,
                    signalType = SignalType.EMF
                )
                _devices.update { current ->
                    val newMap = current.toMutableMap()
                    newMap["EMF_SCAN"] = emfDevice
                    newMap
                }
            }
        } else if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            _inertialSteps.value += 1
        }
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // Azimuth is orientation[0] in radians
            var azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuthDegrees < 0) {
                azimuthDegrees += 360f
            }
            _userAzimuth.value = azimuthDegrees
            
            // Pitch is orientation[1]
            _userPitch.value = Math.toDegrees(orientation[1].toDouble()).toFloat()
            
            // Roll is orientation[2]
            _userRoll.value = Math.toDegrees(orientation[2].toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
