package com.littech.screenintel.core.hasher

import android.graphics.Bitmap
import kotlin.math.roundToInt

object VisualHashCalculator {

    /**
     * Computes a 64-bit difference hash (dHash) for an image bitmap.
     * Downscales the image to 9x8 pixels in grayscale and compares adjacent horizontal pixels.
     * Returns a 64-bit Long hash.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val pixels = IntArray(72)
        scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        val hash = computeDHashFrom9x8Pixels(pixels)
        return hash
    }

    /**
     * Computes a 64-bit difference hash from a 9x8 (72 int) ARGB/RGB pixel array.
     */
    fun computeDHashFrom9x8Pixels(pixels: IntArray): Long {
        // Convert to grayscale luminance: 0.299*R + 0.587*G + 0.114*B
        val gray = IntArray(72)
        for (i in 0 until 72.coerceAtMost(pixels.size)) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
        }

        // 8 rows x 8 comparisons = 64 bits
        var hash = 0L
        for (row in 0 until 8) {
            val rowOffset = row * 9
            for (col in 0 until 8) {
                val left = gray[rowOffset + col]
                val right = gray[rowOffset + col + 1]
                if (left > right) {
                    hash = hash or (1L shl (row * 8 + col))
                }
            }
        }
        return hash
    }

    /**
     * Calculates the Hamming distance between two 64-bit dHashes.
     * Distance 0 means identical visual layout.
     * Distance <= 3 indicates near-duplicate screenshots (PRD §DD-13).
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Determines whether two visual hashes indicate duplicate or near-duplicate screenshots.
     */
    fun isDuplicate(hash1: Long, hash2: Long, threshold: Int = 3): Boolean {
        return hammingDistance(hash1, hash2) <= threshold
    }
}

