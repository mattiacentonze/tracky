package com.aloneagle.tracky.ble.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aloneagle.tracky.domain.model.PairedBluetoothDevice
import com.aloneagle.tracky.domain.service.BluetoothDeviceCatalog
import com.aloneagle.tracky.domain.service.BluetoothRadioState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBluetoothDeviceCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BluetoothDeviceCatalog {
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun currentRadioState(): BluetoothRadioState {
        val bluetoothAdapter = adapter ?: return BluetoothRadioState.Unsupported
        if (!hasConnectPermission()) return BluetoothRadioState.PermissionRequired
        return runCatching { readRadioStateWithPermission(bluetoothAdapter) }
            .getOrDefault(BluetoothRadioState.PermissionRequired)
    }

    override fun pairedDevices(): List<PairedBluetoothDevice> {
        val bluetoothAdapter = adapter ?: return emptyList()
        if (!hasConnectPermission()) return emptyList()
        return runCatching {
            if (readEnabledWithPermission(bluetoothAdapter)) {
                readPairedDevicesWithPermission(bluetoothAdapter)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun hasConnectPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun readRadioStateWithPermission(bluetoothAdapter: BluetoothAdapter): BluetoothRadioState =
        if (bluetoothAdapter.isEnabled) BluetoothRadioState.Enabled else BluetoothRadioState.Disabled

    @SuppressLint("MissingPermission")
    private fun readEnabledWithPermission(bluetoothAdapter: BluetoothAdapter): Boolean =
        bluetoothAdapter.isEnabled

    @SuppressLint("MissingPermission")
    private fun readPairedDevicesWithPermission(bluetoothAdapter: BluetoothAdapter): List<PairedBluetoothDevice> =
        runCatching {
            bluetoothAdapter.bondedDevices
                .asSequence()
                .mapNotNull { device ->
                    val address = device.address?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    PairedBluetoothDevice(
                        deviceAddress = address,
                        systemName = device.alias?.takeIf(String::isNotBlank)
                            ?: device.name?.takeIf(String::isNotBlank),
                    )
                }
                .distinctBy { device -> device.deviceAddress }
                .sortedBy { device ->
                    (device.systemName ?: device.deviceAddress).lowercase(Locale.getDefault())
                }
                .toList()
        }.getOrDefault(emptyList())
}
