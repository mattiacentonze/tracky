package com.aloneagle.tracky.ui.feature.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.ProximityEstimate
import com.aloneagle.tracky.domain.model.TrackerObservation
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.service.TrackerMonitorCoordinator
import com.aloneagle.tracky.service.TrackerMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val tracker: KnownTracker? = null,
    val observation: TrackerObservation? = null,
    val isRinging: Boolean = false,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    trackerRepository: TrackerRepository,
    monitorCoordinator: TrackerMonitorCoordinator,
    private val repository: TrackerRepository,
) : ViewModel() {
    val trackerId: String = checkNotNull(savedStateHandle["trackerId"])
    private val ringing = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>()

    val messages = _messages
    val uiState = combine(
        trackerRepository.observeTracker(trackerId),
        monitorCoordinator.observeRealtimeObservation(trackerId),
        ringing,
        refreshing,
    ) { tracker, observation, isRinging, isRefreshing ->
        SearchUiState(tracker, observation, isRinging, isRefreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SearchUiState(),
    )

    fun rename(name: String) {
        viewModelScope.launch {
            repository.renameTracker(trackerId, name.trim())
            _messages.emit("Device name saved")
        }
    }

    fun ring() {
        viewModelScope.launch {
            ringing.value = true
            val result = repository.ringTracker(trackerId, "search-ring-${System.currentTimeMillis()}")
            _messages.emit(result.message)
            ringing.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val result = repository.refreshTracker(trackerId, "search-refresh-${System.currentTimeMillis()}")
            _messages.emit(if (result.isSuccess) "Device refreshed" else result.exceptionOrNull()?.message ?: "Refresh failed")
            refreshing.value = false
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tracker = uiState.tracker
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current
    var soundEnabled by rememberSaveable { mutableStateOf(false) }
    var hapticsEnabled by rememberSaveable { mutableStateOf(true) }
    var renameOpen by remember { mutableStateOf(false) }
    var draftName by remember(tracker?.id, tracker?.nickname) { mutableStateOf(tracker?.displayName.orEmpty()) }
    var permissionStateVersion by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val blePermissionsGranted = remember(permissionStateVersion) { context.hasBlePermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionStateVersion++ }
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 38) }
    DisposableEffect(tone) { onDispose { tone.release() } }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val liveObservation = uiState.observation?.takeIf { observation ->
        now - observation.seenAt <= LIVE_SIGNAL_TIMEOUT_MILLIS
    }
    val estimate = liveObservation?.proximityEstimate ?: tracker?.proximity?.takeIf {
        tracker.lastSeenAt?.let { lastSeen -> now - lastSeen <= LIVE_SIGNAL_TIMEOUT_MILLIS } == true
    }
    LaunchedEffect(estimate?.level, soundEnabled, hapticsEnabled) {
        if (estimate?.level == ProximityEstimate.Level.Immediate || estimate?.level == ProximityEstimate.Level.Near) {
            if (hapticsEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            if (soundEnabled) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    DisposableEffect(lifecycleOwner, blePermissionsGranted, viewModel.trackerId) {
        val lifecycle = lifecycleOwner.lifecycle
        var started = false
        fun startFinder() {
            if (!started && blePermissionsGranted) {
                started = TrackerMonitorService.startSearch(context, viewModel.trackerId)
            }
        }
        fun stopFinder() {
            if (started) TrackerMonitorService.stopSearch(context, viewModel.trackerId)
            started = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startFinder()
                Lifecycle.Event.ON_STOP -> stopFinder()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startFinder()
        onDispose {
            lifecycle.removeObserver(observer)
            stopFinder()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FINDING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tracker?.displayName ?: "Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { renameOpen = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename") } },
            )
        },
    ) { innerPadding ->
        if (!blePermissionsGranted) {
            PermissionRequired(
                modifier = Modifier.padding(innerPadding),
                onGrant = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                },
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ProximityHero(estimate = estimate, rssi = liveObservation?.rssi) }
                item {
                    SignalHistory(signalPercent = estimate?.signalPercent ?: 0)
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedbackButton(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                            label = "Sound",
                            value = if (soundEnabled) "On" else "Off",
                            selected = soundEnabled,
                            onClick = { soundEnabled = !soundEnabled },
                        )
                        FeedbackButton(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Outlined.Vibration, contentDescription = null) },
                            label = "Haptics",
                            value = if (hapticsEnabled) "On" else "Off",
                            selected = hapticsEnabled,
                            onClick = { hapticsEnabled = !hapticsEnabled },
                        )
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::ring, enabled = !uiState.isRinging, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.isRinging) "Trying…" else "Ring")
                        }
                        OutlinedButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.isRefreshing) "Refreshing" else "Refresh")
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("How to search", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Walk slowly and rotate the phone. Tracky compares several readings before showing whether the signal is improving or weakening.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (renameOpen && tracker != null) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Its advertised Bluetooth name remains available in device details.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = draftName, onValueChange = { draftName = it }, singleLine = true, label = { Text("Display name") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (draftName.isNotBlank()) viewModel.rename(draftName)
                        renameOpen = false
                    },
                    enabled = draftName.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProximityHero(estimate: ProximityEstimate?, rssi: Int?) {
    val level = estimate?.level ?: ProximityEstimate.Level.Lost
    val color = when (level) {
        ProximityEstimate.Level.Immediate, ProximityEstimate.Level.Near -> MaterialTheme.colorScheme.primary
        ProximityEstimate.Level.Warm -> Color(0xFFD18B18)
        ProximityEstimate.Level.Far, ProximityEstimate.Level.Lost -> MaterialTheme.colorScheme.error
    }
    val label = when (level) {
        ProximityEstimate.Level.Immediate -> "Very close"
        ProximityEstimate.Level.Near -> "Nearby"
        ProximityEstimate.Level.Warm -> "Getting warmer"
        ProximityEstimate.Level.Far -> "Far away"
        ProximityEstimate.Level.Lost -> "Signal lost"
    }
    val distance = estimate?.estimatedDistanceMeters?.let { value ->
        if (value < 1) "≈${(value * 100).toInt()} cm" else "≈${"%.1f".format(value)} m"
    } ?: "—"
    val trend = when (estimate?.trend) {
        ProximityEstimate.Trend.Improving -> "↑ Getting closer"
        ProximityEstimate.Trend.Weakening -> "↓ Moving away"
        else -> "• Holding steady"
    }
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(226.dp)
                    .border(1.dp, color.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(174.dp).border(1.dp, color.copy(alpha = 0.17f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(116.dp).background(color.copy(alpha = 0.13f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(36.dp), tint = color)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(distance, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text(trend, style = MaterialTheme.typography.labelLarge)
            Text("RSSI ${rssi ?: "—"} dBm · BLE estimate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignalHistory(signalPercent: Int) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent signal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("$signalPercent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                val factors = listOf(.42f, .5f, .46f, .62f, .57f, .73f, .82f, 1f)
                factors.forEach { factor ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((8 + 34 * factor * (signalPercent.coerceAtLeast(20) / 100f)).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (factor == 1f) 1f else .22f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackButton(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        icon()
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PermissionRequired(modifier: Modifier, onGrant: () -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bluetooth access required", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Finding compares live Bluetooth signal readings from the selected device.")
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
        }
    }
}

private fun Context.hasBlePermissions(): Boolean = listOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
).all { permission -> ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED }

private const val LIVE_SIGNAL_TIMEOUT_MILLIS = 12_000L
