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
- **Every word on a board line is accounted for.** Each is part of the movement, a variation
  the coach knows, or known filler. A word left over makes the row "not sure what this is",
  with no number borrowed from a plainer exercise. The sheet shows the board's own words, with
  what Spotter understood beneath, so a misreading is visible at the gym.
- **One coach, in plain Kotlin.** The `coach` module has no Android code and runs in JVM tests.
  There is no second implementation anywhere.
- **The training data is madCode's.** It lives on the phone, in Android backup and in exports
  they choose. No GitHub token or Reddit key in the app; no real figures in this repository.

## Parts

- **`coach/`** (Kotlin/JVM). Pure functions from inputs to a sheet:
  - *Board parsing:* the day's board lines into exercise rows (movement, variation, reps,
    implement), leaving prose and greetings out.
  - *Identity:* see **Reading a board line**, below.
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

## Reading a board line

Carry's reading of its 1,560-row board corpus shows why this gets its own section: about one
row in five drops words it didn't understand and still names a plainer exercise ("bench bird
dog low row" became *low row*), and some words it understood were lost on the way ("floor close
grip chest press" became *bench press*). The sheet then shows a number for something other than
what is on the board, and nothing says so.

- **An exercise is a movement plus its variations.** Movement: chest press, squat, row.
  Variations, each from a closed list the coach knows, by axis: implement and count (one or two
  dumbbells, Y-bell, TRX, band), arms (single, reciprocating), alternating, legs (single leg,
  split stance), position (floor, incline, bench, bridge hold, high plank), stability (bosu),
  grip (close, neutral, hammer), tempo and range (pulsing, half, full, explosive), band
  placement, direction, and hold; then combos ("A to B") and the rep scheme (reps, "6 each",
  "12 total", ladder, timed). Two rows are the same exercise only when movement and every
  variation match, one value per axis. That identity is fixed when a row is logged.
- **Intensity words are hints, not identity.** "Heavy" and "lighter" can shape the number on
  the sheet, but a heavy sumo deadlift is the same exercise as a sumo deadlift.
- **A line is fully read, or it is "not sure".** Filler is a closed list too ("rest",
  "finisher", "count & remember reps"). A word that is none of these leaves the row unsure: it
  is shown, it can be logged, but it borrows no number.
- **A full read can still be wrong**, so the reader also refuses these, found on the corpus:
  - two movements side by side with no connector ("bench bird dog low row", "TRX bridge row");
  - a word that means one thing on one movement and another elsewhere ("wide" is a stance on
    a squat and a grip on a curl), so such words are only listed with the word that settles
    them ("wide stance", "wide grip");
  - "with" before only an implement or a modifier ("press with y-bell", "with power"): that is
    a variation of the movement before it, not a combo.
- **Related exercises are linked by stated factors**, shown in "Why this number" ("close grip,
  floor: about 0.85 of chest press"). Where no sound factor exists, the row says "new: go
  light". Until strength per movement is switched on (see the scorecard), only identical
  exercises share history.
- **New words go to a review list.** madCode sorts each new word or name once: which movement,
  which variation, harder or easier, or filler. The answer is an event in the phone's log, so it
  never relabels history already logged and is backed up with it. A useful answer can be
  proposed back to the starting list in a PR.
- **Where the lists live.** The starting lists are public knowledge, so they are JSON files in
  `coach/src/main/resources/spotter/`, shipped inside the app and changed by PR:
  `movements.json` (each movement, its true synonyms, pattern, muscles, and whether it is
  one-legged) and `words.json` (variations by axis, connectors, rep-scheme words, filler).
  madCode's own answers live only on the phone.
- **A movement is what is lifted; everything around it is a variation.** Carry's list baked
  variations into names ("goblet squat", "reverse grip low row") and filed different exercises
  as aliases ("hammer curl" under bicep curl, a bare "rotation" under torso rotation). Here an
  alias is only another spelling, a named variation is a word on its axis, and a combo ("lunge
  with torso rotation") is two movements and a connector, never a list entry.
- **Every phrase means one thing.** Loading the lists fails if a phrase is in two places, so
  which one a line meant never depends on file order. The longest phrase wins when reading:
  "high plank" is a position before "high pull" could be a movement.

## Loads

Dumbbells come in 3, 5, 8, 10, 12, 15, 20, 25, 30, 35, 40, 45 and 50 lb; "one step" means the
next one in that list. A Y-bell is another implement and never steps onto that list.

- A load is a count of dumbbells and the weight of each: `2×25`, `1×40`.
- The one conversion between counts: one dumbbell held in both hands (goblet) to two dumbbells
  whose total is no more than it, rounded down to the rack (1×40 → 2×20). Two dumbbells are
  never merged into one: a single-arm lift relates to a two-dumbbell one through a variation
  factor, not arithmetic.
- Imported history gives the weight of each dumbbell but often not how many. Such a load can
  step on the rack, but has no total and converts to no other count. It only feeds a row whose
  count it can't contradict: a row with no count of its own, or one whose name fixes the count
  (a goblet squat is one dumbbell). Otherwise an imported "squat 40" that was a goblet could
  become 2×45 on a two-dumbbell row. Imported decimals round down, never up.

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
  reps), with "felt easy / hard", "twinge", "had to stop" and "did an easier version" one tap
  away. "+ Each round" is optional: each new round starts as a copy of the previous one, so
  only what changed needs a tap. The row's heaviest weight and reps stay its one number.
  Untouched rows stay unlogged, never "skipped". "Add an exercise" records one the board
  missed by picking it from the list.

Later: progress, review of new exercise names, sharing a board for others, the watch.

## Backup

Android backup of the log, plus a scheduled export (the scorecard shape and the full log) to
a folder madCode picks, such as Downloads or a synced folder.

## The watch (later)

madCode wears a Garmin. The watch view is a Connect IQ app (Monkey C) that shows the sheet's
names and numbers; the phone sends it the day's sheet through Garmin's Connect IQ companion
SDK. The coach stays on the phone; the watch only shows what it is sent.

## The scorecard decides

The export writes what gym-coach's `scorecard.py` grades: one file per class with a top-level
`date` (YYYY-MM-DD) and a row per exercise. Each row's `suggested_weight` and `weight` are the
weight of one dumbbell, never a sum (2×25 is 25, a goblet 1×40 is 40), with the count in a field
of its own. `suggested_weight` is null when the sheet showed no number. Rows with no weight, or
`skipped`, aren't graded.

Over every logged weighted set: did the sheet give a number (*numbered*), was it within one
dumbbell of what was lifted (*close*), and how many were more than a step *high* or *low*.
Beside it, the board corpus is graded line by line: how many rows are read fully, unsure, or
wrong against hand-checked answers. Wrong stays at zero; unsure falls as the review list grows.
Spotter switches on for real when, over the same weeks of real classes, it numbers more rows
than Carry, gets more close, and is high no more often. Strength per movement (variations
reading one estimate through stated factors) ships behind a switch and is judged the same way
against plain per-exercise rules before it is turned on.
