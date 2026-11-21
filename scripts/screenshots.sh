#!/usr/bin/env bash
# Screenshots of every demo screen from a running emulator or a phone over adb:
#
#     scripts/screenshots.sh [lang] [out dir] [demo names…]
#
# Installs the debug build, opens each `--es demo <name>` (made-up people,
# no network) and saves <out>/<lang>/<name>.png. See Demo.kt for the names.
set -euo pipefail
cd "$(dirname "$0")/.."
LANG_CODE=${1:-ru}
OUT=${2:-build/shots}
shift $(( $# > 2 ? 2 : $# )) || true
ADB=${ADB:-adb}
NAMES=("$@")
if [ ${#NAMES[@]} -eq 0 ]; then
  NAMES=(signin home sent anyone empty start signal paused offHere book contact add choose toys board pair device deviceWho deviceDoes deviceBoard settings settingsAccount settingsWho notifyLook notifyIntro how)
fi
mkdir -p "$OUT/$LANG_CODE"
$ADB install -r -t app/build/outputs/apk/debug/app-debug.apk >/dev/null
for name in "${NAMES[@]}"; do
  $ADB shell am force-stop me.lovearound.android
  $ADB shell am start -n me.lovearound.android/.MainActivity --es demo "$name" --es lang "$LANG_CODE" >/dev/null
  case "$name" in
    sent|anyone) sleep 4.0 ;;
    signal) sleep 3.0 ;;
    *) sleep 2.6 ;;
  esac
  $ADB exec-out screencap -p > "$OUT/$LANG_CODE/$name.png"
  echo "$OUT/$LANG_CODE/$name.png"
done
