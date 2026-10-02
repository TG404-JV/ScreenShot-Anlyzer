package com.littech.screenintel.model

data class Screenshot(
    val id: Long,
    val uriString: String,
    val filePath: String,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val rawOcrText: String,
    val redactedOcrText: String,
    val category: Category,
    val visualHash: Long,
    val extractedDataJson: String = "{}",
    val processedAt: Long
)

data class SearchResult(
    val screenshot: Screenshot,
    val snippet: String,
    val rank: Double = 0.0
)

data class DuplicateGroup(
    val primary: Screenshot,
    val duplicates: List<Screenshot>,
    val maxHammingDistance: Int
)

