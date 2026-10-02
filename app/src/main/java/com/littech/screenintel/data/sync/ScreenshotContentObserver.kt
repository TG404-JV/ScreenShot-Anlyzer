package com.littech.screenintel.data.sync

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.littech.screenintel.core.notification.NotificationHelper
import com.littech.screenintel.data.repository.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Real-time MediaStore ContentObserver that fires instantly when a user
 * captures a new screenshot on the device.
 *
 * Implements Capture-Time Instant Action:
 * Immediately performs on-device OCR + classification in background,
 * and posts a Heads-Up Notification with 1-tap quick actions:
 * - "Copy OTP"
 * - "Pay UPI"
 * - "Delete Immediately"
 * without needing to open the app!
 */
class ScreenshotContentObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onScreenshotDetected: (suspend () -> Unit)? = null
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var debounceJob: Job? = null
    private val repository = ScreenshotRepository.getInstance(context)

    companion object {
        private const val TAG = "ScreenshotObserver"
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "MediaStore onChange detected (uri=$uri)")

        // Debounce rapid MediaStore writes (initial capture + exif/thumbnail updates)
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            delay(800)
            try {
                // 1. Instant Capture-Time Action: process the newest captured item
                val result = repository.processNewestCapturedScreenshot()
                if (result != null) {
                    val (screenshot, entities) = result
                    Log.i(TAG, "Processed new screenshot ${screenshot.id}, posting heads-up notification")
                    NotificationHelper.showInstantActionNotification(context, screenshot, entities)
                }

                // 2. Invoke callback / full sync if registered
                onScreenshotDetected?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing instant screenshot capture: ${e.message}", e)
            }
        }
    }

    fun register() {
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                this
            )
            Log.i(TAG, "ScreenshotContentObserver registered on MediaStore")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ScreenshotContentObserver", e)
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(this)
            debounceJob?.cancel()
            Log.i(TAG, "ScreenshotContentObserver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister ScreenshotContentObserver", e)
        }
    }
}
