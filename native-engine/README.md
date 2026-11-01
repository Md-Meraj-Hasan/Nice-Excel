# NiceExcel — LibreOffice Calc Android integration

## Current status

The integration branch contains:
- the genuine ARM64 LibreOffice native-core build pipeline;
- official LibreOffice Android Java bootstrap wrappers pinned to the same source revision;
- a CI compile/test workflow;
- a runtime-assets artifact and APK staging/verification pipeline.

The official native core was built for `arm64-v8a` from LibreOffice revision `6e0b39149b549bd05065d451006c4d060589891d`. Its diagnostics recorded 14 matching shared libraries and ELF64 AArch64 architecture. The Java bootstrap wrappers on this branch use the same revision.

**Important:** the production NiceExcel grid and formula engine are not connected to LibreOffice yet. Build success and packaging verification do not prove JNI initialization or workbook recalculation. Do not advertise this as a fully LibreOffice-powered spreadsheet until the device acceptance tests below pass.

## Build sequence

1. Run **Build official LibreOffice Android ARM64 core** with revision `6e0b39149b549bd05065d451006c4d060589891d`.
2. Confirm the run uploads both `libreoffice-arm64-native-core` and `libreoffice-arm64-runtime-assets`.
3. Run **Build NiceExcel APK with LibreOffice ARM64**, supplying that same native build run ID.
4. The APK workflow stages native libraries and the matching official runtime data, adjusts the fontconfig cache path to `com.meraj.niceexcel`, and checks the APK for core, registry, type database, fonts, and license notices.

## Runtime architecture

LibreOffice must be initialized and called on a dedicated LOK worker thread, never on the Android UI thread. The upstream Android application uses a LOKit thread that communicates with a separate native soffice thread. See the pinned upstream architecture notes:

https://github.com/LibreOffice/core/blob/6e0b39149b549bd05065d451006c4d060589891d/android/README.md

## Workbook calculation acceptance tests

Before switching any production formula path, implement an isolated adapter that:
- opens an XLSX working copy through LibreOfficeKit;
- performs a hard recalculation;
- saves to a temporary XLSX;
- reopens and validates the result;
- returns validated values and formula errors to the existing workbook model.

Required tests:
- `=1+2` returns `3`;
- `SUM`, `IF`, `VLOOKUP`, and cross-sheet references return expected values;
- `=1/0` remains a formula error and is never silently converted to zero;
- formulas, cached values, sheet names, and formatting survive save/reopen;
- the UI remains responsive while calculation runs;
- tests pass on a physical ARM64 Android device.

Keep the existing grid and formula path untouched until the complete acceptance suite passes.
