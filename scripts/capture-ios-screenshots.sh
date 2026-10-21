#!/bin/bash
# Captures raw App Store screenshots of the iOS app in a booted simulator, with live Entur and MET
# data, for scripts/frame-ios-screenshots.swift to caption.
#
#   scripts/capture-ios-screenshots.sh <simulator-udid> <app-path> <out-dir> <locale> <device-name>
#
# <locale> is an App Store locale (en-GB, no). The app must be a Debug build (it reads the
# -PTOpenSettings launch argument). Scenes: On the spot at Oslo S, board in light theme, board in dark
# theme, Settings.
set -euo pipefail

SIM=$1 APP=$2 OUT=$3 LOCALE=$4 DEVICE=$5
BUNDLE=com.homieapps.pulsetransit
case $LOCALE in
  en-GB) LANGS="(en-GB)" REGION=en_GB ;;
  no) LANGS="(nb)" REGION=nb_NO ;;
  *) echo "unknown locale $LOCALE" >&2; exit 1 ;;
esac
mkdir -p "$OUT/$LOCALE"

xcrun simctl install "$SIM" "$APP"
# On the spot follows the device: give it Oslo S.
xcrun simctl privacy "$SIM" grant location "$BUNDLE"
xcrun simctl location "$SIM" set 59.9111,10.7528
# A tidy status bar; the clock is left real so it matches the board's own clock.
xcrun simctl status_bar "$SIM" override --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularBars 4 \
  --dataNetwork wifi 2>/dev/null || true

# A typical setup: Oslo S and Jernbanetorget, weather for central Oslo, fastest route to Lillestrøm.
FAVORITES='[{"id":"NSR:StopPlace:59872","name":"Oslo S","locality":"Oslo"},{"id":"NSR:StopPlace:58366","name":"Jernbanetorget","locality":"Oslo"}]'
DESTINATIONS='[{"id":"NSR:StopPlace:62339","name":"Lillestrøm stasjon","locality":"Lillestrøm"}]'
seed() {
  xcrun simctl terminate "$SIM" "$BUNDLE" 2>/dev/null || true
  local write="xcrun simctl spawn $SIM defaults write $BUNDLE"
  $write favorites -string "$FAVORITES"
  $write destination_stations -string "$DESTINATIONS"
  $write saved_lat -string 59.9111
  $write saved_lon -string 10.7528
  $write camera_motion_wake -bool false
  $write burn_in_protection -string OFF # pixel shift would offset the shot
  $write theme_mode -string "$1"
  $write date_display_style -string "$2"
}

plist_string() { local escaped=${1//\"/\\\"}; printf '"%s"' "$escaped"; }

shot() { # name, mode, extra launch args...
  # The mode, stops and destination are launch arguments, which override the stored ones: a value
  # seeded with defaults write is shadowed by the one the app saved itself (e.g. by a recorded
  # preview's tour, or by using the app in that simulator).
  # A JSON value is passed as a quoted plist string, or the argument domain drops it.
  local name=$1 mode=$2; shift 2
  xcrun simctl launch "$SIM" "$BUNDLE" -AppleLanguages "$LANGS" -AppleLocale "$REGION" -app_mode "$mode" \
    -favorites "$(plist_string "$FAVORITES")" -destination_stations "$(plist_string "$DESTINATIONS")" "$@" >/dev/null
  sleep 12 # departures, weather and the fastest route arrive from the network
  xcrun simctl io "$SIM" screenshot "$OUT/$LOCALE/${DEVICE}_$name.png" >/dev/null 2>&1
  echo "captured $OUT/$LOCALE/${DEVICE}_$name.png"
}

seed LIGHT BESIDE && shot 0_on_spot ON_SPOT
seed LIGHT BESIDE && shot 1_board_light BOARD
seed DARK LONG && shot 2_board_dark BOARD
seed LIGHT BESIDE && shot 3_settings BOARD -PTOpenSettings YES
xcrun simctl status_bar "$SIM" clear 2>/dev/null || true
