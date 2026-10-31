# Phase 4 — Reproducible official LibreOffice Android ARM64 core build

## Why this phase exists

The current NiceExcel APK workflow only checks whether a native library has
already been supplied. It does not compile LibreOffice. This phase adds a
repeatable build entry point for a genuine LibreOffice core produced from one
official source checkout and one Android SDK/NDK installation.

Upstream documents the Android core as `liblo-native-code.so`, built by linking
LibreOffice's Android native components into one app-specific shared object.
The Android app also uses matching JNI/bootstrap code and a threaded LOK
architecture. A core library alone is therefore not yet a working Calc engine.

## Toolchain baseline

Use a Linux x86_64 build host, JDK 17+, Android NDK 27, and Android SDK 30.0.3
as described by current upstream LibreOffice build documentation. The script
uses LibreOffice's `LibreOfficeAndroid` distro configuration and the Android
SDK/NDK paths supplied by the environment. It intentionally builds ARM64 only.

## Run

Prepare/clone the official LibreOffice core source tree separately, then set:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/27.0.12077973"
export LIBREOFFICE_SRC="$HOME/src/core"
export LO_BUILD_JOBS=2

bash native-engine/scripts/build-libreoffice-android-arm64.sh "$LIBREOFFICE_SRC"
```

The script writes `autogen.input` into the supplied LibreOffice checkout,
runs `./autogen.sh` and `make android`, locates the official combined core,
checks that it is ARM64 ELF64, and stages the `.so` outputs found beside that
core into:

```text
native-engine/jniLibs/arm64-v8a/
```

It also writes `native-engine/ARM64-BUILD-MANIFEST.txt` with source commit,
toolchain paths, and SHA-256 hashes. Keep the manifest with the produced
artifact so a build can be traced to its exact source revision.

## Mandatory gates after native compilation

```bash
bash native-engine/scripts/check-arm64-core.sh
bash native-engine/scripts/check-native-dependency-closure.sh
```

Then run the existing Android unit tests, assemble the APK, and verify that the
APK contains `lib/arm64-v8a/liblo-native-code.so`.

## Important: not yet a completed NiceExcel Calc integration

This phase only provides the native-core build/staging pipeline. It does NOT
add LibreOffice's matching Android JNI/bootstrap Java classes, runtime/UNO data,
fonts/profile setup, or the XLSX open → recalculate → save → reopen adapter.
It also does not change or remove NiceExcel's current formula engines or UI.
Do not call the APK LibreOffice-enabled until all of those pieces are present
and formula acceptance tests pass on a physical ARM64 Android device.

## Resource note

LibreOffice is a very large C++ project. Build time, RAM, disk, and dependency
availability vary by source revision and runner. A standard hosted CI runner
may not be sufficient. Start with low parallelism (`LO_BUILD_JOBS=2`) and use
a Linux runner with adequate RAM and disk. The script fails explicitly if the
source tree, SDK, NDK, or expected output is missing; it does not substitute a
placeholder library.

## Upstream references

- https://github.com/LibreOffice/core/blob/master/android/README.md
- https://github.com/LibreOffice/core/blob/master/README.cross
- https://github.com/LibreOffice/core/blob/master/README.md
