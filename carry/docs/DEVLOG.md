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
