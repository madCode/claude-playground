# Backlog

Where this is: [DEVLOG.md](DEVLOG.md) opens with a status block and the debug
build link. How it works now: [DESIGN.md](DESIGN.md). What is specified but not
built: [designs/](designs/). This file is what is next.

The running plan. Each cycle picks what matters most right now — device feedback
first, then what the audit turns up — builds it with tests, and ticks it off.
Feature proposals are ideas, not commitments.

## Next

Grouped by part of the app. The tag says where each item came from: *(you asked)*
(the owner), *(device)* (real-phone testing), *(audit)* (the security pass or the
weekly coach audit), *(design)* (has a design doc in
[docs/designs/](designs/) — what it is, why, what makes it non-trivial, and how
to know it worked; written 2026-10-14 so a session can start without re-deriving
the analysis).

### Screens
- [x] ~~**Write the streak into the session file so the dots can light.**~~
  **The diagnosis was wrong and nothing needed writing.** `session_file.py`
  has written `streak_done`/`streak_needed` all along; what was null was
  `Suggestion.streak_done` itself, and the files on disk were all written
  before the runner learned to count the converted streak. Measured
  2026-10-16 against the real history: a sheet written **today** would carry
  dots on **most** of its lines — and **every one of them at 1 of 3**,
  because each setup has been done once. The figures stay with the data, in
  the companion repository's eval report, not here (see "Privacy" in
  `CLAUDE.md`).
  What that revealed instead is in #79: rendered with those numbers the
  Since band was eleven rows of one lit pip under the heading "close to a
  bump" *(you asked)*
- [x] ~~"How close each one is" over-claims in the quantifier~~ — **"Part
  way to a bump"**, which is what every row in it is and claims nothing
  about scope.
- [x] ~~The band sorts by `done/needed`, not by the question its row
  answers~~ — fewest sets away leads now, with how-far-along as the
  tie-break and the sheet's own order under that. Under a fraction a
  movement two sets from a bump outranks one a single set away, and with
  the band capped that is the row that gets cut.
- [x] ~~Three shapes the band's fixture never produced~~ — `family = null`,
  a `needed` other than 3, and one past `MAX_PIPS`. The last one has a real
  consequence: unclamped, a 40-set streak draws 40 pips and squeezes the
  movement name to about one character. Asserted on the name's width, since
  the pips themselves have no semantics to count.
- [ ] **The band shows the runner's streak beside the runner's suggested
  weight, and ignores what the owner logged in the same row.** `Growth.of`
  consults only `skipped`; `weight`, `hard` and `easier` are in the same
  JSON object and Carry wrote them. So a row suggested at 40 lb, logged at
  35 and marked `hard`, still reads "2 of 3 clean sets, 40 pounds" under
  *part way to a bump* until the runner's next pass. Excluding a row whose
  logged weight is below its suggested one would close most of it; the
  streak itself must stay the runner's (`Growth.kt`, "It derives, it never
  coaches") *(audit)*
- [ ] **The streak's weight and the weight shown are different
  quantities.** `streak_done`/`streak_needed` mean "clean sets held at this
  weight" (`Session.kt`), and the band pairs them with `suggestedWeight`.
  When the runner proposes a bump while the streak is unfinished, the row
  reads "2 of 3 clean sets, 45 pounds" for two sets held at 40.
  `previousWeight` is on the same set and nothing compares them. Not
  reachable if the runner only bumps on a finished streak *(audit)*
- [ ] **Aggregate facts about the owner's training are in this public repo,
  predating tonight.** `CLAUDE.md`'s Privacy section rules them out — "the
  test is whether a stranger learns something about the owner's training
  from the sentence" — and names the fix: say "most" or "a small minority".
  Four that are already on `main`: `SinceScreen.kt` ("26 distinct movements
  across 32 sets and exactly one of them done twice"), `DESIGN.md`
  ("20 of 20 lifts"), `GrowthTest.kt` ("19 of 20 distinct"), and the
  "a third of this owner's logged sets" line repeated in `DESIGN.md`,
  `LogScreen.kt`, `LogScreenTest.kt` and `PastSessionScreenTest.kt`. #79
  removed the ones it had just added and left these, because they are
  load-bearing reasoning in several places and converting them is a pass of
  its own — **and it may be a deliberate exception you want kept**.
  Three more the #80 review found that the list above would have missed:
  "eleven rows of one lit pip" in this file, and the same figure in
  `DEVLOG.md` twice, once in a section heading *(audit)*

- [ ] **Should a movement done once appear in the progress band at all?**
  #79 kept it and capped the band to five rows with the rest counted; #80
  settled the heading at *part way to a bump*. The alternative is to show only what is genuinely close
  (2 of 3), which on today's data empties the band. Keeping it shows
  progress; cutting it keeps the screen to signals you can act on. The
  "why the coach is often quiet" band already explains why they all sit at
  one, which argues for keeping *(you asked — your call)*
- [x] **Open a past session from the Since list** — no screen answers "what did I
  lift that day" *(you asked, design:
  [open-a-past-session](designs/open-a-past-session.md))*
- [ ] A session the owner logged entirely by hand is not listed at all: `Growth.of`
  decides what counts as a session from the SHEET's rows, so a borrowed day whose
  sheet rows are all blank and whose real content is in `added` never reaches
  *Every session*. That is the commonest shape of a borrowed day. Changing it moves
  the session count, the date span and the quiet-band figures, so it is its own
  decision. (#77 widened the same filter to count markers as well as numbers, and
  deliberately stopped at the sheet's own rows so as not to answer this in passing.)
  Two things to fix with it, found by the #77 review:
  `Day.lifts`/`Day.best` and the `movements` count read `session.sets` only, so such
  a day would render "0 lifts" over a list with a weight in it; and `Row.answered`
  ignores the typed name, so a hand-logged row with a name and nothing else is
  written to the file and still counted as never having happened *(audit)*
- [ ] **Branch coverage is 67% while the Kover floor is 85% on
  instructions.** The gate is real but it is counting the wrong thing for
  a codebase this full of fail-closed guards — a third of branches have
  never been taken. Not a demand for a second gate (a test written only to
  lift a number is worse than the number), but worth one pass over the
  classes with the widest gap: `SessionFolder` 78%, `CarryAppKt` 77%
  *(audit: CI health)*
- [x] ~~After a successful write, `save()` re-seeds `_edits` from the text
  it wrote, and nothing can distinguish that line from its absence.~~
  **Answered, and it was worse than dead.** The #78 review found the
  re-seed was observable in exactly one case and observably wrong there:
  the *"only if nothing newer arrived"* guard wrapped one line and the
  re-seed ran regardless, so a sheet arriving mid-write left the old
  session's numbers on the new session's rows, with "Saved" still tappable
  above them. Fixed in #78; the whole tail is inside the guard
- [ ] `MainActivity` is at **0%** and `LogBinding` at 0%. The first is the
  SAF plumbing the emulator is honest about and unit tests are not, so it
  is the one place a device check earns its keep; the second is a plain
  data holder *(audit: CI health)*
- [ ] **The intel post still demands a number**, so a wholly bodyweight or timed
  class offers no post at all. `done` is the one marker that states the movement
  WAS in the block, which is this path's publishing criterion — but what goes to
  r/orangetheory is the owner's call, so ask before changing it *(audit)*
- [ ] `ExerciseSet.answered` has no production callers and is the only one of the
  three definitions of "answered" across the two repos that omits `done`. Delete it
  or make it the shared one *(audit)*
- [ ] `prescribed_duration_sec` outside 1..3600 renders no prescription and no
  *done* chip, so a row written on another machine with an absurd duration silently
  loses the only record it could carry. The guard is right; the consequence is not
  *(audit)*
- [ ] Every boolean on `ExerciseSet` is non-nullable with no `coerceInputValues`,
  and `SessionFiles.parse` turns a decode failure into "whole file unreadable" — so
  a `"done": null` from another writer costs the entire day. Carry is the only
  writer of `done` and writes a real boolean, so this is latent, not live *(audit)*
- [ ] The bars on *Heaviest by what it trains* encode every weight in that family
  with no scale and no time direction; the bar count varies by how many sets the
  family has, which reads as meaning something it doesn't. Propose cutting them
  and keeping the number *(you asked)*
- [x] *Every session* answers no question a person would ask. Either each row
  opens that session (above) or the band goes — each row now opens that session
  *(you asked)*

### The lobby glance
- [ ] **The Before tab lands on a stale sheet, and the warning is wrong.**
  It always shows the newest session file and warns "your folder may not have
  synced" — but at one or two classes a week there simply is no class most
  days, so the sync is fine and the warning is a false alarm in the primary
  position every time the app opens. Say "no sheet for today" and make a past
  day's sheet secondary *(you asked)*
- [ ] **No way to say "did it, nothing notable" on a timed row.** A 45-second
  plank has no number to give and no chip that means plain done, so a
  completed hold cannot reach history at all. The runner now counts a feel
  marker or a note as an answer; this is the half that needs an affordance
  here *(audit)*
- [ ] **Notify when a new session file lands** — the glance still depends on
  remembering to open the app. Forces a decision: the app currently declares no
  Android permissions at all *(design:
  [new-session-notification](designs/new-session-notification.md))*
- [ ] **Read the class from the phone's own calendar** — frees the coach from its
  own calendar config. Bigger than it looks: needs a protocol change agreed on the
  runner side first *(design:
  [read-class-from-calendar](designs/read-class-from-calendar.md))*

### Identity
- [ ] **A logo** — chalk on slate, and it has to work as a one-colour launcher
  icon at 48dp *(you asked, design: [logo](designs/logo.md))*

### Owed on a device
Things a JVM cannot answer honestly, to check on a real phone.
- [ ] A persisted tree permission surviving `adb install -r`, which can revoke it
  and look exactly like a first run. Partly done 2026-10-12 **on an emulator**: the
  grant was taken through the real picker and held across a force-stop and
  relaunch. **A phone and a reinstall are still owed** *(device)*
- [ ] Bottom-edge insets against the navigation bar with a long session *(device)*
- [ ] The round trip through Syncthing, end to end *(device)*

## Feature proposals

Ideas worth doing, not yet planned. Each gets a sketch before it moves to Next.

### Quick-add from the lobby
Nothing exists for "the sheet is wrong and I know it now". Adding a row currently
means the After tab, which is a form built for afterwards. Unsketched.

## Done

- [x] **The flaky ViewModel test** — `CarryViewModelTest` left `viewModelScope`
  jobs running past the test that launched them, so one could dispatch to
  `Dispatchers.Main` while the next test set it. Every ViewModel a test builds is
  now registered, cancelled and waited for in teardown (#72) *(design:
  [flaky-viewmodel-test](designs/flaky-viewmodel-test.md))*
- [x] The four review items that sat in Next: index-keyed text boxes, a row written
  twice, a carried key resurrected from a stale draft, and the forty-first added
  row vanishing
- [x] ~~Three rows all say "shoulder press"~~ — closed 2026-10-12 **as not a
  problem**. The owner reframed it: this screen is for which dumbbell to pick up.
  Across the public sheet corpus a repeated movement uses the same implement 78%
  of the time and agrees on whether a weight is needed at all 84% of the time, so
  a distinguishing label would say nothing four times in five. The variant-label
  scheme is dropped; what was left was cosmetic and now collapses to
  `shoulder press ×2` *(device)*
- [x] The prescription on every row — `10 total x`, `45 sec of` — which the Log
  screen never showed, so a 30-second hold and a 90-second one looked identical
  (#60)
- [x] The intel post, with typed weights flagged in the preview before sending (#60)

## Notes

**A day with no history-backed weights is two tail lines and a lot of empty
space.** Correct by the design — there is genuinely nothing to carry — but seeing
it with real data is the first confirmation that on such a day the app's whole
value is the log and the "add something the sheet missed" row *(device)*.

## From the security pass


**Yours to do, not mine:**

- **Ask GitHub Support to garbage-collect the old commits.** The aggregate
  facts about your training are scrubbed from `DESIGN.md` and `DEVLOG.md`
  going forward, and `CLAUDE.md` now has a rule so it doesn't recur — but
  they remain in this repository's history. A force-push would not fix
  that on its own: the old commits stay reachable by direct SHA URL until
  GitHub collects them, which only Support can trigger. It would also
  invalidate the four open Dependabot PRs and the `carry-debug` release
  tag, so it is not worth doing without the Support request behind it.

**For you to decide, not for me:**

- **The other two projects' workflows are unpinned** — 11 zizmor findings
  each, the same as Carry had. Fixing them is a repo-wide pass on someone
  else's projects.
- **The debug keystore is committed**, which is the documented convention
  here and genuinely low risk: `release` has no signing config, so it
  cannot ship a release. But the debug APK *is* the real install, and
  anyone can build an APK with that key, so a sideloaded "update" would be
  accepted as the same package. If that install is to be trusted, it wants
  its own untracked key.
- **`docs/DESIGN.md` publishes aggregate facts about your real training**
  — classes per week, that three `fail` markers exist, that one `knees`
  entry exists — in a public repo. No weights, dates or identity, so it
  stays inside the project's privacy rule, but it should be a choice
  rather than a surprise.

## Five screens that cannot be screenshotted

`captureToImage` comes back as one flat colour for `03-empty`,
`04-first-run`, `06-no-session`, `07-loading` and `14-too-wide` — all
simple centred screens — while `05-lost-grant`, rendered by the same
composable in the same class, captures fine. `forkEvery(1)` fixed seven of
twelve; advancing a frame before capturing changed nothing; the root cause
is unfound.

Those tests keep their assertions, which were always the real test, and no
longer claim a picture. `isFlat()` now fails any capture that comes back
blank, so the suite can never again upload nothing as evidence — which it
did for seven of twelve screenshots, and which fooled me twice, once into
believing the bundled fonts had broken rendering entirely.

## Yours, not mine

The items in "From the security pass" above, and one decision the coach is
waiting on: **whether a bump may count across a movement pattern.** Pattern is
the only grouping where anything in the history reaches three, but `squat` holds
lateral shift and `lunge` holds step-up, so a bump earned on one could name a
weight for the other. The middle option on the table is to let a pattern count
toward the streak while the bump still fires only on a movement already done.

## The endpoint

Once Carry shows the cheat sheet as well as taking the log, the Python brain
has almost nothing left tying it to the assistant it grew up in. Today it
reaches into that repo in four places; three of them die with this app:

| Dependency | Freed by |
|---|---|
| `bin/signal-send` | Carry showing the sheet |
| `groups.yaml` | the same |
| `calendars.yaml` | Carry reading the phone's own calendar |
| `scripts/errorlog.py` | nothing — one small file, vendor or replace it |

At that point the brain moves out to its own folder or repo and the pair
becomes a standalone thing: an Android client and a Python coach that talk
through one JSON file and share no server, no account and no assistant.
That is also the point where it could be worth handing to someone else.

Worth deciding then, not now: the brain carries `wod_cache/` (277 scraped
Reddit comments) and would be publishing other people's writing if it landed
in a public repo as-is.

## Not doing

- Logging during a workout. The phone isn't in the room.
- Anything that needs an account or a server.
