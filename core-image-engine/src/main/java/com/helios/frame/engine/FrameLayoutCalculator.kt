package com.helios.frame.engine

// PURE KOTLIN — zero Android imports. Tested with plain `./gradlew :core-image-engine:testDebugUnitTest`.
// Production compositing uses Canvas.drawBitmap (see AndroidFrameRenderer); this file only
// computes the geometry so the math is unit-testable without Robolectric.

/** How the photo maps into the frame's bounds. OPEN DETAIL default: CENTER_CROP. */
enum class ScaleMode {
    /** Photo fills output, center-cropped if aspect differs. Best for batch. */
    CENTER_CROP,
    /** Whole photo visible, letterboxed. No cropping. */
    FIT_CENTER,
    /** Stretch photo exactly to output size (may distort). */
    STRETCH,
}

data class IntSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Size must be positive: $width x $height" }
    }
}

/** Integer rect in output-pixel coordinates. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Describes how to draw the photo, then the frame on top.
 * @param photoSrc which region of the decoded photo to sample (for CENTER_CROP).
 * @param photoDst where to draw the photo in output space.
 * @param frameDst where to draw the frame (always full output for STRETCH/CENTER_CROP default).
 */
data class CompositeLayout(
    val outputSize: IntSize,
    val photoSrc: IntRect,
    val photoDst: IntRect,
    val frameDst: IntRect,
)

object FrameLayoutCalculator {

    /**
     * Pure function: given photo + frame intrinsic sizes and desired output, compute layout.
     * Default policy (change only if you answer the scaling question differently):
     * output = photo size; frame is scaled to exactly cover output (STRETCH of frame).
     */
    fun compute(
        photoSize: IntSize,
        frameSize: IntSize,
        mode: ScaleMode = ScaleMode.CENTER_CROP,
        // OPEN DETAIL: output defaults to photo size. If you want output = frame size, say so.
        outputSize: IntSize = photoSize,
    ): CompositeLayout {
        val photoSrc = when (mode) {
            ScaleMode.STRETCH, ScaleMode.FIT_CENTER -> IntRect(0, 0, photoSize.width, photoSize.height)
            ScaleMode.CENTER_CROP -> centerCropSrc(photoSize, outputSize)
        }
        val photoDst = when (mode) {
            ScaleMode.STRETCH, ScaleMode.CENTER_CROP ->
                IntRect(0, 0, outputSize.width, outputSize.height)
            ScaleMode.FIT_CENTER -> fitCenterDst(photoSize, outputSize)
        }
        // Frame always stretched to full output. Frame PNGs are expected to carry
        // transparency where the photo shows through.
        val frameDst = IntRect(0, 0, outputSize.width, outputSize.height)
        return CompositeLayout(outputSize, photoSrc, photoDst, frameDst)
    }

    private fun centerCropSrc(photo: IntSize, output: IntSize): IntRect {
        val photoAspect = photo.width.toFloat() / photo.height
        val outAspect = output.width.toFloat() / output.height
        return if (photoAspect > outAspect) {
            // Photo wider → crop sides.
            val srcW = (photo.height * outAspect).toInt().coerceAtLeast(1)
            val left = (photo.width - srcW) / 2
            IntRect(left, 0, left + srcW, photo.height)
        } else {
            // Photo taller → crop top/bottom.
            val srcH = (photo.width / outAspect).toInt().coerceAtLeast(1)
            val top = (photo.height - srcH) / 2
            IntRect(0, top, photo.width, top + srcH)
        }
    }

    private fun fitCenterDst(photo: IntSize, output: IntSize): IntRect {
        val scale = minOf(
            output.width.toFloat() / photo.width,
            output.height.toFloat() / photo.height,
        )
        val dw = (photo.width * scale).toInt().coerceAtLeast(1)
        val dh = (photo.height * scale).toInt().coerceAtLeast(1)
        val left = (output.width - dw) / 2
        val top = (output.height - dh) / 2
        return IntRect(left, top, left + dw, top + dh)
    }

    /**
     * Standard BitmapFactory power-of-2 sample-size math, kept pure for testing.
     * Returns inSampleSize such that decoded dims are >= req dims but minimal memory.
     */
    fun computeSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
        maxLongEdge: Int = 2048, // OPEN DETAIL: memory cap. Lower (e.g. 1600) if OOM on test device.
    ): Int {
        var reqW = reqWidth
        var reqH = reqHeight
        // Clamp request to maxLongEdge to bound memory for hundreds of images.
        val longEdge = maxOf(reqW, reqH)
        if (longEdge > maxLongEdge) {
            val s = maxLongEdge.toFloat() / longEdge
            reqW = (reqW * s).toInt().coerceAtLeast(1)
            reqH = (reqH * s).toInt().coerceAtLeast(1)
        }
        var inSampleSize = 1
        while ((srcWidth / (inSampleSize * 2) >= reqW) &&
            (srcHeight / (inSampleSize * 2) >= reqH)
        ) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /** CPU-aware parallelism bound. OPEN DETAIL: defaults to cores-1, min 2, max 8. */
    fun boundedParallelism(availableCores: Int = Runtime.getRuntime().availableProcessors()): Int {
        return (availableCores - 1).coerceIn(2, 8)
    }
}
