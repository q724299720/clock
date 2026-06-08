package com.smartclock.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartclock.service.ActiveAlarmCoordinator
import com.smartclock.widget.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncCoordinator: SyncCoordinator,
    private val activeAlarmCoordinator: ActiveAlarmCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val triggerSource = inputData.getString(SyncScheduler.KEY_TRIGGER_SOURCE)
            ?.let { runCatching { SyncTriggerSource.valueOf(it) }.getOrNull() }
            ?: SyncTriggerSource.WORKER
        return try {
            syncCoordinator.syncNow(triggerSource)?.let { report ->
                activeAlarmCoordinator.restoreForUser(report.userId)
                WidgetUpdater.updateAll(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            syncCoordinator.recordFailure(triggerSource, e)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "smartclock_sync"
    }
}
