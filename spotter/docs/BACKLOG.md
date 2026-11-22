# Backlog

The running plan. Each cycle picks what matters most next, builds it with tests, and moves it
to Done. The order is the handoff's build order ([HANDOFF.md](HANDOFF.md)), each step
usable on its own.

## Done when

The first run of cycles stops when build steps 1 to 3 are done: the first version the scorecard
can grade against Carry.

- [ ] The coach reads boards, names exercises, and suggests weights, with every trap tested.
- [ ] The phone shows today's sheet from the board feed and the calendar.
- [ ] Classes can be logged, kept in the event log, exported in the scorecard's shape and backed
  up.

## Next

Step 1, the coach core:

- [ ] One exercise, one key, found by cycle 3's review: "dumbbell" and a bare line on a
  lift ("dumbbell alt lateral lunge" and "alt lateral lunge"), "lying on bench" and "bench",
  "rows" and "low row" and "bent-over row", "trx row" and "TRX low row", "bench RFE split squat" and "RFE split squat", "high side
  plank" and "side plank [high plank]". Settle per movement
  with the corpus grade. *(cycle 3)*
- [ ] Refused but readable, from the same review: `position` holds what you're on, your body
  shape and where the weight sits, so "low bench high plank … row" and "incline bench chest
  press" clash; "high plank jack" loses "plank jack" to "high plank". *(cycle 3)*
- [ ] With load: a banded line with no implement ("banded squat") doesn't want a weight
  (trap 6). *(cycle 3)*
- [ ] The corpus grade: every line of `boards.jsonl` read fully, unsure or wrong, against
  hand-checked answers for a sample. *(design)*
- [ ] Progression: up, hold, down from a history of sets; traps 3 and 4 as tests; Epley only in
  3 to 12 reps. *(handoff)*
- [ ] The sheet: rows in block order, warnings first, ▲ ▼ =, a reason for every number, test
  numbers; traps 2, 9, 11. *(design)*
- [ ] The scorecard shape, as DESIGN.md's scorecard section says: one session file per class.
  *(handoff; the shape from the session that wrote `scorecard.py`)*

Step 2, the phone shows today's sheet:
- [ ] Fetch the board feed, keep the last good board, and pick the class from the calendar.
- [ ] Sheet and "Why this number" screens, with screenshot tests. *(mockups)*

Step 3, logging:

- [ ] The event log in Room, with exported schemas. *(design)*
- [ ] The Log screen: ✓ as listed, changed, feel, each round copying the one before. *(madCode)*
- [ ] Export in the scorecard's shape and the full log, on a schedule, to a folder madCode
  picks; Android backup. *(madCode)*

## Your call

- **Board words for madCode to sort**, from the public corpus, with the line they come from.
  Each stays "not sure" until answered; a guess, where there is one, is in brackets. *(cycle 4)*
  - "tap": "high plank alt dumbbell tap", "tap front squat". (Touch the dumbbell; a front-loaded
    tap squat?)
  - "to stand": "sit-up to stand", "seated shoulder press to stand". (Its own exercise, or
    ignore?)
  - "balance": "balance bicep curl". (On one leg?)
  - "with rotation": "push press with rotation", "thruster with rotation". (Which rotation?)
  - bare "press": "squat to press", "reverse lunge to press". (Shoulder press?)
  - "step-out deadlift", "step back low row".
  - "wide": "wide bicep curl". (Wide grip?)
  - Setup: "feet on bench", "hands on bosu", "hand on bench", "foot on bench" on a bridge,
    "shoulders on bench" (a bench bridge; on a press, a press in a bridge), "feet in straps".
  - "anchor": "chest fly (anchor)", "knee drive (feet on bosu) - anchor".
  - "switch": "bear plank switch alt low row". "hinge": "split squat with hinge".
  - "tap squat jump", "single leg hop", "split squat hop", "ultimate burpee", "b-stance goblet
    squat", "bent arm lateral raise", "straight arm sit-up".
  - "reach": "with overhead reach", "chest press to reach".
  - "high plank jack" and "high plank pop": a plank jack or pop done in high plank?

## Recurring reviews

See [practices/recurring-reviews.md](../../practices/recurring-reviews.md).

- [ ] Security and supply chain: before the first release
- [ ] Architecture and test health: around cycle 8
- [ ] Skills and instructions: around cycle 12

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
