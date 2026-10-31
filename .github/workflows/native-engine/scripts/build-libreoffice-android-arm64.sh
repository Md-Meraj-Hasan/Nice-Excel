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

# Force the 64-bit Android host triplet explicitly. LibreOffice's Android
# distro defaults to 32-bit ARM when --host is omitted; that produced the
# previous arm-linux-androideabi / armv7a build despite the workflow name.
cat > "$LO_SRC/autogen.input" <<AUTOGEN
--build=x86_64-unknown-linux-gnu
--host=aarch64-linux-android
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

# Fail before the expensive C++ build unless configure really selected AArch64.
CONFIG_LOG="$LO_SRC/config.log"
[[ -s "$CONFIG_LOG" ]] || fail "configure did not produce config.log; cannot verify Android ABI."
if ! grep -Eq "host_cpu='aarch64'" "$CONFIG_LOG" || \
   ! grep -Eq 'Target: aarch64[^[:space:]]*android21' "$CONFIG_LOG"; then
  {
    echo "ERROR: LibreOffice configure did not select Android AArch64."
    echo "Expected host_cpu=aarch64 and an NDK compiler target like aarch64-unknown-linux-android21."
    echo "Detected configure summary:"
    grep -E "^(host=|host_alias=|host_cpu=|host_os=)" "$CONFIG_LOG" || true
    grep -E 'Target: .*android' "$CONFIG_LOG" | sort -u | tail -10 || true
  } | tee "$ROOT/native-engine/ARM64-ABI-PREFLIGHT-FAILED.txt" >&2
  fail "Refusing to spend time compiling a non-ARM64 LibreOffice core."
fi
rm -f "$ROOT/native-engine/ARM64-ABI-PREFLIGHT-FAILED.txt"
echo "ABI preflight passed: LibreOffice configure selected AArch64 Android."
make -j"$JOBS"

# The Android Gradle project may create several copies for intermediates and
# variants. Inspect every candidate and select only a genuine ELF64 AArch64
# library; never select by filename/count alone.
READELF="$(command -v llvm-readelf || command -v readelf)"
mapfile -t ALL_CORES < <(find "$LO_SRC/android" -type f -name liblo-native-code.so \
  -not -path '*/.gradle/*' -print 2>/dev/null | sort)
[[ ${#ALL_CORES[@]} -gt 0 ]] || fail "No liblo-native-code.so was produced under $LO_SRC/android."

REPORT="$ROOT/native-engine/ARM64-CORE-DISCOVERY.txt"
: > "$REPORT"
ARM64_CORES=()
for candidate in "${ALL_CORES[@]}"; do
  class="$("$READELF" -h "$candidate" 2>/dev/null | awk -F: '/Class:/{gsub(/^[ \t]+/,"",$2); print $2}')"
  machine="$("$READELF" -h "$candidate" 2>/dev/null | awk -F: '/Machine:/{gsub(/^[ \t]+/,"",$2); print $2}')"
  printf 'Candidate: %s\n  ELF class: %s\n  Machine: %s\n' "$candidate" "$class" "$machine" | tee -a "$REPORT"
  if [[ "$class" == ELF64 && "$machine" == *AArch64* ]]; then
    ARM64_CORES+=("$candidate")
    echo '  RESULT: ARM64' | tee -a "$REPORT"
  else
    echo '  RESULT: ignored (not ARM64 ELF64)' | tee -a "$REPORT"
  fi
done

[[ ${#ARM64_CORES[@]} -gt 0 ]] || fail "Found ${#ALL_CORES[@]} core candidate(s), but none is ELF64 AArch64. See $REPORT."

# Prefer the strippedUIDebug output that matches the Android app build used by
# this workflow. If it is absent, accept multiple copies only when byte-identical.
PREFERRED=()
for candidate in "${ARM64_CORES[@]}"; do
  [[ "$candidate" == *"/strippedUIDebug/"* ]] && PREFERRED+=("$candidate")
done
if [[ ${#PREFERRED[@]} -eq 1 ]]; then
  CORE="${PREFERRED[0]}"
elif [[ ${#PREFERRED[@]} -gt 1 ]]; then
  fail "Multiple AArch64 strippedUIDebug cores found. Inspect $REPORT; refusing an ambiguous selection."
else
  declare -A HASH_TO_PATH=()
  for candidate in "${ARM64_CORES[@]}"; do
    hash="$(sha256sum "$candidate" | awk '{print $1}')"
    HASH_TO_PATH["$hash"]="$candidate"
  done
  [[ ${#HASH_TO_PATH[@]} -eq 1 ]] || fail "Multiple non-identical AArch64 core variants found. Inspect $REPORT; refusing an ambiguous selection."
  for candidate in "${ARM64_CORES[@]}"; do CORE="$candidate"; break; done
fi

OUT_DIR="$(dirname "$CORE")"
printf '\nSELECTED ARM64 CORE: %s\n' "$CORE" | tee -a "$REPORT"
printf 'Selected SHA-256: %s\n' "$(sha256sum "$CORE" | awk '{print $1}')" | tee -a "$REPORT"

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
  echo "ABI: arm64-v8a (ELF64 AArch64 verified)"
  echo "Discovery report: $REPORT"
  echo
  sha256sum "$STAGE"/*.so
} | tee "$ROOT/native-engine/ARM64-BUILD-MANIFEST.txt"

echo "Native core build and ARM64 staging completed. JNI bootstrap/runtime and the NiceExcel XLSX calculation adapter remain separate integration milestones."
