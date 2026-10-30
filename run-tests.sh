#!/usr/bin/env bash
# Runs every test that needs no emulator. RUN_E2E=1 also runs the emulator suite.
set -euo pipefail
cd "$(dirname "$0")"

echo "[server] unit and HTTP tests"
python3 -m unittest discover -s tests

echo "[android] unit tests and debug APK"
android/build.sh

if [ "${RUN_E2E:-0}" = 1 ]; then
  echo "[e2e] remote-only flows on the emulator"
  android/emulator.sh debug
  python3 android/e2e/e2e.py
fi

echo "ALL GREEN"
