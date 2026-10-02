package com.littech.screenintel.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialOcrFilterTest {

    @Test
    fun filterLines_removesTopStatusBarTimeAndBattery() {
        val width = 1080
        val height = 2400

        val lines = listOf(
            TextLineGeometry(text = "10:45 AM | 5G | 84%", top = 20, bottom = 65),
            TextLineGeometry(text = "Google Pay", top = 200, bottom = 250),
            TextLineGeometry(text = "Payment Successful ₹1,450.00", top = 300, bottom = 360)
        )

        val result = SpatialOcrFilter.filterLines(lines, width, height)

        assertFalse(result.cleanText.contains("10:45 AM"))
        assertFalse(result.cleanText.contains("84%"))
        assertTrue(result.cleanText.contains("Google Pay"))
        assertTrue(result.cleanText.contains("Payment Successful ₹1,450.00"))
        assertEquals(2, result.filteredLines.size)
        assertEquals(1, result.removedLines.size)
        assertEquals("10:45 AM | 5G | 84%", result.removedLines[0].text)
    }

    @Test
    fun filterLines_removesIndianTelcoCarrierInTopZone() {
        val width = 1080
        val height = 2400

        val lines = listOf(
            TextLineGeometry(text = "Jio 4G  VoLTE", top = 15, bottom = 55),
            TextLineGeometry(text = "Train No 12952 Confirmed", top = 400, bottom = 450)
        )

        val result = SpatialOcrFilter.filterLines(lines, width, height)

        assertFalse(result.cleanText.contains("Jio 4G"))
        assertTrue(result.cleanText.contains("Train No 12952 Confirmed"))
    }

    @Test
    fun filterLines_removesBottomNavBarSystemButtons() {
        val width = 1080
        val height = 2400

        val lines = listOf(
            TextLineGeometry(text = "Order Details #402-19284", top = 250, bottom = 300),
            TextLineGeometry(text = "Back", top = 2320, bottom = 2380)
        )

        val result = SpatialOcrFilter.filterLines(lines, width, height)

        assertTrue(result.cleanText.contains("Order Details"))
        assertFalse(result.cleanText.contains("Back"))
    }

    @Test
    fun filterLines_removesBottomNavTabButtonPay() {
        val width = 1080
        val height = 2400

        // In an e-commerce app, "Pay" or "Home" at the bottom nav bar should NOT trigger payment classification
        val lines = listOf(
            TextLineGeometry(text = "Samsung Galaxy S24 Ultra", top = 300, bottom = 350),
            TextLineGeometry(text = "Pay", top = 2320, bottom = 2370)
        )

        val result = SpatialOcrFilter.filterLines(lines, width, height)

        assertTrue(result.cleanText.contains("Samsung Galaxy S24 Ultra"))
        assertFalse(result.cleanText.contains("Pay"))
    }

    @Test
    fun filterLines_preservesKeywordInMainContentBody() {
        val width = 1080
        val height = 2400

        // "Pay" at y = 1200 (center of screen) is content, NOT bottom navigation chrome
        val lines = listOf(
            TextLineGeometry(text = "Pay ₹500 for groceries", top = 1200, bottom = 1260)
        )

        val result = SpatialOcrFilter.filterLines(lines, width, height)

        assertTrue(result.cleanText.contains("Pay ₹500 for groceries"))
        assertEquals(0, result.removedLines.size)
    }

    @Test
    fun filterLines_handlesCroppedNonPhoneImagesSafely() {
        // Square image (e.g. cropped invoice photo, 1000x1000)
        val width = 1000
        val height = 1000

        val lines = listOf(
            TextLineGeometry(text = "TAX INVOICE", top = 20, bottom = 60),
            TextLineGeometry(text = "Total: ₹2,400.00", top = 500, bottom = 550)
        )

        val result = SpatialOcrFilter.filterLines(lines, width, height)

        // "TAX INVOICE" at the top of a cropped document must NOT be deleted
        assertTrue(result.cleanText.contains("TAX INVOICE"))
        assertTrue(result.cleanText.contains("Total: ₹2,400.00"))
        assertEquals(2, result.filteredLines.size)
    }

    @Test
    fun filterLines_handlesLongScrollingScreenshotSafely() {
        // Long scrolling screenshot stitched by MIUI (1080 x 6525 px)
        val width = 1080
        val height = 6525

        val lines = listOf(
            TextLineGeometry(text = "10:45 AM | 5G | 84%", top = 20, bottom = 65), // status bar -> filtered
            TextLineGeometry(text = "Order Summary: 5 items", top = 250, bottom = 300), // top content (would be inside 5.5% if unclamped) -> preserved!
            TextLineGeometry(text = "Item 1: ₹500", top = 1200, bottom = 1250),
            TextLineGeometry(text = "Subtotal: ₹2,500.00", top = 6200, bottom = 6250), // bottom content -> preserved!
            TextLineGeometry(text = "Home", top = 6450, bottom = 6500) // nav bar -> filtered
        )

        val result = SpatialOcrFilter.filterLines(lines, width, height)

        assertFalse(result.cleanText.contains("10:45 AM"))
        assertTrue(result.cleanText.contains("Order Summary: 5 items"))
        assertTrue(result.cleanText.contains("Item 1: ₹500"))
        assertTrue(result.cleanText.contains("Subtotal: ₹2,500.00"))
        assertFalse(result.cleanText.contains("Home"))
        assertEquals(3, result.filteredLines.size)
        assertEquals(2, result.removedLines.size)
    }
}
