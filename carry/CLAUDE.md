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
- Session files are the only I/O. No network, no account, no database that
  outlives a session file; if that stops being true, something has gone wrong
  with the design rather than grown.

## Commands

    ./gradlew build koverVerify          # everything CI runs
    ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'   # PNGs in app/build/screenshots

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
Tests, screenshots and sample session files use invented exercises and
weights. Real workout text comes from r/orangetheory via the companion
script and stays on the owner's machine; none of it is committed here.
