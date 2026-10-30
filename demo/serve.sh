#!/usr/bin/env bash
# Builds the fictional library and serves it on port 8097 for the emulator. Ctrl+C stops it.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p build
python3 demo/make_library.py build/demo-library
cd server
MEDIA_LAN_ROOT=../build/demo-library \
MEDIA_LAN_DATABASE=../build/demo.sqlite \
MEDIA_LAN_ADDRESS=0.0.0.0 \
MEDIA_LAN_NETWORKS=127.0.0.0/8,10.0.2.0/24 \
exec python3 -m media_lan
