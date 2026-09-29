package com.helios.frame.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStream

// Repository layer: ALL MediaStore / SAF access lives here. No direct File-path assumptions
// (scoped storage → content URIs only). UI and workers call these, never ContentResolver directly.

data class PickedImage(val uri: Uri, val displayName: String?)

object UriPermissionHelper {
    fun takePersistable(contentResolver: ContentResolver, uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Photo Picker / MediaStore URIs don't need persistable grants; SAF ones do.
            // Logged (not swallowed): without persistence a later read can fail and
            // surface as a stuck placeholder/blank image instead of the real error.
            android.util.Log.w("HeliosPick", "takePersistable failed for $uri: ${e.message}")
        }
    }
}

class MediaRepository(private val context: Context) {

    fun displayName(uri: Uri): String? {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        // SAF fallback.
        return DocumentFile.fromSingleUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    /**
     * Save [bitmap] to shared storage.
     * Default: MediaStore Pictures/HeliosFrame (works on real device, no extra permission on API 29+).
     * OPEN DETAIL: if you choose SAF-folder output instead, use [saveToTreeUri] with the picked tree.
     *
     * @param format OPEN DETAIL default JPEG 92. Change to PNG if you answer output-format differently.
     */
    fun saveToHeliosFolder(
        bitmap: Bitmap,
        baseName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 92,
    ): Uri {
        val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val fileName = "$baseName.$ext"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (ext == "png") "image/png" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/HeliosFrame",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert failed for $fileName")
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(format, quality, out)) {
                throw IllegalStateException("Bitmap.compress failed for $fileName")
            }
        } ?: throw IllegalStateException("Cannot open output for $fileName")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    /**
     * Alternative SAF-tree output: write into a user-picked directory (ACTION_OPEN_DOCUMENT_TREE).
     * Persist the tree URI with takePersistableUriPermission before calling.
     */
    fun saveToTreeUri(
        treeUri: Uri,
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 92,
    ): Uri {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Invalid tree URI: $treeUri")
        val mime = if (fileName.endsWith(".png", true)) "image/png" else "image/jpeg"
        val file = dir.createFile(mime, fileName)
            ?: throw IllegalStateException("Cannot create $fileName in $treeUri")
        val out: OutputStream = context.contentResolver.openOutputStream(file.uri)
            ?: throw IllegalStateException("Cannot open ${file.uri}")
        out.use {
            if (!bitmap.compress(format, quality, it)) throw IllegalStateException("compress failed")
        }
        return file.uri
    }
}
