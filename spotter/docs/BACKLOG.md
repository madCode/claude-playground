# Backlog

The running plan. Each cycle picks what matters most next, builds it with tests, and moves it
to Done. The order is the handoff's build order ([HANDOFF.md](HANDOFF.md)), each step
usable on its own.

## Done when

The first run of cycles stops when build steps 1 to 3 are done: the first version the scorecard
can grade against Carry.

- [x] The coach reads boards, names exercises, and suggests weights, with every trap tested
  (traps 12 and 13 are checked on the scorecard and after a release, not by rule tests).
- [x] The phone shows today's sheet from the board feed and the calendar.
- [ ] Classes can be logged, kept in the event log, exported in the scorecard's shape and backed
  up.

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

- [ ] The Log screen, second half: "+ Each round", each round a copy of the one before; "Add an
  exercise"; and the person's calls below. *(madCode)* From cycles 11 and 12's reviews:
  - Logged rows no longer on the sheet (a borrowed board replaced by the class's own, a line
    the board's poster revised) can't be seen or cleared, and stay in history beside the new
    board's rows. Show them, with Clear.
  - A row logged from a stale or borrowed board matches the real board's row by name the next
    time; say whether that's wanted, or store the board's date and source with the event.
  - A repeat of a line added above the one done shifts the repeat number, so the log moves to
    the other copy (same words, so the same weight).
  - Two classes of the same format on one day share one log (keyed by date and "2G").
  - A class after 23:00 is gone at midnight, before it can be logged.
- [ ] The scorecard export from the log: it takes rows by index (`ScorecardSession.from`); map
  ids to places. *(cycle 11 review)*
- [ ] Export in the scorecard's shape and the full log, on a schedule, to a folder madCode
  picks; Android backup. *(madCode)*

## Your call

- **One logged set is read as the first set.** The Log screen records the heaviest weight and
  its reps, one number per row, as you asked. The rules then read it as set 1, so one rep short
  ("7 of 8") counts as a miss on the first set and drops the next number a step, even with a
  twinge. Keep that, or read a single logged set more gently (hold after one or two short)?
  *(cycle 12)*

- **Check the export against `scorecard.py`** (cycles can't read gym-coach): run it on
  [scorecard-example.json](scorecard-example.json), an invented class, and say whether it
  grades. It is in Carry's `gym-session/1` format, so it should. *(cycle 7)*

- **Board words still unsorted**, after madCode's answers of 2026-11-22. Each stays "not sure"
  until answered. *(cycle 4)*
  - Setup: "hand on bench" on a row, "foot on bench" on a single-leg bridge, "feet in straps".
  - "tap squat jump", "single leg hop", "split squat hop", "bent arm lateral raise", "straight
    arm sit-up".
  - "reach": "with overhead reach", "chest press to reach".

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

- History import from gym-coach's `history.jsonl`: unknown counts stay unknown, decimals round
  down. (Step 4.)
- Strength per movement, behind a switch, judged on the scorecard. (Step 5.)
- Progress, the new-word review screen, sharing a board. (Step 6.)
- The Garmin watch view, a Connect IQ app the phone sends the sheet to. (Step 7.)

## Not doing

- A lock-screen card or widget: madCode glances in the app (2026-11-21).
- A GitHub token or Reddit key on the phone: the board comes from the public feed, backup goes
  to a folder madCode controls.

## Done

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
