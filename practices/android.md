# Android apps

What every Android app here has from its first PR, so they all work the same way for the
owner, who installs them side by side and reports what they see. newspaperss has all of it;
copy from there.

## From the first PR

- **Its own launcher icon.** An adaptive icon (a vector foreground on a plain background, with
  a monochrome layer for themed icons), drawn for this app. With several apps from here on one
  phone, the template's default icon tells none of them apart.
- **Debug installs beside release**: `applicationIdSuffix = ".debug"`, so trying a build never
  replaces the one in use.
- **The version says which build it is.** On a CI debug build the version name is
  `<x.y.z>-debug.<CI run>+<commit>` and the version code is the CI run number; newspaperss's
  `app/build.gradle.kts` does both.
- **The version is on screen, copyable in one tap**: [version-page.md](version-page.md).
- **The debug APK's file name carries the build too.** CI publishes main's debug build to a
  `latest-debug` release twice: under a fixed name, for the link in DEVLOG.md that never
  breaks, and as `<app>-debug-<run>.apk`, so a downloaded file can't be mistaken for an older
  one (newspaperss's `build-and-test.yml`).
- **The app says when a newer debug build is out.** newspaperss's `DebugUpdates.kt` asks
  GitHub for the `latest-debug` release when the app comes to the front, at most every three
  hours, only on a CI debug build, and the app links to the new APK.
- The tests, CI and Room schema rules in [Starting a project](README.md#starting-a-project).

## In a cloud session

Run `jekyll-poster/tools/install-android-sdk.sh` before the first build. Besides the SDK, it
installs a Gradle init script that sends Maven Central requests to Google's mirror of it.
Maven Central rate-limits the address cloud sessions share, and Gradle reports a refusal as
**"Could not find"** some well-known artifact, so it reads like a broken dependency rather than
a network problem. Robolectric fetches its Android jars outside Gradle and fails the same way
("Failed to fetch maven artifact org.robolectric:android-all…"); the init script covers it too.

To have it on every session, put the script in the cloud environment's setup script
(environment settings, then *Setup script*):

    bash /home/user/claude-playground/jekyll-poster/tools/install-android-sdk.sh

## Emulators in a cycle

A cycle that starts an emulator shuts it down before it ends
(`adb emu kill`, then check `adb devices` and that no `qemu` or `emulator` process is left):
one left running has run for eight hours.

The one exception is a run whose cycles are at most 30 minutes apart, where restarting each time
would cost more than the cycle. Unless this is the run's last cycle, the emulator may stay up
for the next, but only behind a watchdog that shuts it down whatever happens to the run:

    W=${TMPDIR:-/tmp}/claude-emulator-watchdog.pid
    stop_watchdog() { [ -f "$W" ] && { p=$(cat "$W"); rm -f "$W"
      ps -p "$p" -o args= | grep -q 'emu kill' || return 0
      kids=$(pgrep -P "$p"); kill "$p"; [ -n "$kids" ] && kill $kids; }; return 0; }
    stop_watchdog
    nohup sh -c "sleep 2700; '$(command -v adb)' emu kill; rm -f '$W'" >/dev/null 2>&1 &
    echo $! > "$W"

That is 45 minutes from the end of this cycle. The next cycle runs the same lines to restart the
clock; if no next cycle comes, the emulator stops anyway. Two details matter: the shell is
stopped before its `sleep`, or it goes on to `adb emu kill`; and a process is killed only if it
is still the watchdog, since a stale PID file can name a process that has nothing to do with it.

The emulator and its watchdog are the only things allowed to outlive a cycle, and the cycle log
entry says they were left running. The last cycle of a run, and any cycle in a run spaced
further apart, shuts the emulator down and runs `stop_watchdog`.

At the start, an emulator already running is noted. If the last cycle left it behind a watchdog,
use it and renew the watchdog. If a cycle left it without one, stop it. Otherwise it may be
madCode's, so leave it, and put it first in the summary and on the morning page.

## When it gets real releases

The debug channel is enough while the owner is the only person using the app. Before anyone
else installs it (a tester, F-Droid, a store), or once the owner wants a build that doesn't
change every merge, it gets signed releases: [releasing.md](releasing.md). The debug channel
carries on beside them.
