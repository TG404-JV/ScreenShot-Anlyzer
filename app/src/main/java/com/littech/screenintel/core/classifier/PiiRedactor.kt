package com.littech.screenintel.core.classifier

/**
 * High-performance on-device PII sanitizer for OCR text.
 * Redacts financial and identity identifiers (Cards, CVVs, OTPs, Aadhaar, PAN, Bank Accounts)
 * before text is written to caches, UI preview cards, clipboard sharing, or FTS snippets.
 */
object PiiRedactor {

    // Credit/Debit Card PAN: 13-19 digits formatted (spaces, hyphens, dots) or 16 continuous digits
    // Supports 4-4-4-4 (Visa/Mastercard/RuPay), 4-6-5 (AmEx)
    private val CARD_FORMATTED_REGEX = Regex("""\b(?:\d{4}[ \-.]){3}\d{1,4}\b|\b\d{4}[ \-.]\d{6}[ \-.]\d{5}\b""")
    private val CARD_RAW_16_REGEX = Regex("""\b\d{16}\b""")

    // CVV / CVC / Security Code
    private val CVV_REGEX = Regex("""(?i)\b(cvv2?|cvc2?|cid|security\s+code)\b(?:\s+is|\s*[:=-])?\s*([0-9]{3,4})\b""")

    // Forward OTP: keyword followed by 4-8 digits (reluctant matching prevents ReDoS)
    private val OTP_FORWARD_REGEX = Regex(
        """(?i)\b(otp|one[- ]time[- ](?:password|passcode|verification[- ]code)|verification[- ]code|auth(?:entication)?[- ]code|2FA[- ]code|temporary[- ]password)\b[^0-9\n]{0,25}?([0-9]{4,8})\b"""
    )

    // Reversed OTP: 4-8 digits followed by "is your OTP / verification code" (reluctant matching prevents ReDoS)
    private val OTP_REVERSED_REGEX = Regex(
        """(?i)\b([0-9]{4,8})\b([^0-9\n]{1,30}?\b(?:is\s+(?:your|the)?\s*)?(?:otp|one[- ]time[- ](?:password|passcode|verification[- ]code)|verification[- ]code|auth(?:entication)?[- ]code|2FA[- ]code|temporary[- ]password)\b)"""
    )

    // Aadhaar / 12-digit Indian National ID: 4-4-4 format or labeled 12 continuous digits
    private val AADHAAR_FORMATTED_REGEX = Regex("""\b(\d{4})[\s-](\d{4})[\s-](\d{4})\b""")
    private val AADHAAR_LABELED_REGEX = Regex("""(?i)\b(aadhaar|uidai)\b[\s:=-]*(\d{12})\b""")

    // Indian PAN: 5 letters + 4 digits + 1 letter (case-insensitive)
    private val PAN_REGEX = Regex("""(?i)\b([A-Z]{5})([0-9]{4})([A-Z])\b""")

    // Bank Account Number: 9 to 18 digits preceded by account identifier (excludes bare "ac" to prevent "AC bus" collisions)
    private val BANK_ACCOUNT_REGEX = Regex(
        """(?i)\b(a/c|account|acct)\s*(?:no\.?|number|#)?\s*[:=-]?\s*([0-9]{9,18})\b"""
    )

    /**
     * Sanitizes raw OCR text in-memory before writing to caches, previews, or FTS snippets.
     */
    fun redact(text: String): String {
        if (text.isBlank()) return text

        // Fast-path: All PII targets require digits; bail out instantly if none exist
        if (!text.any { it.isDigit() }) return text

        var result = text

        // 1. Mask formatted credit/debit card numbers based on 4-4-4-4 or 4-6-5 structure
        result = CARD_FORMATTED_REGEX.replace(result) { match ->
            val clean = match.value.replace(" ", "").replace("-", "").replace(".", "")
            if (clean.length in 13..19) {
                val last4 = clean.takeLast(4)
                "•••• •••• •••• $last4"
            } else {
                match.value
            }
        }

        // 2. Mask raw continuous 16 digits only if they pass Luhn (protects Amazon/Flipkart tracking numbers)
        result = CARD_RAW_16_REGEX.replace(result) { match ->
            val num = match.value
            if (isValidLuhn(num)) {
                val last4 = num.takeLast(4)
                "•••• •••• •••• $last4"
            } else {
                match.value
            }
        }

        // 3. Mask CVVs
        result = CVV_REGEX.replace(result) { match ->
            val label = match.groupValues[1]
            "$label: •••"
        }

        // 4. Mask Forward OTPs
        result = OTP_FORWARD_REGEX.replace(result) { match ->
            val label = match.groupValues[1]
            "$label: ••••••"
        }

        // 5. Mask Reversed OTPs
        result = OTP_REVERSED_REGEX.replace(result) { match ->
            val suffix = match.groupValues[2]
            "••••••$suffix"
        }

        // 6. Mask Aadhaar numbers keeping last 4 digits
        result = AADHAAR_FORMATTED_REGEX.replace(result) { match ->
            val last4 = match.groupValues[3]
            "•••• •••• $last4"
        }
        result = AADHAAR_LABELED_REGEX.replace(result) { match ->
            val label = match.groupValues[1]
            val last4 = match.groupValues[2].takeLast(4)
            "$label: •••• •••• $last4"
        }

        // 7. Mask Indian PAN numbers
        result = PAN_REGEX.replace(result) { match ->
            val prefix = match.groupValues[1].take(3).uppercase()
            val suffix = (match.groupValues[2].takeLast(1) + match.groupValues[3]).uppercase()
            "$prefix•••••$suffix"
        }

        // 8. Mask Bank Account numbers keeping last 4 digits
        result = BANK_ACCOUNT_REGEX.replace(result) { match ->
            val label = match.groupValues[1]
            val acc = match.groupValues[2]
            val last4 = acc.takeLast(4)
            val maskedPrefix = "•".repeat((acc.length - 4).coerceAtLeast(4))
            "$label: $maskedPrefix$last4"
        }

        return result
    }

    /**
     * Standard Luhn checksum algorithm for credit/debit card number validation.
     */
    fun isValidLuhn(number: String): Boolean {
        if (number.length !in 13..19) return false
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            val c = number[i]
            if (c !in '0'..'9') return false
            var n = c - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }
}
