# Phase 2 status — JNI bootstrap preparation

Completed in this source patch:
- Consolidated duplicate Gradle `sourceSets` configuration.
- Added ordered native dependency loader based on the official LibreOffice Android Bootstrap source.
- Added a unit test for the dependency order.
- Kept the probe isolated from production cell editing.
- Preserved the existing Kotlin grid and current formula paths.

Not completed:
- No LibreOffice native binaries are bundled.
- No official matching JNI/bootstrap source tree or runtime assets are bundled.
- No `LibreOfficeKit.initialize()` call is made.
- No XLSX open/recalculate/save/reopen adapter exists.

Next acceptance gate: build and package the full matching ARM64 runtime, initialize it on a dedicated worker thread, then prove XLSX open/recalculate/save/reopen with formula/error test workbooks on a physical Android ARM64 device.
