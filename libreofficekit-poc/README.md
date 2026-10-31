# NiceExcel — LibreOfficeKit ARM64 POC (isolated)

**Status: scaffolding only; native engine is not included and this module is not yet buildable.**

This folder is deliberately isolated from the production NiceExcel app. It does not change the current Kotlin grid, XLSX codec, formula evaluator, Flask/Web code, manifest, Gradle configuration, or APK workflow.

## Goal
Prove that a bundled LibreOffice Android core can open an XLSX workbook, recalculate formulas, save it, and reopen it while NiceExcel keeps its current grid UI.

## Why the native core is a separate prerequisite
LibreOffice's Android implementation bundles its core as `liblo-native-code.so` and uses JNI. Its documented design also has a dedicated LOKit thread and native LibreOffice thread; this is not a drop-in Maven formula library. See:
- https://docs.libreoffice.org/android.html
- https://github.com/LibreOffice/core/blob/master/android/README.md
- https://github.com/LibreOffice/core/blob/master/README.cross

## POC stages
1. Build the official LibreOffice Android core for `arm64-v8a` on Linux using a compatible Android SDK/NDK toolchain.
2. Obtain the matching JNI/bootstrap Java sources and required runtime data from the same LibreOffice source revision.
3. Package the native core and runtime assets in a standalone test app; verify native loading and initialization first.
4. Open the workbook in headless/document mode through a supported LibreOfficeKit/UNO bridge; recalculate, save, and reopen.
5. Compare formulas and cached values; then test NiceExcel adapter integration without replacing its UI.

## Acceptance workbook tests
- `=1+2` → `3`
- `=SUM(A1:A3)` → expected total
- `=IF(A1>0,"YES","NO")` → expected text
- `=VLOOKUP(105,A2:B5,2,FALSE)` → matching value
- cross-sheet reference such as `=Sheet1.A1+10`
- formula error such as `=1/0` must remain an error, not silently become zero
- save/reopen must preserve formulas, values, sheets, and basic formatting

## Important limitations
- This scaffold intentionally does **not** fake a native library or claim that LibreOfficeKit is working.
- The production project has not been modified by this POC.
- Do not connect this adapter to `MainActivity` until the native-core build and workbook acceptance tests pass.
- Build and licensing details must be checked against the exact LibreOffice source revision used.
