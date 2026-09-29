# Helios Frame (Android, Kotlin) — MVP scaffold

Native Android batch frame compositor. Pick 1 PNG frame → pick N photos → preview → WorkManager batch → summary.
Performance-first: sampled decodes, `Canvas.drawBitmap` only, CPU-bounded concurrency, frame decoded once.

## Modules

```
:app                Compose UI + ViewModels (StateFlow idle/processing/done/error)
:core-image-engine  Pure-math layout (FrameLayoutCalculator, unit-tested) + thin Canvas edge
:core-data          MediaStore + SAF repository (no raw file paths)
:core-batch         WorkManager CoroutineWorker + progress + cancel
```

## Prerequisites (physical device only, no emulator)

- JDK 17, Android SDK 34, VS Code + Gradle extension
- Physical phone with USB debugging ON (`adb devices` shows it)
- Frame PNGs + test photos copied to device manually

## Build & run (VS Code or terminal)

```powershell
# 1. Generate wrapper (first time, needs Gradle installed once):
gradle wrapper --gradle-version 8.7

# 2. Engine unit tests (pure JVM, no device):
.\gradlew.bat :core-image-engine:testDebugUnitTest

# 3. Build debug APK:
.\gradlew.bat assembleDebug

# 4. Install + launch on USB phone:
.\gradlew.bat installDebug
adb shell am start -n com.helios.frame/.MainActivity
```

Or use VS Code: `Ctrl+Shift+P → Tasks: Run Task` → `assembleDebug` / `install + launch` / `unit-test engine`.
See `.vscode/tasks.json`.

## Real-device test flow

1. Tap **Pick frame** → choose PNG (SAF).
2. Tap **Pick photos** → multi-select (Photo Picker / SAF).
3. Check **Preview** (photo + toggleable frame overlay).
4. Optional: **Output** button → default MediaStore `Pictures/HeliosFrame`, or pick SAF folder.
5. **Run batch** → progress bar `Processing X of N` + notification when backgrounded.
6. Result: `N succeeded, M failed` + failed filenames. Cancel keeps already-saved files.

Check output: device Gallery → `HeliosFrame` album, or `adb shell ls /sdcard/Pictures/HeliosFrame`.

## Performance notes

- `FrameCache.decodeForReuse` — frame decoded once per batch, recycled once at end.
- `FrameCache.decodeSampled` — per-photo `inSampleSize`, one image at a time per permit.
- `Semaphore(boundedParallelism())` — cores-1, clamped 2..8; no unbounded coroutines.
- `recycle()` calls flagged with LIFECYCLE comments (less critical on API 26+ but bounds peak in 100s-batches).

## Open details I did NOT assume (reply to lock in)

1. **applicationId**: scaffold uses `com.helios.frame` placeholder — give me your custom ID and I rename.
2. **Output format/quality/naming**: default JPEG 92, `framed_<base>_<index>.jpg` — confirm PNG vs JPEG, quality, pattern.
3. **Scaling**: default `CENTER_CROP` photo + frame stretched to output; output size = photo size. Confirm or choose FIT/STRETCH, output = frame size?
4. **Pickers/output**: default Photo Picker + MediaStore folder, SAF folder optional. Confirm pure-SAF if you insist.
5. **Toolchain**: AGP 8.5.2 / Kotlin 2.0.21 / Compose BOM 2024.09.00 / compileSdk+target 34 / Java 17. Confirm your SDK versions.
6. **Batch caps**: photo cap 1000, long-edge 2048px, parallelism cores-1 (2..8). Confirm for your test phone (RAM? SoC?).
7. **Min/target SDK**: min 26 per prompt, target 34. Confirm if Play forces 35.
