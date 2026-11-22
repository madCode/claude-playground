# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.

## Status

- **Built so far:** the design, and the coach's first piece: loads per hand with a count, and
  the rack's uneven steps.
- **Waiting on you:** installing the first debug build on your phone, once it's published.

## Sunday 22 November 2026

### Cycle 3: naming the exercise
- **Why:** the number for a row is only as good as knowing which exercise it is; this is where
  Carry turned "floor close grip chest press" into a bench press.
- **Shipped:** identity. Of Carry's 1,546 exercise rows, 1,191 (77%) are named in full; the
  other 355 are "not sure", with the reason: 311 have a word no list holds yet (madCode's
  review list), 34 put two movements side by side ("bench bird dog low row"), and the rest are
  choices, clashes or no movement. A floor close-grip chest press is now its own exercise.
- **Fixed on the way:** four kinds of row were refused wrongly and now read: "pulsing half
  squat" (half is a range, not a tempo), "dumbbell mini band bridge" (a band is worn, not held),
  "tricep extension lying on bench", and "bridge hold with chest press".
- **Review caught**, on the corpus: a combo's implement went to one part only, so the curl in
  "TRX high row to bicep curl" read as a dumbbell curl; "single leg hold with shoulder press"
  read as an isometric press; bare "close" and "narrow" read as a grip on a squat; "dbs" and
  "dumbbells" meant different counts. All fixed and tested. Left for the corpus grade: pairs
  of keys for one exercise ("dumbbell lunge" and "lunge"), and refusals that a richer
  `position` would read; both in the backlog.

### Cycle 2: reading what a line asks for
- **Why:** every number needs the line's reps first, and three traps live here: "12 total" vs
  "6 each" (7), ladders (8), and prose read as an exercise (5).
- **Shipped:** the line reader. On Carry's 919 distinct board lines it reads a scheme from all
  but 9, and those 9 have none ("burpee", "seated low row … until time is called"). It keeps 5
  lines out as instructions or headings ("Goal: match or beat your reps…", a run recap).
- **Review caught**, on the corpus: a trailing "(total)" ignored (trap 7); recap paragraphs
  that start with a number kept as exercises; "45 sec to do 8 x …" losing its reps; rower
  lines ("600 m row") becoming dumbbell rows; bulleted lines losing their scheme; a side lost
  before a timed rest. And on hostile input: a 2 KB line that overflowed the stack, a line of
  commas that took minutes, and numbers too big for an Int. All fixed and tested.

### The app, and it updates itself
- **Why:** madCode asked for automatic updating to be built into the app now.
- **Shipped:** the first Android app (`com.app.spotter`, beside Carry): an icon, a home screen
  with the version to copy, and self-update. Each merge to main publishes a debug build; the
  installed one checks on opening and every three hours, downloads a newer build, checks its
  size and SHA-256 against the feed, and installs it while Spotter isn't on screen, without a
  tap on Android 12 and later. Otherwise a blue box offers it.
- **Review caught:** CI would have failed every run (WorkManager not set up in the test, with
  the run number CI always has); two runs could share one download file, so the checked bytes
  weren't the installed ones; the app could be replaced mid-use when opened during a download;
  three dropped connections blocked a build; on Android 8 to 11 the whole build was downloaded
  every three hours while waiting for a tap; the box didn't appear on the open that found the
  build; and CI could publish an older build over a newer one. All fixed and tested. Left: the
  installer test checks a session is made, not its settings, which only a phone shows.
- **For madCode:** install it once from the link in the README, then allow Spotter to install
  apps when its first update asks. `docs/DEVICE.md` says how to see it working.
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
