package com.helios.frame.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.helios.frame.engine.AndroidFrameRenderer
import com.helios.frame.engine.FrameCache
import com.helios.frame.engine.ScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Preview compositing: uses the SAME AndroidFrameRenderer.composite path as the
// real export (BatchWorker.processOne) so the preview matches export output
// exactly — no approximate CSS-style overlay. Only the decode resolution differs
// (aggressively downscaled for live UI; long edge capped at PREVIEW_MAX_EDGE).

object PreviewCompositor {
    const val PREVIEW_MAX_EDGE = 600

    /**
     * Decode [photoUri] + [frameUri] sampled to [maxEdge] and composite with
     * [AndroidFrameRenderer.composite] (same function as export).
     * Returns a new Bitmap owned by the caller, or null if either side is missing.
     * Runs fully off the main thread. Null-safe: never throws for empty selection.
     */
    suspend fun loadCombinedPreview(
        resolver: ContentResolver,
        photoUri: Uri?,
        frameUri: Uri?,
        mode: ScaleMode,
        maxEdge: Int = PREVIEW_MAX_EDGE,
    ): Bitmap? {
        if (photoUri == null || frameUri == null) return null
        return withContext(Dispatchers.IO) {
            var photo: Bitmap? = null
            var frame: Bitmap? = null
            try {
                photo = FrameCache.decodeSampled(resolver, photoUri, maxEdge, maxEdge, maxEdge)
                frame = FrameCache.decodeForReuse(resolver, frameUri, maxEdge)
                // SAME compositing function as real export — exact match by construction.
                AndroidFrameRenderer.composite(photo, frame, mode)
            } catch (_: Throwable) {
                null
            } finally {
                // LIFECYCLE: inputs are preview-local decodes — recycle immediately.
                // The returned composite is a fresh Bitmap owned by the caller.
                try {
                    if (photo != null && !photo.isRecycled) photo.recycle()
                } catch (_: Throwable) { }
                try {
                    if (frame != null && !frame.isRecycled) frame.recycle()
                } catch (_: Throwable) { }
            }
        }
    }
}
