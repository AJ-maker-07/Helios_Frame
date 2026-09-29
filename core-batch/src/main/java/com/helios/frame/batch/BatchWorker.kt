package com.helios.frame.batch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.helios.frame.data.FileLogger
import com.helios.frame.data.MediaRepository
import com.helios.frame.engine.AndroidFrameRenderer
import com.helios.frame.engine.FrameCache
import com.helios.frame.engine.FrameLayoutCalculator
import com.helios.frame.engine.ScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

// WorkManager batch worker: survives rotation, Activity death, and backgrounding.
// Progress via setProgress(Data); UI observes WorkInfo. Cancel via WorkManager.cancelWorkById.

object BatchContract {
    const val KEY_FRAME_URI = "frame_uri"
    const val KEY_PHOTO_URIS = "photo_uris"
    const val KEY_OUTPUT_TREE_URI = "output_tree_uri" // optional; null → MediaStore folder
    const val KEY_SCALE_MODE = "scale_mode" // CENTER_CROP | FIT_CENTER | STRETCH
    const val KEY_JPEG_QUALITY = "jpeg_quality" // OPEN DETAIL default 92

    const val PROG_DONE = "done"
    const val PROG_TOTAL = "total"
    const val PROG_LAST_FILE = "last_file"

    const val OUT_SUCCEEDED = "succeeded"
    const val OUT_FAILED = "failed"
    const val OUT_FAILED_NAMES = "failed_names"

    const val CHANNEL_ID = "helios_batch"
    const val NOTIF_ID = 1001
}

class BatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        /**
         * Phase 3 watchdog: per-image decode → composite → encode budget.
         * Tunable in one place. 12s comfortably covers a 2048px image on a
         * mid-range device while preventing one stuck file from wedging a slot.
         */
        const val PER_IMAGE_TIMEOUT_MS = 12_000L

        /**
         * Testable core of the watchdog: runs [block] with [timeoutMs].
         * Throws [TimeoutCancellationException] on hang — caller records the
         * image as failed and continues. Unit-tested, no Android calls inside.
         */
        suspend fun <T> runWithTimeout(timeoutMs: Long, block: suspend () -> T): T =
            withTimeout(timeoutMs) { block() }
    }

    override suspend fun doWork(): Result {
        // Safety: Application.onCreate inits the logger, but a worker can run in
        // a fresh process — init is idempotent, so ensure a path exists.
        try { FileLogger.init(applicationContext) } catch (_: Throwable) { }
        val frameUri = inputData.getString(BatchContract.KEY_FRAME_URI)?.let(Uri::parse)
            ?: return Result.failure(errorData("Missing frame URI"))
        val photoStrings = inputData.getStringArray(BatchContract.KEY_PHOTO_URIS)
            ?: return Result.failure(errorData("Missing photo URIs"))
        if (photoStrings.isEmpty()) return Result.failure(errorData("Empty batch"))
        val treeUri = inputData.getString(BatchContract.KEY_OUTPUT_TREE_URI)?.let(Uri::parse)
        val mode = runCatching { ScaleMode.valueOf(inputData.getString(BatchContract.KEY_SCALE_MODE) ?: "CENTER_CROP") }
            .getOrDefault(ScaleMode.CENTER_CROP)
        val quality = inputData.getInt(BatchContract.KEY_JPEG_QUALITY, 92)

        setForeground(createForegroundInfo(0, photoStrings.size))

        val resolver = applicationContext.contentResolver
        val repo = MediaRepository(applicationContext)

        // Decode frame ONCE, reuse across all composites. Never re-decode per photo.
        val frameBitmap: Bitmap = try {
            FrameCache.decodeForReuse(resolver, frameUri)
        } catch (t: Throwable) {
            FileLogger.warn("HeliosBatch", "frame decode failed: ${t.message}")
            return Result.failure(errorData("Frame decode failed: ${t.message}"))
        }
        val cores = Runtime.getRuntime().availableProcessors()
        val parallelism = FrameLayoutCalculator.boundedParallelism(cores)
        val gate = Semaphore(parallelism) // bounded concurrency, NOT one coroutine per image unbounded
        val done = AtomicInteger(0)
        val succeeded = AtomicInteger(0)
        val failedNames = java.util.Collections.synchronizedList(mutableListOf<String>())
        val total = photoStrings.size
        FileLogger.log("HeliosBatch", "batch start total=$total parallelism=$parallelism")

        try {
            coroutineScope {
                photoStrings.mapIndexed { index, s ->
                    async(Dispatchers.Default) {
                        gate.acquire()
                        try {
                            // Cooperative cancel: stop scheduling new work quickly.
                            if (isStopped) return@async
                            // Phase 3 watchdog: a hung decode/composite/encode is
                            // cancelled at PER_IMAGE_TIMEOUT_MS, recorded as a
                            // failure, and the slot is freed for the next image.
                            runWithTimeout(PER_IMAGE_TIMEOUT_MS) {
                                processOne(
                                    photoUri = Uri.parse(s),
                                    frame = frameBitmap,
                                    mode = mode,
                                    quality = quality,
                                    treeUri = treeUri,
                                    repo = repo,
                                    index = index,
                                )
                            }
                            succeeded.incrementAndGet()
                        } catch (t: TimeoutCancellationException) {
                            val name = try { repo.displayName(Uri.parse(s)) } catch (_: Throwable) { s }
                            val msg = "${name ?: s}: timed out after ${PER_IMAGE_TIMEOUT_MS}ms, skipped"
                            failedNames.add(msg)
                            FileLogger.warn("HeliosBatch", "timeout index=$index $msg")
                        } catch (t: Throwable) {
                            // Skip-and-continue: one bad file never kills the batch.
                            val name = try { repo.displayName(Uri.parse(s)) } catch (_: Throwable) { s }
                            failedNames.add("${name ?: s}: ${t.message}")
                        } finally {
                            val d = done.incrementAndGet()
                            // Throttle progress writes: every item is fine up to a few hundred;
                            // for thousands, consider every N. Kept per-item per prompt spec.
                            setProgress(
                                Data.Builder()
                                    .putInt(BatchContract.PROG_DONE, d)
                                    .putInt(BatchContract.PROG_TOTAL, total)
                                    .putString(BatchContract.PROG_LAST_FILE, s)
                                    .build(),
                            )
                            updateNotification(d, total)
                            gate.release()
                        }
                    }
                }.awaitAll()
            }
        } finally {
            // LIFECYCLE: frame was shared across batch — recycle exactly once at the end.
            // On API 26+ recycle() is less critical (pixels on native heap auto-freed) but
            // explicit recycle bounds peak memory when user runs back-to-back batches.
            withContext(Dispatchers.IO) {
                if (!frameBitmap.isRecycled) frameBitmap.recycle()
            }
        }

        if (isStopped) {
            // Cancel support: completed images stay saved; report what finished.
            FileLogger.log(
                "HeliosBatch",
                "batch cancelled succeeded=${succeeded.get()} failed=${failedNames.size}",
            )
            return Result.success(resultData(succeeded.get(), failedNames, cancelled = true))
        }
        FileLogger.log(
            "HeliosBatch",
            "batch done succeeded=${succeeded.get()} failed=${failedNames.size}",
        )
        return Result.success(resultData(succeeded.get(), failedNames, cancelled = false))
    }

    private suspend fun processOne(
        photoUri: Uri,
        frame: Bitmap,
        mode: ScaleMode,
        quality: Int,
        treeUri: Uri?,
        repo: MediaRepository,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        // One image fully processed and released before next on a given permit:
        // decode sampled → composite → encode → write → free.
        val photo = FrameCache.decodeSampled(
            resolver = applicationContext.contentResolver,
            uri = photoUri,
            reqWidth = 2048, // OPEN DETAIL: target long edge; lower if OOM on test device.
            reqHeight = 2048,
        )
        var out: Bitmap? = null
        try {
            // No bulk full-res loading: photo is sampled; frame pre-scaled once.
            out = AndroidFrameRenderer.composite(photo, frame, mode)
            val base = (repo.displayName(photoUri)?.substringBeforeLast('.') ?: "photo-$index")
                .take(60).replace(Regex("[^A-Za-z0-9_-]+"), "_")
            // OPEN DETAIL: filename pattern framed_<base>_<index>.jpg — confirm or change.
            val fileName = "framed_${base}_$index"
            if (treeUri != null) {
                repo.saveToTreeUri(treeUri, out, "$fileName.jpg", Bitmap.CompressFormat.JPEG, quality)
            } else {
                repo.saveToHeliosFolder(out, fileName, Bitmap.CompressFormat.JPEG, quality)
            }
        } finally {
            // LIFECYCLE: free per-image bitmaps immediately; never hold N full images.
            if (!photo.isRecycled) photo.recycle()
            val o = out
            if (o != null && !o.isRecycled) o.recycle()
        }
    }

    // --- notifications (foreground service for long batches) ---

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val notif = notifBuilder("Processing $done of $total", done, total).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                BatchContract.NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(BatchContract.NOTIF_ID, notif)
        }
    }

    private suspend fun updateNotification(done: Int, total: Int) {
        try {
            // setForeground may throw if worker already finishing; ignore.
            setForeground(createForegroundInfo(done, total))
        } catch (_: Throwable) { }
    }

    private fun notifBuilder(text: String, done: Int, total: Int): NotificationCompat.Builder {
        return NotificationCompat.Builder(applicationContext, BatchContract.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Helios Frame batch")
            .setContentText(text)
            .setProgress(total, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = applicationContext.getSystemService(NotificationManager::class.java)
            if (mgr?.getNotificationChannel(BatchContract.CHANNEL_ID) == null) {
                mgr?.createNotificationChannel(
                    NotificationChannel(
                        BatchContract.CHANNEL_ID,
                        "Helios batch export",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    private fun resultData(succeeded: Int, failed: List<String>, cancelled: Boolean): Data {
        return Data.Builder()
            .putInt(BatchContract.OUT_SUCCEEDED, succeeded)
            .putInt(BatchContract.OUT_FAILED, failed.size)
            .putStringArray(BatchContract.OUT_FAILED_NAMES, failed.toTypedArray())
            .putBoolean("cancelled", cancelled)
            .build()
    }

    private fun errorData(msg: String): Data =
        Data.Builder().putString("error", msg).build()
}
