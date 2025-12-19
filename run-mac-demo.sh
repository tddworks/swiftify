#!/bin/bash
# run-mac-demo.sh - Build and run the Swiftify macOS demo app

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Swiftify macOS Demo ==="
echo ""

# Step 1: Build Kotlin framework (Swift code auto-generated!)
echo "[1/2] Building Kotlin framework + generating Swift code..."
./gradlew :sample:linkDebugFrameworkMacosArm64 --quiet

# Step 2: Build and run macOS app
echo "[2/2] Building and running macOS app..."
cd sample/macApp

# Build the app
xcodebuild -project macApp.xcodeproj \
    -scheme macApp \
    -configuration Debug \
    -destination 'platform=macOS' \
    build \
    2>/dev/null | grep -E "(error:|warning:|BUILD|Compiling|Linking)" || true

# Find and run the built app
APP_PATH=$(xcodebuild -project macApp.xcodeproj -scheme macApp -showBuildSettings 2>/dev/null | grep "BUILT_PRODUCTS_DIR" | head -1 | awk '{print $3}')
if [ -d "$APP_PATH/macApp.app" ]; then
    echo ""
    echo "=== Launching macOS Demo App ==="
    open "$APP_PATH/macApp.app"
else
    echo "App built. Opening Xcode to run..."
    open macApp.xcodeproj
fi
