#!/usr/bin/env bash
# Runs the unit tests and builds the APK. No argument: debug. "release": the optimized one for the stick.
# Local builds are capped at 6 GB of memory.
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ] && [ -x /snap/android-studio/current/jbr/bin/javac ]; then
  export JAVA_HOME=/snap/android-studio/current/jbr
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

TASK=assembleDebug
FOLDER=debug
FILE=app-debug.apk
if [ "${1:-}" = release ]; then
  TASK=assembleRelease
  FOLDER=release
  FILE=app-release.apk
fi

GRADLE=(./gradlew --console=plain testDebugUnitTest "$TASK")
if [ -n "${MEDIA_LAN_SERVER:-}" ]; then
  GRADLE+=("-PdefaultServer=$MEDIA_LAN_SERVER")
fi

if [ -z "${CI:-}" ] && command -v systemd-run >/dev/null; then
  systemd-run --user --scope --quiet -p MemoryMax=6G -p MemorySwapMax=0 "${GRADLE[@]}"
else
  "${GRADLE[@]}"
fi
echo "[build] done: $(pwd)/app/build/outputs/apk/$FOLDER/$FILE"
