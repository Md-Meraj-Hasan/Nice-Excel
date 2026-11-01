# LibreOffice Android bootstrap and runtime packaging

## Pinned upstream revision

The following official LibreOffice Android Bootstrap Java wrappers are copied unchanged into `app/src/main/java/org/libreoffice/kit/` from core revision `6e0b39149b549bd05065d451006c4d060589891d`:

- `LibreOfficeKit.java`
- `Office.java`
- `Document.java`
- `DirectBufferAllocator.java`

The ARM64 native-core build diagnostics confirm that the native core was also built from this exact revision. The wrappers retain their upstream MPL-2.0 notices.

## Completed groundwork

- The four Java wrappers compile with the NiceExcel Android project.
- CI `testDebugUnitTest assembleDebug` passed on this branch.
- The native-core workflow now has a matching-revision gate and a separate artifact for official Android runtime assets: `assets`, `assets_strippedUI`, generated license and notice resources, and the source commit marker.
- The LibreOffice APK workflow downloads native libraries and runtime assets from the same native build run, verifies the pinned source SHA, stages the assets without replacing NiceExcel's existing `niceexcel_index.html`, and rewrites the fontconfig cache path for package `com.meraj.niceexcel`.
- An APK verification script checks required runtime files, fonts, and license resources.

## Not yet completed

These changes are still isolated from the production grid and formula engine. A successful Java compile or APK packaging check does not prove that LibreOfficeKit initializes or calculates a workbook.

Remaining gates:
1. Run the native build workflow at the pinned revision and verify both native-core and runtime-assets artifacts are produced.
2. Run the APK workflow using that same native build run ID.
3. On an ARM64 Android device, verify `LibreOfficeKit.init(Activity)` succeeds on a dedicated worker thread and the returned LOK handle is valid.
4. Implement and test an isolated XLSX open → hard-recalculate → save → reopen adapter.
5. Verify formulas, formula errors, cached values, sheet names, and formatting survive the round trip.

Do not connect LibreOffice to production cell editing or remove the current formula path until all runtime and device acceptance tests pass.
