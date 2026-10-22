#!/usr/bin/env bash
# Helpers for the laptop's emulator checks (docs/DEVICE-CHECKS.md). One step per call:
#
#   tools/emulator-check.sh start <avd>    # boot it, behind a 45-minute watchdog
#   tools/emulator-check.sh install        # the newest CI debug build from main
#   tools/emulator-check.sh record on|pull # the request record (pull writes requests.jsonl here)
#   tools/emulator-check.sh big-text|dark|rotate on|off
#   tools/emulator-check.sh stop           # shut it down and check nothing is left
set -euo pipefail
PKG=com.app.jekyllposter.debug
APK_URL=https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk
W=${TMPDIR:-/tmp}/claude-emulator-watchdog.pid

# The cycles skill's watchdog: stops the emulator 45 minutes after the last start, whatever
# happens to the session. A stale PID file is only acted on if it still names the watchdog.
stop_watchdog() { [ -f "$W" ] && { p=$(cat "$W"); rm -f "$W"
  ps -p "$p" -o args= | grep -q 'emu kill' || return 0
  kids=$(pgrep -P "$p"); kill "$p"; [ -n "$kids" ] && kill $kids; }; return 0; }

case "${1:-}" in
  start)
    avd=${2:?an AVD name, from: emulator -list-avds}
    stop_watchdog
    nohup emulator -avd "$avd" -no-snapshot-save >/dev/null 2>&1 &
    nohup sh -c "sleep 2700; '$(command -v adb)' emu kill; rm -f '$W'" >/dev/null 2>&1 &
    echo $! > "$W"
    adb wait-for-device
    until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
    echo "Booted $avd; it stops by itself in 45 minutes."
    ;;
  install)
    tmp=$(mktemp -d)
    curl -fsSL -o "$tmp/app.apk" "$APK_URL"
    adb install -r "$tmp/app.apk"
    rm -rf "$tmp"
    adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
    ;;
  record)
    case "${2:-}" in
      on) adb shell run-as "$PKG" mkdir -p files/requests
          adb shell run-as "$PKG" touch files/requests/on ;;
      pull) adb exec-out run-as "$PKG" cat files/requests/requests.jsonl > requests.jsonl
            echo "$(wc -l < requests.jsonl) requests in requests.jsonl" ;;
      *) echo "record on|pull" >&2; exit 2 ;;
    esac
    ;;
  big-text) adb shell settings put system font_scale "$([ "${2:-on}" = on ] && echo 2.0 || echo 1.0)" ;;
  dark) adb shell cmd uimode night "$([ "${2:-on}" = on ] && echo yes || echo no)" ;;
  rotate)
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation "$([ "${2:-on}" = on ] && echo 1 || echo 0)"
    ;;
  stop)
    adb emu kill || true
    stop_watchdog
    sleep 3
    adb devices | grep -q emulator && { echo "An emulator is still listed" >&2; exit 1; }
    pgrep -f 'qemu|emulator' >/dev/null && { echo "An emulator process is still running" >&2; exit 1; }
    echo "Stopped; nothing left running."
    ;;
  *) sed -n 2,8p "$0"; exit 2 ;;
esac
