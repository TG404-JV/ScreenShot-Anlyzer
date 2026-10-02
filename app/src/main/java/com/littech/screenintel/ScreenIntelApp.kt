package com.littech.screenintel

import android.app.Application
import com.littech.screenintel.core.notification.NotificationHelper
import com.littech.screenintel.data.sync.ScreenshotContentObserver
import com.littech.screenintel.data.sync.ScreenshotSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ScreenIntelApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var screenshotObserver: ScreenshotContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize notification channels for background indexing progress and heads-up instant actions
        NotificationHelper.createNotificationChannel(this)

        // Initialize periodic background screenshot sync via WorkManager
        ScreenshotSyncWorker.schedulePeriodicSync(this)

        // Register Real-time MediaStore ContentObserver for Capture-Time Instant Action Toast/Heads-Up
        screenshotObserver = ScreenshotContentObserver(
            context = applicationContext,
            scope = appScope
        ).also {
            it.register()
        }
    }
}
