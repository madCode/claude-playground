# <Project name>

<One or two sentences: what it is and who it's for.> Read `docs/DESIGN.md` before changing
behaviour; `docs/DEVLOG.md` is the log of what changed and why. It lives in `<folder>/` of the
claude-playground repository; run commands from this folder. The rules every project shares are
in the root CLAUDE.md.

<The one rule worth keeping, if there is one, and why.>

## Layout

<Modules and what each holds; how tests fake the network, the clock and storage.>

## Commands

    ./gradlew build koverVerify          # everything CI runs
    ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'   # PNGs in app/build/screenshots

JDK 21 and the Android SDK are required; `../jekyll-poster/tools/install-android-sdk.sh`
installs the SDK for a cloud session.

## Privacy

<What this project's own data is (tokens, accounts, content) and what tests use instead.>
