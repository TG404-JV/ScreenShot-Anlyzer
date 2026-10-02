package com.littech.screenintel.core

import com.littech.screenintel.core.classifier.CategoryClassifier
import com.littech.screenintel.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun testPaymentClassificationAndEntityExtraction() {
        val ocrText = """
            Google Pay
            Paid to Cafe Bistro
            ₹850.00
            UPI Ref: 482910394819
            To: cafe.bistro@okicici
            Payment Successful
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        assertTrue("UPI ID should be extracted", entities.upiIds.isNotEmpty())
        assertTrue(entities.amounts.any { it.contains("850") })

        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.PAYMENT, category)
    }

    @Test
    fun testGmailEmailDoesNotTriggerPayment() {
        // A corporate/personal email should NOT be classified as PAYMENT
        val ocrText = """
            BioEnable Technologies Pvt. Ltd.
            Contact: atish@bioenabletech.com
            Phone: +91 8601020621
            Website: www.bioenable.com
            Payment Terms: Advance Payment Required
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        // Gmail/corporate email should NOT be in upiIds
        assertTrue("Corporate email should not be a UPI ID", entities.upiIds.isEmpty())
        // It should be in emails
        assertTrue("Corporate email should be extracted as email", entities.emails.isNotEmpty())

        val category = CategoryClassifier.classify(ocrText, entities)
        // "Payment Terms" alone with no confirmed transaction → should NOT be PAYMENT
        assertFalse("Should not be PAYMENT for a document with payment terms text", category == Category.PAYMENT)
    }

    @Test
    fun testContactCardClassification() {
        val ocrText = """
            Rajesh Kumar
            Senior Manager
            BioEnable Technologies Pvt. Ltd.
            Mobile: 9876543210
            Email: rajesh.kumar@bioenable.com
            Website: www.bioenable.com
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        assertTrue("Phone should be extracted", entities.phoneNumbers.isNotEmpty())
        assertTrue("Email should be extracted", entities.emails.isNotEmpty())
        assertTrue("UPI IDs should be empty for contact card", entities.upiIds.isEmpty())

        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.CONTACT, category)
    }

    @Test
    fun testTicketClassificationAndPnrExtraction() {
        val ocrText = """
            IRCTC Electronic Reservation Slip
            PNR No: 8492019482
            Train: RAJDHANI EXPRESS
            Coach: A1 Berth: 12
            Confirmed
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        assertTrue("PNR should be extracted", entities.pnrNumbers.isNotEmpty())
        assertTrue("Extracted PNR should contain the number", entities.pnrNumbers.first().contains("8492019482"))

        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.TICKET, category)
    }

    @Test
    fun testReceiptClassification() {
        val ocrText = """
            Amazon.in Tax Invoice
            Order #402-910294-1029
            Subtotal: ₹1,299.00
            GST: ₹233.82
            Total Amount: ₹1,532.82
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.RECEIPT, category)
    }

    @Test
    fun testCodeClassification() {
        val ocrText = """
            import kotlinx.coroutines.flow.Flow
            
            fun searchFts(query: String): List<SearchResult> {
                val sql = "SELECT * FROM screenshots_fts"
                return db.rawQuery(sql, arrayOf(query))
            }
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.CODE, category)
    }

    @Test
    fun testCredentialOtpClassification() {
        val ocrText = """
            Your one time password (OTP) is 492018 for bank login.
            Do not share this code with anyone.
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        assertEquals(listOf("492018"), entities.otps)

        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.CREDENTIAL, category)
    }

    @Test
    fun testProposalDocumentNotMisclassifiedAsPayment() {
        val ocrText = """
            Dear Sir,
            We are pleased to submit our proposal for the biometric attendance system.
            Payment Terms: 50% Advance, 50% on Delivery
            Payment Mode: NEFT/RTGS/Cheque
            Total Cost: ₹2,50,000
            Please review the attached contract and revert.
            Regards, BioEnable Team
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        assertTrue("No UPI IDs in proposal doc", entities.upiIds.isEmpty())

        val category = CategoryClassifier.classify(ocrText, entities)
        assertFalse("Proposal should not be PAYMENT", category == Category.PAYMENT)
        // Should be DOCUMENT (long text, sentences, word count)
        assertEquals(Category.DOCUMENT, category)
    }

    @Test
    fun testPhonepeTransactionIsPayment() {
        val ocrText = """
            PhonePe
            Payment Successful
            ₹500 sent to Priya Sharma
            UPI Ref: 311929394850
            From: HDFC Bank ****1234
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.PAYMENT, category)
    }

    @Test
    fun testChatClassification() {
        val ocrText = """
            WhatsApp
            Priya Kumar
            Today at 3:30 PM
            Hey are you coming tomorrow?
            Delivered
            Online
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.CHAT, category)
    }

    @Test
    fun testCorporateEmailDomainDoesNotMatchAsUpi() {
        val ocrText = """
            From: john@sbi.co.in
            To: finance@hdfcbank.com
            Support: admin@axisbank.co.in
            Please review the attached quarterly balance sheet.
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        assertTrue("Bank domain corporate emails must NOT match as UPI handles", entities.upiIds.isEmpty())
        assertTrue("All emails should be extracted as valid emails", entities.emails.size >= 3)
    }

    @Test
    fun testWordBoundaryPreventsColaMatchingOla() {
        // "Coca Cola" should not trigger RECEIPT via "ola"
        val ocrText = """
            Grocery Shopping List
            2 bottles of Coca Cola
            1 pack of whole wheat bread
            Fresh apples and bananas
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        val category = CategoryClassifier.classify(ocrText, entities)
        assertFalse("Shopping list with Coca Cola should NOT be classified as RECEIPT", category == Category.RECEIPT)
    }

    @Test
    fun testSyntaxErrorDoesNotTriggerFiscalTaxSignal() {
        val ocrText = """
            class TokenValidator {
                // SyntaxError: unexpected token on line 42
                val token = "test"
                fun parse() = true
            }
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        val category = CategoryClassifier.classify(ocrText, entities)
        assertEquals(Category.CODE, category)
    }

    @Test
    fun testReversedOtpExtraction() {
        val text = "948210 is your one-time verification code for Swiggy account login. Do not share OTP."
        val entities = CategoryClassifier.extractEntities(text)
        assertTrue("Reversed OTP should be captured in otps", entities.otps.contains("948210"))
    }

    @Test
    fun testCommaSafeJsonSerializationAndDeserialization() {
        val original = com.littech.screenintel.core.classifier.ExtractedEntities(
            amounts = listOf("₹1,200.00", "₹45,500.50"),
            upiIds = listOf("priya@okicici", "rahul@ybl")
        )

        val json = original.toJson()
        val deserialized = com.littech.screenintel.core.classifier.ExtractedEntities.fromJson(json)

        assertEquals(2, deserialized.amounts.size)
        assertEquals("₹1,200.00", deserialized.amounts[0])
        assertEquals("₹45,500.50", deserialized.amounts[1])
        assertEquals(2, deserialized.upiIds.size)
    }

    @Test
    fun testLegacyCommaSeparatedJsonBackwardCompatibility() {
        val legacyJson = """{"amounts":"₹500.00,₹200.00","upiIds":"merchant@upi"}"""
        val deserialized = com.littech.screenintel.core.classifier.ExtractedEntities.fromJson(legacyJson)

        assertEquals(listOf("₹500.00", "₹200.00"), deserialized.amounts)
        assertEquals(listOf("merchant@upi"), deserialized.upiIds)
    }
}
