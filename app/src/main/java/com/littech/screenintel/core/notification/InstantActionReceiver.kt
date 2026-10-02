package com.littech.screenintel.core.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.littech.screenintel.data.repository.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class InstantActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COPY_OTP = "com.littech.screenintel.ACTION_COPY_OTP"
        const val ACTION_DELETE_SCREENSHOT = "com.littech.screenintel.ACTION_DELETE_SCREENSHOT"
        const val ACTION_PAY_UPI = "com.littech.screenintel.ACTION_PAY_UPI"

        const val EXTRA_OTP = "extra_otp"
        const val EXTRA_UPI = "extra_upi"
        const val EXTRA_SCREENSHOT_ID = "extra_screenshot_id"
        const val EXTRA_URI_STRING = "extra_uri_string"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId != -1) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notifId)
        }

        when (intent.action) {
            ACTION_COPY_OTP -> {
                val otp = intent.getStringExtra(EXTRA_OTP) ?: return
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("OTP", otp))
                Toast.makeText(context, "Copied OTP: $otp", Toast.LENGTH_SHORT).show()
            }

            ACTION_DELETE_SCREENSHOT -> {
                val screenshotId = intent.getLongExtra(EXTRA_SCREENSHOT_ID, -1L)
                val uriString = intent.getStringExtra(EXTRA_URI_STRING)
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. Delete from MediaStore ContentResolver if uri available
                        if (!uriString.isNullOrBlank()) {
                            try {
                                context.contentResolver.delete(Uri.parse(uriString), null, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // 2. Try deleting file directly
                        if (!filePath.isNullOrBlank()) {
                            try {
                                val file = File(filePath)
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // 3. Delete from local database
                        if (screenshotId != -1L) {
                            val repo = ScreenshotRepository.getInstance(context)
                            repo.deleteScreenshot(screenshotId)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Toast.makeText(context, "Screenshot deleted", Toast.LENGTH_SHORT).show()
            }

            ACTION_PAY_UPI -> {
                val upi = intent.getStringExtra(EXTRA_UPI) ?: return
                try {
                    val uri = Uri.parse("upi://pay?pa=$upi")
                    val payIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(payIntent)
                } catch (e: Exception) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("UPI ID", upi))
                    Toast.makeText(context, "No UPI app found. Copied: $upi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
