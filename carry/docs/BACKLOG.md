# Backlog

## Next

- **Move the folder read off the main thread.** `carryState` does a
  `ContentResolver.query` and a whole-file read synchronously in `onResume`
  and on every rotation. A sync-backed provider can be slow or blocked, and
  this is the resume path -- an ANR at exactly the "glance before class"
  moment. Top of the list.
- **Log** screen: weight, reps, easy, hurt, add-a-row; save back. The write
  path must **patch the original JSON**, not re-serialise the model: two
  tests in `SessionTest` show re-encoding is lossy (an unknown field from a
  newer runner is dropped, and `45` comes back as `45.0`). CLAUDE.md says
  unfilled rows survive a round trip untouched, and only patching can.
- **Staleness.** `SessionFiles.newest` picks the latest date in the folder
  and never compares it to today, so an un-synced Saturday shows Thursday's
  weights as what to carry in. The only hint is a small subhead. This is the
  one path that can put a wrong number in someone's head.
- Bundle Barlow Condensed and IBM Plex Mono; names are less compact than the
  approved mock until then.

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
