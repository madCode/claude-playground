# Backlog

## Next

**From cycle 12's review, with repros:**

- **Added-row boxes are keyed by list index**, so they show the previous
  occupant's text after the list shrinks. Tap "+ Add" twice, fill only
  the second (`plank`, 30), save: the blank row is dropped, `plank` moves
  to index 0, and the weight box next to it renders empty while the file
  holds 30. The screen says "Saved" and shows a number that isn't there —
  the re-enter-from-memory failure. Needs a stable id per added row
  rather than its position.
- **A carried key can be resurrected from a stale draft.** `asRead` is
  pinned to the bytes of the first keystroke; if the file is rewritten
  underneath and the writer removed a key, saving puts it back. Narrow —
  it needs the rewritten-file path and a key that was deleted remotely —
  but the copied-through value should come from the row as it is on disk
  now.

Otherwise nothing outstanding from a review. What is left either needs the phone
(see "Owed on a device"), needs a decision that is yours (below), or is
a new feature from "Later" — the notification on a new session file is
the one that pays for itself soonest, because the lobby glance currently
depends on remembering to open the app.


## From the security pass


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

## Owed on a device

Things a JVM cannot answer honestly, to check on a real phone:

- A persisted tree permission actually surviving a cold start, and surviving
  `adb install -r` (which can revoke it and look exactly like a first run).
- Bottom-edge insets against the navigation bar with a long session.
- The round trip through Syncthing end to end.

## Later

- Notification when a new session file appears, so the lobby glance doesn't
  depend on remembering to open the app.
- Read the class from the phone's own calendar, which is what finally frees
  the companion script from needing a calendar config of its own.
- Compose the 3G intel post for Reddit and hand it over to paste — the only
  path that gets a 3G session into history at all, and it helps the people
  in the later classes.

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
