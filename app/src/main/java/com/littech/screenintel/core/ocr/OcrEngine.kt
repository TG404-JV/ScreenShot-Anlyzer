package com.littech.screenintel.core.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrResult(
    val rawText: String,
    val lineCount: Int,
    val isSuccessful: Boolean,
    val errorMessage: String? = null,
    val unfilteredText: String = rawText
)

class MlKitOcrEngine {

    // On-device ML Kit text recognizer (100% offline, zero network)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val task = recognizer.process(inputImage)
            val visionText = Tasks.await(task)

            val lineGeometries = visionText.textBlocks.flatMap { block ->
                block.lines.map { line ->
                    val box = line.boundingBox
                    TextLineGeometry(
                        text = line.text,
                        top = box?.top ?: 0,
                        bottom = box?.bottom ?: 0,
                        left = box?.left ?: 0,
                        right = box?.right ?: 0
                    )
                }
            }

            val spatialResult = SpatialOcrFilter.filterLines(
                lines = lineGeometries,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )

            val fullUnfilteredText = visionText.text.trim()
            val cleanContentText = spatialResult.cleanText.ifBlank { fullUnfilteredText }
            val lineCount = spatialResult.filteredLines.size

            OcrResult(
                rawText = cleanContentText,
                lineCount = lineCount,
                isSuccessful = true,
                unfilteredText = fullUnfilteredText
            )
        } catch (e: Exception) {
            OcrResult(
                rawText = "",
                lineCount = 0,
                isSuccessful = false,
                errorMessage = e.message ?: "OCR processing failed",
                unfilteredText = ""
            )
        }
    }

    fun close() {
        recognizer.close()
    }
}
