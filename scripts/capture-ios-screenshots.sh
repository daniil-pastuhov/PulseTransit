#!/bin/bash
# Captures raw App Store screenshots of the iOS app in a booted simulator, with live Entur and MET
# data, for scripts/frame-ios-screenshots.swift to caption.
#
#   scripts/capture-ios-screenshots.sh <simulator-udid> <app-path> <out-dir> <locale> <device-name>
#
# <locale> is an App Store locale (en-GB, no). The app must be a Debug build (it reads the
# -PTOpenSettings launch argument). Scenes: board in light theme, board in dark theme, Settings.
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
# A tidy status bar; the clock is left real so it matches the board's own clock.
xcrun simctl status_bar "$SIM" override --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularBars 4 \
  --dataNetwork wifi 2>/dev/null || true

# A typical setup: Oslo S and Jernbanetorget, weather for central Oslo, fastest route to Lillestrøm.
seed() {
  xcrun simctl terminate "$SIM" "$BUNDLE" 2>/dev/null || true
  local write="xcrun simctl spawn $SIM defaults write $BUNDLE"
  $write favorites -string '[{"id":"NSR:StopPlace:59872","name":"Oslo S","locality":"Oslo"},{"id":"NSR:StopPlace:58366","name":"Jernbanetorget","locality":"Oslo"}]'
  $write destination_stations -string '[{"id":"NSR:StopPlace:62339","name":"Lillestrøm stasjon","locality":"Lillestrøm"}]'
  $write saved_lat -string 59.9111
  $write saved_lon -string 10.7528
  $write camera_motion_wake -bool false
  $write burn_in_protection -string OFF # pixel shift would offset the shot
  $write theme_mode -string "$1"
  $write date_display_style -string "$2"
}

shot() { # name, extra launch args...
  local name=$1; shift
  xcrun simctl launch "$SIM" "$BUNDLE" -AppleLanguages "$LANGS" -AppleLocale "$REGION" "$@" >/dev/null
  sleep 12 # departures, weather and the fastest route arrive from the network
  xcrun simctl io "$SIM" screenshot "$OUT/$LOCALE/${DEVICE}_$name.png" >/dev/null 2>&1
  echo "captured $OUT/$LOCALE/${DEVICE}_$name.png"
}

seed LIGHT BESIDE && shot 1_board_light
seed DARK LONG && shot 2_board_dark
seed LIGHT BESIDE && shot 3_settings -PTOpenSettings YES
xcrun simctl status_bar "$SIM" clear 2>/dev/null || true
