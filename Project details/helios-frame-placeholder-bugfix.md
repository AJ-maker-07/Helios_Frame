# OpenCode Bug Fix — Placeholder Text Not Updating After Selection

## Bug
On the main screen, the preview boxes still show "No frame picked" / "No photo picked" placeholder text even after the user has successfully picked a frame and/or photos. The placeholder state is not clearing once a valid selection is made.

## Expected Behavior
- As soon as a frame PNG is picked, the "No frame picked" placeholder should be replaced by the frame preview (rendered on its transparent/checkered background).
- As soon as at least one photo is picked, the "No photo picked" placeholder should be replaced by the selected photo thumbnail.
- The combined composite preview should also update as soon as both a frame and a photo are available.

## Likely Root Cause (check these first)
1. **State not being observed correctly**: the picked frame/photo URI may be getting stored in the ViewModel's state, but the Compose UI isn't recomposing because it's reading a stale snapshot, reading the wrong state field, or the state isn't hoisted/collected properly (e.g. missing `collectAsState()` / `collectAsStateWithLifecycle()` on the `StateFlow`, or the placeholder condition is checking a variable that's never updated).
2. **SAF picker result not propagated to state**: the `ActivityResultLauncher` callback for the file picker may be returning the URI, but not actually calling the ViewModel function that updates the selection state (e.g. silently swallowed, or updating a local variable instead of the shared state).
3. **Placeholder condition logic bug**: double-check the actual condition used to decide "show placeholder" vs "show preview" — e.g. checking `frameUri == null` when the state is actually stored as an empty string, a list, or a different nullable type than expected, so the null-check never evaluates true even after selection.
4. **Permission/URI persistence issue silently failing**: if `takePersistableUriPermission` isn't being called on the returned URI, later reads of that URI could silently fail and get caught by error handling that then falls back to showing the placeholder instead of surfacing the real error — check that failures aren't being swallowed here.

## What to Do
- Add logging (or temporarily surface a Toast/Log.d) at the point where frame/photo selection state is set, to confirm the state is actually being updated when the picker returns.
- Add logging at the point where the Compose UI reads that state to decide whether to show the placeholder, to confirm what value it's actually seeing.
- Fix whichever of the above is the actual disconnect, and confirm the fix by testing on the physical device: pick a frame → confirm placeholder clears immediately, pick photos → confirm placeholder clears immediately, without needing to navigate away and back or trigger any other recomposition manually.
- Make sure this also works correctly after cycling through multiple selected photos (next/prev arrows) and after re-picking a different frame/photo (i.e. changing an existing selection, not just picking for the first time).
