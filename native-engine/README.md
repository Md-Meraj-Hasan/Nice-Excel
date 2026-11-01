# NiceExcel — LibreOffice Calc Android formula engine

## Current integration

NiceExcel now contains an isolated LibreOffice Calc formula adapter at:

`app/src/main/java/com/meraj/niceexcel/LibreOfficeCalcEngine.kt`

The existing Kotlin spreadsheet grid and workbook model remain in place. During formula recalculation, NiceExcel snapshots all sheets to a temporary XLSX workbook, sends it to LibreOfficeKit on a dedicated single thread, issues a hard recalculation, saves as XLSX, reopens the result, and reads the cached formula values back for the existing grid.

LibreOffice Calc is the preferred evaluator when its native runtime and assets are present. The XLSX Save path also sends the actual OOXML workbook through Calc before copying it to the selected destination, so formula caches are stored in the saved file. If initialization or recalculation fails, the original OOXML export is saved with full-recalculation flags and the current Apache POI/local evaluator remains the compatibility fallback. This avoids making the grid dependent on a successful native startup.

## Matching build inputs

The following items must come from one identical LibreOffice source revision and build:

- ARM64 Android shared libraries in `native-engine/jniLibs/arm64-v8a/`
- Official Java/JNI bootstrap sources in `app/src/main/java/org/libreoffice/kit/`
- Runtime assets under the APK's `assets/` directory, including `program/`, `share/`, and `unpack/`

The Java bootstrap sources currently match LibreOffice core commit
`6e0b39149b549bd05065d451006c4d060589891d`. Their MPL-2.0 notice is recorded in
`native-engine/THIRD-PARTY-LICENSES.md`.

The native libraries are not committed as binary files. GitHub Actions downloads the verified native-core artifact and the matching runtime-assets artifact from the same native build run, stages them into the APK, and checks the required runtime files before packaging.

## Build pipeline

1. `.github/workflows/build-libreoffice-arm64.yml` builds the pinned LibreOffice Android ARM64 core and generated runtime assets.
2. It verifies the ARM64 ELF architecture and native dependency closure.
3. It uploads `libreoffice-arm64-native-core` and `libreoffice-arm64-runtime-assets` artifacts from the same run.
4. `.github/workflows/build-apk-with-libreoffice.yml` downloads both artifacts, fetches the pinned official Java bootstrap, stages them into the app, and builds the APK.
5. Gradle verifies the core library, Java bootstrap, and required runtime assets.

The ARM64 native-core artifact previously produced by run `41206388117` contains 14 shared libraries. All 14 were independently inspected as ELF64 AArch64. A new build is running to produce the matching runtime-assets package as well.

## Runtime safety and acceptance

- All LibreOfficeKit JNI calls are serialized on one dedicated worker thread.
- The formula adapter is invoked off the Android UI thread.
- The native engine is attempted only on `arm64-v8a`; other ABIs use the compatibility evaluator.
- Existing grid rendering and UI code remain separate from LibreOffice's document renderer.
- Native initialization failures do not crash formula rendering; they are logged and fall back.

A successful Gradle build or APK packaging check does not prove runtime formula calculation. Final acceptance still requires a physical ARM64 Android test with a workbook containing:
- `=1+2`
- `=SUM(A1:A3)`
- `=IF(A1>0,"YES","NO")`
- `=VLOOKUP(105,A2:B5,2,FALSE)`
- a cross-sheet reference
- `=1/0`, which must remain an error rather than silently becoming zero

The same workbook must survive XLSX save/reopen with formulas, cached values, sheets, and formatting preserved. Do not remove the fallback evaluator until this acceptance test passes.
