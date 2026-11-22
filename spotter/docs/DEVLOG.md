# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.

## Status

- **Built so far:** the design; loads per hand with a count on the rack's uneven steps; the
  starting lists of movements and the words around them.
- **Waiting on you:** nothing.

## Saturday 21 November 2026

### Cycle 1: the starting lists
- **Why:** reading a board line needs to know every word it may hold, and Carry's list had
  variations baked into names and different exercises filed as aliases, which is where the
  mystery numbers came from.
- **Shipped:** 99 movements (true synonyms only, one-legged ones marked) and the closed lists of
  variation words by axis (implement, arms, legs, position, grip, tempo, band, stability,
  direction, hold), connectors, rep-scheme words and filler. A phrase can mean only one thing.
- **Measured:** a longest-phrase-first read of Carry's 1,560 board rows with these lists leaves
  no word over on 79% of rows (96% of words, counting numbers as words). That says the lists
  are wide enough, not that every row is read right; the corpus grade will say that.
- **Review caught:** some lines read fully and wrongly. In the lists: bare "wide" (a stance on
  squats, a grip on curls), "alt" filed as an arm count though it mostly alternates legs,
  "2 dumbbell" missing, aliases that hid a variation (Bulgarian split squat, bare fly, full
  v-up), prone and supine merged, and board text's word joiners and `&amp;`; all fixed. For the
  reader, still to build: two movements side by side with no connector, and "with" before an
  implement; DESIGN.md now lists them.
- **Before this, three scheduled runs built nothing:** they started without push access to the
  repository. The schedule now runs in the setup session, which has it.

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
