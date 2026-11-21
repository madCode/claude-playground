# Handoff: a fresh app for the lobby glance

madCode asked, on 2026-11-21, for this app to be built from scratch in a new
session, beside the current Carry (`../carry/`), which keeps working until this
one is better. "Better" is measured, not felt: see **The scorecard decides**.
This file is the brief. It hands over what a month of Carry learned, not its
code. Read it whole before planning; the name `carry-next` is a placeholder
for madCode to change.

## Who it's for

One person who does Orange Theory classes, usually 2G or 3G:
a treadmill block and a floor block of dumbbell, bench and bodyweight work.
Each day's workout, "the board", is posted by members on r/orangetheory as an
intel comment; a class with none of its own mostly follows the other format's.

**The phone can't go into the studio.** The weights are read in one glance at
the gym, in the app, or at home beforehand. Logging happens in the car right
after class. A watch can go in, where a phone can't.

## The user stories (madCode's own, as outcomes)

Before class:

1. Walk into class knowing which weight to grab for each exercise, from one glance beforehand.
2. Not repeat last week's weight out of habit when it's time to go up or down.
3. Get a science-backed and personal-training-history-backed recommendation on what weight to use for every exercise. OTF does many variations of a movement (split squat, front squat, goblet squat); learning and growing across the variations is critical.
4. Know before starting when something went wrong last time (missed reps, pain, a run of unfinished sets). A warning is worth more than a suggestion.
5. Get advice for the class actually being taken, today or tomorrow, without telling the app which one.
6. Advice for one variation doesn't carry over blindly to a harder one: single arm, one dumbbell or two, bosu, split stance, tempo, combos, ladders, timed holds, "12 total" vs "6 each".

After class:

1. Record what was done quickly (weight, reps, how it felt, whether it hurt, whether an easier version was done), often in the car right after.
2. Record exercises the board missed, in a way the coach will recognise later.
3. History holds only what was actually done, as done. A gap is better than a guess: a wrong number is worse than a missing one, because nothing later can tell the difference.
4. What's logged never quietly changes meaning later: an exercise recorded stays that exercise.
5. When nobody posted the class's workout, help others by sharing it, seeing exactly what goes out before it does.

Over time:

1. Get steadily stronger: advice goes up when ready and backs off after a big miss or pain, as the training research says.
2. See progress: what has moved, and what is close to a bump.
3. Know whether the advice is working: are the suggested weights completed?
4. The coach keeps up with new exercise names on boards, and never mislabels one without madCode's say.

Living with it:

1. It updates itself without them doing anything.
2. When Reddit, GitHub or Wi-Fi is down, there's still the last good sheet, never a blank or wrong one.
3. Training data is theirs: backed up where they control it, never public.

## What madCode decided (2026-11-21)

| Question | Answer, and what it means |
| --- | --- |
| What's remembered after class | Sometimes reps per round and how each felt, often only the heaviest weight and reps. One row per exercise records the heaviest; rounds and feel are optional, one tap away. Rows in block order help memory. |
| Where the glance happens | In the app, at the gym or at home. No lock-screen card. A **watch** view is the big later bet (ask madCode which watch). |
| Backup | Android backup plus a scheduled export to Downloads or a synced folder. **No GitHub token on the phone.** |
| Test numbers ("12 · try 15 if set 1 felt easy") | Yes. |
| Pain | Usually a twinge trained through, maybe adjusting the weight. A twinge holds the weight and notes it; "had to stop" drops a step. |
| Weight between rounds | Sometimes changes; they log the heaviest. One number per exercise on the sheet. |

## The design to build

The full reasoning is in madCode's "Carry redesign options" doc (two
independent proposals and a merged one). In short:

- **One coach, in Kotlin, on the phone**, with no Android code inside it so it
  runs in plain JVM tests. No second implementation in another language.
- **History on the phone**, as an append-only log of events: a correction is a
  new event, nothing is edited in place, and an exercise's identity is fixed
  when it is logged.
- **Weight is per hand plus a count** ("2×25", "1×50"), never a bare total.
- **Strength per movement, not per exercise name.** Each movement keeps one
  estimate; variations read from it through stated factors (single arm, bosu,
  tempo, one leg ≈ 0.6 of two), replaced by the person's own measured ratios
  after a few paired sessions. Where no sound relationship exists, the row says
  "new: go light", no number. Ship it behind a switch and compare it with plain
  per-exercise rules on the scorecard before turning it on.
- **Progression as the research says:** straight sets with 1–2 reps in reserve;
  up after clean sessions; hold after a near miss; down a step after a miss of
  3+ reps, a miss on the first set, broken form, or having to stop. Epley
  estimates only within 3–12 reps. Test numbers when the next dumbbell is a big
  jump.
- **Every number explains itself**, one tap away; the glance shows only the
  number, ▲ ▼ = against last time, and warnings first.
- **Logging:** each row "✓ as listed" or "changed" (pick the heaviest weight
  from the rack, reps); optional per-round detail. Untouched rows stay
  unlogged; rows from a borrowed sheet that weren't in the class are easy to
  leave.
- **Install beside Carry** under its own package name, so both run on the
  phone until the switch.

Dumbbells come in uneven steps: 3, 5, 8, 10, 12, 15, 20, 25, 30, 35, 40, 45, 50 lb.

## What already exists to reuse (public, in this repository)

- **The day's board:** a scheduled job (`.github/workflows/carry-boards.yml`,
  `carry/tools/boards/`) publishes `boards/YYYY-MM-DD.json` to the
  `carry-boards` branch every half hour: each class's intel comment cut to its
  workout lines. Schema `carry-board/1`. Fetch it; never put a Reddit key in
  the app.
- **The exercise list:** `carry/brain-fixtures/exercises.json`, about 170
  movements with aliases, patterns and muscle groups.
- **A board corpus for parser tests:** `carry/brain-fixtures/boards.jsonl`,
  277 real boards with what the current matcher read from each line.
- **Which class is when:** the phone's calendar; booked classes have the format
  in the event title. `carry/app/.../CalendarReader.kt` shows one way.
- **Self-update:** `carry/app/src/debug/` installs newer CI builds silently on
  Android 12+, which needs `UPDATE_PACKAGES_WITHOUT_USER_ACTION` declared
  (without it, every install asks).

Read these for facts; don't copy the app's structure.

## The traps (each has cost a plausible wrong number)

Each is a test to write before the code it guards. Examples are invented.

1. **Per hand vs total.** A 40 lb goblet squat is one dumbbell in both hands; a
   two-dumbbell front squat from it is 20 a hand at most, not 35.
2. **A combo set credits its parts only on the same implement.** A 26 lb Y-bell
   deadlift-to-calf-raise says nothing about a dumbbell deadlift.
3. **A set that hurt never raises a number,** neither as a floor nor as a
   source for a start.
4. **A later miss caps an older success.** Clean at 40 on Tuesday, 3 of 8 at 35
   on Thursday: the next suggestion is under 35, not 40.
5. **Misread lines.** "High plank pull through" is not a high pull; "Good
   morning, all!" in prose is not the good-morning exercise. Long prose lines
   are commentary, not workout.
6. **A bare line isn't bodyweight.** "5 x squat" with no implement, from
   someone who has squatted with 40 lb, wants a weight; "5 x air squat", "BW",
   "banded", "hold", "pulses", "jumps" don't.
7. **"12 total" is 6 a side; "6 each" is 6 a side.** Reps logged against such a
   line are easy to double count.
8. **Ladders** ("8 / 6 / 4"): the number is for the first round picked up.
9. **Borrowed sheets** list exercises that weren't in the class; leaving them
   blank must be normal, never "skipped".
10. **One leg from two.** A single-leg or split-stance row from a two-legged set
    is well under it (≈ 0.6). "Step-up", "lunge" and "single-leg deadlift" are
    one-legged even when the line has no stance word.
11. **A loose relative is no basis.** A chest fly says little about a chest
    press: no number, not a tiny one.
12. **Silence is a failure too.** The old coach answered "by feel" for most
    rows; the newer one gave numbers that ran light, by stacking cautions
    (halving when unsure of the implement, then a step down, then another).
    Both are caught by the scorecard, not by rule tests.
13. **Code that isn't running isn't a fix.** A merged change once reached no
    sheet for a week. After each release, check the next sheet shows it.

## The scorecard decides

The private repository `madCode/gym-coach` has `scorecard.py` (merging now):
over every logged weighted set, did the sheet give a number (**numbered**),
was it within one dumbbell of what was lifted (**close**), and how many were
more than a step **high** or **low**. It grades any app from session files
that record, per row, the weight the sheet showed (`suggested_weight`) and the
weight logged (`weight`), with the session's `date`.

- **Write that shape**, or export it, so the same scorecard grades this app.
- **The switch from Carry happens when this app's scorecard beats Carry's** on
  the same weeks of real classes: more rows numbered, more close, no more high.
- madCode's figures stay private: never in this public repository, its tests,
  screenshots or commits. Tests use an invented lifter.

## Importing history

madCode's history lives in gym-coach's format (`history.jsonl`, one set per
line: `date`, `exercise` or `components`, `line`, `variant`, `weight`, `reps`,
`prescribed_reps`, `easy`, `hard`, `hurt`, `skipped`, `incomplete`,
`breakdown`). Older rows don't say whether a weight was per hand or total, so
import them with the count marked unknown and treat them as weaker evidence.
The import reads a file madCode provides at runtime; their data never enters
this repository.

## How to work

The repository's root `CLAUDE.md` and `practices/` hold the shared rules:
small reviewed PRs, tests that could catch a regression, screenshots for
screens, privacy. Start with the `new-project` skill and madCode, then build
in this order, each step usable:

1. The coach core: board parsing, identity, per-hand weights, progression,
   the traps as tests, the scorecard shape.
2. A phone app showing today's sheet from the board feed and the calendar.
3. Logging, the history log, the export and backup.
4. History import from the current coach.
5. Strength per movement, behind a switch, judged on the scorecard.
6. Progress, test numbers, new-name review, sharing intel.
7. The watch view.
