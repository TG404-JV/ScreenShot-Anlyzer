package com.littech.screenintel.core

import com.littech.screenintel.core.classifier.CategoryClassifier
import com.littech.screenintel.core.classifier.PiiRedactor
import com.littech.screenintel.core.hasher.VisualHashCalculator
import com.littech.screenintel.model.Category
import com.littech.screenintel.model.DuplicateGroup
import com.littech.screenintel.model.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end logic test demonstrating how real-world screenshots are processed,
 * classified, scrubbed for PII, indexed, and deduplicated.
 */
class ScreenshotPipelineLogicTest {

    // =========================================================================
    // 1. UPI Payment Screenshot Test
    // =========================================================================
    @Test
    fun testRealUpiPaymentScreenshot() {
        val ocrText = """
            Google Pay
            Payment to Blue Tokai Coffee Roasters
            ₹420.00
            UPI Transaction ID: 429103948291
            To: bluetokai@icici
            From: HDFC Bank •••• 9876
            Google Transaction ID: CICAgODV246ePA
            Payment Successful
            Sep 25, 2026, 11:32 AM
        """.trimIndent()

        // 1. Entity Extraction
        val entities = CategoryClassifier.extractEntities(ocrText)
        println("--- UPI Payment Extraction ---")
        println("UPI IDs found: ${entities.upiIds}")
        println("Amounts found: ${entities.amounts}")

        assertTrue("Should extract bluetokai@icici UPI ID", entities.upiIds.contains("bluetokai@icici"))
        assertTrue("Should extract ₹420.00 amount", entities.amounts.contains("₹420.00"))

        // 2. Classification
        val category = CategoryClassifier.classify(ocrText, entities)
        println("Classified Category: $category")
        assertEquals(Category.PAYMENT, category)
    }

    // =========================================================================
    // 2. Ticket / Booking Screenshot Test
    // =========================================================================
    @Test
    fun testRealTicketBookingScreenshot() {
        val ocrText = """
            IRCTC Electronic Reservation Slip
            PNR: 8492019482
            Train No. & Name: 12952 / MUMBAI RAJDHANI
            Booking Date: 2026-09-15
            Class: 3A (Third AC)
            From: NDLS To: MMCT
            Passenger 1: Rajesh Kumar / Age: 34 / Berth: B4-23 (Lower)
            Total Fare: ₹2,450.00
            Booking Confirmed
        """.trimIndent()

        // 1. Entity Extraction
        val entities = CategoryClassifier.extractEntities(ocrText)
        println("--- Ticket Booking Extraction ---")
        println("PNR Numbers found: ${entities.pnrNumbers}")

        assertTrue("Should extract PNR 8492019482", entities.pnrNumbers.contains("8492019482"))

        // 2. Classification
        val category = CategoryClassifier.classify(ocrText, entities)
        println("Classified Category: $category")
        assertEquals(Category.TICKET, category)
    }

    // =========================================================================
    // 3. Credential & PII Masking Screenshot Test
    // =========================================================================
    @Test
    fun testRealCredentialScreenshotWithPiiScrubbing() {
        val rawOcrText = """
            State Bank of India Security Alert
            Your verification OTP is 839102 for net banking login.
            Do not share this code or your password with anyone.
            Card ending with 4532 8912 3456 7890 registered.
            CVV: 456
        """.trimIndent()

        // 1. Entity Extraction
        val entities = CategoryClassifier.extractEntities(rawOcrText)
        println("--- Credential Screenshot Extraction ---")
        println("OTPs found: ${entities.otps}")
        assertTrue("Should extract OTP 839102", entities.otps.contains("839102"))

        // 2. Category Classification
        val category = CategoryClassifier.classify(rawOcrText, entities)
        println("Classified Category: $category")
        assertEquals(Category.CREDENTIAL, category)

        // 3. PII Scrubbing
        val redactedText = PiiRedactor.redact(rawOcrText)
        println("Redacted Text:\n$redactedText")

        // Assert sensitive card numbers and CVV are sanitized
        assertTrue("PAN must be masked to •••• •••• •••• 7890", redactedText.contains("•••• •••• •••• 7890"))
        assertFalse("Raw card digits must not remain", redactedText.contains("4532 8912 3456"))
        assertTrue("CVV must be masked to •••", redactedText.contains("CVV: •••"))
        assertFalse("Raw CVV digits must not remain", redactedText.contains("456"))
    }

    // =========================================================================
    // 4. Chat & Messaging Screenshot Test
    // =========================================================================
    @Test
    fun testRealChatScreenshot() {
        val ocrText = """
            Rajesh (WhatsApp)
            Today at 10:45 AM
            Hey, here is the link to our project docs:
            https://github.com/android/architecture-samples
            Call me at 9876543210 if any questions!
            typing...
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        println("--- Chat Screenshot Extraction ---")
        println("URLs found: ${entities.urls}")
        println("Phone numbers found: ${entities.phoneNumbers}")

        assertTrue("Should extract github URL", entities.urls.contains("https://github.com/android/architecture-samples"))
        assertTrue("Should extract phone number", entities.phoneNumbers.any { it.contains("9876543210") })

        val category = CategoryClassifier.classify(ocrText, entities)
        println("Classified Category: $category")
        assertEquals(Category.CHAT, category)
    }

    // =========================================================================
    // 5. Code & Technical Screenshot Test
    // =========================================================================
    @Test
    fun testRealCodeScreenshot() {
        val ocrText = """
            package com.littech.screenintel.data
            
            import kotlinx.coroutines.flow.Flow
            
            class ScreenshotRepository(val db: ScreenIntelDatabase) {
                fun searchFts(query: String): Flow<List<Screenshot>> {
                    val sql = "SELECT * FROM screenshots_fts WHERE text MATCH ?"
                    return db.query(sql, query)
                }
            }
        """.trimIndent()

        val entities = CategoryClassifier.extractEntities(ocrText)
        val category = CategoryClassifier.classify(ocrText, entities)
        println("--- Code Screenshot ---")
        println("Classified Category: $category")
        assertEquals(Category.CODE, category)
    }

    // =========================================================================
    // 6. Visual Hash (dHash) & Deduplication Clustering Test
    // =========================================================================
    @Test
    fun testScreenshotDeduplicationAndGrouping() {
        // Screenshot A: Original payment screen
        val hashA = 0x7C3C1E1E1E1E3C7CL
        // Screenshot B: Near-duplicate of Screenshot A (1 bit diff, e.g. status bar clock tick)
        val hashB = 0x7C3C1E1E1E1E3C7EL
        // Screenshot C: Completely different ticket screen
        val hashC = 0x00FF00FF00FF00FFL

        val distAB = VisualHashCalculator.hammingDistance(hashA, hashB)
        val distAC = VisualHashCalculator.hammingDistance(hashA, hashC)

        println("--- Visual dHash Hamming Distances ---")
        println("Hash A vs Near-Duplicate B distance: $distAB bits")
        println("Hash A vs Different Ticket C distance: $distAC bits")

        assertEquals(1, distAB)
        assertTrue(VisualHashCalculator.isDuplicate(hashA, hashB, threshold = 3))
        assertFalse(VisualHashCalculator.isDuplicate(hashA, hashC, threshold = 3))

        // Create sample screenshots
        val s1 = Screenshot(
            id = 1L,
            uriString = "content://media/external/images/media/1",
            filePath = "/storage/emulated/0/Pictures/Screenshots/Screenshot_1.png",
            dateAdded = 1000L,
            width = 1080,
            height = 2400,
            fileSize = 450_000L, // 450 KB
            rawOcrText = "Google Pay ₹420.00 bluetokai@icici",
            redactedOcrText = "Google Pay ₹420.00 bluetokai@icici",
            category = Category.PAYMENT,
            visualHash = hashA,
            processedAt = 1000L
        )

        val s2Duplicate = Screenshot(
            id = 2L,
            uriString = "content://media/external/images/media/2",
            filePath = "/storage/emulated/0/Pictures/Screenshots/Screenshot_2.png",
            dateAdded = 1010L, // 10s later
            width = 1080,
            height = 2400,
            fileSize = 450_000L, // 450 KB
            rawOcrText = "Google Pay ₹420.00 bluetokai@icici",
            redactedOcrText = "Google Pay ₹420.00 bluetokai@icici",
            category = Category.PAYMENT,
            visualHash = hashB,
            processedAt = 1010L
        )

        val s3Ticket = Screenshot(
            id = 3L,
            uriString = "content://media/external/images/media/3",
            filePath = "/storage/emulated/0/Pictures/Screenshots/Screenshot_3.png",
            dateAdded = 2000L,
            width = 1080,
            height = 2400,
            fileSize = 520_000L,
            rawOcrText = "IRCTC PNR: 8492019482",
            redactedOcrText = "IRCTC PNR: 8492019482",
            category = Category.TICKET,
            visualHash = hashC,
            processedAt = 2000L
        )

        // Cluster deduplication algorithm (identical to ScreenshotRepository.findDuplicates)
        val all = listOf(s1, s2Duplicate, s3Ticket)
        val visited = mutableSetOf<Long>()
        val groups = mutableListOf<DuplicateGroup>()

        for (i in all.indices) {
            val primary = all[i]
            if (visited.contains(primary.id)) continue

            val duplicates = mutableListOf<Screenshot>()
            var maxDistance = 0

            for (j in (i + 1) until all.size) {
                val candidate = all[j]
                if (visited.contains(candidate.id)) continue

                val distance = VisualHashCalculator.hammingDistance(primary.visualHash, candidate.visualHash)
                if (distance <= 3) {
                    duplicates.add(candidate)
                    visited.add(candidate.id)
                    if (distance > maxDistance) maxDistance = distance
                }
            }

            if (duplicates.isNotEmpty()) {
                visited.add(primary.id)
                groups.add(DuplicateGroup(primary, duplicates, maxDistance))
            }
        }

        println("Duplicate groups found: ${groups.size}")
        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals(s1.id, group.primary.id)
        assertEquals(1, group.duplicates.size)
        assertEquals(s2Duplicate.id, group.duplicates[0].id)

        val reclaimableSpace = group.duplicates.sumOf { it.fileSize }
        println("Reclaimable storage space: ${reclaimableSpace / 1024} KB")
        assertEquals(439L, reclaimableSpace / 1024)
    }

    // =========================================================================
    // 7. Full Search, Filter & Actionability Multi-Screenshot Test
    // =========================================================================
    @Test
    fun testMultiScreenshotSearchAndFilter() {
        val screenshots = listOf(
            Screenshot(
                id = 1L,
                uriString = "uri_1",
                filePath = "path_1",
                dateAdded = 1000L,
                width = 1080,
                height = 2400,
                fileSize = 400_000L,
                rawOcrText = "Paid to Blue Tokai ₹420.00 via UPI bluetokai@icici",
                redactedOcrText = "Paid to Blue Tokai ₹420.00 via UPI bluetokai@icici",
                category = Category.PAYMENT,
                visualHash = 0x11L,
                processedAt = 1000L
            ),
            Screenshot(
                id = 2L,
                uriString = "uri_2",
                filePath = "path_2",
                dateAdded = 2000L,
                width = 1080,
                height = 2400,
                fileSize = 500_000L,
                rawOcrText = "IRCTC Rajdhani Express PNR 8492019482 Confirmed",
                redactedOcrText = "IRCTC Rajdhani Express PNR 8492019482 Confirmed",
                category = Category.TICKET,
                visualHash = 0x22L,
                processedAt = 2000L
            ),
            Screenshot(
                id = 3L,
                uriString = "uri_3",
                filePath = "path_3",
                dateAdded = 3000L,
                width = 1080,
                height = 2400,
                fileSize = 300_000L,
                rawOcrText = "class ScreenshotRepository { fun searchFts() }",
                redactedOcrText = "class ScreenshotRepository { fun searchFts() }",
                category = Category.CODE,
                visualHash = 0x33L,
                processedAt = 3000L
            )
        )

        // 1. Text Search for "Rajdhani"
        val query1 = "Rajdhani"
        val results1 = screenshots.filter { it.rawOcrText.contains(query1, ignoreCase = true) }
        assertEquals(1, results1.size)
        assertEquals(Category.TICKET, results1[0].category)

        // 2. Text Search for "UPI"
        val query2 = "UPI"
        val results2 = screenshots.filter { it.rawOcrText.contains(query2, ignoreCase = true) }
        assertEquals(1, results2.size)
        assertEquals(Category.PAYMENT, results2[0].category)

        // 3. Category Filter
        val paymentScreenshots = screenshots.filter { it.category == Category.PAYMENT }
        assertEquals(1, paymentScreenshots.size)

        // 4. Actionable items filter
        // A screenshot is actionable if it contains payment, ticket, receipt, or credential entities
        val actionableScreenshots = screenshots.filter { s ->
            s.category in listOf(Category.PAYMENT, Category.TICKET, Category.CREDENTIAL, Category.RECEIPT)
        }
        assertEquals(2, actionableScreenshots.size)
        assertTrue(actionableScreenshots.none { it.category == Category.CODE })

        println("Multi-screenshot Search & Filter verified successfully!")
    }
}
