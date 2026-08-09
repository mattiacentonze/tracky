package com.aloneagle.tracky.domain.model

enum class TrackerConnectionState {
    Disconnected,
    Connecting,
    Connected,
    DiscoveringServices,
    Ready,
    Failed,
}
