package com.littech.screenintel.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.littech.screenintel.MainActivity
import com.littech.screenintel.R
import com.littech.screenintel.core.classifier.ExtractedEntities
import com.littech.screenintel.model.Screenshot

/**
 * Handles creation and updates of user-facing notifications:
 * 1. Background indexing progress and completion
 * 2. Real-time capture Heads-Up notifications with 1-tap instant actions (Copy OTP, Pay UPI, Delete)
 */
object NotificationHelper {

    const val CHANNEL_ID_INDEXING = "screenintel_indexing_channel"
    const val CHANNEL_ID_HEADS_UP = "screenintel_heads_up_actions"

    private const val NOTIFICATION_ID_PROGRESS = 1001
    private const val NOTIFICATION_ID_COMPLETE = 1002
    private const val NOTIFICATION_BASE_INSTANT = 2000

    private var lastProgressPostTimeMs = 0L
    private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 600L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Indexing Progress Channel (Low importance, silent)
            val name = context.getString(R.string.notif_channel_name)
            val descriptionText = context.getString(R.string.notif_channel_desc)
            val channelIndexing = NotificationChannel(CHANNEL_ID_INDEXING, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = descriptionText
                setShowBadge(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channelIndexing)

            // 2. Capture-Time Heads-Up Channel (High importance, pops up as a banner on top of the screen)
            val headsUpChannel = NotificationChannel(
                CHANNEL_ID_HEADS_UP,
                "Instant Screenshot Actions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up quick actions when a new screenshot is captured"
                setShowBadge(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(headsUpChannel)
        }
    }

    fun canPostNotification(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showProgressNotification(context: Context, current: Int, total: Int) {
        if (!canPostNotification(context)) return

        val now = System.currentTimeMillis()
        val isFirst = current <= 1
        val isLast = current >= total
        if (!isFirst && !isLast && (now - lastProgressPostTimeMs < MIN_PROGRESS_UPDATE_INTERVAL_MS)) {
            return
        }
        lastProgressPostTimeMs = now

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INDEXING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_indexing_title))
            .setContentText(context.getString(R.string.notif_indexing_progress, current, total))
            .setProgress(total, current, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelProgressNotification(context: Context) {
        lastProgressPostTimeMs = 0L
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showCompletionNotification(context: Context, totalIndexed: Int) {
        if (!canPostNotification(context) || totalIndexed <= 0) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INDEXING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_complete_title))
            .setContentText(context.getString(R.string.notif_complete_msg, totalIndexed))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COMPLETE, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Posts a high-priority Heads-Up Notification instantly when a screenshot is captured.
     * Offers 1-tap quick actions: "Copy OTP", "Pay UPI", or "Delete Immediately"
     * without even opening the main app!
     */
    fun showInstantActionNotification(
        context: Context,
        screenshot: Screenshot,
        entities: ExtractedEntities
    ) {
        if (!canPostNotification(context)) return

        val notifId = NOTIFICATION_BASE_INSTANT + (screenshot.id % 500).toInt()

        // Content intent: Opens ViewerScreen in MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_screenshot_id", screenshot.id)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dynamic Title & Summary based on extracted intelligence
        val title: String
        val body: String
        when {
            entities.otps.isNotEmpty() -> {
                val otp = entities.otps.first()
                title = "Verification OTP Detected"
                body = "OTP: $otp · Tap action to copy"
            }
            entities.upiIds.isNotEmpty() -> {
                val upi = entities.upiIds.first()
                title = "UPI Payment Detected"
                body = "UPI: $upi"
            }
            entities.phoneNumbers.isNotEmpty() -> {
                title = "Contact Detected"
                body = "Phone: ${entities.phoneNumbers.first()}"
            }
            else -> {
                title = "Screenshot Captured (${screenshot.category.name})"
                body = if (screenshot.rawOcrText.isNotBlank()) {
                    screenshot.rawOcrText.take(80).replace("\n", " ")
                } else {
                    "Tap to view details and extracted intelligence"
                }
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_HEADS_UP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)

        // Action 1: Copy OTP (if present)
        if (entities.otps.isNotEmpty()) {
            val otp = entities.otps.first()
            val copyOtpIntent = Intent(context, InstantActionReceiver::class.java).apply {
                action = InstantActionReceiver.ACTION_COPY_OTP
                putExtra(InstantActionReceiver.EXTRA_OTP, otp)
                putExtra(InstantActionReceiver.EXTRA_NOTIF_ID, notifId)
            }
            val copyOtpPending = PendingIntent.getBroadcast(
                context,
                notifId + 10,
                copyOtpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_save,
                "Copy OTP ($otp)",
                copyOtpPending
            )
        }

        // Action 2: Pay UPI (if present)
        if (entities.upiIds.isNotEmpty()) {
            val upi = entities.upiIds.first()
            val payIntent = Intent(context, InstantActionReceiver::class.java).apply {
                action = InstantActionReceiver.ACTION_PAY_UPI
                putExtra(InstantActionReceiver.EXTRA_UPI, upi)
                putExtra(InstantActionReceiver.EXTRA_NOTIF_ID, notifId)
            }
            val payPending = PendingIntent.getBroadcast(
                context,
                notifId + 20,
                payIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_send,
                "Pay UPI",
                payPending
            )
        }

        // Action 3: Delete Screenshot Immediately (Always available)
        val deleteIntent = Intent(context, InstantActionReceiver::class.java).apply {
            action = InstantActionReceiver.ACTION_DELETE_SCREENSHOT
            putExtra(InstantActionReceiver.EXTRA_SCREENSHOT_ID, screenshot.id)
            putExtra(InstantActionReceiver.EXTRA_URI_STRING, screenshot.uriString)
            putExtra(InstantActionReceiver.EXTRA_FILE_PATH, screenshot.filePath)
            putExtra(InstantActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val deletePending = PendingIntent.getBroadcast(
            context,
            notifId + 30,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_menu_delete,
            "Delete Immediately",
            deletePending
        )

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
