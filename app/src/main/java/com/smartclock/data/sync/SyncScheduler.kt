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
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(KEY_TRIGGER_SOURCE to SyncTriggerSource.PERIODIC.name))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleImmediateSync(source: SyncTriggerSource = SyncTriggerSource.USER_ACTION) {
        val request = buildImmediateRequest(source)
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun buildImmediateRequest(source: SyncTriggerSource): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(KEY_TRIGGER_SOURCE to source.name))
            .build()

    companion object {
        private const val IMMEDIATE_NAME = "smartclock_sync_now"
        const val KEY_TRIGGER_SOURCE = "sync_trigger_source"
    }
}
