#!/usr/bin/env bash
# Build the official LibreOffice Android ARM64 core from a prepared core checkout.
# No placeholder or third-party native libraries are accepted.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LO_SRC="${1:-${LIBREOFFICE_SRC:-}}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
JOBS="${LO_BUILD_JOBS:-2}"
STAGE="$ROOT/native-engine/jniLibs/arm64-v8a"

fail() { echo "::error::$*" >&2; exit 2; }
[[ -n "$LO_SRC" && -d "$LO_SRC" ]] || fail "Pass a prepared LibreOffice core checkout as argument 1 or set LIBREOFFICE_SRC."
[[ -x "$LO_SRC/autogen.sh" ]] || fail "No autogen.sh in $LO_SRC; this is not a LibreOffice core source checkout."
[[ -n "$SDK" && -d "$SDK" ]] || fail "ANDROID_SDK_ROOT/ANDROID_HOME is missing."
[[ -n "$NDK" && -d "$NDK" ]] || fail "ANDROID_NDK_HOME/ANDROID_NDK_ROOT is missing."
[[ -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || fail "Linux x86_64 Android NDK LLVM toolchain not found."
command -v make >/dev/null || fail "make is required."
command -v sha256sum >/dev/null || fail "sha256sum is required."
command -v readelf >/dev/null || command -v llvm-readelf >/dev/null || fail "readelf or llvm-readelf is required."

SDK="$(cd "$SDK" && pwd)"
NDK="$(cd "$NDK" && pwd)"
LO_SRC="$(cd "$LO_SRC" && pwd)"

# Official LibreOffice Android distro configuration. Keep SDK/NDK values explicit.
cat > "$LO_SRC/autogen.input" <<AUTOGEN
--build=x86_64-unknown-linux-gnu
--with-distro=LibreOfficeAndroid
--with-android-sdk=$SDK
--with-android-ndk=$NDK
--disable-debug
--disable-dbgutil
AUTOGEN

printf 'LibreOffice source: %s\n' "$LO_SRC"
printf 'Android SDK: %s\nAndroid NDK: %s\nBuild jobs: %s\n' "$SDK" "$NDK" "$JOBS"
git -C "$LO_SRC" rev-parse HEAD | tee "$ROOT/native-engine/LIBREOFFICE-SOURCE-COMMIT.txt" || fail "LibreOffice source must be a Git checkout so its exact revision is recorded."

cd "$LO_SRC"
./autogen.sh
# Upstream Android build instructions use the normal top-level make after autogen.
make -j"$JOBS"

# Android build output location varies across upstream revisions. Search the full
# checkout, but accept exactly one official combined library and its sibling set.
mapfile -t CORES < <(find "$LO_SRC" -type f -name liblo-native-code.so \
  -not -path '*/.git/*' -print 2>/dev/null | sort)
[[ ${#CORES[@]} -eq 1 ]] || fail "Expected exactly one liblo-native-code.so in the completed checkout; found ${#CORES[@]}. Inspect build logs/output layout; nothing was staged."
CORE="${CORES[0]}"
OUT_DIR="$(dirname "$CORE")"
READELF="$(command -v llvm-readelf || command -v readelf)"
CLASS="$("$READELF" -h "$CORE" | awk -F: '/Class:/{gsub(/^[ \t]+/,"",$2); print $2}')"
MACHINE="$("$READELF" -h "$CORE" | awk -F: '/Machine:/{gsub(/^[ \t]+/,"",$2); print $2}')"
[[ "$CLASS" == ELF64 && "$MACHINE" == *AArch64* ]] || fail "Core is not ARM64 ELF64: class=$CLASS machine=$MACHINE"

mkdir -p "$STAGE"
find "$STAGE" -maxdepth 1 -type f -name '*.so' -delete
while IFS= read -r -d '' so; do
  cp -f "$so" "$STAGE/$(basename "$so")"
done < <(find "$OUT_DIR" -maxdepth 1 -type f -name '*.so' -print0)
[[ -s "$STAGE/liblo-native-code.so" ]] || fail "Core was not staged."

{
  echo "LibreOffice commit: $(git -C "$LO_SRC" rev-parse HEAD)"
  echo "Android SDK: $SDK"
  echo "Android NDK: $NDK"
  echo "Build jobs: $JOBS"
  echo "Output directory: $OUT_DIR"
  echo "ABI: arm64-v8a"
  echo
  sha256sum "$STAGE"/*.so
} | tee "$ROOT/native-engine/ARM64-BUILD-MANIFEST.txt"

echo "Native core build and ARM64 staging completed. JNI bootstrap/runtime and the NiceExcel XLSX calculation adapter remain separate integration milestones."
