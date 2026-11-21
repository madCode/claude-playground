# Design

How Spotter works now, for someone about to change it. Plans go in BACKLOG.md; history in
DEVLOG.md and the PRs. Until the first code lands this describes what is being built; each
section is updated to "now" as its piece ships.

## What it's for

One person who takes Orange Theory classes (2G and 3G: a treadmill block and a floor block of
dumbbell, bench and bodyweight work). The phone can't go into the studio, so Spotter gives
them, from one glance beforehand, **which weight to grab for each exercise in today's class**,
with warnings first. In the car afterwards it logs what was actually done, in under a minute.

It replaces Carry (`../carry/`) only when it beats Carry on the scorecard (below). Until then
both are installed side by side.

## Rules

- **A gap beats a guess.** A wrong number is worse than none, because nothing later can tell
  them apart. Where there is no sound basis, the row says "new: go light", not a small number.
  But silence is a failure too: the scorecard counts unnumbered rows.
- **What's logged never changes meaning.** History is an append-only log of events. A
  correction is a new event; an exercise's identity is fixed when it is logged.
- **Weight is per hand plus a count:** `2×25`, `1×50`, never a bare total. Imported rows whose
  count is unknown say so and count as weaker evidence.
- **One coach, in plain Kotlin.** The `coach` module has no Android code and runs in JVM tests.
  There is no second implementation anywhere.
- **The training data is madCode's.** It lives on the phone, in Android backup and in exports
  they choose. No GitHub token or Reddit key in the app; no real figures in this repository.

## Parts

- **`coach/`** (Kotlin/JVM). Pure functions from inputs to a sheet:
  - *Board parsing:* the day's board lines into exercise rows (movement, variation, reps,
    implement), leaving prose and greetings out.
  - *Identity:* each row is matched to an exercise in the exercise list (aliases, patterns),
    with its variation stated: single arm, one or two dumbbells, bosu, split stance, tempo,
    combo, ladder, timed hold, "12 total" vs "6 each".
  - *Progression:* straight sets with 1–2 reps in reserve. Up a dumbbell after clean sessions;
    hold after a near miss or a twinge; down a step after a miss of 3+ reps, a miss on the
    first set, broken form or having to stop. A later miss caps an older success. Epley only
    within 3–12 reps. A test number ("12 · try 15 if set 1 felt easy") when the next dumbbell
    is a big jump.
  - *Explanations:* every number carries the reasons behind it.
  - *Scorecard rows:* per logged row, `date`, `suggested_weight` and `weight`, the shape
    gym-coach's `scorecard.py` grades.
- **`app/`** (Android, Compose). Fetches the board, reads the calendar, stores the log, shows
  the screens, runs the export. Package `com.app.spotter`, so it installs beside Carry.

Dumbbells come in 3, 5, 8, 10, 12, 15, 20, 25, 30, 35, 40, 45 and 50 lb; "one step" means the
next one in that list.

## Where the inputs come from

- **The board:** `boards/YYYY-MM-DD.json` (schema `carry-board/1`) on this repository's
  `carry-boards` branch, which a scheduled job refreshes every half hour. The last good board
  is kept, so a Reddit, GitHub or Wi-Fi outage shows yesterday's sheet clearly marked, never a
  blank or wrong one. A class with no board of its own borrows the other format's, labelled.
- **Which class:** the next booked class in the phone's calendar, today or tomorrow; the
  format is in the event title. Nobody picks the class by hand.
- **The exercise list:** a copy of `carry/brain-fixtures/exercises.json`, in the coach's
  resources.

## Screens

- **Sheet.** Today's (or tomorrow's) class and its board's age. One row per exercise in block
  order: name, the number, ▲ ▼ = against last time. Warnings at the top and on their rows
  (a miss, pain, a run of unfinished sets). A tap on a row opens why.
- **Log.** The same rows. Each is "✓ as listed" or "changed" (heaviest weight from the rack,
  reps), with "felt easy / hard", "twinge" and "had to stop" one tap away, and per-round detail
  optional. Untouched rows stay unlogged, never "skipped". "Add an exercise" records one the
  board missed by picking it from the list.

Later: progress, review of new exercise names, sharing a board for others, the watch.

## Backup

Android backup of the log, plus a scheduled export (the scorecard shape and the full log) to
a folder madCode picks, such as Downloads or a synced folder.

## The watch (later)

madCode wears a Garmin. The watch view is a Connect IQ app (Monkey C) that shows the sheet's
names and numbers; the phone sends it the day's sheet through Garmin's Connect IQ companion
SDK. The coach stays on the phone; the watch only shows what it is sent.

## The scorecard decides

Over every logged weighted set: did the sheet give a number (*numbered*), was it within one
dumbbell of what was lifted (*close*), and how many were more than a step *high* or *low*.
Spotter switches on for real when, over the same weeks of real classes, it numbers more rows
than Carry, gets more close, and is high no more often. Strength per movement (variations
reading one estimate through stated factors) ships behind a switch and is judged the same way
against plain per-exercise rules before it is turned on.
