package com.aloneagle.tracky.ui.feature.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.PairedBluetoothDevice
import com.aloneagle.tracky.domain.model.ScanSessionType
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.service.BluetoothDeviceCatalog
import com.aloneagle.tracky.domain.service.BluetoothRadioState
import com.aloneagle.tracky.domain.service.TrackerMonitorCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScanUiState(
    val isScanning: Boolean = false,
    val radioState: BluetoothRadioState = BluetoothRadioState.PermissionRequired,
    val sortOption: DeviceSortOption = DeviceSortOption.Distance,
    val sections: ScanSections = ScanSections(),
    val scanError: String? = null,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val monitorCoordinator: TrackerMonitorCoordinator,
    private val trackerRepository: TrackerRepository,
    private val bluetoothDeviceCatalog: BluetoothDeviceCatalog,
) : ViewModel() {
    private val discovered = MutableStateFlow<Map<String, BleScanResult>>(emptyMap())
    private val pairedDevices = MutableStateFlow<List<PairedBluetoothDevice>>(emptyList())
    private val scanning = MutableStateFlow(false)
    private val radioState = MutableStateFlow(BluetoothRadioState.PermissionRequired)
    private val sortOption = MutableStateFlow(DeviceSortOption.Distance)
    private val scanError = MutableStateFlow<String?>(null)
    private val _addedTrackers = MutableSharedFlow<String>()
    private val _findTrackers = MutableSharedFlow<String>()
    private val _messages = MutableSharedFlow<String>()
    private val scanMutex = Mutex()
    private val scanStateLock = Any()
    private val snapshotter = NearbyDeviceSnapshotter()
    private var scanJob: Job? = null

    val addedTrackers = _addedTrackers
    val findTrackers = _findTrackers
    val messages = _messages

    private val scanPreferences = combine(radioState, sortOption, scanError) { state, selectedSort, error ->
        Triple(state, selectedSort, error)
    }

    val uiState = combine(
        discovered,
        scanning,
        trackerRepository.observeKnownTrackers(),
        pairedDevices,
        scanPreferences,
    ) { discoveredDevices, isScanning, knownTrackers, paired, preferences ->
        val (currentRadioState, selectedSort, currentScanError) = preferences
        ScanUiState(
            isScanning = isScanning,
            radioState = currentRadioState,
            sortOption = selectedSort,
            scanError = currentScanError,
            sections = buildScanSections(
                discoveredDevices = discoveredDevices.values,
                knownTrackers = knownTrackers,
                pairedDevices = paired,
                sortOption = selectedSort,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScanUiState(),
    )

    init {
        refreshBluetoothEnvironment()
    }

    fun refreshBluetoothEnvironment() {
        val previousState = radioState.value
        val state = bluetoothDeviceCatalog.currentRadioState()
        if (state == BluetoothRadioState.Enabled) {
            if (previousState != BluetoothRadioState.Enabled) discovered.value = emptyMap()
            pairedDevices.value = bluetoothDeviceCatalog.pairedDevices()
        } else {
            stopScanning()
            pairedDevices.value = emptyList()
        }
        radioState.value = state
    }

    fun setSortOption(option: DeviceSortOption) {
        sortOption.value = option
    }

    fun startScanning() {
        refreshBluetoothEnvironment()
        if (radioState.value != BluetoothRadioState.Enabled) {
            stopScanning()
            return
        }
        val sessionId = "manual-${System.currentTimeMillis()}"
        val nextJob = synchronized(scanStateLock) {
            if (scanJob != null) return
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                scanMutex.withLock {
                    val ownerJob = currentCoroutineContext()[Job]
                    if (radioState.value != BluetoothRadioState.Enabled) {
                        synchronized(scanStateLock) {
                            if (scanJob === ownerJob) scanJob = null
                        }
                        return@withLock
                    }
                    scanning.value = true
                    scanError.value = null
                    snapshotter.clear()
                    discovered.value = emptyMap()
                    val snapshotJob = launch {
                        while (true) {
                            delay(LIVE_LIST_REFRESH_MILLIS)
                            discovered.value = snapshotter.snapshot(monotonicNowMillis())
                        }
                    }
                    try {
                        monitorCoordinator.scanNearby(sessionId).collect { result ->
                            snapshotter.record(result, monotonicNowMillis())
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (throwable: Throwable) {
                        val message = throwable.message ?: "Unable to start Bluetooth scan."
                        scanError.value = message
                        _messages.emit(message)
                    } finally {
                        snapshotJob.cancel()
                        snapshotter.clear()
                        discovered.value = emptyMap()
                        synchronized(scanStateLock) {
                            if (scanJob === ownerJob) {
                                scanJob = null
                                scanning.value = false
                            }
                        }
                    }
                }
            }.also { scanJob = it }
        }
        nextJob.start()
    }

    fun refreshVisibleDevices() {
        if (radioState.value != BluetoothRadioState.Enabled) return
        val scannerActive = synchronized(scanStateLock) { scanJob != null }
        if (!scannerActive) {
            startScanning()
            return
        }
        discovered.value = snapshotter.snapshot(monotonicNowMillis())
    }

    fun stopScanning() {
        val activeJob = synchronized(scanStateLock) {
            scanJob.also { scanJob = null }
        }
        activeJob?.cancel()
        snapshotter.clear()
        discovered.value = emptyMap()
        scanning.value = false
    }

    fun addTracker(deviceAddress: String) {
        viewModelScope.launch {
            val saved = ensureTracker(deviceAddress) ?: return@launch
            _messages.emit("Saved ${saved.displayName}")
            _addedTrackers.emit(saved.id)
        }
    }

    fun findDevice(deviceAddress: String) {
        viewModelScope.launch {
            val saved = ensureTracker(deviceAddress) ?: return@launch
            _findTrackers.emit(saved.id)
        }
    }

    private suspend fun ensureTracker(deviceAddress: String): KnownTracker? {
        val result = discovered.value.values.firstOrNull {
            scanResult -> scanResult.deviceAddress.equals(deviceAddress, ignoreCase = true)
        }
        if (result != null) return trackerRepository.upsertFromScan(result, ScanSessionType.Manual)
        _messages.emit("That device is no longer broadcasting. Wait for it to reappear and try again.")
        return null
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
    var permissionStateVersion by remember { mutableIntStateOf(0) }
    var enableRequestAttempted by rememberSaveable { mutableStateOf(false) }
    val discoveryPermissionsGranted = remember(permissionStateVersion) { context.hasDiscoveryPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionStateVersion++
        viewModel.refreshBluetoothEnvironment()
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshBluetoothEnvironment()
    }
    val requestBluetoothEnable = {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableRequestAttempted = true
            runCatching {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }
    val scanReady = discoveryPermissionsGranted && uiState.radioState == BluetoothRadioState.Enabled

    androidx.compose.runtime.DisposableEffect(context, discoveryPermissionsGranted) {
        if (discoveryPermissionsGranted) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothAdapter.ACTION_STATE_CHANGED -> viewModel.refreshBluetoothEnvironment()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        } else {
            onDispose { }
        }
    }
    LaunchedEffect(discoveryPermissionsGranted, uiState.radioState) {
        if (uiState.radioState == BluetoothRadioState.Enabled) {
            enableRequestAttempted = false
        }
        if (discoveryPermissionsGranted && uiState.radioState == BluetoothRadioState.PermissionRequired) {
            viewModel.refreshBluetoothEnvironment()
        }
        if (
            discoveryPermissionsGranted &&
            uiState.radioState == BluetoothRadioState.Disabled &&
            !enableRequestAttempted
        ) {
            requestBluetoothEnable()
        }
    }
    LifecycleStartEffect(scanReady) {
        permissionStateVersion++
        viewModel.refreshBluetoothEnvironment()
        if (scanReady) viewModel.startScanning()
        onStopOrDispose {
            viewModel.stopScanning()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    LaunchedEffect(Unit) {
        viewModel.addedTrackers.collect(onOpenTracker)
    }
    LaunchedEffect(Unit) {
        viewModel.findTrackers.collect(onFindTracker)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NearbyHeader(
            isScanning = uiState.isScanning,
            deviceCount = uiState.sections.totalDeviceCount,
            sortOption = uiState.sortOption,
            onSortChanged = viewModel::setSortOption,
            onRefresh = {
                when (uiState.radioState) {
                    BluetoothRadioState.Enabled -> viewModel.refreshVisibleDevices()
                    BluetoothRadioState.Disabled -> requestBluetoothEnable()
                    else -> Unit
                }
            },
        )
        if (!discoveryPermissionsGranted) {
            PermissionExplanation(
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ),
                    )
                },
            )
        } else if (uiState.radioState == BluetoothRadioState.Disabled) {
            BluetoothDisabledState(onEnableBluetooth = requestBluetoothEnable)
        } else if (uiState.radioState == BluetoothRadioState.Unsupported) {
            BluetoothUnsupportedState()
        } else if (uiState.radioState == BluetoothRadioState.Enabled) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (uiState.sections.totalDeviceCount == 0) {
                    item {
                        EmptyScanState(
                            isScanning = uiState.isScanning,
                            scanError = uiState.scanError,
                        )
                    }
                }
                deviceSection(
                    title = "PAIRED DEVICES",
                    devices = uiState.sections.yourDevices,
                    onSave = viewModel::addTracker,
                    onDetails = onOpenTracker,
                    onFind = viewModel::findDevice,
                )
                deviceSection(
                    title = "OTHER NAMED DEVICES",
                    devices = uiState.sections.namedNearby,
                    onSave = viewModel::addTracker,
                    onDetails = onOpenTracker,
                    onFind = viewModel::findDevice,
                )
                deviceSection(
                    title = "UNNAMED DEVICES",
                    devices = uiState.sections.unnamedNearby,
                    onSave = viewModel::addTracker,
                    onDetails = onOpenTracker,
                    onFind = viewModel::findDevice,
                )
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
        } else {
            BluetoothCheckingState()
        }
    }
}

@Composable
private fun NearbyHeader(
    isScanning: Boolean,
    deviceCount: Int,
    sortOption: DeviceSortOption,
    onSortChanged: (DeviceSortOption) -> Unit,
    onRefresh: () -> Unit,
) {
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
                Text("$deviceCount ${if (deviceCount == 1) "device" else "devices"} listed", fontWeight = FontWeight.SemiBold)
                Text(
                    if (isScanning) "Recently seen · refreshes every 5 seconds" else "Bluetooth scan paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SORT BY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DeviceSortOption.entries.forEach { option ->
                FilterChip(
                    selected = sortOption == option,
                    onClick = { onSortChanged(option) },
                    label = { Text(option.name) },
                )
            }
        }
    }
}

private fun LazyListScope.deviceSection(
    title: String,
    devices: List<ScanCandidateUi>,
    onSave: (String) -> Unit,
    onDetails: (String) -> Unit,
    onFind: (String) -> Unit,
) {
    if (devices.isEmpty()) return
    item(key = "header-$title") {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(devices.size.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(7.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    items(
        items = devices,
        key = { candidate -> "$title-${candidate.deviceAddress}" },
    ) { candidate ->
        DeviceCard(
            candidate = candidate,
            onSave = { onSave(candidate.deviceAddress) },
            onDetails = { candidate.savedTracker?.id?.let(onDetails) },
            onFind = { onFind(candidate.deviceAddress) },
        )
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
    val canFind = candidate.isSaved || candidate.isPaired
    val proximity = rawProximity(result.rssi)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canFind, onClick = onFind),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBadge(result.rssi)
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(candidate.deviceAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            if (candidate.isPaired) append("Paired")
                            if (candidate.isSaved) {
                                if (isNotEmpty()) append(" · ")
                                append("Saved")
                            }
                            if (isNotEmpty()) append(" · ")
                            append(if (result.connectable) "Connectable" else "Bluetooth LE")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(proximity.first, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        proximity.second,
                        style = MaterialTheme.typography.labelSmall,
                        color = proximity.third,
                    )
                }
                if (canFind) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!canFind) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save and rename") }
            } else {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onFind, modifier = Modifier.weight(1f)) { Text("Find") }
                    if (tracker != null) {
                        OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f)) { Text("Details") }
                    } else if (candidate.isPaired) {
                        OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Rename") }
                    }
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
private fun EmptyScanState(isScanning: Boolean, scanError: String?) {
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
            Text(
                when {
                    scanError != null -> "Bluetooth scan failed"
                    isScanning -> "Listening for devices…"
                    else -> "No devices found"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                scanError ?: "The live list refreshes every 5 seconds. Keep this screen open and move closer.",
                style = MaterialTheme.typography.bodySmall,
                color = if (scanError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BluetoothDisabledState(onEnableBluetooth: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ElevatedCard(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Bluetooth is off", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Allow Android to turn on Bluetooth. Tracky will load your paired devices and start scanning automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onEnableBluetooth, modifier = Modifier.fillMaxWidth()) { Text("Turn on Bluetooth") }
            }
        }
    }
}

@Composable
private fun BluetoothUnsupportedState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ElevatedCard(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Bluetooth unavailable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("This phone does not expose a Bluetooth adapter that Tracky can use.")
            }
        }
    }
}

@Composable
private fun BluetoothCheckingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
                    "Tracky needs Nearby devices and precise location permission for complete BLE results and proximity estimates. Android can filter some beacons without it. GPS does not improve Bluetooth range and can remain off; it is used only if you want a last-seen place. Data stays on this phone.",
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

private fun Context.hasDiscoveryPermissions(): Boolean = listOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION,
).all { permission -> ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED }

private fun monotonicNowMillis(): Long = System.nanoTime() / 1_000_000L
