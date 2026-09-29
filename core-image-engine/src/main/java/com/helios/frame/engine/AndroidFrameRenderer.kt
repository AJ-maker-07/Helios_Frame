package com.helios.frame.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

// ANDROID EDGE — thin wrapper over Canvas/Bitmap. All math delegated to FrameLayoutCalculator.
// Performance rules enforced here:
// - No pixel-by-pixel loops; Canvas.drawBitmap only.
// - Caller must decode frame ONCE and reuse (see FrameCache); do not call with freshly
//   decoded frame per image in a batch.
// - Caller owns bitmap lifecycle: recycle() photo bitmap right after composite (see BatchWorker).

object AndroidFrameRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Composite [photo] under [frame] into a new output Bitmap sized to photo.
     * @param mode OPEN DETAIL default CENTER_CROP; mirrors FrameLayoutCalculator default.
     * NOTE: returned Bitmap must be recycled or written+recycled by caller on API 26+
     * recycling is less critical but still good hygiene in a hundreds-image batch.
     */
    fun composite(photo: Bitmap, frame: Bitmap, mode: ScaleMode = ScaleMode.CENTER_CROP): Bitmap {
        val layout = FrameLayoutCalculator.compute(
            photoSize = IntSize(photo.width, photo.height),
            frameSize = IntSize(frame.width, frame.height),
            mode = mode,
        )
        val out = Bitmap.createBitmap(
            layout.outputSize.width,
            layout.outputSize.height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(out)
        canvas.drawBitmap(
            photo,
            Rect(layout.photoSrc.left, layout.photoSrc.top, layout.photoSrc.right, layout.photoSrc.bottom),
            Rect(layout.photoDst.left, layout.photoDst.top, layout.photoDst.right, layout.photoDst.bottom),
            paint,
        )
        canvas.drawBitmap(
            frame,
            null,
            Rect(layout.frameDst.left, layout.frameDst.top, layout.frameDst.right, layout.frameDst.bottom),
            paint,
        )
        return out
    }

    /**
     * Preview variant capped at [maxEdge] px to keep UI smooth.
     * Decodes are expected to already be sampled; this only caps the output bitmap.
     */
    fun compositePreview(
        photo: Bitmap,
        frame: Bitmap,
        maxEdge: Int = 1080,
        mode: ScaleMode = ScaleMode.CENTER_CROP,
    ): Bitmap {
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(photo.width, photo.height))
        if (scale >= 1f) return composite(photo, frame, mode)
        val w = (photo.width * scale).toInt().coerceAtLeast(1)
        val h = (photo.height * scale).toInt().coerceAtLeast(1)
        val smallPhoto = Bitmap.createScaledBitmap(photo, w, h, true)
        val out = composite(smallPhoto, frame, mode)
        // LIFECYCLE: smallPhoto is an intermediate — recycle immediately to bound peak memory.
        if (smallPhoto !== photo && !smallPhoto.isRecycled) smallPhoto.recycle()
        return out
    }
}
