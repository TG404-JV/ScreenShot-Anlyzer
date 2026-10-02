package com.littech.screenintel.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaStoreItem(
    val id: Long,
    val uri: Uri,
    val filePath: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val size: Long
)

class MediaStoreScanner(private val context: Context) {

    companion object {
        private const val TAG = "ScreenIntel"
    }

    /**
     * Scans MediaStore for all screenshot images across all device directories,
     * supporting Xiaomi/MIUI/HyperOS, Samsung, OnePlus, Oppo, Vivo, and Pixel devices.
     */
    suspend fun scanScreenshots(): List<MediaStoreItem> = withContext(Dispatchers.IO) {
        val itemsMap = LinkedHashMap<Long, MediaStoreItem>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Diagnostic: discover all image folders on the device and log their names and item counts
        logAvailableImageBuckets(collection)

        val projectionList = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Images.Media.RELATIVE_PATH)
        }

        val projection = projectionList.toTypedArray()

        // Build comprehensive selection query covering all known screenshot bucket names,
        // case variations, relative paths, and Android 14+ IS_SCREENSHOT flag.
        val conditions = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // 1. Known screenshot bucket display names (exact and case variations)
        val bucketNames = listOf(
            "Screenshots", "Screenshot", "ScreenShot", "SCREENSHOT", "screenshots",
            "ScreenCapture", "ScreenCaptures", "screencap", "screencaps", "Screencap", "Screencaps"
        )
        val bucketPlaceholders = bucketNames.joinToString(", ") { "?" }
        conditions.add("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} IN ($bucketPlaceholders)")
        selectionArgs.addAll(bucketNames)

        // 2. Bucket LIKE patterns (for subfolders or OEM variants like "DCIM/Screenshots")
        val bucketLikePatterns = listOf(
            "%screenshot%", "%Screenshot%", "%SCREENSHOT%",
            "%screen_shot%", "%Screen_Shot%", "%ScreenCapture%", "%screencap%"
        )
        for (pattern in bucketLikePatterns) {
            conditions.add("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?")
            selectionArgs.add(pattern)
        }

        // 3. Display name LIKE patterns (filenames starting with or containing screenshot identifiers)
        val nameLikePatterns = listOf(
            "%screenshot%", "%Screenshot%", "%SCREENSHOT%",
            "%screen_shot%", "%Screen_Shot%", "%screencap%",
            "%ScreenCapture%", "%Capture_%"
        )
        for (pattern in nameLikePatterns) {
            conditions.add("${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?")
            selectionArgs.add(pattern)
        }

        // 4. Relative path on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pathPatterns = listOf(
                "%Screenshots/%", "%screenshot%", "%ScreenCapture/%", "%Pictures/Screenshots/%", "%DCIM/Screenshots/%"
            )
            for (pattern in pathPatterns) {
                conditions.add("${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?")
                selectionArgs.add(pattern)
            }
        }

        val selection = "(" + conditions.joinToString(" OR ") + ")"
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        Log.i(TAG, "MediaStore query: selection clauses count = ${conditions.size}, args = ${selectionArgs.size}")

        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs.toTypedArray(),
                sortOrder
            )

            if (cursor == null) {
                Log.w(TAG, "MediaStore query returned NULL cursor!")
            } else {
                cursor.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
                    val dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA)
                    val dateCol = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val widthCol = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightCol = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                    val sizeCol = c.getColumnIndex(MediaStore.Images.Media.SIZE)

                    var rowCount = 0
                    while (c.moveToNext()) {
                        rowCount++
                        if (idCol < 0) continue
                        val id = c.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val filePath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                        val dateAdded = if (dateCol >= 0) c.getLong(dateCol) * 1000L else System.currentTimeMillis()
                        val width = if (widthCol >= 0) c.getInt(widthCol) else 0
                        val height = if (heightCol >= 0) c.getInt(heightCol) else 0
                        val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L

                        itemsMap[id] = MediaStoreItem(
                            id = id,
                            uri = contentUri,
                            filePath = filePath,
                            dateAdded = dateAdded,
                            width = width,
                            height = height,
                            size = size
                        )
                    }
                    Log.i(TAG, "MediaStore query matched $rowCount rows. Unique screenshot items: ${itemsMap.size}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Primary MediaStore screenshot query failed with exception: ${e.message}", e)
        }

        // Fallback: If 0 items matched via selection, query all images and filter in memory by bucket/name
        if (itemsMap.isEmpty()) {
            Log.w(TAG, "Primary query returned 0 items. Running memory-filtered fallback scan...")
            runFallbackScan(collection, projection, itemsMap)
        }

        Log.i(TAG, "Final scanScreenshots result: ${itemsMap.size} screenshots found on device")
        itemsMap.values.toList()
    }

    private fun runFallbackScan(
        collection: Uri,
        projection: Array<String>,
        itemsMap: LinkedHashMap<Long, MediaStoreItem>
    ) {
        try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = c.getColumnIndex(MediaStore.Images.Media.DATA)
                val dateCol = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeCol = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                val bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1

                var inspected = 0
                while (c.moveToNext()) {
                    inspected++
                    val bucketName = if (bucketCol >= 0) c.getString(bucketCol) ?: "" else ""
                    val displayName = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                    val relPath = if (relPathCol >= 0) c.getString(relPathCol) ?: "" else ""
                    val dataPath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""

                    val isScreenshot = isLikelyScreenshot(bucketName, displayName, relPath, dataPath)
                    if (isScreenshot && idCol >= 0) {
                        val id = c.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val dateAdded = if (dateCol >= 0) c.getLong(dateCol) * 1000L else System.currentTimeMillis()
                        val width = if (widthCol >= 0) c.getInt(widthCol) else 0
                        val height = if (heightCol >= 0) c.getInt(heightCol) else 0
                        val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L

                        itemsMap[id] = MediaStoreItem(
                            id = id,
                            uri = contentUri,
                            filePath = dataPath,
                            dateAdded = dateAdded,
                            width = width,
                            height = height,
                            size = size
                        )
                    }
                }
                Log.i(TAG, "Fallback scan inspected $inspected images, identified ${itemsMap.size} screenshots")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback scan failed: ${e.message}", e)
        }
    }

    private fun isLikelyScreenshot(bucket: String, name: String, relPath: String, data: String): Boolean {
        val b = bucket.lowercase()
        val n = name.lowercase()
        val r = relPath.lowercase()
        val d = data.lowercase()

        return b.contains("screenshot") || b.contains("screen_shot") || b.contains("screencap") || b.contains("screencapture") ||
                n.contains("screenshot") || n.contains("screen_shot") || n.contains("screencap") || n.contains("screencapture") ||
                r.contains("screenshot") || r.contains("screencap") ||
                d.contains("screenshot") || d.contains("screencap")
    }

    private fun logAvailableImageBuckets(collection: Uri) {
        try {
            val cursor = context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use { c ->
                val bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val counts = mutableMapOf<String, Int>()
                var total = 0
                while (c.moveToNext()) {
                    total++
                    val bucket = if (bucketCol >= 0) c.getString(bucketCol) ?: "(Unknown)" else "(Unknown)"
                    counts[bucket] = (counts[bucket] ?: 0) + 1
                }
                Log.i(TAG, "Device Gallery Diagnostic: Total $total images across ${counts.size} folders: $counts")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not run gallery diagnostic: ${e.message}")
        }
    }
}
