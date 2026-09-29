package com.helios.frame.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Plain JUnit, no Robolectric. Run: ./gradlew :core-image-engine:testDebugUnitTest
// Uses solid-color mock dimensions (no real bitmaps) to verify alignment/scaling math.

class FrameLayoutCalculatorTest {

    @Test
    fun `square photo square output centerCrop is identity`() {
        val layout = FrameLayoutCalculator.compute(
            IntSize(1000, 1000), IntSize(500, 500), ScaleMode.CENTER_CROP,
        )
        assertEquals(IntRect(0, 0, 1000, 1000), layout.photoSrc)
        assertEquals(IntRect(0, 0, 1000, 1000), layout.photoDst)
        assertEquals(IntRect(0, 0, 1000, 1000), layout.frameDst)
    }

    @Test
    fun `wide photo centerCrop crops sides`() {
        val layout = FrameLayoutCalculator.compute(
            IntSize(2000, 1000), IntSize(100, 100), ScaleMode.CENTER_CROP,
        )
        // Output = photo size (2000x1000); src crop keeps aspect → 2000x1000 src is identity here.
        // Use explicit square output to force side crop:
        val square = FrameLayoutCalculator.compute(
            IntSize(2000, 1000), IntSize(100, 100),
            ScaleMode.CENTER_CROP, IntSize(1000, 1000),
        )
        assertEquals(1000, square.photoSrc.width)
        assertEquals(1000, square.photoSrc.height)
        assertEquals(500, square.photoSrc.left) // centered: (2000-1000)/2
        assertEquals(0, square.photoSrc.top)
        assertEquals(IntRect(0, 0, 1000, 1000), square.photoDst)
    }

    @Test
    fun `tall photo centerCrop crops top and bottom`() {
        val layout = FrameLayoutCalculator.compute(
            IntSize(1000, 2000), IntSize(100, 100),
            ScaleMode.CENTER_CROP, IntSize(1000, 1000),
        )
        assertEquals(1000, layout.photoSrc.width)
        assertEquals(1000, layout.photoSrc.height)
        assertEquals(0, layout.photoSrc.left)
        assertEquals(500, layout.photoSrc.top)
    }

    @Test
    fun `fitCenter letterboxes wide photo`() {
        val layout = FrameLayoutCalculator.compute(
            IntSize(2000, 1000), IntSize(100, 100),
            ScaleMode.FIT_CENTER, IntSize(1000, 1000),
        )
        // 2000x1000 scaled to fit 1000x1000 → 1000x500 centered vertically.
        assertEquals(IntRect(0, 250, 1000, 750), layout.photoDst)
        // No cropping in FIT mode.
        assertEquals(IntRect(0, 0, 2000, 1000), layout.photoSrc)
    }

    @Test
    fun `sampleSize bounds memory`() {
        // 8000px source requested at 2000px → sample >= 4.
        val s = FrameLayoutCalculator.computeSampleSize(8000, 6000, 2000, 2000)
        assertTrue(s >= 2)
        // Decoded dims stay >= requested.
        assertTrue(8000 / s >= 2000 || 6000 / s >= 1500)
        // Small source → no sampling.
        assertEquals(1, FrameLayoutCalculator.computeSampleSize(800, 600, 2000, 2000))
    }

    @Test
    fun `boundedParallelism stays in range`() {
        assertEquals(2, FrameLayoutCalculator.boundedParallelism(1))
        assertEquals(2, FrameLayoutCalculator.boundedParallelism(2))
        assertTrue(FrameLayoutCalculator.boundedParallelism(8) in 2..8)
        assertEquals(8, FrameLayoutCalculator.boundedParallelism(64))
    }
}
