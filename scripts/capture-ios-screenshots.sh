#!/bin/bash
# Captures raw App Store screenshots of the iOS app in a booted simulator, with live Entur and MET
# data, for scripts/frame-ios-screenshots.swift to caption.
#
#   scripts/capture-ios-screenshots.sh <simulator-udid> <app-path> <out-dir> <locale> <device-name> [<pose>]
#
# <locale> is an App Store locale (en-GB, no). Use a simulator whose own language is the locale's
# (a fresh one, `xcrun simctl create`, with AppleLanguages set before its first boot), or the
# status bar and the widgets come out in another language. The app must be a Debug build (it reads
# the -PTSeedSettings launch argument). Scenes, in store order (the wall-board mock-up, 3, is drawn by
# the framing script from the iPad's dark board):
#
#   1_board_light   the board, light
#   2_widgets       the Home Screen with three widgets (Maestro; see below)
#   4_on_spot       On the spot at Oslo S
#   5_trip          a route's details, with journey alerts (Maestro)
#   6_route_search  route search with five routes (Maestro)
#   7_board_dark    the board, dark, with the weather
#   8_departure     a departure's details, where its line can be filtered out (Maestro)
#
# The Maestro scenes need the maestro CLI (scripts/store-flows). 2_widgets adds widgets to the
# simulator's first Home Screen page, so take it on a fresh simulator, once, and check the shot:
# SpringBoard's layout is not scripted beyond adding. WIDGETS sets which gallery pages to add (see
# scripts/store-flows/ios-add-widget.yaml), last added first on the page.
#
# For more than one iPhone Duo pose, set the pose in Device Hub (fold, rotate) and run again with
# <pose> naming it: the shots are then named <device>_<n>_<scene>-<pose>.png, beside the others in
# the same set. simctl can neither fold nor rotate a simulator. SCREEN picks the display to capture
# (simctl's --display, e.g. SCREEN=1 for the Duo's outer display; the default is the inner one), and
# SCENES the scenes to take (e.g. SCENES="board_light board_dark"), as a set holds at most 10.
set -euo pipefail

SIM=$1 APP=$2 OUT=$3 LOCALE=$4 DEVICE=$5 POSE=${6:-}
SCENES=${SCENES:-board_light widgets on_spot trip route_search board_dark departure}
WIDGETS=${WIDGETS:-5 6 0} # Departures large, Fastest route small, At a glance small
BUNDLE=com.homieapps.pulsetransit
FLOWS=$(cd "$(dirname "$0")/store-flows" && pwd)
case $LOCALE in
  en-GB) LANGS="(en-GB)" REGION=en_GB EDIT=Edit ADD_WIDGET="Add Widget" DONE=Done ;;
  no) LANGS="(nb)" REGION=nb_NO EDIT=Rediger ADD_WIDGET="Legg til widget" DONE=Ferdig ;;
  *) echo "unknown locale $LOCALE" >&2; exit 1 ;;
esac
mkdir -p "$OUT/$LOCALE"

xcrun simctl install "$SIM" "$APP"
# On the spot follows the device: give it Oslo S.
xcrun simctl privacy "$SIM" grant location "$BUNDLE"
xcrun simctl location "$SIM" set 59.9111,10.7528
# Maestro types into route search; autocorrect would turn "Majorstuen" into something else.
for key in KeyboardAutocorrection KeyboardPrediction KeyboardAutocapitalization KeyboardCheckSpelling KeyboardShowPredictionBar; do
  xcrun simctl spawn "$SIM" defaults write com.apple.Preferences "$key" -bool NO
done
tidy_status_bar() { # the clock is left real, so it matches the board's own clock
  xcrun simctl status_bar "$SIM" override --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularBars 4 \
    --dataNetwork wifi 2>/dev/null || true
}

# A typical setup: Oslo S and Jernbanetorget, weather for central Oslo, fastest route to Lillestrøm.
FAVORITES='[{"id":"NSR:StopPlace:59872","name":"Oslo S","locality":"Oslo"},{"id":"NSR:StopPlace:58366","name":"Jernbanetorget","locality":"Oslo"}]'
DESTINATIONS='[{"id":"NSR:StopPlace:62339","name":"Lillestrøm stasjon","locality":"Lillestrøm"}]'
# The board's departure whose details 8_departure shows, counted from the top: pick another when the
# first is cancelled, as a struck-through route reads badly in a store.
DEPARTURE=${DEPARTURE:-0}
# Route search goes to Majorstuen, which several metro lines serve, so its five routes differ.
PLACE=Majorstuen PLACE_ROW="Majorstuen, Oslo"

plist_string() { local escaped=${1//\"/\\\"}; printf '"%s"' "$escaped"; }

launch() { # mode, theme, date style, extra launch args...
  # Every setting is a launch argument: they override the stored ones, and -PTSeedSettings saves them
  # where the app and its widgets keep settings (a fresh simulator ignores `defaults write`).
  # A JSON value is passed as a quoted plist string, or the argument domain drops it.
  local mode=$1 theme=$2 date=$3; shift 3
  xcrun simctl terminate "$SIM" "$BUNDLE" 2>/dev/null || true
  xcrun simctl launch "$SIM" "$BUNDLE" -AppleLanguages "$LANGS" -AppleLocale "$REGION" -PTSeedSettings YES \
    -app_mode "$mode" -theme_mode "$theme" -date_display_style "$date" \
    -favorites "$(plist_string "$FAVORITES")" -destination_stations "$(plist_string "$DESTINATIONS")" \
    -saved_lat 59.9111 -saved_lon 10.7528 -camera_motion_wake NO -burn_in_protection OFF "$@" >/dev/null
  sleep 12 # departures, weather and the fastest route arrive from the network
}

flow() { # flow file, extra maestro args...
  local file=$1; shift
  run() {
    maestro --device "$SIM" test "$@" -e PLACE="$PLACE" -e PLACE_ROW="$PLACE_ROW" -e EDIT="$EDIT" \
      -e ADD_WIDGET="$ADD_WIDGET" -e DONE="$DONE" -e DEPARTURE="$DEPARTURE" "$FLOWS/$file" >/dev/null
  }
  # Live data and SpringBoard are both a little flaky: one more try, then the scene is skipped.
  run "$@" || run "$@" || { echo "skipped: $file failed twice" >&2; return 1; }
}

shot() { # name
  tidy_status_bar
  sleep 2
  local file="$OUT/$LOCALE/${DEVICE}_$1${POSE:+-$POSE}.png"
  xcrun simctl io "$SIM" screenshot ${SCREEN:+--display="$SCREEN"} "$file" >/dev/null
  echo "captured $file"
}

wants() { [[ " $SCENES " == *" $1 "* ]]; }

if wants board_light; then { launch BOARD LIGHT BESIDE && shot 1_board_light; } || echo "skipped: board_light" >&2; fi
widgets_scene() {
  launch BOARD LIGHT BESIDE || return 1 # saves the settings the widgets read
  local swipes
  for swipes in $WIDGETS; do flow ios-add-widget.yaml -e SWIPES="$swipes" || return 1; done
  sleep 10 # the widgets load their departures
  shot 2_widgets
}
if wants widgets; then widgets_scene || echo "skipped: widgets" >&2; fi
if wants on_spot; then { launch ON_SPOT LIGHT BESIDE && shot 4_on_spot; } || echo "skipped: on_spot" >&2; fi
if wants trip; then { launch ON_SPOT LIGHT BESIDE && flow ios-trip.yaml && shot 5_trip; } || echo "skipped: trip" >&2; fi
if wants route_search; then { launch ON_SPOT LIGHT BESIDE && flow ios-route-search.yaml && shot 6_route_search; } || echo "skipped: route_search" >&2; fi
if wants board_dark; then { launch BOARD DARK LONG && shot 7_board_dark; } || echo "skipped: board_dark" >&2; fi
if wants departure; then { launch BOARD LIGHT BESIDE && flow ios-departure.yaml && shot 8_departure; } || echo "skipped: departure" >&2; fi
xcrun simctl status_bar "$SIM" clear 2>/dev/null || true
