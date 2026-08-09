package com.aloneagle.tracky.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores user-enabled monitoring after a reboot or application update. */
class TrackyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            TrackerMonitorService.syncMonitoring(context)
        }
    }
}
