package com.smartclock.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 注册周期同步任务（每 15 分钟，WorkManager 最小周期）。 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensurePeriodicSync() {
        val workManager = WorkManager.getInstance(context)
        if (LEGACY_PERIODIC_NAME != PERIODIC_NAME) {
            workManager.cancelUniqueWork(LEGACY_PERIODIC_NAME)
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(KEY_TRIGGER_SOURCE to SyncTriggerSource.PERIODIC.name))
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleImmediateSync(source: SyncTriggerSource = SyncTriggerSource.USER_ACTION) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(DEBOUNCED_NAME)
        val request = buildImmediateRequest(source)
        workManager.enqueueUniqueWork(
            IMMEDIATE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleDebouncedSync(source: SyncTriggerSource = SyncTriggerSource.USER_ACTION) {
        val request = buildImmediateRequest(source, DEBOUNCE_DELAY_SECONDS)
        WorkManager.getInstance(context).enqueueUniqueWork(
            DEBOUNCED_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAllSync() {
        val workManager = WorkManager.getInstance(context)
        if (LEGACY_PERIODIC_NAME != PERIODIC_NAME) {
            workManager.cancelUniqueWork(LEGACY_PERIODIC_NAME)
        }
        workManager.cancelUniqueWork(PERIODIC_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_NAME)
        workManager.cancelUniqueWork(DEBOUNCED_NAME)
    }

    private fun buildImmediateRequest(
        source: SyncTriggerSource,
        delaySeconds: Long = 0L
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_TRIGGER_SOURCE to source.name))
            .build()

    companion object {
        private const val LEGACY_PERIODIC_NAME = SyncWorker.NAME
        private const val PERIODIC_NAME = "smartclock_sync_periodic"
        private const val IMMEDIATE_NAME = "smartclock_sync_now"
        private const val DEBOUNCED_NAME = "smartclock_sync_debounced"
        private const val PERIODIC_INTERVAL_HOURS = 6L
        private const val DEBOUNCE_DELAY_SECONDS = 45L
        const val KEY_TRIGGER_SOURCE = "sync_trigger_source"
    }
}
