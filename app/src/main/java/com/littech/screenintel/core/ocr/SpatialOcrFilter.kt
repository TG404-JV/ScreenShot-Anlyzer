package com.littech.screenintel.core.ocr

/**
 * Geometric representation of an OCR text line and its bounding coordinates.
 * Pure-Kotlin structure to allow unit testing on JVM without Android SDK stubs.
 */
data class TextLineGeometry(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int = 0,
    val right: Int = 0
)

/**
 * Configuration for spatial filtering of OCR text.
 *
 * @param topZoneRatio Fraction of total image height considered the status bar zone (default 5.5%).
 * @param bottomZoneRatio Fraction of total image height considered the navigation bar zone (default 6.5%).
 * @param minPhoneAspectRatio Minimum height/width ratio to qualify as a full-screen portrait screenshot.
 * @param filterStatusBarNoise Whether to filter status bar telemetry (time, battery, carrier, wifi).
 * @param filterNavBarNoise Whether to filter bottom navigation chrome and tab bars.
 */
data class SpatialFilterConfig(
    val topZoneRatio: Float = 0.055f,
    val bottomZoneRatio: Float = 0.065f,
    val minPhoneAspectRatio: Float = 1.5f,
    val filterStatusBarNoise: Boolean = true,
    val filterNavBarNoise: Boolean = true
)

data class SpatialFilterResult(
    val cleanText: String,
    val filteredLines: List<TextLineGeometry>,
    val removedLines: List<TextLineGeometry>
)

/**
 * Filters out system UI chrome (Status Bar and Navigation Bar) and in-app navigation
 * noise from OCR results using spatial coordinates and regex heuristics.
 */
object SpatialOcrFilter {

    private val STATUS_TIME_REGEX = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?(\s*(?:AM|PM|am|pm))?\b""", RegexOption.IGNORE_CASE)
    private val STATUS_BATTERY_REGEX = Regex("""\b\d{1,3}%\b""")
    private val STATUS_NETWORK_REGEX = Regex(
        """\b(?:5G|4G|3G|2G|LTE|VoLTE|VoWiFi|Wi-?Fi|WiFi|Kb/s|KB/s|Mb/s|MB/s|bps)\b""",
        RegexOption.IGNORE_CASE
    )
    private val STATUS_CARRIER_REGEX = Regex(
        """\b(?:Jio|Airtel|Vi\b|Vodafone|BSNL|Verizon|AT&T|T-Mobile|Sprint)\b""",
        RegexOption.IGNORE_CASE
    )
    private val STATUS_SYSTEM_REGEX = Regex(
        """\b(?:Do not disturb|Silent|Alarm|Bluetooth|NFC|Hotspot|Airplane mode)\b""",
        RegexOption.IGNORE_CASE
    )

    // System navigation text and common bottom-bar tab navigation buttons
    private val NAV_SYSTEM_REGEX = Regex("""^(?:Back|Home|Recent|Overview|Menu|—|_|\.)$""", RegexOption.IGNORE_CASE)
    private val BOTTOM_NAV_TABS_REGEX = Regex(
        """^(?:Home|Search|Explore|Feed|Reels|Profile|Account|Pay|Scan|Orders|Settings|Chat|Chats|Calls|Status|Updates)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Filters lines based on vertical screen coordinates and heuristic patterns.
     */
    fun filterLines(
        lines: List<TextLineGeometry>,
        imageWidth: Int,
        imageHeight: Int,
        config: SpatialFilterConfig = SpatialFilterConfig()
    ): SpatialFilterResult {
        if (lines.isEmpty() || imageHeight <= 0 || imageWidth <= 0) {
            val fullText = lines.joinToString("\n") { it.text }
            return SpatialFilterResult(fullText, lines, emptyList())
        }

        val aspectRatio = imageHeight.toFloat() / imageWidth.toFloat()
        val isPortraitPhoneScreenshot = aspectRatio >= config.minPhoneAspectRatio

        // For long scrolling screenshots (e.g. 6000px+ height), clamp cutoffs to standard phone chrome proportions
        // so content in the body of stitched screenshots is never accidentally classified as status/nav chrome.
        val maxTopCutoff = (imageWidth * 0.15f).toInt()
        val topCutoffY = minOf((imageHeight * config.topZoneRatio).toInt(), maxTopCutoff)

        val maxBottomChromeHeight = (imageWidth * 0.18f).toInt()
        val bottomCutoffY = maxOf((imageHeight * (1.0f - config.bottomZoneRatio)).toInt(), imageHeight - maxBottomChromeHeight)

        val acceptedLines = ArrayList<TextLineGeometry>(lines.size)
        val removedLines = ArrayList<TextLineGeometry>()

        for (line in lines) {
            val trimmed = line.text.trim()
            if (trimmed.isEmpty()) continue

            var shouldRemove = false

            // 1. Status Bar Zone Evaluation (Top)
            if (config.filterStatusBarNoise && line.top <= topCutoffY) {
                // If it's a full phone screenshot, lines in the top 5.5% that match status bar patterns are filtered
                val isStatusBarContent = isStatusBarText(trimmed)
                if (isStatusBarContent || (isPortraitPhoneScreenshot && line.bottom <= topCutoffY && isLikelyTopChrome(trimmed))) {
                    shouldRemove = true
                }
            }

            // 2. Navigation Bar Zone Evaluation (Bottom)
            if (!shouldRemove && config.filterNavBarNoise && line.bottom >= bottomCutoffY) {
                val isNavBarContent = isNavBarText(trimmed)
                if (isNavBarContent || (isPortraitPhoneScreenshot && line.top >= bottomCutoffY && isLikelyBottomChrome(trimmed))) {
                    shouldRemove = true
                }
            }

            if (shouldRemove) {
                removedLines.add(line)
            } else {
                acceptedLines.add(line)
            }
        }

        val cleanText = acceptedLines.joinToString("\n") { it.text }
        return SpatialFilterResult(cleanText, acceptedLines, removedLines)
    }

    private fun isStatusBarText(text: String): Boolean {
        return STATUS_TIME_REGEX.containsMatchIn(text) ||
                STATUS_BATTERY_REGEX.containsMatchIn(text) ||
                STATUS_NETWORK_REGEX.containsMatchIn(text) ||
                STATUS_CARRIER_REGEX.containsMatchIn(text) ||
                STATUS_SYSTEM_REGEX.containsMatchIn(text)
    }

    private fun isLikelyTopChrome(text: String): Boolean {
        // Pure short numbers, network glyphs or battery indicators
        return text.length <= 15 && (text.contains("%") || text.contains(":") || text.any { it.isDigit() })
    }

    private fun isNavBarText(text: String): Boolean {
        val trimmed = text.trim()
        return NAV_SYSTEM_REGEX.matches(trimmed) || BOTTOM_NAV_TABS_REGEX.matches(trimmed)
    }

    private fun isLikelyBottomChrome(text: String): Boolean {
        val words = text.trim().split(Regex("""\s+"""))
        // Bottom bar usually has 1-3 short single words (tab items)
        return words.size <= 2 && words.all { it.length <= 10 && BOTTOM_NAV_TABS_REGEX.matches(it) }
    }
}
