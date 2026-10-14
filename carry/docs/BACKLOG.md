# Backlog

The running plan. Each cycle picks what matters most right now — device feedback
first, then what the audit turns up — builds it with tests, and ticks it off.
Feature proposals are ideas, not commitments.

## Next

Grouped by part of the app. The tag says where each item came from: *(you asked)*
(the owner), *(device)* (real-phone testing), *(audit)* (the security pass or the
weekly coach audit), *(design)* (has a design doc in
[docs/designs/](designs/) — what it is, why, what makes it non-trivial, and how
to know it worked; written 2026-10-14 so a session can start without re-deriving
the analysis).

### Screens
- [ ] **Open a past session from the Since list** — no screen answers "what did I
  lift that day" *(you asked, design:
  [open-a-past-session](designs/open-a-past-session.md))*
- [ ] The bars on *Heaviest by what it trains* encode every weight in that family
  with no scale and no time direction; the bar count varies by how many sets the
  family has, which reads as meaning something it doesn't. Propose cutting them
  and keeping the number *(you asked)*
- [ ] *Every session* answers no question a person would ask. Either each row
  opens that session (above) or the band goes *(you asked)*

### The lobby glance
- [ ] **Notify when a new session file lands** — the glance still depends on
  remembering to open the app. Forces a decision: the app currently declares no
  Android permissions at all *(design:
  [new-session-notification](designs/new-session-notification.md))*
- [ ] **Read the class from the phone's own calendar** — frees the coach from its
  own calendar config. Bigger than it looks: needs a protocol change agreed on the
  runner side first *(design:
  [read-class-from-calendar](designs/read-class-from-calendar.md))*

### Tests and CI
- [ ] **The flaky ViewModel test** — `CarryViewModelTest` dies at random in a full
  suite with "Dispatchers.Main is used concurrently with setting it": a
  `viewModelScope` job outlives its test. It reddens unrelated PRs *(design:
  [flaky-viewmodel-test](designs/flaky-viewmodel-test.md))*

### Identity
- [ ] **A logo** — chalk on slate, and it has to work as a one-colour launcher
  icon at 48dp *(you asked, design: [logo](designs/logo.md))*

### Owed on a device
Things a JVM cannot answer honestly, to check on a real phone.
- [ ] A persisted tree permission surviving `adb install -r`, which can revoke it
  and look exactly like a first run. Partly done 2026-10-12 **on an emulator**: the
  grant was taken through the real picker and held across a force-stop and
  relaunch. **A phone and a reinstall are still owed** *(device)*
- [ ] Bottom-edge insets against the navigation bar with a long session *(device)*
- [ ] The round trip through Syncthing, end to end *(device)*

## Feature proposals

Ideas worth doing, not yet planned. Each gets a sketch before it moves to Next.

### Quick-add from the lobby
Nothing exists for "the sheet is wrong and I know it now". Adding a row currently
means the After tab, which is a form built for afterwards. Unsketched.

## Done

- [x] The four review items that sat in Next: index-keyed text boxes, a row written
  twice, a carried key resurrected from a stale draft, and the forty-first added
  row vanishing
- [x] ~~Three rows all say "shoulder press"~~ — closed 2026-10-12 **as not a
  problem**. The owner reframed it: this screen is for which dumbbell to pick up.
  Across the public sheet corpus a repeated movement uses the same implement 78%
  of the time and agrees on whether a weight is needed at all 84% of the time, so
  a distinguishing label would say nothing four times in five. The variant-label
  scheme is dropped; what was left was cosmetic and now collapses to
  `shoulder press ×2` *(device)*
- [x] The prescription on every row — `10 total x`, `45 sec of` — which the Log
  screen never showed, so a 30-second hold and a 90-second one looked identical
  (#60)
- [x] The intel post, with typed weights flagged in the preview before sending (#60)

## Notes

**A day with no history-backed weights is two tail lines and a lot of empty
space.** Correct by the design — there is genuinely nothing to carry — but seeing
it with real data is the first confirmation that on such a day the app's whole
value is the log and the "add something the sheet missed" row *(device)*.

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

## Yours, not mine

The items in "From the security pass" above, and one decision the coach is
waiting on: **whether a bump may count across a movement pattern.** Pattern is
the only grouping where anything in the history reaches three, but `squat` holds
lateral shift and `lunge` holds step-up, so a bump earned on one could name a
weight for the other. The middle option on the table is to let a pattern count
toward the streak while the bump still fires only on a movement already done.

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
