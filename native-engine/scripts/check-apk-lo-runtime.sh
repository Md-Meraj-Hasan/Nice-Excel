#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="com.meraj.niceexcel"

if [[ ! -s "$APK" ]]; then
  echo "ERROR: APK not found: $APK" >&2
  exit 2
fi
command -v unzip >/dev/null 2>&1 || { echo "ERROR: unzip is required." >&2; exit 3; }

required=(
  "assets/program/fundamentalrc"
  "assets/unpack/program/offapi.rdb"
  "assets/unpack/etc/fonts/fonts.conf"
  "assets/share/registry/main.xcd"
  "res/raw/lo_license.txt"
  "res/raw/lo_notice.txt"
)
for entry in "${required[@]}"; do
  if ! unzip -Z1 "$APK" | grep -Fxq "$entry"; then
    echo "ERROR: Required LibreOffice runtime entry is missing from APK: $entry" >&2
    exit 4
  fi
done

FONT_CONFIG="$(unzip -p "$APK" assets/unpack/etc/fonts/fonts.conf)"
if ! grep -Fq "/data/data/$PACKAGE/fontconfig" <<<"$FONT_CONFIG"; then
  echo "ERROR: packaged fonts.conf does not target the NiceExcel application package." >&2
  exit 5
fi

FONT_COUNT="$(unzip -Z1 "$APK" | grep -Ec '^assets/unpack/user/fonts/[^/]+\.ttf$' || true)"
if [[ "$FONT_COUNT" -lt 1 ]]; then
  echo "ERROR: no LibreOffice runtime fonts were packaged." >&2
  exit 6
fi

echo "OK: APK contains required LibreOffice runtime data, font configuration, fonts, and license notices."
echo "Runtime fonts packaged: $FONT_COUNT"
