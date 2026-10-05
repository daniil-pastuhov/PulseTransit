#!/bin/bash
# Records an App Store app preview of the iOS app in a booted simulator: launches the Debug build's
# scripted tour (-PTDemo YES) and encodes it to Apple's preview format.
#
#   scripts/record-ios-preview.sh <simulator-udid> <out.mp4> <locale> <width>x<height>
#
# Sizes: 886x1920 for 6.9" iPhone, 1200x1600 for 13" iPad. Run scripts/capture-ios-screenshots.sh
# first: it installs the app and seeds the stops this records.
set -euo pipefail

SIM=$1 OUT=$2 LOCALE=$3 SIZE=$4
BUNDLE=com.homieapps.pulsetransit
case $LOCALE in
  en-GB) LANGS="(en-GB)" REGION=en_GB ;;
  no) LANGS="(nb)" REGION=nb_NO ;;
  *) echo "unknown locale $LOCALE" >&2; exit 1 ;;
esac
RAW=$(mktemp -t pt-preview).mov

xcrun simctl terminate "$SIM" "$BUNDLE" 2>/dev/null || true
xcrun simctl spawn "$SIM" defaults write "$BUNDLE" theme_mode -string LIGHT
xcrun simctl spawn "$SIM" defaults write "$BUNDLE" date_display_style -string BESIDE
xcrun simctl status_bar "$SIM" override --batteryState charged --batteryLevel 100 --wifiBars 3 --cellularBars 4 2>/dev/null || true

xcrun simctl io "$SIM" recordVideo --codec h264 --force "$RAW" 2>/dev/null &
RECORDER=$!
sleep 2
xcrun simctl launch "$SIM" "$BUNDLE" -AppleLanguages "$LANGS" -AppleLocale "$REGION" -PTDemo YES >/dev/null
sleep 33 # the tour lasts about 29 seconds after launch
kill -INT $RECORDER
wait $RECORDER 2>/dev/null || true
xcrun simctl status_bar "$SIM" clear 2>/dev/null || true

# Skip the launch, keep 29 s (previews must be 15-30 s), fit Apple's size, 30 fps, and add the
# silent stereo AAC track App Store Connect expects.
W=${SIZE%x*} H=${SIZE#*x}
ffmpeg -loglevel error -y -ss 3 -t 29 -i "$RAW" -f lavfi -t 29 -i anullsrc=channel_layout=stereo:sample_rate=44100 \
  -vf "scale=$W:$H:force_original_aspect_ratio=decrease,pad=$W:$H:(ow-iw)/2:(oh-ih)/2:color=black,fps=30,format=yuv420p" \
  -c:v libx264 -profile:v high -level 4.0 -preset slow -crf 18 -c:a aac -b:a 128k -shortest -movflags +faststart "$OUT"
rm -f "$RAW"
echo "Wrote $OUT"
