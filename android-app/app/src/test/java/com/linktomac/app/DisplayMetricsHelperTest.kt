package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayMetricsHelperTest {

    @Test
    fun testAlign16_alwaysProducesMultiplesOf16() {
        val testValues = intArrayOf(320, 720, 1080, 1440, 1600, 1920, 2400, 3120)
        for (dim in testValues) {
            val aligned = DisplayMetricsHelper.align16(dim)
            assertEquals(0, aligned % 16)
            assertTrue(aligned >= DisplayMetricsHelper.MIN_STREAM_DIMENSION)
        }
    }

    @Test
    fun testCalculateStreamDimensions_preservesBoundsAndAspectRatios() {
        // Standard 9:16 phone: 1080 x 1920 -> scaled down so maxEdge <= 1600
        val (w1, h1) = DisplayMetricsHelper.calculateStreamDimensions(1080, 1920, maxDimension = 1600)
        assertEquals(0, w1 % 16)
        assertEquals(0, h1 % 16)
        assertTrue(h1 <= 1600)
        // Aspect ratio: 1080 / 1920 = 0.5625; w1 / h1 should be within 1%
        val aspectOriginal1 = 1080.0 / 1920.0
        val aspectScaled1 = w1.toDouble() / h1.toDouble()
        assertTrue(Math.abs(aspectOriginal1 - aspectScaled1) < 0.02)

        // Tall 20:9 phone: 1080 x 2400
        val (w2, h2) = DisplayMetricsHelper.calculateStreamDimensions(1080, 2400, maxDimension = 1600)
        assertEquals(0, w2 % 16)
        assertEquals(0, h2 % 16)
        assertEquals(1600, h2)
        assertEquals(720, w2) // (1080 * 1600 / 2400) = 720, 720 % 16 == 0

        // Landscape tablet: 2560 x 1600
        val (w3, h3) = DisplayMetricsHelper.calculateStreamDimensions(2560, 1600, maxDimension = 1600)
        assertEquals(0, w3 % 16)
        assertEquals(0, h3 % 16)
        assertEquals(1600, w3)
        assertEquals(1008, h3) // (1600 * 1600 / 2560) = 1000 -> round(1000/16)*16 = 1008
    }

    @Test
    fun testCalculateStreamDimensions_alreadySmallScreenNotUpscaled() {
        val (w, h) = DisplayMetricsHelper.calculateStreamDimensions(720, 1280, maxDimension = 1600)
        assertEquals(720, w)
        assertEquals(1280, h)
        assertEquals(0, w % 16)
        assertEquals(0, h % 16)
    }
}
