package com.aloneagle.tracky.ui.feature.automation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aloneagle.tracky.domain.model.KnownTracker
import com.aloneagle.tracky.domain.model.ProximityAutomationAction
import com.aloneagle.tracky.domain.model.ProximityAutomationEvent
import com.aloneagle.tracky.domain.model.ProximityAutomationRule
import com.aloneagle.tracky.domain.model.ProximityTransition
import com.aloneagle.tracky.domain.repository.AutomationRepository
import com.aloneagle.tracky.domain.repository.StoredAutomationRule
import com.aloneagle.tracky.domain.repository.TrackerRepository
import com.aloneagle.tracky.domain.service.AutomationActionExecutor
import com.aloneagle.tracky.service.TrackerMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutomationRuleUi(
    val stored: StoredAutomationRule,
    val trackerName: String,
)

data class AutomationsUiState(
    val trackers: List<KnownTracker> = emptyList(),
    val rules: List<AutomationRuleUi> = emptyList(),
)

enum class AutomationActionChoice(val label: String, val icon: ImageVector) {
    Notification("Notification", Icons.Outlined.NotificationsNone),
    Sound("Sound", Icons.Outlined.GraphicEq),
    Vibration("Vibration", Icons.Outlined.Vibration),
    Wifi("Wi-Fi panel", Icons.Outlined.Wifi),
    WhatsApp("WhatsApp draft", Icons.Outlined.ChatBubbleOutline),
    Telegram("Telegram draft", Icons.Outlined.ChatBubbleOutline),
}

enum class NotificationPermissionOperation {
    Create,
    Enable,
    Test,
}

private data class PendingNotificationAction(
    val operation: NotificationPermissionOperation,
    val onGranted: () -> Unit,
)

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val trackerRepository: TrackerRepository,
    private val actionExecutor: AutomationActionExecutor,
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages

    val uiState = combine(
        trackerRepository.observeKnownTrackers(),
        automationRepository.observeRules(),
    ) { trackers, rules ->
        val names = trackers.associate { tracker -> tracker.id to tracker.displayName }
        AutomationsUiState(
            trackers = trackers,
            rules = rules.map { stored ->
                AutomationRuleUi(stored, names[stored.rule.trackerId] ?: "Unknown device")
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AutomationsUiState(),
    )

    fun createRule(
        trackerId: String,
        transition: ProximityTransition,
        radiusMeters: Double,
        actionChoice: AutomationActionChoice,
        message: String,
        onStored: () -> Unit,
    ) {
        val safeMessage = message.trim().ifBlank {
            if (transition == ProximityTransition.Enter) "The device entered your selected range." else "The device moved out of your selected range."
        }
        val action = when (actionChoice) {
            AutomationActionChoice.Notification -> ProximityAutomationAction.ShowNotification("Tracky proximity alert", safeMessage)
            AutomationActionChoice.Sound -> ProximityAutomationAction.PlaySound()
            AutomationActionChoice.Vibration -> ProximityAutomationAction.Vibrate()
            AutomationActionChoice.Wifi -> ProximityAutomationAction.OpenWifiPanel
            AutomationActionChoice.WhatsApp -> ProximityAutomationAction.WhatsAppDraft(safeMessage)
            AutomationActionChoice.Telegram -> ProximityAutomationAction.TelegramDraft(safeMessage)
        }
        val rule = ProximityAutomationRule(
            id = UUID.randomUUID().toString(),
            trackerId = trackerId,
            transition = transition,
            radiusMeters = radiusMeters,
            hysteresisMeters = max(1.0, radiusMeters * 0.15),
            minimumSamples = 3,
            minimumDwellMillis = 2_500L,
            cooldownMillis = 60_000L,
            actions = listOf(action),
        )
        viewModelScope.launch {
            automationRepository.save(rule)
            onStored()
        }
    }

    fun setEnabled(rule: ProximityAutomationRule, enabled: Boolean, onStored: () -> Unit) {
        viewModelScope.launch {
            automationRepository.setEnabled(rule.id, enabled)
            onStored()
        }
    }

    fun delete(rule: ProximityAutomationRule, onStored: () -> Unit) {
        viewModelScope.launch {
            automationRepository.delete(rule.id)
            onStored()
        }
    }

    fun test(ruleUi: AutomationRuleUi) {
        viewModelScope.launch {
            val result = actionExecutor.execute(
                ProximityAutomationEvent(
                    ruleId = "test-${ruleUi.stored.rule.id}",
                    trackerId = ruleUi.stored.rule.trackerId,
                    transition = ruleUi.stored.rule.transition,
                    observedAtMillis = System.currentTimeMillis(),
                    distanceMeters = ruleUi.stored.rule.radiusMeters,
                    actions = ruleUi.stored.rule.actions,
                ),
                trackerName = ruleUi.trackerName,
            )
            _messages.emit(if (result.isSuccess) "Test action dispatched" else result.summary)
        }
    }

    fun reportMonitoringUnavailable() {
        viewModelScope.launch {
            _messages.emit("Grant Nearby devices permission before enabling monitoring.")
        }
    }

    fun reportNotificationPermissionDenied(operation: NotificationPermissionOperation) {
        val message = when (operation) {
            NotificationPermissionOperation.Create ->
                "Notification permission denied. The automation was not created."
            NotificationPermissionOperation.Enable ->
                "Notification permission denied. The rule remains paused."
            NotificationPermissionOperation.Test ->
                "Notification permission denied. The test was not run."
        }
        viewModelScope.launch { _messages.emit(message) }
    }
}

@Composable
fun AutomationsScreen(viewModel: AutomationsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showBuilder by remember { mutableStateOf(false) }
    var pendingNotificationAction by remember { mutableStateOf<PendingNotificationAction?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val syncMonitoring = {
        if (!TrackerMonitorService.syncMonitoring(context)) viewModel.reportMonitoringUnavailable()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pendingAction = pendingNotificationAction
        pendingNotificationAction = null
        if (granted) {
            pendingAction?.onGranted?.invoke()
        } else if (pendingAction != null) {
            viewModel.reportNotificationPermissionDenied(pendingAction.operation)
        }
    }
    val runWithNotificationPermission: (NotificationPermissionOperation, () -> Unit) -> Unit =
        { operation, action ->
            val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (alreadyGranted) {
                action()
            } else {
                pendingNotificationAction = PendingNotificationAction(operation, action)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ON-DEVICE RULES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Automations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = { if (uiState.trackers.isNotEmpty()) showBuilder = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "New automation")
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(15.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Let distance do the work", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Trigger an action after a device reliably enters or leaves your selected range.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (uiState.trackers.isEmpty()) {
                item {
                    EmptyAutomationState(
                        title = "Save a device first",
                        message = "Nearby Bluetooth devices can be saved from the Devices tab, then used in an automation.",
                    )
                }
            } else if (uiState.rules.isEmpty()) {
                item {
                    EmptyAutomationState(
                        title = "No automations yet",
                        message = "Create a private on-device rule for a saved device.",
                    )
                }
                item {
                    Button(onClick = { showBuilder = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("New automation")
                    }
                }
            } else {
                items(uiState.rules.size, key = { index -> uiState.rules[index].stored.rule.id }) { index ->
                    val ruleUi = uiState.rules[index]
                    RuleCard(
                        ruleUi = ruleUi,
                        onToggle = { enabled ->
                            val updateRule = {
                                viewModel.setEnabled(ruleUi.stored.rule, enabled, syncMonitoring)
                            }
                            if (enabled && ruleUi.stored.rule.dependsOnNotifications) {
                                runWithNotificationPermission(NotificationPermissionOperation.Enable, updateRule)
                            } else {
                                updateRule()
                            }
                        },
                        onDelete = { viewModel.delete(ruleUi.stored.rule, syncMonitoring) },
                        onTest = {
                            val testRule = { viewModel.test(ruleUi) }
                            if (ruleUi.stored.rule.dependsOnNotifications) {
                                runWithNotificationPermission(NotificationPermissionOperation.Test, testRule)
                            } else {
                                testRule()
                            }
                        },
                    )
                }
                item {
                    Button(onClick = { showBuilder = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("New automation")
                    }
                }
            }
            item {
                Text(
                    "Monitoring runs with a visible Android notification. Personal messages are always opened as drafts for you to review and send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
    }

    if (showBuilder && uiState.trackers.isNotEmpty()) {
        AutomationBuilderDialog(
            trackers = uiState.trackers,
            onDismiss = { showBuilder = false },
            onSave = { trackerId, transition, radius, action, message ->
                val createRule = {
                    viewModel.createRule(trackerId, transition, radius, action, message) {
                        syncMonitoring()
                    }
                    showBuilder = false
                }
                if (action.dependsOnNotifications) {
                    runWithNotificationPermission(NotificationPermissionOperation.Create, createRule)
                } else {
                    createRule()
                }
            },
        )
    }
}

@Composable
private fun RuleCard(
    ruleUi: AutomationRuleUi,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    val rule = ruleUi.stored.rule
    val action = rule.actions.first()
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${ruleUi.trackerName} ${if (rule.transition == ProximityTransition.Enter) "enters" else "leaves"} ${rule.radiusMeters.toInt()} m",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(action.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rule.enabled) "Monitoring" else "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onTest) { Text("Test") }
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete automation", modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun AutomationBuilderDialog(
    trackers: List<KnownTracker>,
    onDismiss: () -> Unit,
    onSave: (String, ProximityTransition, Double, AutomationActionChoice, String) -> Unit,
) {
    var trackerId by remember(trackers) { mutableStateOf(trackers.first().id) }
    var transition by remember { mutableStateOf(ProximityTransition.Leave) }
    var radius by remember { mutableFloatStateOf(8f) }
    var action by remember { mutableStateOf(AutomationActionChoice.Notification) }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New automation") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                BuilderLabel("Device")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trackers.forEach { tracker ->
                        FilterChip(
                            selected = trackerId == tracker.id,
                            onClick = { trackerId = tracker.id },
                            label = { Text(tracker.displayName) },
                        )
                    }
                }
                BuilderLabel("When it…")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(selected = transition == ProximityTransition.Enter, onClick = { transition = ProximityTransition.Enter }, label = { Text("Enters range") })
                    FilterChip(selected = transition == ProximityTransition.Leave, onClick = { transition = ProximityTransition.Leave }, label = { Text("Leaves range") })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BuilderLabel("Range")
                    Text("${radius.toInt()} m", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Slider(value = radius, onValueChange = { radius = it }, valueRange = 1f..20f, steps = 18)
                Text(
                    "Tracky adds a safety margin, repeated samples and a short dwell to prevent noisy boundary triggers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BuilderLabel("Then…")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AutomationActionChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = action == choice,
                            onClick = { action = choice },
                            label = { Text(choice.label) },
                            leadingIcon = { Icon(choice.icon, contentDescription = null, modifier = Modifier.size(17.dp)) },
                        )
                    }
                }
                if (action == AutomationActionChoice.Notification || action == AutomationActionChoice.WhatsApp || action == AutomationActionChoice.Telegram) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Message") },
                        placeholder = { Text("Optional — Tracky can use a default") },
                        minLines = 2,
                        maxLines = 3,
                    )
                }
                if (action == AutomationActionChoice.Wifi) {
                    Text("Android requires confirmation, so the trigger posts a notification that opens Wi-Fi controls.", style = MaterialTheme.typography.bodySmall)
                }
                if (action == AutomationActionChoice.WhatsApp || action == AutomationActionChoice.Telegram) {
                    Text("The trigger posts a notification that opens a pre-filled draft. Tracky never presses Send for you.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(trackerId, transition, radius.toDouble(), action, message) }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BuilderLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmptyAutomationState(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(9.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val ProximityAutomationAction.label: String
    get() = when (this) {
        is ProximityAutomationAction.ShowNotification -> "Send a notification"
        is ProximityAutomationAction.PlaySound -> "Play a sound"
        is ProximityAutomationAction.Vibrate -> "Vibrate the phone"
        ProximityAutomationAction.OpenWifiPanel -> "Open Wi-Fi controls"
        is ProximityAutomationAction.WhatsAppDraft -> "Prepare a WhatsApp draft"
        is ProximityAutomationAction.TelegramDraft -> "Prepare a Telegram draft"
    }

private val AutomationActionChoice.dependsOnNotifications: Boolean
    get() = when (this) {
        AutomationActionChoice.Notification,
        AutomationActionChoice.Wifi,
        AutomationActionChoice.WhatsApp,
        AutomationActionChoice.Telegram,
        -> true
        AutomationActionChoice.Sound,
        AutomationActionChoice.Vibration,
        -> false
    }

private val ProximityAutomationRule.dependsOnNotifications: Boolean
    get() = actions.any { action ->
        when (action) {
            is ProximityAutomationAction.ShowNotification,
            ProximityAutomationAction.OpenWifiPanel,
            is ProximityAutomationAction.WhatsAppDraft,
            is ProximityAutomationAction.TelegramDraft,
            -> true
            is ProximityAutomationAction.PlaySound,
            is ProximityAutomationAction.Vibrate,
            -> false
        }
    }
