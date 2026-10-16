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
- [ ] **Aggregate facts about the owner's training are in this public repo,
  predating tonight.** `CLAUDE.md`'s Privacy section rules them out — "the
  test is whether a stranger learns something about the owner's training
  from the sentence" — and names the fix: say "most" or "a small minority".
  Four that are already on `main`: `SinceScreen.kt` ("26 distinct movements
  across 32 sets and exactly one of them done twice"), `DESIGN.md`
  ("20 of 20 lifts"), `GrowthTest.kt` ("19 of 20 distinct"), and the
  "a third of this owner's logged sets" line repeated in `DESIGN.md`,
  `LogScreen.kt`, `LogScreenTest.kt`, `PastSessionScreenTest.kt` and
  — found by the #85 review, missed by every earlier pass over this
  list — `SessionPatch.kt`. Three more in `TodayScreenTest.kt`, found
  while working nearby: "78% of the real history was logged this way",
  "a sheet repeats a movement on 42% of days", "the repeats use the same
  implement 78% of the time". And one in `DEVLOG.md` (cycle 20): "37% of
  your logged sets carry no weight at all". A regex sweep of `docs/` and
  `app/src/` for this shape now returns only these, so the list is
  complete as of 2026-10-16 — what it cannot find is a figure phrased
  without a percent sign or an "of N", which is how the `SessionPatch.kt`
  one hid. #79
  removed the ones it had just added and left these, because they are
  load-bearing reasoning in several places and converting them is a pass of
  its own — **and it may be a deliberate exception you want kept**.
  Three more the #80 review found that the list above would have missed:
  "eleven rows of one lit pip" in this file, and the same figure in
  `DEVLOG.md` twice, once in a section heading *(audit)*

- [x] ~~**Should a movement done once appear in the progress band at all?**~~
  **ANSWERED 2026-10-16 by the owner: keep it — "Keep — show everything
  part way".** Which is what #79 and #80 had already built, so there is no
  work: the band keeps movements at one of three, capped at five rows with
  the rest counted, under the heading *part way to a bump*. The "why the
  coach is often quiet" band explains why they all sit at one.

  Recorded here because the item sat open for a day after being answered,
  which is its own small cost: a cycle reading "the top unfinished item"
  reads a settled question first *(you asked — answered)*
- [x] ~~**The intel post still demands a number**, so a wholly bodyweight or
  timed class offers no post at all.~~ The owner was asked and said yes, and
  the TIMED half shipped 2026-10-16 (#92): `done` qualifies a movement. The
  bodyweight half does not, because an untimed row has no `done` chip at
  all — that remainder is the *Which marks say "I did this movement"?*
  decision above, which is theirs rather than work *(audit)*
- [x] ~~**A session logged entirely by hand is not listed at all.**~~ Shipped
  2026-10-16 (#91), with two defects of its own found by the review and
  fixed in #92's branch: the quiet band printed `movements` over `sets`
  once those stopped being the same population, and a weighted row with no
  name could displace the named lift that describes a day. The reasoning
  below is kept because it is the record of the owner's decision.

  `Growth.of` decided what counted as a session from the SHEET's rows, so a
  borrowed day whose sheet rows are blank and whose real content is in
  `added` never reaches *Every session*.

  **DECIDED 2026-10-16 by the owner: yes, it should count.** Their words,
  and the two shapes they named: *"Yes if the 2G was incomplete/completely
  off or I left a class early after only doing the row session, it should
  still count."* So a day is a session when the owner recorded doing
  something, whichever box they typed it into — a short day is still a
  day.

  Changing it moves the session count, the date span and the quiet-band
  figures, so measure those before and after. Two things to fix with it,
  found by the #77 review: `Day.lifts`/`Day.best` and the `movements`
  count read `session.sets` only, so such a day would render "0 lifts"
  over a list with a weight in it; and `Row.answered` ignores the typed
  name, so a hand-logged row with a name and nothing else is written to
  the file and still counted as never having happened.

  Context from 2026-10-16: the owner has **never** used Carry's Add button
  — zero `added` rows across all seven session files — but they add
  exercises the sheet missed often, via Signal (both ybell sets on 09-20).
  So the behaviour is routine and only the ROUTE is hypothetical *(you
  asked)*

### From the security pass, 2026-10-16

The app has **no `INTERNET` permission** — confirmed in the merged
manifest, not just the source — so it cannot open a socket. The share
sheet is the only way anything leaves, which makes the preview above it
the whole of the egress control.

- [x] ~~The flagged lines in the post preview were marked by **colour
  alone.**~~ Fixed. "Check the lines in red" is no use to a reader with a
  colour vision deficiency, in glare, or on a greyscale display, and the
  block had no content description at all, so TalkBack read it as one
  undifferentiated string. The flagged lines now carry `⚠` in the text, the
  caption names that mark instead of the colour, and the caption — a short
  node, not the preview — is what reads out how many lines are marked and
  which. The preview deliberately keeps **no** content description: a
  review of the first attempt found that labelling it took away LINE and
  PAGE navigation (Compose offers those only to a text node whose
  description is empty), read every flagged line twice, and flattened the
  blank line under the heading into a bare comma. The mark is on the
  preview only:
  `post.text` goes to the share sheet untouched, and a test asserts the
  warning never reaches the subreddit.
- [ ] **The Python side was not covered by this pass.** It holds the
  credentials, the outbound writes and the allowlist that decides what may
  leave the data directory — three things this app has none of — and a
  later security cycle should start there rather than re-reading the app.
  Named specifically in that repo's own notes, which is where a map of its
  controls belongs; this one is public *(audit: security)*

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
- [ ] `prescribed_duration_sec` outside 1..3600 renders no prescription and no
  *done* chip, so a row written on another machine with an absurd duration silently
  loses the only record it could carry. The guard is right; the consequence is not
  *(audit)*
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
- [ ] The bars on *Heaviest by what it trains* encode every weight in that family
  with no scale and no time direction; the bar count varies by how many sets the
  family has, which reads as meaning something it doesn't. Propose cutting them
  and keeping the number *(you asked)*
- [x] ~~**Notify when a new session file lands**~~ Built 2026-10-16, to the
  design as written — and the permission question it forced had a bigger
  answer than the design or I expected. The app went from zero permissions
  to **five**: `POST_NOTIFICATIONS` is ours, and `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE`
  arrive with `androidx.work`. They appear only in the merged manifest, so
  I had written "one permission" in DESIGN.md and it was false until
  `aapt2 dump permissions` on the built APK said otherwise. `DESIGN.md`
  carries the table. Still no `INTERNET` *(design:
  [new-session-notification](designs/new-session-notification.md))*
- [ ] **Time the notification to the class, not to the clock.** The owner's
  idea, 2026-10-16, and it is better than what shipped — it also fixes a
  real bug in it. `EARLIEST_HOUR = 6` holds a sheet found overnight until
  06:00, which is right as a floor and wrong as the only rule: a 05:30
  class would be announced AFTER it started. Their classes run mid-morning
  today, so the shipped rule is safe by luck rather than by design.

  **The time is not in the session file.** Every key across the owner's
  eight real files: `added, backfilled, borrowed_from, class, date,
  recovered_from, schema, sets, sheet_id`. The brain has it —
  `gym_calendar.py` reads `DTSTART`, and `scheduled_surveys.jsonl` already
  stores derived times — but nothing carries it to the phone.

  So this is two-sided, and **gym-coach goes first**: Carry does not own
  the schema, and a field added here is one the importer silently drops.

  - gym-coach writes `starts_at`, ISO local, from the calendar event.
  - Carry announces when `now >= starts_at - 90min` rather than consulting
    the hour.
  - The 06:00 floor STAYS as the fallback for any file without
    `starts_at`: backfills, hand-written days, and everything the runner
    wrote before the field existed. Deleting the fallback would make those
    files silently un-announceable, which is the failure this whole
    feature exists to prevent.

  Granularity note: the check is hourly, so "90 minutes before" means
  "within the hour after that moment". Adequate for a sheet read in a
  lobby. Tighter would need a one-shot alarm scheduled from `starts_at`,
  which is more machinery than this earns *(you asked)*
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

- [ ] **A logo** — chalk on slate, and it has to work as a one-colour launcher
  icon at 48dp *(you asked, design: [logo](designs/logo.md))*


- [ ] **`Growth.of` picks the newest session by the date INSIDE the file,
  while the gate that decides whether to trust it reads the date from the
  FILENAME.** They agree on everything the runner writes, so this is
  latent: a file whose content date is blank or older than its name's
  would take the band while the gate called the folder fine. Found by the
  #89 review. The fix is to pick by one rule — most likely by passing the
  newest text in rather than letting `Growth.of` choose *(audit)*

- [ ] **A file whose `read` comes back null is invisible to
  `Since.short`.** `growthOf` drops it from `texts` before `Growth.of`
  can count it as unparsed, so a session the folder could not hand over
  at all is the one kind of loss the Since tab still does not mention.
  Pre-existing; found by the #89 review *(audit)*
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
- [ ] `ExerciseSet.answered` has no production callers and is the only one of the
  three definitions of "answered" across the two repos that omits `done`. Delete it
  or make it the shared one *(audit)*

### Owed on a device

Only a real phone can answer these.
- [ ] A persisted tree permission surviving `adb install -r`, which can revoke it
  and look exactly like a first run. Partly done 2026-10-12 **on an emulator**: the
  grant was taken through the real picker and held across a force-stop and
  relaunch. **A phone and a reinstall are still owed** *(device)*
- [ ] Bottom-edge insets against the navigation bar with a long session *(device)*
- [ ] The round trip through Syncthing, end to end *(device)*


## Feature proposals

Ideas worth doing, not yet planned. Each gets a sketch before it moves to Next.

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
