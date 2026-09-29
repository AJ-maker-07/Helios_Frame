# OpenCode Addition — Preview Section + Export Popup (Helios Frame)

Add the following to the existing Helios Frame app.

## 1. Preview Section (main screen, above the Export button)

- Side-by-side (stacked on narrow screens) boxes:
  - **Left**: thumbnail of the currently selected photo. Default to the first selected photo; if more than one photo is selected, allow next/prev arrows to cycle through them.
  - **Right**: the frame PNG rendered alone on a transparent/checkered background so its shape and borders are visible on their own.
- Below or overlapping both: a **combined preview** showing the actual composited result (photo + frame), rendered using the same compositing function used for real export — do not build a separate approximate/CSS-style overlay just for preview, so what's shown matches the real export output exactly.
- Downscale preview renders aggressively (max ~400–600px on the long edge) since this is a live UI preview, not the final export resolution.
- Recompute the preview only when the selected photo (via cycling) or frame changes — not on every recomposition. Debounce rapid next/prev taps so they don't queue redundant composite calls.
- Show a placeholder/empty state in each box if no photo/frame is selected yet — never a blank space or crash.

## 2. Export Button

- Fixed at the bottom of the screen, disabled until both a frame and at least one photo are selected.
- Tapping it opens the Export popup (below) and starts the batch job.

## 3. Export Popup (modal/dialog)

- Opens when Export is tapped, immediately starts the batch job.
- **While processing**: shows a progress bar and a count (e.g. "Processing 42 of 300"). Keep it simple — no thumbnail-per-image, no ETA.
- **Cancel button** available while processing is active. Tapping it cancels the underlying batch job cleanly — already-completed images remain saved — and reflects the cancellation in the popup.
- **On completion** (success or partial failure): the popup stays open showing a summary (e.g. "298 succeeded, 2 failed", listing which files failed) until the user taps a **Done** button to dismiss it. It does not auto-close.
- Implement as its own Composable (e.g. `ExportProgressDialog`) driven by the same batch-state source already used for progress tracking — don't create a second, parallel progress mechanism.
