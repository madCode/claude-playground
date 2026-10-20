# Backlog

Where this is: [DEVLOG.md](DEVLOG.md) opens with a status block and the debug
build link. How it works now: [DESIGN.md](DESIGN.md). What is specified but not
built: [designs/](designs/). This file is what is next.

The running plan. Each cycle picks what matters most right now — device feedback
first, then what the audit turns up — builds it with tests, and ticks it off.
Feature proposals are ideas, not commitments.

## Next

**Read in order.** *Your call* first — those are decisions, not work. Then
the paths to a wrong number, then what would say more, then health, then
what only a phone can answer. A cycle taking "the top unfinished item"
should start at the first line of *Wrong numbers*.


### Your call — nothing moves until you decide

None open. Each is a question about what you want the app to claim,
not a thing anyone is blocked from building.
- [x] **Should an old count leave the "part way to a bump" band?** The
  band now reaches back across every sheet, and a row from an older sheet
  shows its date. The runner's streak has no time limit, so the band has
  none either: a count from two months ago still shows, dated. A cutoff
  (say 28 days, the window the runner uses for its related lines) would
  hide counts the owner may have moved on from without logging. Leaving
  it out shows everything and lets the date speak. **Answered
  2026-10-17: keep the counts, no cutoff, and revisit as data builds**
  *(you decided)*
- [x] **A "10 total" row shows a faint "5"**, the coach's per-side count,
  since the importer marks a set short below that. **Answered
  2026-10-17: clear enough, keep it** *(you decided)*
- [x] ~~**Aggregate facts about the owner's training are in this public
  repo.**~~ **Converted 2026-10-16.** The owner was asked and said they
  were "okay with leaving them if you absolutely need them to continue to
  make good decisions". Checked honestly: not needed. Every one justified
  a decision already made and shipped — why the bodyweight progression
  path exists, why the variant key is shaped as it is, why the Since tab
  opens without a chart — and the reasoning survives "a large share",
  "almost all", "most". None was an input to a future decision, so the
  condition was not met and `CLAUDE.md`'s own test settled it.

  Converted in `DESIGN.md`, `DEVLOG.md` (two places), `SinceScreen.kt`,
  `Session.kt`, `LogScreen.kt`, `GrowthTest.kt`, `LogScreenTest.kt`,
  `PastSessionScreenTest.kt` and `TodayScreenTest.kt`.

  **Deliberately NOT converted: the public-corpus figures.** "A sheet
  repeats a movement on 42% of days", "the repeats use the same implement
  78% of the time", "agree on whether a weight is needed 84% of the time"
  — these are measured across r/orangetheory's own public posts, not the
  owner's training. A stranger learns nothing about the owner from them,
  and converting them would lose real information for no privacy gain.
  The rule's test is specifically about the OWNER's training.

  One lesson for the next sweep: my first search used `%` and "a third of"
  and found six of ten. The ones it missed were phrased "26 distinct
  movements across 32 sets", "20 of 20 lifts", "19 of 20 distinct" — a
  figure does not need a percent sign. This item's own text had already
  warned about exactly that *(answered)*

- [x] ~~**The Python side was not covered by this pass.**~~ **Done
  2026-10-16:** a security audit of the companion script, and fixes, in
  its own repository. That includes the importer that reads what Carry
  saves: it now refuses a file whose numbers, types or date would corrupt
  the record, and names the row to fix. The findings and what is still
  open are kept there, not here, since this repository is public
  *(audit: security)*
- [x] **Should a save refuse whenever the file changed underneath?**
  **Answered 2026-10-17: refuse and offer the new file, the simple
  version.** Built: a changed, empty or unreadable file refuses the save,
  keeps the typing, lists it, and offers to drop it *(you decided)*

### Wrong numbers, or a road to one
- [x] ~~**A weight over 1000 saves on the phone, and the importer then
  refuses the whole day.**~~ **Done 2026-10-16:** the owner asked for the
  form to validate like a full feature. Each out-of-range box says so
  under its row, and Save is refused with the rows named (DESIGN.md,
  "Numbers the importer would refuse"). Was: Since 2026-10-16 the companion importer refuses
  a session file holding a weight or rep count outside 0 to 1000. That
  stops a typo from corrupting the record, but the number boxes take up to
  six characters, so a slip like `2500` saves on the phone and loses the
  day's import, and Carry never says so. Dropping the keystroke would turn
  `2500` into `250`, a wrong number. Blanking it while showing it breaks
  "the box and the stored value never disagree". The two ways that keep
  both rules: refuse the save, naming the row; or mark the box and say
  "not saved" next to it. A question about what Save may refuse
  *(found by the importer's security fix)*

The app's stated worst outcome is a wrong number in a training history.
These are the open paths to one, hardest first.
- [x] ~~**The streak's weight and the weight shown are different
  quantities.**~~ Fixed 2026-10-16. Confirmed end to end against the
  runner first: two sets held at 30 lb for eight reps produce
  `streak_done: 2` beside `suggested_weight: 25.0`, the Epley estimate
  for today's twelve — nothing was ever held at 25, and `previous_weight`
  is null on that path, so the real weight is not even in the file.

  **The sighted row never made the claim** — it shows a name, pips and a
  weight side by side. The spoken label did, because #80 joined them into
  one sentence: *"2 of 3 clean sets, 25 pounds"*. It now says *"next at
  25 pounds"*, which is true on every rule, since `suggested_weight` is
  always what to use today.

  Left open deliberately: `Session.kt` still documents the pair as "clean
  sets held at this weight", which is right on the same-reps rule and
  wrong on the converted one. Correcting it is a note about the runner's
  schema rather than a change to this app, and the runner owns it.
- [x] ~~Every boolean on `ExerciseSet` is non-nullable with no
  `coerceInputValues`, so a `"done": null` from another writer costs the
  entire day.~~ Fixed 2026-10-16. `coerceInputValues = true` falls back to
  the default, which for these fields is also the right answer: `false` is
  exactly what an unset flag means, and a null `sets` now reads the same
  as an absent one. `schema` is nullable now so coercion cannot hand the
  gate its own default back and vouch for another tool's file.
- [x] ~~**The same bug one level up: ANY malformed field on ANY row still
  costs the whole day.**~~ Fixed 2026-10-16. `SessionFiles.parse` decodes
  `sets` one row at a time, so a `"ref": null`, a missing `ref`, a
  `"line": null`, a `"weight": "45 lb"` or a `"done": "yes"` costs that
  row and not the day — and the head is decoded with `sets` taken out, so
  a bad row cannot take the date and the class tag with it either.

  **Dropping a row is only an improvement if it is said**, so both tabs
  say it: *"1 row of this sheet didn't read. It stays in the file
  untouched; Carry can't show it."* On the Before tab a missing line
  otherwise reads as the coach not prescribing it; on the After tab the
  consequence is a box that is not on screen, which reads as a row already
  filled in. One wording, in `SessionFiles.unreadableNote`, so the two
  cannot drift.

  The claim in that sentence was verified end to end before it was
  written: a save with three undecodable rows writes all of them back
  **byte for byte**, nulls, string weights and all. Six falsifications;
  the sixth — `carryState` not counting — passed every test until a case
  that goes through a real folder read was added.

- [x] ~~**A day that read short is left out of the history entirely**, and
  silently.~~ The exclusion stays — the #87 review established the
  alternative is worse, because these figures are CLAIMS and "4 lifts,
  heaviest 50" over a day whose heaviest row did not decode is a confident
  wrong number nothing downstream could tell. What is fixed 2026-10-16 is
  the silence: `Since.short` counts them and the tab says *"2 sessions
  aren't in these totals: their sheets didn't read cleanly."*, above the
  figures it qualifies and above the empty check — and the empty copy
  stops offering advice that cannot work. It counts a file that would not
  parse AT ALL as well as one that read short, and only the short ones
  that have evidence a session happened. The day detail needs nothing: a
  short day never reaches `days`, so there is no detail view to mark.
- [x] ~~**A newest file that will not parse at all silently hands the bump
  band to yesterday.**~~ Fixed 2026-10-16. The band is WITHHELD rather
  than inherited, and says so where it would have been: *"The newest
  sheet didn't read. Rather than show an older day's, this is blank."*,
  or *"The newest sheet needs a newer Carry..."* when the file is intact
  and the app is what is behind — the heading with one line under it,
  which the band shows in no other case, because "nothing is close" and
  "we cannot tell" are different answers and a band that simply
  disappears gives the first. It says *the newest sheet*, never "today's":
  the newest file is the newest by NAME, which on a rest day is last
  week's and can be dated ahead.

  `Growth.of` could not have decided this alone: it sees text and nothing
  else, so it cannot tell a newest sheet that failed to parse from a
  folder that ends earlier. Only the FILENAMES answer that, and `growthOf`
  was dropping them — and the question is about the newest DATE, since
  two classes on one day are two files and either reading is enough.
  Pinned from both sides: an older broken file must not cost the band.
- [x] ~~**Showing a short day's rows on the Since tab, rather than omitting
  the day.**~~ **Dropped 2026-10-16, on the gate the item set itself:**
  "worth doing only if this ever happens to a real file". Measured across
  every session file in the owner's synced folder — a short day has never
  occurred, nor has any of the eight other shapes checked in the same pass
  (no `schema` key, a duration outside 1..3600, an empty `sets`, two
  classes at the newest date, a filename disagreeing with the date inside,
  an added row with no id, a non-integer `ref`, duplicate `ref`s).

  Kept in mind rather than forgotten: the work it described is real and
  the reasoning below still holds. If a short day ever shows up, this is
  the design *(audit)*
- [x] ~~**A `sets` holding both `"ref": 3` and `"ref": "3"` shows two rows
  that share a ref, and an edit typed against the second is written onto
  the first.** `refOf` excludes the string one from duplicate detection,
  so the save does not refuse — it silently misplaces. Verified by the
  #87 review (20 lb typed against one movement written into another,
  outcome `Patched`). Pre-existing: the old whole-document decode accepted
  that file too. Note that two rows sharing a plain integer ref IS now
  refused by both halves, so this is the one remaining hole in that
  guard, and it is the quiet one *(audit)*

  **Fixed 2026-10-16.** Re-measured before touching it, because a third of
  review findings do not hold — this one did. `refOf` now reads `ref` the
  way the DECODER reads it, so the duplicate guard sees both rows and
  refuses the file. The probe before the fix: 20 lb typed against `goblet
  squat` written onto `low row`, outcome `Patched`, `unreadableRows` 0.
  It also fixed the other side — a lone quoted ref made its row
  unplaceable, refusing a save for a row visibly on screen.
- [x] ~~**`CarryViewModelTest`'s save wait fails about one run in six.**~~
  **#82 fixed it. The "one in six" was measured BEFORE #82's own fix and
  never re-taken**, so this item has been describing a state that stopped
  existing the moment it was written. Measured properly 2026-10-16,
  before and after, in separate directories:

  | | full-suite runs | failures |
  |---|---|---|
  | **before** — `0c3f96b`, a worktree of the commit #82 branched from | 14 | **1** |
  | **after** — this branch, same machine | 28 | 0 |
  | **after** — CI, every Carry run since #82 merged | 27 | 0 |

  The pre-fix failure carried the OLD assertion text ("the save never
  finished", with no state named), which is the check that the worktree
  really was pre-#82 — that suffix was added by #82 itself.

  Pooling the pre-fix runs with #82's own (1 in 6) gives 2 in 20. Against
  that rate, 55 clean runs after is a 0.3% outcome; against the 1 in 14
  seen here, 1.7%. So the fix is the cause rather than a coincidence of
  timing, with the uncertainty stated rather than rounded away.

  **What fixed it:** `tearDown` keeps a registry of every ViewModel a test
  builds so their scopes are cancelled, and ONE test built its model with
  the constructor directly — the test whose own comment named the
  intermittent failure had opted out of the guard against it. Its scope
  outlived its test and ran into the next one.

  **Do not add `forkEvery(1)`**, which this item used to propose. It would
  slow all 233 tests to fix nothing.

### Says more, or says it better
- [x] ~~**Notify when a new session file lands**~~ Built 2026-10-16, to the
  design as written — and the permission question it forced had a bigger
  answer than the design or I expected. The app went from zero permissions
  to **five**: `POST_NOTIFICATIONS` is ours, and `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE`
  arrive with `androidx.work`. They appear only in the merged manifest, so
  I had written "one permission" in DESIGN.md and it was false until
  `aapt2 dump permissions` on the built APK said otherwise. `DESIGN.md`
  carries the table *(design:
  [new-session-notification](designs/new-session-notification.md))*
- [ ] **Can two of WorkManager's four permissions be stripped?**
  `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` are for features this app
  does not use — no network constraint, no expedited work — so
  `tools:node="remove"` in the manifest should drop them, leaving a set
  that is entirely explicable. NOT done blind: removing a permission a
  library expects fails at runtime, in a background worker, silently, which
  is the worst failure shape this app has. Needs an install on a device and
  a confirmed check actually running after a reboot before it is believed.
  `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` must stay — the work would stop
  surviving reboots without them *(found by checking the built APK rather
  than the source manifest)*
- [ ] **Read the class from the phone's own calendar** — BLOCKED, and its
  stated benefit is void. It justified itself as freeing the coach from its
  own calendar config; that config works, demonstrated 2026-10-16 when the
  laptop's trigger read the calendar and correctly reported no class. So
  there is nothing to free. This only becomes work if the brain moves to
  the phone, which is its own entry under *Feature proposals* with its own
  preconditions — do not pick this up before that *(design:
  [read-class-from-calendar](designs/read-class-from-calendar.md))*



- [ ] **Say what to type when one row stands for several rounds**
  *(you asked, 2026-10-18; needs gym-coach's half first)*. A board that
  prescribes `8 / 6 / 4` is three sets of one movement, and Carry gives it
  one row. Nothing on either screen says what that row means, so the
  convention lives only in the owner's head: they record *"only my max
  weight-rep combo"*.

  That is the right convention — one true (weight, reps) pair is exactly
  what the coach's arithmetic consumes, and it is cheap to log after a
  class. The problem is that it is unstated, so nothing can verify a row
  followed it, and a tired day could quietly change what the record means.

  **The hint, on the After tab where the typing happens** — not on the
  Before tab, which is read standing in a lobby: a row of several rounds
  says so, and says what to log. Something close to
  "3 rounds · log your heaviest set", with the weight box taking the reps
  achieved at that weight.

  **Read it from a field, never from the text.** gym-coach will emit
  `rounds: [8, 6, 4]`, null when there is one round. Carry must not regex
  `prescribed_text` for a ladder: rows arrive with their structure already
  attached, which is the rule this app rests on, and the corpus writes
  ladders two ways (54 slash forms, 17 comma). A parser here would be a
  second place to keep teaching board syntax, on the device where it is
  hardest to fix.

  Sizing, measured on the 277-post corpus: a three-rung ladder is in
  **10% of posts**, so this is not a rare shape.

  Pairs with gym-coach naming which rung its suggested weight is for. The
  two together make the screens coherent — the sheet advises the set you
  pick up first, the input records the heaviest one you finished. Either
  alone leaves the other half unexplained *(you asked)*

### Health and tidying
- [ ] **Bring updates up to newspaperss's.** Carry's in-app update
  (`DebugUpdates`, `UpdateInstaller`) is as safe as newspaperss's
  (`SelfUpdate`) but needs more from the owner. Four gaps:
  - **It waits for a tap.** newspaperss downloads a new test build on
    Wi-Fi and installs it while the app is closed and nothing is running.
  - **It only checks on a return to the app.** newspaperss also checks in
    the background, every six hours.
  - [x] ~~**It leaves the file checks to Android.**~~ Done in cycle 71:
    Carry checks the package, the signing key and a higher version code
    itself, and gives up on a build after three failed installs.
  - [x] ~~**The installer and `REQUEST_INSTALL_PACKAGES` ship in every
    build.**~~ Done in cycle 71: both, and `INTERNET`, are debug-only, and
    `check` fails if a release's merged manifest asks for them.
  Keep Carry's own rule: never install over unsaved typing on the After
  tab, so a background install waits for no draft and no save in progress.
  A device check is owed with it, since the install itself happens in
  Android's hands *(you approved, 2026-10-19)*
- [ ] **Branch coverage is 67% while the Kover floor is 85% on
  instructions.** The gate is real but it is counting the wrong thing for
  a codebase this full of fail-closed guards — a third of branches have
  never been taken. Not a demand for a second gate (a test written only to
  lift a number is worse than the number), but worth one pass over the
  classes with the widest gap: `SessionFolder` 78%, `CarryAppKt` 77%
  *(audit: CI health)*
- [ ] `MainActivity` is at **0%** and `LogBinding` at 0%. The first is the
  SAF plumbing the emulator is honest about and unit tests are not, so it
  is the one place a device check earns its keep; the second is a plain
  data holder *(audit: CI health)*
### Owed on a device

Only a real phone can answer these.
- [ ] **The After page v4 on a phone.** Tap an empty reps box: it should
  fill with the faint count, selected, and typing should replace it. With
  TalkBack, the box should say "empty, prescribed 10" and offer a
  "Fill in 10" action, then say the number once filled.
  Check a timed row's *done* tag, and the page at the largest font size
  *(due with #127 v4)*
- [ ] **TalkBack reads "You typed: …" when it appears after Save.** The
  test proves the line is a polite live region, not that TalkBack speaks
  it. With TalkBack on: add a row, type a name, save, and listen *(due
  with the typed-name change)*
- [ ] A persisted tree permission surviving `adb install -r`, which can revoke it
  and look exactly like a first run. Partly done 2026-10-12 **on an emulator**: the
  grant was taken through the real picker and held across a force-stop and
  relaunch. **A phone and a reinstall are still owed** *(device)*
- [ ] **Does tapping a notification preserve typed-but-unsaved numbers?**
  The tap itself is confirmed — it opens the app on the Before tab. What is
  NOT confirmed is the property the `SINGLE_TOP` fix exists for: that the
  activity is brought forward rather than finished and rebuilt, so
  `CarryViewModel`'s edits, added rows and in-flight save survive.

  The 2026-10-16 test could not exercise it. The test sheet was blank and
  nothing had been typed, so there was no state to lose and a broken
  version would have looked identical. Worth saying plainly because "the
  tap works" reads like the fix is proven, and it is not.

  The sequence: type a weight on the After tab, do NOT save, background the
  app, then tap a notification when one arrives. Numbers still there means
  the fix holds; empty boxes mean the activity was rebuilt, which for this
  app is the cardinal failure *(device)*
- [ ] Bottom-edge insets against the navigation bar with a long session *(device)*
- [x] ~~The round trip through Syncthing, end to end~~ **Confirmed on the
  owner's own phone, 2026-10-16.** A blank, labelled session file placed in
  the synced folder on the laptop reached the phone, parsed, appeared on the
  Before tab, and the notification arrived. Which also confirms the two
  fixes it was dated to exercise: the file was dated TOMORROW when placed,
  so the future-date rule had to leave it unrecorded overnight rather than
  swallowing it, and the 06:00 rule had to hold it through the small hours
  instead of buzzing at midnight. Both held.

  Tapping it opens the app on the Before tab: confirmed on the device the
  same morning.


## Feature proposals

Ideas worth doing, not yet planned. Each gets a sketch before it moves to Next.

### Maybe allow editing a past session *(you asked)*

The owner's words, 2026-10-16: *"I don't see why editing a past session
wouldn't be allowed"*, and then *"maybe allow editing a past session"* —
filed as a proposal rather than approved work, because the "maybe" is
theirs and the risk is real.

**Today:** `PastSession.kt` states "The only session you can change is
today's", and a `backfilled` day is read-only on top of that. The Past
screen is a reader.

**Why it is not a small change.** This is the save path, which is where a
wrong number in the owner's training history would come from, and that is
the stated worst outcome in the project. It also stops being Carry's
decision alone: gym-coach now imports filled session files into history
the day after a class, so an edit to a past day has to reach the record
too, and the two sides have to agree on what an edit MEANS.

**Constraints the gym-coach side has already committed to**, so Carry must
be built to them rather than against them:

- **`ref` must stay stable for a sheet row.** A ref reused for a different
  exercise is refused by the importer as "now a different exercise; it may
  have been regenerated". Carry never generates a ref today — it reads
  them off the runner's rows and writes them back — so this holds as long
  as past-session editing does not start assigning any.
- **Clearing a row means writing it back BLANK, not dropping it from
  `sets`.** A row that simply disappears from the file is reported and
  left alone, never removed from history. So the editor must blank rather
  than delete.
- **Signal-era rows whose only content was an "ok" or a breakdown word
  read as blank in Carry**, and can never be cleared from there. Those
  days are not safely editable until they have been adopted.

**Open questions for whoever builds it:**

- Which days are editable? "Any past day" is the owner's instinct;
  "adopted days only" is the safe subset, since those have row ids and
  came from Carry's own schema.
- Does the Before tab's one-glance purpose survive a Past screen that is
  also a form? The two screens exist because afterwards is a different job
  from beforehand.
- What does the owner see when an edit cannot reach history — a Signal-era
  blank-looking row, say? Silence is the failure mode this project keeps
  finding.

Not sketched further. It wants its own cycle and its own fresh-eyes
review, not a late-evening addition to an unrelated branch.

### The brain moves to the phone, eventually

The owner's direction, 2026-10-16: *"once we feel confident that the brain
is working we do eventually want to move it to the phone."* Not planned,
and nothing here should be built toward it yet — but it changes what
counts as a good decision in the meantime, so it is written down.

Tonight's work is a step toward it rather than away: the session file is
now the delivery channel and the app notices it, so Signal is leaving the
path. The remaining distance is the brain itself — the matcher
(`gym_lib` + `variants.py` + 104 movements in `exercises.yaml`), a WOD
fetch, and the history the suggestions read.

**The precondition, and it is not negotiable.** Carry's one rule exists so
there is exactly ONE derivation of exercise identity; the system this
replaced lost about a third of what was reported by re-deriving identity
from free text in a second place. A brain that MOVES keeps one derivation
and is fine. A Kotlin reimplementation living alongside the Python one
creates two, and the two will drift silently — the owner's history would
be wrong before anyone noticed.

So before any port: a shared corpus of (line, expected parse) fixtures
that BOTH implementations must pass, checked in where both can read it.
Without that there is no way to tell a faithful port from one that has
quietly lost a third of the lines, because the only witness is the
owner's own training record months later.

**Waits for this move**, because the session file goes with it:

- A resume can put another session on screen over pending typing. The
  refresh on every resume shows the newest session; the draft for the old
  one is then hidden, and the next keystroke replaces it. Finishing an
  older session (cycle 68) has the same exposure: a sync that briefly
  drops the old file from the listing puts the newest back under the
  typing, and the pin goes with it *(the simple save refusal, #136
  closed; review of cycle 68)*

Two smaller things it would also need: the `INTERNET` permission and
stored Reddit credentials, which is a much bigger change to this app's
stated properties than the one notification permission; and the history
on the phone, which currently lives beside the coach and is deliberately
read-only to Claude.

### Quick-add from the lobby
Nothing exists for "the sheet is wrong and I know it now". Adding a row currently
means the After tab, which is a form built for afterwards. Unsketched.

## Done

- [x] **Carry and gym-coach agree about an old *hard*-only row.** Carry
  moved to gym-coach's reading: its importer turns a row whose only
  record is `hard` into a set in history (its #50 kept that on purpose),
  so the Since tab now counts that day too, and a past session shows the
  row with a *hard* mark rather than as done with nothing to say why.
  Checked with gym-coach's own `to_history` on such a file: one entry
  for the hard row, none for the untouched one *(review of #136)*

- [x] **Finish an older session.** Built 2026-10-18: a *Finish this
  session* button on a past day in Since puts that sheet on the After tab,
  pinned by `sheet_id`, with the same save checks. A strip on every tab
  says an older sheet is on screen; switching is refused over unsaved
  typing. DESIGN.md's "record, not a form" now says how *(you approved)*

- [x] **A set marked *hurt* leaves the "part way to a bump" band.** The
  runner ends a streak at a hurt set with a weight and says hold, so the
  row goes, like a lighter one. A hurt row with no weight stays: its
  streak rules only read weighted sets, checked through gym-coach's own
  importer and `suggest`. Left as is: one movement listed twice on a sheet,
  the first clean and the second hurt, still shows the clean copy's count
  where the runner breaks it. Which copy the runner reads depends on board
  order, and a movement twice on one sheet with only one of them hurt is
  too rare to re-derive that here *(found dropping the hard check)*
- [x] **The "part way to a bump" band keeps a row an old file marked
  *hard*.** gym-coach stopped reading `hard` (its #50), so a completed set
  counts however it felt and the row's count is as good as any other's
  *(found removing the hard chip)*
- [x] **The minimum After page, mockup v4
  ([#127](https://github.com/madCode/claude-playground/issues/127)).**
  Each row says "I did it" with its own fields: a weight, reps (one tap
  fills the faint count), or *done* in a timed row's time slot. *skip* and
  *hard* are gone, *hurt* sits on the name line, labels sit above the
  boxes, and the page's type is a step up. This also answers **which marks
  say "I did this movement"**: a bodyweight-only day now gets a post once
  its reps are filled *(you approved v4)*
- [x] **A file with no `schema` is no longer accepted as ours.** You
  approved requiring `"schema": "gym-session/1"` after the laptop's check
  of the synced folder found every session file carrying exactly that. A
  missing or unknown label is refused with the file named and the label
  quoted, not as a sync glitch. A bare `"schema": 2` now reads as an
  unknown label rather than as damage *(you approved)*
- [x] **The "part way to a bump" band is a history.** It read only the
  newest sheet, so a sheet with no streak rows (or no rows) blanked it.
  It now covers every movement across the sheets, each from the newest
  sheet that lists it, and a row from an older sheet shows that sheet's
  date. This also settles **two classes at the newest date with the
  streaks in the damaged one**: the band now says "A sheet didn't read,
  so nothing older than it is shown here" instead of going blank without
  a word, and still shows the readable class's rows *(you asked:
  "'Since' should be a history")*
- [x] **Two identical bullets could appear in one post, and both got
  marked.** A line that reads the same (case folded, NFC) is now posted
  once, and where a typed line meets the sheet's the sheet's copy is kept,
  unmarked. Different words, even near-identical ones, stay two bullets;
  the file keeps both rows *(you asked: "show it once")*
- [x] **The weight guard is gone; a typed name is shown back instead.**
  The digit test missed "deadlift twenty six" and "½ rack" and flagged
  "bicep curl 21s". After a save, the screen shows each typed name and
  asks whether the weight is in the weight box. The number boxes are
  labelled "lb" and "reps" *(you asked: "show the typed name back, no
  digit check")*
- [x] **The *hard* chip is gone.** The coach treats a completed set as
  completed however it felt, so *hard* told it nothing. Files that say
  `hard` still read and keep it; it isn't shown *(you decided,
  2026-10-16)*
- [x] **The bars on *Heaviest by what it trains* are gone; the number
  stays.** They encoded every weight in the family with no scale and no
  time direction, and their count varied with how many sets the family had,
  which read as meaning something it didn't *(you asked)*
- [x] **A `prescribed_duration_sec` outside 1..3600 lost the row's `done`
  chip,** the only record a hold can carry. Any positive duration now makes
  the row timed; only the "sec of" text keeps the range check *(audit)*
- [x] **`Growth.of` picked the newest session by the date inside the file,
  while its gate read the filename.** `growthOf` now passes in the newest
  file by name that parses, so the band and the gate pick the same day
  *(audit)*
- [x] **A file whose `read` came back null was invisible to `Since.short`.**
  `growthOf` now passes it on as empty text, so it's counted with the files
  that didn't read, and the Since tab says a session is missing *(audit)*
- [x] **`ExerciseSet.answered` had no production callers**, and was the one
  definition of "answered" that left out `done`. Deleted, with the test that
  only exercised it. `Growth.Row.answered`, which counts the markers too, is
  the one the screens use *(audit)*
- [x] **Time the notification to the class, not to the clock.** gym-coach
  writes `starts_at` (gym-coach #15), and a sheet with one is announced
  from 90 minutes before its class. A 05:30 class is no longer announced
  after it started. 06:00 stays the fallback for files without it *(you
  asked)*
- [x] **How long before the class should the notice arrive — 90 minutes or
  60?** The owner chose 90, so `SessionWatcher.LEAD_MINUTES` stays as it is
  *(you asked)*
- [x] **Say when a newer debug build is out.** The owner chose Carry asking
  GitHub itself (#107) over routing the number through the folder, which
  brought the `INTERNET` permission. A CI debug build asks at most every
  three hours, and the Since tab says "Build N is out" at the top. Next,
  installing it from the app: on the owner's word in #107 *(you asked)*
- [x] **A logo, and a launcher icon of its own.** The H3 "pulse on a dot"
  concept the owner picked on 2026-10-16: an interval heartbeat in the five
  heart-rate zones with a chalk dot at the peak. Adaptive icon, a monochrome
  layer for themed icons, and the notification's status-bar icon, which was
  Android's info symbol. `IconTest` keeps the drawing inside the safe circle
  and the status-bar icon one colour *(you asked; [logo](designs/logo.md))*
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
- [x] ~~The band ignores what the owner logged in the same row.~~ Fixed
  2026-10-16. A row marked `hard`, or logged BELOW its suggested weight,
  leaves the band: those are the runner's own signals that the next bump
  is blocked, so "part way to a bump" is no longer true of the row and the
  band is capped, so it was taking a slot from one that is. Logged *at*
  the weight stays — that count is stale by one and understates, which is
  the safe direction — and logged *heavier* stays, because going up
  unprompted is the thing this app exists to notice. The streak itself is
  still never re-derived here.
- [x] **Open a past session from the Since list** — no screen answers "what did I
  lift that day" *(you asked, design:
  [open-a-past-session](designs/open-a-past-session.md))*
- [x] ~~After a successful write, `save()` re-seeds `_edits` from the text
  it wrote, and nothing can distinguish that line from its absence.~~
  **Answered, and it was worse than dead.** The #78 review found the
  re-seed was observable in exactly one case and observably wrong there:
  the *"only if nothing newer arrived"* guard wrapped one line and the
  re-seed ran regardless, so a sheet arriving mid-write left the old
  session's numbers on the new session's rows, with "Saved" still tappable
  above them. Fixed in #78; the whole tail is inside the guard
- [x] *Every session* answers no question a person would ask. Either each row
  opens that session (above) or the band goes — each row now opens that session
  *(you asked)*
- [x] ~~**The Before tab lands on a stale sheet, and the warning is wrong.**~~
  Shipped in #76: the tab leads with "No sheet for today" and shows the most recent below it, with no warning about the sync.
- [x] ~~**No way to say "did it, nothing notable" on a timed row.** A 45-second~~
  Shipped in #77: the *done* chip, offered only on a row the board asked for in seconds.

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
