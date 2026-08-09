package com.aloneagle.tracky.ui.feature.trackers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.service.TrackerMonitorService
import com.aloneagle.tracky.ui.components.ConnectionBadge
import com.aloneagle.tracky.ui.components.PresenceBadge
import com.aloneagle.tracky.ui.components.SignalMeter
import com.aloneagle.tracky.util.formatBattery
import com.aloneagle.tracky.util.formatLastSeen
import com.aloneagle.tracky.util.formatLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackersUiState(
    val trackers: List<KnownTracker> = emptyList(),
)

@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val trackerRepository: TrackerRepository,
) : ViewModel() {
    val uiState = trackerRepository.observeKnownTrackers()
        .map(::TrackersUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = TrackersUiState(),
        )

    fun setMonitorEnabled(trackerId: String, enabled: Boolean, onStored: () -> Unit) {
        viewModelScope.launch {
            trackerRepository.setMonitorEnabled(trackerId, enabled)
            onStored()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackersScreen(
    viewModel: TrackersViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenScan: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Tracky") },
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Outlined.BugReport, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenScan) {
                Icon(Icons.Outlined.Troubleshoot, contentDescription = "Scan")
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.trackers.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("No saved trackers yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Run a manual BLE scan, save the devices you trust, then use search and monitoring from the detail screen.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                items(
                    count = uiState.trackers.size,
                    key = { index -> uiState.trackers[index].id },
                ) { index ->
                    val tracker = uiState.trackers[index]
                    TrackerRow(
                        tracker = tracker,
                        onClick = { onOpenDetail(tracker.id) },
                        onToggleMonitor = { enabled ->
                            viewModel.setMonitorEnabled(tracker.id, enabled) {
                                TrackerMonitorService.syncMonitoring(context)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackerRow(
    tracker: KnownTracker,
    onClick: () -> Unit,
    onToggleMonitor: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = tracker.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = tracker.deviceAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PresenceBadge(tracker.presence)
                ConnectionBadge(tracker.connectionState)
                AssistChip(onClick = {}, label = { Text(tracker.protocolType.name) })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Last seen ${formatLastSeen(tracker.lastSeenAt)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Battery ${formatBattery(tracker.batteryState)}", style = MaterialTheme.typography.bodyMedium)
                    Text(formatLocation(tracker.lastLocation), style = MaterialTheme.typography.bodySmall)
                }
                SignalMeter(signalPercent = tracker.proximity?.signalPercent ?: 0)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Monitor", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = tracker.monitorEnabled,
                    onCheckedChange = onToggleMonitor,
                )
            }
        }
    }
}
