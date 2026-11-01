# Phase 3 — Native core and matching runtime artifacts

## Verified native-core result

The successful LibreOffice native build recorded:
- LibreOffice source revision: `6e0b39149b549bd05065d451006c4d060589891d`;
- Android NDK: `27.2.12479018`;
- ABI: `arm64-v8a`, ELF64 AArch64;
- 14 shared libraries, including `liblo-native-code.so`;
- SHA-256 hashes for each staged shared library.

The official Android Bootstrap Java wrappers added on the integration branch are pinned to the same source revision.

## Runtime asset pipeline

The native build workflow now stages the generated Android runtime directories from the same LibreOffice build:
- `android/app/assets/` (program, share, unpacked type databases and fonts);
- `android/app/assets_strippedUI/` (stripped UI registry/configuration);
- generated `license.txt` and `notice.txt` resources;
- a source commit marker.

The APK workflow downloads the native core and runtime assets using the same workflow run ID. It refuses to stage runtime data if the recorded source revision differs from the pinned bootstrap revision. It preserves NiceExcel's existing HTML asset and adjusts the fontconfig cache directory to the NiceExcel application ID.

## Verification

The APK workflow runs:
```bash
bash native-engine/scripts/check-arm64-core.sh
bash native-engine/scripts/check-native-dependency-closure.sh
gradle --no-daemon testDebugUnitTest
gradle --no-daemon :app:verifyLibreOfficeArm64Core
gradle --no-daemon assembleDebug
bash native-engine/scripts/check-apk-libreoffice.sh app/build/outputs/apk/debug/app-debug.apk
bash native-engine/scripts/check-apk-lo-runtime.sh app/build/outputs/apk/debug/app-debug.apk
```

## Remaining runtime integration

APK contents and Java compilation are necessary but not sufficient. LibreOfficeKit must still be initialized on a dedicated worker thread, a valid office handle obtained, and a workbook adapter implemented and tested.

Do not switch production formula calculation to LibreOffice until physical ARM64 device tests prove XLSX open → hard-recalculate → save → reopen, including formula errors, cached values, sheets, and formatting. The existing NiceExcel grid and formula path must remain intact until that gate passes.

LibreOffice native linking is resource-intensive; keep the full native build separate from the regular Android app build.
