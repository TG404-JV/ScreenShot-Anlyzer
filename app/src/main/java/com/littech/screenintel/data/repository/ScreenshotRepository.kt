package com.littech.screenintel.data.repository

import android.content.Context
import android.util.Log
import com.littech.screenintel.core.classifier.CategoryClassifier
import com.littech.screenintel.core.classifier.ExtractedEntities
import com.littech.screenintel.core.classifier.PiiRedactor
import com.littech.screenintel.core.hasher.VisualHashCalculator
import com.littech.screenintel.core.ocr.BitmapPreprocessor
import com.littech.screenintel.core.ocr.MlKitOcrEngine
import com.littech.screenintel.data.local.ScreenIntelDatabaseHelper
import com.littech.screenintel.data.scanner.MediaStoreScanner
import com.littech.screenintel.model.Category
import com.littech.screenintel.model.DuplicateGroup
import com.littech.screenintel.model.Screenshot
import com.littech.screenintel.model.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class IndexingProgress(
    val isIndexing: Boolean = false,
    val current: Int = 0,
    val total: Int = 0
)

class ScreenshotRepository(
    private val context: Context,
    private val dbHelper: ScreenIntelDatabaseHelper = ScreenIntelDatabaseHelper(context),
    private val scanner: MediaStoreScanner = MediaStoreScanner(context),
    private val ocrEngine: MlKitOcrEngine = MlKitOcrEngine()
) {
    companion object {
        private const val TAG = "ScreenIntel"

        @Volatile
        private var instance: ScreenshotRepository? = null

        fun getInstance(context: Context): ScreenshotRepository {
            return instance ?: synchronized(this) {
                instance ?: ScreenshotRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexingMutex = Mutex()
    private val hasPendingRescan = AtomicBoolean(false)

    private val _indexingState = MutableStateFlow(IndexingProgress())
    val indexingState: Flow<IndexingProgress> = _indexingState.asStateFlow()

    private val _screenshotsFlow = MutableStateFlow<List<Screenshot>>(emptyList())
    val screenshotsFlow: Flow<List<Screenshot>> = _screenshotsFlow.asStateFlow()

    init {
        refreshScreenshots()
    }

    fun refreshScreenshots() {
        repositoryScope.launch {
            val list = dbHelper.getAllScreenshots()
            _screenshotsFlow.value = list
        }
    }

    suspend fun getScreenshotById(id: Long): Screenshot? = withContext(Dispatchers.IO) {
        dbHelper.getScreenshotById(id)
    }

    suspend fun search(query: String, category: Category? = null): List<SearchResult> = withContext(Dispatchers.IO) {
        dbHelper.searchFts(query, category)
    }

    suspend fun getByCategory(category: Category): List<Screenshot> = withContext(Dispatchers.IO) {
        dbHelper.getByCategory(category)
    }

    suspend fun getCategoryCounts(): Map<Category, Int> = withContext(Dispatchers.IO) {
        dbHelper.getCategoryCounts()
    }

    suspend fun deleteScreenshot(id: Long): Boolean = withContext(Dispatchers.IO) {
        val deleted = dbHelper.deleteScreenshot(id)
        if (deleted) {
            refreshScreenshots()
        }
        deleted
    }

    suspend fun bulkDeleteScreenshots(ids: Set<Long>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (id in ids) {
            if (dbHelper.deleteScreenshot(id)) count++
        }
        if (count > 0) refreshScreenshots()
        count
    }

    suspend fun restoreScreenshot(screenshot: Screenshot) = withContext(Dispatchers.IO) {
        dbHelper.insertScreenshot(screenshot)
        refreshScreenshots()
    }

    suspend fun restoreScreenshots(screenshots: List<Screenshot>) = withContext(Dispatchers.IO) {
        for (item in screenshots) {
            dbHelper.insertScreenshot(item)
        }
        refreshScreenshots()
    }

    /**
     * Finds groups of near-duplicate screenshots using 64-bit dHash Hamming distance <= threshold.
     * Uses Multi-Index Pigeonhole Bucketing to scale from O(N^2) to O(N).
     *
     * Mathematical guarantee: For a 64-bit hash split into 4 chunks of 16 bits,
     * if the Hamming distance <= 3, by the Pigeonhole Principle at least one 16-bit
     * chunk must have ZERO bit differences (exact match).
     *
     * Complexity:
     * - Old pairwise: 10,000 * 9,999 / 2 = ~50,000,000 comparisons (O(N^2)) -> ANR freeze!
     * - New bucketing: Hash lookups into 4 buckets + comparing only candidate collisions (O(N)) -> ~20ms!
     */
    suspend fun findDuplicates(threshold: Int = 3): List<DuplicateGroup> = withContext(Dispatchers.Default) {
        val all = dbHelper.getAllScreenshots()
        if (all.size < 2) return@withContext emptyList()

        // 4 Multi-Index Buckets (16 bits each: chunk 0, 1, 2, 3)
        val buckets = HashMap<Int, ArrayList<Int>>(all.size * 4)

        for (i in all.indices) {
            val h = all[i].visualHash
            val c0 = (0 shl 16) or ((h ushr 48).toInt() and 0xFFFF)
            val c1 = (1 shl 16) or ((h ushr 32).toInt() and 0xFFFF)
            val c2 = (2 shl 16) or ((h ushr 16).toInt() and 0xFFFF)
            val c3 = (3 shl 16) or (h.toInt() and 0xFFFF)

            buckets.getOrPut(c0) { ArrayList(2) }.add(i)
            buckets.getOrPut(c1) { ArrayList(2) }.add(i)
            buckets.getOrPut(c2) { ArrayList(2) }.add(i)
            buckets.getOrPut(c3) { ArrayList(2) }.add(i)
        }

        val visited = HashSet<Long>(all.size)
        val candidateSet = HashSet<Int>()
        val groups = ArrayList<DuplicateGroup>()

        for (i in all.indices) {
            val s1 = all[i]
            if (visited.contains(s1.id)) continue

            candidateSet.clear()
            val h1 = s1.visualHash
            val c0 = (0 shl 16) or ((h1 ushr 48).toInt() and 0xFFFF)
            val c1 = (1 shl 16) or ((h1 ushr 32).toInt() and 0xFFFF)
            val c2 = (2 shl 16) or ((h1 ushr 16).toInt() and 0xFFFF)
            val c3 = (3 shl 16) or (h1.toInt() and 0xFFFF)

            buckets[c0]?.let { candidateSet.addAll(it) }
            buckets[c1]?.let { candidateSet.addAll(it) }
            buckets[c2]?.let { candidateSet.addAll(it) }
            buckets[c3]?.let { candidateSet.addAll(it) }

            val duplicates = ArrayList<Screenshot>()
            var maxDistance = 0

            for (candIdx in candidateSet) {
                if (candIdx <= i) continue
                val s2 = all[candIdx]
                if (visited.contains(s2.id)) continue

                val distance = VisualHashCalculator.hammingDistance(h1, s2.visualHash)
                if (distance <= threshold) {
                    duplicates.add(s2)
                    visited.add(s2.id)
                    if (distance > maxDistance) {
                        maxDistance = distance
                    }
                }
            }

            if (duplicates.isNotEmpty()) {
                visited.add(s1.id)
                groups.add(
                    DuplicateGroup(
                        primary = s1,
                        duplicates = duplicates,
                        maxHammingDistance = maxDistance
                    )
                )
            }
        }

        groups
    }

    /**
     * Scans MediaStore for new screenshots, runs OCR, redacts PII, and indexes them in SQLite FTS.
     * Streams updates every 25 screenshots to keep UI responsive and memory bounded.
     *
     * @return Number of newly indexed screenshots, or -1 if indexing is already actively running.
     */
    suspend fun scanAndIndex(isForeground: Boolean = true): Int = withContext(Dispatchers.IO) {
        Log.i(TAG, "scanAndIndex called (isForeground=$isForeground)")
        if (!indexingMutex.tryLock()) {
            Log.i(TAG, "scanAndIndex: Mutex already locked, another indexing job is actively running. Setting pending rescan flag.")
            hasPendingRescan.set(true)
            return@withContext -1
        }

        try {
            val mediaItems = scanner.scanScreenshots()
            val indexedIds = dbHelper.getAllIndexedIds()
            // Priority Tiering: Newest screenshots first
            val pendingAll = mediaItems.filter { !indexedIds.contains(it.id) }.sortedByDescending { it.dateAdded }

            Log.i(TAG, "scanAndIndex: MediaStore found ${mediaItems.size} candidate screenshots, DB has ${indexedIds.size} already indexed. Pending new to index: ${pendingAll.size}")

            if (pendingAll.isEmpty()) {
                Log.i(TAG, "scanAndIndex: All screenshots are already indexed. No pending items.")
                refreshScreenshots()
                return@withContext 0
            }

            // Process all pending screenshots in streaming batches (no artificial 50-item cap)
            val currentBatchToProcess = pendingAll

            _indexingState.value = IndexingProgress(isIndexing = true, current = 0, total = pendingAll.size)
            var totalIndexedCount = 0
            val batchBuffer = ArrayList<Screenshot>(25)

            try {
                for ((index, item) in currentBatchToProcess.withIndex()) {
                    val currentStep = index + 1
                    _indexingState.value = IndexingProgress(
                        isIndexing = true,
                        current = currentStep,
                        total = pendingAll.size
                    )

                    if (currentStep == 1 || currentStep % 25 == 0 || currentStep == pendingAll.size) {
                        Log.i(TAG, "scanAndIndex progress: $currentStep / ${pendingAll.size} screenshots processed (Total newly indexed: $totalIndexedCount)")
                    }

                    // Update system notification progress
                    com.littech.screenintel.core.notification.NotificationHelper.showProgressNotification(
                        context = context,
                        current = currentStep,
                        total = pendingAll.size
                    )

                    try {
                        // Decode downsampled bitmap bounded to 1080p
                        val bitmap = BitmapPreprocessor.decodeSampledBitmapFromUri(context, item.uri)
                        if (bitmap != null) {
                            try {
                                val visualHash = VisualHashCalculator.computeDHash(bitmap)
                                val ocrResult = ocrEngine.processImage(bitmap)
                                val redactedText = PiiRedactor.redact(ocrResult.rawText)
                                val (category, entities) = CategoryClassifier.extractAndClassify(ocrResult.rawText)

                                val screenshot = Screenshot(
                                    id = item.id,
                                    uriString = item.uri.toString(),
                                    filePath = item.filePath,
                                    dateAdded = item.dateAdded,
                                    width = item.width,
                                    height = item.height,
                                    fileSize = item.size,
                                    rawOcrText = ocrResult.rawText,
                                    redactedOcrText = redactedText,
                                    category = category,
                                    visualHash = visualHash,
                                    extractedDataJson = entities.toJson(),
                                    processedAt = System.currentTimeMillis()
                                )

                                batchBuffer.add(screenshot)
                                totalIndexedCount++

                                // Flush batch to SQLite in a single transaction every 25 items
                                if (batchBuffer.size >= 25) {
                                    dbHelper.insertScreenshotsBatch(batchBuffer)
                                    Log.i(TAG, "scanAndIndex: Committed batch of ${batchBuffer.size} screenshots to SQLite (total inserted: $totalIndexedCount)")
                                    batchBuffer.clear()
                                    refreshScreenshots() // Live stream update to UI
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        } else {
                            Log.w(TAG, "scanAndIndex: Failed to decode bitmap for uri=${item.uri}, file=${item.filePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "scanAndIndex: Error indexing screenshot (id=${item.id}, uri=${item.uri}): ${e.message}", e)
                    }
                }

                // Flush remaining buffer
                if (batchBuffer.isNotEmpty()) {
                    dbHelper.insertScreenshotsBatch(batchBuffer)
                    Log.i(TAG, "scanAndIndex: Committed final batch of ${batchBuffer.size} screenshots to SQLite (total inserted: $totalIndexedCount)")
                    batchBuffer.clear()
                    refreshScreenshots()
                }
            } finally {
                com.littech.screenintel.core.notification.NotificationHelper.cancelProgressNotification(context)

                if (totalIndexedCount > 0) {
                    com.littech.screenintel.core.notification.NotificationHelper.showCompletionNotification(
                        context = context,
                        totalIndexed = totalIndexedCount
                    )
                }
            }

            Log.i(TAG, "scanAndIndex: Finished indexing $totalIndexedCount new screenshots. Total in DB: ${indexedIds.size + totalIndexedCount}")
            _indexingState.value = IndexingProgress(isIndexing = false, current = totalIndexedCount, total = totalIndexedCount)
            refreshScreenshots()
            totalIndexedCount
        } finally {
            indexingMutex.unlock()
            if (hasPendingRescan.getAndSet(false)) {
                Log.i(TAG, "scanAndIndex: Pending rescan was requested during active scan. Launching follow-up scan...")
                repositoryScope.launch {
                    scanAndIndex(isForeground)
                }
            }
        }
    }

    fun isIndexing(): Boolean = _indexingState.value.isIndexing

    suspend fun getAllScreenshotCount(): Int = withContext(Dispatchers.IO) {
        dbHelper.getAllScreenshots().size
    }

    /**
     * Re-runs entity extraction and categorization on all already-indexed screenshots.
     * Runs in milliseconds since OCR text is already cached in SQLite.
     */
    suspend fun reclassifyAllScreenshots(): Int = withContext(Dispatchers.IO) {
        val all = dbHelper.getAllScreenshots()
        if (all.isEmpty()) return@withContext 0

        _indexingState.value = IndexingProgress(isIndexing = true, current = 0, total = all.size)
        var updated = 0
        val pendingUpdates = ArrayList<Triple<Long, Category, String>>(50)
        try {
            for ((index, item) in all.withIndex()) {
                _indexingState.value = IndexingProgress(isIndexing = true, current = index + 1, total = all.size)
                val (newCategory, entities) = CategoryClassifier.extractAndClassify(item.rawOcrText)
                val newJson = entities.toJson()
                if (item.category != newCategory || item.extractedDataJson != newJson) {
                    pendingUpdates.add(Triple(item.id, newCategory, newJson))
                    updated++
                }

                if (pendingUpdates.size >= 50) {
                    dbHelper.updateCategoryAndEntitiesBatch(pendingUpdates)
                    pendingUpdates.clear()
                    refreshScreenshots()
                }
            }

            if (pendingUpdates.isNotEmpty()) {
                dbHelper.updateCategoryAndEntitiesBatch(pendingUpdates)
                pendingUpdates.clear()
                refreshScreenshots()
            }
        } finally {
            _indexingState.value = IndexingProgress(isIndexing = false, current = all.size, total = all.size)
            refreshScreenshots()
        }
        updated
    }

    /**
     * Rapidly detects and processes the single newest screenshot captured on the device.
     * Extracts text, runs classification and entity extraction, stores in SQLite/FTS,
     * and returns the Screenshot (along with ExtractedEntities) for instant notification actions.
     */
    suspend fun processNewestCapturedScreenshot(): Pair<Screenshot, ExtractedEntities>? = withContext(Dispatchers.IO) {
        val candidates = scanner.scanScreenshots()
        if (candidates.isEmpty()) return@withContext null

        val newest = candidates.maxByOrNull { it.dateAdded } ?: return@withContext null

        // Check if already processed in database
        val existing = dbHelper.getScreenshotById(newest.id)
        if (existing != null) {
            val entities = ExtractedEntities.fromJson(existing.extractedDataJson)
            return@withContext Pair(existing, entities)
        }

        // Process newly captured screenshot immediately
        val bitmap = BitmapPreprocessor.decodeSampledBitmapFromUri(context, newest.uri) ?: return@withContext null
        try {
            val visualHash = VisualHashCalculator.computeDHash(bitmap)
            val ocrResult = ocrEngine.processImage(bitmap)
            val redactedText = PiiRedactor.redact(ocrResult.rawText)
            val (category, entities) = CategoryClassifier.extractAndClassify(ocrResult.rawText)

            val screenshot = Screenshot(
                id = newest.id,
                uriString = newest.uri.toString(),
                filePath = newest.filePath,
                dateAdded = newest.dateAdded,
                width = newest.width,
                height = newest.height,
                fileSize = newest.size,
                rawOcrText = ocrResult.rawText,
                redactedOcrText = redactedText,
                category = category,
                visualHash = visualHash,
                extractedDataJson = entities.toJson(),
                processedAt = System.currentTimeMillis()
            )

            dbHelper.insertScreenshot(screenshot)
            try {
                dbHelper.writableDatabase.execSQL(
                    "INSERT OR REPLACE INTO ${ScreenIntelDatabaseHelper.TABLE_FTS}(docid, ${ScreenIntelDatabaseHelper.COL_RAW_OCR}, ${ScreenIntelDatabaseHelper.COL_REDACTED_OCR}, ${ScreenIntelDatabaseHelper.COL_CATEGORY}) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(screenshot.id, screenshot.rawOcrText, screenshot.redactedOcrText, screenshot.category.name)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            refreshScreenshots()
            Pair(screenshot, entities)
        } finally {
            bitmap.recycle()
        }
    }
}



