package com.aloneagle.tracky

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aloneagle.tracky.notifications.TrackyNotifications
import com.aloneagle.tracky.worker.monitor.MaintenanceWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class TrackyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: TrackyNotifications

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "tracky-maintenance",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
