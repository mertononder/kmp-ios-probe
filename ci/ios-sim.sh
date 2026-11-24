#!/usr/bin/env bash
# Builds the app for the iOS simulator on a Mac, launches every demo screen and saves a screenshot of each.
# All CI logic lives here so the same script runs on GitHub Actions, Codemagic or a borrowed Mac.
#   ci/ios-sim.sh [screen ...]
set -euo pipefail

APP_NAME="Probe"
BUNDLE_ID="dev.probe.kmp"
SCREEN_ARG="-probeScreen"
DEFAULT_SCREENS=(light dark)

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
  sleep 15
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
xcrun simctl spawn "$UDID" log show --last 5m --predicate "process == \"$APP_NAME\"" --style compact > "$OUT/app.log" 2>/dev/null || true
cp "$HOME"/Library/Logs/DiagnosticReports/"$APP_NAME"*.ips "$OUT/" 2>/dev/null || true
exit "$CRASHED"
