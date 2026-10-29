#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
test -f keystore.properties || { echo "Configure signing first; see RELEASING.md"; exit 1; }
bash gradlew :app:assembleRelease --no-daemon "$@"
mkdir -p Apk
cp app/build/outputs/apk/release/app-release.apk Apk/Spock.apk
