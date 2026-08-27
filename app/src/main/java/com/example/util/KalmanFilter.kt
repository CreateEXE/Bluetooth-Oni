package com.example.util

class KalmanFilter(
    private var processNoise: Double = 0.05, // Q: how fast we expect the signal to change (lower = smoother but more lag)
    private var measurementNoise: Double = 2.0, // R: how noisy the environment is (higher = more smoothing)
    private var estimatedError: Double = 1.0     // P: error covariance
) {
    private var estimate: Double? = null

    fun filter(measurement: Double): Double {
        if (estimate == null) {
            estimate = measurement
            return measurement
        }
        
        // Prediction update
        estimatedError += processNoise
        
        // Measurement update
        val kalmanGain = estimatedError / (estimatedError + measurementNoise)
        estimate = estimate!! + kalmanGain * (measurement - estimate!!)
        estimatedError = (1.0 - kalmanGain) * estimatedError
        
        return estimate!!
    }
}
