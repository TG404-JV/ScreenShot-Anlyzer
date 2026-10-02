package com.littech.screenintel.core.classifier

import com.littech.screenintel.core.classifier.rules.RuleResult
import com.littech.screenintel.model.Category

data class ExtractedEntities(
    val upiIds: List<String> = emptyList(),
    val amounts: List<String> = emptyList(),
    val pnrNumbers: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val otps: List<String> = emptyList(),
    val emails: List<String> = emptyList()
) {
    fun toJson(): String {
        val parts = mutableListOf<String>()
        fun addArray(key: String, list: List<String>) {
            if (list.isNotEmpty()) {
                val arrayStr = list.joinToString(prefix = "[", postfix = "]") { quote(it) }
                parts.add("\"$key\":$arrayStr")
            }
        }
        addArray("upiIds", upiIds)
        addArray("amounts", amounts)
        addArray("pnrNumbers", pnrNumbers)
        addArray("urls", urls)
        addArray("phoneNumbers", phoneNumbers)
        addArray("otps", otps)
        addArray("emails", emails)
        return "{" + parts.joinToString(",") + "}"
    }

    companion object {
        private fun quote(s: String): String {
            val escaped = s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return "\"$escaped\""
        }

        private fun unquote(s: String): String {
            return s
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        private fun parseField(json: String, key: String): List<String> {
            val arrayPattern = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)\\]")
            val arrayMatch = arrayPattern.find(json)
            if (arrayMatch != null) {
                val content = arrayMatch.groupValues[1]
                val itemPattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
                return itemPattern.findAll(content)
                    .map { unquote(it.groupValues[1]).trim() }
                    .filter { it.isNotBlank() }
                    .toList()
            }
            // Backward compatibility: legacy string format "key": "val1,val2"
            val stringPattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            val stringMatch = stringPattern.find(json)
            if (stringMatch != null) {
                val content = unquote(stringMatch.groupValues[1])
                return content.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            return emptyList()
        }

        fun fromJson(jsonStr: String): ExtractedEntities {
            return try {
                if (jsonStr.isBlank()) return ExtractedEntities()
                ExtractedEntities(
                    upiIds = parseField(jsonStr, "upiIds"),
                    amounts = parseField(jsonStr, "amounts"),
                    pnrNumbers = parseField(jsonStr, "pnrNumbers"),
                    urls = parseField(jsonStr, "urls"),
                    phoneNumbers = parseField(jsonStr, "phoneNumbers"),
                    otps = parseField(jsonStr, "otps"),
                    emails = parseField(jsonStr, "emails")
                )
            } catch (e: Exception) {
                ExtractedEntities()
            }
        }
    }
}

data class ClassificationResult(
    val category: Category,
    val entities: ExtractedEntities
)

/**
 * High-performance, on-device OCR entity extraction and screenshot category classifier.
 *
 * Delegates classification to a modular [CategoryRuleEngine], preserving full backward
 * compatibility for all callers while exposing fine-grained diagnostic and tuning hooks.
 */
object CategoryClassifier {

    private val defaultEngine = CategoryRuleEngine()

    // --- UPI IDs: must match known Indian payment VPAs, NOT generic corporate/personal email addresses ---
    private val UPI_PSP_PATTERN = Regex(
        """(?i)\b[a-zA-Z0-9.\-_+]{2,64}@(""" +
        """upi|okaxis|okhdfcbank|okicici|oksbi|ybl|ibl|axl|axisbank|hdfcbank|sbi|icici|""" +
        """kotak|rbl|indus|pnb|barodampay|allbank|cnrb|ubi|""" +
        """paytm|paytmbank|freecharge|fbl|""" +
        """apl|superyes|""" +
        """gpay|phonepe|bhim|""" +
        """imobile|""" +
        """naviaxis|myaxis|""" +
        """aubank|""" +
        """bandhan|sliceaxis""" +
        """)\b(?!\.[a-zA-Z])"""
    )

    // Generic email regex (everything else — Gmail, corporate, etc.)
    private val EMAIL_REGEX = Regex(
        """(?i)\b[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}\b"""
    )

    private val AMOUNT_REGEX = Regex(
        """(?:[₹$€£]\s*|(?<!\w)INR\s*|(?<!\w)Rs\.?\s*)([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // PNR: Airline 6-char PNR (IATA), IRCTC 10-digit PNR, or Booking Reference (6-12 alphanumeric chars)
    private val PNR_REGEX = Regex(
        """(?i)\b(?:PNR|Booking\s+(?:Ref(?:erence)?|ID|No)|Ticket\s+(?:No\.?|Number))(?:\s+(?:No\.?|Number))?\s*[:#\s\-]\s*([A-Z0-9]{6,12})\b"""
    )

    private val URL_REGEX = Regex("""https?://[^\s/$.?#][^\s]*""", RegexOption.IGNORE_CASE)

    // Indian phone numbers: +91 prefix, trunk 0, or 10-digit starting with 6-9 (supports 5-5 digit grouping with space/dash)
    private val PHONE_REGEX = Regex(
        """(?:(?:\+91|91|0)[.\-\s]?)?(?<![0-9])([6-9]\d{4})[.\-\s]?(\d{5})(?![0-9])"""
    )

    // OTP: forward and reversed patterns with 4-8 digits
    private val OTP_FORWARD_REGEX = Regex(
        """(?i)\b(?:OTP|one[- ]time[- ](?:password|passcode|verification[- ]code)|verification[- ]code|auth(?:entication)?[- ]code|2FA[- ]code|security[- ]code|temporary[- ]password)\b[^0-9\n]{0,25}?(\d{4,8})\b"""
    )

    private val OTP_REVERSED_REGEX = Regex(
        """(?i)\b(\d{4,8})\b[^0-9\n]{1,30}?\b(?:is\s+(?:your|the)?\s*)?(?:OTP|one[- ]time[- ](?:password|passcode|verification[- ]code)|verification[- ]code|auth(?:entication)?[- ]code|2FA[- ]code|security[- ]code|temporary[- ]password)\b"""
    )

    private val COMMON_YEARS = (1990..2035).map { it.toString() }.toSet()

    // ─── Entity Extraction ────────────────────────────────────────────────────

    fun extractEntities(text: String): ExtractedEntities {
        val clean = OcrTextNormalizer.normalize(text)
        return extractEntitiesFromCleanText(clean)
    }

    /**
     * Unified, zero-redundancy extraction and classification pass.
     * Normalizes text once and runs both operations without redundant passes.
     */
    fun extractAndClassify(text: String, config: ClassifierConfig? = null): ClassificationResult {
        val clean = OcrTextNormalizer.normalize(text)
        val entities = extractEntitiesFromCleanText(clean)
        val category = if (config != null) {
            CategoryRuleEngine(config).classify(clean, entities)
        } else {
            defaultEngine.classify(clean, entities)
        }
        return ClassificationResult(category, entities)
    }

    private fun extractEntitiesFromCleanText(clean: String): ExtractedEntities {
        // Extract genuine UPI VPAs only (corporate emails excluded by negative lookahead)
        val upis = UPI_PSP_PATTERN.findAll(clean)
            .map { it.value.trim() }
            .filter { it.length <= 80 }
            .distinct().take(5).toList()

        val upiSet = upis.map { it.lowercase() }.toSet()

        // Extract all emails (excluding those that are valid UPI VPAs)
        val emails = EMAIL_REGEX.findAll(clean)
            .map { it.value.trim() }
            .filter { it.lowercase() !in upiSet && !UPI_PSP_PATTERN.matches(it) }
            .distinct().take(5).toList()

        val amounts = AMOUNT_REGEX.findAll(clean)
            .map { it.value.trim() }
            .distinct().take(5).toList()

        val pnrs = PNR_REGEX.findAll(clean)
            .map { it.groupValues[1] }
            .distinct().take(3).toList()

        val urls = URL_REGEX.findAll(clean)
            .map { it.value }.distinct().take(5).toList()

        val phones = PHONE_REGEX.findAll(clean)
            .map { it.groupValues[1] + it.groupValues[2] }
            .filter { it.length == 10 }
            .distinct().take(5).toList()

        // Forward and reversed OTP extraction
        val forwardOtps = OTP_FORWARD_REGEX.findAll(clean).map { it.groupValues[1] }
        val reversedOtps = OTP_REVERSED_REGEX.findAll(clean).map { it.groupValues[1] }
        val otps = (forwardOtps + reversedOtps)
            .filter { it.length in 4..8 && it !in COMMON_YEARS }
            .distinct().take(3).toList()

        return ExtractedEntities(
            upiIds = upis,
            amounts = amounts,
            pnrNumbers = pnrs,
            urls = urls,
            phoneNumbers = phones,
            otps = otps,
            emails = emails
        )
    }

    /**
     * Categorizes OCR text using the default rule engine.
     */
    fun classify(text: String, entities: ExtractedEntities): Category {
        val clean = OcrTextNormalizer.normalize(text)
        return defaultEngine.classify(clean, entities)
    }

    /**
     * Categorizes OCR text with custom tuning weights and thresholds.
     */
    fun classify(text: String, entities: ExtractedEntities, config: ClassifierConfig): Category {
        val clean = OcrTextNormalizer.normalize(text)
        return CategoryRuleEngine(config).classify(clean, entities)
    }

    /**
     * Evaluates all rules and returns diagnostic reasons and scores for debugging.
     */
    fun evaluateDetailed(text: String, entities: ExtractedEntities): List<RuleResult> {
        val clean = OcrTextNormalizer.normalize(text)
        return defaultEngine.evaluateDetailed(clean, entities)
    }
}
