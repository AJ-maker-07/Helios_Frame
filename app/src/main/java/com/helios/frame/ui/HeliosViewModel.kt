package com.helios.frame.ui

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.helios.frame.engine.ScaleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val TAG_PICK = "HeliosPick"

// ViewModel holds StateFlow for UI state (idle / picking / processing / done / error).
// Survives rotation; batch itself survives Activity death via WorkManager (see BatchWorker).

sealed interface HeliosUiState {
    data object Idle : HeliosUiState
    data class Ready(val frameUri: Uri?, val photos: List<Uri>, val outputTreeUri: Uri?) : HeliosUiState
    data class Processing(val workId: UUID, val done: Int, val total: Int, val lastFile: String?) : HeliosUiState
    data class Done(
        val succeeded: Int,
        val failed: Int,
        val failedNames: List<String>,
        val cancelled: Boolean,
    ) : HeliosUiState
    data class Error(val message: String) : HeliosUiState
}

class HeliosViewModel : ViewModel() {

    private val _state = MutableStateFlow<HeliosUiState>(HeliosUiState.Idle)
    val state: StateFlow<HeliosUiState> = _state.asStateFlow()

    /**
     * Observed selection state. The Compose UI collects this directly, so placeholder
     * conditions and previews recompose on every real change — independent of the
     * batch state machine below (which legitimately suppresses emissions while
     * Processing or after Done/Error).
     */
    private val _selection = MutableStateFlow(Selection())
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    var frameUri: Uri? = null
        private set
    // SnapshotStateList: removals are targeted operations, so lazy consumers with
    // stable keys (e.g. the photo grid popup) don't recompose every surviving cell.
    val photoUris = mutableStateListOf<Uri>()
    var outputTreeUri: Uri? = null // null → MediaStore Pictures/HeliosFrame
        private set
    var scaleMode: ScaleMode = ScaleMode.CENTER_CROP // OPEN DETAIL default
        private set
    var currentWorkId: UUID? = null
        private set

    // Preview cycling: index into photoUris for the Preview Section.
    // Defaults to the first selected photo; arrows cycle when >1 selected.
    private val _previewIndex = MutableStateFlow(0)
    val previewIndex: StateFlow<Int> = _previewIndex.asStateFlow()

    fun setFrame(uri: Uri?) {
        frameUri = uri
        publishSelection()
        Log.d(TAG_PICK, "setFrame uri=$uri")
        publishReady()
    }

    fun setPhotos(uris: List<Uri>) {
        // Cap defensively; OPEN DETAIL: current cap 1000. Confirm max batch size.
        photoUris.clear()
        photoUris.addAll(uris.take(1000))
        // Clamp preview index to the new list; default to first photo.
        _previewIndex.value = 0
        publishSelection()
        Log.d(TAG_PICK, "setPhotos count=${photoUris.size}")
        publishReady()
    }

    /** Remove one photo from the queue (grid popup). Keeps the preview on a valid item. */
    fun removePhoto(uri: Uri) {
        val idx = photoUris.indexOf(uri)
        if (idx < 0) return
        photoUris.removeAt(idx)
        val n = photoUris.size
        _previewIndex.value = when {
            n == 0 -> 0
            idx < _previewIndex.value -> _previewIndex.value - 1
            _previewIndex.value >= n -> n - 1 // removed active (or later) item at the end → step back
            else -> _previewIndex.value // removed active item → same index now shows the next photo
        }
        publishSelection()
        Log.d(TAG_PICK, "removePhoto remaining=$n previewIndex=${_previewIndex.value}")
    }

    /** Set the active preview photo (grid popup tap-to-select). */
    fun setPreviewPhoto(uri: Uri) {
        val idx = photoUris.indexOf(uri)
        if (idx >= 0) {
            _previewIndex.value = idx
            Log.d(TAG_PICK, "setPreviewPhoto index=$idx")
        }
    }

    fun setOutputTree(uri: Uri?) {
        outputTreeUri = uri
        publishSelection()
        Log.d(TAG_PICK, "setOutputTree uri=$uri")
        publishReady()
    }

    fun setScaleMode(mode: ScaleMode) {
        scaleMode = mode
        // Emit so the exact-composite preview recomputes on mode change.
        publishSelection()
        Log.d(TAG_PICK, "setScaleMode mode=$mode")
        publishReady()
    }

    fun onEnqueued(workId: UUID, total: Int) {
        currentWorkId = workId
        _state.value = HeliosUiState.Processing(workId, 0, total, null)
    }

    fun onProgress(done: Int, total: Int, lastFile: String?) {
        val cur = _state.value as? HeliosUiState.Processing ?: return
        _state.value = cur.copy(done = done, total = total, lastFile = lastFile)
    }

    fun onDone(succeeded: Int, failed: Int, failedNames: List<String>, cancelled: Boolean) {
        _state.value = HeliosUiState.Done(succeeded, failed, failedNames, cancelled)
    }

    fun onError(msg: String) {
        _state.value = HeliosUiState.Error(msg)
    }

    fun resetToReady() {
        publishReady()
    }

    fun nextPreview() {
        if (photoUris.isEmpty()) return
        _previewIndex.value = (_previewIndex.value + 1) % photoUris.size
    }

    fun prevPreview() {
        if (photoUris.isEmpty()) return
        val n = photoUris.size
        _previewIndex.value = (_previewIndex.value - 1 + n) % n
    }

    private fun publishReady() {
        // Keep any terminal state until user explicitly resets; otherwise update Ready.
        val cur = _state.value
        if (cur is HeliosUiState.Processing) return
        if (cur is HeliosUiState.Done || cur is HeliosUiState.Error) return
        _state.value = HeliosUiState.Ready(frameUri, photoUris.toList(), outputTreeUri)
    }

    /** Snapshot the mutable selection into the observed [selection] flow. */
    private fun publishSelection() {
        _selection.value = Selection(
            frameUri = frameUri,
            photos = photoUris.toList(),
            outputTreeUri = outputTreeUri,
            scaleMode = scaleMode,
        )
    }
}

/** Everything the placeholder/preview/setup UI reads. Observed via [HeliosViewModel.selection]. */
data class Selection(
    val frameUri: Uri? = null,
    val photos: List<Uri> = emptyList(),
    val outputTreeUri: Uri? = null,
    val scaleMode: ScaleMode = ScaleMode.CENTER_CROP,
)
