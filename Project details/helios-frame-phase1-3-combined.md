# OpenCode — Combined Task List (Phases 1–3, Beta Test Tonight)

Work through these phases **in order**. Do not start Phase 2 until Phase 1 is verifiably fixed, and do not start Phase 3 until Phase 2 is done. Each phase has its own verification step — confirm it before moving on.

---

## PHASE 1 — Fix: Placeholder Text Not Clearing After Selection

### Bug
On the main screen, "No frame picked" / "No photo picked" placeholder text still shows even after a valid frame/photo has been picked.

### Likely Root Cause (check in this order)
1. State not observed correctly — missing or incorrect `collectAsState()` / `collectAsStateWithLifecycle()` on the relevant `StateFlow`, or the placeholder condition reads a variable that's never actually updated.
2. SAF picker result not propagated — the `ActivityResultLauncher` callback may return the URI but never call the ViewModel function that updates shared state.
3. Placeholder condition logic bug — e.g. checking `== null` against a state type that's actually an empty string/list rather than null.
4. Permission/URI persistence failing silently — missing `takePersistableUriPermission()` causing later reads to fail and fall back to placeholder without surfacing the real error.

### Fix & Verify
- Add temporary logging at (a) the point selection state is set and (b) the point the UI reads it, to confirm where the disconnect is, then fix it.
- Verify on the physical device: pick a frame → placeholder clears immediately. Pick photos → placeholder clears immediately. Re-pick a different frame/photo → updates correctly. No navigation or extra taps required to trigger the update.

---

## PHASE 2 — Replace Queue List with Tap-to-Open Photo Grid Popup

### Remove
- Delete the existing scrollable queue list Composable and any per-item live-updating state/rendering built specifically for it.

### Add
- The **left preview box** (current photo thumbnail) becomes tappable.
- Tapping it opens a modal popup showing all queued photos in a `LazyVerticalGrid`.
- **Remove action** per photo (small "x" overlay or long-press) — removes it from the queue immediately. If the removed photo was the active preview, fall back to the next available photo, or show the placeholder if the queue is now empty.
- **Tap-to-select**: tapping a photo (not its remove control) sets it as the active preview photo, updates the main screen's preview + combined composite preview, and closes the popup.
- Popup dismissible via close button or tap-outside, with no changes applied if dismissed without a selection.

### Performance Requirements (build these in from the start — do not ship unoptimized then fix later)
- `LazyVerticalGrid`, never a manually chunked layout.
- Every grid item has a **stable key** (photo URI) in the `items()` call.
- Thumbnails via Coil's `AsyncImage` (or equivalent) requested at a small fixed size (~100–150px) — never decode full-resolution images for grid cells. If not using Coil, use a manual `LruCache<Uri, Bitmap>` with `inSampleSize`-based decoding.
- Back the queued-photos list with a `SnapshotStateList` (or equivalent) so removal is a targeted mutation, not a full list replacement.
- Selection highlight (if implemented) should only recompose the previously-selected and newly-selected cells, not the whole grid.
- Grid content should only be composed while the popup is open — not retained/rendering in the background when dismissed.

### Verify
On the physical device with 200+ queued photos:
- Scrolling the grid is smooth, no visible frame drops.
- Removing several photos in quick succession causes no stutter.
- Tap-to-select closes the popup and updates the main preview instantly.
- Re-opening the popup doesn't cause a noticeable reload delay for already-seen thumbnails.

---

## PHASE 3 — ANR/Freeze Safety Net

Build this as an always-on background safety system — no manual freeze reproduction should be required to verify it works.

1. **StrictMode (debug builds only)**: detect disk reads/writes and network calls on the main thread, `penaltyLog()` only, gated behind `BuildConfig.DEBUG`, set up once in the `Application` class.
2. **Per-image timeout watchdog**: wrap each image's decode → composite → encode step in a coroutine timeout (default 10–15s, easily tunable). On timeout: log it, record it as a failure for that image in the batch's failure list (surfaces in the Export popup summary), and continue to the next image — the worker slot must not stay stuck. Include a unit/instrumented test that simulates a hang (artificial delay past the timeout) and asserts it's cancelled and reported as a failure rather than blocking indefinitely.
3. **Memory pressure handling**: implement `ComponentCallbacks2.onTrimMemory()` in the `Application` class (or a dedicated memory manager) to proactively clear the thumbnail cache and Coil's memory cache on moderate/high memory pressure signals, before the OS is forced to OOM-kill the process.
4. **Persistent local logging**: log all timeout/recovery events to a local log file (not just Logcat) that can be pulled via `adb pull` after a real-world test run, so issues are visible after the fact.
5. **Pre-batch sanity check**: before starting a batch, check available memory headroom via `ActivityManager.getMemoryInfo()` and show a UI warning (not a crash) if the device is under memory pressure before starting.

### Verify
- Run the simulated-hang unit test and confirm it passes (timeout triggers, image marked failed, batch continues).
- Confirm the local log file path/format, and that log entries appear after a real test run.

---

## After All 3 Phases: Real Stress Test (for tonight's beta)
On the physical device, run a batch of 200–300 real photos, including 2–3 intentionally corrupted/malformed files mixed in. Confirm:
- No ANR/freeze occurs.
- Corrupted files are skipped and reported in the failure summary, not crashing the batch.
- Placeholder bug (Phase 1) does not resurface.
- Grid popup (Phase 2) stays smooth throughout.
- Pull the local log file via `adb pull` afterward and check it reflects what happened.

Report back with what passed, what didn't, and the pulled log file if any issues came up.
