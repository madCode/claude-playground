# Jekyll Poster

An Android app for writing posts on your phone and publishing them to a Jekyll blog on GitHub
Pages, without making commits by hand. Read `docs/DESIGN.md` before changing behaviour and
`docs/ARCHITECTURE.md` for how the code fits together; `docs/BACKLOG.md` is the running plan
and `docs/DEVLOG.md` the log of what changed and why. It lives in `jekyll-poster/` of the
claude-playground repository; run commands from this folder. The rules every project shares
(comments, tests, docs, PRs and their review) are in the root CLAUDE.md.

## Layout

- `:core`: pure Kotlin/JVM, no Android. Front matter, Jekyll's conventions (paths, slugs,
  categories, the preview), the GitHub client, the VPN gate (`net/`), Obsidian notes, tracking
  codes and the blog index. Test with plain JUnit. Its test fixtures hold `FakeGitHub`, a
  MockWebServer serving a repository (or more, with `addRepository`), and `FakeVpn`; the app's tests use both.
- `:app`: Android, Jetpack Compose, Room, WorkManager, DataStore. Manual DI through
  `AppContainer`. Test with Robolectric against `TestApp` (fake GitHub and VPN, a database file
  of its own, publishing at once); `TestApp.signIn()` signs in. A Compose test that waits on
  anything but the screen (the database, GitHub, a view model's state) uses `waitUntilLooped`:
  Compose's own `waitUntil` doesn't turn the main looper, so that work never finishes.
- `app/src/androidTest`: device tests, on an emulator in CI ("Jekyll Poster device tests"; a cloud
  session has no KVM for one). `DeviceTestRunner` swaps in `DeviceTestApp`: the real app, with
  `FakeGitHub` serving the sample blog on the device itself, over plain HTTP to localhost, which
  only debug builds allow. Each test runs in a process of its own with the app's data cleared.
- `sample-blog/`: a small Jekyll blog with every front matter shape the app reads. It is the
  tests' fixture and the blog to try the app on.

## Commands

    ./gradlew :core:test                 # fast JVM tests
    ./gradlew :app:testDebugUnitTest     # Robolectric tests
    ./gradlew build koverVerify          # everything CI runs (fails below 85% line coverage)
    ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'   # PNGs in app/build/screenshots
    ./gradlew :app:connectedDebugAndroidTest   # device tests, on a running emulator

To try a change against a real blog, run the "Jekyll Poster live check" workflow on its branch:
it publishes a post to the sample blog, waits for Pages, then deletes it. Run it from Actions, not
from a cloud session: the session's GitHub proxy refuses the commit calls the app makes.

JDK 21 and the Android SDK (compileSdk 37) are required. The cloud environment has the SDK at
`/opt/android-sdk`: write `sdk.dir=/opt/android-sdk` into the gitignored `local.properties`;
`tools/install-android-sdk.sh` installs it where it's missing. CI publishes main's debug APK to
the `jekyll-poster-debug` release.

Debug builds are installed and in use, so changing a Room entity means bumping the database
version, adding a Migration with a `MigrationTest` case, and committing the new schema JSON in
`app/schemas`.

## Privacy

The repository is public. Never commit anyone's own data: no tokens, blog contents or accounts.
Tests and screenshots use the sample blog only.
