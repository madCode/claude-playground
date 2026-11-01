# Moving the coach onto the phone

**Status:** plan, agreed 2026-11-01 (decisions at the end). Nothing is
built yet; phase 1 is next.

## Why now

Today the coach lives on the laptop. It runs every half hour, reads the day's board
from Reddit and writes a session file into a folder that Syncthing copies
to the phone. Carry shows the file, takes the log and writes it back the
same way, and the laptop imports it into the history the next day.

On 2026-11-01 the coach did its job: it wrote the day's file at 09:51. The
sheet never reached the phone, because the copy between the two machines
stopped. Nothing on either side noticed. Every failure on this path looks
the same from the lobby, "no sheet for today", and finding which part broke
took two Claude sessions and the owner.

The owner, 2026-11-01: *"time to move the brain and the reddit pull to the
app so everything is coherent and in one place and we're not dealing with
syncing issues."* This replaces the "eventually" in the backlog with a plan.

## What moves, and what it depends on

| On the laptop now | On the phone after |
|---|---|
| Exercise knowledge base: `exercises.yaml` (movements, aliases) and `variants.py` (implements, grips, stances) | The same data, as an app asset |
| Matcher: board text to rows (`gym_lib.match_wod`, combos) | Kotlin, in a `:brain` module |
| Suggestions: streaks, estimates, floors and ceilings, related lines | Kotlin, in `:brain` |
| Board fetch from Reddit (`reddit_fetch.py`) | Carry, on Wi-Fi or data, on a schedule |
| Which class is when: a private calendar URL (`gym_calendar.py`) | The phone's own calendar (`READ_CALENDAR`), as already designed in [read-class-from-calendar](read-class-from-calendar.md) |
| History (`history.jsonl`) and the next-day import | Carry, which already writes the log |
| Weekly audit and coverage probe | Stay on the laptop, reading an export of the history (below) |

`:brain` is a plain JVM module, so it needs no Android. That keeps its
tests fast under `./gradlew :brain:test` and lets a cloud session run it.

## The rule that decides how

Carry's one rule exists so that there is **one** derivation of exercise
identity. The system before it lost about a third of what was reported by
working identity out from free text in a second place. A port creates a
second derivation for as long as both run. So it is built so the two can't
drift without a test going red:

1. **Shared fixtures first.** Before any Kotlin, gym-coach generates
   fixtures from the Python as it is now:
   - every board line in the corpus (about 280 boards), with the row it
     parses to;
   - for suggestions, (history, board, date) and the sheet Python
     produces.

   The port passes all of them, and so does every later change, in both
   languages, until the Python is retired.
2. **Two kinds of history fixture.** Public fixtures use a made-up lifter's
   history (gym-coach's trainer simulation already invents one). The real
   history never enters this repository. A private parity check runs the
   Kotlin against it on the laptop or from a gym-data export, and reports
   only "same" or the lines that differ.
3. **Run both in parallel before switching.** For two weeks, or at least six
   classes, Carry works out its own sheet for each class and compares it
   with the laptop's file, row by row. The sheet shown stays the laptop's.
   Any difference is a bug to fix before the switch, not after.

## Phases

Each phase is one or more cycles, each its own PR, with CI green and a
fresh-eyes review.

1. **Fixtures.** gym-coach: a `fixtures` command writing line-to-row and
   board-to-sheet fixtures from the corpus and the simulated lifter.
   Checked into Carry, under `brain-fixtures/` (but see question 2).
2. **Knowledge base and matcher.** `:brain` reads the same YAML and parses
   lines. Done when every line fixture passes, combos included.
3. **Suggestions.** Rules 1 to 4, streaks, estimates, claims, the hurt and
   incomplete rules, ladders. Done when every sheet fixture passes and the
   private parity check is clean on the real history.
4. **History on the phone.** One-time import of the laptop's
   `history.jsonl` into Carry. After that, Carry adds each finished session
   itself, the same way `session_file.to_history` does now, with its own
   tests. Carry writes an export to the synced folder after every change,
   for the audit and as a backup.
5. **Board and class on the phone.** The phone's calendar for when, and
   Reddit for what: an installed-app token, which needs no secret stored
   in the app, plus the borrow-the-other-class rule and its timing window.
   Background work in WorkManager, like the hourly sheet check.
6. **Side-by-side period**, as above.
7. **Switch.** Carry's sheet becomes the sheet. The laptop job stops
   writing sheets and importing, and keeps the audit. Syncthing is then
   only a backup copy, not part of getting the sheet.
8. **Retire the Python brain.** Rule changes happen in Kotlin, against the
   fixtures. gym-coach keeps `PLAN.md`, `TRAINING.md` and the audit.

## Fine-tuning after the move

*The owner asked: easier or harder?* About the same, if the tools come
across with the code, and harder if they don't. So they're part of the
plan:

- **The tuning loop stays.** Today a change is checked by replaying the
  history and planning a board (`gym.py plan`), and by the evaluation
  harnesses (`eval_trainer_sim.py`, `eval_advice.py`). `:brain` gets a
  small command line to do the same (`./gradlew :brain:run --args "plan
  board.txt"`), and the two harnesses are ported in phase 3, not left
  behind.
- **The knowledge base stays a data file.** Adding an alias or a movement
  is still a one-line edit to YAML. It reaches the phone with the next
  debug build, which already updates itself.
- **Slower:** Kotlin is longer to write than Python, and a Gradle run takes
  seconds where `uv run` is instant.
- **Better:** the history is on the phone, so tuning works on the real
  record rather than a copy that may be days behind. The fixtures also
  make every rule change show exactly which sheets it moves, which today
  only the audit does, a week later.
- **Live fixes need a build.** On the laptop, a fix merged to main is live at the next run.
  On the phone it waits for the next debug build: the app checks every
  six hours and installs on Wi-Fi, or sooner when opened.

## What stays on the laptop

- The weekly audit and coverage probe, reading the exported history.
- `PLAN.md` and `TRAINING.md`: the reasoning behind each rule.
- The research harness that samples old Reddit threads (`real_harness.py`).

## Decisions (owner, 2026-11-01)

1. **Rewrite in Kotlin**, with the fixtures as the guard. Not Chaquopy:
   Python on Android runs only on a device or emulator, so every coach
   test would become an emulator test, and the APK would grow by tens of
   MB.
2. **Board lines may be published** in this repository, since they come
   from public r/orangetheory workout posts. Lines only: no usernames, and
   the full comments in `wod_cache/` stay in gym-coach.
3. **History backup:** Carry writes its export to the synced folder, and
   Syncthing carries it from the phone to the laptop. Claude reads that
   folder for analysis: a local session reads it there, and a cloud
   session reads the copy the laptop job puts in the gym-data repository.
4. **The switch:** after the side-by-side period shows no differences. The
   owner expects the fixtures and the back-test over every old board to
   carry most of the weight, so the side-by-side period is a short
   confirmation on live classes, not the main test.
