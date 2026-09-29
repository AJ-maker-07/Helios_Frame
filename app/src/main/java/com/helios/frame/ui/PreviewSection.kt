package com.helios.frame.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.helios.frame.engine.ScaleMode
import kotlinx.coroutines.delay

private val PreviewBoxBg = Color(0xFFF3F0E9)
private val PreviewInk = Color(0xFF1F2225)
private val PreviewSub = Color(0xFF5C6268)
private val PreviewAccent = Color(0xFFC2410C)
private val PreviewAccentDark = Color(0xFF9A3412)
private val PreviewHairline = Color(0x1F1F2225)
private val CheckerA = Color(0xFFE3E0D8)
private val CheckerB = Color(0xFFFFFFFF)

/**
 * Preview Section (main screen, above the Export button).
 * - Left: current photo thumbnail w/ next/prev cycling (defaults to first).
 * - Right: frame PNG alone on a checkered (transparency) background.
 * - Below: combined preview via the SAME composite fn as export ([PreviewCompositor]).
 * Recomputes only when the selected photo / frame / scale mode changes
 * (LaunchedEffect keys), downscaled to ~600px long edge, with debounce so rapid
 * next/prev taps cancel redundant composites. Placeholders — never blank/crash.
 */
@Composable
fun PreviewSection(
    photoUri: Uri?,
    frameUri: Uri?,
    scaleMode: ScaleMode,
    photoPosition: Int, // 1-based for label; 0 when empty
    photoTotal: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "PREVIEW",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = PreviewSub,
        )
        // Side-by-side on normal widths, stacked on narrow screens.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 360.dp
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoBox(photoUri, photoPosition, photoTotal, onNext, onPrev, onOpenGrid, Modifier.fillMaxWidth())
                    FrameBox(frameUri, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoBox(photoUri, photoPosition, photoTotal, onNext, onPrev, onOpenGrid, Modifier.weight(1f))
                    FrameBox(frameUri, Modifier.weight(1f))
                }
            }
        }
        CombinedPreviewBox(photoUri = photoUri, frameUri = frameUri, scaleMode = scaleMode)
    }
}

@Composable
private fun PhotoBox(
    photoUri: Uri?,
    position: Int,
    total: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PreviewBoxBg)
                .border(1.dp, PreviewHairline, RoundedCornerShape(6.dp))
                // Tapping opens the photo grid popup (manage / remove / pick preview).
                .clickable(enabled = photoUri != null, onClick = onOpenGrid),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri == null) {
                PreviewPlaceholder("No photo yet", "Pick photos to preview them here.")
            } else {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Selected photo preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Cycling arrows — only when more than one photo is selected.
                if (total > 1) {
                    Text(
                        "‹",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .background(Color(0x80000000), RoundedCornerShape(6.dp))
                            .clickable(onClick = onPrev)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Text(
                        "›",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(Color(0x80000000), RoundedCornerShape(6.dp))
                            .clickable(onClick = onNext)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Text(
            when {
                photoUri == null -> "Photo"
                total > 1 -> "Photo $position of $total — tap to manage"
                else -> "Photo 1 of 1 — tap to manage"
            },
            fontSize = 12.sp,
            color = PreviewSub,
        )
    }
}

@Composable
private fun FrameBox(frameUri: Uri?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, PreviewHairline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (frameUri == null) {
                Box(
                    Modifier.fillMaxSize().background(PreviewBoxBg),
                    contentAlignment = Alignment.Center,
                ) {
                    PreviewPlaceholder("No frame yet", "Pick a PNG frame to see its shape.")
                }
            } else {
                // Checkered transparency background so the frame's shape/borders read alone.
                Canvas(Modifier.fillMaxSize()) {
                    val cell = 24f
                    var y = 0f
                    var row = 0
                    while (y < size.height) {
                        var x = 0f
                        var col = row
                        while (x < size.width) {
                            drawRect(
                                color = if (col % 2 == 0) CheckerA else CheckerB,
                                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(
                                    cell.coerceAtMost(size.width - x),
                                    cell.coerceAtMost(size.height - y),
                                ),
                            )
                            x += cell
                            col++
                        }
                        y += cell
                        row++
                    }
                }
                AsyncImage(
                    model = frameUri,
                    contentDescription = "Selected frame on transparency",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        }
        Text("Frame", fontSize = 12.sp, color = PreviewSub)
    }
}

@Composable
private fun CombinedPreviewBox(photoUri: Uri?, frameUri: Uri?, scaleMode: ScaleMode) {
    val ctx = LocalContext.current
    var bitmap: Bitmap? by remember { mutableStateOf(null) }
    var loading by remember { mutableStateOf(false) }

    // Recompute ONLY on photo / frame / mode change — not on every recomposition.
    // Debounce: small delay + LaunchedEffect restart cancels redundant composites
    // when the user taps next/prev rapidly.
    LaunchedEffect(photoUri, frameUri, scaleMode) {
        // Recycle the outgoing bitmap once replaced (handled below on new value).
        val old = bitmap
        bitmap = null
        try {
            if (old != null && !old.isRecycled) old.recycle()
        } catch (_: Throwable) { }
        if (photoUri == null || frameUri == null) {
            Log.d("HeliosPreview", "combined skipped photoNull=${photoUri == null} frameNull=${frameUri == null}")
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(180)
        val out = PreviewCompositor.loadCombinedPreview(
            resolver = ctx.contentResolver,
            photoUri = photoUri,
            frameUri = frameUri,
            mode = scaleMode,
        )
        Log.d("HeliosPreview", "combined loaded ${out?.width}x${out?.height} mode=$scaleMode")
        bitmap = out
        loading = false
    }
    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose {
            try {
                if (current != null && !current.isRecycled) current.recycle()
            } catch (_: Throwable) { }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PreviewBoxBg)
                .border(1.dp, PreviewHairline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PreviewAccent,
                            strokeWidth = 2.dp,
                        )
                        Text("Compositing preview…", fontSize = 13.sp, color = PreviewSub)
                    }
                }
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Composited export preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                }
                else -> {
                    PreviewPlaceholder(
                        title = if (photoUri == null && frameUri == null) "Nothing to preview yet"
                        else if (photoUri == null) "No photo yet"
                        else "No frame yet",
                        subtitle = "Pick a frame and photos — the exact export composite shows here.",
                    )
                }
            }
        }
        Text(
            "Export preview — exact composite, downscaled",
            fontSize = 12.sp,
            color = if (bitmap != null) PreviewAccentDark else PreviewSub,
            fontWeight = if (bitmap != null) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PreviewPlaceholder(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PreviewInk)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = PreviewSub)
        Spacer(Modifier.width(0.dp))
    }
}
