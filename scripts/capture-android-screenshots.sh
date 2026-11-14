#!/bin/bash
# Captures raw Play screenshots of the Android app on an emulator, with live Entur and MET data, for
# `swift scripts/frame-ios-screenshots.swift --play` to caption into the Play listing.
#
#   scripts/capture-android-screenshots.sh <adb-serial> <debug.apk> <out-dir> <locale> <phone|tablet>
#
# <locale> is the App Store locale the framing script keys captions by (en-GB, no); it sets the
# app's own language (Android 13+), so the system's stays as it is. The app must be a debug build,
# whose settings `run-as` can write. Scenes, as for the App Store (see capture-ios-screenshots.sh):
# 1_board_light, 2_widgets (phone only), 4_on_spot, 5_trip, 6_route_search, 7_board_dark and
# 8_departure; the framing script draws 3_wall from the tablet's 7_board_dark. The Maestro scenes
# need the maestro CLI (scripts/store-flows). 2_widgets adds two widgets to the launcher's home
# screen, so take it once on a fresh emulator and check the shot; KEEP_WIDGETS=1 skips adding them.
set -euo pipefail

SERIAL=$1 APK=$2 OUT=$3 LOCALE=$4 DEVICE=$5
SCENES=${SCENES:-board_light widgets on_spot trip route_search board_dark departure}
[ "$DEVICE" = tablet ] && SCENES=${SCENES/widgets/}
BUNDLE=com.homieapps.pulsetransit
ACTIVITY=$BUNDLE/com.example.pulsetransit.MainActivity
FLOWS=$(cd "$(dirname "$0")/store-flows" && pwd)
case $LOCALE in
  en-GB) APP_LOCALE=en-GB FIND_ROUTE="Find a route" ;;
  no) APP_LOCALE=nb-NO FIND_ROUTE="Finn en reise" ;;
  *) echo "unknown locale $LOCALE" >&2; exit 1 ;;
esac
# The board's departure whose details 8_departure shows, from the top: pick another when the first
# is cancelled. Route search goes to Majorstuen, as on iOS.
DEPARTURE=${DEPARTURE:-0} PLACE=Majorstuen
mkdir -p "$OUT/$LOCALE"
adb() { command adb -s "$SERIAL" "$@"; }

adb install -r -g "$APK" >/dev/null # -g: location and notifications granted, no prompts
adb shell cmd locale set-app-locales "$BUNDLE" --locales "$APP_LOCALE"
adb emu geo fix 10.7528 59.9111 >/dev/null # On the spot follows the device: Oslo S

# A typical setup, as on iOS: Oslo S and Jernbanetorget, fastest route to Lillestrøm.
FAVORITES='[{"id":"NSR:StopPlace:59872","name":"Oslo S","locality":"Oslo"},{"id":"NSR:StopPlace:58366","name":"Jernbanetorget","locality":"Oslo"}]'
DESTINATIONS='[{"id":"NSR:StopPlace:62339","name":"Lillestrøm stasjon","locality":"Lillestrøm"}]'
xml() { local s=${1//&/&amp;}; s=${s//\"/&quot;}; s=${s//</&lt;}; printf '%s' "${s//>/&gt;}"; }

launch() { # mode, theme, date style: writes the settings file while the app is stopped, then starts it
  adb shell am force-stop "$BUNDLE"
  adb shell run-as "$BUNDLE" mkdir -p shared_prefs
  cat <<EOF | adb shell "run-as $BUNDLE sh -c 'cat > shared_prefs/app_settings.xml'"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="app_mode">$1</string>
    <string name="theme_mode">$2</string>
    <string name="date_display_style">$3</string>
    <string name="favorites">$(xml "$FAVORITES")</string>
    <string name="destination_stations">$(xml "$DESTINATIONS")</string>
    <string name="saved_lat">59.9111</string>
    <string name="saved_lon">10.7528</string>
    <boolean name="camera_motion_wake" value="false" />
    <string name="burn_in_protection">OFF</string>
</map>
EOF
  adb shell am start -n "$ACTIVITY" >/dev/null
  # LOAD_WAIT=<seconds> waits that long instead, where Maestro will not run (it can hang on an
  # emulator short of memory; the scenes without taps still work then).
  if [ -n "${LOAD_WAIT:-}" ]; then sleep "$LOAD_WAIT"; else flow android-wait-loaded.yaml || true; fi
  sleep 6 # the weather and the fastest route arrive after the departures
}

flow() { # flow file, extra maestro args...
  local file=$1; shift
  run() {
    maestro --device "$SERIAL" test "$@" -e PLACE="$PLACE" -e FIND_ROUTE="$FIND_ROUTE" \
      -e DEPARTURE="$DEPARTURE" -e WIDGETS_LABEL=Widgets "$FLOWS/$file" >/dev/null
  }
  # Live data and the launcher are both a little flaky: one more try, then the scene is skipped.
  run "$@" || run "$@" || { echo "skipped: $file failed twice" >&2; return 1; }
}

# A tidy status bar (System UI demo mode), with the real time so it matches the board's clock.
adb shell settings put global sysui_demo_allowed 1
adb shell settings put system time_12_24 24 # the status bar's clock, like the board's
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
tidy_status_bar() {
  demo enter
  demo clock -e hhmm "$(date +%H%M)"
  demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4 -e mobile hide
  demo notifications -e visible false
}

shot() { # name
  tidy_status_bar
  sleep 2
  local file="$OUT/$LOCALE/${DEVICE}_$1.png"
  adb exec-out screencap -p > "$file"
  echo "captured $file"
}

wants() { [[ " $SCENES " == *" $1 "* ]]; }

if wants board_light; then { launch BOARD LIGHT BESIDE && shot 1_board_light; } || echo "skipped: board_light" >&2; fi
widgets_scene() {
  launch BOARD LIGHT BESIDE || return 1 # the settings the widgets read
  # Added once: they redraw in the app's language, so a second locale's run reuses them (KEEP_WIDGETS=1).
  if [ -z "${KEEP_WIDGETS:-}" ]; then
    flow android-add-widget.yaml -e WIDGET=Departures || return 1
    flow android-add-widget.yaml -e WIDGET="Fastest route" || return 1
  fi
  # Widgets refresh when the app stops.
  adb shell am start -n "$ACTIVITY" >/dev/null && sleep 8 && adb shell input keyevent KEYCODE_HOME && sleep 15
  shot 2_widgets
}
if wants widgets; then widgets_scene || echo "skipped: widgets" >&2; fi
if wants on_spot; then { launch ON_SPOT LIGHT BESIDE && shot 4_on_spot; } || echo "skipped: on_spot" >&2; fi
if wants trip; then { launch ON_SPOT LIGHT BESIDE && flow android-trip.yaml && shot 5_trip; } || echo "skipped: trip" >&2; fi
if wants route_search; then { launch ON_SPOT LIGHT BESIDE && flow android-route-search.yaml && shot 6_route_search; } || echo "skipped: route_search" >&2; fi
if wants board_dark; then { launch BOARD DARK LONG && shot 7_board_dark; } || echo "skipped: board_dark" >&2; fi
if wants departure; then { launch BOARD LIGHT BESIDE && flow android-departure.yaml && shot 8_departure; } || echo "skipped: departure" >&2; fi
demo exit
