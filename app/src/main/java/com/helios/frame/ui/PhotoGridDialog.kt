package com.helios.frame.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

private val GridInk = Color(0xFF1F2225)
private val GridSub = Color(0xFF5C6268)
private val GridAccent = Color(0xFFC2410C)
private val GridBoxBg = Color(0xFFF3F0E9)

/**
 * Photo-management popup, opened by tapping the preview box. Separate from the
 * Export popup. Composed only while open (caller gates with `if (showGrid)`).
 *
 * Performance (200+ photos):
 * - [LazyVerticalGrid] with stable URI keys + contentType so removals are
 *   targeted and surviving cells are skipped, not recomposed.
 * - Thumbnails are Coil [AsyncImage] with a fixed small [ImageRequest.size]
 *   (~150px) served from Coil's memory cache — no full-res decodes, no
 *   re-decode on scroll or re-open. Memory cache default is sufficient.
 * - Callbacks are stable `(Uri) -> Unit` references (no per-item lambda
 *   allocation in `items`), so a selection change only recomposes the two
 *   cells whose `isActive` flips; the rest skip via parameter equality.
 * - `rememberLazyGridState` preserves scroll position across unrelated
 *   parent recompositions (e.g. export progress) while open; state is dropped
 *   on dismiss since the whole dialog leaves composition.
 */
@Composable
fun PhotoGridDialog(
    photos: List<Uri>,
    activeUri: Uri?,
    onSelect: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Queued photos — ${photos.size}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GridInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "✕ Close",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GridAccent,
                        modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
                    )
                }
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    "Tap a photo to preview it. ✕ removes it from the queue.",
                    fontSize = 13.sp,
                    color = GridSub,
                )
                Spacer(Modifier.padding(top = 8.dp))
                if (photos.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Queue is empty — pick photos to fill it.", fontSize = 14.sp, color = GridSub)
                    }
                } else {
                    // Hoisted here so scroll position survives parent recompositions
                    // (e.g. export progress) while open; dropped on dismiss.
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        state = gridState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = photos,
                            key = { it.toString() },
                            contentType = { "photo" },
                        ) { uri ->
                            GridCell(
                                uri = uri,
                                isActive = uri == activeUri,
                                onSelect = onSelect,
                                onRemove = onRemove,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    uri: Uri,
    isActive: Boolean,
    onSelect: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
) {
    // Thumbnail request is remembered per URI: fixed 150px downsample, memory
    // cache enabled (default sized) so scroll + re-open never re-decodes.
    // No full-resolution load for grid cells.
    val context = LocalContext.current
    val thumbRequest = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(150, 150)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(GridBoxBg)
            .border(
                width = if (isActive) 3.dp else 1.dp,
                color = if (isActive) GridAccent else Color(0x1F1F2225),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = { onSelect(uri) }),
    ) {
        AsyncImage(
            model = thumbRequest,
            contentDescription = if (isActive) "Queued photo, currently previewed" else "Queued photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Per-photo remove: small overlay control; tapping the cell body selects.
        Text(
            "✕",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color(0xCC000000), RoundedCornerShape(bottomStart = 6.dp))
                .clickable(onClick = { onRemove(uri) })
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
        if (isActive) {
            Text(
                "Previewing",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xCC000000), RoundedCornerShape(topEnd = 6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
