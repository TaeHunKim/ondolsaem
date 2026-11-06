#!/usr/bin/env bash
# Checks the NPU builds on a connected phone: each embedding model whose NPU build is downloaded must load on the
# NPU and give about the same vectors as the CPU build (NpuParityTest; logs cosine and time per input).
# Keeps the downloaded models: installs the app and the test APK and runs with `am instrument` instead of
# connectedDebugAndroidTest (which uninstalls the app at the end).
#
#   scripts/npu-check.sh [-s SERIAL]
#
# Needs: in OndolSaem, an embedding model and its NPU build ("Download NPU build" under the model).
# Output: build/npu/<device>-<date>.txt (also printed).
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ROOT"

while getopts "s:" o; do
    case $o in
        s) export ANDROID_SERIAL="$OPTARG" ;;
        *) echo "usage: $0 [-s SERIAL]"; exit 2 ;;
    esac
done

adb get-state >/dev/null || { echo "no device"; exit 1; }
DEVICE=$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '_')
echo "device: $DEVICE, chip $(adb shell getprop ro.soc.manufacturer | tr -d '\r') $(adb shell getprop ro.soc.model | tr -d '\r')"

./gradlew -q :app:installDebug :app:installDebugAndroidTest
adb logcat -c
INSTR=$(adb shell am instrument -w -r -e class io.github.taehunkim.ondolsaem.NpuParityTest \
    "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" || true)

mkdir -p build/npu
OUT="build/npu/${DEVICE}-$(date +%Y%m%d-%H%M).txt"
{
    echo "# OndolSaem NPU check, $(date '+%Y-%m-%d %H:%M'), $(git rev-parse --short HEAD)"
    adb logcat -d -s OndolSaem.NpuTest:I OndolSaem.EG2:I OndolSaem.EG2:W OndolSaem.Npu:I -v brief | grep -v '^-' || true
    echo
    echo "# instrumentation"
    echo "$INSTR" | grep -E "INSTRUMENTATION_STATUS: (test|stack)=|INSTRUMENTATION_STATUS_CODE|OK \(|FAILURES|Tests run" || true
} > "$OUT"
cat "$OUT"
echo "saved: $OUT"
