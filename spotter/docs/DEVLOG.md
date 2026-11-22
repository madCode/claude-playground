# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.

## Status

- **Built so far:** the design; loads per hand on the rack's steps; reading a board line's
  count; naming its exercise, in full on 91% of Carry's board rows; the suggested weight from a
  log; the app, which updates itself; today's sheet on the phone; logging a class.
- **Waiting on you:** nothing.

## Sunday 22 November 2026

### Cycle 13: the logging rules, after their review
- **Why:** the review of the day's logging decisions found numbers going wrong; these are the
  ones in the coach that needed no answer from you.
- **Shipped:** a ladder logged with one weight holds instead of raising round one. A twinge
  holds the weight and always says so on the row, even as your first log of an exercise; the
  latest twinge counts, not the lowest. "Felt hard" waits a class before going up. Each rep
  count keeps its own history, so a board that swaps 8s and 10s moves each up by itself. Each
  logged row keeps the number the sheet showed, for the scorecard. Your phone's log is carried
  over to the new format.
- **Review caught:** my first version walked the number down on alternating rep counts and
  never back up; raised a ladder's weight when converting to other reps; lent a twinged lift's
  weight to its one-leg version; and left ladders already on the phone able to step up. All
  fixed and tested.
- **Left:** a class logged before this update and reopened after it won't show its rows as
  logged (their ids changed format). Nothing is logged on your phone yet, so it wasn't bridged.

### Research: what the rules rest on
- **Asked:** "shouldn't the science have an opinion?" on when a tick raises the number, and to
  check gym-coach's research list rather than trust it.
- **Shipped:** `docs/TRAINING.md`: eight topics, each with how sure it is, what it means for
  Spotter, and sources checked against the papers. gym-coach's 16 sources are all real; five
  citations were corrected and one dropped.
- **What it says, in short:** no trial settles when to go up; the consensus is two good
  sessions in a row, and reaching the top of a range first. "Felt easy" is trustworthy,
  "felt hard" weakly so. Converting between rep counts is a rough guess. The pain rules and the
  0.6 one-leg factor are choices, not findings.

### Your answers: the last board words
- **Asked:** ten words the boards use that Spotter couldn't read. You answered all ten.
- **Shipped:** "hand on bench", "foot on bench" and "feet in straps" are setups, kept apart from
  the same move without them. Tap squat jump, single leg hop, split squat hop (same lead leg),
  bent arm lateral raise, straight arm sit-up (arms straight overhead, as in your video), overhead
  reach (one arm or both, so neither is recorded) and the reach after a chest press are moves of
  their own. Rows named in full: 1,379 to 1,404 of 1,546 (91%). A "reach" anywhere but after a
  chest press stays "not sure": the review caught "high plank alt lateral reach" being read as
  the press's reach.
- **Also:** a row logged from a borrowed board carries over to your own board's row with the
  same exercise, your call; it's built with the Log screen's second half.

### Your answer: one set short
- **Asked:** with one set logged, should one rep short drop the next number? You chose "hold
  unless three or more short".
- **Shipped:** a single logged set has no first set to miss. One or two short holds the
  weight, three or more drops a step. With a twinge as well, the weight is capped at what you
  held, not a step under it. Several sets logged are judged as before.
- **Also:** `scorecard.py` grades Spotter's example export (exit 0, numbered and close 67%, as
  expected), so the export's shape is settled.

### Cycle 12: the Log screen
- **Why:** step 3's centre: what was done, logged in the car in under a minute.
- **Shipped:** "Log this class" under the sheet. Each row is "✓ as listed" or "changed": the
  heaviest dumbbell, one or two, and the reps, with felt easy or hard, twinge, had to stop and
  easier version one tap away. Save keeps it in the log, and the next class's numbers come from
  it. Reopened, the screen shows what you logged, and saving again only writes what changed.
  "+ Each round" and "Add an exercise" are next.
- **Review caught:** the class moved on to tomorrow's an hour after it started, so it could
  never be logged in the car (now three hours); a screen opened on an out-of-date log could have
  removed rows saved since; a save could be seen half-written; a number with no count couldn't
  be given one; the steppers' + went off screen at large text; TalkBack didn't say which row a
  "twinge" was for; and the next class replacing this one mid-log would have saved the rows
  under its date. All fixed and tested. Rows logged on a board since replaced can't be cleared
  yet: in BACKLOG.md.
- **For you:** one question in BACKLOG.md's "Your call": one rep short on the one set logged
  drops the next number a step.

### Cycle 11: ready for the Log screen
- **Why:** the three things cycle 10's review said the Log screen needs first.
- **Shipped:** each row on the sheet has an id that stays put when the kept board is swapped for
  the fetched one, or the board is tidied after posting, so a set you log lands on the row you
  did. "New: go light" is now a fact the app knows, not words it matches. What Spotter read and
  why the number, the two lines logging shows back, now have their own tests. Nothing on
  screen changes.
- **Review caught:** the first id took the line's raw text and the block's full heading, so a
  fixed capital or a changed "12.5 minutes" would have orphaned logged sets. Fixed and tested.
  Two smaller cases are left for the Log screen (BACKLOG.md).

### Cycle 10: the log, and a review of the whole
- **Why:** step 3 starts: classes have to be kept before they can be logged on screen.
- **Shipped:** the event log on the phone. It only ever grows: a correction or a removal is a new
  entry beside what it changes, and each logged row keeps the exercise it was read as, so a
  later change to the word lists never relabels your history.
- **Review caught:** one damaged stored row would have stopped the whole log from reading, and
  a weight no dumbbell has stopped the coach; a ladder had no stated unit, so a total stored
  where reps a side were meant would have counted every set as a miss; unwritten entries could
  collapse into one; and a change to the key's format would have made all history vanish. All
  fixed and tested.
- **The architecture and test review** (due around now) found a real bug, fixed here: a ladder
  written in totals ("6 / 8 / 10 total") was read as 6 a side, not 3. Its other findings are in
  BACKLOG.md: three to do before the Log screen, the rest for a tidy-up.

### Cycle 9: the sheet on the phone
- **Why:** the end of step 2: the glance before class.
- **Shipped:** the home screen now shows the next class's sheet: its date and start, notes when
  the board is borrowed or from an earlier day, warnings first, then each row with the board's
  words, what Spotter read, and the number. A tap on a row says why. Spotter asks for the
  calendar once, on first open. **Step 2 is done.**
- **Review caught:** a rotation or a cold start blanked the sheet until the fetch finished, up
  to about 45 seconds on weak gym signal; the board kept on the phone now shows at once and the
  fetch refreshes it. Also: "Allow the calendar" lingered after it was allowed; dismissing the
  dialog once sent the next tap to settings; two loads could write one file; a long reason
  squeezed the board's words to a sliver at large text; the outage note said "today's" for
  tomorrow's class; "why" on a new row said nothing; and TalkBack read "▲" as a triangle. All
  fixed and tested.
- **For you:** after this update, open Spotter and allow the calendar; DEVICE.md says what to
  check. Until logging (step 3), weighted rows say "new: go light".

### Cycle 8: today's board on the phone
- **Why:** step 2 starts: the phone has to know which class is next and have its board.
- **Shipped:** the phone fetches the day's board from the public feed and keeps a week of them;
  when the day's can't be fetched it falls back to the last one kept, marked with its date. The
  class comes from the calendar's next booking, and a class with no board borrows the other
  format's, labelled. Checked against the real feed: yesterday's file reads, today's isn't
  posted yet.
- **Review caught:** every evening, tomorrow's board isn't posted yet, and today's would have
  shown as tomorrow's, marked as an old board; a "not posted yet" is now told apart from an
  outage. Also: a class with no format ("Tread 50") let tomorrow's class show in its place; an
  empty feed file hid the last good board; cancelled and declined bookings counted; and the
  all-day and timed calendar entries had no test. All fixed and tested.
- **For the next cycle:** the screen, and the one-time calendar permission prompt. Until then,
  nothing on the phone changes.

### Cycle 7: the scorecard export
- **Why:** the switch from Carry is decided on the scorecard, so Spotter has to write what it
  grades. The last item of step 1.
- **Shipped:** a session file per class: each row's suggested and lifted weight per dumbbell,
  the count apart, null where the sheet had no number, and blank rows not counted as skipped.
- **Review caught:** the first version only borrowed some of Carry's field names, with its own
  schema, `reps` as a list and no `sheet_id`, so a grader written for Carry's files could skip
  or crash on it. It is now Carry's format field for field. Also: a set could be logged
  against another row, and the example file could drift from the code. Both fixed and tested.
- **Step 1, the coach core, is done.** Next: the phone shows today's sheet (step 2).
- **For you:** one check only you can do. Run `scorecard.py` on `docs/scorecard-example.json`, an
  invented class, and say whether it grades.

### Cycle 6: the sheet
- **Why:** the last piece of the coach before the phone can show today's numbers.
- **Shipped:** `Sheet`, from a class's board lines and the log to the rows the glance shows: the
  board's words, then the number with ▲ ▼ =, a test weight on a big jump, or "no weight", "new:
  go light", or "?" with the reason on top. Combos take the lightest of their parts done the
  same way; push-ups, planks and bodyweight lines want no weight.
- **Review caught**, on the corpus and the feed: a combo took its heavier part's number when
  the lighter part had none, or hurt, or was held differently ("deadlift to high pull" at the
  deadlift's weight); TRX, holds, pulses and unflagged bodyweight moves (pull-up, v-up) wanted a
  weight, while a single-arm press with a mini band didn't; a nested "Back-to-back complex:"
  heading lost its block; "45 sec base", "30 sec rest", stroke-rate and "member choice" lines
  showed as rows; a change of reps showed ▼; and a curl on one leg borrowed 0.6 of a curl. All
  fixed and tested. The first recovery rule also hid "30 sec of active recovery: bird dog"; it
  now takes only a whole line of recovery.
- **Found on a real board:** the post's title, "90 sec recovery", "1 min to transition to the
  rower" and lone backslashes were rows. Now they're not, and Carry's 1,546 exercise rows are
  unchanged.

### Cycle 5: the suggested weight
- **Why:** the first piece that puts a number on a row; madCode asked for faster progress on
  step 1, so progression came before the corpus grade.
- **Shipped:** `Progression`, from a log of sets to a weight with its reasons and ▲ ▼ =
  against last time. Up a dumbbell after a clean session, hold in range or after a near miss,
  down after a miss; a set that hurt never raises a number; a later miss caps an older
  success; Epley only within 3 to 12 reps; a test weight when the next dumbbell is a big jump;
  a one-legged version borrows 0.6 of the two-legged lift, said in its reason.
- **Review caught**, on invented logs: a set that hurt and was short didn't step down; a
  conversion to fewer reps lifted the number past what hurt; a test weight was offered at what
  hurt; a session with no reps logged counted as clean; timed and counted sets converted into
  each other unchecked; ladders couldn't be judged; and a miss at the lightest dumbbell said
  "down a step". All fixed and tested. Left for the sheet: an imported load with an unknown
  count feeding a row whose count it contradicts.
- **Next:** the sheet, which feeds each row's history in, then more borrowing factors.

### madCode's answers on the board words
- **Why:** madCode went through the words cycle 4 couldn't sort, in chat.
- **Shipped:** each answer as a word in the lists, or a rule for words that only mean something
  on certain movements or after certain words. Named in full: 1,276 of Carry's 1,546 rows before,
  1,379 (89%) after; rows with an unknown word: 209 before, 98 after.
- **Learned from the boards:** "anchor" is the move a circuit comes back to between the others,
  not a partner; it changes nothing about the exercise.
- **Review caught:** "switch" as filler made a switch lunge (a jumping lunge) a plain lunge, now
  only "switch alt" is; "with rotation" after a plank or deadbug became a torso rotation; a
  "press" after a Y-raise became a shoulder press (the Y-raise was filed as a lunge); "single arm
  squat to press" lost "single arm" on the press; and a combo's "hands on bosu" overrode a squat's
  own "bosu". All fixed and tested.
- **Changed:** rows with "feet on bosu" now name it, so they are no longer the same exercise as a
  line that only says "bosu".

### Cycle 4: words the boards use
- **Why:** a row with an unknown word gets no number, and 311 of Carry's 1,546 exercise rows
  had one.
- **Shipped:** 22 exercises whose meaning is plain from the boards ("tap squat", "step-out
  squat", "squat jack", "tricep push-up", "windmill"), each its own exercise so it borrows no
  number; "rear foot on bench" as rear foot elevated; "moderate" as a hint; "buy-out" as a label; and "recover when done until
  next round begins" taken off. Named in full: 1,191 rows before, 1,276 (83%) after; rows with an
  unknown word: 311 before, 209 after.
- **Review caught:** "shoulders on bench" read as a flat bench on a press, and "feet in straps"
  as a TRX push-up, which puts hands in the straps; both now stay unknown. Also "band walk"
  filed as a lateral walk, and labels matched inside longer words. All fixed.
- **Left for you:** words whose meaning isn't plain, like "tap" alone, "to stand", "balance",
  and "feet on bench" (which could be either of two climbers). They're listed with their board
  lines in BACKLOG.md.

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
