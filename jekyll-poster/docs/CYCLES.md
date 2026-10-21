# Cycle log

For the next cycle: what to do differently, newest first. What each cycle built is in
[DEVLOG.md](DEVLOG.md). Keep the last ten or so.

**The run of 21 Oct (night)**, as madCode set it: cycles until everything in BACKLOG.md that isn't
waiting on madCode is done, or 7am Pacific (14:00 UTC), whichever comes first. About 60% build,
40% audit. Near the end, documentation cycles until the docs match the app. One of them checks
that the app's update system (the `jekyll-poster-debug` release) matches the other apps' (Carry,
newspaperss), and puts any difference in BACKLOG.md.

### Cycle 47 — build: Hide from the site

- Falsify each fix alone. One test covering three fixes went red when all three were broken,
  and stayed green when two of them were broken one at a time: the test app sends at once
  whatever time is chosen, and the first odd YAML tried wasn't one the check refuses.
- Read the JUnit XML, not `gradlew -q`'s silence: a loop whose restore failed printed nothing
  either way.

### Cycle 44 — build: the `?` key and Sign in with GitHub twice, on the emulator

- Read the code a check is about before writing its test: two tokens for one account are two
  sign-ins on purpose (`AccountStore.save` merges only the same token, or GitHub App sign-ins). A
  live test written from the card's wording asserted the opposite.
- A fake that hands out one token for every sign-in can't tell two merge rules apart. Make the
  fake as distinct as the real thing where the rule under test depends on it.
- A failure picture beats a guess: the sign-in test's failure was blamed on the notification
  shade, but its picture showed the app on Blog & privacy, a screen the test didn't expect.

### Cycle 43 — build: Only connect through a VPN, with a real one

- The first falsification of the VPN test stayed green: the worker waits for the VPN before the
  network gate ever sees a request. Break the layer the test claims to cover, not the nearest
  one, and add an assertion at that layer (here, the request record).

### Cycle 42 — build: the laptop's device checks on CI's emulator

- `UiAutomation.executeShellCommand` splits at spaces and doesn't parse quotes: pass text to
  another app as Base64.
- At large text a `LazyColumn` composes only what fits: wait for what heads the list.

### Cycle 39 — audit: what GitHub knows about the writer

- Read the run's records, not the code: the commit's `-0700` and the blog read three times on
  opening only show up there. Next audits start from the latest nightly artifact
  (`jekyll-poster-device-live`), which a cloud session downloads with `actions_get`.

### Cycle 38 — build: the device live check on the tester's blogs

- The real GitHub found what the fake couldn't (TLS closed on the main thread). A change to
  connections or threads wants the device live check run on its PR, not only the fake suite.
- A scan that guards a public artifact must fail closed: test it against the failure (a missing
  file, a name with spaces) as well as the leak.

### Cycle 36 — build: device tests on an emulator, on every PR

- A cloud session can't run an emulator (no KVM): the PR's own CI is the only run. Make the
  first push print what a dead app leaves (`adb logcat -d -b crash`, logcat as an artifact),
  or a crash at start reads only "Instrumentation did not complete".
- Nothing on the device looks up "localhost": the fake GitHub is 127.0.0.1 by number.
- Falsify device tests in one pushed commit that breaks each covered feature, then revert it.

### Cycle 35 — build: a request record in debug builds

- Android lint crashed (`Unexpected owner function: null`, in `ExperimentalDetector`) on a class
  whose constructor declared `val dir: File`; a plain parameter assigned to a property passed.
  When `lintAnalyzeDebug` crashes on a new file, try that before disabling a detector.
- Two hiding rules over the same secret (a form field and a token shape) make each test pass
  without its own rule. Give the test a secret only its rule can catch, then falsify each rule.

### Cycle 34 — build: the open blog never reads as signed out while its sign-in goes

- A race seen once in 20 full runs, and never in 200 runs of its own test, became certain with a
  fixture: a DataStore whose `data` is 300 ms slow. When a timing failure won't repeat, slow down
  one side on purpose rather than counting more runs.

### Cycle 33 — build: front matter the line editor can't see

- A harness that ran 38 front matter shapes through the real `PostWriter.edit` found what reading
  the parser hadn't: a `?` key, or front matter written as one `{…}`, lost keys on an edit. Its
  shapes are now the table in `EditKeepsTheRestTest`; add to it rather than reasoning about YAML.

### Cycle 32 — build: the Compose-test hang, found, and the preview flake, counted

- The "Room hang" of cycles 1, 11 and 18 was the main looper. Robolectric runs the test on the
  main thread, and Compose's `waitUntil` turns the looper only when its condition reads the
  screen. A wait on the database, GitHub or a view model's state uses `waitUntilLooped`
  (`testutil/Idle.kt`); a thread dump showed Room's threads idle, not stuck. `runBlocking` on a
  view model's suspend function hangs the same way: call it as the screen does.
- Count a flake in full runs, not in its class alone: 40 runs of the class in one JVM never
  failed, and 20 full runs failed it twice.
- This container is shared. Exit 137 (out of memory) and another agent's `gradlew --stop` are
  not test results: read "What went wrong" first. Run Gradle with `--max-workers=2`, one build
  at a time, from this worktree only.
- `git checkout -- .` to undo a falsification break also undid uncommitted docs, as cycle 27
  warned about code. Commit everything, docs too, before a break.

### Cycle 31 — audit: a UX pass on the blog switcher

- Rendered with `ScreenshotTest` (21 to 28) and read as a writer with two blogs. Nothing needed
  changing; the request-log half of the audit needs a real run's log, which a cloud session
  can't make (a question for madCode on the morning page).

### Cycle 30 — build: the same account signed in twice

- No subagent tool again: the review ran through `code-review`, and its second look still found
  a real gap (a "Sign in with GitHub" without a refresh token stored as a pasted token). Ask the
  reviewer how each value is inferred, not only how it's used.
- `pkill -f` with a pattern from your own command line kills your own shell (exit 144). Kill by
  PID.

### Cycle 29 — build: privacy per blog

- Two tests passed under their break because the test's own steps re-did the fix's work: turning
  the VPN switch on renewed the preview before the blog switch it was testing, and reading the
  token back renewed it again. When a break stays green, look for a step in the test that does
  the fix's work, rather than at the fix.
- A compile error in a falsification break leaves the last run's XML behind, which reads as a
  result. Check the log for `e:` lines after every break.
- A design decision that leaks under review (a new blog's VPN off) was changed and flagged for
  madCode in DEVLOG and the PR, not silently kept or silently changed.
- madCode answered that question on the morning page with "I don't know what that means". Write
  a decision as the situation they'd be in ("you add a blog to an account whose other blog uses
  the VPN…") and what each answer does to them, not in the design's terms.

### Cycle 28 — build: several sign-ins and the blog switcher

- `koverVerify` doesn't rewrite `build/reports/kover/report.xml`: a report left from an
  earlier cycle in the same container gave the old figure. Run `koverXmlReport` before quoting
  coverage, and check the file's time.
- Run a new test green before falsifying it. Two flow tests went "red" under a break while
  they were already failing on the fix (navigation called off the main thread), so the break
  proved nothing; the failure message is the check: it should be the assertion, not a crash.
- A race the review finds (two steps with a window between) needs a delay added in the
  falsification break to widen the window; say so in the PR. `removingTheOpenBlogsSignIn…`
  went red only with a 300 ms delay between the delete and the key move.
- Cycle 3 builds on `FakeGitHub.addAccount` (a second account, its own token and log) and
  `SeveralSignInsTest`; the switcher's rows are `BlogList` in `ui/blogs/Blogs.kt`.

### Cycle 27 — build: a queued post goes to its own blog

- Commit before every falsification: a `git checkout` of a file to restore a break also
  reverted uncommitted review fixes in it, and they had to be re-applied by hand.
- `FakeGitHub.addRepository` serves a second blog; `TwoBlogsTest` shows how to drive two blogs
  through the real switch path. Cycle 2 builds on both.
- The review ran through the `code-review` skill (the build agent had no subagent tool); it found
  three real races and gaps. Keep pointing it at switch and sign-out during background work.

### Cycle 26 — design: several sign-ins and blogs

- The design waits on madCode in [#137](https://github.com/madCode/claude-playground/issues/137).
  Build only after a clear yes there, and build its three calls as answered, not as proposed.
- Start build cycle 1 from the issue's "How it's built": the code map in it (file:line) was
  checked on this cycle's main. `Account` is one DataStore record, `Settings` app-wide,
  `CachedPost` has no blog column; only `Draft.blog` is per blog.
- The SDK installs with `tools/install-android-sdk.sh`, and the screenshot test
  then renders all 20 screens. Run both in the background at the start of any cycle that
  touches a screen.
- Mockups for a proposal: HTML at 412×915 with the app's own `res/font/bricolage.ttf` and the
  Zine colours from `ui/theme/Theme.kt`, beside the screenshot test's "before" PNGs. Put the
  note boxes where they cover nothing: the first render put two over rows.
