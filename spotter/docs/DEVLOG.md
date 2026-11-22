# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.

## Status

- **Built so far:** the design, and the coach's first piece: loads per hand with a count, and
  the rack's uneven steps.
- **Waiting on you:** installing the first debug build on your phone, once it's published.

## Sunday 22 November 2026

### The app, and it updates itself
- **Why:** madCode asked for automatic updating to be built into the app now.
- **Shipped:** the first Android app (`com.app.spotter`, beside Carry): an icon, a home screen
  with the version to copy, and self-update. Each merge to main publishes a debug build; the
  installed one checks on opening and every three hours, downloads a newer build, checks its
  size and SHA-256 against the feed, and installs it while Spotter isn't on screen, without a
  tap on Android 12 and later. Otherwise a blue box offers it.
- **For madCode:** install it once from the link in the README, then allow Spotter to install
  apps when its first update asks. `docs/DEVICE.md` says how to see it working.

## Saturday 21 November 2026

### The scorecard's shape
- **Why:** the export has to be what gym-coach's `scorecard.py` grades.
- **Learned:** from the session that wrote it, at madCode's ask: weights are per dumbbell, never
  a sum, compared by their place on the rack. Older history also holds per-dumbbell weights; only
  the count is missing.
- **Shipped:** an imported load with an unknown count now keeps its per-dumbbell weight, so it
  can step on the rack; it still has no total and converts to nothing. DESIGN.md has the
  export's shape.
- **Review caught:** stepping from an unknown count could double a weight if an old goblet "40"
  fed a two-dumbbell row; DESIGN.md now says such a load only feeds rows whose count it can't
  contradict, for the progression work to test.

### Spotter starts
- **Why:** madCode asked for a fresh app beside Carry, from the handoff now in
  [HANDOFF.md](HANDOFF.md), switched to only when it beats Carry on the scorecard.
- **Decided with madCode:** the name Spotter; a Garmin watch for the later watch view, so a
  Connect IQ app; the first run of cycles stops after build steps 1 to 3; the mockups for the
  sheet, "why", log and borrowed-board screens; each logged round starts as a copy of the one
  before.
- **Decided from the corpus:** Carry's reading of its 1,560 board rows drops words it doesn't
  understand on about one row in five and still names a plainer exercise, which is why numbers
  sometimes seemed to come from nowhere. So every word of a line must be accounted for, the
  sheet shows the board's own words, and new words go to a review list madCode answers once.
- **Shipped:** the design, the project's docs and CI, and `Load` and `Rack` in the coach, with
  trap 1 (per hand vs total) as a test.
- **Review caught:** converting 2×25 into one dumbbell gave 1×50, which on a single-arm row is
  twice the weight in one hand. Now the only conversion is a shared goblet dumbbell to two, and
  an imported load with an unknown count has no per-hand weight to step from.
