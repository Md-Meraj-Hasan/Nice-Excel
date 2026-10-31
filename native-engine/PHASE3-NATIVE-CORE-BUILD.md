# Phase 3 — Native core build and dependency-closure gate

## Current state

The Android project currently has a packaging location and checks, but it does
**not** contain the official LibreOffice Android core. A green Gradle APK build
or a green “availability check” does not mean Calc is linked.

The upstream Android application builds LibreOffice core into
`liblo-native-code.so` and uses matching LibreOfficeKit JNI/bootstrap code.
It also uses a dedicated LOK thread and a separate native soffice thread.
See the upstream Android architecture notes:
https://github.com/LibreOffice/core/blob/master/android/README.md

## Native build requirements

1. Build LibreOffice core for Android using the upstream `README.cross` and
   Android build instructions from the same LibreOffice source revision.
2. Use the Android SDK and NDK versions required by that source revision.
3. Keep the core, JNI/bootstrap Java sources, UNO/runtime data, and dependent
   shared libraries from one matching build. Do not mix `.so` files from
   different LibreOffice versions or unrelated Android packages.
4. Start with **arm64-v8a only**. Other APK ABIs must not claim LibreOffice
   support until their own matching native cores are built and tested.
5. Preserve the build logs, source commit SHA, NDK version, configure options,
   and SHA-256 hashes of all native outputs.

## Artifact staging

After a successful official native build, stage the complete ARM64 shared
library set here:

```text
native-engine/jniLibs/arm64-v8a/
  liblo-native-code.so
  <all matching non-platform dependent .so files>
```

The exact dependent libraries are determined by the actual native build output;
this document intentionally does not invent a fixed list.

Run these checks before Gradle packaging:

```bash
bash native-engine/scripts/check-arm64-core.sh
bash native-engine/scripts/check-native-dependency-closure.sh
```

Then build the APK and verify it contains the core:

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
bash native-engine/scripts/check-apk-libreoffice.sh app/build/outputs/apk/debug/app-debug.apk
```

## Important integration gap after packaging

Even after the `.so` files are packaged, Calc is not yet usable from NiceExcel.
The next code milestone must import the matching official JNI/bootstrap classes,
initialize the runtime on a dedicated worker thread, provide the required
LibreOffice program/runtime data, and implement an XLSX open → recalculate →
save → reopen adapter. Only then should formula test workbooks be run on a
physical ARM64 Android device. Keep the existing formula engine and Kotlin grid
until that acceptance test passes.

## CI resource caution

LibreOffice is a large native source build. The upstream Android instructions
refer to `README.cross`; the upstream Android README also notes that linking
with full debug symbols can require around 16 GB RAM plus swap. Do not assume
that a normal Android Gradle runner has completed the native build. Use a
runner with sufficient RAM, disk, and build time, or use a verified native
artifact produced by a dedicated build job.
