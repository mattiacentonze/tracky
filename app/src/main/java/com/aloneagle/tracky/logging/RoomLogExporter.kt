package com.aloneagle.tracky.logging

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.aloneagle.tracky.data.local.dao.BleLogEventDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RoomLogExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleLogEventDao: BleLogEventDao,
) : LogExporter {
    override suspend fun exportLogs(trackerId: String?): Uri = withContext(Dispatchers.IO) {
        val logs = bleLogEventDao.getAll()
            .filter { trackerId == null || it.trackerId == trackerId }
            .sortedBy { it.timestamp }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetFile = File(exportDir, "tracky-logs-${System.currentTimeMillis()}.txt")
        targetFile.writeText(
            buildString {
                appendLine("Tracky BLE diagnostics export")
                appendLine("Generated at: ${System.currentTimeMillis()}")
                appendLine("Tracker filter: ${trackerId ?: "all"}")
                appendLine()
                logs.forEach { log ->
                    appendLine("[${log.timestamp}] ${log.category}/${log.action}")
                    appendLine("  tracker=${log.trackerId ?: "-"} address=${log.deviceAddress ?: "-"} rssi=${log.rssi ?: "-"}")
                    appendLine("  service=${log.serviceUuid ?: "-"} characteristic=${log.characteristicUuid ?: "-"} result=${log.resultCode ?: "-"}")
                    appendLine("  payload=${log.payloadHex ?: "-"}")
                    appendLine("  message=${log.message}")
                    appendLine()
                }
            },
        )
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
    }
}
