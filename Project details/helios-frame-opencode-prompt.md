# OpenCode Build Prompt — Helios Frame (Android, Kotlin)

## Project Summary
Build **Helios Frame**, a native Android app that lets a user pick a single PNG frame overlay and batch-apply it to hundreds of photos at once, exporting the results. Priority is **runtime performance and stability**, not visual polish. Previous attempt in Python/Tkinter was abandoned due to instability on Android — this is a full native rewrite.

## Target Environment
- Language: **Kotlin**, no cross-platform framework (no Flutter/React Native/Kivy)
- UI: **Jetpack Compose**
- Min SDK: 26 (Android 8.0) — adjust only if a dependency forces higher
- Target SDK: latest stable
- Build tool: Gradle (Kotlin DSL, `build.gradle.kts`)
- Testing device: physical Android phone via USB debugging (no emulator) — do not assume emulator-only APIs are fine; verify permission flows work on a real device (scoped storage, MediaStore, runtime permissions)

## Architecture — Multi-module, MVVM
Set up the project with these modules:

```
:app                 → Compose UI screens, navigation, ViewModels
:core-image-engine   → Pure Kotlin/Java frame-compositing logic, NO Android UI deps
:core-data           → MediaStore access, file I/O, repository layer
:core-batch          → WorkManager worker, batch progress tracking
```

Rules:
- `:core-image-engine` must be testable with plain `./gradlew test` — no Robolectric, no instrumented tests needed for the core compositing math. Abstract the bitmap type behind an interface so the compositing logic itself has zero Android dependency if feasible; if that's impractical with `android.graphics.Bitmap`, keep the module as close to pure-Kotlin as possible and isolate Android-only calls at the edges.
- ViewModel holds `StateFlow` for UI state (idle / picking / processing / done / error).
- Repository layer in `:core-data` handles all `MediaStore` / `SAF` (Storage Access Framework) file access — no direct file path assumptions, since Android scoped storage requires content URIs.

## MVP + Polish Feature Scope (this build)
1. **Frame selection**: user picks one PNG frame overlay from device storage (SAF file picker).
2. **Photo selection**: user picks a batch of photos (up to hundreds) via SAF multi-select or a folder-based picker.
3. **Preview**: show a live preview of the frame composited onto at least one selected photo before running the batch, so the user can confirm alignment/scale before committing.
4. **Batch processing**:
   - Runs via `WorkManager` (foreground service notification), survives app backgrounding and process death.
   - Bounded concurrency: use a `Semaphore` sized to available CPU cores, not one coroutine per image.
   - Per-image pipeline: decode at target resolution using `inSampleSize` (no full-res bulk loading) → composite via `Canvas`/`Bitmap`/`Paint` → encode → write to output URI → free bitmap. One image fully processed and released before starting the next on a given worker.
   - Frame PNG is decoded and scaled once, then reused/cached across all composites — do not re-decode the frame per photo.
5. **Progress UI**: visible progress bar / counter ("Processing 42 of 300") tied to WorkManager progress data, updates live in Compose UI, plus a persistent notification for when the app is backgrounded.
6. **Output**: save composited images to a user-chosen output location (SAF directory) or a dedicated app-specific "Helios Frame" folder in shared storage via MediaStore, without blocking UI thread.
7. **Error handling**: if an individual image fails (corrupt file, OOM risk, unsupported format), skip it, log it, and continue the batch — do not crash the whole batch on one bad file. Show a final summary (e.g. "298 succeeded, 2 failed") with which files failed.
8. **Cancel support**: user can cancel an in-progress batch; already-completed images remain saved, WorkManager job stops cleanly.

## Explicitly Out of Scope for This Build
- Cropping/adjusting individual photos
- Multiple frame slots or frame presets/library management
- Cloud sync, accounts, or sharing features
- Any RenderScript usage (deprecated) — use Canvas/Bitmap only for this build

## Performance Requirements (non-negotiable)
- No bulk loading of full-resolution bitmaps into memory at once, ever.
- No pixel-by-pixel manual compositing loops — use `Canvas.drawBitmap()`.
- Concurrency must be bounded and CPU-core-aware, not unbounded coroutine/thread spawning.
- Batch job must survive configuration changes (rotation) and must survive the Activity being destroyed (this is why it's WorkManager-backed, not a plain background thread).
- Memory profiling note: flag in code comments anywhere a `Bitmap.recycle()` or explicit lifecycle consideration matters, even though it's less critical on API 26+.

## Deliverables Requested from OpenCode
1. Full Gradle multi-module project scaffold matching the module breakdown above, with working `settings.gradle.kts` and module `build.gradle.kts` files.
2. `:core-image-engine` with the frame-compositing function fully implemented and unit tested (test with a solid-color mock bitmap + mock frame to verify alignment/scaling math).
3. `:core-data` with SAF-based file picking and MediaStore output writing implemented.
4. `:core-batch` with a working `CoroutineWorker` or `ListenableWorker` that reports progress via `WorkManager`'s `Data` progress API.
5. `:app` with Compose screens for: frame pick → photo pick → preview → processing (progress bar) → results summary. Minimal styling is fine — functional over polished, per project priorities.
6. A short `README.md` explaining how to build and run on a physical device via `adb`, and how to run the `:core-image-engine` unit tests standalone.

## Notes for the Coding Agent
- Assume the developer has **frame PNGs and test photos ready** and will supply them manually to a device for real-device testing — do not spend effort building placeholder/test asset generation.
- Assume no emulator will be used at any point — do not rely on emulator-specific behavior or skip permission-flow implementation "for now."
- Prioritize getting the batch pipeline correct and stable over UI refinement; if forced to cut corners, cut them in Compose styling, not in the image engine or batch reliability logic.
