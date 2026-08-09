package com.aloneagle.tracky.ui.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.TrackerObservation
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.service.TrackerMonitorService
import com.aloneagle.tracky.ui.components.ConnectionBadge
import com.aloneagle.tracky.ui.components.PresenceBadge
import com.aloneagle.tracky.ui.components.SectionCard
import com.aloneagle.tracky.ui.components.SignalMeter
import com.aloneagle.tracky.util.formatBattery
import com.aloneagle.tracky.util.formatLastSeen
import com.aloneagle.tracky.util.formatLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackerDetailUiState(
    val tracker: KnownTracker? = null,
    val observations: List<TrackerObservation> = emptyList(),
    val isRefreshing: Boolean = false,
    val isRinging: Boolean = false,
)

@HiltViewModel
class TrackerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackerRepository: TrackerRepository,
) : ViewModel() {
    private val trackerId: String = checkNotNull(savedStateHandle["trackerId"])
    private val refreshing = MutableStateFlow(false)
    private val ringing = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>()

    val messages = _messages

    val uiState = combine(
        trackerRepository.observeTracker(trackerId),
        trackerRepository.observeRecentObservations(trackerId),
        refreshing,
        ringing,
    ) { tracker, observations, isRefreshing, isRinging ->
        TrackerDetailUiState(
            tracker = tracker,
            observations = observations,
            isRefreshing = isRefreshing,
            isRinging = isRinging,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TrackerDetailUiState(),
    )

    fun renameTracker(nickname: String) {
        viewModelScope.launch {
            trackerRepository.renameTracker(trackerId, nickname.trim())
            _messages.emit("Tracker name updated")
        }
    }

    fun setMonitorEnabled(enabled: Boolean, onStored: () -> Unit) {
        viewModelScope.launch {
            trackerRepository.setMonitorEnabled(trackerId, enabled)
            onStored()
        }
    }

    fun refreshTracker() {
        viewModelScope.launch {
            refreshing.value = true
            val result = trackerRepository.refreshTracker(trackerId, "detail-refresh-${System.currentTimeMillis()}")
            _messages.emit(
                if (result.isSuccess) "Tracker refreshed" else result.exceptionOrNull()?.message ?: "Refresh failed",
            )
            refreshing.value = false
        }
    }

    fun ringTracker() {
        viewModelScope.launch {
            ringing.value = true
            val result = trackerRepository.ringTracker(trackerId, "detail-ring-${System.currentTimeMillis()}")
            _messages.emit(result.message)
            ringing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    viewModel: TrackerDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenDiagnostics: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracker = uiState.tracker
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    if (tracker == null) {
        Text("Tracker not found", modifier = Modifier.padding(24.dp))
        return
    }

    var nickname by remember(tracker.id, tracker.nickname) { mutableStateOf(tracker.nickname.orEmpty()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tracker.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenDiagnostics(tracker.id) }) {
                        Icon(Icons.Outlined.BugReport, contentDescription = "Diagnostics")
                    }
                },
            )
        },
    ) { innerPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Status") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PresenceBadge(tracker.presence)
                        ConnectionBadge(tracker.connectionState)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Last seen ${formatLastSeen(tracker.lastSeenAt)}")
                            Text("Battery ${formatBattery(tracker.batteryState)}")
                            Text(tracker.protocolType.name)
                        }
                        SignalMeter(signalPercent = tracker.proximity?.signalPercent ?: 0)
                    }
                }
            }
            item {
                SectionCard("Actions") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSearch(tracker.id) }) {
                            Icon(Icons.Outlined.MyLocation, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Search")
                        }
                        OutlinedButton(onClick = viewModel::refreshTracker, enabled = !uiState.isRefreshing) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(if (uiState.isRefreshing) "Refreshing" else "Refresh")
                        }
                        OutlinedButton(onClick = viewModel::ringTracker, enabled = !uiState.isRinging) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(if (uiState.isRinging) "Trying…" else "Ring")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Out-of-range monitor")
                        Switch(
                            checked = tracker.monitorEnabled,
                            onCheckedChange = {
                                viewModel.setMonitorEnabled(it) {
                                    TrackerMonitorService.syncMonitoring(context)
                                }
                            },
                        )
                    }
                }
            }
            item {
                SectionCard("Rename") {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Friendly name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.renameTracker(nickname) }) {
                        Text("Save name")
                    }
                }
            }
            item {
                SectionCard("Tracker Data") {
                    Text("Address", style = MaterialTheme.typography.labelMedium)
                    Text(tracker.deviceAddress, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Location", style = MaterialTheme.typography.labelMedium)
                    Text(formatLocation(tracker.lastLocation))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Manufacturer data", style = MaterialTheme.typography.labelMedium)
                    Text(tracker.manufacturerDataHex ?: "Not observed")
                }
            }
            item {
                SectionCard("Discovered Services") {
                    if (tracker.discoveredServices.isEmpty()) {
                        Text("No service discovery captured yet.")
                    } else {
                        tracker.discoveredServices.forEach { service ->
                            Text(service.serviceUuid, fontFamily = FontFamily.Monospace)
                            service.characteristics.forEach { characteristic ->
                                Text(
                                    buildString {
                                        append("• ")
                                        append(characteristic.characteristicUuid)
                                        if (characteristic.properties.isNotEmpty()) {
                                            append(" [")
                                            append(characteristic.properties.joinToString("|"))
                                            append("]")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                if (characteristic.descriptorUuids.isNotEmpty()) {
                                    Text(
                                        "  desc ${characteristic.descriptorUuids.joinToString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            item {
                SectionCard("Recent Sightings") {
                    if (uiState.observations.isEmpty()) {
                        Text("No stored observations yet.")
                    } else {
                        uiState.observations.forEach { observation ->
                            Text("${formatLastSeen(observation.seenAt)} · RSSI ${observation.rssi}")
                            observation.proximityEstimate?.let { proximity ->
                                val distance = proximity.estimatedDistanceMeters?.let { meters ->
                                    " · ≈${"%.1f".format(meters)} m"
                                }.orEmpty()
                                Text(
                                    "${proximity.descriptor}$distance",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}
