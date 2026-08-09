package com.aloneagle.tracky.domain.model

data class RingResult(
    val status: Status,
    val message: String,
) {
    enum class Status {
        Success,
        Unsupported,
        Unconfirmed,
        Failed,
    }
}
