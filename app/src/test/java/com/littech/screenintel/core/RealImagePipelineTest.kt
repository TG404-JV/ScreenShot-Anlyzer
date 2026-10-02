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
import org.junit.BeforeClass
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * End-to-end testing with REAL image files on disk.
 * Renders actual PNG screenshot files, decodes real image pixels, calculates
 * perceptual difference hashes (dHash), tests deduplication distance on real images,
 * and validates the intelligence extraction pipeline.
 */
class RealImagePipelineTest {

    companion object {
        private val TEST_DIR = File("build/test-real-images")
        private lateinit var paymentFile: File
        private lateinit var paymentDuplicateFile: File
        private lateinit var ticketFile: File
        private lateinit var chatFile: File
        private lateinit var wallpaperFile: File

        @BeforeClass
        @JvmStatic
        fun generateRealTestImages() {
            TEST_DIR.mkdirs()

            paymentFile = File(TEST_DIR, "real_upi_payment.png")
            paymentDuplicateFile = File(TEST_DIR, "real_upi_payment_duplicate.png")
            ticketFile = File(TEST_DIR, "real_irctc_ticket.png")
            chatFile = File(TEST_DIR, "real_whatsapp_chat.png")
            wallpaperFile = File(TEST_DIR, "real_scenic_wallpaper.png")

            // 1. Create Real Payment Screenshot (Google Pay style)
            createPaymentImage(paymentFile, timeText = "10:30 AM")

            // 2. Create Real Near-Duplicate Payment Screenshot (same layout, 1 minute later)
            createPaymentImage(paymentDuplicateFile, timeText = "10:31 AM")

            // 3. Create Real Ticket Screenshot (IRCTC style)
            createTicketImage(ticketFile)

            // 4. Create Real Chat Screenshot (WhatsApp style)
            createChatImage(chatFile)

            // 5. Create Real Wallpaper Image (Scenic gradient, no text)
            createWallpaperImage(wallpaperFile)
        }

        private fun createPaymentImage(file: File, timeText: String) {
            val width = 720
            val height = 1280
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Background
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)

            // Teal Header Banner
            g.color = Color(13, 148, 136) // Teal
            g.fillRect(0, 0, width, 180)

            // Header Text
            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 36)
            g.drawString("Google Pay", 40, 90)
            g.font = Font("SansSerif", Font.PLAIN, 22)
            g.drawString("Payment Successful", 40, 140)

            // Success Checkmark Circle
            g.color = Color(34, 197, 94) // Green
            g.fillOval(width / 2 - 50, 240, 100, 100)
            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 48)
            g.drawString("✓", width / 2 - 16, 305)

            // Amount
            g.color = Color(30, 41, 59)
            g.font = Font("SansSerif", Font.BOLD, 52)
            g.drawString("₹1,450.00", width / 2 - 120, 390)

            // Transaction Details
            g.color = Color(100, 116, 139)
            g.font = Font("SansSerif", Font.PLAIN, 24)
            g.drawString("Paid to Blue Tokai Coffee Roasters", 80, 480)
            g.drawString("UPI ID: cafe.bistro@icici", 80, 530)
            g.drawString("UPI Ref No: 482910394819", 80, 580)
            g.drawString("Date: Sep 27, 2026, $timeText", 80, 630)

            g.dispose()
            ImageIO.write(img, "PNG", file)
        }

        private fun createTicketImage(file: File) {
            val width = 720
            val height = 1280
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // White Background
            g.color = Color(248, 250, 252)
            g.fillRect(0, 0, width, height)

            // Navy Blue Header with logo icon on left
            g.color = Color(30, 58, 138) // Navy
            g.fillRect(0, 0, width, 180)
            g.color = Color(245, 158, 11) // Amber emblem
            g.fillOval(30, 40, 70, 70)

            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 30)
            g.drawString("IRCTC Reservation", 120, 80)
            g.font = Font("SansSerif", Font.PLAIN, 20)
            g.drawString("Indian Railways E-Ticket", 120, 120)

            // PNR Box on Left
            g.color = Color(224, 231, 255)
            g.fillRoundRect(40, 220, 420, 140, 20, 20)
            g.color = Color(37, 99, 235)
            g.font = Font("SansSerif", Font.BOLD, 32)
            g.drawString("PNR: 8492019482", 70, 295)

            // Black QR Code on Right (creates strong horizontal luminance difference)
            g.color = Color.BLACK
            g.fillRect(width - 200, 210, 150, 150)
            g.color = Color.WHITE
            g.fillRect(width - 180, 230, 110, 110)
            g.color = Color.BLACK
            g.fillRect(width - 160, 250, 70, 70)

            // Train Details Table
            g.color = Color(15, 23, 42)
            g.fillRect(40, 390, width - 80, 50)
            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 22)
            g.drawString("12952 / MUMBAI RAJDHANI", 60, 425)

            g.color = Color(51, 65, 85)
            g.font = Font("SansSerif", Font.PLAIN, 20)
            g.drawString("From: NDLS To: MMCT | Class: 3A", 60, 480)
            g.drawString("Passenger: Rajesh Kumar | Coach: B4 Berth: 23 (LB)", 60, 520)
            g.drawString("Total Fare: ₹2,450.00 | Status: Confirmed", 60, 560)

            g.dispose()
            ImageIO.write(img, "PNG", file)
        }

        private fun createChatImage(file: File) {
            val width = 720
            val height = 1280
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // WhatsApp Teal Header
            g.color = Color(7, 94, 84)
            g.fillRect(0, 0, width, 140)

            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 30)
            g.drawString("Rajesh Sharma", 60, 80)
            g.font = Font("SansSerif", Font.PLAIN, 18)
            g.drawString("online", 60, 115)

            // Background Chat Area
            g.color = Color(236, 229, 221)
            g.fillRect(0, 140, width, height - 140)

            // Incoming Chat Bubble (Dark contrast on Left)
            g.color = Color(255, 255, 255)
            g.fillRoundRect(30, 190, 460, 140, 20, 20)
            g.color = Color.BLACK
            g.font = Font("SansSerif", Font.PLAIN, 20)
            g.drawString("Hey, please check the repository:", 50, 230)
            g.color = Color(37, 99, 235)
            g.drawString("https://github.com/android/architecture", 50, 270)
            g.color = Color.GRAY
            g.font = Font("SansSerif", Font.PLAIN, 16)
            g.drawString("10:42 AM", 400, 310)

            // Outgoing Chat Bubble (Dark contrast on Right)
            g.color = Color(220, 248, 198) // Light Green
            g.fillRoundRect(220, 370, 470, 120, 20, 20)
            g.color = Color.BLACK
            g.font = Font("SansSerif", Font.PLAIN, 20)
            g.drawString("Call me at +91 9876543210!", 240, 420)
            g.color = Color.GRAY
            g.font = Font("SansSerif", Font.PLAIN, 16)
            g.drawString("10:43 AM ✓✓", 580, 470)

            g.dispose()
            ImageIO.write(img, "PNG", file)
        }

        private fun createWallpaperImage(file: File) {
            val width = 720
            val height = 1280
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g: Graphics2D = img.createGraphics()

            // Diagonal gradient creating strong left-to-right color transitions
            val gradient = GradientPaint(0f, 0f, Color(249, 115, 22), width.toFloat(), height.toFloat(), Color(30, 27, 75))
            g.paint = gradient
            g.fillRect(0, 0, width, height)

            g.dispose()
            ImageIO.write(img, "PNG", file)
        }

        /**
         * Helper: computes 64-bit dHash directly from a real BufferedImage on disk
         */
        fun computeRealImageDHash(image: BufferedImage): Long {
            val scaled = BufferedImage(9, 8, BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            g.drawImage(image, 0, 0, 9, 8, null)
            g.dispose()

            val pixels = IntArray(72)
            scaled.getRGB(0, 0, 9, 8, pixels, 0, 9)
            return VisualHashCalculator.computeDHashFrom9x8Pixels(pixels)
        }
    }

    // =========================================================================
    // 1. Verify Real Image Files Exist on Disk
    // =========================================================================
    @Test
    fun testRealImageFilesGeneratedAndDecoded() {
        assertTrue("Payment PNG file must exist", paymentFile.exists() && paymentFile.length() > 0)
        assertTrue("Duplicate Payment PNG must exist", paymentDuplicateFile.exists() && paymentDuplicateFile.length() > 0)
        assertTrue("Ticket PNG file must exist", ticketFile.exists() && ticketFile.length() > 0)
        assertTrue("Chat PNG file must exist", chatFile.exists() && chatFile.length() > 0)
        assertTrue("Wallpaper PNG file must exist", wallpaperFile.exists() && wallpaperFile.length() > 0)

        println("--- Real Image Files Generated On Disk ---")
        println("Payment PNG: ${paymentFile.absolutePath} (${paymentFile.length() / 1024} KB)")
        println("Payment Duplicate PNG: ${paymentDuplicateFile.length() / 1024} KB")
        println("Ticket PNG: ${ticketFile.length() / 1024} KB")
        println("Chat PNG: ${chatFile.length() / 1024} KB")
        println("Wallpaper PNG: ${wallpaperFile.length() / 1024} KB")
    }

    // =========================================================================
    // 2. Real Image Visual Hash (dHash) & Duplicate Detection
    // =========================================================================
    @Test
    fun testRealImagePerceptualDHashCalculation() {
        val paymentImg = ImageIO.read(paymentFile)
        val duplicateImg = ImageIO.read(paymentDuplicateFile)
        val ticketImg = ImageIO.read(ticketFile)
        val wallpaperImg = ImageIO.read(wallpaperFile)

        val hashPayment = computeRealImageDHash(paymentImg)
        val hashDuplicate = computeRealImageDHash(duplicateImg)
        val hashTicket = computeRealImageDHash(ticketImg)
        val hashWallpaper = computeRealImageDHash(wallpaperImg)

        val distDuplicate = VisualHashCalculator.hammingDistance(hashPayment, hashDuplicate)
        val distTicket = VisualHashCalculator.hammingDistance(hashPayment, hashTicket)
        val distWallpaper = VisualHashCalculator.hammingDistance(hashPayment, hashWallpaper)

        println("--- Real Image dHash Results ---")
        println("Real Payment Hash: 0x${java.lang.Long.toHexString(hashPayment).uppercase()}")
        println("Real Duplicate Hash: 0x${java.lang.Long.toHexString(hashDuplicate).uppercase()}")
        println("Real Ticket Hash: 0x${java.lang.Long.toHexString(hashTicket).uppercase()}")
        println("Real Wallpaper Hash: 0x${java.lang.Long.toHexString(hashWallpaper).uppercase()}")
        println("Distance (Payment vs Duplicate): $distDuplicate bits")
        println("Distance (Payment vs Ticket): $distTicket bits")
        println("Distance (Payment vs Wallpaper): $distWallpaper bits")

        // Assert near-duplicate: same screenshot with minor timestamp change has distance <= 3
        assertTrue(
            "Real Payment vs Duplicate must be <= 3 bits (was $distDuplicate)",
            VisualHashCalculator.isDuplicate(hashPayment, hashDuplicate, threshold = 3)
        )

        // Assert distinct: Payment vs Ticket has large Hamming distance
        assertTrue(
            "Real Payment vs Ticket must be > 3 bits (was $distTicket)",
            distTicket > 3
        )
        assertFalse(VisualHashCalculator.isDuplicate(hashPayment, hashTicket, threshold = 3))
    }

    // =========================================================================
    // 3. Real Image End-to-End Extraction & Categorization
    // =========================================================================
    @Test
    fun testRealScreenshotTextExtractionAndClassification() {
        // Text rendered on the real Payment screenshot
        val paymentOcrText = """
            Google Pay
            Payment Successful
            ✓
            ₹1,450.00
            Paid to Blue Tokai Coffee Roasters
            UPI ID: cafe.bistro@icici
            UPI Ref No: 482910394819
            Date: Sep 27, 2026, 10:30 AM
        """.trimIndent()

        val paymentEntities = CategoryClassifier.extractEntities(paymentOcrText)
        assertEquals(listOf("cafe.bistro@icici"), paymentEntities.upiIds)
        assertTrue(paymentEntities.amounts.contains("₹1,450.00"))
        assertEquals(Category.PAYMENT, CategoryClassifier.classify(paymentOcrText, paymentEntities))

        // Text rendered on the real Ticket screenshot
        val ticketOcrText = """
            IRCTC Electronic Reservation
            Indian Railways Passenger Ticket
            PNR: 8492019482
            12952 / MUMBAI RAJDHANI
            From: NDLS To: MMCT
            Class: 3A | Quota: General
            Passenger: Rajesh Kumar | Coach: B4 Berth: 23 (LB)
            Total Fare: ₹2,450.00 | Status: Confirmed
        """.trimIndent()

        val ticketEntities = CategoryClassifier.extractEntities(ticketOcrText)
        assertEquals(listOf("8492019482"), ticketEntities.pnrNumbers)
        assertEquals(Category.TICKET, CategoryClassifier.classify(ticketOcrText, ticketEntities))

        // Text rendered on the real Chat screenshot
        val chatOcrText = """
            WhatsApp
            Rajesh Sharma
            online
            Hey, please check the repository documentation:
            https://github.com/android/architecture-samples
            today at 10:42 AM
            Thanks! Call me at +91 9876543210 if needed.
            10:43 AM delivered
        """.trimIndent()

        val chatEntities = CategoryClassifier.extractEntities(chatOcrText)
        assertTrue(chatEntities.urls.contains("https://github.com/android/architecture-samples"))
        assertTrue(chatEntities.phoneNumbers.any { it.contains("9876543210") })
        assertEquals(Category.CHAT, CategoryClassifier.classify(chatOcrText, chatEntities))
    }

    // =========================================================================
    // 4. Real Image Deduplication Clustering & Reclaimable Space Calculation
    // =========================================================================
    @Test
    fun testRealImageDeduplicationClusterWithDiskSizes() {
        val paymentImg = ImageIO.read(paymentFile)
        val duplicateImg = ImageIO.read(paymentDuplicateFile)
        val ticketImg = ImageIO.read(ticketFile)

        val hashPayment = computeRealImageDHash(paymentImg)
        val hashDuplicate = computeRealImageDHash(duplicateImg)
        val hashTicket = computeRealImageDHash(ticketImg)

        val s1 = Screenshot(
            id = 101L,
            uriString = paymentFile.toURI().toString(),
            filePath = paymentFile.absolutePath,
            dateAdded = paymentFile.lastModified(),
            width = paymentImg.width,
            height = paymentImg.height,
            fileSize = paymentFile.length(),
            rawOcrText = "Google Pay ₹1,450.00 cafe.bistro@icici",
            redactedOcrText = "Google Pay ₹1,450.00 cafe.bistro@icici",
            category = Category.PAYMENT,
            visualHash = hashPayment,
            processedAt = System.currentTimeMillis()
        )

        val s2 = Screenshot(
            id = 102L,
            uriString = paymentDuplicateFile.toURI().toString(),
            filePath = paymentDuplicateFile.absolutePath,
            dateAdded = paymentDuplicateFile.lastModified() + 60000L,
            width = duplicateImg.width,
            height = duplicateImg.height,
            fileSize = paymentDuplicateFile.length(),
            rawOcrText = "Google Pay ₹1,450.00 cafe.bistro@icici",
            redactedOcrText = "Google Pay ₹1,450.00 cafe.bistro@icici",
            category = Category.PAYMENT,
            visualHash = hashDuplicate,
            processedAt = System.currentTimeMillis()
        )

        val s3 = Screenshot(
            id = 103L,
            uriString = ticketFile.toURI().toString(),
            filePath = ticketFile.absolutePath,
            dateAdded = ticketFile.lastModified(),
            width = ticketImg.width,
            height = ticketImg.height,
            fileSize = ticketFile.length(),
            rawOcrText = "IRCTC PNR: 8492019482 12952 RAJDHANI",
            redactedOcrText = "IRCTC PNR: 8492019482 12952 RAJDHANI",
            category = Category.TICKET,
            visualHash = hashTicket,
            processedAt = System.currentTimeMillis()
        )

        val realScreenshots = listOf(s1, s2, s3)
        val visited = mutableSetOf<Long>()
        val duplicateGroups = mutableListOf<DuplicateGroup>()

        for (i in realScreenshots.indices) {
            val primary = realScreenshots[i]
            if (visited.contains(primary.id)) continue

            val duplicates = mutableListOf<Screenshot>()
            var maxDist = 0

            for (j in (i + 1) until realScreenshots.size) {
                val candidate = realScreenshots[j]
                if (visited.contains(candidate.id)) continue

                val dist = VisualHashCalculator.hammingDistance(primary.visualHash, candidate.visualHash)
                if (dist <= 3) {
                    duplicates.add(candidate)
                    visited.add(candidate.id)
                    if (dist > maxDist) maxDist = dist
                }
            }

            if (duplicates.isNotEmpty()) {
                visited.add(primary.id)
                duplicateGroups.add(DuplicateGroup(primary, duplicates, maxDist))
            }
        }

        println("Duplicate clusters detected from real image files: ${duplicateGroups.size}")
        assertEquals(1, duplicateGroups.size)
        val group = duplicateGroups[0]
        assertEquals(s1.id, group.primary.id)
        assertEquals(1, group.duplicates.size)
        assertEquals(s2.id, group.duplicates[0].id)

        val reclaimableBytes = group.duplicates.sumOf { it.fileSize }
        println("Reclaimable storage from real duplicate image file: ${reclaimableBytes / 1024} KB")
        assertTrue("Reclaimable space should match real file size", reclaimableBytes > 0)
    }
}
