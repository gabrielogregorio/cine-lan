#!/usr/bin/env bash
# Starts an emulator with an estimated Fire TV Stick 3rd gen profile and installs the APK.
# Estimate: 1 GB of RAM, 4 weak cores, Android 9 (API 28), 1920x1080 xhdpi, home Wi-Fi. It is not the real stick.
set -euo pipefail
cd "$(dirname "$0")"

AVD=media-lan-tv
IMAGE="system-images;android-28;android-tv;x86"
RAM_MB=1024
HEAP_MB=128
CORES=4
CPU_TOTAL=150%          # process cap: approximates 4 Cortex-A53 on a desktop CPU
NETWORK_KBPS=40000:40000
LATENCY_MS=2:12
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PACKAGE=app.filmes.tv
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
CONFIG="$HOME/.android/avd/$AVD.avd/config.ini"
if [ -z "${JAVA_HOME:-}" ] && [ -x /snap/android-studio/current/jbr/bin/javac ]; then
  export JAVA_HOME=/snap/android-studio/current/jbr
fi

if [ ! -f "$CONFIG" ]; then
  echo "[emulator] creating AVD $AVD"
  echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMAGE" -d tv_1080p
fi

set_option() {
  if grep -q "^$1=" "$CONFIG"; then
    sed -i "s|^$1=.*|$1=$2|" "$CONFIG"
  else
    echo "$1=$2" >> "$CONFIG"
  fi
}
set_option hw.ramSize "$RAM_MB"
set_option vm.heapSize "$HEAP_MB"
set_option hw.cpu.ncore "$CORES"
set_option hw.lcd.width 1920
set_option hw.lcd.height 1080
set_option hw.lcd.density 320
set_option hw.keyboard yes
set_option hw.gpu.enabled yes
set_option hw.gpu.mode host
set_option hw.initialOrientation landscape

echo "[emulator] profile: ${RAM_MB} MB, ${CORES} cores, CPU ${CPU_TOTAL}, 1920x1080, network ${NETWORK_KBPS} kbit/s"
systemd-run --user --scope --quiet -p MemoryMax=4G -p MemorySwapMax=0 -p CPUQuota="$CPU_TOTAL" \
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-snapshot -no-boot-anim -gpu host \
  -netspeed "$NETWORK_KBPS" -netdelay "$LATENCY_MS" > /dev/null 2>&1 &
disown

echo "[emulator] waiting for Android"
for attempt in $(seq 1 60); do
  [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
  [ $((attempt % 8)) = 0 ] && echo "[emulator] still booting, $((attempt * 5)) s"
  sleep 5
done

APK=app/build/outputs/apk/release/app-release.apk
[ "${1:-}" = debug ] && APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] && adb -s "$SERIAL" install -r "$APK" | tail -1
adb -s "$SERIAL" shell am start -n "$PACKAGE/app.medialan.MainActivity" | tail -1
echo "[emulator] ready"
