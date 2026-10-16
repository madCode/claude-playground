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

## When it gets real releases

The debug channel is enough while the owner is the only person using the app. Before anyone
else installs it (a tester, F-Droid, a store), or once the owner wants a build that doesn't
change every merge, it gets signed releases: [releasing.md](releasing.md). The debug channel
carries on beside them.
