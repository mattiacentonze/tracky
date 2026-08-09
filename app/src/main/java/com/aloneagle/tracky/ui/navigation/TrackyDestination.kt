package com.aloneagle.tracky.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TrackyDestination(
    val route: String,
    val title: String,
    val icon: ImageVector? = null,
) {
    data object Trackers : TrackyDestination("trackers", "Trackers", Icons.Outlined.TrackChanges)
    data object Scan : TrackyDestination("scan", "Devices", Icons.AutoMirrored.Outlined.BluetoothSearching)
    data object Automations : TrackyDestination("automations", "Automations", Icons.Outlined.Bolt)
    data object Diagnostics : TrackyDestination("diagnostics?trackerId={trackerId}", "Debug", Icons.Outlined.BugReport)
    data object Settings : TrackyDestination("settings", "Settings", Icons.Outlined.Settings)
    data object Detail : TrackyDestination("detail/{trackerId}", "Tracker")
    data object Search : TrackyDestination("search/{trackerId}", "Search")

    companion object {
        val bottomBarItems = listOf(Scan, Automations, Settings)

        fun detailRoute(trackerId: String): String = "detail/$trackerId"

        fun searchRoute(trackerId: String): String = "search/$trackerId"

        fun diagnosticsRoute(trackerId: String? = null): String =
            if (trackerId == null) {
                "diagnostics"
            } else {
                "diagnostics?trackerId=$trackerId"
            }
    }
}
