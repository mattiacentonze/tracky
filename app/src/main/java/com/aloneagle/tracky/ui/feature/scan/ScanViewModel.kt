package com.aloneagle.tracky.ui.feature.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.service.TrackerMonitorCoordinator
import com.aloneagle.tracky.service.TrackerMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanCandidateUi(
    val result: BleScanResult,
    val savedTracker: KnownTracker?,
)

data class ScanUiState(
    val isScanning: Boolean = false,
    val devices: List<ScanCandidateUi> = emptyList(),
    val savedNotSeen: List<KnownTracker> = emptyList(),
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val monitorCoordinator: TrackerMonitorCoordinator,
    private val trackerRepository: TrackerRepository,
) : ViewModel() {
    private val discovered = MutableStateFlow<Map<String, BleScanResult>>(emptyMap())
    private val scanning = MutableStateFlow(false)
    private val _addedTrackers = MutableSharedFlow<String>()
    private val _messages = MutableSharedFlow<String>()
    private var scanJob: Job? = null
    private var cleanupJob: Job? = null

    val addedTrackers = _addedTrackers
    val messages = _messages

    val uiState = combine(
        discovered,
        scanning,
        trackerRepository.observeKnownTrackers(),
    ) { discoveredDevices, isScanning, knownTrackers ->
        val knownById = knownTrackers.associateBy(KnownTracker::id)
        ScanUiState(
            isScanning = isScanning,
            devices = discoveredDevices.values
                .sortedWith(compareByDescending<BleScanResult> { it.rssi }.thenByDescending { it.seenAt })
                .map { result -> ScanCandidateUi(result, knownById[result.deviceAddress]) },
            savedNotSeen = knownTrackers
                .filterNot { tracker -> discoveredDevices.containsKey(tracker.deviceAddress) }
                .sortedBy { tracker -> tracker.displayName.lowercase() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScanUiState(),
    )

    fun rescan() {
        stopScanning()
        scanning.value = true
        discovered.value = emptyMap()
        val sessionId = "manual-${System.currentTimeMillis()}"
        scanJob = viewModelScope.launch {
            try {
                monitorCoordinator.scanNearby(sessionId).collect { result ->
                    discovered.update { current -> current + (result.deviceAddress to result) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _messages.emit(throwable.message ?: "Unable to start Bluetooth scan.")
            } finally {
                scanning.value = false
            }
        }
        cleanupJob = viewModelScope.launch {
            while (true) {
                delay(DEVICE_EXPIRY_CHECK_MILLIS)
                val cutoff = System.currentTimeMillis() - DEVICE_EXPIRY_MILLIS
                discovered.update { devices ->
                    devices.filterValues { result -> result.seenAt >= cutoff }
                }
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        scanning.value = false
    }

    fun addTracker(deviceAddress: String) {
        val result = discovered.value[deviceAddress] ?: return
        viewModelScope.launch {
            val saved = trackerRepository.upsertFromScan(result, ScanSessionType.Manual)
            _messages.emit("Saved ${saved.displayName}")
            _addedTrackers.emit(saved.id)
        }
    }
}

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onOpenTracker: (String) -> Unit,
    onFindTracker: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionStateVersion by remember { mutableIntStateOf(0) }
    val blePermissionsGranted = remember(permissionStateVersion) { context.hasBlePermissions() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionStateVersion++ }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, blePermissionsGranted) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (blePermissionsGranted) {
                    TrackerMonitorService.syncMonitoring(context)
                    viewModel.rescan()
                }
                Lifecycle.Event.ON_STOP -> viewModel.stopScanning()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && blePermissionsGranted) {
            TrackerMonitorService.syncMonitoring(context)
            viewModel.rescan()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.stopScanning()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    LaunchedEffect(Unit) {
        viewModel.addedTrackers.collect(onOpenTracker)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NearbyHeader(
            isScanning = uiState.isScanning,
            deviceCount = uiState.devices.size,
            onRefresh = { if (blePermissionsGranted) viewModel.rescan() },
        )
        if (!blePermissionsGranted) {
            PermissionExplanation(
                onRequestPermissions = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ),
                    )
                },
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (uiState.devices.isEmpty()) {
                    item { EmptyScanState(isScanning = uiState.isScanning) }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp, bottom = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("SIGNAL STRENGTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("CURRENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(
                        count = uiState.devices.size,
                        key = { index -> uiState.devices[index].result.deviceAddress },
                    ) { index ->
                        DeviceCard(
                            candidate = uiState.devices[index],
                            onSave = { viewModel.addTracker(uiState.devices[index].result.deviceAddress) },
                            onDetails = { uiState.devices[index].savedTracker?.id?.let(onOpenTracker) },
                            onFind = { uiState.devices[index].savedTracker?.id?.let(onFindTracker) },
                        )
                    }
                }
                if (uiState.savedNotSeen.isNotEmpty()) {
                    item {
                        Text(
                            "SAVED · NOT CURRENTLY SEEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 9.dp, bottom = 3.dp),
                        )
                    }
                    items(
                        count = uiState.savedNotSeen.size,
                        key = { index -> "saved-${uiState.savedNotSeen[index].id}" },
                    ) { index ->
                        val saved = uiState.savedNotSeen[index]
                        SavedDeviceCard(
                            tracker = saved,
                            onFind = { onFindTracker(saved.id) },
                            onDetails = { onOpenTracker(saved.id) },
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(13.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Distance is an estimate. Walls, pockets and different radio hardware affect Bluetooth signal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceCard(
    tracker: KnownTracker,
    onFind: () -> Unit,
    onDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tracker.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        tracker.advertisedName?.takeUnless { it == tracker.displayName } ?: tracker.deviceAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Waiting for a Bluetooth broadcast", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onFind, modifier = Modifier.weight(1f)) { Text("Find") }
                OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f)) { Text("Details") }
            }
        }
    }
}

@Composable
private fun NearbyHeader(isScanning: Boolean, deviceCount: Int, onRefresh: () -> Unit) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 14.dp, top = 22.dp, bottom = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("NEARBY DEVICES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Find a device", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onRefresh) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Scan again")
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text("$deviceCount ${if (deviceCount == 1) "device" else "devices"} in range", fontWeight = FontWeight.SemiBold)
                Text(
                    if (isScanning) "Scanning nearby broadcasts" else "Tap refresh to scan again",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    candidate: ScanCandidateUi,
    onSave: () -> Unit,
    onDetails: () -> Unit,
    onFind: () -> Unit,
) {
    val result = candidate.result
    val tracker = candidate.savedTracker
    val displayName = tracker?.displayName
        ?: result.resolvedName
        ?: result.advertisedName
        ?: "Unknown device"
    val secondaryName = result.advertisedName
        ?.takeUnless { it == displayName }
        ?: result.resolvedName?.takeUnless { it == displayName }
        ?: result.deviceAddress
    val proximity = rawProximity(result.rssi)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tracker != null, onClick = onFind),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBadge(result.rssi)
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(secondaryName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(if (result.connectable) "Connectable" else "Bluetooth LE")
                            if (tracker != null) append(" · Saved")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(proximity.first, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(proximity.second, style = MaterialTheme.typography.labelSmall, color = proximity.third)
                }
                if (tracker != null) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tracker == null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save and rename") }
            } else {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onFind, modifier = Modifier.weight(1f)) { Text("Find") }
                    OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f)) { Text("Details") }
                }
            }
        }
    }
}

@Composable
private fun SignalBadge(rssi: Int) {
    val color = when {
        rssi >= -62 -> MaterialTheme.colorScheme.primary
        rssi >= -76 -> androidx.compose.ui.graphics.Color(0xFFD18B18)
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(color.copy(alpha = 0.11f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(34.dp).background(color.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(19.dp), tint = color)
        }
    }
}

@Composable
private fun EmptyScanState(isScanning: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(if (isScanning) "Listening for devices…" else "No devices found", style = MaterialTheme.typography.titleMedium)
            Text("Keep this screen open and move closer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionExplanation(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ElevatedCard(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Allow nearby device access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tracky needs Bluetooth scan access to list nearby broadcasts and compare signal strength. Device names and sightings stay on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun rawProximity(rssi: Int): Triple<String, String, androidx.compose.ui.graphics.Color> {
    val color = when {
        rssi >= -62 -> MaterialTheme.colorScheme.primary
        rssi >= -76 -> androidx.compose.ui.graphics.Color(0xFFD18B18)
        else -> MaterialTheme.colorScheme.error
    }
    val label = when {
        rssi >= -58 -> "Very close"
        rssi >= -68 -> "Nearby"
        rssi >= -78 -> "Getting warmer"
        else -> "Far away"
    }
    return Triple("$rssi dBm", label, color)
}

private fun Context.hasBlePermissions(): Boolean = listOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
).all { permission -> ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED }

private const val DEVICE_EXPIRY_CHECK_MILLIS = 3_000L
private const val DEVICE_EXPIRY_MILLIS = 12_000L
