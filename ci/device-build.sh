#!/usr/bin/env bash
# Release build for a real iPhone (arm64, optimised Kotlin/Native), unsigned. Proves the device link without an
# Apple account; nothing is installed anywhere. Runs as its own CI job because the optimised link is slow.
set -euo pipefail

APP_NAME="Probe"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/device"
DERIVED="$ROOT/build/DerivedData-device"
mkdir -p "$OUT"
cd "$ROOT"

echo "::group::Kotlin release framework for iosArm64"
START=$(date +%s)
/usr/bin/time -l ./gradlew :shared:linkReleaseFrameworkIosArm64 --console=plain 2> "$OUT/kotlin-link-time.txt" | tail -n 30
echo "kotlin release link: $(( $(date +%s) - START )) s" | tee "$OUT/device-build.txt"
grep -E "real|maximum resident" "$OUT/kotlin-link-time.txt" | tee -a "$OUT/device-build.txt" || true
echo "::endgroup::"

echo "::group::xcodegen"
cd "$ROOT/iosApp"
xcodegen generate
echo "::endgroup::"

echo "::group::xcodebuild Release iphoneos (unsigned)"
START=$(date +%s)
set +e
xcodebuild -project "$APP_NAME.xcodeproj" -scheme "$APP_NAME" -configuration Release   -destination "generic/platform=iOS" -derivedDataPath "$DERIVED"   CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build > "$OUT/xcodebuild-device.log" 2>&1
XCB=$?
set -e
echo "xcodebuild release: exit=$XCB in $(( $(date +%s) - START )) s" | tee -a "$OUT/device-build.txt"
echo "::endgroup::"
if [ "$XCB" -ne 0 ]; then
  grep -nE "error:|What went wrong|Undefined symbols|duplicate symbol|ld: " -A 12 "$OUT/xcodebuild-device.log" | tail -n 200 || true
  exit "$XCB"
fi
APP="$DERIVED/Build/Products/Release-iphoneos/$APP_NAME.app"
du -sh "$APP" | tee -a "$OUT/device-build.txt"
ls -la "$APP/$APP_NAME" | tee -a "$OUT/device-build.txt"
