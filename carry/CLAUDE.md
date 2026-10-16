# Carry

An Android app for logging Orange Theory workouts, for someone who can't take
their phone into the studio. It reads a session file a companion script
writes, shows the few weights worth carrying in, and takes back what was
lifted. Read `docs/DESIGN.md` before changing behaviour; `docs/DEVLOG.md` is
the log of what changed and why. It lives in `carry/` of the claude-playground
repository; run commands from this folder. The rules every project shares are
in the root CLAUDE.md.

**The one rule: Carry never invents an exercise's identity.** Rows arrive
from the session file with their canonical name, components and variant
already attached, and go back with numbers filled in. Only a row the owner
adds by hand carries a typed name, and that row is flagged unmatched rather
than guessed at. The thing this replaces lost about a third of what was
reported by re-deriving identity from free text.

**And a blank row is blank** — never recorded as "skipped". A wrong number in
a training history is worse than a missing one, because nothing downstream
can tell the difference.

## Layout

- `app/`: Android, Jetpack Compose. The two screens, the folder picker (SAF),
  and reading/writing the session file.
- Session files are the only I/O for the owner's data, with one
  deliberate exception: after a save, Carry can hand a composed text post
  to the share sheet. It keeps no account — the owner's own app does the
  posting, and the text is shown in full before it goes.
- The network is for what the owner has approved, one use at a time:
  today, a CI debug build asking GitHub whether a newer build is out
  (`DebugUpdates`, #107), sending nothing of theirs. Fetching the board
  from Reddit is expected to follow, when the brain moves to the phone.
  Anything else that reaches off the device means something has gone
  wrong with the design rather than grown.
- No account, and no database that outlives a session file.

## Commands

    ./gradlew build koverVerify          # everything CI runs
    ./gradlew :app:testDebugUnitTest --tests '*ScreenTest'   # PNGs in app/build/screenshots

JDK 21 and the Android SDK are required; `../jekyll-poster/tools/install-android-sdk.sh`
installs the SDK for a cloud session.

## The session file

Carry does not own the schema — the companion script does, and extending it
here produces fields the importer silently drops. The runner writes each set
with its identity and `weight: null`; Carry fills those in and saves the file
back. Unfilled rows must survive a round trip untouched, because a session
can be finished in two sittings.

## Privacy

The repository is public and the owner's training data never belongs in it.
**That includes aggregate facts about it** — how many classes a week, what
share of entries are borrowed, how many times a marker appears. No weights,
dates or identity is not the test; the test is whether a stranger learns
something about the owner's training from the sentence. Reasoning that
needs a figure says "most" or "a small minority"; the figure itself belongs
with the data, outside any repository. Note that `$GYM_DATA_DIR` is
read-only to Claude by design, so a figure worth keeping gets handed to the
owner to file rather than written there.
Tests, screenshots and sample session files use invented exercises and
weights. Real workout text comes from r/orangetheory via the companion
script and stays on the owner's machine; none of it is committed here.
