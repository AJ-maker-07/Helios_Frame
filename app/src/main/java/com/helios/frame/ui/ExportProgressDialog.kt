package com.helios.frame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.helios.frame.data.FileLogger

private val DialogAccent = Color(0xFFC2410C)
private val DialogInk = Color(0xFF1F2225)
private val DialogSub = Color(0xFF5C6268)
private val DialogTrack = Color(0xFFF3F0E9)
private val DialogOk = Color(0xFF1B7A4D)

/**
 * Export popup (modal dialog). Driven by the SAME [HeliosUiState] batch-state
 * source used for progress tracking — no parallel progress mechanism.
 *
 * - Opens on Export tap; the batch job is already enqueued by then.
 * - While [HeliosUiState.Processing]: progress bar + "Processing N of M" count
 *   (no thumbnails, no ETA) + Cancel button.
 * - On completion ([HeliosUiState.Done], success / partial / cancelled): stays
 *   open with a summary until the user taps Done. Never auto-closes.
 */
@Composable
fun ExportProgressDialog(
    state: HeliosUiState,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    Dialog(onDismissRequest = { /* stay open until Done/Cancel resolves it */ }) {
        Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                when (state) {
                    is HeliosUiState.Processing -> ProcessingBody(state, onCancel)
                    is HeliosUiState.Done -> DoneBody(state, onDone)
                    is HeliosUiState.Error -> {
                        Text("Export failed", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DialogInk)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Batch could not finish. ${state.message}.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = DialogAccent),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Done") }
                    }
                    else -> {
                        // Idle/Ready should never show the dialog, but never crash if it does.
                        Text("Preparing export…", fontSize = 16.sp, color = DialogSub)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = DialogAccent,
                            trackColor = DialogTrack,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingBody(state: HeliosUiState.Processing, onCancel: () -> Unit) {
    Text("Exporting photos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DialogInk)
    Spacer(Modifier.height(4.dp))
    Text(
        "Processing ${state.done} of ${state.total}",
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        color = DialogSub,
    )
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { if (state.total > 0) state.done / state.total.toFloat() else 0f },
        modifier = Modifier.fillMaxWidth(),
        color = DialogAccent,
        trackColor = DialogTrack,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel — keep finished files")
    }
}

@Composable
private fun DoneBody(state: HeliosUiState.Done, onDone: () -> Unit) {
    val headline = when {
        state.cancelled -> "Batch stopped — ${state.succeeded} files kept"
        else -> "${state.succeeded} succeeded, ${state.failed} failed"
    }
    Text(headline, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = DialogInk)
    Spacer(Modifier.height(4.dp))
    Text(
        if (state.cancelled) "Completed images remain saved."
        else if (state.failed == 0) "Every file exported. Find them in Pictures / HeliosFrame."
        else "Completed images are saved; only the files below failed.",
        fontSize = 14.sp,
        color = if (!state.cancelled && state.failed == 0) DialogOk else DialogSub,
    )
    if (state.failedNames.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Failed files", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DialogSub)
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier.fillMaxWidth().height(160.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            state.failedNames.take(100).forEach {
                Text("• $it", fontSize = 13.sp, color = DialogInk)
            }
            if (state.failedNames.size > 100) {
                Text("…and ${state.failedNames.size - 100} more", fontSize = 13.sp, color = DialogSub)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onDone,
        colors = ButtonDefaults.buttonColors(containerColor = DialogAccent),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Done") }
    // Phase 3: where to pull the persistent log after a test run.
    val logPath = FileLogger.path()
    if (logPath != null) {
        Spacer(Modifier.height(8.dp))
        Text("Log: $logPath", fontSize = 11.sp, color = DialogSub)
    }
}
