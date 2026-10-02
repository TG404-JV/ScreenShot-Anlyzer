package com.littech.screenintel.core.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextNormalizerTest {

    @Test
    fun testZeroWidthCharacterStripping() {
        val corruptedText = "UPI:\u200Bra\u200Dh\u200Cul@\uFEFFybl"
        val normalized = OcrTextNormalizer.normalize(corruptedText)

        assertEquals("UPI:rahul@ybl", normalized)
        val entities = CategoryClassifier.extractEntities(corruptedText)
        assertTrue("Zero-width corrupted UPI ID should be cleanly extracted", entities.upiIds.contains("rahul@ybl"))
    }

    @Test
    fun testUnicodeWhitespaceNormalization() {
        val nonBreakingSpaceText = "Paid\u00A0to\u00A0Merchant\u2003₹500.00"
        val normalized = OcrTextNormalizer.normalize(nonBreakingSpaceText)

        assertEquals("Paid to Merchant ₹500.00", normalized)
    }

    @Test
    fun testInrCurrencyOcrRepair() {
        val ocrWithPipeInr = "Amount debited: |NR 1,450.00 via GPay"
        val normalized = OcrTextNormalizer.normalize(ocrWithPipeInr)

        assertTrue(normalized.contains("INR 1,450.00"))

        val entities = CategoryClassifier.extractEntities(ocrWithPipeInr)
        assertTrue("Amount with |NR corruption should be extracted", entities.amounts.isNotEmpty())
    }

    @Test
    fun testSpacedRupeeRepair() {
        val text = "Total Bill: R s . 850.00"
        val normalized = OcrTextNormalizer.normalize(text)

        assertTrue(normalized.contains("Rs. 850.00"))

        val entities = CategoryClassifier.extractEntities(text)
        assertTrue("Amount with spaced Rs. should be extracted", entities.amounts.any { it.contains("850") })
    }

    @Test
    fun testSpacedIndianPhoneNumberExtraction() {
        val contactCard = """
            Customer Care Support
            Mobile: +91 98765 43210
            Alternate: 99887-76655
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(contactCard)
        assertEquals(2, entities.phoneNumbers.size)
        assertTrue(entities.phoneNumbers.contains("9876543210"))
        assertTrue(entities.phoneNumbers.contains("9988776655"))
    }
}
