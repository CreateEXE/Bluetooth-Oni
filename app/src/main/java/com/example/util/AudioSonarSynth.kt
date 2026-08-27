package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AudioSonarSynth {

    private const val SAMPLE_RATE = 44100

    /**
     * Synthesizes an authentic tactical sonar ping (decaying sine wave chirp with resonance)
     */
    suspend fun playSonarPing(
        baseFreq: Float = 950f,
        durationMs: Int = 320,
        volume: Float = 0.8f
    ) = withContext(Dispatchers.Default) {
        try {
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val samples = ShortArray(numSamples)
            
            val decayFactor = 5.0 / numSamples // Exponential decay envelope

            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                // Slight frequency slide down (chirp)
                val instantFreq = baseFreq * (1.0 - 0.15 * (i.toDouble() / numSamples))
                val wave = sin(2.0 * PI * instantFreq * t)
                val envelope = exp(-decayFactor * i) * volume
                
                // Add second harmonic for crisp underwater resonance
                val harmonic = 0.3 * sin(4.0 * PI * instantFreq * t) * envelope
                val sampleValue = ((wave * envelope + harmonic) * Short.MAX_VALUE).toInt()
                samples[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            playPcmBuffer(samples)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Synthesizes a high-tech cyber radar click / geiger tick
     */
    suspend fun playGeigerTick(intensity: Float = 1.0f) = withContext(Dispatchers.Default) {
        try {
            val durationMs = 15
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val samples = ShortArray(numSamples)
            
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val wave = sin(2.0 * PI * 2400.0 * t)
                val envelope = (1.0 - (i.toDouble() / numSamples)) * intensity
                samples[i] = (wave * envelope * Short.MAX_VALUE).toInt().toShort()
            }

            playPcmBuffer(samples)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Synthesizes a cyber-deck data transmission / frequency probe chirp
     */
    suspend fun playCyberTone(startFreq: Float = 600f, endFreq: Float = 1800f, durationMs: Int = 180) = withContext(Dispatchers.Default) {
        try {
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val samples = ShortArray(numSamples)
            
            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val freq = startFreq + (endFreq - startFreq) * progress
                val t = i.toDouble() / SAMPLE_RATE
                val wave = sin(2.0 * PI * freq * t)
                val envelope = sin(progress * PI) * 0.7 // Raised sine envelope
                samples[i] = (wave * envelope * Short.MAX_VALUE).toInt().toShort()
            }

            playPcmBuffer(samples)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playPcmBuffer(samples: ShortArray) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, samples.size * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size)
        audioTrack.play()
        
        // Release after playback finishes
        val sleepTime = (samples.size * 1000L / SAMPLE_RATE) + 50
        Thread {
            try {
                Thread.sleep(sleepTime)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Ignore
            }
        }.start()
    }
}
