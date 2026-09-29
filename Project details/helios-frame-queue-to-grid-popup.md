# OpenCode Revision — Replace Queue List with Photo Grid Popup

## Change Summary
Remove the existing scrollable queue list (was causing stutter/lag with 100+ photos). Replace it with: tapping the photo preview box (left side of the Preview Section) opens a popup showing all queued photos in a grid, with the ability to remove photos and to select one as the active preview.

## 1. Remove
- Delete the current queue list Composable and its backing per-item live-updating state/list rendering.
- Remove any thumbnail decoding/caching logic that existed specifically to support that list, if it's not reused elsewhere.

## 2. Add: Tap-to-Open Photo Grid Popup

- The **left preview box** (currently showing the current photo thumbnail) becomes tappable/clickable.
- Tapping it opens a **modal popup** showing all currently queued photos in a grid (e.g. `LazyVerticalGrid`), each cell showing a small thumbnail.
- **Per-photo remove action**: each grid cell has a way to remove that photo from the queue (e.g. a small "x" overlay on the thumbnail, or long-press to reveal a remove option — pick whichever is simpler to implement cleanly). Removing updates the shared selected-photos state immediately; if the removed photo was the currently active preview photo, fall back to the next available photo (or show the "No photo picked" placeholder if the queue becomes empty).
- **Tap-to-select**: tapping a photo (not its remove control) in the grid sets it as the active preview photo — closes the popup and updates the main screen's preview box (and combined composite preview) to that photo.
- Popup should have a clear way to dismiss without selecting anything (close button or tap-outside-to-dismiss), leaving the current preview photo unchanged.

## Performance Requirements (this grid must not repeat the queue list's lag)

- Use `LazyVerticalGrid`, not a manually laid-out grid or a `Column` of `Row`s — this is essential for scroll performance with 100+ items.
- Every grid item **must** have a stable `key` (e.g. photo URI) so Compose doesn't over-recompose on state changes like removal.
- Thumbnails shown in the grid must be small, pre-downsampled (e.g. ~100–150px), and cached — do not decode full-resolution bitmaps for grid cells. Use Coil's `AsyncImage` with a memory cache, or an equivalent small-thumbnail decode-and-cache approach, rather than re-decoding on every recomposition or scroll event.
- Removing a photo should update only that item's presence in the underlying list/state — avoid replacing the entire list object in a way that forces every remaining grid cell to recompose. Prefer a `SnapshotStateList` or similar structure where removal is a targeted operation.
- The popup itself should only be composed while open — don't keep the grid alive/rendering in the background when dismissed.

## Notes
- This popup is separate from the Export popup (progress bar/summary) already implemented — don't conflate the two. This is a photo-management/preview popup, opened by tapping the preview box, available any time (not just during export).
- The right preview box (frame alone) and combined composite preview are unaffected by this change.
