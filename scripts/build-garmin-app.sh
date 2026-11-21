#!/bin/bash
# Builds the Garmin watch app (garminApp/) for one device with the Connect IQ SDK.
#
#   scripts/build-garmin-app.sh [device] [extra monkeyc flags]     # device defaults to fenix847mm
#   scripts/build-garmin-app.sh store                              # the package to upload to the Connect IQ Store
#
# "store" builds garminApp/build/PulseTransit.iq: a release build for every device in the manifest,
# which is what the Connect IQ Store's upload form takes (apps.garmin.com/developer).
#
# Needs the SDK and that device's files, installed with Garmin's SDK Manager, and a developer key
# (GARMIN_DEVELOPER_KEY, default ~/.garmin/pulsetransit_developer_key.der). The key is not in the
# repo: a store update must be signed with the key the first upload was.
set -euo pipefail

cd "$(dirname "$0")/../garminApp"
device="${1:-fenix847mm}"
shift || true

sdk_cfg="$HOME/Library/Application Support/Garmin/ConnectIQ/current-sdk.cfg"
sdk="${CONNECTIQ_SDK:-$(cat "$sdk_cfg" 2>/dev/null || true)}"
if [ -z "$sdk" ] || [ ! -x "${sdk%/}/bin/monkeyc" ]; then
  echo "Connect IQ SDK not found; install one with the SDK Manager or set CONNECTIQ_SDK." >&2
  exit 1
fi
key="${GARMIN_DEVELOPER_KEY:-$HOME/.garmin/pulsetransit_developer_key.der}"
if [ ! -f "$key" ]; then
  echo "Developer key not found at $key; set GARMIN_DEVELOPER_KEY." >&2
  exit 1
fi

mkdir -p build
if [ "$device" = "store" ]; then
  "${sdk%/}/bin/monkeyc" -e -r -f monkey.jungle -o build/PulseTransit.iq -y "$key" -w -l 2 "$@"
  echo "build/PulseTransit.iq"
  exit 0
fi
"${sdk%/}/bin/monkeyc" -d "$device" -f monkey.jungle -o "build/PulseTransit-$device.prg" -y "$key" -w -l 2 "$@"
echo "build/PulseTransit-$device.prg"
