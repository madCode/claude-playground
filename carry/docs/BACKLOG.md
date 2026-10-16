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

Five decisions. Each is a question about what you want the app to claim,
not a thing anyone is blocked from building.
- [ ] **Which marks say "I did this movement"?** You answered the narrow
  version on 2026-10-16 — `done` qualifies a movement for the post — and
  that shipped. But `done` is only offered on a TIMED row, because a timed
  hold is the case where the number boxes are empty by definition. So a
  wholly BODYWEIGHT class still has no post: air squats, push-ups and jump
  lunges have no duration, so they get no `done` chip, and nothing else on
  the row says the movement happened.

  The marks that would fix it are `easy`, `hard` and `easier`. Each does
  imply a set happened — you cannot call a set easy without doing it — so
  the argument for counting them is decent. The argument against is that
  they are judgements about a set rather than a record of one, and `hurt`
  is the proof the distinction matters: tapping it while deciding is not
  evidence you did the movement, which is why it stays out.

  Two ways to go: count `easy`/`hard`/`easier` as evidence too, or draw the
  `done` chip on every row rather than only timed ones. The second is the
  smaller claim and probably the better one — it adds a way to say "I did
  it" instead of reinterpreting marks that mean something else — but it
  puts a chip on rows where the number boxes are the normal answer, which
  is the reason it was timed-only to begin with *(you asked for the narrow
  version; this is what it left out)*
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

- [ ] **The weight guard only sees decimal digits, and only on a typed
  name.** `Char.isDigit` is Unicode `Nd`, so a load written as a word or a
  symbol publishes silently — verified unflagged: `ybell deadlift heavy`,
  `deadlift twenty six`, `deadlift ½ rack`. And the other way: a
  legitimate name with a digit in it IS flagged when hand-typed —
  `bicep curl 21s`, `90/90 hip stretch`, `3 way shoulder` — which is the
  false alarm that costs the guard its meaning. Both directions are the
  same question: is a digit test the right instrument at all, or should
  this be a "you typed this name, read it" prompt with no claim about
  weights? Needs deciding before patching *(audit: security)*
- [ ] **Two identical bullets can appear in one post, and both get
  marked.** `IntelPost` dedupes by (prescription, name), which is right —
  it is what keeps a hand-typed row from eating the sheet's own — but a
  name typed WITH its scheme (`10 total x squat`, no prescription) renders
  the same line as the sheet's (`10 total x` + `squat`). The pair survives
  the dedupe, the post carries the bullet twice, and the preview marks
  both. `IntelPostTest` already constructs this state on purpose, so the
  behaviour is pinned; what is undecided is whether the post should carry
  it *(audit: security)*

### Wrong numbers, or a road to one
- [ ] **A weight over 1000 saves on the phone, and the importer then
  refuses the whole day.** Since 2026-10-16 the companion importer refuses
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
- [ ] **A sheet with no rows blanks the "part way to a bump" band, even
  when older sheets have something to say.** `Growth.of` picks `newest` from
  every parsed session rather than from the ones with rows, so an empty
  newest sheet — a `"sets": []`, or a day the runner wrote before matching
  anything — takes the band to nothing. Pre-existing, found by the #86
  review. The band is "where you stand today", so an empty sheet arguably
  SHOULD empty it; the question is whether "the newest sheet" or "the newest
  sheet that said anything" is the right reading *(audit)*
- [ ] **A file with no `schema` key at all is accepted as ours.** `schema`
  defaults to `"gym-session/1"`, so another tool's JSON carrying `sheet_id`
  and `date` reads as a session. Pre-existing and narrow — the folder
  filter already requires the runner's filename — but making `schema`
  required would reject any older file that omitted it, which is a
  judgement about what counts as our file rather than a patch. Found
  alongside: `"schema": 2` unquoted says "wait for the sync" where "update
  the app" is the better guess, because `looksLikeNewerSchema`'s regex
  requires the quotes *(audit)*
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



- [ ] **Two classes at the newest date, the readable one with no bump
  rows and the damaged one holding the streaks: the band disappears with
  no "we cannot tell" marker.** The date-based gate says that day read
  fine, which it did — just not the half with the streaks in it. The top
  line still says a session is missing, so it is not silent, but it is a
  quieter signal than the name-based gate gave. The trade was taken
  deliberately in #89; worth revisiting if a two-class day ever breaks
  this way *(audit)*

### Health and tidying
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

Two smaller things it would also need: the `INTERNET` permission and
stored Reddit credentials, which is a much bigger change to this app's
stated properties than the one notification permission; and the history
on the phone, which currently lives beside the coach and is deliberately
read-only to Claude.

### Quick-add from the lobby
Nothing exists for "the sheet is wrong and I know it now". Adding a row currently
means the After tab, which is a form built for afterwards. Unsketched.

## Done

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
