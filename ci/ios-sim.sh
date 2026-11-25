#!/usr/bin/env bash
# Builds the app for the iOS simulator on a Mac, launches every demo screen and saves a screenshot of each.
# All CI logic lives here so the same script runs on GitHub Actions, Codemagic or a borrowed Mac.
#   ci/ios-sim.sh [screen ...]
set -euo pipefail

APP_NAME="Probe"
BUNDLE_ID="dev.probe.kmp"
SCREEN_ARG="-probeScreen"
DEFAULT_SCREENS=(light dark map gate)
# Set RIDE_PROBE=0 to skip the background ride-session check (about 3 minutes).
RIDE_PROBE="${RIDE_PROBE:-1}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/ios-shots"
DERIVED="$ROOT/build/DerivedData"
SCREENS=("$@")
if [ ${#SCREENS[@]} -eq 0 ]; then SCREENS=("${DEFAULT_SCREENS[@]}"); fi
mkdir -p "$OUT"

# Pick an iPhone simulator whose iOS version matches the selected Xcode's SDK (newest runtime otherwise).
SDK_VERSION="$(xcrun --sdk iphonesimulator --show-sdk-version 2>/dev/null || true)"
UDID="$(xcrun simctl list devices available -j | python3 -c '
import json, sys
devices = json.load(sys.stdin)["devices"]
sdk = sys.argv[1] if len(sys.argv) > 1 else ""
want = ".iOS-" + "-".join(sdk.split(".")[:2])
runtimes = sorted((r for r in devices if ".iOS-" in r), reverse=True)
runtimes.sort(key=lambda r: not r.endswith(want))
phones = [d for r in runtimes for d in devices[r] if d["name"].startswith("iPhone")]
preferred = [d for d in phones if "Pro Max" not in d["name"] and "Plus" not in d["name"] and "SE" not in d["name"]]
print((preferred or phones)[0]["udid"])
' "$SDK_VERSION")"
echo "SDK $SDK_VERSION, simulator $UDID"
xcrun simctl list devices available | grep "$UDID" || true

echo "::group::shared Kotlin tests on the iOS simulator"
cd "$ROOT"
./gradlew :shared:iosSimulatorArm64Test --console=plain
echo "::endgroup::"

echo "::group::link shared framework"
/usr/bin/time -l ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 --console=plain 2> "$OUT/link-time.txt" || { cat "$OUT/link-time.txt"; exit 1; }
tail -n 20 "$OUT/link-time.txt"
echo "::endgroup::"

echo "::group::xcodegen"
cd "$ROOT/iosApp"
xcodegen generate
xcodebuild -list -project "$APP_NAME.xcodeproj"
echo "::endgroup::"

# Boot only now: the simulator and the Kotlin/Native link would otherwise compete for the runner's memory.
xcrun simctl boot "$UDID" || true

echo "::group::xcodebuild"
set +e
xcodebuild -project "$APP_NAME.xcodeproj" -scheme "$APP_NAME" -configuration Debug \
  -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath "$DERIVED" build > "$OUT/xcodebuild.log" 2>&1
XCB=$?
set -e
tail -n 80 "$OUT/xcodebuild.log"
echo "::endgroup::"
if [ "$XCB" -ne 0 ]; then
  echo "xcodebuild failed ($XCB); error lines from xcodebuild.log (the full log is in the ios-shots artifact):"
  grep -nE "error:|FAILURE:|What went wrong|BUILD FAILED|Undefined symbols|ld: " -A 12 "$OUT/xcodebuild.log" | tail -n 300 || true
  exit "$XCB"
fi

APP="$DERIVED/Build/Products/Debug-iphonesimulator/$APP_NAME.app"
echo "bundled assets:"; find "$APP/assets" -maxdepth 2 | head -n 20 || true
du -sh "$APP" | tee "$OUT/app-size.txt"
xcrun simctl bootstatus "$UDID" -b
xcrun simctl install "$UDID" "$APP"
xcrun simctl status_bar "$UDID" override --time "09:41" --batteryState charged --batteryLevel 100 || true

SIM_TMP="$HOME/Library/Developer/CoreSimulator/Devices/$UDID/data/tmp"
CRASHED=0
for screen in "${SCREENS[@]}"; do
  xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
  # Kotlin/Native reports an uncaught exception on stderr only, so keep the app's stdout/stderr.
  launch() {
    xcrun simctl launch --stdout=/tmp/app-stdout.log --stderr=/tmp/app-stderr.log "$UDID" "$BUNDLE_ID" "$SCREEN_ARG" "$screen"
  }
  launch || { sleep 10; launch; }
  # The native-parts screen does network and engine work before it settles.
  if [ "$screen" = "gate" ]; then sleep 35; else sleep 15; fi
  xcrun simctl io "$UDID" screenshot "$OUT/$screen.png"
  echo "shot $OUT/$screen.png"
  for f in app-stdout.log app-stderr.log; do
    cp "$SIM_TMP/$f" "$OUT/$screen-$f" 2>/dev/null || cp "/tmp/$f" "$OUT/$screen-$f" 2>/dev/null || true
  done
  running="$(xcrun simctl spawn "$UDID" launchctl list 2>/dev/null || true)"
  if [[ "$running" != *"UIKitApplication:$BUNDLE_ID"* ]]; then
    CRASHED=1
    echo "::error::$BUNDLE_ID is not running after launching '$screen' (probable crash); stderr follows"
    cat "$OUT/$screen-app-stderr.log" 2>/dev/null || true
  fi
done

container_file() {
  local data
  data="$(xcrun simctl get_app_container "$UDID" "$BUNDLE_ID" data 2>/dev/null || true)"
  [ -n "$data" ] && cp "$data/Documents/$1" "$OUT/$1" 2>/dev/null && echo "fetched $1" || echo "missing $1"
}
container_file probe-report.json || true
DATA_DIR="$(xcrun simctl get_app_container "$UDID" "$BUNDLE_ID" data 2>/dev/null || true)"
if [ -n "$DATA_DIR" ]; then
  cp "$DATA_DIR"/Documents/probe-*.txt "$OUT/" 2>/dev/null || true
  echo "probe results:"; for f in "$OUT"/probe-*.txt; do [ -f "$f" ] && echo "  $(basename "$f" .txt): $(cat "$f")"; done
fi

if [ "$RIDE_PROBE" = "1" ]; then
  echo "::group::ride session probe (background location + audio)"
  xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
  xcrun simctl privacy "$UDID" grant location "$BUNDLE_ID" || echo "::warning::could not pre-grant location"
  # Simulated ride along a coastal road, 15 m/s, one fix per second.
  xcrun simctl location "$UDID" start --speed=15 --interval=1     41.0082,28.9784 41.0250,28.9850 41.0420,29.0050 41.0560,29.0270 41.0780,29.0400 41.1000,29.0560 41.1200,29.0600     || echo "::warning::simctl location start failed"
  xcrun simctl launch --stdout=/tmp/ride-stdout.log --stderr=/tmp/ride-stderr.log "$UDID" "$BUNDLE_ID" "$SCREEN_ARG" ride
  sleep 25
  xcrun simctl io "$UDID" screenshot "$OUT/ride-foreground.png"
  container_file ride-spike.json && cp "$OUT/ride-spike.json" "$OUT/ride-spike-foreground.json" || true
  # Send the app to the background by opening Settings, and leave it there.
  xcrun simctl launch "$UDID" com.apple.Preferences || true
  sleep 150
  xcrun simctl io "$UDID" screenshot "$OUT/ride-while-backgrounded.png"
  container_file ride-spike.json || true
  # Bring the probe back (it is still running, so this only foregrounds it).
  xcrun simctl launch "$UDID" "$BUNDLE_ID" "$SCREEN_ARG" ride || true
  sleep 5
  xcrun simctl io "$UDID" screenshot "$OUT/ride-after.png"
  xcrun simctl location "$UDID" clear || true
  cp "$SIM_TMP/ride-stderr.log" "$OUT/ride-stderr.log" 2>/dev/null || cp /tmp/ride-stderr.log "$OUT/ride-stderr.log" 2>/dev/null || true
  echo "ride evidence:"; cat "$OUT/ride-spike.json" 2>/dev/null || echo "(none)"
  echo "::endgroup::"
fi

if [ "${DEVICE_LINK:-0}" = "1" ]; then
  # Release build for a real iPhone (arm64, optimised Kotlin/Native), unsigned: proves the device link without
  # an Apple account. Nothing is installed anywhere.
  echo "::group::Release device build (unsigned)"
  cd "$ROOT/iosApp"
  set +e
  START=$(date +%s)
  xcodebuild -project "$APP_NAME.xcodeproj" -scheme "$APP_NAME" -configuration Release     -destination "generic/platform=iOS" -derivedDataPath "$DERIVED-device"     CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build > "$OUT/xcodebuild-device.log" 2>&1
  DEV=$?
  set -e
  END=$(date +%s)
  tail -n 30 "$OUT/xcodebuild-device.log"
  echo "::endgroup::"
  echo "device build exit=$DEV in $((END - START)) s" | tee "$OUT/device-build.txt"
  if [ "$DEV" -ne 0 ]; then
    grep -nE "error:|What went wrong|Undefined symbols|duplicate symbol|ld: " -A 12 "$OUT/xcodebuild-device.log" | tail -n 200 || true
    CRASHED=1
  else
    DEV_APP="$DERIVED-device/Build/Products/Release-iphoneos/$APP_NAME.app"
    du -sh "$DEV_APP" | tee -a "$OUT/device-build.txt"
    ls -la "$DEV_APP/$APP_NAME" | tee -a "$OUT/device-build.txt"
  fi
  cd "$ROOT"
fi

xcrun simctl spawn "$UDID" log show --last 5m --predicate "process == \"$APP_NAME\"" --style compact > "$OUT/app.log" 2>/dev/null || true
cp "$HOME"/Library/Logs/DiagnosticReports/"$APP_NAME"*.ips "$OUT/" 2>/dev/null || true
exit "$CRASHED"
