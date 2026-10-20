# Cycle log

For the next cycle, not for the owner: what each cycle would do
differently. Newest first; read the top before starting. What was built is
in [DEVLOG.md](DEVLOG.md). Kept to the last ten or so cycles; older lessons
that hold become rules in CLAUDE.md or the cycles skill.

## 2026-10-20 (cloud session, second Carry agent, later)

### Cycle 72 — build: tests for the guards, and `MainActivity`

- **Results from a script on a shared path are unproven** (cycle 71's
  note on the scratchpad): rerun every falsification it touched.
- **Drive the real Activity in Robolectric** with
  `createAndroidComposeRule<MainActivity>()`, `shadowOf(activity)
  .nextStartedActivityForResult` and `receiveResult`, then
  `scenario.moveToState` for the resume. Before asserting a screen, wait
  for the one read it depends on (wrap the ViewModel's `read` seam and
  count).
- **`compose.waitUntil` in an Activity test runs on `runTest`'s virtual
  time**, the same trap CLAUDE.md names for `withTimeoutOrNull`: it timed
  out with its condition already true, about one run in five, and on CI.
  Wait on the real clock and call `compose.waitForIdle()` each pass
  (`MainActivityTest.until`). A dump on timeout showed it: catch the flake
  before guessing at it.

## 2026-10-20 (cloud session, first Carry agent)

### Cycle 71 — build: updates, part one (debug-only installer, its checks)

- **AGP 9 makes unit tests for debug only.** There is no
  `testReleaseUnitTest`, so code moved to `src/debug` can still be tested
  from `src/test`; tests for it go in `src/testDebug` anyway, so a later
  release unit test task can't break on them.
- **This container's memory is shared with other agents' builds, and the
  OOM killer takes the biggest JVM: the Gradle daemon.** "Daemon
  disappeared" is that. `--max-workers=1 -Dorg.gradle.jvmargs="-Xmx2g
  -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process`
  compiled, and `-Xmx1g` ran the tests. A baseline built while the source
  was being edited measured nothing: build it from a separate worktree
  first.
- **Two agents in one session share its scratchpad.** Both wrote
  `falsify.sh` there; one run executed the other agent's script, in the
  other agent's worktree. Keep scripts and logs in a folder of the
  agent's own (`$S/<agent>/`), and have a script `cd` to its own tree and
  check it.

## 2026-10-20 (cloud session, second Carry agent)

### Cycle 70 — build: an old *hard*-only row counts

- **A "the other side is changing to match" note in DESIGN.md is a claim
  to check.** #133 said gym-coach would stop counting `hard`; gym-coach
  then chose to keep it. Read the other repo's code before trusting it.
- **The machine is shared and loaded.** A full `testDebugUnitTest` took
  23 minutes and a Compose idle timeout flaked under a load of 39; the
  first build's daemon was killed for memory. Run the touched classes
  with `--tests`, and take the baseline from main's CI artifact
  (`carry-reports`), which is the number CI gates on.

## 2026-10-18 (cloud session)

### Cycle 69 — fix: Add takes you to the new row, and mod stays put

- **The rows merge no semantics, so a sibling matcher can't scope a chip.**
  Every chip on the After page is a sibling of every box. Find a row's
  chip by sitting level with its box (`chipOn` in `LogScreenTest`).
- **Robolectric records the keyboard.** `shadowOf(InputMethodManager)
  .isSoftInputVisible` turns true on a focus request, so "the keyboard
  opens" can be asserted. Start from false, or it proves nothing.
- **A background run's own timeout kills it, files half-edited.** A
  falsification script stopped mid-case left a broken source; restore
  with `git checkout` and give the run the full time it needs.

## 2026-10-16 run (cloud session)

### Cycle 66 — build: a save refuses a file that changed underneath

- **Ask how long the thing lives before hardening it.** Three review
  rounds on #136 each found a deeper edge in keeping typing across
  sessions; madCode then chose the simple version because the session
  file is temporary. Size the fix to the file's lifetime first.
- **`settled()` waits; it doesn't drive the scheduler.** Advancing it
  from a Default thread while `runTest` drained it ran two Main tasks at
  once, an intermittent failure in the save tests.

### Cycle 65 — build: the bump band keeps an old *hard* row

- **When a rule leaves the runner, check what else it changed in the same
  release.** The `hard` removal (gym-coach #50) sat on top of #49, which
  made `hurt` end a streak, and the band had no hurt check. Read the
  runner's neighbouring commits, not only the one the item names.
- **A "before" shot for a test the old code fails:** copy the test into a
  detached `origin/main` worktree with its assertions cut, so it shoots.

### Cycle 64 — build: the minimum After page (#127 v4)

- **A text field reports its inner bounds.** `BasicTextField`'s semantics
  node is the text, not the padded box, whatever the modifier order, so a
  layout test compares centres rather than edges.
- **Prove the hand-off with the real importer.** A throwaway test wrote a
  file through `SessionPatch`; `session_file.to_history` read it. That is
  what confirmed "10 total" must fill 5, not 10.

### Cycle 63 — build: drop the hard chip

- **A layout test can lose its premise when a chip goes.** The timed-row
  test asserted `skip` wraps below `done`; with one chip fewer it fits.
  Assert what the test guards (skip on screen), not the layout that once
  carried it.

### Cycle 62 — build: show a typed name back

- **`clearAndSetSemantics` takes a node out of the test tree too.** To
  hide a visible label from TalkBack and still assert on it, use
  `hideFromAccessibility()` and check `SemanticsProperties.HideFromAccessibility`.

### Cycle 61 — build: require the schema label

- **A field's default can hide a rule.** `schema` defaulted to our own
  label, so a missing key passed the gate by construction. Check a gate
  against the raw input, not the decoded model.

### Cycle 60 — build: the bump band reads every sheet

- **Write a date in a fixture, check its weekday.** A screen test failed
  on "Sat 4 Oct" for a Sunday, and the first guess was a merged
  semantics node. `date -d 2026-10-04 +%a` before writing the assertion.
- **A changed scope rewrites tests that pinned the old one.** Three tests
  asserted "the newest sheet and nowhere else". Each was replaced or
  widened on purpose, and the PR names them, so a reviewer can tell a
  deliberate change from a test bent to pass.

### Cycle 59 — build: a repeated line is posted once

- **Check a new screenshot's number against every `shoot(` in the test
  tree.** The started branch had `22-post-repeated-line` beside an
  existing `22-timed-row` and `22-past-session`; the PNGs sort together
  and a reviewer reads the wrong pair. `grep -rhoE 'shoot\("[0-9]+' app/src/test`.

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
