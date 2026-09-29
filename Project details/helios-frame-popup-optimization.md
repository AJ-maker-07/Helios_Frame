# OpenCode Optimization — Photo Grid Preview Popup

## Context
The photo grid popup (opened by tapping the left preview box, showing all queued photos with remove + tap-to-select) needs to stay smooth with queues of 100–300+ photos. This prompt is specifically for optimizing that popup's performance — assume the popup already exists and is functionally correct; focus purely on making it fast and stutter-free.

## Optimization Checklist

### 1. Thumbnail loading
- Use **Coil's `AsyncImage`** (or equivalent) for every grid cell, not manual `BitmapFactory` decode calls inside the Composable.
- Request thumbnails at a fixed small size (e.g. `size(150, 150)` or similar) via Coil's `ImageRequest` — never load the full-resolution source image just to show a grid thumbnail.
- Confirm Coil's memory cache is enabled and sized reasonably (default is usually fine) so re-opening the popup or scrolling back up doesn't re-decode already-seen thumbnails.
- If not using Coil: implement a manual `LruCache<Uri, Bitmap>` keyed by photo URI, decode with `inSampleSize` sized to the target thumbnail dimensions, and reuse cached bitmaps rather than re-decoding on recomposition.

### 2. List/grid structure
- `LazyVerticalGrid` with `GridCells.Adaptive` or `GridCells.Fixed` — never a manually chunked `Column`/`Row` layout for 100+ items.
- Every item must have a **stable, unique `key`** (photo URI or stable id) passed to the `items()` call — this is the single most impactful fix for avoiding unnecessary recomposition on state changes like removal or selection.
- Avoid putting expensive computation (formatting, derived state) directly inline in the item Composable body if it can be hoisted or remembered instead.

### 3. State management
- Back the queued-photos list with a `SnapshotStateList` (or similar structure) so that **removing one item is a targeted mutation**, not a full list replacement — replacing the entire list object on every removal forces Compose to treat all items as potentially changed.
- Selecting a photo (tap-to-preview) should only affect the state needed to update the main screen's preview — it should not cause the grid popup itself to recompose entries unrelated to the selection (e.g. don't wrap "is this the selected photo" highlighting logic in a way that touches global state read by every grid cell, if it can be scoped more narrowly).
- If a "selected" highlight border/indicator is shown on the currently active preview photo within the grid, make sure only the previously-selected and newly-selected cells recompose — not the entire grid — when selection changes.

### 4. Composition scope
- The popup's grid content should only be composed while the popup is actually open (e.g. gated behind the dialog's visibility state) — it should not remain composed and consuming resources in the background when dismissed.
- Avoid recomposition of the entire popup (including scroll position reset) on unrelated state changes elsewhere in the app — the popup's own state (scroll position, open/closed) should be scoped so it isn't affected by, e.g., export progress updates happening elsewhere.

### 5. Remove-action feedback
- The per-photo remove action (tap "x" or long-press) should update state immediately and only remove that one grid cell — verify it doesn't trigger a full grid rebuild or a visible flash/reflow of unrelated cells.
- If removal causes a grid reflow (remaining items shifting to fill the gap), that's expected and fine — just confirm it's not accompanied by a full re-decode of already-visible thumbnails.

## Verification
After implementing, verify on the physical test device with a queue of 200+ photos:
- Scrolling the grid up and down is smooth with no visible frame drops.
- Removing several photos in quick succession doesn't cause stutter or lag.
- Tapping to select a preview photo closes the popup and updates the main screen instantly, without a visible delay or flash.
- Re-opening the popup after closing it doesn't cause a noticeable reload/re-decode delay for thumbnails already seen this session.
