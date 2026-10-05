#!/bin/sh
# Xcode build phase: compiles the Kotlin `Shared` framework for the SDK and configuration Xcode is
# building, into shared/build/xcode-frameworks/, where the app target's FRAMEWORK_SEARCH_PATHS look.
#
# Xcode runs build phases without the user's shell profile, so Gradle may not find a JDK on its own.
# Gradle itself needs one to start (its toolchain then provisions JDK 17 for compiling).
set -eu

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
    export JAVA_HOME
fi

cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
