package com.helios.frame

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.helios.frame.batch.BatchContract
import com.helios.frame.batch.BatchEnqueuer
import com.helios.frame.data.FileLogger
import com.helios.frame.data.UriPermissionHelper
import com.helios.frame.engine.ScaleMode
import com.helios.frame.ui.ExportProgressDialog
import com.helios.frame.ui.HeliosUiState
import com.helios.frame.ui.HeliosViewModel
import com.helios.frame.ui.PhotoGridDialog
import com.helios.frame.ui.PreviewSection
import com.helios.frame.ui.Selection

// Direction C — Queue Stack (Convention Track, OJO app-ui-ux-best-practices).
// Evidence-locked register: quiet / polished-functional / medium density / solemn.
// Shell: Stack (Queue → Detail → Summary). Pattern: List + Detail drill-in.
// Tokens (visual-tokens structure; values derived from direction, 60/30/10):
//   60% base daylight paper #FAF8F4 · 30% support white/graphite hairlines
//   10% accent safety-orange #C2410C (throughput signal; text-safe 4.8:1 on paper)
//   Cool counter-hue: deep teal #0F766E for info links (anti one-note warm wash).
//   Error red reserved for failures only (semantic). Radius SHARP 6dp. Depth FLAT.
// Motion (motion-system, Convention: standard ease-out; every animation purpose-tested):
//   drill-in slide 300ms ease-out / exit 225ms = Continuity; press ripple = Feedback;
//   progress bar = Feedback. No scroll reveals, no loops, transform+opacity only.
// Per-row honesty rule: the worker reports only a global done-count + last file, so rows
// show failed/active/complete/queued WITHOUT inventing per-row completion order —
// unprocessed rows stay "queued" behind the counter during the run.

private val Paper = Color(0xFFFAF8F4)
private val PaperSupport = Color(0xFFF3F0E9)
private val Surface = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1F2225)
private val InkSecondary = Color(0xFF5C6268)
private val Hairline = Color(0x1F1F2225)
private val Accent = Color(0xFFC2410C) // safety-orange, deep variant for 4.5:1 text on paper
private val AccentDark = Color(0xFF9A3412)
private val AccentTint = Color(0xFFFBE9DC) // 8-12% tonal active pill
private val InfoTeal = Color(0xFF0F766E) // cool counter-hue, info links only
private val OkGreen = Color(0xFF1B7A4D)

private val HeliosColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentTint,
    onPrimaryContainer = AccentDark,
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = PaperSupport,
    onSurfaceVariant = InkSecondary,
    outline = Color(0xFF8A9096),
    error = Color(0xFFB3261E),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = HeliosColors) {
                HeliosFlow()
            }
        }
    }
}

/** In-app Stack destinations. Back supported on every page (layout anti-pattern rule). */
private sealed interface Screen {
    data object Queue : Screen
    data class Detail(val uri: Uri) : Screen
    data object Summary : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeliosFlow(vm: HeliosViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Selection is collected directly: placeholder conditions and previews below
    // read this, so they recompose on every real change (BUGFIX: they previously
    // read plain vars and only recomposed as a side effect of batch-state emission).
    val selection by vm.selection.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var screen: Screen by remember { mutableStateOf(Screen.Queue) }
    // Export popup visibility. The popup is driven by the same HeliosUiState batch
    // source — this flag only controls whether the dialog is on screen.
    var showExportDialog by remember { mutableStateOf(false) }
    // Phase 3.5: pre-batch memory warning (non-blocking). Set on Export tap when
    // the device reports memory pressure; the dialog below offers Export anyway.
    var memWarning by remember { mutableStateOf<String?>(null) }

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val framePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        Log.d("HeliosPick", "framePicker returned uri=$uri")
        if (uri != null) {
            UriPermissionHelper.takePersistable(ctx.contentResolver, uri)
            vm.setFrame(uri)
        }
    }
    val photosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        Log.d("HeliosPick", "photosPicker returned count=${uris.size}")
        uris.forEach { UriPermissionHelper.takePersistable(ctx.contentResolver, it) }
        vm.setPhotos(uris)
        screen = Screen.Queue
    }
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) { }
            vm.setOutputTree(uri)
        }
    }

    val workId = (state as? HeliosUiState.Processing)?.workId
    LaunchedEffect(workId) {
        if (workId == null) return@LaunchedEffect
        WorkManager.getInstance(ctx).getWorkInfoByIdFlow(workId).collect { info ->
            if (info == null) return@collect
            val p = info.progress
            val cur = vm.state.value as? HeliosUiState.Processing ?: return@collect
            vm.onProgress(
                p.getInt(BatchContract.PROG_DONE, cur.done),
                p.getInt(BatchContract.PROG_TOTAL, cur.total),
                p.getString(BatchContract.PROG_LAST_FILE),
            )
            if (info.state.isFinished) {
                val out = info.outputData
                if (info.state == WorkInfo.State.SUCCEEDED) {
                    vm.onDone(
                        succeeded = out.getInt(BatchContract.OUT_SUCCEEDED, cur.done),
                        failed = out.getInt(BatchContract.OUT_FAILED, 0),
                        failedNames = out.getStringArray(BatchContract.OUT_FAILED_NAMES)?.toList().orEmpty(),
                        cancelled = out.getBoolean("cancelled", false),
                    )
                } else if (info.state == WorkInfo.State.CANCELLED) {
                    vm.onDone(cur.done, 0, listOf("Batch cancelled by user."), cancelled = true)
                } else {
                    vm.onError(out.getString("error") ?: "Worker ${info.state}")
                }
                // Summary lives inside ExportProgressDialog (stays open until Done);
                // do not auto-navigate away — the dialog owns the result view.
            }
        }
    }

    BackHandler(enabled = screen != Screen.Queue) {
        screen = Screen.Queue
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (screen != Screen.Queue) {
                            Text(
                                "‹ ",
                                fontSize = 22.sp,
                                color = Accent,
                                modifier = Modifier.clickable { screen = Screen.Queue },
                            )
                        }
                        Column {
                            Text("Helios Frame", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                            Text(
                                when (screen) {
                                    Screen.Queue -> "Batch queue"
                                    is Screen.Detail -> "File detail"
                                    Screen.Summary -> "Run summary"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSecondary,
                            )
                        }
                    }
                },
                actions = {
                    if (selection.photos.isNotEmpty() && screen == Screen.Queue) {
                        // Tabular figures for the counter (visual-tokens: numeric data rule).
                        Text(
                            "${selection.photos.size}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentDark,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
        // Run bar docked at the bottom = primary action in the reachable third.
        // Export button: fixed at bottom, disabled until frame + >=1 photo selected.
        bottomBar = {
            if (screen == Screen.Queue) {
                RunBar(
                    state = state,
                    canRun = selection.frameUri != null && selection.photos.isNotEmpty(),
                    photoCount = selection.photos.size,
                    onRun = {
                        // Phase 3.5: pre-batch sanity check — warn, don't crash.
                        val warn = checkMemoryHeadroom(ctx)
                        if (warn != null) {
                            FileLogger.warn("HeliosMem", "pre-batch warning: $warn")
                            memWarning = warn
                        } else {
                            val total = selection.photos.size
                            val id = BatchEnqueuer.enqueue(
                                context = ctx,
                                frameUri = selection.frameUri!!,
                                photoUris = selection.photos,
                                outputTreeUri = selection.outputTreeUri,
                                mode = selection.scaleMode,
                            )
                            vm.onEnqueued(id, total)
                            // Tapping Export opens the popup and starts the batch job.
                            showExportDialog = true
                        }
                    },
                    onCancel = {
                        (state as? HeliosUiState.Processing)?.workId?.let { BatchEnqueuer.cancel(ctx, it) }
                    },
                )
            }
        },
    ) { inner ->
        // Drill-in transition: slide = Continuity purpose, 300/225ms (motion-system template).
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                slideInHorizontally(tween(300), initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(tween(225), targetOffsetX = { -it / 3 })
            },
            label = "stack-nav",
            modifier = Modifier.padding(inner),
        ) { dest ->
            when (dest) {
                Screen.Queue -> QueueScreen(
                    vm = vm,
                    state = state,
                    selection = selection,
                    onPickFrame = { framePicker.launch(arrayOf("image/png", "image/*")) },
                    onClearFrame = { vm.setFrame(null) },
                    onPickPhotos = { photosPicker.launch(arrayOf("image/*")) },
                    onPickOutput = { treePicker.launch(null) },
                    onBackToQueue = { vm.resetToReady(); screen = Screen.Queue },
                )
                is Screen.Detail -> DetailScreen(
                    photoUri = dest.uri,
                    frameUri = selection.frameUri,
                    position = selection.photos.indexOf(dest.uri) + 1,
                    total = selection.photos.size,
                    onBack = { screen = Screen.Queue },
                )
                Screen.Summary -> SummaryScreen(
                    state = state,
                    onBack = { vm.resetToReady(); screen = Screen.Queue },
                )
            }
        }
    }

    // Export popup: opens on Export tap, starts from the same batch state.
    // Stays open through completion until Done is tapped; never auto-closes.
    if (showExportDialog) {
        ExportProgressDialog(
            state = state,
            onCancel = {
                (state as? HeliosUiState.Processing)?.workId?.let { BatchEnqueuer.cancel(ctx, it) }
            },
            onDone = {
                showExportDialog = false
                if (state is HeliosUiState.Done || state is HeliosUiState.Error) {
                    vm.resetToReady()
                    screen = Screen.Queue
                }
            },
        )
    }

    // Phase 3.5 warning dialog: device under memory pressure. Non-blocking —
    // user can export anyway or cancel. Never auto-starts the batch.
    if (memWarning != null) {
        AlertDialog(
            onDismissRequest = { memWarning = null },
            title = { Text("Low memory warning") },
            text = {
                Text(
                    "${memWarning}\n\nExporting ${selection.photos.size} photos may be slow " +
                        "or fail partway. Finished files are always kept, failures are listed " +
                        "after the run.",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    FileLogger.log("HeliosMem", "user proceeded despite warning")
                    memWarning = null
                    val total = selection.photos.size
                    val id = BatchEnqueuer.enqueue(
                        context = ctx,
                        frameUri = selection.frameUri!!,
                        photoUris = selection.photos,
                        outputTreeUri = selection.outputTreeUri,
                        mode = selection.scaleMode,
                    )
                    vm.onEnqueued(id, total)
                    showExportDialog = true
                }) { Text("Export anyway") }
            },
            dismissButton = {
                TextButton(onClick = { memWarning = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Phase 3.5: pre-batch sanity check. Returns a warning message when the device
 * reports memory pressure, else null. Threshold: [ActivityManager.MemoryInfo.lowMemory]
 * or < 350MB available. Warning only — caller decides (dialog), never a crash.
 */
private fun checkMemoryHeadroom(context: Context): String? {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val availMb = info.availMem / (1024 * 1024)
        if (info.lowMemory) {
            "Device reports low memory (${availMb}MB available)."
        } else if (info.availMem < 350L * 1024 * 1024) {
            "Only ${availMb}MB memory available."
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }
}

// --- Queue: setup strip + flat status rows (List + Detail pattern) ---

@Composable
private fun QueueScreen(
    vm: HeliosViewModel,
    state: HeliosUiState,
    selection: Selection,
    onPickFrame: () -> Unit,
    onClearFrame: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickOutput: () -> Unit,
    onBackToQueue: () -> Unit,
) {
    val proc = state as? HeliosUiState.Processing
    val previewIndex by vm.previewIndex.collectAsStateWithLifecycle()
    // Read the SnapshotStateList directly: targeted snapshot reads, not the
    // Selection copy. Removal via removeAt() then notifies only the removed key.
    val previewUri = vm.photoUris.getOrNull(previewIndex)
    // Photo grid popup visibility. The grid is composed only while open.
    var showGrid by remember { mutableStateOf(false) }
    // Stable close callback so PhotoGridHost can skip recomposition on
    // unrelated parent updates (e.g. export progress). Captures the state
    // holder, not the boolean value.
    val closeGrid: () -> Unit = remember { { showGrid = false } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            SetupStrip(
                selection = selection,
                onPickFrame = onPickFrame,
                onClearFrame = onClearFrame,
                onPickPhotos = onPickPhotos,
                onPickOutput = onPickOutput,
                onScaleMode = vm::setScaleMode,
            )
            Spacer(Modifier.height(12.dp))
            // Preview Section (main screen, above the Export button):
            // photo + frame-alone + exact combined composite.
            PreviewSection(
                photoUri = previewUri,
                frameUri = selection.frameUri,
                scaleMode = selection.scaleMode,
                photoPosition = if (previewUri == null) 0 else previewIndex + 1,
                photoTotal = selection.photos.size,
                onNext = vm::nextPreview,
                onPrev = vm::prevPreview,
                onOpenGrid = { if (previewUri != null) showGrid = true },
            )
            Spacer(Modifier.height(12.dp))
            if (proc != null) {
                // Global progress = Feedback purpose. Detail lives in the Export popup.
                Text(
                    "Exporting — ${proc.done} of ${proc.total} complete",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = InkSecondary,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (proc.total > 0) proc.done / proc.total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = PaperSupport,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (selection.photos.isNotEmpty()) {
                Text(
                    "${selection.photos.size} photos queued — tap the photo preview to manage them.",
                    fontSize = 13.sp,
                    color = InkSecondary,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        if (selection.photos.isEmpty()) {
            item {
                // Empty state does work: value + action (anti-patterns: never "Nothing here").
                EmptyQueue(onPickPhotos = onPickPhotos)
            }
        }

        if (state is HeliosUiState.Error) {
            item {
                Spacer(Modifier.height(12.dp))
                // Three-part error: what + why + fix (anti-patterns copy formula).
                Text(
                    "Batch could not start. ${state.message}. Check the frame and photos, then try again.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBackToQueue) { Text("Reset") }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }

    // Photo grid popup: composed only while open, isolated from export-progress
    // recompositions (reads only photoUris + previewIndex, never `state`).
    // Separate from the Export popup.
    PhotoGridHost(vm = vm, showGrid = showGrid, onClose = closeGrid)
}

/**
 * Isolates the photo grid popup from QueueScreen's export-progress (`state`)
 * recompositions. This host reads only `vm.photoUris` (SnapshotStateList, so
 * removals are targeted mutations observed by key) + `previewIndex` for the
 * active highlight. Export `Processing(done/total)` updates recompose
 * QueueScreen but skip this host (params unchanged), preserving grid scroll
 * position and avoiding full-grid re-decode. Callbacks are remembered for
 * stability so selection flips recompose only the two affected cells.
 */
@Composable
private fun PhotoGridHost(vm: HeliosViewModel, showGrid: Boolean, onClose: () -> Unit) {
    if (!showGrid) return
    val previewIndex by vm.previewIndex.collectAsStateWithLifecycle()
    // Snapshot read: Compose tracks per-item keys, not the whole list object.
    val activeUri = vm.photoUris.getOrNull(previewIndex)
    val onRemove = remember(vm) { { uri: Uri -> vm.removePhoto(uri) } }
    val onSelect = remember(vm) { { uri: Uri -> vm.setPreviewPhoto(uri) } }
    PhotoGridDialog(
        photos = vm.photoUris,
        activeUri = activeUri,
        onSelect = { onSelect(it); onClose() },
        onRemove = onRemove,
        onDismiss = onClose,
    )
}

@Composable
private fun SetupStrip(
    selection: Selection,
    onPickFrame: () -> Unit,
    onClearFrame: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickOutput: () -> Unit,
    onScaleMode: (ScaleMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Compact setup rows: label left, control right. Sharp 6dp, flat, hairline borders.
        SetupRow(
            label = "Frame",
            value = selection.frameUri?.lastPathSegment?.takeLast(28) ?: "No frame picked",
            actionLabel = if (selection.frameUri == null) "Pick frame" else "Change",
            onAction = onPickFrame,
            onClear = if (selection.frameUri != null) onClearFrame else null,
        )
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        SetupRow(
            label = "Photos",
            value = if (selection.photos.isEmpty()) "No photos picked"
            else "${selection.photos.size} selected — tap any row to preview",
            actionLabel = if (selection.photos.isEmpty()) "Pick photos" else "Change",
            onAction = onPickPhotos,
            onClear = null,
        )
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        SetupRow(
            label = "Save to",
            value = if (selection.outputTreeUri == null) "Pictures / HeliosFrame" else "Chosen folder",
            actionLabel = "Folder",
            onAction = onPickOutput,
            onClear = null,
        )
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Fit", fontSize = 13.sp, color = InkSecondary)
            ScaleMode.entries.forEach { m ->
                FilterChip(
                    selected = selection.scaleMode == m,
                    onClick = { onScaleMode(m) },
                    label = { Text(m.name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentTint,
                        selectedLabelColor = AccentDark,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SetupRow(
    label: String,
    value: String,
    actionLabel: String,
    onAction: () -> Unit,
    onClear: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = InkSecondary)
            Text(
                value,
                fontSize = 14.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClear != null) {
            Text(
                "Clear",
                fontSize = 13.sp,
                color = InfoTeal, // cool counter-hue for secondary links
                modifier = Modifier.clickable(onClick = onClear).padding(8.dp),
            )
        }
        OutlinedButton(onClick = onAction) { Text(actionLabel, fontSize = 13.sp) }
    }
}

@Composable
private fun EmptyQueue(onPickPhotos: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your queue is empty", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(
            "Pick a frame and photos — each file lines up here with live status.",
            fontSize = 14.sp,
            color = InkSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onPickPhotos,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        ) { Text("Pick photos") }
    }
}

// --- Detail: drill-in file preview (Box overlay = its valid use) ---

@Composable
private fun DetailScreen(
    photoUri: Uri,
    frameUri: Uri?,
    position: Int,
    total: Int,
    onBack: () -> Unit,
) {
    var showFrame by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "File $position of $total",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = InkSecondary,
        )
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Photo $position of $total",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (frameUri != null && showFrame) {
                AsyncImage(
                    model = frameUri,
                    contentDescription = "Frame overlay on photo $position",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (frameUri == null) "No frame picked — photo only" else "True overlay preview",
                fontSize = 13.sp,
                color = InkSecondary,
            )
            if (frameUri != null) {
                OutlinedButton(onClick = { showFrame = !showFrame }) {
                    Text(if (showFrame) "Hide frame" else "Show frame", fontSize = 13.sp)
                }
            }
        }
        Text(
            photoUri.lastPathSegment?.substringAfterLast('/').orEmpty(),
            fontSize = 12.sp,
            color = InkSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(onClick = onBack) { Text("‹ Back to queue") }
    }
}

// --- Summary: counts + failures, verb-first CTA ---

@Composable
private fun SummaryScreen(state: HeliosUiState, onBack: () -> Unit) {
    val done = state as? HeliosUiState.Done
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (done == null) {
            Text("Run finished.", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        } else {
            // Messy-real numbers stay as reported; tabular figures for the count.
            Text(
                if (done.cancelled) "Batch stopped — ${done.succeeded} files kept"
                else "${done.succeeded} exported, ${done.failed} failed",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (done.failed > 0) MaterialTheme.colorScheme.error else Ink,
            )
            if (done.failedNames.isNotEmpty()) {
                Text("Failed files", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkSecondary)
                done.failedNames.take(50).forEach {
                    Text("• $it", fontSize = 13.sp, color = Ink)
                }
                if (done.failedNames.size > 50) Text("…and ${done.failedNames.size - 50} more", fontSize = 13.sp)
            } else if (!done.cancelled) {
                Text("Every file exported. Find them in Pictures / HeliosFrame.", fontSize = 14.sp, color = OkGreen)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Queue another batch") }
    }
}

// --- Run bar: reachable primary action (component-recipe 8-state mapping) ---
// Default/disabled via Button(enabled); press ripple = tactile Feedback;
// processing shows LinearProgress (loading state); error/success via Summary colors.

@Composable
private fun RunBar(
    state: HeliosUiState,
    canRun: Boolean,
    photoCount: Int,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (state) {
            is HeliosUiState.Processing -> {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop after current file")
                }
            }
            else -> {
                Button(
                    enabled = canRun,
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = PaperSupport,
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    // Verb + object CTA (copy rule): names the action, never "Submit".
                    Text(
                        "Export $photoCount photos",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!canRun) {
                    Text(
                        "Pick a frame and at least one photo to enable export.",
                        fontSize = 12.sp,
                        color = InkSecondary,
                    )
                }
            }
        }
    }
}
