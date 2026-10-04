#!/bin/bash
# Prepares a release branch for Gradle Play Publisher, and exports RELEASE_VERSION for later
# steps. Used by .github/workflows/android.yml with the version from the branch name:
#
#   scripts/stage-release-notes.sh 1.0.1
#
# Checks that versionName matches the branch, then copies release-notes/vX.Y.Z/<locale>.txt
# (written by scripts/release) to app/src/main/play/release-notes/<locale>/default.txt, where
# GPP reads "What's new" from. Those copies are git-ignored: release-notes/ is the only record.
set -euo pipefail

VERSION=${1:?usage: $0 X.Y.Z}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LOCALES=(en-US no-NO)
fail() { echo "::error::$1"; exit 1; }

[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "'$VERSION' is not a X.Y.Z version"
grep -q "versionName = \"$VERSION\"" "$ROOT/app/build.gradle.kts" ||
  fail "app/build.gradle.kts versionName is not $VERSION"
for locale in "${LOCALES[@]}"; do
  notes="$ROOT/release-notes/v$VERSION/$locale.txt"
  [[ -s $notes ]] || fail "missing release notes: release-notes/v$VERSION/$locale.txt"
  mkdir -p "$ROOT/app/src/main/play/release-notes/$locale"
  cp "$notes" "$ROOT/app/src/main/play/release-notes/$locale/default.txt"
done
echo "Release $VERSION: notes staged for ${LOCALES[*]}"
if [[ -n ${GITHUB_ENV:-} ]]; then echo "RELEASE_VERSION=$VERSION" >> "$GITHUB_ENV"; fi
