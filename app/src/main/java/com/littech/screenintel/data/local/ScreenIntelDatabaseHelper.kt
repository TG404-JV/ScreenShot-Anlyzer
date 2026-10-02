package com.littech.screenintel.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.littech.screenintel.model.Category
import com.littech.screenintel.model.Screenshot
import com.littech.screenintel.model.SearchResult

class ScreenIntelDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "screenintel.db"
        const val DATABASE_VERSION = 4

        const val TABLE_SCREENSHOTS = "screenshots"
        const val TABLE_FTS = "screenshots_fts"

        const val COL_ID = "id"
        const val COL_URI_STRING = "uri_string"
        const val COL_FILE_PATH = "file_path"
        const val COL_DATE_ADDED = "date_added"
        const val COL_WIDTH = "width"
        const val COL_HEIGHT = "height"
        const val COL_FILE_SIZE = "file_size"
        const val COL_RAW_OCR = "raw_ocr_text"
        const val COL_REDACTED_OCR = "redacted_ocr_text"
        const val COL_CATEGORY = "category"
        const val COL_VISUAL_HASH = "visual_hash"
        const val COL_EXTRACTED_DATA = "extracted_data_json"
        const val COL_PROCESSED_AT = "processed_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Base screenshots table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SCREENSHOTS (
                $COL_ID INTEGER PRIMARY KEY,
                $COL_URI_STRING TEXT NOT NULL,
                $COL_FILE_PATH TEXT NOT NULL,
                $COL_DATE_ADDED INTEGER NOT NULL,
                $COL_WIDTH INTEGER NOT NULL,
                $COL_HEIGHT INTEGER NOT NULL,
                $COL_FILE_SIZE INTEGER NOT NULL,
                $COL_RAW_OCR TEXT NOT NULL,
                $COL_REDACTED_OCR TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_VISUAL_HASH INTEGER NOT NULL,
                $COL_EXTRACTED_DATA TEXT NOT NULL DEFAULT '{}',
                $COL_PROCESSED_AT INTEGER NOT NULL
            );
            """.trimIndent()
        )

        // Performance indices
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_screenshots_cat_date ON $TABLE_SCREENSHOTS($COL_CATEGORY, $COL_DATE_ADDED DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_screenshots_date ON $TABLE_SCREENSHOTS($COL_DATE_ADDED DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_screenshots_hash ON $TABLE_SCREENSHOTS($COL_VISUAL_HASH);")

        // FTS4 Virtual Table for fast Full-Text Search
        createFtsAndTriggers(db)
    }

    private fun createFtsAndTriggers(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts4(
                content='$TABLE_SCREENSHOTS',
                $COL_RAW_OCR,
                $COL_REDACTED_OCR,
                $COL_CATEGORY,
                tokenize=unicode61
            );
            """.trimIndent()
        )

        // FTS Sync Triggers
        // NOTE: For SQLite FTS4 external content tables, deletion and old update terms MUST be removed BEFORE DELETE / BEFORE UPDATE.
        // If an AFTER DELETE trigger is used, the row is already gone from the content table, causing SQLite to throw "SQL logic error".
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_screenshots_ai AFTER INSERT ON $TABLE_SCREENSHOTS BEGIN
                INSERT INTO $TABLE_FTS(docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY)
                VALUES (new.$COL_ID, new.$COL_RAW_OCR, new.$COL_REDACTED_OCR, new.$COL_CATEGORY);
            END;
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_screenshots_bd BEFORE DELETE ON $TABLE_SCREENSHOTS BEGIN
                INSERT INTO $TABLE_FTS($TABLE_FTS, docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY)
                VALUES('delete', old.$COL_ID, old.$COL_RAW_OCR, old.$COL_REDACTED_OCR, old.$COL_CATEGORY);
            END;
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_screenshots_bu BEFORE UPDATE ON $TABLE_SCREENSHOTS BEGIN
                INSERT INTO $TABLE_FTS($TABLE_FTS, docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY)
                VALUES('delete', old.$COL_ID, old.$COL_RAW_OCR, old.$COL_REDACTED_OCR, old.$COL_CATEGORY);
            END;
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_screenshots_au AFTER UPDATE ON $TABLE_SCREENSHOTS BEGIN
                INSERT INTO $TABLE_FTS(docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY)
                VALUES (new.$COL_ID, new.$COL_RAW_OCR, new.$COL_REDACTED_OCR, new.$COL_CATEGORY);
            END;
            """.trimIndent()
        )
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // Self-healing migration for existing databases: drop buggy AFTER DELETE trigger if present
        try {
            db.execSQL("DROP TRIGGER IF EXISTS trg_screenshots_ad")
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_screenshots_bd BEFORE DELETE ON $TABLE_SCREENSHOTS BEGIN
                    INSERT INTO $TABLE_FTS($TABLE_FTS, docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY)
                    VALUES('delete', old.$COL_ID, old.$COL_RAW_OCR, old.$COL_REDACTED_OCR, old.$COL_CATEGORY);
                END;
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_screenshots_bu BEFORE UPDATE ON $TABLE_SCREENSHOTS BEGIN
                    INSERT INTO $TABLE_FTS($TABLE_FTS, docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY)
                    VALUES('delete', old.$COL_ID, old.$COL_RAW_OCR, old.$COL_REDACTED_OCR, old.$COL_CATEGORY);
                END;
                """.trimIndent()
            )

            // Ensure FTS index is populated — check if FTS row count matches screenshots row count
            val ssCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_SCREENSHOTS", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            val ftsCount = try {
                db.rawQuery("SELECT COUNT(*) FROM $TABLE_FTS", null).use {
                    if (it.moveToFirst()) it.getInt(0) else -1
                }
            } catch (e: Exception) { -1 }

            if (ftsCount < ssCount) {
                // FTS is out of sync — rebuild it entirely
                try {
                    db.execSQL("INSERT INTO $TABLE_FTS($TABLE_FTS) VALUES('rebuild')")
                } catch (e: Exception) {
                    // Fallback: manual repopulate
                    try {
                        db.execSQL("DELETE FROM $TABLE_FTS")
                        db.execSQL("INSERT INTO $TABLE_FTS(docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY) SELECT $COL_ID, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY FROM $TABLE_SCREENSHOTS")
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TRIGGER IF EXISTS trg_screenshots_ai")
        db.execSQL("DROP TRIGGER IF EXISTS trg_screenshots_ad")
        db.execSQL("DROP TRIGGER IF EXISTS trg_screenshots_au")
        db.execSQL("DROP TRIGGER IF EXISTS trg_screenshots_bd")
        db.execSQL("DROP TRIGGER IF EXISTS trg_screenshots_bu")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FTS")
        createFtsAndTriggers(db)
        try {
            db.execSQL("INSERT INTO $TABLE_FTS(docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY) SELECT $COL_ID, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY FROM $TABLE_SCREENSHOTS;")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun insertScreenshot(screenshot: Screenshot): Long {
        val values = ContentValues().apply {
            put(COL_ID, screenshot.id)
            put(COL_URI_STRING, screenshot.uriString)
            put(COL_FILE_PATH, screenshot.filePath)
            put(COL_DATE_ADDED, screenshot.dateAdded)
            put(COL_WIDTH, screenshot.width)
            put(COL_HEIGHT, screenshot.height)
            put(COL_FILE_SIZE, screenshot.fileSize)
            put(COL_RAW_OCR, screenshot.rawOcrText)
            put(COL_REDACTED_OCR, screenshot.redactedOcrText)
            put(COL_CATEGORY, screenshot.category.name)
            put(COL_VISUAL_HASH, screenshot.visualHash)
            put(COL_EXTRACTED_DATA, screenshot.extractedDataJson)
            put(COL_PROCESSED_AT, screenshot.processedAt)
        }
        return writableDatabase.insertWithOnConflict(
            TABLE_SCREENSHOTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Batch inserts screenshots inside a single SQLite transaction.
     * Prevents thousands of fsync operations when handling large collections (up to 10k items).
     */
    fun insertScreenshotsBatch(screenshots: List<Screenshot>): Int {
        val db = writableDatabase
        db.beginTransaction()
        var count = 0
        try {
            for (screenshot in screenshots) {
                val values = ContentValues().apply {
                    put(COL_ID, screenshot.id)
                    put(COL_URI_STRING, screenshot.uriString)
                    put(COL_FILE_PATH, screenshot.filePath)
                    put(COL_DATE_ADDED, screenshot.dateAdded)
                    put(COL_WIDTH, screenshot.width)
                    put(COL_HEIGHT, screenshot.height)
                    put(COL_FILE_SIZE, screenshot.fileSize)
                    put(COL_RAW_OCR, screenshot.rawOcrText)
                    put(COL_REDACTED_OCR, screenshot.redactedOcrText)
                    put(COL_CATEGORY, screenshot.category.name)
                    put(COL_VISUAL_HASH, screenshot.visualHash)
                    put(COL_EXTRACTED_DATA, screenshot.extractedDataJson)
                    put(COL_PROCESSED_AT, screenshot.processedAt)
                }
                if (db.insertWithOnConflict(TABLE_SCREENSHOTS, null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0) {
                    try {
                        db.execSQL(
                            "INSERT OR REPLACE INTO $TABLE_FTS(docid, $COL_RAW_OCR, $COL_REDACTED_OCR, $COL_CATEGORY) VALUES (?, ?, ?, ?)",
                            arrayOf<Any>(screenshot.id, screenshot.rawOcrText, screenshot.redactedOcrText, screenshot.category.name)
                        )
                    } catch (e: Exception) {
                        // Triggers may handle this, catch to prevent abort
                    }
                    count++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun getScreenshotById(id: Long): Screenshot? {
        val cursor = readableDatabase.query(
            TABLE_SCREENSHOTS,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToScreenshot(it) else null
        }
    }

    fun getAllScreenshots(): List<Screenshot> {
        val list = mutableListOf<Screenshot>()
        val cursor = readableDatabase.query(
            TABLE_SCREENSHOTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_DATE_ADDED DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToScreenshot(it))
            }
        }
        return list
    }

    fun getByCategory(category: Category): List<Screenshot> {
        val list = mutableListOf<Screenshot>()
        val cursor = readableDatabase.query(
            TABLE_SCREENSHOTS,
            null,
            "$COL_CATEGORY = ?",
            arrayOf(category.name),
            null,
            null,
            "$COL_DATE_ADDED DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToScreenshot(it))
            }
        }
        return list
    }

    fun updateCategoryAndEntities(id: Long, category: Category, extractedJson: String): Boolean {
        val cv = android.content.ContentValues().apply {
            put(COL_CATEGORY, category.name)
            put(COL_EXTRACTED_DATA, extractedJson)
        }
        return writableDatabase.update(TABLE_SCREENSHOTS, cv, "$COL_ID = ?", arrayOf(id.toString())) > 0
    }

    /**
     * Updates category and extracted data for multiple screenshots in a single atomic SQLite transaction.
     * Prevents hundreds of individual disk fsyncs and executes in milliseconds.
     */
    fun updateCategoryAndEntitiesBatch(updates: List<Triple<Long, Category, String>>): Int {
        if (updates.isEmpty()) return 0
        val db = writableDatabase
        db.beginTransaction()
        var count = 0
        try {
            val statement = db.compileStatement("UPDATE $TABLE_SCREENSHOTS SET $COL_CATEGORY = ?, $COL_EXTRACTED_DATA = ? WHERE $COL_ID = ?")
            for (update in updates) {
                statement.bindString(1, update.second.name)
                statement.bindString(2, update.third)
                statement.bindLong(3, update.first)
                statement.executeUpdateDelete()
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun getAllIndexedIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        val cursor = readableDatabase.query(
            TABLE_SCREENSHOTS,
            arrayOf(COL_ID),
            null,
            null,
            null,
            null,
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                ids.add(it.getLong(0))
            }
        }
        return ids
    }

    /**
     * Executes Full-Text Search against SQLite FTS4 table with snippet generation and relevance ranking.
     */
    fun searchFts(query: String, category: Category? = null): List<SearchResult> {
        val cleanQuery = sanitizeFtsQuery(query)
        if (cleanQuery.isBlank()) {
            val baseList = if (category != null) getByCategory(category) else getAllScreenshots()
            return baseList.map { SearchResult(it, snippet = "") }
        }

        val results = mutableListOf<SearchResult>()
        val sql = if (category != null) {
            """
            SELECT s.*, snippet($TABLE_FTS, '<b>', '</b>', '...', -1, 15) AS snippet_text
            FROM $TABLE_FTS fts
            JOIN $TABLE_SCREENSHOTS s ON s.$COL_ID = fts.docid
            WHERE fts MATCH ? AND s.$COL_CATEGORY = ?
            ORDER BY s.$COL_DATE_ADDED DESC
            LIMIT 100
            """.trimIndent()
        } else {
            """
            SELECT s.*, snippet($TABLE_FTS, '<b>', '</b>', '...', -1, 15) AS snippet_text
            FROM $TABLE_FTS fts
            JOIN $TABLE_SCREENSHOTS s ON s.$COL_ID = fts.docid
            WHERE fts MATCH ?
            ORDER BY s.$COL_DATE_ADDED DESC
            LIMIT 100
            """.trimIndent()
        }

        val args = if (category != null) {
            arrayOf(cleanQuery, category.name)
        } else {
            arrayOf(cleanQuery)
        }

        try {
            val cursor = readableDatabase.rawQuery(sql, args)
            cursor.use {
                val snippetIndex = it.getColumnIndex("snippet_text")
                while (it.moveToNext()) {
                    val screenshot = cursorToScreenshot(it)
                    val snippet = if (snippetIndex >= 0) it.getString(snippetIndex) ?: "" else ""
                    val rank = calculateRelevanceRank(screenshot, query)
                    results.add(SearchResult(screenshot, snippet, rank))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Resilient Fallback: If FTS returned 0 results or failed, search directly using SQL LIKE
        if (results.isEmpty() && query.isNotBlank()) {
            val fallbackSql = if (category != null) {
                """
                SELECT * FROM $TABLE_SCREENSHOTS
                WHERE ($COL_RAW_OCR LIKE ? OR $COL_REDACTED_OCR LIKE ? OR $COL_FILE_PATH LIKE ? OR $COL_EXTRACTED_DATA LIKE ?)
                  AND $COL_CATEGORY = ?
                ORDER BY $COL_DATE_ADDED DESC
                LIMIT 100
                """.trimIndent()
            } else {
                """
                SELECT * FROM $TABLE_SCREENSHOTS
                WHERE ($COL_RAW_OCR LIKE ? OR $COL_REDACTED_OCR LIKE ? OR $COL_FILE_PATH LIKE ? OR $COL_EXTRACTED_DATA LIKE ?)
                ORDER BY $COL_DATE_ADDED DESC
                LIMIT 100
                """.trimIndent()
            }
            val pattern = "%${query.trim()}%"
            val fallbackArgs = if (category != null) {
                arrayOf(pattern, pattern, pattern, pattern, category.name)
            } else {
                arrayOf(pattern, pattern, pattern, pattern)
            }
            try {
                val cursor = readableDatabase.rawQuery(fallbackSql, fallbackArgs)
                cursor.use {
                    while (it.moveToNext()) {
                        val screenshot = cursorToScreenshot(it)
                        val snippet = buildSnippetFallback(screenshot.rawOcrText, query)
                        val rank = calculateRelevanceRank(screenshot, query)
                        results.add(SearchResult(screenshot, snippet, rank))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return results
    }

    private fun buildSnippetFallback(text: String, query: String): String {
        if (text.isBlank() || query.isBlank()) return ""
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase().trim()
        val idx = lowerText.indexOf(lowerQuery)
        if (idx == -1) {
            val firstWord = lowerQuery.split(" ").firstOrNull() ?: return ""
            val wordIdx = lowerText.indexOf(firstWord)
            if (wordIdx == -1) return text.take(60)
            val start = (wordIdx - 20).coerceAtLeast(0)
            val end = (wordIdx + firstWord.length + 30).coerceAtMost(text.length)
            val snippet = text.substring(start, end).replace("\n", " ")
            return "...$snippet..."
        }
        val start = (idx - 25).coerceAtLeast(0)
        val end = (idx + query.length + 35).coerceAtMost(text.length)
        val matchPart = text.substring(idx, (idx + query.length).coerceAtMost(text.length))
        val beforePart = text.substring(start, idx).replace("\n", " ")
        val afterPart = text.substring((idx + query.length).coerceAtMost(text.length), end).replace("\n", " ")
        return "...$beforePart<b>$matchPart</b>$afterPart..."
    }

    private fun calculateRelevanceRank(screenshot: Screenshot, query: String): Double {
        val tokens = query.lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 0.0
        var score = 0.0
        val text = screenshot.rawOcrText.lowercase()
        val catName = screenshot.category.name.lowercase()
        for (token in tokens) {
            val clean = token.trimEnd('*')
            if (clean.isBlank()) continue
            if (catName.contains(clean)) score += 5.0
            var idx = 0
            while (true) {
                idx = text.indexOf(clean, idx)
                if (idx == -1) break
                score += 1.0
                idx += clean.length
            }
        }
        return -score
    }

    fun deleteScreenshot(id: Long): Boolean {
        return try {
            writableDatabase.delete(TABLE_SCREENSHOTS, "$COL_ID = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getCategoryCounts(): Map<Category, Int> {
        val map = mutableMapOf<Category, Int>()
        val sql = "SELECT $COL_CATEGORY, COUNT(*) FROM $TABLE_SCREENSHOTS GROUP BY $COL_CATEGORY"
        val cursor = readableDatabase.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                val catName = it.getString(0)
                val count = it.getInt(1)
                map[Category.fromString(catName)] = count
            }
        }
        return map
    }

    fun getTotalCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_SCREENSHOTS", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun sanitizeFtsQuery(query: String): String {
        // Strip non-alphanumeric characters that cause FTS parse errors
        val cleaned = query.trim().replace(Regex("""[^\w\s@.]"""), "")
        val tokens = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        // Append prefix query wildcard '*' to each token
        return tokens.joinToString(" ") { "$it*" }
    }

    private fun cursorToScreenshot(cursor: Cursor): Screenshot {
        return Screenshot(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            uriString = cursor.getString(cursor.getColumnIndexOrThrow(COL_URI_STRING)),
            filePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH)),
            dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DATE_ADDED)),
            width = cursor.getInt(cursor.getColumnIndexOrThrow(COL_WIDTH)),
            height = cursor.getInt(cursor.getColumnIndexOrThrow(COL_HEIGHT)),
            fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE)),
            rawOcrText = cursor.getString(cursor.getColumnIndexOrThrow(COL_RAW_OCR)),
            redactedOcrText = cursor.getString(cursor.getColumnIndexOrThrow(COL_REDACTED_OCR)),
            category = Category.fromString(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY))),
            visualHash = cursor.getLong(cursor.getColumnIndexOrThrow(COL_VISUAL_HASH)),
            extractedDataJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_EXTRACTED_DATA)),
            processedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PROCESSED_AT))
        )
    }

    /** Returns screenshots filtered by file size range (bytes). */
    fun getByFileSizeRange(minBytes: Long, maxBytes: Long): List<Screenshot> {
        val list = mutableListOf<Screenshot>()
        val cursor = readableDatabase.query(
            TABLE_SCREENSHOTS, null,
            "$COL_FILE_SIZE BETWEEN ? AND ?",
            arrayOf(minBytes.toString(), maxBytes.toString()),
            null, null, "$COL_DATE_ADDED DESC"
        )
        cursor.use { while (it.moveToNext()) list.add(cursorToScreenshot(it)) }
        return list
    }

    /** Searches screenshots by file name / path — case-insensitive LIKE. */
    fun searchByFileName(nameQuery: String): List<Screenshot> {
        val list = mutableListOf<Screenshot>()
        val cursor = readableDatabase.query(
            TABLE_SCREENSHOTS, null,
            "$COL_FILE_PATH LIKE ?",
            arrayOf("%${nameQuery.trim()}%"),
            null, null, "$COL_DATE_ADDED DESC"
        )
        cursor.use { while (it.moveToNext()) list.add(cursorToScreenshot(it)) }
        return list
    }

    /** Returns screenshots sorted by file size descending (largest first). */
    fun getAllBySize(category: Category? = null): List<Screenshot> {
        val list = mutableListOf<Screenshot>()
        val where = if (category != null) "$COL_CATEGORY = ?" else null
        val args = if (category != null) arrayOf(category.name) else null
        val cursor = readableDatabase.query(
            TABLE_SCREENSHOTS, null, where, args,
            null, null, "$COL_FILE_SIZE DESC"
        )
        cursor.use { while (it.moveToNext()) list.add(cursorToScreenshot(it)) }
        return list
    }
}



