#!/usr/bin/env bash
# Installs the Android SDK pieces the Android apps here build with, and a Gradle mirror of Maven
# Central, for a cloud session or its setup script.
# Safe to re-run: skips what's already there.
set -euo pipefail
SDK="${ANDROID_HOME:-/opt/android-sdk}"
TOOLS_ZIP=commandlinetools-linux-13114758_latest.zip
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  curl -fsSL "https://dl.google.com/android/repository/$TOOLS_ZIP" -o /tmp/cmdline-tools.zip
  unzip -q -o /tmp/cmdline-tools.zip -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm /tmp/cmdline-tools.zip
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses > /dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "platform-tools" "platforms;android-37.0" "build-tools;36.0.0" > /dev/null
# Maven Central rate-limits the address cloud sessions share; route Gradle and
# Robolectric through Google's mirror of it instead.
mkdir -p "$HOME/.gradle/init.d"
cp "$(dirname "$0")/maven-mirror.init.gradle" "$HOME/.gradle/init.d/"
# Gradle finds the SDK through ANDROID_HOME; persist it for later shells.
grep -q ANDROID_HOME /etc/environment 2>/dev/null || echo "ANDROID_HOME=$SDK" >> /etc/environment
echo "Android SDK ready at $SDK"
