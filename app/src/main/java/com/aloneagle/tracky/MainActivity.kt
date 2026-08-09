package com.aloneagle.tracky

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.aloneagle.tracky.service.TrackerMonitorService
import com.aloneagle.tracky.ui.navigation.TrackyNavHost
import com.aloneagle.tracky.ui.theme.TrackyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackyNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val canScan = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED
        val canConnect = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        if (canScan && canConnect) TrackerMonitorService.syncMonitoring(this)
    }
}
