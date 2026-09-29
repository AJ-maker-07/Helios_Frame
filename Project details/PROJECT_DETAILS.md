# Helios Frame — Project Details

Consolidated record of every project decision discussed. Setup troubleshooting is a short note at the end.

## 1. Project summary

**Helios Frame** — native Android app that batch-applies one PNG frame overlay to hundreds of photos and exports the results. Full native rewrite (previous Python/Tkinter attempt abandoned for Android instability). Priority: **runtime performance and stability**, not visual polish.

## 2. Target environment (locked)

- Language: Kotlin, no cross-platform framework
- UI: Jetpack Compose (Material 3)
- minSdk 26 (Android 8.0), compileSdk 34, targetSdk 34
- Gradle 8.7, AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.09.00
- Java: Android Studio bundled JBR (21) via `org.gradle.java.home` in `gradle.properties`
- WorkManager 2.9.0, Coil 2.6.0, DocumentFile 1.0.1
- Test device: physical Android phone via USB debugging (no emulator); scoped storage, MediaStore, runtime permissions must work on a real device
- Developer supplies frame PNGs and test photos manually — no placeholder asset generation

## 3. Architecture — multi-module MVVM

```
:app                → Compose UI screens, navigation, ViewModels
:core-image-engine  → frame-compositing logic; pure math isolated from Android calls
:core-data          → MediaStore access, file I/O, repository layer (SAF + MediaStore)
:core-batch         → WorkManager worker, batch progress tracking
```

Rules enforced:
- `:core-image-engine`: Android library, but all compositing geometry lives in `FrameLayoutCalculator` (zero Android imports) tested with plain JUnit via `:core-image-engine:testDebugUnitTest` — no Robolectric. Android-only calls isolated in `AndroidFrameRenderer` / `FrameCache`.
- ViewModel holds `StateFlow` (`Idle` / `Ready` / `Processing` / `Done` / `Error`).
- All MediaStore/SAF access lives in `:core-data` (`MediaRepository`) — no raw file-path assumptions, content URIs only.

## 4. MVP feature scope (built)

1. **Frame selection**: one PNG via SAF (`OpenDocument`).
2. **Photo selection**: batch via `OpenMultipleDocuments` (SAF multi-select / Photo Picker), capped at 1000.
3. **Preview**: live frame-on-photo overlay before running the batch (true `Box` overlay).
4. **Batch processing**: `BatchWorker` (`CoroutineWorker`) with foreground notification; survives backgrounding, rotation, Activity death. `Semaphore` bounded to `cores - 1` (clamped 2–8). Per-image pipeline: sampled decode (`inSampleSize`) → `Canvas.drawBitmap` composite → encode → write → `recycle()`. Frame decoded once per batch and reused.
5. **Progress UI**: live "done of total" bar/counter from WorkManager `Data` progress + persistent notification when backgrounded.
6. **Output**: default MediaStore `Pictures/HeliosFrame` (JPEG 92), or user-picked SAF directory via `OpenDocumentTree`.
7. **Error handling**: per-image try/catch — skip, log, continue. Final summary "N succeeded, M failed" with failed filenames.
8. **Cancel**: `WorkManager.cancelWorkById`; completed images stay saved.

Explicitly out of scope: per-photo cropping, frame library/presets, cloud/accounts/sharing, RenderScript (Canvas/Bitmap only).

## 5. Performance rules (non-negotiable, in code)

- Never bulk-load full-res bitmaps; sampled decodes only (2048px long-edge default).
- No pixel loops; `Canvas.drawBitmap()` only.
- Bounded CPU-core-aware concurrency; no unbounded coroutines.
- WorkManager-backed (not a raw background thread).
- `recycle()` / lifecycle points flagged with `LIFECYCLE:` comments.

## 6. Package / naming defaults (awaiting user)

- `applicationId` / namespace placeholder: `com.helios.frame` — user has a custom ID, value not yet supplied.
- Output naming: `framed_<sanitized-base>_<index>.jpg`, JPEG quality 92.
- Scaling: `CENTER_CROP` photo, frame stretched to output, output size = photo size.
- Photo cap 1000, long edge 2048px, parallelism cores−1 (2–8).

## 7. UI design — OJO skill, Direction C (locked)

- Skill: `app-ui-ux-best-practices` (OJO-Design-Skills, installed globally), **Convention Track** (utility product → adopt proven language, no brand methodology).
- Inspiration research consulted: Photoroom batch grid, Snapseed darkroom, Lightroom bench, Batch Photo Editor 3-step flow.
- Register (evidence-derived): quiet / polished-functional / medium density / medium weight / solemn-professional.
- Selected: **C — Queue Stack** (Lightroom-style), Stack shell, List + Detail pattern.
- Tokens: daylight paper `#FAF8F4` (60%), white/graphite support (30%), safety-orange `#C2410C` accent (~10%, 4.8:1 on paper), deep teal `#0F766E` info counter-hue, semantic red/green reserved for fail/success. Sharp 6dp radius, flat rows + hairline dividers, tabular (monospace) counters, Compact type scale.
- Motion (purpose-tested only): drill-in slide 300/225ms (Continuity), press ripple (Feedback), progress bar (Feedback). No scroll reveals, no loops.
- Honesty rule: rows show failed/active/complete/queued — only the worker-reported last file claims "active"; per-row completion order is never invented.
- Banned patterns avoided: no purple/violet primary, no gradients/glow, no fake OS chrome, no gray-box placeholders, no generic copy ("Export N photos", 3-part errors, working empty state).
- Earlier `ui-ux-pro-max` pass also applied: `collectAsStateWithLifecycle`, `LaunchedEffect(workId)`, `Box` only for overlays, Material3 tokens.

### Open design questions (not yet answered)

1. Accent hue — keep safety-orange or swap to a cool accent (teal/olive) per OJO's amber-bias warning?
2. Dark mode — direction C is daylight-locked; add a dark variant or stay light-only?
3. Detail screen — add per-file retry for failed files?

### Open product questions (not yet answered)

4. Custom `applicationId` value?
5. Output format/quality/naming final (JPEG 92 vs PNG, pattern)?
6. Scaling final (CENTER_CROP vs FIT vs STRETCH; output = photo vs frame size)?
7. Pickers/output final (Photo Picker + MediaStore vs pure SAF)?
8. Toolchain versions confirmed for the user's machine?
9. Batch caps final for the test phone (RAM/SoC)?

## 8. Skills present in this project

`.opencode/skills/`: `banner-design`, `brand`, `design-system`, `design`, `slides`, `ui-styling`, `ui-ux-pro-max`. OJO (`app-ui-ux-best-practices`) installed globally at `%APPDATA%/../Local` → `C:/Users/USER/.config/opencode/skills/`.

## 9. Build, test, run

```powershell
cmd /c gradlew.bat :core-image-engine:testDebugUnitTest   # pure-JVM unit tests
cmd /c gradlew.bat assembleDebug                          # debug APK
cmd /c gradlew.bat installDebug                           # reinstall on USB device
& "C:\Users\USER\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.helios.frame/.MainActivity
```

VS Code `Tasks: Run Task` equivalents live in `.vscode/tasks.json`. Real-device flow: Pick frame → Pick photos → Preview → Run batch → summary; outputs in Gallery → `HeliosFrame` album (`/sdcard/Pictures/HeliosFrame`).

## 10. Note — setup fixes applied along the way

`local.properties` was created with the SDK path (it is file content, not a terminal command); the Gradle wrapper (`gradlew.bat` + `gradle-wrapper.jar`, 8.7) was generated since Gradle wasn't on PATH; `settings.gradle.kts` version-catalog duplicate removed; `lifecycle-runtime-compose` / `lifecycle-viewmodel-compose` dependencies added; `local.properties`-style quoting pitfalls (spaces/parens in path) sidestepped with `cmd /c` and full-path `& "...adb.exe"` invocations.
