package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onNavigateToMain: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        // Simulate load progress
        val totalTime = 2500L
        val interval = 50L
        var currentTime = 0L
        while (currentTime < totalTime) {
            delay(interval)
            currentTime += interval
            progress = currentTime.toFloat() / totalTime.toFloat()
        }
        onNavigateToMain()
    }

    val rotation by animateFloatAsState(
        targetValue = progress * 360f * 3, // Rotate 3 times
        animationSpec = tween(durationMillis = 2500, easing = LinearEasing),
        label = "radarRotation"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothSearching,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Radar compass animation
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2

                    // Background circles
                    for (i in 1..3) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.2f),
                            radius = radius * (i / 3f),
                            center = center,
                            style = Stroke(width = 2f)
                        )
                    }

                    // Radar sweep line
                    val angleRad = Math.toRadians((rotation - 90).toDouble())
                    val endX = center.x + (radius * cos(angleRad)).toFloat()
                    val endY = center.y + (radius * sin(angleRad)).toFloat()

                    drawLine(
                        color = primaryColor,
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 4f
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                text = "ONI SONG",
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Bluetooth Oni",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = progress.coerceIn(0f, 1f)),
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
