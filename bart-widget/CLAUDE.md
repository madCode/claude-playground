# BART Stations

An Android widget with BART departures for starred stations; see README.md for what it does and
why. It lives in `bart-widget/` of the claude-playground repository; run commands from this folder.

The point of the app is that it never guesses a station: no location, and a tap on a station opens
that station. Keep it that way. Show wall-clock times on the widget, since it redraws rarely.

    ./gradlew build    # compile, lint and unit tests (DeparturesTest)

The repository is public: no one's own data in commits.
