#!/usr/bin/env bash
# Measures the shared embedding model on a connected device (real phone or emulator): load time, backend (GPU/CPU),
# memory, latency for one client, two clients at once and an interactive client next to background work.
# Keeps the downloaded models: installs the app and the test APK and runs the benchmark with `am instrument`
# instead of connectedDebugAndroidTest (which uninstalls the app at the end).
#
#   scripts/bench-device.sh [-s SERIAL] [-n REQUESTS]
#
# Needs: OndolSaem's active embedding model downloaded on the device. Memory is measured inside the run (the host
# process ends with the instrumentation).
# Output: build/bench/<device>-<model>-<date>.txt (also printed).
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ROOT"

N=30
while getopts "s:n:" o; do
    case $o in
        s) export ANDROID_SERIAL="$OPTARG" ;;
        n) N="$OPTARG" ;;
        *) echo "usage: $0 [-s SERIAL] [-n REQUESTS]"; exit 2 ;;
    esac
done

adb get-state >/dev/null || { echo "no device"; exit 1; }
DEVICE=$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '_')
echo "device: $DEVICE ($(adb shell getprop ro.build.version.release | tr -d '\r'))"

adb shell pm path "$APP_ID" >/dev/null 2>&1 || echo "OndolSaem is not installed yet: it will be, but without models the benchmark is skipped"

./gradlew -q :app:installDebug :app:installDebugAndroidTest
adb logcat -c
adb shell am instrument -w -r \
    -e class io.github.taehunkim.ondolsaem.SharedModelBench -e bench 1 -e benchN "$N" \
    "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" > /tmp/ondolsaem-bench-instr.$$ || true

RESULT=$(adb logcat -d -s OndolSaem.Bench:I -v raw | grep -v '^-' || true)
if [ -z "$RESULT" ]; then
    echo "no benchmark output; instrumentation said:"; tail -20 /tmp/ondolsaem-bench-instr.$$; rm -f /tmp/ondolsaem-bench-instr.$$
    exit 1
fi
rm -f /tmp/ondolsaem-bench-instr.$$
MODEL=$(echo "$RESULT" | sed -n 's/.*model=\([^ ]*\).*/\1/p' | head -1)
mkdir -p build/bench
OUT="build/bench/${DEVICE}-${MODEL:-model}-$(date +%Y%m%d-%H%M).txt"
{
    echo "# OndolSaem shared-model benchmark, $(date '+%Y-%m-%d %H:%M'), $(git rev-parse --short HEAD)"
    echo "$RESULT"
    echo
    echo "# backend lines from the host's log"
    adb logcat -d | grep -iE "OndolSaem.EG2|OndolSaem.Chat|gpu|opencl" | grep -i "$APP_ID\|OndolSaem" | tail -5 || true
} > "$OUT"
cat "$OUT"
echo "saved: $OUT"
