# LibreOfficeKit POC — next steps

1. Build the official LibreOffice Android ARM64 native core with a pinned source revision and compatible Android toolchain.
2. Add all matching native dependencies to `native-engine/jniLibs/arm64-v8a/` and run `check-arm64-core.sh`.
3. Import matching JNI/bootstrap Java sources and runtime data from that same revision; initialize off the UI thread.
4. Prove native initialization and XLSX open, recalculate, save, and reopen in an isolated test harness.
5. Implement a Kotlin workbook adapter that preserves the existing NiceExcel grid and transfers workbook/cell state safely.
6. Run formula acceptance tests on a physical ARM64 device.
7. Only after passing, route formula recalculation through LibreOffice and remove legacy formula engines in a separate, reviewable change.

A normal `assembleDebug` success is not proof of LibreOffice integration. The `Verify LibreOffice Calc Native APK` workflow intentionally fails unless the genuine native core is present and packaged.
