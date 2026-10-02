package com.littech.screenintel.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.littech.screenintel.data.repository.ScreenshotRepository
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker that automatically scans and indexes new screenshots
 * periodically even when the app is closed or killed.
 */
class ScreenshotSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val repository = ScreenshotRepository.getInstance(applicationContext)
            repository.scanAndIndex(isForeground = false)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ScreenshotSyncWork"
        const val WORK_NAME_ONETIME = "ScreenshotSyncWorkOneTime"

        fun scheduleImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<ScreenshotSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONETIME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            // Run periodic background check every 1 hour (minimum 15-minute flex window)
            val syncRequest = PeriodicWorkRequestBuilder<ScreenshotSyncWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
