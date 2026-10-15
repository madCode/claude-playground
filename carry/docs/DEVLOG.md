# Devlog

## Status

**Latest debug build:** [carry-debug.apk](https://github.com/madCode/claude-playground/releases/download/carry-debug/carry-debug.apk)
— rebuilt on every merge to main, installs beside anything else as
`com.app.carry.debug`. The build number is also on the Since tab, tap to copy.

- **Lately:** the save that never started, and the view model that opted
  out of teardown (#82); the progress band finished — honest heading,
  sorted by the question its rows answer (#80); first rendered with real numbers and
- **Lately:** the band stops believing a count the owner has already
  answered (#81); the progress band finished — honest heading, sorted by
  the question its rows answer (#80); first rendered with real numbers and
  found to be claiming movements were close to a bump (#79); the save
  path's own tests, which it had never had, and the wrong-number bug they
  exposed (#78); the *done* chip for timed holds, and the Since screen no
  longer dropping a session logged only with markers (#77); opening a past
  session from the Since list (#71); the flaky
  `CarryViewModelTest` fixed and proved with a denser probe (#72); the
  prescription on every row and the intel post (#60).
- **Watching:** a session logged entirely by hand is still not listed on the
  Since tab at all — it moves the screen's aggregates, so it is its own
  decision rather than a bug fix. A session logged only with markers *is*
  listed now (#77); that one was a wrong answer rather than a missing
  feature.
- **Waiting on you:** three checks only a real phone can answer (the folder
  grant surviving a reinstall, bottom insets, the Syncthing round trip), and
  the GitHub Support request in the backlog.

The plan is [BACKLOG.md](BACKLOG.md); how it works now is
[DESIGN.md](DESIGN.md); what is specified but unbuilt is in
[designs/](designs/). Newest first below.

## 2026-10-16 — cycle 36: one null key cost a whole day

**Top of *Wrong numbers*, and the probe found it was wider than the item
said.** `Session.json` had no `coerceInputValues`, so a `null` where the
decoder wanted a plain value threw, `SessionFiles.parse` turned that into
null, and `CarryState.Unreadable` told the owner "Can't read that
session" — for a file whose every number was intact, with nothing they
could do about it from the phone.

**Fixed:** coercion on, which falls back to the field's default. For
these fields that is also the right answer rather than a guess: `false`
is exactly what an unset flag means, and a null `sets` now reads the same
as an absent one, which it always should have.

**The hazard that comes with it, and the reason `schema` is now
nullable.** `parse` decides a file is ours by comparing `schema` to
`"gym-session/1"`. Left non-nullable with that as its default, coercion
would turn another tool's `"schema": null` INTO the expected value and
the gate would pass its own default back to itself. A test pins it.

**What the probe found that the backlog item had not.** One row per
case, everything else valid: a null `ref`, a missing `ref`, a null
`line`, a `"weight": "45 lb"` and a `"done": "yes"` all still cost the
entire day. Coercion rescues a null and nothing else. The real fix is
decoding `sets` row by row — which `splitAdded` already does for the
`added` array — but what the owner is TOLD when a row is dropped needs
deciding first, so it is in the backlog with the table rather than
patched in passing.

Two falsifications: coercion removed, and `schema` non-nullable again.
Whole suite green, so nothing else was quietly depending on the
strictness.

## 2026-10-16 — cycle 35: the one check before the only way out

**A security pass, and most of its value is what did NOT hold.** Four
hypotheses, four dead ends, each worth having checked:

- **Notes never reach the post.** Searched; `IntelPost` does not touch
  the field at all.
- **Bidi and control characters are already handled.** `post()` runs
  `readable()`, then cuts at the first control character *or* Unicode line
  or paragraph separator, collapses whitespace and caps the length — so a
  name cannot smuggle extra bullets or reorder the preview against what
  gets sent.
- **The app has no `INTERNET` permission.** Confirmed in the *merged*
  manifest rather than the source: what ships carries exactly one
  permission, AndroidX's own signature-level
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which is a protection and
  not a capability. The app cannot open a socket.
- **Backup and device transfer both exclude everything**, and the only
  exported component is the launcher activity.

So the share sheet is the only way anything leaves this device, and the
preview above it is the whole of the egress control.

**And that control was carried by colour alone.** The warning says "check
the lines in red"; the flagged lines differed from the rest only by
`BrickInk` versus `ChalkDim`, and the preview block had no content
description, so TalkBack read it as one undifferentiated string. Unusable
in glare, on a greyscale display, or to anyone with a colour vision
deficiency — for the one check standing between a hand-typed weight and a
public subreddit.

**Shipped:** `⚠` on the flagged lines in the text; the caption naming
that mark rather than the colour; and the count and the names of the
flagged lines spoken on the CAPTION, which is a short node of its own.
The mark is on the preview only — `post.text` goes to the share sheet
untouched, and a test asserts the warning never reaches the subreddit.
Eight falsifications across the two attempts.

**The first attempt put the spoken label on the preview, and a
fresh-eyes review found that made TalkBack worse, three ways.** Compose
offers a text node LINE and PAGE navigation only while its content
description is empty, so labelling the preview took away the one thing a
listener most needs there — stepping through the post a bullet at a time
— and replaced it with character-and-word stepping over a string with no
newlines in it at all. The label named the flagged lines and then read
the whole post, so every flagged line was spoken twice and a full post's
label ran past 3 kB with no way to skim. And joining the lines with
`", "` turned the blank line under the heading into a bare comma. On top
of that the caption it was supporting still said "check the lines in
red", so the sighted reader with no red was told to look for a colour and
the listener heard neither the colour nor the glyph.

The fix is a split, not a tweak: the preview keeps **no** description and
is read as what it is, and everything about which lines are flagged is
said on the caption — where it is a sentence, not a second copy of the
post. Three flagged lines are named and the rest counted ("and 2 more").

**A second look at that fix found the cutoff itself was a hole**, and it
is the same mistake one layer down: dropping the names past three handed
identification back to the `⚠` in the preview — which is the one
channel this whole change exists because a listener may not get. Named
plus counted keeps both the bound and the information. It also found
that `trimStart(' ', '-')` would read a typed `-26 lb curl` as `26 lb
curl`, renaming a movement while reading it out; the bullet comes off
with `removePrefix("- ")` now, which is exactly what the post put on.

## 2026-10-16 — cycle 34: two numbers that were never about the same thing

**Confirmed against the runner before touching anything.** Its
converted-streak rule counts clean sets at a *level* and pairs them with
an Epley estimate for today's reps: two sets held at 30 lb for eight
produce `streak_done: 2` beside `suggested_weight: 25.0`. Nothing was
ever held at 25, and `previous_weight` is null on that path, so the real
weight is not even in the file.

**The sighted row never made the claim.** It shows a name, pips and a
weight side by side and asserts no relation between them. The spoken
label did — #78's accessibility work joined them into one sentence,
*"2 of 3 clean sets, 25 pounds"*, which reads as two clean sets at 25.
That was mine, two cycles ago.

**Shipped:** *"2 of 3 clean sets, next at 25 pounds"*. True on every
rule, because `suggested_weight` is always what to use today — a hold, a
bump, or an estimate. Three falsifications.

**Left open deliberately:** `Session.kt` still documents the pair as
"clean sets held at this weight", right on the same-reps rule and wrong
on the converted one. That is a note about the runner's schema rather
than a change here, and the runner owns it.

## 2026-10-16 — cycle 33: the save that never started

**CI went red on a PR that could not have caused it.** `CarryViewModelTest
> a save re-reads the folder, once the button already says Saved` timed
out. The branch touched neither that file nor the save path — and the
**same test, the same assertion, had failed on `main` ninety minutes
earlier**. A pre-existing flake, surfacing on someone else's PR.

**First, a real defect found by reading.** `tearDown` keeps a registry of
every ViewModel a test builds so their scopes can be cancelled, and its
KDoc says why: a scope that outlives its test runs into the next one,
which is this class's documented intermittent failure (#72). That test
was **the one model in the file built with the constructor directly** —
the test whose own comment names the intermittent failure had opted out
of the guard against it. It goes through `viewModel()` now, with a guard
test that reads the source and fails if a direct construction comes back,
because an unregistered model is invisible at runtime by definition.

**Then the diagnosis, from an assertion message.** `awaitSaved` said only
"the save never finished". It now names the state it saw — and the next
failure, minutes later, said **"(saving was Idle)"**. Not `Saving`:
`save()` had returned at its first line because the screen was not
`Ready`, and the twenty seconds after that were a wait for a coroutine
that never started. The test asserts that precondition directly now, so
the fault reports itself instead of timing out.

**And the probe was wrong.** Twenty class-only runs passed, and eight
more under CPU load. It reproduces when the **whole suite** runs — which
is what CI does — about one run in six. `app/build.gradle.kts` sets
neither `forkEvery` nor `maxParallelForks`, so every class shares one
JVM, which makes a leak from another class the first suspect.

**Not claimed as fixed.** One real defect is gone and the failure now
diagnoses itself one layer further in. The rest is in BACKLOG.md with the
reproduction recipe.
## 2026-10-16 — cycle 32: the band believed a count the owner had already answered

**The top backlog item, and it half held.** *"The band shows the runner's
streak beside the runner's suggested weight and ignores what the owner
logged in the same row"* — `Growth.of` consulted only `skipped`, while
`weight` and `hard` sit in the same JSON object and Carry itself wrote
them. So a row suggested at 40 lb, logged at 35 and marked *hard*, kept
reading "2 of 3 clean sets, 40 pounds" under **part way to a bump**.

**Shipped.** A row leaves the band when the owner's own answer
contradicts it — marked *hard*, or logged **below** the weight the count
is about. Those are the runner's own signals that the next bump is
blocked. Two things deliberately stay:

- **Logged at that weight.** The count is then stale by one and
  *understates*, which is the safe direction, and re-deriving it is the
  one thing this file must not do.
- **Logged heavier.** Going up unprompted is the thing this app exists to
  notice, not a reason to stop counting.

Five falsifications, one per edge of that condition.

**The sibling item did NOT hold as written**, which is worth saying. The
#80 review gave the mechanism as "when the runner proposes a bump while
the streak is unfinished" — checked against the runner, the two branches
that propose a bump set no streak at all, so those rows never reach this
band. The real route is the converted streak, which pairs the count with
an *Epley estimate for today's reps* rather than a weight anything was
held at. Same conclusion, different cause; the backlog now says so.

## 2026-10-16 — cycle 31: the band, finished

Three things the last cycle's review left on the backlog, all in the one
band.

**The heading still over-claimed, in the other direction.** "How close each
one is" reads as every movement trained; the band is three filters deep —
the newest sheet only, streaks strictly in progress, five of those. It is
**"Part way to a bump"** now, which is what every row in it is and claims
nothing about scope. Second rename in two cycles, and the reason is worth
keeping: both earlier headings described the owner's *position* ("close",
"each one"), which is a claim about data the band does not have. This one
describes the rows.

**It sorted by the wrong question.** `done/needed` is how far along; the
row's own line is "another clean set moves it". Under a fraction, a
movement at 8 of 10 — two sets away — outranks one at 2 of 3, which is a
single set away, and with the band capped that is the row that gets cut.
Fewest sets away leads now, how-far-along breaks the tie, and the sheet's
own order breaks that. Not reachable while every `needed` is 3; the
runner's rule is free to differ per movement.

**And three shapes its fixture never produced:** a row whose family the
runner did not say, a `needed` other than 3, and one past `MAX_PIPS`. The
last has a real consequence — unclamped, a 40-set streak draws 40 pips and
squeezes the movement name to **about one character**. The pips have no
semantics to count, so the test asserts what they eat: the name keeps at
least half the row.

**And then the review counted the pips, which no test had.** Clamping
`needed` to ten without rescaling `done` does not draw a shorter version
of the truth — it draws a different number. *3 of 40* came out as three
lit and seven unlit, which reads as **seven sets away instead of
thirty-seven**; *12 of 40* came out as **ten lit and none unlit** — the
picture of a bump already earned — under a heading saying part way. The
new fixture introduced `needed = 40` and asserted the row's *name*, so
the diff certified as correct the very row that was wrong.

Past ten, pips cannot tell the truth and are not countable at a glance
anyway, so the row says **"12 of 40"** instead. Both screens: the Before
tab had the same shape, clamping `done` to the clamped `needed`, which
produced the same full row of lit pips.

Also from that review: `DESIGN.md` still named the retired heading in the
present tense five lines above the paragraph this changed; the comparator's
third rule — ties keep the sheet's own order — had no test, and the
ordering test that existed passed under the *old* sort too; and two
comments were wrong about their own code ("`sortedBy`" for `sortedWith`,
"three filters deep" for five).

## 2026-10-16 — cycle 30: eleven movements that were not close to anything

**Started as a backlog item that turned out to be wrong.** "Write the
streak into the session file so the dots can light — nothing writes them."
The runner has written `streak_done`/`streak_needed` all along; what was
null was the number itself, and the seven session files on disk all predate
the change that started producing it. Measured against the real history: a
sheet written **today** would carry dots on **most** of its lines, every one
at **1 of 3**, because each setup has been done once. (Figures with the
data, not in a public repository.)

**So the band was rendered with those numbers**, which is the first time
anyone had seen it with data. Eleven rows, each a single lit pip of three,
filling the entire screen under the heading **"CLOSE TO A BUMP"** — a claim
none of the rows supported, on the screen whose job is to say when the
owner can push themselves. The runner counts a streak from the first clean
set, and this owner starts nearly every movement fresh because the studio
changes the setup most classes, so one of three is the normal case rather
than the edge.

**Shipped.** The pips were worth keeping; the word *close* was not. The
band is now **"How close each one is"**, showing the five furthest along
and counting the rest ("and 6 more"). Capped, the three bands underneath
fit on the same screen again — including *why the coach is often quiet*,
which explains exactly why every pip is at one.

**Then the review took the same defect out of the fix.** "It shows the five
furthest along" was itself a claim the rows could not support: when every
row is on the same rung — the usual case, and the case that motivated all
this — the sort is a no-op and the five shown are simply the five the board
listed first. The wording says that now, and the cap counts the others
rather than ranking them. Three more from the same pass:

- **The band was inaudible.** The pips are bare `Box`es with no semantics,
  so TalkBack read "movement 1" then "21" — the streak, which is the entire
  answer to the heading above it, was not read at all, and the weight had
  no unit. The row has one spoken label now, like the day rows.
- **Nothing tested that `Growth.of` sorts.** The one ordering test asserted
  a single-element list and the screen test sorted its own fixture before
  handing it over, so deleting the sort left every test green.
- **A movement they skipped could take a slot** from one they worked on,
  now the band is capped. Excluded, the same way the lift counts in that
  file already exclude it.

**And figures about the owner's training went into a public repo.** Three
sentences of them, in this file, `BACKLOG.md` and a test. `CLAUDE.md` rules
them out by name — the test is whether a stranger learns something about
their training from the sentence — and says what to write instead. Rewritten
as "most"; the numbers stay with the data. Four older instances are on
`main` already and are in `BACKLOG.md` as your call.

**Not decided here:** whether a movement done once belongs in that band at
all. Cutting to genuinely-close rows empties it on today's data. In
BACKLOG.md as the owner's call, with both renderings.

## 2026-10-16 — cycle 29: the save path had never been driven

**Found.** Kover's line report for `CarryViewModel.kt`: everything below
`folder.save(...)` had never executed in any test. The three outcomes, the
"only if nothing newer arrived" guard, the draft clear, the re-seed — all
of it. `SaveTest` covers the *folder's* write thoroughly; what was missing
was the ViewModel's handling of what it answers. The two existing
`save()` calls in `CarryViewModelTest` both return before the write: one
hits a refusal, the other "already recorded".

That is the path whose stated worst outcome is a wrong number in a
training history.

**Why it was untestable.** `folder` is a concrete `SessionFolder` because
the Activity needs its SAF-only parts. There is now an `internal var
write` seam beside the existing `read` and `readSince` ones — the single
call the save makes into the folder, and the only production change here.

**Shipped.** Four tests: a clean write puts the written bytes on screen
and spends the draft; a refused write says the file is unchanged and
**keeps** the draft, because "try again" is only honest while the numbers
are still somewhere; a damaged file says so instead; and a newer sheet
arriving mid-write is not overwritten by what we just wrote. The save
coroutine went from 29% to 93%.

**And then the review found what they had just made visible.** The *"only
if nothing newer arrived"* guard wrapped exactly one line. The draft clear,
the `_edits` re-seed and the "Saved" badge all ran regardless — so when the
sync dropped the day's other class in mid-write (a refresh runs on every
resume), the form kept **Saturday's numbers against Tuesday's rows**:
`text` is the old session's bytes, and seeding the form from it puts a
weight the owner never typed on a movement they have not done. "Saved" is
still tappable, so one tap wrote it into the newer file. A wrong number in
a training history, through the one path with no sheet-identity check.

The whole tail is inside the guard now. When the sheet HAS changed,
`reconcile` has already seeded the form from the file that arrived and
nothing here second-guesses it.

Two more branches that were still at zero got tests with it: a row with a
weight and no name is **refused** and the typing kept (the case CLAUDE.md
names, which returns before the folder is ever touched), and two taps in
one frame write once.

**One test had to be rewritten twice.** It first asserted the badge said
"Saved" after the newer sheet arrived; it does not. Then it asserted
`Idle` — and the review measured that failing **4 times in 20**, because
which of the two lands depends on whether the save's resumption or
`reconcile`'s returns from `Dispatchers.Default` last. It now asserts the
thing that is both deterministic and the actual damage: the form is not
holding the old sheet's numbers. 20 clean runs after the fix.
## 2026-10-16 — cycle 28: a hold you did had nowhere to land

**Found.** A row the board prescribes in seconds — "45 sec of low plank" —
has no rep count and takes no weight, so both number boxes on the Log
screen are empty by definition. Every chip on the row claimed something
else: *easy* and *hard* are a judgement, *mod* says you used an easier
version, *hurt* is a warning, *skip* says it did not happen. There was
nothing honest left to tap, and a blank row is written blank by design — so
a plank the owner finished reached their training history as nothing at
all.

**Shipped.** A sixth chip, **done** — "I did it, and there is nothing to
say about it" — offered only on a row the board asked for in seconds. It
rides the save path, comes back on a second sitting, shows on the read-back
screen, and reaches the importer as a complete set, which is what the
bodyweight rule counts. Nothing downstream reads the field by name; what it
changes is that the row arrives at all.

**And the same gap one layer up.** The *since you started* screen dropped a
whole session unless some row carried a number or a skip — so a class
recorded only with markers counted as never having happened, in the session
count, the date range and the movement counts. It now asks the same
question the row does. Deliberately of the sheet's own rows only: whether a
session logged entirely by hand belongs there is a separate open question,
and answering it in passing is how a decision gets made without anyone
making it.

**One thing the screenshot caught that the tests did not.** Six chips do
not fit on one 411dp line, and what slid off the right edge was *skip* — on
the one row shape with no number to say it with instead. The chip row wraps
now, and a test asserts that this row's *skip* sits on a line below *done*
rather than beside it.

## 2026-10-16 — cycle 27: the Before tab stops blaming the sync

**From you.** "I don't really understand why the first page I land on is a
non-existent cheat sheet for a past day."

**Found.** The tab always showed the newest session file and, when it was
not today's, warned *"your folder may not have synced"*. At one or two
classes a week there is simply no class most days, so the sync was fine and
the warning was a false alarm in the primary position nearly every time the
app opened — under the instruction "Carry these in", over another day's
weights.

**Shipped.** A past sheet now leads with the answer rather than the
instruction: the headline is **"No sheet for today"** and the line under it
is "Showing the most recent: 2G · Sat 17 Oct". The numbers stay below,
because looking back is reasonable; what is gone is the instruction to
carry them in and the warning asserting a cause that was wrong most of the
time it appeared.

A sheet dated *ahead*, or with an unreadable date, keeps its warning —
those are genuine anomalies rather than the ordinary state of a rest day.

**Tested.** Both directions falsified: forcing stale off reddens the new
test, forcing it on reddens today's-sheet-still-instructs and one other.

## 2026-10-16 — cycle 26: what the app is for, written down

**From you.** "We want *me* to have the information to know when to push
myself... I also think that idea can be the guiding light here." It had been
said in conversation and recorded nowhere, which is exactly the failure this
run spent an evening fixing everywhere else.

**Shipped.** `DESIGN.md` gains "What it is for", immediately after the
constraint everything else follows from — because it governs the coaching
side, not just the screens. It carries the measurement that produced it: a
progress rule that acted on its own signal was built, measured ten ways, and
rejected every time, because the smallest step a gym stocks is a whole
dumbbell against a rep-conversion signal worth about a quarter of that.

Two consequences are stated because they look like missing features:
**silence is an answer**, and **a warning is worth more than a suggestion** —
telling someone they did not finish a weight last time needs no evidence
about what they should lift, and it is the one thing they cannot reconstruct
in a car park.

**Left.** Nothing. Docs only; no code changed.

## 2026-10-14 — cycle 25: the flaky ViewModel test

**From the backlog**, not from you: `CarryViewModelTest` had been failing at
random in full-suite runs with "Dispatchers.Main is used concurrently with
setting it" since cycle 24 noted it. Test-only change; no production code
moved.

**The cause.** Nothing clears a ViewModel in a unit test — there is no
ViewModelStore, so `onCleared` never runs — so a `viewModelScope` job carries
on past the test that launched it. Two tests park one on `awaitCancellation`
for ever, and every `refresh` ends by resuming from
`withContext(Dispatchers.IO)` to write state back on Main. That resumption
reads `Dispatchers.Main` from an IO thread, and a read landing while
`resetMain` or the next test's `setMain` writes is the message. The cost was
never a real defect: it reddened pull requests that had nothing to do with it.

**Reproducing it first, which is the only reason any of this is a claim rather
than a hope.** Twenty full-suite runs on `main` failed **zero** times. Idle
machines do not delay an IO thread past the end of a test. `forkEvery(1)`
already gives the class its own JVM, so running only `CarryViewModelTest` is
the same trial at a third of the cost — and thirty of those under CPU load
reproduced it once, at `resetMain()`, in one of the two tests that build two
ViewModels.

**A registry, because a single field would have hidden the bug.** Two tests
build two ViewModels each; a field holds whichever was assigned last and
leaves the other's scope running into the next test, which is the race. Every
ViewModel a test builds now goes through a helper that registers it, and
teardown reaches all of them.

**And the cancel the design doc asked for was not enough.** Built exactly as
written, the same probe failed again — once in thirty, same line. Cancelled is
not finished: the blocking read inside `withContext(Dispatchers.IO)` is not
cooperative, so cancelling stops the write-back's effect but not the dispatch
that carries it, and the dispatch still lands whenever the IO thread is next
scheduled. Teardown now cancels **and waits**, driving the test dispatcher
until every registered job reports `isCompleted`, which puts that last
dispatch before `resetMain` by construction rather than by luck. If a job
cannot be drained in ten seconds the teardown fails by name, so the next
version of this bug accuses itself instead of a random later test.

**Measured, after:** 0 of 90 focused runs under the same load, and 0 of 20
full-suite runs. At the ~3% per-run rate the probe measured, 0 of 90 is
evidence rather than proof; what carries the rest is structural — every job
that can touch Main is in a `viewModelScope`, and none can still be running
when `resetMain` is called.

## 2026-10-14 — cycle 25: opening a past session

Tapping a row in *Every session* opens that day: every row the sheet listed,
in sheet order, with the weight and reps that were entered — and the rows
nobody filled in shown blank rather than as anything else. Then the rows
added by hand, which on a borrowed day are the whole session.

Three decisions worth keeping:

- **The rows ride along on `Growth.Day`** rather than the screen re-reading
  the file by document id. The parse has already happened, off the main
  thread, on the read that built the Since screen; a second read would add a
  loading state, a missing-file state and an SAF round trip to a screen
  whose data is already in hand. A file that goes away between reads is
  handled by the selection, not by the detail view.
- **The selection is the session's own `sheet_id`**, held in a
  `rememberSaveable` inside the Since screen — no nav library for one
  screen. An index would have reopened a different day the moment a sync
  dropped a file in, and two classes on one day share a date. When the
  selected session is no longer in the folder the selection is dropped, so
  the detail view cannot spring open again later over whatever is being
  read.
- **It is a record, not a form.** No text box and nothing to toggle, and the
  screen says so in a line rather than leaving the owner to infer it from
  what is missing. A test asserts there is no node on the screen that takes
  text or toggles, and that the only thing to tap is the way out.

**What the testing turned up.** `BackHandler` no longer registers with
`OnBackPressedDispatcher`: activity-compose 1.13 prefers the navigation-event
dispatcher and only falls back to the old one. So
`activity.onBackPressedDispatcher.onBackPressed()` reports success against a
screen that never handled the press — the test drives a
`DirectNavigationEventInput` instead, and the handler is reached.

**Left alone, deliberately.** A session whose sheet rows are all blank and
whose real content is in `added` is still not listed as a session at all:
`Growth.of` decides what counts as a session from `sets` alone. That is the
commonest shape of a borrowed day, so it matters — but changing it moves the
session count, the date span and the quiet-band figures on the Since screen,
which is a decision of its own rather than a detail of this one. In the
backlog.

**What the review found.** Four real bugs, three of them in code this cycle
touched and one older:

- **One new field nearly cost whole sessions.** Reading `backfilled` off the
  `Session` model made any value that is not a JSON boolean — a `null`, a
  `1`, a `"true"` — throw during deserialization, which the loader turns
  into "Can't read that session" and which takes the Before tab's sheet with
  it. The exact failure `added` is kept out of the model to avoid, walked
  into one file over. It is read out of the raw JSON now, where anything
  unexpected costs a line of wording rather than a day.
- **A row answered only with a marker was called blank.** `mod` is the only
  progression axis a bodyweight movement has, and for the third of this
  owner's sets that carry no weight it IS the record. The screen was telling
  them they had left empty the one row they did answer.
- **Two classes in one day were added together.** `Growth.of` counted a
  day's lifts by date rather than by session, so opening the 3G session
  showed a header claiming the 2G class's heaviest lift, with no such weight
  in the rows underneath it. Older than this cycle; it only became visible
  once the count sat above that session's own rows.
- **The Since tab was a save behind.** Only `refresh`, which runs on resume,
  recomputed the history — so the owner could save the day's log, open it
  from the list and read back the blank rows they had just filled in, under
  the words "what you saved that day".

And two smaller ones: closing a session dropped the list's scroll position,
landing the owner back at the top of a year of history (the list's state is
hoisted out of the branch now); and the test that claimed to prove otherwise
could not have, because its fixture fit on one screen and the scroll was a
no-op — the third time that trap has caught this project. The fixture is two
dozen sessions now, with a test whose only job is to fail if the row it
reaches for is ever on screen without scrolling.

**Left as it is.** Two files claiming the same `sheet_id` leave the
selection picking the first; nothing dedupes them, and an index would be
worse for the reasons this screen keeps an id at all.

**And the second look caught the fix.** The short review of just the fix
diff — the one the rules ask for after acting on findings — found that the
Since re-read had been dropped inside the `when` whose value becomes
`_saving`. So the save button went on saying "Saving…" for the whole of a
read of every file in the folder, against a provider that can block
forever, with the file already written and the retry guard refusing another
tap: no way to tell a hung save from a lost one. Worse than the staleness it
was fixing.

It is a tracked job of its own now, started after the state flips and
cancelled by the next one, which also stops a save's slow snapshot landing
on top of a fresher refresh. Worth recording that the review *of the review
fixes* was where the real bug was.

The test for it also had to be rewritten twice. The first version waited on
a coroutine deliberately left suspended, which outlives its test and is the
shape of this project's one known intermittent failure — it passed alone and
failed in the suite. The second asserts what the button said at the moment
the read started, which needs nothing left hanging.

## 2026-10-12 — cycle 24: handing the block over

**From you.** Nothing directly; this was "compose the 3G intel post" from
the backlog's Later list. Mid-cycle you also cleared three decisions that
had been blocking: the runner may emit session files, added rows may carry
an id, and the aggregate facts about your training come out of the public
docs. Then the one this PR was parked on — what to promise about names you
type — you left to me: "I have no idea, up to you."

**Shipped.** After a save, Carry composes the post that tells the subreddit
what this class did, shows it in full, and hands it to your share sheet.
Intel for your class is posted on a small minority of days, which is why
most of your history is borrowed; this is the only path that gets one of
your sessions into the corpus at all.

**Review caught — and the first version should not have shipped.** The
fresh-eyes pass returned twelve findings, and the first was a design error
rather than a bug:

- **On a borrowed day the post published the OTHER class's block under
  your class's heading.** The sheet on a borrowed day *is* 3G's block, with
  3G's rep counts. Posting that as 2G intel would have fabricated a board
  that never existed — and the coach scrapes r/orangetheory, so it would
  have read its own fabrication back next week as genuine. The exact
  pollution this project exists to fix, made self-sustaining and public.
  Most of your days are borrowed days, so this was not an edge case; it was
  the main case.
- **Added rows published the reps you managed as the block's
  prescription.** An added row has no prescription — `reps` is what you
  did. "8 x ybell deadlift" leaks a personal number *and* invents a
  workout. A test of mine asserted this as correct, which locked it in.
- **It was built from `line`**, the one raw field Carry deliberately never
  displays and does not own. Its sibling `suggested` holds "try 45 lb (up
  from 40, held 3x)" — same schema, same writer. One runner revision
  folding an annotation into `line` and "never your weights" was false,
  with no change here and no test failing.
- A newline in a name wrote its own bullets, publishing movements the class
  never did. Folding the break to a space stopped the extra bullets and
  still published the rest of the string, so the name is cut at the break.
- The heading was neither sanitised nor capped; nothing capped the post's
  length; and the share failure was swallowed, so a text too big for the
  transaction made the button do nothing, for ever, silently.
- **The button outlived its session.** Nothing reset `SaveState` on a new
  read, so a sync dropping the day's second class in left "Saved" standing
  and offered a post for a sheet nobody had filled in.
- "The same test the history uses" was not: tapping *hurt* while deciding
  counted as having done the movement.
- Added rows were appended last and then truncated, so a long sheet
  silently dropped exactly the rows that matter most on a borrowed day.
- **And the two headline tests could not fail.** They asserted the output
  did not contain a weight — but the composer never read a weight field on
  any path, so they would have stayed green while it started publishing
  your notes. They are rewritten to drive the channels that actually carry
  text: `line`, `suggested`, `note`, the class tag, the date, added rows,
  each with hostile content in it.

The fix made the thing simpler. It is built from the parsed fields, it
publishes rep counts only where they are genuinely the board's, and it
shows you the text before it goes — which is the guard worth more than all
the others, because no amount of care in this file beats you reading it.

**Then it sat for 36 commits, and the unparking found two more.**

- **The rep count it published was the wrong number.** It used
  `prescribed_reps`, which is what the runner's weight math settled on, not
  what the board said: an alternating "10 total" is 5 a side, and a "6 -
  10" range is its floor. Measured against the cached sheets, **those two
  disagree for 56% of rows** — so most of the post would have told the
  subreddit to do half the reps. The runner now carries the board's own
  wording (`prescribed_text`) and the duration of a timed line
  (`prescribed_duration_sec`) in the session file, and the post uses those.
  The same fields put the prescription on the Log row, where there was
  previously nothing: a 30-second plank and a 90-second one had looked
  identical.
- **The promise under the button was one the app cannot keep.** "The
  movements only — never your weights" is false the moment you type `ybell
  deadlift 26`. Stripping trailing digits was the obvious fix and the wrong
  one: it mangles `bicep curl 21s`, a real movement. So the preview shows
  any bullet whose *movement name* carries a digit in brick and says to
  check them. The prescription's own digits are excluded, or every line on
  an intel day would be flagged and the flag would mean nothing.

**Caught on the rebase, which is its own lesson.** Two tests passed for the
wrong reason once the rows became two lines tall. The post button is the
last item of a `LazyColumn`, which does not compose what is off-screen, so
`assertDoesNotExist` on it passed whether or not the button existed — the
list simply never scrolled that far. The "offered only once the log is
written down" test now drives one composition from Idle to Saved and
asserts the button IS reachable after the save, which is what gives the
absence before it any meaning.

**The second review, on the unparked branch, found six more.** Five were
mine, from this pass:

- **A duration the file could not mean was rendered anyway.** An `Int` `0`
  is not null, so `prescribed_duration_sec: 0` beat a perfectly good
  prescription and published `0 sec of squat`; a negative went straight
  through the `>= 60` minutes test as `-45 sec of`. Now anything outside
  one second to one hour is refused and the row falls back to its rep
  scheme.
- **`prescribed_text` was published verbatim.** It is free text from the
  same file and the same writer as `suggested`, which holds "try 45 lb (up
  from 40, held 3x)" — so it was exactly the hole the design already names,
  reopened in a field that is both shown to you and published, under a
  caption saying "never your weights". It is now accepted only if it looks
  like a rep scheme: digits, separators, and total/each. `10 (45 lb)` is
  dropped, and the row carries no prescription rather than a trusted one.
  That bounds its length on the Log row too, which was otherwise unbounded
  and could push the movement name off the row entirely.
- **The flag fired on movements you never typed.** `bicep curl 21s`,
  `90/90 hip stretch` and `3 way shoulder` are real names, and flagging one
  lights "check the lines in red" on a line you cannot fix — on every intel
  day, until you learn to ignore the one guard the design calls worth more
  than all the others. Only a name you typed can be flagged now.
- **Adding the new fields to the row identity would have refused a save.**
  A runner upgrade re-emits the same session WITH the new fields, which
  flips every row's fingerprint — so a session half-filled across two
  sittings could only be discarded. Reverted; the fields add nothing
  `prescribed_reps` did not already give.
- **A typed row could shadow the sheet's own.** Deduplicating on the
  rendered bullet made "10 total x squat" typed by hand equal to the
  sheet's, and typed rows come first, so the real one was dropped and the
  survivor flagged.
- And `an empty log is offered no post to make` was the third test passing
  for the wrong reason, the same off-screen `LazyColumn` trap.

**Noted, not fixed.** `SessionFiles.formatDate` hands back the raw `date`
field when it will not parse, so free text in a synced file reaches the
post's heading. It is capped at 80 characters and cut at any control
character, so it cannot invent bullets — it would read as nonsense, which
you see before sending.

**Still open, and not this PR's to fix:** `CarryViewModelTest` can fail in
a full-suite run with "Dispatchers.Main is used concurrently with setting
it" — a `viewModelScope` job outliving its test and racing the next
`setMain`. It is on main, it is intermittent, and it is unrelated to the
post; it is in the backlog.

**And the review found one in the coach, not the app.** Asked whether the
coach could read back what the app publishes, it turned out
`6-10 x deadlift` normalised to `610 x deadlift` and was read as six
hundred and ten prescribed reps — twelve rows in the cached sheets, fixed
on the runner side.

## 2026-10-12 — cycle 23: bigger again, and say which build you are

**From you**, with another screenshot: *"This text still feels small."*

It was, and the reason the first pass did not settle it is worth writing
down: there is no way to tell from a screenshot which build it came from.
`SmallSize` has now gone 11 → 13.5 → 15 and `LabelSize` 9.5 → 11.5 → 13,
and at no point could either of us be sure the picture we were discussing
had the previous change in it.

So, your second ask, now a practice: **every app shows which build it is,
copyable in one tap.** In Carry that is one line at the bottom of the
*Since* tab — the screen read at home rather than in a lobby or a car —
reading `Carry 0.1.0-debug.66+c0f0d17` and copying itself when tapped. It
comes from `BuildConfig`, never a string typed into the UI, because a typed
one goes stale silently. Written up in `practices/version-page.md`.

It is not a changelog or an update check. One line.

## 2026-10-12 — cycle 22: the last two from the reviews

**From you.** "Keep moving" — these were the two left that needed nothing
from you.

**A key the writer removed came back.** `asRead` is pinned to the bytes
the owner's first keystroke saw, so copying unknown keys out of it put
back a key the coach had since deleted — resurrecting data nobody holds
any more. The row is found on disk by id now, and the keys come from the
file as it stands rather than from a stale copy. Both directions are
tested: a key removed since stays removed, a key added since is kept. The
ids shipped two cycles ago are what made this possible at all; before
them there was nothing to match a row on.

**The forty-first added row vanished.** The cap on shown rows was a count,
and a count has a cliff in it: with forty rows already in the file, a row
the owner typed was written and then dropped by the re-seed after the save
— in the file, and no longer reachable from the phone. The budget is in
bytes now, which is what the draft actually has to carry across a Binder
transaction; a real added row is a hundred-odd of them, so there is no
cliff at any count a person could reach.

That empties the review backlog. Everything still open is either yours to
decide, owed on a phone, or a feature nobody has asked for yet.

## 2026-10-12 — cycle 21: the add-row leads on a borrowed day

**From you.** "Borrowed-day layout: promote the add-row."

Intel for your class is posted on a small minority of days, so the sheet
is usually another class's block and whatever you add by hand IS the
workout. That button was at the bottom, under eleven rows of the wrong
exercises. On a borrowed day it now leads: the warning, then "+ Add what
you did" in amber, then your added rows, and only then the borrowed sheet.
On a normal day nothing moves.

**It also changed a decision on the parked intel post (#60).** The first
of its three open questions was whether anything but an added row is
publishable on a borrowed day — the honest answer being "no", at the cost
of most of the feature's value on exactly the days it exists for. That
cost is gone: the app now collects added rows first on those days, so the
honest answer and the useful one are the same one.

## 2026-10-12 — cycle 20: the axis with no weight on it

**From you.** *"There should be a way for me to note that... like pushups
I'm still on knee pushups. I should know when to push myself to do more
full pushups. But maybe that's too much to include in the app."*

Not too much — the largest uncovered area there is. **37% of your logged
sets carry no weight at all**, and across 98 real sheets 31% of lines are
bodyweight, median a third of any given sheet. The coach's whole model is
"what weight next"; for a third of your training there is no weight, so it
said "no weight needed" and nothing else, for ever.

**And it was worse than silent.** The bodyweight path already had a rule:
three clean sets in a row earned *"full form 3x in a row, push it: faster
pace or the harder option"*. Ten knee push-ups done for all the prescribed
reps are COMPLETE — so three sessions of them congratulated you on form you
had not used, and told you to go faster on top of it. Recording *mod* fixes
a wrong message, not just a missing one.

**Shipped:** *mod* on every row, three clean modified sets earn "try one as
prescribed", mixed form holds rather than pushing, and the full-form
message now only fires when nothing in the streak was modified. The ladder
stays in your head — naming the next rung for every movement is a knowledge
base we do not have and you do not need.

Also *hard*, finally reaching the app: it went into the schema and the
engine earlier today and there was no way to set it, which is a half
feature. *easy* and *hard* are one question with two answers, so they are
exclusive.

Five chips fit because the rows went to two lines an hour earlier. "easier"
wrapped inside its 44dp box, so the chips size to their label now with 44dp
as a minimum rather than a fixed square — and the word is *mod*, which is
what a coach on the floor says.

## 2026-10-12 — cycle 19: legible, on a real phone

**From you**, with screenshots from your own device: *"This font is WAY too
small."* It was. `SmallSize` was 11sp where Android's default body text is
14, and the section labels were 9.5sp. These screens are read at arm's
length in a lobby and filled in in a car; that is not caption territory.

The whole scale went up — names 15→16.5, figures 18→20, small 11→13.5,
labels 9.5→11.5, titles 19→21 — and the two sizes that were literals in
three different files are tokens now.

**Which forced a layout change rather than allowing one.** Your own
screenshot already showed `bicep curl + lateral lu…` truncating at the OLD
size: the name was sharing ~400dp with two number boxes and three 44dp
chips. Bigger type would have made that worse. Neither side can give up
room — a name you cannot read is useless, and the 44dp targets exist
because this is filled with cold hands — so the row got taller instead.
Name on its own line, controls beneath it. Added rows match.

The cost is real and visible: about nine rows fit where eleven did, so
there is more scrolling on a screen that already had some. That is the
right trade on the tab you sit and fill in, and it would be the wrong one
on the glance screen, which is why only this one changed.

**It also showed up in the tests.** Five failed, all because a
`LazyColumn` does not compose what is off-screen — so there was no node to
scroll *to*, and the list had to be scrolled to it instead. Worth knowing:
making rows taller silently moved half the screen out of every test's
reach, and the failures said "could not find node", not "your layout
changed".

**Also, the quiet band now retires itself.** You asked whether "why the
coach is often quiet" is a long-term thing or wants reframing, and whether
I would update it on a cadence. It needs no cadence — it is computed from
the files every time — but it should not be permanent: it earns its place
only while almost nothing repeats. Once a quarter of movements have come
round again the quiet explains itself, and a band still saying it would be
telling you off for variety the studio chose. It now disappears on its
own at that point, and stays away below eight movements, where there is
too little training to say anything about its shape.

## 2026-10-12 — cycle 18: the label that wasn't needed

**From you**, and it was a correction worth recording: *"all that matters
is what dumbbell to grab whenever a shoulder press presents itself, right?"*

I had measured the right corpus and asked the wrong question of it. I asked
"which variant fields tell two same-named rows apart" — tempo 26%,
implement 21%, stance 20% — concluded that no single field would do, and
proposed a contextual label computed per sheet, plus a schema key to carry
it. All of that was answering "how do we distinguish these rows", when the
screen's job is "what do I pick up".

Asked the owner's question instead, the same corpus says don't:

- a sheet repeats a movement on **42%** of days, so this is not an edge case
- those repeats use the **same implement 78%** of the time
- they agree on whether a weight is needed at all **84%** of the time

So a label would have said nothing four times in five. And in the 15% that
are genuinely mixed, the sheet already separates them by the only axis that
matters: on 2026-10-12 the two TRX shoulder presses carry "no weight
needed" and land in the *No weight* line, while the double-dumbbell one
lands in *By feel*. The information was already on the screen.

**Shipped instead:** the names collapse. `shoulder press ×2` rather than
`shoulder press, shoulder press` — the second mention was spending the one
thing the glance screen is short of to repeat itself.

The scheme that got dropped would have been a schema key, a per-sheet
computation in the runner and a new field on the client, to restate
something two existing fields already said.

## 2026-10-12 — cycle 17: saying you skipped it

**From you.** "When will you work on the skip chip?" — fair, having been
promised twice.

**Shipped.** A third chip. `skipped` had been in the schema, in the patch
and in the importer since before the Log screen existed, with no way to set
it: a movement you deliberately didn't do looked exactly like one you never
got to, so the coach kept suggesting it for ever. Skipping clears the row
and removes its boxes — there is no number to put in them, and a weight
against a set you did not do is the wrong number this app exists to avoid.
Un-skipping does not restore the old number, because nobody re-entered it.

**The cost, which is real and is yours to judge on the phone.** A third
44dp target takes 44dp from the name column, and "single-arm row · split
stance" now truncates where it didn't. The 44dp is deliberate — this is
filled in a car with cold hands — so the trade is name width against hit
area. Taking the boxes away from a skipped row gives the width back on
exactly the rows that no longer need it, but it does nothing for the other
nine. Logged for a device look rather than settled here.

## 2026-10-12 — cycle 16: looking back

**From you.** The three user stories, and then the architecture: "copy past
data to the synced folder yourself, the app ingests from there, and writes
to it with its own data." That is better than either option I had put in
the proposal, and it dissolved the problem I was stuck on — if every
session is one file, written once and completed once, there is no shared
mutable history for two parties to disagree about.

**Shipped.** A third tab. The folder is read whole, and what it adds up to
is shown: what is nearly earned, the heaviest in each family, every session
newest first, and why the coach is so often quiet. Your 32 past sets are
back-filled into the folder as session files, marked so the importer
refuses them — they are already in `history.jsonl`, and the duplicate guard
keys on a field the Signal-era rows never carried.

**What running it against your real data found**, none of which any fixture
would have:

- **It counted rows on sheets, not movements done.** An unfilled test sheet
  sitting in the folder was being counted as a session and contributing
  eleven movements to the very figure that explains why the coach is quiet.
- **It had its own idea of "the same exercise".** Hashing the whole variant
  made 20 of 20 lifts look distinct, when the engine says 19 — because the
  engine's key deliberately leaves out intensity, grip and reps. This is
  exactly the two-implementations-drift failure I had warned about in the
  proposal one hour earlier, and then walked into. The runner writes the
  key now; the app groups by it and computes nothing. Where a file has no
  key the count is withheld.
- **It had no answer to "what did I do on the 27th."** Every band was cut
  across days. There is a session list now, each named by its heaviest lift,
  because that is what a person remembers a session by.

Both substantive tests were checked by breaking the code and watching them
fail before being kept.

**And the device found a fourth, as it did last time.** The tab opened on
"0 sessions" over a folder holding nine. `Folder.list()` returns
**(document id, display name)** — it says so in its own doc comment — and
this read them the other way round, filtering ids against the filename
pattern and matching nothing. Every test passed, because every one of them
handed file TEXT straight to `Growth` and never went through a folder at
all. There is a test through a real listing now, with the junk a shared
folder really holds in it, and it was checked by flipping the pair back.

Two device checks, two integration bugs invisible to a green suite. The
pattern is the same both times: the tests covered each half and nothing
covered the seam.

## 2026-10-12 — cycle 15: rows that know their own name

**From you.** "Go ahead, add an id" — the schema addition that unblocked
this. It fixes three logged defects at once, which is why they were grouped
rather than done piecemeal.

**Shipped.** Every added row carries an id of its own, minted when the row
is added and written into the file. Position was the identity before, and
it was wrong three ways at once:

- **The screen showed a number that was not in the file.** Text boxes were
  keyed by list index. Add two rows, fill only the second, save: the blank
  one is dropped, the second slides into slot 0, and its weight box renders
  the blank row's empty string while the file holds 30. "Saved", and a
  number missing from the form that is sitting in the file — the
  re-enter-from-memory failure, which is how a *wrong* number gets in.
- **A row could be written twice.** The draft survives the coach rewriting
  the file underneath, so the owner's rows can come from older bytes while
  the carried elements are read from the current ones. With nothing to
  match on, both copies were written. The save now drops a carried element
  whose id is one the owner is already writing.
- Nothing could tell "the row being edited" from "the row in that slot".

**Caught before the review got to it**, which is worth writing down because
it is the same class of mistake as the rest of the night: a file written
before ids gets a positional stand-in on read, and the first version
*wrote that stand-in back into the file*. Persisted, `"@1"` collides with
the next read's stand-in for whatever row then sits at index 1 — and two
rows sharing an id is worse than neither having one, since the screen keys
its boxes by it and the save dedupes by it. Stand-ins now stay in memory,
where they are unique by construction, and an id starting with `@` is never
trusted from a file.

**The test was checked by breaking the fix.** `a box keeps its own number
when the list shrinks under it` passed the moment it was written, which
after tonight is a reason for suspicion rather than confidence — so the
keying was reverted to index, the test was watched to fail, and the fix put
back. Four tests tonight could not fail on the bug they named; this one can.

**Review caught, and it was the same trap one level out.** The fix only
applied to rows added on this phone: nothing promoted a positional
stand-in, so a row from an existing file kept position as its identity
forever and the original bug survived inside a single file — drop a blank
row, save, and the next row inherits its stand-in, its slot and its box.
The screen test passed vacuously because its rows were minted. Promotion
now happens where the rows are seeded, through one function so no path can
miss it, and the ViewModel test for it was likewise checked by removing
the promotion and watching it fail.

The review also found the dedupe worse than what it replaced. Dropping a
carried element whose id the owner is also writing sounds right, but the
owner's copy comes from a draft pinned to older bytes, so the file's copy
is deleted and a stale `asRead` written over it — destroying a key added
since, which is the one thing the carry path exists to prevent. That turned
a visible, lossless duplicate into a silent deletion. Reverted; the
duplicate stays in the backlog, where it was.

## 2026-10-12 — cycle 12: the added-rows round trip

**From you.** Nothing; this was the item left open at the top of the
backlog after cycle 5's review.

**Shipped.** The `added` array — the rows you add for something the sheet
missed — is replaced whole on every save. That makes it a different kind
of risk from `sets`: a row Carry can't read isn't skipped, it's deleted.
Three ways that lost your own typing.

The live one wasn't in the patch code at all. `save()` ran
`_added.value.filter { it.name.isNotBlank() }` before handing anything to
the patch, so a row you typed a weight into and hadn't named yet was
thrown away — silently, under a "Saved" message, with nothing afterwards
to tell you it had happened. No test covered that line. The comment above
it said "a row the owner started and left nameless is not an answer",
which is true right up until there's a number in it.

So the patch decides now, not the ViewModel. A row holding nothing is
left out. A row holding numbers with no name is **refused**, and because a
refusal about a row you can't see is a dead end, unnamed rows now reach
the screen too — and the row's own name box asks for it in brick before
you ever get to the save button.

The second was `encode` not copying unknown keys through, which the `sets`
path has done since cycle 1. Added rows now carry the object the file held,
verbatim. Two things fall out of that: a key a newer Carry wrote survives
an edit by an older one, and a save that changed nothing finally compares
equal — before this, a file with any extra key on an added row rewrote
itself on every save, which to a folder being watched by a sync looks like
a change that isn't one.

The third is the new one: those verbatim rows live in the draft, which is
re-encoded into saved instance state on **every keystroke** and crosses a
Binder transaction to get there. The draft used to hold the whole file
text and died of exactly that past about half a megabyte, destroying the
typing it exists to protect. The file is synced from another machine, so
its size is not ours to assume: an added list bigger than Carry would ever
write refuses the save rather than deleting it.

**Also**: the tally read "0 of 11 filled" with 20 lb typed into an added
row, because it counted added rows by whether they had a name. The
screenshot of the new state is what showed it — which is the argument for
cycle 11 in one line.

**Review caught** (fresh-eyes subagent, eleven findings; it verified the
kotlinx-JSON behaviour empirically rather than from memory, which is how
three of these were pinned down):

- **The first fix was a dead end.** Refusing to save over an added row
  Carry couldn't read sounded like the cautious choice and wasn't: that
  row is *by definition* one the owner can't see, so there was nothing to
  do about it, and the day's real numbers could never be written either.
  The same shape the file already guards against in two other places. The
  answer is to **carry** anything the screen never saw straight back out —
  nothing lost, nothing blocked.
- **A whole class of row was still being deleted silently.** The guard
  checked key *names*, so `{"name": {"canonical": "goblet squat"}}`
  decoded to an empty name, looked like an empty row, and was written out
  of existence under "Saved". Carrying fixes this too.
- **`added` should never have been in the `Session` model.** `name` had no
  default, so a row with no `name` key threw during deserialization and
  the loader reported "Can't read that session" — the whole day lost over
  one odd row. Worse, it made the new behaviour unreachable: the test
  fixture for "an unnamed row reaches the screen" was a file the app
  cannot load, and the test passed only because it called the patch
  directly. A green test with no reachable behaviour, which is the exact
  trap written up at the top of this log.
- **A crash loop I had just introduced.** The size cap measured rows with
  `toString()`, which recurses: 12 KB of two-thousand-deep JSON overflows
  the stack, and that ran inside the read on every resume. Now measured
  with a depth-capped walk, the same lesson `normalise()` already carried.
- **Re-encoding destroyed values on rows nobody edited** — `reps: 12.5`
  to null, `easy: "yes"` to false — while reporting success, and made
  no-op saves rewrite the file (`45.0` → `45`). An untouched row now goes
  back as the bytes that came in.
- **A one-space name refused the save with both hints suppressed.** The
  refusal tests `isBlank`, the placeholder tested `isEmpty`: told to name
  a row, with every row on screen looking named.
- The draft encode wasn't guarded, so a throw there crashed on a
  keystroke — destroying the typing the draft exists to protect.

A second look at the fixes found two more real losses, both now fixed:
`{"name": "", "weight": "45 lb"}` counted as an empty row and was
deleted, because the check looked at key names rather than values; and
`{"name": null}` reached the screen as an exercise called `null`, because
`JsonNull.content` is the string "null" — Carry inventing an identity,
which is the one thing it must never do.

It also caught two claims I had written that were too strong. Carrying
does not block *nothing*: a row nested two thousand deep still can't be
written, because the whole document is re-encoded and the nesting is in
the document. And the `runCatching` around the draft encode cannot catch
the oversized-transaction failure that motivated it — that is thrown
later, by the system. Both corrected where they were claimed.

Four findings are logged in the backlog with repros rather than fixed
here: index-keyed text boxes after the list shrinks, a carried key
resurrected from a stale draft, a row written twice when the file is
rewritten underneath and a row changes category, and the forty-first
added row vanishing from the screen. The first two predate this change;
the last two need a file no writer produces today, and in every one of
them what shipped before deleted the row outright.

## 2026-10-12 — cycle 11: screenshots that are actually of something

**From you.** Nothing; the repo had no picture of what this is.

**Shipped.** Three screenshots in the README — and, far more usefully, the
discovery that most of them were blank.

Running the whole suite in one JVM produced **seven of twelve screenshots
as the same blank image**. Not an error: `captureToImage` returns a flat
bitmap, the tests stay green, and CI uploads the blanks as artifacts.
Twice during this project I read one and drew a conclusion from it — once
believing the newly bundled fonts had broken rendering entirely.

`forkEvery(1)` fixed seven of them. Five remain, all simple centred
screens, and the cause is unfound — one screen rendered by the same
composable in the same class captures fine. Rather than keep guessing at
00:30, those five stop claiming a picture and keep their assertions, which
were always the real test.

The part worth keeping is `isFlat()`: every capture is now checked, and a
blank one fails loudly. A green test that produced no picture is worse
than a red one, because the picture is what gets reviewed.

Also merged tonight: Gradle 9.8, Kotlin 2.4.20, Kover 0.9.11 and AGP
9.4.1, from the Dependabot config added in cycle 8. The AGP bump failed
until the Gradle one landed — it needs 9.6+ and the wrapper was on 9.3,
which Dependabot had raised as a separate PR.

## 2026-10-12 — the first run on a device, with real data

**From you.** "Yes, wire it up" and "feel free to copy the existing data
into session files" — which together made this possible for the first
time.

The runner now writes the session file (in the other repo), so the whole
chain could be tried end to end: real history → runner → file → sync
folder → the real folder picker → the two screens. It was put on an
Android 36 emulator and driven through the SAF grant by hand.

**It did not work, and nothing in either repo had failed.** The phone said
*"No session in that folder yet"* with two session files sitting in the
folder. The client matches `session-<date>-<class>.json` and ignores
everything else — the folder is shared, and a half-synced file is normal
there — and the runner was writing `<date>-<class>.json`. 125 tests here,
26 test files there, all green, on two halves that could not talk to each
other. One function owns the name now and a test pins it against the
client's own regex.

**What the real data then showed**, which no invented fixture would have:

- Of eleven exercises, **none** had a suggested weight. Eight are
  bodyweight or TRX, and of the three weighted moves only one appears in
  the history at all — as a dumbbell overhead variant against today's TRX
  one, so "no weight needed" is the correct answer rather than a miss.
  A day like this is two tail lines and a lot of empty space, which is the
  design working: there is nothing to carry, and the app's whole value
  that day is the log.
- **Three rows all read "shoulder press."** The sheet has three
  shoulder-press lines differing only by variant, and the screen shows
  `canonical`. On both tabs they are indistinguishable, so there is no way
  to know which one you are filling in. Logged for the owner, because the
  fix is a display string from the runner rather than Carry inventing one.

**And then the whole loop closed, for the first time.** On the device: 35
lb x 12 typed into the first row, saved, and the file on disk came back
with that row filled and every other row untouched — identity, variant
and the runner's own keys intact. The file was pulled off the phone and
handed to the importer, which read it as `shoulder press: 35 lb x 12`,
ready to log.

Real history → runner → session file → folder → phone → SAF → form →
save → importer. Every link, with the owner's own data, none of it
invented. That is the thing the rebuild was for, and until tonight no
part of it had been run against another part.

## 2026-10-11 — cycle 10: the last two from the security pass

**Shipped.** `chosen` now requires read *and* write, because Carry writes
the log back — checking only read made a read-only tree (what some cloud
providers hand out) look perfectly usable right up until the save failed,
after the workout, with the numbers already typed.

And exercise names have bidi overrides and zero-width marks stripped.
These arrive by accident rather than by attack — the names come from
Reddit comments by way of another machine — but this is an app whose
stated worst outcome is a wrong number in someone's head, and text that
renders in an order other than the one stored is the same kind of failure.

**Note for the morning.** Adding `dependabot.yml` in cycle 8 did what it
was supposed to and immediately opened nine pull requests: five action
bumps, which are repo-wide because workflows are, and four Gradle and
Kotlin bumps for Carry. None are merged. The toolchain ones (Kotlin 2.3 to
2.4, AGP 9.1 to 9.4) are real upgrades that deserve a cycle rather than a
rubber stamp, and the action ones touch the other two projects.

## 2026-10-11 — cycle 9: stop carrying the file around

**From you.** Nothing new; two findings the security pass recorded rather
than fixed.

**Shipped.** The draft keeps a fingerprint and the per-ref identities
instead of the whole file text. That is all it was ever asked: has this
file been rewritten, and does each edited row still describe the same
exercise. Carrying the document meant a JSON escape of the entire thing on
every keystroke, through saved instance state — which past about half a
megabyte is a `TransactionTooLargeException` that destroys exactly the
typing the draft exists to protect.

Parsing also moved off the main thread: seeding was two full parses on
every resume, and a save was several more, all in the click handler.

**Caught while building.** My own test asserted the draft was "smaller
than the file" and failed — on a two-row fixture the JSON escaping
dominates, so the claim was false while the design was right. The saving
is in the fields *not* kept, which is what the structural assertions pin.
Replaced the claim rather than the fixture, because a test that only holds
on a flattering input is not evidence.

**Next.** What is left is in BACKLOG.md, including three things that are
the owner's call rather than mine.

## 2026-10-11 — cycle 8: the security pass

**From you.** "Make sure we're doing architecture and code cleanliness and
design and security passes."

Run the way `practices/recurring-reviews.md` describes: zizmor on the
workflow, and two fresh-eyes reviewers on separate MASVS areas — storage
and privacy, platform and hostile input.

**Two crash loops, both from a file written on another machine.** These
are the ones worth remembering, because each made the app unusable with no
fix available from the phone — `onResume` re-reads the same file, so every
relaunch wedged again.

- `"streak_needed": 2000000000` is sixty bytes of valid, correctly-shaped
  JSON, and was rendered literally: two billion nodes emitted during the
  first composition of the default tab. Clamped.
- Two rows sharing a `ref` threw out of `LazyColumn` on the Today screen.
  The Log screen had been guarded against exactly this a cycle earlier,
  with a comment explaining why — and Today, which renders *first*, was
  missed. So `SessionPatch`'s careful refusal for such a file could never
  have been seen.

**Scope, which is this app's whole reason for existing.** A SAF tree grant
is recursive and survives reboots, and "Internal storage" sits one row
above the folder in the picker. Carry now refuses a whole volume outright,
names the folder it holds on every screen that offers the picker, and
offers to hand it back — a grant nobody can see is a grant nobody can
audit, and the predecessor's failure was precisely a handle wider than its
job.

**Also fixed:** a 1 MB ceiling on what will be read at all (the text is
held three times over — string, parse tree, draft — so an oversized file
was an OOM that took the typed numbers with it); `dataExtractionRules`,
because `allowBackup="false"` stopped covering device-to-device transfer
at Android 12; a depth cap in the identity comparison, where a deeply
nested `variant` would have overflowed the stack and been swallowed into a
permanent "the exercises no longer line up"; caps on the strings rendered
from the file; and an unused DataStore dependency dropped.

**Supply chain:** actions pinned to commit SHAs and `dependabot.yml` added
with the 7-day cooldown. I argued against pinning in cycle 0 because pins
rot without something to bump them — which was an argument for adding
Dependabot, not for leaving tags.

**Clean, explicitly:** no logging anywhere, no permissions declared at all
(so nothing can leave the device), one exported component, no personal
data in the repo, no network dependency.

**Left, with reasons, in BACKLOG.md** — including three things that are
yours to decide rather than mine.

## 2026-10-11 — cycle 7: the real typefaces

**From you.** The look you picked depended on them.

**Shipped.** Barlow Condensed and IBM Plex Mono bundled (732K, OFL, with
the licences in `licenses/`). Bundled rather than downloadable: this app
is opened in a lobby with the phone possibly offline, and a font that has
not arrived yet would reflow the one screen whose whole job is to be read
in a glance.

Names are properly compact now — "single-arm row · split stance" fits
without truncating — and the figures are tabular, so a digit keeps its
width as it changes.

**Caught by looking.** The canonical screenshot was carrying a "dated
ahead of today" warning, because the sample session is dated the 17th and
the suite runs whenever it runs. True, but an artefact: the headline
picture should show the ordinary case. It passes an explicit date now, and
the other three day-states keep their own tests.

Also worth recording: a screenshot read while the suite was still writing
it came back blank, and for a moment that looked like the fonts having
broken rendering entirely. A clean directory and a targeted re-run showed
the truth. Screenshot evidence needs the run to have finished — the same
class of silent wrongness as everything else in that medium.

**Next.** The security pass.

## 2026-10-11 — cycle 6: don't lose what was typed

**From you.** Nothing new; the cycle cycle 5's review asked for.

**Shipped.** The four ways the owner's typing could be lost or misplaced,
which is worth stating plainly: everything else on screen can be
re-derived from the session file, and the numbers someone just entered
cannot.

- A draft, rewritten on every keystroke and held in `SavedStateHandle`, so
  process death no longer takes a half-filled form. Ten rows in the car,
  switch to Spotify, Android reclaims the process — they come back.
- A newer file arriving keeps what was typed instead of blanking it, and
  says so on the screen rather than leaving it to be noticed.
- The draft keeps the bytes the numbers were typed against, so a save can
  compare each edited row's identity between then and now. `ref` is only
  an index: a re-run that renumbers rows would otherwise write a weight
  meant for a deadlift against a lateral raise, and the patcher cannot see
  that — it only refuses refs that have *gone*.
- Two taps in one frame no longer start two writes, and a save completing
  after a refresh no longer overwrites the newer state.

**Caught while building.** The restore path did nothing when the file was
unchanged — I had written the draft on every edit and then left `_edits`
alone on reload, which is precisely the case process death produces. The
test caught it immediately, which is the argument for writing the test
from the scenario rather than from the code.

**Review caught — the design was unsound, not just buggy.**

- **A draft carried no session identity.** Saturday's unsaved weights would
  load onto Tuesday's sheet when it synced in, and if the two shared an
  exercise at the same index — the same studio running the same template
  does that routinely — they would be *written into it*. A `sheetId` on the
  draft closes that, and closes two more findings with it: added rows being
  replaced wholesale into another session's file, and the identity check
  passing vacuously when only added rows were touched.
- **"Typed" meant "the file has numbers in it."** A session finished in two
  sittings therefore looked identical to one being filled in, so the
  keep-and-warn path fired with nothing typed at all. A draft is now only
  created by a keystroke.
- **The identity gate could be skipped through its own bookkeeping**: the
  pin was re-set to "whatever is current" on every edit, so edits typed
  against one file could end up pinned to another and patched in blind. It
  keeps the original pin now.
- **The warning was a dead end.** Once the file moved and the save refused,
  every refresh re-raised it and the save could never succeed — the only
  exits were clearing app data or swiping the app away, both of which
  destroy the numbers. There is a "use the new sheet instead" out.
- **The identity comparison was order-sensitive** (a runner re-emitting the
  same variant object with its keys in a different order would have refused
  saves permanently) and could degenerate to comparing `line` alone, so two
  blocks of the same movement swapping places slipped through. Normalised,
  with prescribed reps in the key, and it has direct tests in both
  directions now — it had none.

**Next.** Bundled fonts, then a security pass before anything is called a
release.

## 2026-10-11 — cycle 5: the Log screen

**From you.** Nothing new; the last thing on the near list.

**Shipped.** Two tabs — *Before* and *After*, because there are two
moments and the app is never open between them. Each row takes a weight, a
reps count and the two chips that cannot be derived; plus a row the owner
can add for anything the sheet missed, which on a day with no intel for
their class is the session rather than an afterthought.

A session opens showing what a previous sitting already entered, seeded
from the file itself rather than remembered, and only when the file
changes — re-seeding on every read would throw away edits in progress. A
save reports which of the three real outcomes happened, and "couldn't
save, your file is unchanged" is worded differently from "the file may be
damaged", because they ask different things of the owner.

**Review caught — a weight ten times too large, silently.**

- **The decimal point could not be typed.** The field rendered the *parsed*
  value back into the box, so "12." parsed to 12.0, rendered as "12", and
  the next keystroke made **125**. A half-pound dumbbell became a weight
  ten times too large with nothing on screen to show it. The field holds
  text now and only the parent parses. My test for exactly this used
  `performTextReplacement`, which commits the whole string at once — the
  one path that worked — so it could not have failed on the bug it was
  named for. It types key by key now.
- A file with two rows sharing a ref crashed the After tab outright:
  `LazyColumn` throws on a duplicate key, before `SessionPatch`'s refusal
  could ever be shown.
- `isDigit()` accepts Hindi and Arabic digits, which then fail to parse, so
  on those keyboards the box blanked on every keystroke. `'0'..'9'` now.
- Chips announced identically to TalkBack whether on or off, with colour
  the only signal, and were ~26x20dp — filled in a car with cold hands.
  They are `toggleable` with a `Checkbox` role in a 44dp target.
- Three more of my tests asserted less than their names: one could only
  fail if the save button were entirely unwired, one never turned a chip
  off, one typed nothing.

**Left for cycle 6, recorded in BACKLOG.md:** four ways the owner's typing
can still be lost or misplaced — process death, a newer file arriving
mid-fill, edits made against bytes that have since changed, and save
racing refresh. They are one coherent cycle rather than a scattering.

**Next.** Cycle 6, above.

## 2026-10-11 — cycle 4: writing back without disturbing anything

**From you.** Nothing new; the write path, which cycle 1 had already
designed by accident.

**Shipped.** `SessionPatch`, which writes the owner's numbers into the
runner's own JSON rather than re-serialising `Session`, and
`SessionFolder.write`. No UI yet — the patching is the risky half and gets
its own cycle, so the screen can be built against something already proven.

The rule it enforces falls out of the approach rather than needing care:
identity is copied, never rebuilt, so there is no code path that could
invent it. Saving an unedited session returns the original text untouched,
which matters because a sync watching the folder would otherwise see a
change that isn't one. A blank row stays blank and only an explicit skip is
recorded as one.

**Review caught — the first version could destroy a day's data.** Easily
the most valuable review so far:

- `openOutputStream(doc, "wt")` **truncates on open**. Any failure after
  that — a provider error, a sync replacing the file, no space — left a
  zero-length document, taking the owner's numbers and the runner's intel
  with it, while the method returned `false`, which the interface defined
  as "the write did not happen". Now `save()` writes, reads back, restores
  the old content if what landed is wrong, and reports which of three
  things happened. Still not atomic: SAF has nothing that is.
- A `sets` key that was not an array became `[]` — one edit would have
  deleted the whole workout. Refused now.
- Two rows sharing a `ref` got the same weight written to **both**. One
  number typed once, against two different exercises, in a training
  history. Refused.
- An edit whose `ref` was not in the file was dropped silently while the
  save reported success. The likeliest cause is the file having been
  replaced by a sync mid-fill, so the rows on screen are not the rows on
  disk — the whole save is refused.
- A blank entry could never clear a mistyped weight, because blanks were
  filtered out. Edits are now the desired state of the rows they name.
- Added rows were appended, so a second save duplicated every one of them,
  and there was no way to read them back. They are replaced, with an
  `addedIn` to match `entriesIn`.
- `entriesIn` had one guard around the whole loop, so a single junk element
  erased every other row's recorded answers and the owner would re-enter
  from memory. Guarded per row.
- A NaN weight reached the encoder and threw. Four of my tests asserted
  less than their names claimed, including one that could not fail by
  construction.

**Next.** The Log screen itself, against a write path that is now genuinely
tested.

## 2026-10-11 — cycle 3: a sheet that is not today's says so

**From you.** Nothing new; top of the backlog after cycle 2.

**Shipped.** The session's own date is compared to the day it is read on,
and anything that is not today is marked above the numbers. Dates are also
shown the way a person writes them ("Sat 17 Oct") rather than as ISO.

Why it matters more than it looks: the newest file in the folder is chosen
by the date in its name, which is right, but nothing checked that the date
was *today*. Saturday morning with the sync not yet run would have shown
Thursday's weights under "carry these in" — the one way this screen can put
a wrong number in someone's head, and it would have looked completely
normal.

**Caught while building.** Two of my own edits were applied with
`str.replace` and no assertion, so they silently did nothing — one of them
left the header printing a raw ISO date while a test insisted it should be
formatted, which cost three runs to find. Every scripted edit in this cycle
now asserts the target text exists first. The failing test also disagreed
with the code about the locale; it now builds its expectation from the same
formatter rather than hardcoding a day name.

**Review caught — the cycle had not actually closed the hole.**

- `Ready` carried the freshness verdict as a field, computed when the file
  was read. `keepLastGood` then puts a sheet read on Friday back on screen
  on Saturday still carrying "this is today's" — the precise failure this
  cycle exists to prevent, now reachable through the one path whose job is
  to show an old sheet. A verdict stored beside the thing it judges can
  only get more wrong as it ages, so it is derived at render from the date
  on the line above it, and the field is gone.
- `Day.TODAY` as a default meant "forgot to pass it" rendered as "this is
  today's" — the one value of four that shows no warning, inverting the
  rule that an unreadable date is admitted rather than assumed. No default
  survives.
- Two of the four warnings shipped un-rendered by any test, and nothing
  asserted that today stays quiet. A warning that always shows is the
  cry-wolf failure, and this line borrows the borrowed-block warning's
  voice, so devaluing it costs more than itself. All four are covered now.
- I removed a locale-hardcoded assertion from one test and immediately
  reintroduced it in the new one: `"Sat 17 Oct"` fails on a German CI
  runner. Now asserts properties — that it stops being ISO and keeps the
  day — because the formatter uses the reader's locale on purpose.
- And the replacement assertion I was pleased with built its expectation
  from the function under test, so it would have moved with the very bug
  it was written for. It now asserts the header is *not* the raw ISO date.

**Not fixed, documented instead.** The file's date comes from the Mac and
"today" comes from the phone; travelling across zones can mis-report by a
day. Carrying a zone is the companion script's call, and a tolerance band
would blunt the signal. In DESIGN.md as a known limit.

**Next.** The Log screen — the write path, which must patch the original
JSON rather than re-serialise.

## 2026-10-11 — cycle 2: reading off the main thread

**From you.** Nothing new; top of the backlog from cycle 1's review.

**Shipped.** The folder read moved to `Dispatchers.IO` behind a
`SessionLoader`, state moved into a ViewModel, and a 15-second stall state
so a wedged provider cannot trap the app on "Reading" with no button.

**Review caught.** The async change had itself regressed the one thing the
app is for:

- Moving the initial state to `Loading` meant every Activity recreation —
  rotation, font size, multi-window, process death — drew "Reading" over a
  sheet that was already up, for as long as a blocked folder took. The
  synchronous version never showed it because `onResume` finished before
  the first frame. A ViewModel fixes it, and fixes the Activity leak in the
  same move: an abandoned read held the Activity as its Context.
- "One read at a time" was false. Cancelling a Job does not interrupt a
  blocking `ContentResolver` call, so the abandoned read keeps running and
  keeps holding the provider. The cancel is still right — it discards a
  stale answer that would otherwise land after a newer one — but the
  comment claimed an invariant the code does not have.
- The picker callback's read was always thrown away, because `onResume`
  runs after it and started a second read of the same folder. Removed.
- Two of my tests again asserted less than their names: one exercised a
  `keepLastGood` call production never makes, and the screenshot test for
  "unreadable" rendered one of the four states, so it could not have caught
  them being conflated. Rewritten to drive all four through one composition
  and assert they differ.

**Next.** Staleness — nothing compares the session's date to today, so an
un-synced Saturday shows Thursday's weights as what to carry in.

## 2026-10-11 — cycle 1: the folder picker

**From you.** "Consider this your project to build" — plus a set of cycles
tonight rather than a standing routine.

**Shipped.** A folder chosen once with a persisted SAF tree permission, the
day's session read from it, and Today rendered from a real file instead of
the sample. Screens for the three ways that can fail: no folder, a grant
that has gone, and a folder with nothing in it yet.

**Review caught** (fresh-eyes subagent, eleven findings, most of them real):

- `remember()` stored the Uri even when `takePersistableUriPermission`
  threw, so the app believed it held a folder it could not read — picker on
  every cold start, forever, with nothing to explain it. "Do we hold this
  folder" now asks `persistedUriPermissions`, which is the only authority.
- An empty folder was reported as a lost grant. The likeliest cause is the
  coach not having written today's session yet, so this told the owner they
  had lost a folder they had just correctly picked. Separated.
- Resuming while Syncthing replaced a file threw away the sheet on screen
  and offered only the picker. `keepLastGood` holds the last good session
  through an empty or unreadable refresh.
- `getTreeDocumentId` ran outside the guard, so a document Uri where a tree
  Uri belonged crashed on resume instead of falling back to the picker.
- Three of my tests proved nothing they claimed: one asserted `isTaskRoot`
  (true for any launched activity), one claimed to test persistence that
  Robolectric shadows away, one claimed to exercise a `SecurityException`
  path it never entered. Rewritten to claim only what they show.
- My headline round-trip test compared one field, so it would have passed
  while everything else was mangled. Widened — and it then failed, which
  found that re-encoding reformats `45` as `45.0` on top of dropping unknown
  fields. Hence the write path's design, now written down in the backlog.

**Next.** The main-thread read, then the Log screen.

## 2026-10-11 — the first device check, and what it caught

The Today screen shipped with its title drawing underneath the status bar.
`targetSdk` is 37, so Android enforces edge-to-edge and the app draws behind
the system bars; no insets were applied.

Worth writing down because of HOW it was missed. The Robolectric screenshot
test draws the composable with no system bars in the picture at all, so it
produced a clean, convincing image of a screen that was broken on a phone.
Machine-checkable tests are the trustworthy kind, but they are only as good
as what they model, and this one modelled a world without a status bar.

**Insets are a device-only check.** Every screen added here gets looked at on
a real phone before it is called done, and the fix pattern is: background on
the outer box so slate runs behind the bars, `safeDrawingPadding()` on the
content so nothing slides under the clock.

## 2026-10-11 — started

Carry exists because the thing it replaces could read too much. The previous
version of this coach collected workouts through Signal replies, which meant
the code that parsed "50, 8" was reading a log holding every decrypted
message on the device. Narrowing that read wasn't enough — the file is the
unit of access — so the reads were removed outright and the survey went with
them. A file-based client is what replaces it.

Two measurements from the old version set the priorities here:

- **A large share of what the owner reported never reached their history.**
  An all-or-nothing parser refused whole replies over one bad line, and
  most of the refused replies were fine but for that line. Typed fields
  exist to delete that whole class of loss.
- **Most logged entries were borrowed** from the other class's floor block,
  because intel for 3G is posted on a small minority of days. "Add
  something the sheet missed" isn't a convenience feature; on those days
  it is the session.

(Measurements are deliberately not quoted here — this repository is
public, and an aggregate fact about one person's training is still a fact
about them. The figures were handed to the owner to file with the data.)
