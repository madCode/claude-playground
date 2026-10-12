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
