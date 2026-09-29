package com.helios.frame.engine

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

// ANDROID EDGE — decode helpers. Frame decoded ONCE per batch and cached.

object FrameCache {

    /**
     * Decode frame PNG bounds only (no pixels) to get intrinsic size for layout math.
     */
    fun decodeBounds(resolver: ContentResolver, uri: Uri): IntSize {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val w = opts.outWidth.takeIf { it > 0 } ?: 512
        val h = opts.outHeight.takeIf { it > 0 } ?: 512
        return IntSize(w, h)
    }

    /**
     * Decode frame at a size suitable for [targetLongEdge] output reuse.
     * Call ONCE per batch; reuse returned Bitmap for every image.
     * LIFECYCLE: caller must recycle() when batch finishes/cancelled.
     */
    fun decodeForReuse(resolver: ContentResolver, uri: Uri, targetLongEdge: Int = 2048): Bitmap {
        val bounds = decodeBounds(resolver, uri)
        val sample = FrameLayoutCalculator.computeSampleSize(
            bounds.width, bounds.height, targetLongEdge, targetLongEdge, targetLongEdge,
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalArgumentException("Cannot decode frame URI: $uri")
        return bmp
    }

    /**
     * Decode a photo sampled to [reqWidth]x[reqHeight] to avoid full-res bulk loading.
     * LIFECYCLE: caller must recycle() after each composite.
     */
    fun decodeSampled(
        resolver: ContentResolver,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int,
        maxLongEdge: Int = 2048,
    ): Bitmap {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val srcW = boundsOpts.outWidth.takeIf { it > 0 } ?: reqWidth
        val srcH = boundsOpts.outHeight.takeIf { it > 0 } ?: reqHeight
        val sample = FrameLayoutCalculator.computeSampleSize(srcW, srcH, reqWidth, reqHeight, maxLongEdge)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalArgumentException("Cannot decode photo URI: $uri")
    }
}
