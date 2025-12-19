#!/bin/bash
# run-ios-demo.sh - Build and run the Swiftify iOS demo app in simulator

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Swiftify iOS Demo ==="
echo ""

# Step 1: Build Kotlin framework (Swift code auto-generated!)
echo "[1/2] Building Kotlin framework + generating Swift code..."
./gradlew :sample:linkDebugFrameworkIosSimulatorArm64 --quiet

# Step 2: Build and run iOS app
echo "[2/2] Building iOS app..."
cd sample/iosApp

# Build for iOS Simulator
xcodebuild -project iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Debug \
    -destination 'platform=iOS Simulator,name=iPhone 16' \
    build \
    2>/dev/null | grep -E "(error:|warning:|BUILD|Compiling|Linking)" || true

echo ""
echo "=== Opening Xcode for iOS Demo ==="
echo "Press Cmd+R in Xcode to run on simulator"
open iosApp.xcodeproj
