# Backlog

## Next

Nothing outstanding from a review. The four items that sat here are all
closed: index-keyed text boxes, a row written twice, a carried key
resurrected from a stale draft, and the forty-first added row vanishing.

What is left either needs the phone (see "Owed on a device"), needs a
decision that is yours (below), or is a feature from "Later" — of which
the notification on a new session file pays for itself soonest, because
the lobby glance still depends on remembering to open the app.

## From the security pass


**Yours to do, not mine:**

- **Ask GitHub Support to garbage-collect the old commits.** The aggregate
  facts about your training are scrubbed from `DESIGN.md` and `DEVLOG.md`
  going forward, and `CLAUDE.md` now has a rule so it doesn't recur — but
  they remain in this repository's history. A force-push would not fix
  that on its own: the old commits stay reachable by direct SHA URL until
  GitHub collects them, which only Support can trigger. It would also
  invalidate the four open Dependabot PRs and the `carry-debug` release
  tag, so it is not worth doing without the Support request behind it.

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

## Found by putting it on a device, with your real data

- ~~**Three rows all say "shoulder press"**~~ — **closed 2026-10-12, as
  not a problem.** The owner reframed it: what this screen is for is
  which dumbbell to pick up. Measured across the corpus, a sheet repeats
  a movement on 42% of days, and those repeats use the **same implement
  78%** of the time and agree on whether a weight is needed at all **84%**
  of the time — so a label distinguishing them would say nothing four
  times in five. In the 15% that are mixed, the sheet already separates
  them: on 2026-10-12 the two TRX shoulder presses read "no weight
  needed" and land under *No weight*, while the dumbbell one lands under
  *By feel*. The variant-label scheme is dropped. What was left was
  cosmetic — the same name printed twice in one line — and now collapses
  to `shoulder press ×2`.
- **A day with no history-backed weights is two tail lines and a lot of
  empty space.** Correct by the design — there is genuinely nothing to
  carry — but seeing it with real data is the first confirmation that on
  such a day the app's whole value is the log and the "add something the
  sheet missed" row. Worth knowing before judging the Today screen.

## Owed on a device

Things a JVM cannot answer honestly, to check on a real phone:

- A persisted tree permission actually surviving a cold start, and surviving
  `adb install -r` (which can revoke it and look exactly like a first run).
  (Partly done 2026-10-12 on an emulator: the grant was taken through the
  real picker and the folder held across a force-stop and relaunch. A
  phone and a reinstall are still owed.)
- Bottom-edge insets against the navigation bar with a long session.
- The round trip through Syncthing end to end.

## Later

- **A logo.** Owner asked 2026-10-12. Chalk on slate, and it has to work
  as a one-colour launcher icon at 48dp as well as wherever else it ends
  up; the app's own identity is already settled (Barlow Condensed, the
  amber that means exactly one thing), so this is drawing inside it.


- Notification when a new session file appears, so the lobby glance doesn't
  depend on remembering to open the app.
- Read the class from the phone's own calendar, which is what finally frees
  the companion script from needing a calendar config of its own.
- A test that can fail for the wrong reason: `CarryViewModelTest`
  occasionally dies in a full-suite run with "Dispatchers.Main is used
  concurrently with setting it". A `viewModelScope` job outlives its test
  and races the next `setMain`. Intermittent, so it reddens CI at random
  rather than reliably; the fix is cancelling the model in `@After`.

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
