#!/bin/bash
# Builds the iOS app for a simulator and runs its UI tests (iosAppUITests), as CI's ios-build job
# does on pull requests. The tests run on fixed data (-PTUITest YES), so they need no network.
#
#   scripts/run-ios-ui-tests.sh                         # the newest iPhone 17 simulator
#   IOS_UI_TEST_DESTINATION='id=<udid>' scripts/run-ios-ui-tests.sh
#
# xcodebuild can stay alive after the tests have finished (seen with some Xcode builds), so the
# outcome is read from its log rather than its exit code, and it is stopped a minute after the
# tests are done. The result bundle, with failure screenshots and a recording, is in
# iosApp/build/UITests.xcresult.
set -uo pipefail

cd "$(dirname "$0")/.."
destination="${IOS_UI_TEST_DESTINATION:-platform=iOS Simulator,name=iPhone 17,OS=latest}"
result=iosApp/build/UITests.xcresult
log=iosApp/build/ui-tests.log
rm -rf "$result"
mkdir -p iosApp/build

xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
    -destination "$destination" -only-testing:iosAppUITests -resultBundlePath "$result" \
    CODE_SIGNING_ALLOWED=NO >"$log" 2>&1 &
pid=$!

finished_at=""
while kill -0 "$pid" 2>/dev/null; do
    if [ -z "$finished_at" ] && grep -qE "Executed [0-9]+ tests?," "$log"; then
        finished_at=$SECONDS
    fi
    if [ -n "$finished_at" ] && ((SECONDS - finished_at > 60)); then
        echo "xcodebuild is still running a minute after the tests finished; stopping it."
        kill "$pid"
        break
    fi
    sleep 5
done
wait "$pid" 2>/dev/null

grep -E "error:|Test Case .*(passed|failed)" "$log"
summary=$(grep -E "Executed [0-9]+ tests?," "$log" | tail -1)
if [ -z "$summary" ]; then
    echo "The UI tests did not run. The end of the log:"
    tail -40 "$log"
    exit 1
fi
echo "$summary"
echo "$summary" | grep -qE "with 0 failures"
