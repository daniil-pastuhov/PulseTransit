#!/usr/bin/env bash
# Installs the release APK built by GitHub Actions onto a connected device and launches it.
#
#   scripts/install-ci-apk.sh                 # latest successful run on main, auto-picked device
#   scripts/install-ci-apk.sh -s HVA6G0AF     # a specific device
#   scripts/install-ci-apk.sh -b my-branch    # latest successful run on another branch
#   scripts/install-ci-apk.sh -r 35915861599  # a specific run
#   scripts/install-ci-apk.sh --reinstall     # uninstall first (WIPES app settings) — needed when
#                                             # the device has a build signed with another key
#
# Needs `gh` (authenticated) and `adb` on PATH.
set -euo pipefail

PACKAGE=com.homieapps.pulsetransit
WORKFLOW=ci.yml
ARTIFACT=app-release

branch=main
run_id=""
serial="${ANDROID_SERIAL:-}"
reinstall=false

usage() {
    sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -s | --serial) serial="$2"; shift 2 ;;
        -b | --branch) branch="$2"; shift 2 ;;
        -r | --run) run_id="$2"; shift 2 ;;
        --reinstall) reinstall=true; shift ;;
        -h | --help) usage 0 ;;
        *) echo "Unknown option: $1" >&2; usage 1 ;;
    esac
done

for tool in gh adb; do
    command -v "$tool" > /dev/null || { echo "error: $tool not found on PATH" >&2; exit 1; }
done

cd "$(dirname "$0")/.."

# Pick the device: an explicit serial wins; otherwise the only physical device, falling back to
# the only device of any kind. Anything ambiguous is an error rather than a guess.
if [ -z "$serial" ]; then
    devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    physical=$(printf '%s\n' "$devices" | grep -v '^emulator-' || true)
    if [ "$(printf '%s' "$physical" | grep -c .)" -eq 1 ]; then
        serial="$physical"
    elif [ "$(printf '%s' "$devices" | grep -c .)" -eq 1 ]; then
        serial="$devices"
    else
        echo "error: can't pick a device automatically; pass -s <serial>. Connected:" >&2
        adb devices -l >&2
        exit 1
    fi
fi
model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
echo "Device:   $serial ($model)"

if [ -z "$run_id" ]; then
    run_id=$(gh run list --workflow "$WORKFLOW" --branch "$branch" --status success --limit 1 \
        --json databaseId --jq '.[0].databaseId // empty')
    [ -n "$run_id" ] || { echo "error: no successful $WORKFLOW run on $branch" >&2; exit 1; }
fi
gh run view "$run_id" --json headSha,headBranch,displayTitle,createdAt \
    --jq '"Run:      '"$run_id"' \(.headBranch)@\(.headSha[0:7]) \(.createdAt)\nCommit:   \(.displayTitle)"'

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
gh run download "$run_id" --name "$ARTIFACT" --dir "$tmp"
apk=$(find "$tmp" -name '*.apk' | head -n 1)
[ -n "$apk" ] || { echo "error: artifact $ARTIFACT has no APK" >&2; exit 1; }

if $reinstall; then
    echo "Uninstalling $PACKAGE (app settings will be lost)…"
    adb -s "$serial" uninstall "$PACKAGE" > /dev/null || true
fi

echo "Installing…"
if ! output=$(adb -s "$serial" install -r "$apk" 2>&1); then
    echo "$output" >&2
    if printf '%s' "$output" | grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE'; then
        echo >&2
        echo "The installed app is signed with a different key (probably a local debug build)." >&2
        echo "Re-run with --reinstall to uninstall it first; this wipes the app's settings." >&2
    fi
    exit 1
fi

adb -s "$serial" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
echo "Installed and launched on $model."
