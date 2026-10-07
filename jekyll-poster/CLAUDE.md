# Jekyll Poster

An Android app for writing posts on your phone and publishing them to a Jekyll blog on GitHub
Pages, without making commits by hand. Read `docs/DESIGN.md` before changing behaviour and
`docs/ARCHITECTURE.md` for how the code fits together; `docs/BACKLOG.md` is the running plan
and `docs/DEVLOG.md` the log of what changed and why. It lives in `jekyll-poster/` of the
claude-playground repository; run commands from this folder.

## Layout

- `:core`: pure Kotlin/JVM, no Android. Front matter, Jekyll's conventions (paths, slugs,
  categories, the preview), the GitHub client, the VPN gate (`net/`), Obsidian notes, tracking
  codes and the blog index. Test with plain JUnit. Its test fixtures hold `FakeGitHub`, a
  MockWebServer serving one repository, and `FakeVpn`; the app's tests use both.
- `:app`: Android, Jetpack Compose, Room, WorkManager, DataStore. Manual DI through
  `AppContainer`. Test with Robolectric against `TestApp` (fake GitHub and VPN, a database file
  of its own, publishing at once); `TestApp.signIn()` signs in.
- `sample-blog/`: a small Jekyll blog with every front matter shape the app reads. It is the
  tests' fixture and the blog to try the app on.

## Commands

    ./gradlew :core:test                 # fast JVM tests
    ./gradlew :app:testDebugUnitTest     # Robolectric tests
    ./gradlew build koverVerify          # everything CI runs (fails below 85% line coverage)
    ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'   # PNGs in app/build/screenshots

To try a change against a real blog, run the "Jekyll Poster live check" workflow on its branch:
it publishes a post to the sample blog, waits for Pages, then deletes it. Run it from Actions, not
from a cloud session: the session's GitHub proxy refuses the commit calls the app makes.

JDK 21 and the Android SDK (compileSdk 37) are required; `tools/install-android-sdk.sh` installs
the SDK for a cloud session. CI publishes main's debug APK to the `jekyll-poster-debug` release.

Debug builds are installed and in use, so changing a Room entity means bumping the database
version, adding a Migration with a `MigrationTest` case, and committing the new schema JSON in
`app/schemas`.

## Privacy

The repository is public. Never commit anyone's own data: no tokens, blog contents or accounts.
Tests and screenshots use the sample blog only.

## Comments, tests and docs

As in newspaperss: comments say why, not what; tests catch plausible regressions and test
behaviour; a behaviour change updates DESIGN.md in the same change; docs stay short and plain.
