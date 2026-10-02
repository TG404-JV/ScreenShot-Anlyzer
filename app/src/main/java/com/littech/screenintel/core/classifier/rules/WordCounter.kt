package com.littech.screenintel.core.classifier.rules

/**
 * High-performance, zero-allocation word counter for OCR text analysis.
 * Replaces expensive Regex split operations (which allocate arrays of thousands of strings)
 * with a single linear O(N) primitive character scan.
 */
object WordCounter {

    /**
     * Counts the number of words that contain at least one letter and have length >= [minLength].
     * Allocates zero memory on the heap.
     */
    fun countMeaningfulWords(text: CharSequence, minLength: Int = 3): Int {
        var count = 0
        var currentWordLen = 0
        var hasLetter = false

        for (i in 0 until text.length) {
            val c = text[i]
            if (c.isWhitespace()) {
                if (currentWordLen >= minLength && hasLetter) {
                    count++
                }
                currentWordLen = 0
                hasLetter = false
            } else {
                currentWordLen++
                if (c.isLetter()) {
                    hasLetter = true
                }
            }
        }

        if (currentWordLen >= minLength && hasLetter) {
            count++
        }

        return count
    }
}
