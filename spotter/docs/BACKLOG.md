# Backlog

The running plan. Each cycle picks what matters most next, builds it with tests, and moves it
to Done. The order is the handoff's build order ([HANDOFF.md](HANDOFF.md)), each step
usable on its own.

## Done when

The run of cycles stops when build steps 1 to 6 are done (madCode extended it from step 3 on
2026-11-22). When the next item waits on madCode's answer, a cycle takes another item, a review
or the docs rather than guess: the order may change, nothing is built on a guess.

- [x] The coach reads boards, names exercises, and suggests weights, with every trap tested
  (traps 12 and 13 are checked on the scorecard and after a release, not by rule tests).
- [x] The phone shows today's sheet from the board feed and the calendar.
- [ ] Classes can be logged, kept in the event log, exported in the scorecard's shape and backed
  up.
- [x] History import (step 4): a `history.jsonl` madCode picks on the phone; tested on invented
  data only.
- [ ] Strength per movement (step 5), built behind a switch and left off: turning it on waits
  for the scorecard on madCode's real history.
- [ ] Progress, the new-word review screen, sharing a board (step 6).

## Next

Step 1, the coach core:

- [ ] One exercise, one key, found by cycle 3's review: "dumbbell" and a bare line on a
  lift ("dumbbell alt lateral lunge" and "alt lateral lunge"), "lying on bench" and "bench",
  "rows" and "low row" and "bent-over row", "trx row" and "TRX low row", "bench RFE split squat" and "RFE split squat", "high side
  plank" and "side plank [high plank]", "bosu bridge" and "bridge (feet on bosu)". Settle per movement
  with the corpus grade. *(cycle 3)*
- [ ] Refused but readable, from the same review: `position` holds what you're on, your body
  shape and where the weight sits, so "low bench high plank … row" and "incline bench chest
  press" clash; "high plank jack" loses "plank jack" to "high plank". *(cycle 3)*
- [ ] With load: a banded line with no implement ("banded squat") doesn't want a weight
  (trap 6). *(cycle 3)*
- [ ] The sheet's "why this number" detail and warnings shown on screen: step 2's screens.
  *(design)*
- [ ] More stated factors for borrowing, madCode asked for them (2026-11-22): single arm from two,
  Bosu, a b-stance or balance version from the plain one, each conservative and in the reason.
  One leg from two (0.6) is in. *(madCode)*
- [ ] The corpus grade: every line of `boards.jsonl` read fully, unsure or wrong, against
  hand-checked answers for a sample. Moved after the sheet: it checks readings, but puts no
  number on the screen. *(design)*

Step 2, the phone shows today's sheet: done (see Done).


Step 3, logging:

- [ ] Fixes from the review of today's logging decisions (cycle 13), before more is built on
  them. Each is a wrong number or a guess entering history:
  - "+ Each round" will need a weight per round, not one per row.
  - A ladder done clean with each round's weight known ("8 / 6 / 4") counts as clean at 8 for
    a straight "8 x" row, which is more sets at 8 than were done. *(cycle 13 review)*

- [ ] The Log screen, second half: "+ Each round", each round a copy of the one before; "Add an
  exercise"; and the person's calls below. *(madCode)* From cycles 11 and 12's reviews:
  - With two logged rows of one exercise and one row for it on the new board, clearing the
    carried one brings the other onto that row at the next read. *(cycle 18 review)*
  - The Log screen reopened from a sheet shown before the last save finished loading can save
    against a stale match. *(cycle 18 review)*
  - Stepping the reps of a carried entry counts as entering them anew, so the set is read
    against the new row's target, not the one it was logged under. *(cycle 18 review)*
  - A repeat of a line added above the one done shifts the repeat number, so the log moves to
    the other copy (same words, so the same weight).
  - Two classes of the same format on one day share one log (keyed by date and "2G"), so
    logging the first skips the second's sheet; and with two formats in a day, the earlier
    one's sheet reads the later one's log. Key a class by its start as well. *(cycles 12, 15)*
  - "… isn't logged yet" counts rows by id, so a class logged from a board since changed reads
    as not logged; it also can't be dismissed for a class left unlogged on purpose. *(cycle 15)*
- [ ] "Felt hard" (and an imported unfinished set) delays a step only at the same reps: a
  clean set at other reps from before it can still raise the number. *(cycle 19 review)*
- [ ] Export notes for whoever reads the files (cycle 16 review): a session's `ref` is the row's
  place among logged rows, not on the board, and `borrowed_from` isn't known from the log; a
  timed row's seconds are read from its stored words at export time.

## Your call

From the review of today's logging decisions (also on the morning page). Until answered, the
app keeps what it does now, except where noted.
The research is in [TRAINING.md](TRAINING.md) (madCode: "shouldn't the science have an
opinion?"). What it says is beside each question; the choice is still madCode's.

- **A tick on a fixed count** ("12 x squat", ✓ as listed): up a step every time, as now; up
  after two ticks in a row; or up only with "felt easy"? Research: no trial compares them; the
  consensus (ACSM) is two sessions in a row, and "felt easy" is a fair reason to go up sooner.
- **A tick on a range** ("8-10 x"): counts as the low end, so the weight holds until "changed"
  says the top was reached. Or offer the top as a second one-tap choice? Research: working up
  to the top before adding weight is the best-supported rule, so the top needs to be easy to
  log.
- **A ladder's weight** ("8 / 6 / 4"): remember the round-one weight, or the heaviest round's?
  Until answered, a ladder logged with one number holds. Research: nothing direct; the shape
  doesn't change results, so either works if kept consistent, and the heaviest round must be
  judged against its own reps, never converted to round one's.
- **Borrowed board, then your own with a different count** (ticked "12 x" on the 3G board, your
  2G board says "8 x"): trust your board's count, or ask?

New board words that stay "not sure" are worth a look on the corpus now and then; 142 rows
still are.

## Recurring reviews

See [practices/recurring-reviews.md](../../practices/recurring-reviews.md).

- [ ] Security and supply chain: before the first release
- [x] Architecture and test health: cycle 10, 2026-11-22. Its smaller findings are under
  "Tidy-up", below.
- [ ] Skills and instructions: around cycle 12

## Tidy-up (from the cycle 10 review, when a cycle has room)

- Move `spoken` and the sheet's title and notes into the coach; make `glance` a row member;
  one label for "not sure" (it's built in three places).
- Unused or unwired: `Load.splitSharedDumbbell` and `total` (DESIGN describes the goblet
  conversion as live), `Movement.muscles`, `BoardLine.label`/`side`/`rest` outside tests, an
  unreachable branch in `TodaysBoard.fromDay`.
- `BoardLine.read` classifies and parses in one 65-line function: split out the kind.
- Tests: `MainActivityTest` returns early on local runs (use `Assume`); one shared `shoot()`;
  UI tests that assert whole reason sentences.
- Comments: `Sheet.make`'s doc sits above a private field; plan and date notes in
  `MainActivity` and `Identity`.

## Later

- The replay tool's switch for strength per movement, when step 5 is built
  ([REPLAY.md](REPLAY.md)). The gym-coach session asked for it (2026-11-22).
- Strength per movement, behind a switch, judged on the scorecard. (Step 5.)
- Progress, the new-word review screen, sharing a board. (Step 6.)
- The Garmin watch view, a Connect IQ app the phone sends the sheet to. (Step 7.)
- Choose which calendar Spotter reads, for privacy. Android's calendar permission covers every
  calendar on the phone, so the choice lives in Spotter: list the calendars by name, let the
  person pick one or more, and query only those (`Calendar`, `CalendarContract.Instances` by
  calendar id). Until they pick, it reads all visible calendars as now. Say plainly on the
  screen that Android's permission still covers all of them. *(madCode, 2026-11-22)* Low
  priority: it limits only what Spotter reads, not what Android allows, and Spotter already reads
  just today's and tomorrow's titles and times and keeps nothing.

## Not doing

- A lock-screen card or widget: madCode glances in the app (2026-11-21).
- A GitHub token or Reddit key on the phone: the board comes from the public feed, backup goes
  to a folder madCode controls.

## Done

- [x] History import on the phone (cycle 21): pick a `history.jsonl`, kept beside the log; the
  sheet reads it; the home screen says what was read and left out; replace or remove it; a day
  in both counts once.
- [x] The replay tool (cycle 20, asked by the gym-coach session for madCode): `:coach:replay`
  takes a `history.jsonl` and writes a scorecard session per class, each made from the days
  before it only. How to run it: [REPLAY.md](REPLAY.md). madCode grades in private; nothing
  comes back from that session.
- [x] Reading gym-coach's `history.jsonl` in the coach (cycle 19): each row read in full or left
  out with a reason; on Carry's invented-lifter history, 698 of 880 weighted rows are read.
- [x] A log follows the exercise when the board changes (cycle 18): rows logged from a borrowed
  or revised board carry over to the row with the same exercise; the rest are listed on the Log
  screen with Clear. *(madCode, 2026-11-22)*
- [x] The export goes to a folder madCode picks (cycle 17): after each save, once a day and when
  the folder is picked; only changed files are written, an emptied class's file is removed, and
  the home screen says where the log is or that the copy failed. Android backup keeps the log.
- [x] The export, from the log (cycle 16): a scorecard session per logged class and the full
  event log, with no board needed.
- [x] Which class, after logging (cycle 15): the sheet moves on once today's class is logged;
  an earlier class stays loggable for two days, which also covers a late-evening class past
  midnight.
- [x] "Changed" picks from the rack (cycle 14): one tap on any of the thirteen weights, nothing
  logged before the pick, the count never guessed, last time's weight shown.
- [x] The coach's half of the logging review (cycle 13): a one-weight ladder holds; twinges cap
  from the latest, always say so, and a twinge-only history shows that weight; "felt hard"
  delays a step; each rep count keeps its own history, and conversions start from the reps
  done; events store what the sheet showed; row ids have a fixed format; the log migrates
  from version 1.
- [x] The export grades: `scorecard.py` (gym-coach main) read `scorecard-example.json` with exit
  0, numbered 67%, close 67%, as expected (run by another session at madCode's ask,
  2026-11-22).
- [x] The Log screen, first half (cycle 12): ✓ as listed or changed (weight, one or two, reps),
  how it felt, saved as events in one transaction; the sheet reads the log; the class stays
  three hours after it starts so it can be logged in the car.
- [x] Before the Log screen (cycle 11): a sheet row's stable id, which events record; a typed
  "new: go light"; JVM tests for a row's "why" and "understood".
- [x] The event log: Room, append-only (insert only), schema exported to `app/schemas`; corrections
  and removals are events; a logged row keeps the identity it was read with. Also from the
  review: a ladder in totals is reps a side. Cycle 10, 2026-11-22.

- [x] The sheet on the phone: the class and its date, notes for a borrowed or earlier board,
  warnings first, rows in board order under their blocks with the board's words, what Spotter
  read and the number; a tap shows why. The calendar is asked for once, on first open. Cycle 9,
  2026-11-22.

- [x] The day's board from the feed, a week kept on the phone, the last good one marked with its
  date when the day's can't be fetched; the class from the calendar's next booking; the other
  format's board, labelled, when the class has none. Cycle 8, 2026-11-22.

- [x] The scorecard shape: one session file per class in Carry's `gym-session/1` format, weights per dumbbell with the
  count apart, a null suggestion where the sheet showed no number, and blank rows not skipped.
  Cycle 7, 2026-11-22.

- [x] The sheet: rows in board order under their blocks, warnings first, ▲ ▼ =, a test weight
  on a big jump; combos from their parts held the same way, the lightest (trap 2); bodyweight,
  banded and bare push-ups want no weight (trap 6); ladders and totals as reps a side for round 1
  (traps 7, 8); a borrowed board says so (trap 9); an imported count takes the row's. Cycle 6,
  2026-11-22.

- [x] Progression: up, hold, down from a log of sets; a hurt set never raises a number (trap 3);
  a later miss caps an older success (trap 4); Epley only in 3 to 12 reps; a test number on a
  big jump; one leg borrows 0.6 of the plain two-legged lift (trap 10), a loose relative
  nothing (trap 11). Cycle 5, 2026-11-22.

- [x] madCode's answers on the board words: "to stand", "balance", "press" after a squat or lunge,
  "with rotation", "wide" on a curl, "step-out deadlift", "anchor", "switch", "hinge", high plank
  jack and pop, "ultimate burpee", "b-stance", "dumbbell tap", "tap front squat", feet on a bench
  for a climber, shoulders on a bench for a bridge, and hands or feet on the Bosu. 2026-11-22.

- [x] Board words with a plain meaning: 22 exercises, "rear foot on bench", "moderate", "buy-out" and "recover when done"; the rest on madCode's list above. Cycle 4,
  2026-11-22.

- [x] Identity: each line's words named as movements with variations, or "not sure" with why;
  traps 6 and 10, the close-grip floor press, and the review's refusals tested. Cycle 3,
  2026-11-22.

- [x] Board line reading: rep schemes, labels, sides and rest split from the exercise's words;
  instructions and headings kept out; traps 5, 7 and 8 tested. Cycle 2, 2026-11-22.

- [x] `app/` with its own icon, `.debug` beside release, the version on screen, and the debug
  channel, which installs newer builds by itself. madCode asked for self-update first,
  2026-11-22.
- [x] Starting lists: movements, variations, connectors, rep-scheme words and filler, as JSON in
  the coach, loaded by `Vocabulary`. Cycle 1, 2026-11-21.
