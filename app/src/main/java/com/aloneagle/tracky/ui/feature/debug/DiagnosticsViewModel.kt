package com.aloneagle.tracky.ui.feature.debug

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.domain.model.BleLogEvent
import com.aloneagle.tracky.domain.model.GattCharacteristicProperty
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.logging.LogExporter
import com.aloneagle.tracky.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WritableCharacteristicCandidate(
    val serviceUuid: String,
    val characteristicUuid: String,
    val properties: Set<GattCharacteristicProperty>,
)

data class DiagnosticsUiState(
    val trackerId: String? = null,
    val tracker: KnownTracker? = null,
    val logs: List<BleLogEvent> = emptyList(),
    val writableCandidates: List<WritableCharacteristicCandidate> = emptyList(),
    val isSendingWrite: Boolean = false,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackerRepository: TrackerRepository,
    private val logExporter: LogExporter,
) : ViewModel() {
    val trackerId: String? = savedStateHandle["trackerId"]
    private val _shareUris = MutableSharedFlow<Uri>()
    private val _messages = MutableSharedFlow<String>()
    private val sendingWrite = MutableStateFlow(false)

    val shareUris = _shareUris
    val messages = _messages

    private val trackerFlow = trackerId?.let(trackerRepository::observeTracker) ?: flowOf(null)
    private val logsFlow = trackerRepository.observeBleLogs(trackerId, limit = 800)

    val uiState = combine(trackerFlow, logsFlow, sendingWrite) { tracker, logs, isSendingWrite ->
        DiagnosticsUiState(
            trackerId = trackerId,
            tracker = tracker,
            logs = logs,
            writableCandidates = tracker
                ?.discoveredServices
                .orEmpty()
                .flatMap { service ->
                    service.characteristics.mapNotNull { characteristic ->
                        if (characteristic.supportsWrite) {
                            WritableCharacteristicCandidate(
                                serviceUuid = service.serviceUuid,
                                characteristicUuid = characteristic.characteristicUuid,
                                properties = characteristic.properties,
                            )
                        } else {
                            null
                        }
                    }
                },
            isSendingWrite = isSendingWrite,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DiagnosticsUiState(trackerId = trackerId),
        )

    fun exportLogs() {
        viewModelScope.launch {
            _shareUris.emit(logExporter.exportLogs(trackerId))
            _messages.emit("BLE log export ready")
        }
    }

    fun sendRawWrite(serviceUuid: String, characteristicUuid: String, payloadHex: String) {
        val resolvedTrackerId = trackerId ?: return
        viewModelScope.launch {
            sendingWrite.value = true
            val result = trackerRepository.debugWriteCharacteristic(
                id = resolvedTrackerId,
                serviceUuid = serviceUuid.trim(),
                characteristicUuid = characteristicUuid.trim(),
                payloadHex = payloadHex.trim(),
                sessionId = "diag-write-${System.currentTimeMillis()}",
            )
            _messages.emit(
                if (result.isSuccess) {
                    "Raw GATT write acknowledged. Confirm on the tracker whether any beep/vibration happened."
                } else {
                    result.exceptionOrNull()?.message ?: "Raw GATT write failed"
                },
            )
            sendingWrite.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracker = uiState.tracker
    val context = LocalContext.current
    var serviceUuid by remember(uiState.trackerId) { mutableStateOf("") }
    var characteristicUuid by remember(uiState.trackerId) { mutableStateOf("") }
    var payloadHex by remember(uiState.trackerId) { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.shareUris.collect { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Share BLE logs").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uiState.writableCandidates) {
        val selectedStillExists = uiState.writableCandidates.any { candidate ->
            candidate.serviceUuid.equals(serviceUuid, ignoreCase = true) &&
                candidate.characteristicUuid.equals(characteristicUuid, ignoreCase = true)
        }
        if (!selectedStillExists) {
            val firstCandidate = uiState.writableCandidates.firstOrNull()
            if (firstCandidate != null) {
                serviceUuid = firstCandidate.serviceUuid
                characteristicUuid = firstCandidate.characteristicUuid
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when {
                            tracker != null -> "${tracker.displayName} Diagnostics"
                            uiState.trackerId == null -> "Diagnostics"
                            else -> "Tracker Diagnostics"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::exportLogs) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export logs")
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
                SectionCard("BLE Log Export") {
                    Text("This screen captures scan events, connection attempts, service discovery, and read/write attempts for later Nut validation.")
                    uiState.trackerId?.let { Text("Filtered to $it") }
                }
            }
            if (uiState.trackerId != null) {
                item {
                    SectionCard("Raw GATT Probe") {
                        Text("Use this only for live Nut validation. A successful GATT write only means Android accepted the write; you still need to confirm whether the tracker reacted.")
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.writableCandidates.isEmpty()) {
                            Text("No writable characteristics in the current discovery snapshot. Refresh this tracker from the detail screen first, then come back here.")
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.writableCandidates.forEach { candidate ->
                                    TextButton(
                                        onClick = {
                                            serviceUuid = candidate.serviceUuid
                                            characteristicUuid = candidate.characteristicUuid
                                        },
                                    ) {
                                        Text(
                                            "${candidate.characteristicUuid.take(8)}… ${candidate.properties.joinToString("|")}",
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = serviceUuid,
                                onValueChange = { serviceUuid = it },
                                label = { Text("Service UUID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = characteristicUuid,
                                onValueChange = { characteristicUuid = it },
                                label = { Text("Characteristic UUID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = payloadHex,
                                onValueChange = { payloadHex = it },
                                label = { Text("Payload hex") },
                                placeholder = { Text("e.g. 01 or a55a01") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    viewModel.sendRawWrite(
                                        serviceUuid = serviceUuid,
                                        characteristicUuid = characteristicUuid,
                                        payloadHex = payloadHex,
                                    )
                                },
                                enabled = !uiState.isSendingWrite &&
                                    serviceUuid.isNotBlank() &&
                                    characteristicUuid.isNotBlank() &&
                                    payloadHex.isNotBlank(),
                            ) {
                                Text(if (uiState.isSendingWrite) "Sending…" else "Send raw write")
                            }
                        }
                    }
                }
            }
            if (uiState.logs.isEmpty()) {
                item {
                    SectionCard("No Logs Yet") {
                        Text("Scan, refresh, or search for a tracker to populate diagnostics.")
                    }
                }
            } else {
                items(uiState.logs.size, key = { index -> uiState.logs[index].id }) { index ->
                    val log = uiState.logs[index]
                    SectionCard("${log.category} · ${log.action}") {
                        Text(log.message)
                        Text("Time ${log.timestamp}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "tracker=${log.trackerId ?: "-"} rssi=${log.rssi ?: "-"} result=${log.resultCode ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (!log.serviceUuid.isNullOrBlank()) {
                            Text(log.serviceUuid, fontFamily = FontFamily.Monospace)
                        }
                        if (!log.characteristicUuid.isNullOrBlank()) {
                            Text(log.characteristicUuid, fontFamily = FontFamily.Monospace)
                        }
                        if (!log.payloadHex.isNullOrBlank()) {
                            Text(log.payloadHex, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
