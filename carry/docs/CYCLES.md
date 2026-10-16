# Cycle log

For the next cycle, not for the owner: what each cycle would do
differently. Newest first; read the top before starting. What was built is
in [DEVLOG.md](DEVLOG.md). Kept to the last ten or so cycles; older lessons
that hold become rules in CLAUDE.md or the cycles skill.

## 2026-10-16 run (cloud session)

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
