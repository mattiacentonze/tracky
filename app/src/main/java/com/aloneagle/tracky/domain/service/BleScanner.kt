package com.aloneagle.tracky.domain.service

import com.aloneagle.tracky.domain.model.BleScanResult
import com.aloneagle.tracky.domain.model.ScanSessionType
import kotlinx.coroutines.flow.Flow

interface BleScanner {
    fun scan(
        sessionId: String,
        sessionType: ScanSessionType,
        targetAddresses: Set<String> = emptySet(),
        onScanHealthChanged: (Boolean) -> Unit = {},
    ): Flow<BleScanResult>
}

class BleScanException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
