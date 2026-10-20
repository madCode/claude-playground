#!/usr/bin/env bash
# Puts the WireGuard app on the running emulator with one tunnel, "proton", from $WG_CONF (the
# PROTON_WG_CONF secret), for LiveOnTheDeviceTest's VPN check. The tunnel starts off; the test
# turns it on and off in the app. Needs a google_apis image, whose adb can run as root.
#   tools/wireguard-on-emulator.sh <wireguard.apk>
set -euo pipefail
apk="$1"
pkg=com.wireguard.android
: "${WG_CONF:?WG_CONF is empty}"

adb root >/dev/null
adb wait-for-device
adb install -r "$apk" >/dev/null
# Opened once, so Android makes its data folder; then closed before its tunnel list is read again.
adb shell am start -W -n "$pkg/.activity.MainActivity" >/dev/null
adb shell am force-stop "$pkg"

# The config never reaches the log: written owner-only, pushed, then deleted.
conf="$(mktemp)"
trap 'rm -f "$conf"' EXIT
chmod 600 "$conf"
printf '%s\n' "$WG_CONF" > "$conf"
uid="$(adb shell stat -c %u "/data/data/$pkg")"
adb shell mkdir -p "/data/data/$pkg/files"
adb push "$conf" "/data/data/$pkg/files/proton.conf" >/dev/null
adb shell chown -R "$uid:$uid" "/data/data/$pkg/files"
adb shell chmod 600 "/data/data/$pkg/files/proton.conf"
adb shell restorecon -R "/data/data/$pkg/files"
# Android asks the person before an app's first VPN; this answers yes for them.
adb shell appops set "$pkg" ACTIVATE_VPN allow
echo "WireGuard is on the emulator with the tunnel proton, off."
