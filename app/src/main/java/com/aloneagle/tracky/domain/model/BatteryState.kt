package com.aloneagle.tracky.domain.model

data class BatteryState(
    val percentage: Int?,
    val status: Status,
) {
    enum class Status {
        Available,
        Unknown,
        Unsupported,
    }
}
