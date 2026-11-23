# Spotter

An Android app for one person who takes Orange Theory classes: one glance before class says
which dumbbell to grab for each exercise on today's board, and logging in the car afterwards
takes under a minute. Read `docs/DESIGN.md` before changing behaviour, and `docs/TRAINING.md`
(what the research says, with sources) before changing a progression rule; `docs/DEVLOG.md` is the
log of what changed and why. It lives in `spotter/` of the claude-playground repository; run
commands from this folder. The rules every project shares are in the root CLAUDE.md.

**A gap beats a guess.** No number is better than a wrong one, because nothing later can tell a
wrong number from a right one. A board line is read in full or marked "not sure", and a row
with no sound basis says "new: go light". But a blank row counts against the app on the
scorecard too, so "not sure" is something to fix, not a place to hide.

It is being built beside Carry (`../carry/`), which stays in use until Spotter beats it on the
scorecard (`docs/DESIGN.md`). Carry is a source of facts (the board feed, the exercise list, the
board corpus), not of structure: don't copy its code or layout.

## Layout

- `coach/`: plain Kotlin, no Android. Board reading, identity, loads, progression, the sheet
  and its reasons; the log's events and what a save writes, the export's files, reading an
  imported history and replaying one. Everything the coach decides is tested here on the JVM.
- `app/`: the Android app, which feeds the coach and shows its sheet. `today/` finds the class
  and its board; `log/` is the event store; `export/` copies the log to the backup folder;
  `history/` keeps an imported history; `update/` is how a CI debug build installs newer ones
  (DESIGN.md, "Updating itself"). Where a package talks to Android, its decisions sit in a
  plain class (`UpdateRun`, `ExportRun`, `HistoryPick`) so they run in JVM tests.
- `docs/DEVICE.md`: what only a phone can show, and how to check it by hand.
- `tools/debug-feed.sh`: writes the feed CI publishes beside each debug build.

## Commands

    ./gradlew build koverXmlReport koverHtmlReport koverVerify   # everything CI runs
    ./gradlew :coach:test                # the coach's tests alone
    ./gradlew :app:testDebugUnitTest     # the app's, with screenshots in app/build/screenshots
    ./gradlew -q :coach:replay -Phistory=… -Pout=…   # grade the coach on a history (docs/REPLAY.md)

JDK 21 and the Android SDK are required.
`../jekyll-poster/tools/install-android-sdk.sh` installs it for a cloud session, with the Maven
mirror Gradle needs there.

## What cycles keep relearning

- A fresh checkout needs `local.properties` with `sdk.dir=/opt/android-sdk` before the full
  build, or it fails in a second with "SDK location not found". A session that can't push
  says so on the morning page and stops; it doesn't build locally.
- Put real-shaped data through a new piece before the review does: the board corpus, a real
  feed board, Carry's invented history. Format notes alone have been wrong.
- A fixture is sound when only the rule under test decides its outcome, and its values differ
  where the rule tells them apart (a 20 to 25 step is a big jump: it holds and offers a test).
- Store what was said and derive the reading when the coach reads it, so a better reading
  later needs no migration.
- Cycles run 30 minutes apart at madCode's ask (2026-11-22): take a whole step-sized slice.
- A fix after a review gets its second look: it found a real bug in three of four cycles.

## Privacy

madCode's training history, baselines, calendar and scorecard figures are private: never in this
repository, its tests, screenshots, commits or PR text, not even as aggregates. Tests use an
invented lifter. History import reads a file madCode provides at runtime. Board lines are public
r/orangetheory posts and may be used.

## Audit personas

A cycle's audit takes one of these:

- madCode at the studio door, phone in a locker in a minute: is the number there, is it right?
- madCode in the car after class, tired, with one hand: can a class be logged in a minute?
- A sceptical coach reading a board line and the row Spotter made from it: is anything lost?
