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
