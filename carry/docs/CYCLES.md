# Cycle log

For the next cycle, not for the owner: what each cycle would do
differently. Newest first; read the top before starting. What was built is
in [DEVLOG.md](DEVLOG.md). Kept to the last ten or so cycles; older lessons
that hold become rules in CLAUDE.md or the cycles skill.

## 2026-10-16 run (cloud session)

### Cycle 60 — build: the bump band reads every sheet

- **Write a date in a fixture, check its weekday.** A screen test failed
  on "Sat 4 Oct" for a Sunday, and the first guess was a merged
  semantics node. `date -d 2026-10-04 +%a` before writing the assertion.
- **A changed scope rewrites tests that pinned the old one.** Three tests
  asserted "the newest sheet and nowhere else". Each was replaced or
  widened on purpose, and the PR names them, so a reviewer can tell a
  deliberate change from a test bent to pass.

### Cycle 58 — build: the After screen checks what the importer refuses

- **Read the parse next to the check.** The range check was the ask, but
  the reps box already stored `8.5` as null while showing it: a silent
  drop the new check would have missed, because it looked at the stored
  value. Check every way a box's text can fail to become its number.

### Cycle 57 — build: the bars beside each family's heaviest go

- **A removal has no behaviour to test, so the screenshot is the guard.**
  `17-since` already shoots this band, and its before and after are the
  whole proof. The trail's one unit assertion went with the trail.

### Cycle 55 — audit: the band picks its sheet by name (#113)

- **Falsify against the class the test is in.** A Kotlin test file can hold
  several classes. A falsification run filtered to the wrong one came back
  clean and nearly passed as proof. Find the class with
  `awk '/^class /{c=$2} /<test name>/{print c}'`.

### Cycle 54 — audit: a null read counts as not read (#112)

- **"Is it common?" is the question for a new warning.** The review checked
  every path where `SessionFolder.read` returns null before agreeing the
  Since tab wouldn't cry wolf. Ask it up front for any change that shows a
  new message.

### Cycle 53 — audit: delete `ExerciseSet.answered` (#111)

- **Dead code with a different definition is a bug waiting.** Grep for
  every caller, and delete it rather than aligning it.

### Cycles 50–52 — build: update check, installer, class-timed notice (#108–#110)

- **Every review of an install path found a data-loss window.** Typed
  numbers live until saved, so recheck "unsaved" immediately before anything
  that restarts the app, not only at the tap.
- **A watcher that blocks between reading its record and writing it can
  act on a stale one.** Read the record again after any slow call.
- **Maven Central rate-limits cloud sessions.** `install-android-sdk.sh`
  installs the mirror (#104). A Gradle "Could not find" for a well-known
  artifact is that, not a missing dependency.

### Cycles 48–49 — build: icon, build numbers (#105, #106)

- **A filled background hides a blank render from `isFlat()`.** Check the
  picture for the colours that should be in it.
