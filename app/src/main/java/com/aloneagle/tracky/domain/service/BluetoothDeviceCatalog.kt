package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.PairedBluetoothDevice

enum class BluetoothRadioState {
    PermissionRequired,
    Unsupported,
    Disabled,
    Enabled,
}

interface BluetoothDeviceCatalog {
    fun currentRadioState(): BluetoothRadioState
    fun pairedDevices(): List<PairedBluetoothDevice>
}
