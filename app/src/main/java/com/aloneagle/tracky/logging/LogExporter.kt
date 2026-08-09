package com.aloneagle.tracky.logging

import android.net.Uri

interface LogExporter {
    suspend fun exportLogs(trackerId: String? = null): Uri
}
