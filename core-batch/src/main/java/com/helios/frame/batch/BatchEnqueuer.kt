package com.helios.frame.batch

import android.content.Context
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.helios.frame.engine.ScaleMode
import java.util.UUID

// Thin enqueue/cancel helper so :app never constructs WorkRequests by hand.

object BatchEnqueuer {
    fun enqueue(
        context: Context,
        frameUri: Uri,
        photoUris: List<Uri>,
        outputTreeUri: Uri? = null, // null → MediaStore Pictures/HeliosFrame
        mode: ScaleMode = ScaleMode.CENTER_CROP, // OPEN DETAIL default
        jpegQuality: Int = 92, // OPEN DETAIL default
    ): UUID {
        val req = OneTimeWorkRequestBuilder<BatchWorker>()
            .setInputData(
                workDataOf(
                    BatchContract.KEY_FRAME_URI to frameUri.toString(),
                    BatchContract.KEY_PHOTO_URIS to photoUris.map { it.toString() }.toTypedArray(),
                    BatchContract.KEY_OUTPUT_TREE_URI to outputTreeUri?.toString(),
                    BatchContract.KEY_SCALE_MODE to mode.name,
                    BatchContract.KEY_JPEG_QUALITY to jpegQuality,
                ),
            )
            .addTag("helios-batch")
            .build()
        WorkManager.getInstance(context).enqueue(req)
        return req.id
    }

    fun cancel(context: Context, id: UUID) {
        WorkManager.getInstance(context).cancelWorkById(id)
    }
}
