package com.littech.screenintel.core.classifier

/**
 * Preprocesses and normalizes raw OCR text from ML Kit Text Recognition before
 * entity extraction, PII redaction, and category classification.
 *
 * Repairs common OCR misreads and anomalies:
 * - Strips zero-width characters and invisible directional marks
 * - Normalizes non-standard Unicode whitespace (NBSP, em-space, etc.) to standard ASCII space
 * - Repairs currency symbol OCR corruptions (e.g., '|NR', 'lNR', '1NR' -> 'INR', 'R s .' -> 'Rs.')
 * - Collapses excessive horizontal whitespace while preserving line structure
 */
object OcrTextNormalizer {

    // Zero-width characters & invisible Unicode marks
    private val ZERO_WIDTH_CHARS = Regex("""[\u200B-\u200D\uFEFF\u200E\u200F]""")

    // Non-breaking and diverse Unicode space variants
    private val UNICODE_SPACES = Regex("""[\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]""")

    // Common ML Kit misreads for "INR"
    private val INR_CORRUPTIONS = Regex("""(?i)(?<!\w)(?:\|NR|lNR|1NR)(?!\w)""")

    // Spaced "R s ." or "R s"
    private val RS_SPACED = Regex("""(?i)(?<!\w)R\s*s\s*\.(?!\w)""")

    // Excessive horizontal whitespace (tabs/spaces)
    private val EXCESSIVE_SPACES = Regex("""[ \t]{2,}""")

    /**
     * Cleans and normalizes OCR text with high-performance fast-path scanning.
     * Avoids regex compilation and intermediate string allocations for clean strings.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return text

        if (!needsNormalization(text)) {
            return text
        }

        return text
            // 1. Strip invisible zero-width characters
            .replace(ZERO_WIDTH_CHARS, "")
            // 2. Convert all Unicode whitespace variants to standard ASCII space
            .replace(UNICODE_SPACES, " ")
            // 3. Fix OCR misreads for currency prefixes
            .replace(INR_CORRUPTIONS, "INR")
            .replace(RS_SPACED, "Rs.")
            // 4. Collapse multiple horizontal spaces
            .replace(EXCESSIVE_SPACES, " ")
    }

    /**
     * Fast-path linear scan to check if normalization is required.
     * Evaluates in tens of nanoseconds and avoids memory allocation.
     */
    fun needsNormalization(text: String): Boolean {
        val len = text.length
        for (i in 0 until len) {
            val c = text[i]

            // 1. Zero-width & directional marks
            if (c in '\u200B'..'\u200D' || c == '\uFEFF' || c == '\u200E' || c == '\u200F') {
                return true
            }

            // 2. Non-breaking / exotic Unicode spaces
            if (c == '\u00A0' || c == '\u1680' || (c in '\u2000'..'\u200A') ||
                c == '\u202F' || c == '\u205F' || c == '\u3000') {
                return true
            }

            // 3. Consecutive spaces or tabs
            if (c == '\t') return true
            if (c == ' ' && i + 1 < len && (text[i + 1] == ' ' || text[i + 1] == '\t')) {
                return true
            }

            // 4. INR corruptions (|NR, lNR, 1NR)
            if (c == '|' || c == 'l' || c == '1') {
                if (i + 2 < len &&
                    (text[i + 1] == 'N' || text[i + 1] == 'n') &&
                    (text[i + 2] == 'R' || text[i + 2] == 'r')) {
                    return true
                }
            }

            // 5. Spaced "R s ."
            if ((c == 'R' || c == 'r') && i + 2 < len) {
                if (text[i + 1] == ' ' && (text[i + 2] == 's' || text[i + 2] == 'S')) {
                    return true
                }
            }
        }
        return false
    }
}
