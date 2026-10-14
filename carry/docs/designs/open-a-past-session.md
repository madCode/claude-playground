# Open a past session from the Since list

Asked for by the owner, 2026-10-14. Not built.

## Why

The Since tab lists every session newest first, with its date, class, lift
count and heaviest lift. It answers "when did I train" but not **"what did I
actually lift on the 27th"**, which is the question a person asks about a day.
Today no screen answers it: the Before tab shows only the current sheet, and
the After tab only the current form.

## The shape

`DayRow` becomes tappable and opens a detail view of that one session: every
row the owner answered, with the weight and reps they entered, and the rows
they left blank shown as blank rather than as anything else.

## What makes it non-trivial

- **`Growth.Day` does not carry the rows.** `Growth.of()` reduces every file
  to aggregates. The detail view needs the session's own rows, so either
  `Day` keeps them, or the screen re-reads that one file by document id.
  Re-reading is off the main thread, like every other folder read here.
- **Navigation.** There is no nav library: the app is three tabs and nothing
  else. Local state inside the Since screen (selected day, or null) is enough
  and keeps it that way. Add a `BackHandler` so the system back button
  returns to the list rather than leaving the app.
- **State restoration.** The selection must survive rotation and process
  death, so `rememberSaveable`, and a `StateRestorationTester` test. This has
  bitten this project before.
- **A backfilled session is a record, not a form.** Those files are marked.
  Show them, but do not imply they can be edited.

## What it must not do

Not a second editor. A past session is read-only here -- the one editable
session is today's, on the After tab. Two ways to write the same file is how
a wrong number gets in.

## Tests

A screenshot of the detail view, the empty case (a session with nothing
answered), the back-navigation path, and a restoration test. The screenshot
must assert something real as well: `captureToImage` has returned flat blanks
on this project before, which is what `isFlat()` guards.
