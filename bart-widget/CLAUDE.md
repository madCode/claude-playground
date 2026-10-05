# BART Stations

An Android widget with BART departures for starred stations. Read `docs/DESIGN.md` before
changing behaviour; `docs/DEVLOG.md` is the log of what changed and why. It lives in
`bart-widget/` of the claude-playground repository; run commands from this folder.

The point of the app is that it never guesses a station: no location, and a tap on a station
opens that station. Keep it that way. The widget shows wall-clock times, since it redraws rarely.

## Layout

One `:app` module: Compose, Glance, WorkManager, DataStore. `Departures.kt` holds the pure logic
(parsing BART's JSON, rows, clock times). Manual DI through `AppContainer`; tests swap it in
`TestApp` for a `FakeBart` server answering with boards recorded from the real API
(`src/test/resources/etd-*.json`), a DataStore file per test, a fixed clock (7:20 Pacific) and
test WorkManager.

## Commands

    ./gradlew build koverVerify          # everything CI runs (fails below 85% line coverage)
    ./gradlew :app:testDebugUnitTest --tests '*FlowTest'         # journeys through the real app
    ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'   # PNGs in app/build/screenshots
    ./gradlew :app:testDebugUnitTest --tests '*LiveCheckTest' -PliveCheck   # the real api.bart.gov

Look at the screenshots after a UI change. The widget's station rows come from Glance's
RemoteViewsService, which Robolectric can't bind, so the widget PNG shows only its frame;
`WidgetTest` checks the rows through Glance's test host.

JDK 21 and the Android SDK (compileSdk 37) are required; `../jekyll-poster/tools/install-android-sdk.sh`
installs the SDK for a cloud session. CI publishes main's debug APK to the `bart-widget-debug`
release.

## Comments, tests and docs

Comments say why, not what; tests catch plausible regressions and test behaviour, end to end
where they can; a behaviour change updates DESIGN.md in the same change; docs stay short and plain.
The repository is public: no one's own data in commits.
