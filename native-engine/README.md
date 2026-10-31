# NiceExcel — LibreOffice Calc Android integration (basic wiring)

## Current status
This ZIP contains the Android native-library packaging path, a safe native-load probe, CI checks, and an isolated integration plan. **It does not contain LibreOffice's native core, matching JNI bootstrap implementation, UNO runtime data, or a working workbook recalculation adapter.** Therefore this ZIP is not yet a LibreOffice-enabled APK.

The provided build log showed a successful regular Android/Chaquopy APK build and Python tests, but no LibreOffice core compilation, JNI link, or `liblo-native-code.so` packaging. This ZIP implements the next groundwork; it does not turn that earlier build into an LO build.

## 1. Native core output
Build the official LibreOffice Android core from one pinned LibreOffice source revision using its supported Android SDK/NDK toolchain. Obtain the matching JNI/bootstrap sources and runtime data from the same revision. Do not mix outputs from different LibreOffice versions.

Copy genuine ARM64 `.so` outputs into:

```text
native-engine/jniLibs/arm64-v8a/
  liblo-native-code.so
  <every required dependent .so from the same build>
```

This directory is wired into Android Gradle `main.jniLibs`. `.gitkeep` is only a placeholder, not a library.

## 2. Verify native packaging

```bash
bash native-engine/scripts/check-arm64-core.sh
gradle --no-daemon :app:verifyLibreOfficeArm64Core
gradle --no-daemon assembleDebug
bash native-engine/scripts/check-apk-libreoffice.sh app/build/outputs/apk/debug/app-debug.apk
```

Use GitHub Actions workflow **Verify LibreOffice Calc Native APK** only after adding real native files. The ordinary **Build NiceExcel APK** workflow remains available for UI development and reports LO absence instead of falsely claiming LO integration.

## 3. JNI bootstrap and runtime
`LibreOfficeNativeProbe` is diagnostic only. It attempts `System.loadLibrary("lo-native-code")`; successful loading proves only that Android's linker loaded that library. It does not prove LibreOfficeKit initialized or that a workbook can be recalculated.

The next code stage must add the matching official Android JNI/bootstrap classes and runtime data, initialize LibreOffice on a dedicated worker thread, and expose a narrow Kotlin adapter. Native dependencies, data files, fonts, profiles, and licensing notices must be packaged according to the exact LO build.

## 4. Workbook calculation adapter
Keep the existing NiceExcel Kotlin grid/UI. The planned bridge should accept an XLSX file or private working copy, open it with the supported LibreOfficeKit/UNO document API, recalculate, save to a temporary XLSX, and return validated cell values/formula errors to the existing workbook model. This adapter is **not implemented yet**; LOK is not a simple `calculateCell()` API.

Acceptance tests before switching the app:
- `=1+2` returns `3`
- `SUM`, `IF`, `VLOOKUP` and cross-sheet references return expected values
- `=1/0` remains an error, never silently becomes zero
- formulas, cached values, sheets, and formatting survive save/reopen
- test on physical ARM64 Android device and confirm no UI-thread blocking

## Safety rule
Do not remove the current formula/POI path or connect the probe to production cell editing until native initialization and workbook open/recalculate/save/reopen tests pass. This protects the working grid during migration.
