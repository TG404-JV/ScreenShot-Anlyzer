package com.littech.screenintel.core.classifier.rules

/**
 * Precompiles word-boundary regex patterns for a list of keywords to avoid
 * catastrophic substring collisions (e.g., 'cola' matching 'ola', 'syntax' matching 'tax')
 * and eliminates runtime regex compilation overhead.
 *
 * Uses a unified alternation fast-exit filter so non-matching rules bail out in O(1)
 * regex search without evaluating individual sub-patterns.
 */
class KeywordMatcher(keywords: List<String>) {

    private val patterns: List<Regex> = keywords.map { kw ->
        val prefix = if (kw.first().isLetterOrDigit()) """(?<![a-zA-Z0-9])""" else ""
        val suffix = if (kw.last().isLetterOrDigit()) """(?![a-zA-Z0-9])""" else ""
        Regex("""(?i)$prefix${Regex.escape(kw)}$suffix""")
    }

    // Unified alternation pattern for instant O(1) fast-exit rejection and anyMatch
    private val unifiedPattern: Regex? = if (keywords.isNotEmpty()) {
        val parts = keywords.map { kw ->
            val prefix = if (kw.first().isLetterOrDigit()) """(?<![a-zA-Z0-9])""" else ""
            val suffix = if (kw.last().isLetterOrDigit()) """(?![a-zA-Z0-9])""" else ""
            """$prefix${Regex.escape(kw)}$suffix"""
        }
        Regex("""(?i)(?:${parts.joinToString("|")})""")
    } else null

    fun countMatches(text: String): Int {
        val unified = unifiedPattern ?: return 0
        // Fast-path: If the unified pattern does not match, zero keywords match.
        // Bypasses running N individual regex scans over the entire text.
        if (!unified.containsMatchIn(text)) {
            return 0
        }
        return patterns.count { it.containsMatchIn(text) }
    }

    fun anyMatch(text: String): Boolean {
        return unifiedPattern?.containsMatchIn(text) == true
    }
}
