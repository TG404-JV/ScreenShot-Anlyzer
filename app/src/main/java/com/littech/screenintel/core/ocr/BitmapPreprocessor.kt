package com.littech.screenintel.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object BitmapPreprocessor {

    private const val TAG = "ScreenIntel"
    private const val MAX_DIMENSION = 1920

    /**
     * Decodes a downsampled bitmap from Uri to prevent OutOfMemory errors (PRD §8.3 RAM Ceiling).
     * On Android 9+ (API 28+), uses ImageDecoder which supports modern formats (HDR, 10-bit color, HEIC, WebP, PNG, JPEG).
     * Falls back to BitmapFactory on older APIs or if ImageDecoder fails.
     */
    suspend fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDim: Int = MAX_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        // Preferred modern decoder on Android 9+ (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = info.size
                    val maxSide = max(size.width, size.height)
                    if (maxSide > maxDim) {
                        val sampleSize = Integer.highestOneBit((maxSide.toFloat() / maxDim.toFloat()).toInt()).coerceAtLeast(1)
                        decoder.setTargetSampleSize(sampleSize)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }

                val targetBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
                } else {
                    bitmap
                }
                return@withContext targetBitmap
            } catch (e: Exception) {
                Log.w(TAG, "ImageDecoder failed for uri=$uri (${e.message}), trying BitmapFactory fallback...", e)
            }
        }

        // Standard BitmapFactory decoder (API 26-27 or fallback)
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            var streamOpened = false
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                streamOpened = true
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            if (!streamOpened) {
                Log.w(TAG, "Could not open InputStream for uri=$uri")
                return@withContext null
            }

            val rawWidth = options.outWidth
            val rawHeight = options.outHeight

            if (rawWidth <= 0 || rawHeight <= 0) {
                Log.w(TAG, "Invalid dimensions (${rawWidth}x${rawHeight}) for uri=$uri")
                return@withContext null
            }

            // Calculate inSampleSize as a clean power-of-2 for Android decoder compatibility
            var inSampleSize = 1
            val maxSide = max(rawWidth, rawHeight)
            if (maxSide > maxDim) {
                val ratio = (maxSide.toFloat() / maxDim.toFloat()).toInt()
                inSampleSize = Integer.highestOneBit(ratio).coerceAtLeast(1)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "BitmapFactory failed to decode uri=$uri: ${e.message}", e)
            null
        }
    }
}
