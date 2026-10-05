# BART Stations

A home-screen widget with BART departure times for the stations you star, and nothing else.

Why: the official BART app shows the station it thinks you're nearest to, even when you've starred
another, and tapping a starred station can open the "nearest" one instead. It's easy to read the
wrong board and leave at the wrong time. This app never asks where you are.

- **Your stations only.** Star them in the app; the widget lists them in the order you starred them.
- **Tapping a station opens that station.** Not the nearest one.
- **Clock times, not "13 min".** A widget only redraws every so often, and "13 min" goes wrong as
  it ages while "7:33" stays right. The header says when the times were fetched. If a refresh fails,
  the widget says so and shows how old the times are.
- **Refresh** on the widget fetches now. Otherwise it refreshes every 15 minutes (Android's floor for
  background work), and whenever you open the app.
- **Parking** opens the official BART app, where Parking is one tap away. The BART app has no public
  link straight to its parking screen. If it isn't installed, the button opens bart.gov's parking page.
- **The app** shows the same boards with minutes counting down live, platform, cars and delays.

Times come from BART's public real-time API (api.bart.gov), with BART's published public key.

**Install:** the newest build from main is
[bart-widget-debug.apk](https://github.com/madCode/claude-playground/releases/download/bart-widget-debug/bart-widget-debug.apk).
Then long-press the home screen → Widgets → BART Stations.

## Code

One `:app` module. `Departures.kt` parses BART's JSON and holds the pure logic (tested in
`DeparturesTest`). `Store.kt` keeps the starred stations and the last boards in DataStore.
`Refresher.kt` fetches them (WorkManager, every 15 min). `BartWidget.kt` is the Glance widget and
`MainActivity.kt` the Compose app.

    ./gradlew build    # compile, lint, unit tests

JDK 21 and the Android SDK (compileSdk 37) are required; `../jekyll-poster/tools/install-android-sdk.sh`
installs the SDK in a cloud session.
