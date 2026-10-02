package com.littech.screenintel.core

import com.littech.screenintel.core.classifier.PiiRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiRedactorTest {

    @Test
    fun testCreditCardMasking() {
        val input = "Payment made using Visa 4242 4242 4242 4242 successfully."
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("•••• •••• •••• 4242"))
        assertFalse(redacted.contains("4242 4242 4242"))
    }

    @Test
    fun testAmexCardMasking() {
        // AmEx 15-digit 4-6-5 format
        val input = "Card: 3782-822463-10005"
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("•••• •••• •••• 0005"))
        assertFalse(redacted.contains("3782-822463"))
    }

    @Test
    fun testRawContinuousCardWithLuhnValidation() {
        // Valid 16-digit Luhn test card: 4242424242424242
        val validCard = "Transaction on card 4242424242424242 debited."
        val redactedValid = PiiRedactor.redact(validCard)
        assertTrue(redactedValid.contains("•••• •••• •••• 4242"))
        assertFalse(redactedValid.contains("42424242424242"))

        // Invalid 16-digit order ID: 1234567890123456 (fails Luhn)
        val orderText = "Order ID 1234567890123456 confirmed."
        val redactedOrder = PiiRedactor.redact(orderText)
        assertTrue("Invalid Luhn number should NOT be masked as card", redactedOrder.contains("1234567890123456"))
    }

    @Test
    fun testCvvRedaction() {
        val input = "Do not share CVV: 789 with anyone."
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("CVV: •••"))
        assertFalse(redacted.contains("789"))
    }

    @Test
    fun testForwardOtpRedaction() {
        val input = "Your verification OTP: 839201 expires in 5 minutes."
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("OTP: ••••••"))
        assertFalse(redacted.contains("839201"))
    }

    @Test
    fun testReversedOtpRedaction() {
        val input = "948210 is your one-time verification code for Swiggy account login."
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("•••••• is your one-time verification code"))
        assertFalse(redacted.contains("948210"))
    }

    @Test
    fun testNationalIdMasking() {
        val input = "Aadhaar: 1234 5678 9012 registered."
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("•••• •••• 9012"))
        assertFalse(redacted.contains("1234 5678"))
    }

    @Test
    fun testIndianPanCardRedaction() {
        val input = "Income Tax Return filed for PAN: ABCDE1234F on portal."
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("ABC•••••4F"))
        assertFalse(redacted.contains("ABCDE1234F"))
    }

    @Test
    fun testBankAccountRedaction() {
        val input = "Transferred from A/C No: 10293847561 successfully."
        val redacted = PiiRedactor.redact(input)
        assertTrue(redacted.contains("7561"))
        assertFalse(redacted.contains("10293847561"))
        assertTrue(redacted.contains("•••••••7561"))
    }

    @Test
    fun testLuhnAlgorithmUnit() {
        assertTrue(PiiRedactor.isValidLuhn("4242424242424242"))
        assertTrue(PiiRedactor.isValidLuhn("4532015112830366"))
        assertFalse(PiiRedactor.isValidLuhn("4242424242424241"))
        assertFalse(PiiRedactor.isValidLuhn("12345"))
    }
}
