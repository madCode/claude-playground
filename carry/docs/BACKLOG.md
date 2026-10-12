# Backlog

## Next

**Cycle 6 — don't lose the owner's typing.** Four findings from cycle 5's
review, all the same shape: the only thing in this app that cannot be
re-derived is what a person typed, and it is the least protected.

- **Process death loses the whole form.** Edits live in a ViewModel with no
  `SavedStateHandle`. Fill ten rows in the car, switch to Spotify, Android
  reclaims the process — all gone, and the tab is `rememberSaveable` so you
  land back on *After* looking at empty boxes. The likeliest real loss of
  the lot.
- **A newer file silently wipes the form.** Reseeding is keyed on document
  id, so tomorrow's sheet (or the day's second class) arriving mid-fill
  blanks everything typed, with no prompt.
- **A changed file under in-flight edits can write a number against the
  wrong exercise.** Same document id, new bytes: the edits were made
  against v1 and get patched into v2. `SessionPatch` refuses a *missing*
  ref but cannot see a renumbering. Needs a fingerprint of the bytes the
  edits were taken from.
- **Save races.** A save completing after a refresh overwrites the newer
  state; two taps in one frame start two writes that can undo each other.

**Also from that review, smaller:** `addedIn` silently drops rows it can't
parse and the next save deletes them; `encode` doesn't copy unknown keys
through on added rows, unlike the `sets` path, so a save with no edits can
rewrite the file.


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
