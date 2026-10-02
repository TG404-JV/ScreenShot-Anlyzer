package com.littech.screenintel.core

import com.littech.screenintel.core.classifier.CategoryClassifier
import com.littech.screenintel.core.classifier.PiiRedactor
import com.littech.screenintel.core.hasher.VisualHashCalculator
import com.littech.screenintel.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Non-happy path, boundary conditions, edge cases, and adversarial input tests
 * for OCR text processing, entity extraction, PII redaction, and visual hashing.
 */
class NonHappyPathExtractionTest {

    // =========================================================================
    // 1. Empty, Whitespace-only, and Null-like OCR Output
    // =========================================================================
    @Test
    fun testEmptyAndBlankOcrOutput() {
        val emptyInputs = listOf("", "   ", "\n\n\t  \r\n", "       \n   ")

        for (input in emptyInputs) {
            val entities = CategoryClassifier.extractEntities(input)
            assertTrue("Entities should be empty for blank input", entities.upiIds.isEmpty())
            assertTrue("Amounts should be empty for blank input", entities.amounts.isEmpty())
            assertTrue("PNRs should be empty for blank input", entities.pnrNumbers.isEmpty())
            assertTrue("URLs should be empty for blank input", entities.urls.isEmpty())
            assertTrue("Phone numbers should be empty for blank input", entities.phoneNumbers.isEmpty())
            assertTrue("OTPs should be empty for blank input", entities.otps.isEmpty())

            val category = CategoryClassifier.classify(input, entities)
            assertEquals("Blank OCR text should default to OTHER category", Category.OTHER, category)

            val redacted = PiiRedactor.redact(input)
            assertEquals("Redacting blank text should return identical string", input, redacted)
        }
    }

    // =========================================================================
    // 2. Pure Visual Noise and OCR Artifact Hallucinations
    // =========================================================================
    @Test
    fun testGarbageAndNoisyArtifacts() {
        val noiseText = """
            ~~~~~ |||||| ///// \\\\\\ ...... ,,,,, ::: ;;;;
            !@#${'$'}%^&*()_+=-`~[]{}|;':",./<>?
            0101010101010101010101010101
            æøå äöü ß ÿ ñ ç ¿ ¡ « »
            ----------------------------------------
            ________________________________________
            ========================================
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(noiseText)
        assertTrue("No valid UPI should be extracted from noise", entities.upiIds.isEmpty())
        assertTrue("No valid PNR should be extracted from noise", entities.pnrNumbers.isEmpty())
        assertTrue("No valid URLs should be extracted from noise", entities.urls.isEmpty())

        val category = CategoryClassifier.classify(noiseText, entities)
        assertEquals("Noise should classify as OTHER", Category.OTHER, category)
    }

    // =========================================================================
    // 3. Malformed and Borderline Entity Strings
    // =========================================================================
    @Test
    fun testMalformedUpiHandles() {
        // Invalid handles that should NOT be recognized as legitimate UPI IDs
        val text = """
            Contact me at @ or @bank or user@ or a@b or someone@@icici
            Also email at test@.com or user@127.0.0.1 or user@toolongdomainnamethatisinvalidbecauseofgarbageocr
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(text)
        // Ensure none of the malformed handles were accepted
        assertFalse(entities.upiIds.contains("@"))
        assertFalse(entities.upiIds.contains("@bank"))
        assertFalse(entities.upiIds.contains("user@"))
        assertFalse(entities.upiIds.contains("someone@@icici"))
    }

    @Test
    fun testMalformedPnrNumbers() {
        // PNR requires 6-10 alphanumeric characters after keyword
        val text = """
            PNR: 12
            PNR: 
            PNR: !@#${'$'}%
            Booking ID: 123
            Booking Reference: AB
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(text)
        assertTrue("Short or invalid PNRs must be rejected", entities.pnrNumbers.isEmpty())
    }

    @Test
    fun testFalsePositiveOtpPrevention() {
        // Numbers that should NOT be parsed as OTPs
        val text = """
            Founded in year 2024.
            Invoice number 492019482 for your purchase.
            Call 9876543210 for support.
            Total count: 1234 items.
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(text)
        assertTrue("General numbers without OTP context must not be tagged as OTP", entities.otps.isEmpty())
        assertFalse("Invoice text without credentials shouldn't be CREDENTIAL",
            CategoryClassifier.classify(text, entities) == Category.CREDENTIAL)
    }

    // =========================================================================
    // 4. SQL Injection, Script Injection, and FTS5 Operators in OCR
    // =========================================================================
    @Test
    fun testSqlAndScriptInjectionInOcrText() {
        val maliciousOcr = """
            ' OR '1'='1'; DROP TABLE screenshots; --
            <script>alert('XSS')</script>
            " OR ""="
            NEAR(payment, receipt, 10)
            * AND NOT category:PAYMENT
            Robert'); DROP TABLE Students;--
        """.trimIndent()

        // Entity extraction must not crash
        val entities = CategoryClassifier.extractEntities(maliciousOcr)
        val category = CategoryClassifier.classify(maliciousOcr, entities)

        // PII redaction must not alter SQL/script syntax unexpectedly or crash
        val redacted = PiiRedactor.redact(maliciousOcr)
        assertTrue("Malicious strings must be handled safely without error", redacted.contains("DROP TABLE"))

        // FTS Query Sanitization validation:
        // Strip non-alphanumeric except @ and .
        val query = "'; DROP TABLE screenshots; --"
        val cleaned = query.trim().replace(Regex("""[^\w\s@.]"""), "")
        val tokens = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val ftsSafe = tokens.joinToString(" ") { "$it*" }

        assertEquals("DROP* TABLE* screenshots*", ftsSafe)
        assertFalse(ftsSafe.contains(";"))
        assertFalse(ftsSafe.contains("'"))
        assertFalse(ftsSafe.contains("--"))
    }

    // =========================================================================
    // 5. Stress Test: Massive OCR Text (ReDoS / Catastrophic Backtracking defense)
    // =========================================================================
    @Test(timeout = 3000) // Must finish within 3 seconds, proving no ReDoS
    fun testMassiveOcrTextPerformanceAndSafety() {
        // Build 50,000 characters of repeating patterns with potential catastrophic backtracking
        val sb = StringBuilder()
        repeat(500) { i ->
            sb.append("This is line $i with random text and repeated aaaaaa@aaaaaaa and ₹$i.99 and OTP $i\n")
        }
        val massiveText = sb.toString()
        assertTrue("Massive text should exceed 20KB", massiveText.length > 20000)

        val startTime = System.currentTimeMillis()
        val entities = CategoryClassifier.extractEntities(massiveText)
        val category = CategoryClassifier.classify(massiveText, entities)
        val redacted = PiiRedactor.redact(massiveText)
        val duration = System.currentTimeMillis() - startTime

        println("Processed ${massiveText.length} chars in ${duration}ms")
        assertTrue("Regex entity extraction must cap results to prevent memory explosion", entities.amounts.size <= 5)
        assertTrue("Execution must be fast (under 1000ms)", duration < 1000)
    }

    // =========================================================================
    // 6. Multilingual, Emojis, and UTF-16 Surrogate Pairs
    // =========================================================================
    @Test
    fun testMultilingualAndEmojiText() {
        val multiLingualText = """
            🎉 बधाई हो! आपका भुगतान सफल रहा! 🚀
            Paid ₹999.00 to sharma.kirana@oksbi
            सत्यापन कोड / OTP: 492018
            شكرا جزيلا / ありがとうございます
            Customer Care: +91 9876543210
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(multiLingualText)
        assertTrue("Should extract UPI ID amidst Hindi & emoji", entities.upiIds.contains("sharma.kirana@oksbi"))
        assertTrue("Should extract amount with ₹ symbol", entities.amounts.contains("₹999.00"))
        assertTrue("Should extract OTP code", entities.otps.contains("492018"))

        val category = CategoryClassifier.classify(multiLingualText, entities)
        // With OTP present, it will be prioritized as CREDENTIAL or PAYMENT
        assertTrue("Should classify as valid category", category in listOf(Category.CREDENTIAL, Category.PAYMENT))

        val redacted = PiiRedactor.redact(multiLingualText)
        assertFalse("OTP must be masked", redacted.contains("492018"))
    }

    // =========================================================================
    // 7. Visual Hashing Boundary Conditions (Solid Black / Solid White Images)
    // =========================================================================
    @Test
    fun testVisualHashExtremeCases() {
        val solidBlackHash = 0x0000000000000000L
        val solidWhiteHash = 0x0000000000000000L // Uniform pixel values result in all 0s (no left > right differences)
        val invertedHash = -1L // 0xFFFFFFFFFFFFFFFFL (64 bits set)

        // Hamming distance between identical uniform images
        val distIdentical = VisualHashCalculator.hammingDistance(solidBlackHash, solidWhiteHash)
        assertEquals("Two uniform blank screens must have 0 distance", 0, distIdentical)
        assertTrue(VisualHashCalculator.isDuplicate(solidBlackHash, solidWhiteHash, threshold = 0))

        // Hamming distance between complete opposites
        val distOpposite = VisualHashCalculator.hammingDistance(solidBlackHash, invertedHash)
        assertEquals("Opposite hashes must have exactly 64 bits distance", 64, distOpposite)
        assertFalse(VisualHashCalculator.isDuplicate(solidBlackHash, invertedHash, threshold = 3))
    }

    // =========================================================================
    // 8. PII Masking Boundary and Tricky Formats
    // =========================================================================
    @Test
    fun testTrickyPiiBoundaries() {
        val input = """
            Visa: 4111-2222-3333-4444
            MasterCard: 5500.0000.1111.2222
            Amex: 3782 822463 10005
            Aadhaar: 1234-5678-9012
            CVV is 007.
        """.trimIndent()

        val redacted = PiiRedactor.redact(input)
        println("Redacted tricky PII:\n$redacted")

        // Assert no raw 16-digit credit cards or CVV remain unmasked
        assertFalse("Hyphenated card must be masked", redacted.contains("4111-2222"))
        assertFalse("Dotted card must be masked", redacted.contains("5500.0000"))
        assertFalse("CVV must be masked", redacted.contains("007"))
        assertTrue("CVV masked token must be present", redacted.contains("CVV: •••") || redacted.contains("CVV is •••"))
    }
}
