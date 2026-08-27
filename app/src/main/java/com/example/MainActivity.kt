package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.osmdroid.config.Configuration
import android.preference.PreferenceManager
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Load osmdroid configuration
    Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
    // Set a custom user agent to prevent being banned from OSM servers
    Configuration.getInstance().userAgentValue = packageName
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MainScreen()
      }
    }
  }
}

