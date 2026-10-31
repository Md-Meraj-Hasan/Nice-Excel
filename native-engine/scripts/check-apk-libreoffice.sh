#!/usr/bin/env bash
set -euo pipefail
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [[ ! -s "$APK" ]]; then
  echo "ERROR: APK not found: $APK" >&2
  exit 2
fi
if ! command -v unzip >/dev/null 2>&1; then
  echo "ERROR: unzip is required." >&2
  exit 3
fi
ENTRY="lib/arm64-v8a/liblo-native-code.so"
if ! unzip -Z1 "$APK" | grep -Fxq "$ENTRY"; then
  echo "ERROR: APK was built without $ENTRY. Refusing to report a LibreOffice-enabled build." >&2
  echo "APK native entries:" >&2
  unzip -Z1 "$APK" | grep '^lib/' >&2 || true
  exit 4
fi
SIZE=$(unzip -p "$APK" "$ENTRY" | wc -c | tr -d ' ')
if [[ "$SIZE" -lt 1000000 ]]; then
  echo "ERROR: $ENTRY is unexpectedly small ($SIZE bytes); refusing likely placeholder/stub." >&2
  exit 5
fi
echo "OK: APK contains $ENTRY ($SIZE bytes)."
unzip -Z1 "$APK" | grep '^lib/arm64-v8a/.*\.so$' | sort
