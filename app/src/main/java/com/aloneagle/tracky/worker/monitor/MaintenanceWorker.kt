package com.aloneagle.tracky.worker.monitor

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aloneagle.tracky.domain.repository.TrackerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val trackerRepository: TrackerRepository,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = runCatching {
        trackerRepository.pruneLogs()
        Result.success()
    }.getOrElse {
        Result.retry()
    }
}
