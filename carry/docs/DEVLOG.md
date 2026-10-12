# Devlog

## 2026-10-11 — started

Carry exists because the thing it replaces could read too much. The previous
version of this coach collected workouts through Signal replies, which meant
the code that parsed "50, 8" was reading a log holding every decrypted
message on the device. Narrowing that read wasn't enough — the file is the
unit of access — so the reads were removed outright and the survey went with
them. A file-based client is what replaces it.

Two measurements from the old version set the priorities here:

- **About a third of what the owner reported never reached their history.**
  Three replies were refused by an all-or-nothing parser; in two of them,
  every line but one was fine. Typed fields exist to delete that whole class.
- **78% of logged entries were borrowed** from the other class's floor block,
  because intel for 3G is posted 3 days in 31. "Add something the sheet
  missed" isn't a convenience feature; on those days it is the session.

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
