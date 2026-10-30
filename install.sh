#!/usr/bin/env bash
# Installs or updates the server on a Raspberry Pi. Usage: ./install.sh user@pi-address
set -euo pipefail

TARGET="${1:?usage: ./install.sh user@pi-address}"
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[1/3] Copying files to $TARGET"
# shellcheck disable=SC2029
ssh "$TARGET" 'rm -rf /tmp/media-lan-new && mkdir -p /tmp/media-lan-new'
scp -q -r "$DIR/server/media_lan" "$DIR/server/media-lan.service" "$TARGET:/tmp/media-lan-new/"

echo "[2/3] Installing the service"
ssh "$TARGET" 'bash -s' <<'REMOTE'
set -euo pipefail
id media-lan >/dev/null 2>&1 || sudo useradd --system --no-create-home --shell /usr/sbin/nologin media-lan
sudo install -d -o root -g root -m 755 /opt/media-lan /opt/media-lan/media_lan
sudo install -o root -g root -m 644 /tmp/media-lan-new/media_lan/*.py /opt/media-lan/media_lan/
sudo install -o root -g root -m 644 /tmp/media-lan-new/media-lan.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable media-lan >/dev/null 2>&1
sudo systemctl restart media-lan
rm -rf /tmp/media-lan-new
REMOTE

echo "[3/3] Checking"
ssh "$TARGET" 'systemctl is-active media-lan'
echo "Done. Port 8097."
