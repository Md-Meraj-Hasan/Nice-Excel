#!/usr/bin/env bash
# Build the official LibreOffice Android ARM64 core from a matching source tree.
# This script deliberately does not download or mix third-party .so files.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LO_SRC="${1:-${LIBREOFFICE_SRC:-}}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
JOBS="${LO_BUILD_JOBS:-2}"
STAGE="$ROOT/native-engine/jniLibs/arm64-v8a"

fail() { echo "ERROR: $*" >&2; exit 2; }
[[ -n "$LO_SRC" && -d "$LO_SRC" ]] || fail "Pass the path to a prepared LibreOffice core source checkout as argument 1 (or set LIBREOFFICE_SRC)."
[[ -x "$LO_SRC/autogen.sh" ]] || fail "No autogen.sh found in $LO_SRC; expected the official LibreOffice core source tree."
[[ -n "$SDK" && -d "$SDK" ]] || fail "ANDROID_SDK_ROOT (or ANDROID_HOME) must point to the Android SDK."
[[ -n "$NDK" && -d "$NDK" ]] || fail "ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) must point to the Android NDK."
[[ -x "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || fail "Linux Android NDK LLVM toolchain not found under $NDK. This build script expects a Linux host."
command -v make >/dev/null || fail "make is required."
command -v sha256sum >/dev/null || fail "sha256sum is required."
command -v readelf >/dev/null || command -v llvm-readelf >/dev/null || fail "readelf or llvm-readelf is required."

SDK_REAL="$(cd "$SDK" && pwd)"
NDK_REAL="$(cd "$NDK" && pwd)"
LO_SRC="$(cd "$LO_SRC" && pwd)"

# LibreOffice's Android distro config is responsible for the Android target
# details. Keep this minimal and aligned with upstream README.cross guidance.
cat > "$LO_SRC/autogen.input" <<AUTOGEN
--build=x86_64-unknown-linux-gnu
--with-distro=LibreOfficeAndroid
--with-android-sdk=$SDK_REAL
--with-android-ndk=$NDK_REAL
--disable-debug
--disable-dbgutil
AUTOGEN

printf '\n== LibreOffice source ==\n%s\n' "$LO_SRC"
printf '== Android SDK ==\n%s\n' "$SDK_REAL"
printf '== Android NDK ==\n%s\n' "$NDK_REAL"
printf '== Build jobs ==\n%s\n' "$JOBS"
if git -C "$LO_SRC" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  printf '== LibreOffice commit ==\n%s\n' "$(git -C "$LO_SRC" rev-parse HEAD)"
else
  echo "WARNING: source tree is not a Git checkout; commit provenance cannot be recorded." >&2
fi

cd "$LO_SRC"
./autogen.sh
make -j"$JOBS"

# Upstream's Android build output location can vary with the exact source
# revision. Locate the named official combined core, then stage only the
# matching .so siblings from that same ABI output directory.
mapfile -t CORES < <(find "$LO_SRC/android" -type f -name liblo-native-code.so -print 2>/dev/null | sort)
[[ ${#CORES[@]} -eq 1 ]] || fail "Expected exactly one liblo-native-code.so under $LO_SRC/android; found ${#CORES[@]}. Inspect build output and adapt for this source revision."
CORE="${CORES[0]}"
OUT_DIR="$(dirname "$CORE")"
READELF="$(command -v llvm-readelf || command -v readelf)"
CLASS="$("$READELF" -h "$CORE" | awk -F: '/Class:/{gsub(/^[ \t]+/,"",$2); print $2}')"
MACHINE="$("$READELF" -h "$CORE" | awk -F: '/Machine:/{gsub(/^[ \t]+/,"",$2); print $2}')"
[[ "$CLASS" == ELF64 && "$MACHINE" == *AArch64* ]] || fail "Core is not an ARM64 ELF64 library: class=$CLASS machine=$MACHINE"

mkdir -p "$STAGE"
find "$STAGE" -maxdepth 1 -type f -name '*.so' -delete
while IFS= read -r -d '' so; do
  cp -f "$so" "$STAGE/$(basename "$so")"
done < <(find "$OUT_DIR" -maxdepth 1 -type f -name '*.so' -print0)

[[ -s "$STAGE/liblo-native-code.so" ]] || fail "Core was not staged correctly."
{
  echo "LibreOffice source: $LO_SRC"
  if git -C "$LO_SRC" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "LibreOffice commit: $(git -C "$LO_SRC" rev-parse HEAD)"
  fi
  echo "Android NDK: $NDK_REAL"
  echo "Android SDK: $SDK_REAL"
  echo "Build jobs: $JOBS"
  echo "Staged ABI: arm64-v8a"
  echo
  sha256sum "$STAGE"/*.so
} | tee "$ROOT/native-engine/ARM64-BUILD-MANIFEST.txt"

echo "SUCCESS: native core and same-directory shared libraries staged in $STAGE"
echo "NEXT: run check-arm64-core.sh and check-native-dependency-closure.sh, then build the APK."
echo "NOTE: this does not integrate JNI bootstrap Java classes, UNO runtime data, or the XLSX recalculation adapter."
