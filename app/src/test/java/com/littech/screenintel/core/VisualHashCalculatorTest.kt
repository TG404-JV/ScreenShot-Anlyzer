package com.littech.screenintel.core

import com.littech.screenintel.core.hasher.VisualHashCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualHashCalculatorTest {

    @Test
    fun testIdenticalHashesHaveZeroDistance() {
        val hash = 0x123456789ABCDEF0L
        val distance = VisualHashCalculator.hammingDistance(hash, hash)
        assertEquals(0, distance)
        assertTrue(VisualHashCalculator.isDuplicate(hash, hash))
    }

    @Test
    fun testOneBitDifferenceHasDistanceOne() {
        val hash1 = 0x123456789ABCDEF0L
        val hash2 = 0x123456789ABCDEF1L // differs in lowest bit
        val distance = VisualHashCalculator.hammingDistance(hash1, hash2)
        assertEquals(1, distance)
        assertTrue(VisualHashCalculator.isDuplicate(hash1, hash2, threshold = 3))
    }

    @Test
    fun testThreeBitDifferenceIsFlaggedAsDuplicate() {
        val hash1 = 0b0000_0000L
        val hash2 = 0b0000_0111L // 3 bits flipped
        val distance = VisualHashCalculator.hammingDistance(hash1, hash2)
        assertEquals(3, distance)
        assertTrue(VisualHashCalculator.isDuplicate(hash1, hash2, threshold = 3))
    }

    @Test
    fun testFourBitDifferenceIsNotDuplicateAtThresholdThree() {
        val hash1 = 0b0000_0000L
        val hash2 = 0b0000_1111L // 4 bits flipped
        val distance = VisualHashCalculator.hammingDistance(hash1, hash2)
        assertEquals(4, distance)
        assertFalse(VisualHashCalculator.isDuplicate(hash1, hash2, threshold = 3))
    }

    @Test
    fun testPigeonholeChunkMatchingForDistanceThree() {
        // 64-bit hash split into 4x16-bit blocks (chunks 0..3)
        // Flip 1 bit in chunk 0 (bit 60), 1 bit in chunk 1 (bit 40), 1 bit in chunk 2 (bit 20)
        // Chunk 3 (bits 0..15) remains 100% identical!
        val baseHash = 0x1234_5678_9ABC_DEF0L
        val diffHash = baseHash xor (1L shl 60) xor (1L shl 40) xor (1L shl 20)
        val distance = VisualHashCalculator.hammingDistance(baseHash, diffHash)
        assertEquals(3, distance)
        assertTrue("Distance <= 3 must be detected as duplicate", VisualHashCalculator.isDuplicate(baseHash, diffHash, threshold = 3))

        // Verify that chunk 3 (bits 0..15) is an exact match
        val chunk3Base = baseHash.toInt() and 0xFFFF
        val chunk3Diff = diffHash.toInt() and 0xFFFF
        assertEquals(chunk3Base, chunk3Diff)
    }
}

