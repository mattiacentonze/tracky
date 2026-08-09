package com.aloneagle.tracky.ui.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.BuildConfig
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val knownTrackerCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    trackerRepository: TrackerRepository,
) : ViewModel() {
    val uiState = trackerRepository.observeKnownTrackers()
        .map { trackers -> SettingsUiState(knownTrackerCount = trackers.size) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(),
        )
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    val blePermissionsGranted = remember(permissionVersion) { context.hasAllPermissions(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) }
    val locationGranted = remember(permissionVersion) {
        context.hasAnyPermission(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
    val notificationsGranted = remember(permissionVersion) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.hasAllPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionVersion++
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("Permissions") {
                PermissionLine("BLE scan/connect", blePermissionsGranted)
                PermissionLine("Location for last seen place", locationGranted)
                PermissionLine("Notifications for out-of-range alerts", notificationsGranted)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ),
                        )
                    }) {
                        Text("Request app permissions")
                    }
                    OutlinedButton(onClick = { context.openAppSettings() }) {
                        Text("Open system settings")
                    }
                }
            }
        }
        item {
            SectionCard("Build") {
                Text("Tracky ${BuildConfig.VERSION_NAME}")
                Text("${uiState.knownTrackerCount} saved tracker(s)")
            }
        }
        item {
            SectionCard("BLE limits") {
                Text("Tracky estimates proximity from RSSI. It does not provide a directional arrow, UWB precision, or a crowd-tracking network.")
            }
        }
        item {
            SectionCard("Privacy") {
                Text("Tracker sightings, battery snapshots, and last known phone locations stay on-device in Room. There is no cloud backend in v1.")
            }
        }
    }
}

@Composable
private fun PermissionLine(label: String, granted: Boolean) {
    Text(
        text = "$label: ${if (granted) "Granted" else "Missing"}",
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

private fun Context.hasAllPermissions(vararg permissions: String): Boolean = permissions.all { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.hasAnyPermission(vararg permissions: String): Boolean = permissions.any { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
