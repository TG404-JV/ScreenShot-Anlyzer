package com.littech.screenintel.core.classifier

import com.littech.screenintel.core.classifier.rules.CodeRule
import com.littech.screenintel.core.classifier.rules.CredentialRule
import com.littech.screenintel.core.classifier.rules.PaymentRule
import com.littech.screenintel.core.classifier.rules.TicketRule
import com.littech.screenintel.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRuleEngineTest {

    private val config = ClassifierConfig()

    @Test
    fun testCredentialRuleIsolated() {
        val rule = CredentialRule()
        val text = "Your one-time password is here. Do not share OTP."
        val entities = ExtractedEntities(otps = listOf("492018"))

        val result = rule.evaluate(text, entities, config)
        assertTrue(result.matched)
        assertEquals(Category.CREDENTIAL, result.category)
        assertTrue(result.score >= 5)
        assertTrue(result.reason.contains("otpBonus=5"))
    }

    @Test
    fun testPaymentRuleIsolated() {
        val rule = PaymentRule()
        val text = "Payment Successful to Rohan. Paid successfully ₹500 via UPI."
        val entities = ExtractedEntities(
            upiIds = listOf("rohan@okicici"),
            amounts = listOf("₹500.00")
        )

        val result = rule.evaluate(text, entities, config)
        assertTrue(result.matched)
        assertEquals(Category.PAYMENT, result.category)
        assertTrue(result.score >= 3)
    }

    @Test
    fun testTicketRuleIsolated() {
        val rule = TicketRule()
        val text = "IRCTC Train Ticket Electronic Reservation Slip Departure: 06:00"
        val entities = ExtractedEntities(pnrNumbers = listOf("1234567890"))

        val result = rule.evaluate(text, entities, config)
        assertTrue(result.matched)
        assertEquals(Category.TICKET, result.category)
        assertTrue(result.score >= 5)
    }

    @Test
    fun testCodeRuleIsolated() {
        val rule = CodeRule()
        val text = "public static void main(String[] args) {\n    System.out.println();\n}"
        val entities = ExtractedEntities()

        val result = rule.evaluate(text, entities, config)
        assertTrue(result.matched)
        assertEquals(Category.CODE, result.category)
    }

    @Test
    fun testCustomConfigThresholdTuning() {
        // Strict threshold requirement: score must be >= 15
        val strictConfig = ClassifierConfig(paymentThreshold = 15)
        val text = "Payment Successful ₹100.00"
        val entities = ExtractedEntities(amounts = listOf("₹100.00"))

        val defaultResult = CategoryClassifier.classify(text, entities)
        assertEquals(Category.PAYMENT, defaultResult)

        val strictResult = CategoryClassifier.classify(text, entities, strictConfig)
        assertFalse("Strict threshold should prevent low-score classification", strictResult == Category.PAYMENT)
    }

    @Test
    fun testDetailedDiagnostics() {
        val text = "Paid successfully to rahul@ybl ₹450.00 UPI Ref No 328491829101"
        val entities = CategoryClassifier.extractEntities(text)

        val diagnostics = CategoryClassifier.evaluateDetailed(text, entities)
        assertEquals("Should evaluate all 9 categories", 9, diagnostics.size)

        val paymentDiag = diagnostics.first { it.category == Category.PAYMENT }
        assertTrue(paymentDiag.matched)
        assertTrue(paymentDiag.score > 0)
        assertTrue(paymentDiag.reason.isNotBlank())
    }
}
