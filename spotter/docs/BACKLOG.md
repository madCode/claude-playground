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
- [x] Classes can be logged, kept in the event log, exported in the scorecard's shape and backed
  up. (What the Log screen still lacks is under Next.)
- [x] History import (step 4): a `history.jsonl` madCode picks on the phone; tested on invented
  data only.
- [ ] Strength per movement (step 5). Built: wordings that leave the weight as it is share one
  history, and a tweaked lift takes an estimate from its nearest logged version (DESIGN.md,
  "Words that share a weight" and "Tweaks that take an estimate"). On the yardstick (an
  invented lifter working through the board corpus): 110 rows of 361 with a number before,
  128 with shared wordings, 140 with estimates, 158 with factors for the harder tweaks, 165 with a band no longer deciding and
  one dumbbell against two, 167 with more board lines read, 170 with lifts filed apart linked (a reverse and
  a forward lunge, a lateral lunge at 0.8, a split squat at 0.6 of the squat). Next: which
  other movements are one lift under two names (curtsy, cross-back and walking lunges; rows).
  Then the person's own measured ratio replacing a stated factor.
  The measured ratio waits (cycle 28): a ratio measured on one movement is only sound for
  that movement, and there its one-leg version then has a log of its own, which already
  outranks any factor. Carried to another movement it would be the loose relative trap 11
  rules out. Worth building only if the replay on a real history shows first goes it would
  have helped.
- [ ] Progress, the new-word review screen, sharing a board (step 6). Built: the progress card
  (cycle 29). It names an exercise by its key ("bicep curl [alternating=yes]"), as the sheet
  does beneath a row; plainer names want the reader's help. Its "for 6" is reps a side, also for a row the board
  writes as "12 total". The review list is built read only (cycle 31): sorting a word, and sharing a board, wait
  on madCode (Your call). "Whether the advice is working" is on it too, as counts (cycle 30);
  taking the sheet's "try" weight counts as "lifted heavier", since the log doesn't keep the
  try weight.
  Still to build: sorting a word on the review list, sharing a board.

## Next

Step 1, the coach core (ticked above; these are its leftovers):

- [ ] One exercise, one key, found by cycle 3's review: "dumbbell" and a bare line on a
  lift ("dumbbell alt lateral lunge" and "alt lateral lunge"), "lying on bench" and "bench",
  "rows" and "low row" and "bent-over row", "trx row" and "TRX low row", "bench RFE split squat" and "RFE split squat", "high side
  plank" and "side plank [high plank]", "bosu bridge" and "bridge (feet on bosu)". Settle per movement
  with the corpus grade. *(cycle 3)*
- [ ] Refused but readable, from the same review: `position` holds what you're on, your body
  shape and where the weight sits, so "incline bench chest press" clashes ("low bench high plank" and "bench high plank"
  are now positions of their own); "high plank jack" loses "plank jack" to "high plank". *(cycle 3)*
- [ ] More stated factors for borrowing, madCode asked for them (2026-11-22): single arm from two,
  Bosu, a b-stance or balance version from the plain one, each conservative and in the reason.
  One leg from two (0.6) is in. *(madCode)*
- [ ] The corpus grade: every line of `boards.jsonl` read fully, unsure or wrong, against
  hand-checked answers for a sample. Moved after the sheet: it checks readings, but puts no
  number on the screen. *(design)*

Step 2, the phone shows today's sheet: done (see Done).


Step 3, logging:

- [ ] A ladder done clean with each round's weight known ("8 / 6 / 4") counts as clean at 8 for
  a straight "8 x" row, which is more sets at 8 than were done. *(cycle 13 review)*

- [ ] A ladder's later rounds, now that their weights can be logged: 30 held for the 6 of
  "10 / 8 / 6" says nothing yet to a later "6 x" row, which converts from round one's. And a
  twinge or a stop is put on the heaviest round, since the screen doesn't ask which.
  *(cycle 24)*
- [ ] The reader, what the review list still shows after cycle 32 took the tread and rower
  instructions out: "front foot on bosu" beside "bosu" reads as two stabilities in conflict
  (the more specific should win); "band around the wrists" and "low to high raise" aren't
  listed; "High plank until ready for next round" and a finisher named inside a "When done"
  line stay unread. 414 corpus lines are still "not sure" for unknown words. A bare timed "row"
  under a label ("Finisher: 30 sec row") is taken for the rower, and typed on the Log screen
  it is refused as not an exercise: write "low row" there.
- [ ] Log screen, from the cycle 25 review: "12 total x chest press" typed for a two-handed
  lift is read as 6 a side, as a board line is.
- [ ] Log screen, from the cycle 24 review: "Last time" under "Heaviest dumbbell" shows the
  weight the coach read a ladder at, which can be round one's; a carried ladder's rounds are
  labelled with this row's reps when the two have as many rounds.
- [ ] The Log screen, second half: the person's calls below. *(madCode)* From reviews:
  - With two logged rows of one exercise and one row for it on the new board, clearing the
    carried one brings the other onto that row at the next read. *(cycle 18 review)*
  - The Log screen reopened from a sheet shown before the last save finished loading can save
    against a stale match. *(cycle 18 review)*
  - Stepping the reps of a carried entry counts as entering them anew, so the set is read
    against the new row's target, not the one it was logged under. *(cycle 18 review)*
  - A repeat of a line added above the one done shifts the repeat number, so the log moves to
    the other copy (same words, so the same weight).
  - Two classes of the same format on one day share one log (keyed by date and "2G"), so
    logging the first skips the second's sheet. Key a class by its start as well; rare enough
    to wait for a second "2G" in one day to be a real plan. *(cycles 12, 15)*
  - "… isn't logged yet" counts rows by id, so a class logged from a board since changed reads
    as not logged; it also can't be dismissed for a class left unlogged on purpose. *(cycle 15)*
- [ ] A board that alternates 8s and 12s, following the sheet: the 12s stall behind the big
  jump from 20 to 25, and then the 8s read the latest 20 as overruling their clean 30 and drop
  to 20. The reason also mentions a test weight that isn't shown. *(cycle 22 review; older)*
- [ ] A range ticked at its low end holds whether or not it "felt easy", and reaching the top
  takes "Changed": waits on the range ticks ("✓ 8" / "✓ 10") in Your call. *(cycle 22 review)*
- [ ] "Felt easy" before a big jump (12 to 15) still holds and offers the test. *(cycle 22 review)*
- [ ] "Felt hard" (and an imported unfinished set) delays a step only at the same reps: a
  clean set at other reps from before it can still raise the number. *(cycle 19 review)*
- [ ] Export notes for whoever reads the files (cycle 16 review): a session's `ref` is the row's
  place among logged rows, not on the board, and `borrowed_from` isn't known from the log; a
  timed row's seconds are read from its stored words at export time.

## Your call

Answered by madCode in chat on 2026-11-23; each is built in the order under "Next" unless it
says otherwise.

- **Ticks on a range:** no tick; a button for each count (built).
- **An Epley "try" offer:** yes (built). A step up earned at other reps is offered as "try 30
  if set 1 feels easy" above the converted number; it never sets the number.
- **Variations that share a weight:** yes, build it. First set, where the number carries
  straight across: alternating; naming the implement; "floor" or "bench" on a pullover or
  press; "seated" on an upper-body row or curl; grip words; "(heavy)" and "(light)". Built,
  but for grip words (a board lists "close grip chest press" beside "chest press" at another
  weight): share them or not is madCode's to say again. Still to
  agree: factors for single arm, single leg, split stance, bosu, tempo, incline. The yardstick
  is the share of weighted rows with a number when an invented lifter works through the board
  corpus: 110 of 361 before this work, 128 with the first set built (without grip words).
- **Coming back to a lighter weight:** a limit of eight weeks (built). The research counts in
  weeks, not classes, and eight is on the cautious side (TRAINING.md, 9).
- **Sharing a board:** start with the floor rows. madCode: "and maybe row weights only", which
  needs asking (the rows' weights, or the rower?) before it is built.
- **The morning page:** it depends on the project. Spotter belongs on Carry's page; it would
  not belong on newspapeRSS's, nor share one with the BART widget or Jekyll Poster. Both
  skills should say: a project shares a page only with a project it works beside.
- **"One thing per cycle":** the project's own pace wins when it states one.
- **The emulator and watchdog section:** move it to `practices/android.md`.
- **`practices/lifecycle.md`:** point at the handle-issues skill instead of restating it.
  (These four are written into the skills and practices, 2026-11-23.)

Still open:

- **A history row that doesn't say how many dumbbells** takes its count from madCode's own
  counts file (COUNTS-FORMAT.md), read by the import and the replay and picked on the
  phone's home screen. Behind it, the movement list's own usual numbers fill what is left, counted on the home
  screen. Watch for one that proves wrong.

- **Sorting a word on the review list.** How should the phone ask? Proposed first step: two
  answers only, "ignore this word" (filler) and "it's another name for …" with a movement
  picked from a search box. An answer changes how every later board reads, so it shouldn't be
  guessed at.

Settled on 2026-11-22: up after two clean sessions or one that felt easy (built, cycle 22); a
ladder's one number is its heaviest round's (as built; "+ Each round" for the rest, built in
cycle 24); a log from
a borrowed board is trusted as logged, with no prompt (built, cycle 18).

New board words that stay "not sure" are worth a look on the corpus now and then; 142 rows
still are.

## Recurring reviews

See [practices/recurring-reviews.md](../../practices/recurring-reviews.md).

- [ ] Security and supply chain: before the first release
- [x] Architecture and test health: cycle 10, 2026-11-22. Its smaller findings are under
  "Tidy-up", below. Next: around cycle 40, or before the first release.
- [x] Skills and instructions: cycle 28, 2026-11-23. Next: around cycle 45. What it left for
  madCode is under "Your call".

## Tidy-up (from the cycle 10 review, when a cycle has room)

- Move `spoken` and the sheet's title and notes into the coach; make `glance` a row member;
  one label for "not sure" (it's built in three places).
- Unused or unwired: `Load.splitSharedDumbbell` and `total`, `Movement.muscles`,
  `Reading.Known.hints`, `BoardLine.rest` outside tests, an unreachable branch in
  `TodaysBoard.fromDay`.
- `BoardLine.read` classifies and parses in one 65-line function: split out the kind.
- Tests: `MainActivityTest` returns early on local runs (use `Assume`); one shared `shoot()`;
  UI tests that assert whole reason sentences.
- Comments: `Sheet.make`'s doc sits above a private field; date notes in `Identity`.

## Later

- The Garmin watch view, a Connect IQ app the phone sends the sheet to. (Step 7.)
- Choose which calendar Spotter reads, for privacy. Android's calendar permission covers every
  calendar on the phone, so the choice lives in Spotter: list the calendars by name, let the
  person pick one or more, and query only those (`Calendar`, `CalendarContract.Instances` by
  calendar id). Until they pick, it reads all visible calendars as now. Say plainly on the
  screen that Android's permission still covers all of them. *(madCode, 2026-11-22)* Low
  priority: it limits only what Spotter reads, not what Android allows, and Spotter already reads
  only titles and times from two days back to tomorrow, and keeps nothing.

## Not doing

- A lock-screen card or widget: madCode glances in the app (2026-11-21).
- A GitHub token or Reddit key on the phone: the board comes from the public feed, backup goes
  to a folder madCode controls.

## Done

- [x] Two clean sessions to go up (cycle 22; madCode, 2026-11-22): a tick holds; two clean
  sessions in a row at a weight go up a step; one that felt easy goes up at once; a weight
  lifted above the sheet's becomes the number and waits its own two.
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
