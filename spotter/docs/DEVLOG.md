# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.

## Status

- **Work started again on 23 November**, in chat with madCode, after the run closed that
  morning. Nothing is scheduled.
- **The finding that matters most:** on realistic boards Spotter is mostly silent. An invented
  lifter working through the board corpus got a number on 110 of 361 weighted rows, because a
  reworded repeat ("floor pullover" after "pullover") was treated as new. The first fix is
  in (128 of 361); the rest needs factors from madCode (BACKLOG, step 5).
- **Built:** steps 1 to 4 in full. Step 5 has shared weights for wordings that don't change the
  weight. Step
  6 has the Progress card, the advice counts and the read-only list of unknown words.
- **Built from madCode's answers today:** count buttons for a range, shared weights, the Epley
  "try" offer, the eight-week limit, and four fixes to the instructions. Sharing a board waits
  on what it should send.
- **Waiting on you:** how sorting a word should work; and with the phone (DEVICE.md), choose a
  backup folder, import your history file, and the hand checks.
- **Not started:** step 7, the Garmin watch.

## Tuesday 24 November 2026

### A lift nobody counted takes its usual number
- **Why:** someone importing a history with no counts file of their own had every unsaid
  count left unknown, which blocks the one-arm and alternating links. madCode: the app
  should start from a guess.
- **Shipped:** ten movements carry the number they are usually done with (two for a deadlift,
  Romanian deadlift, chest fly, lateral raise, upright row, high row, tricep kickback; one
  for a tricep extension, good morning, halo). It is the last word, after the line, the row
  and the owner's counts file, and the home screen says how many rows rest on it.
- **The review caught:** the guess was applied to every wording of the lift, so a sumo
  deadlift read as two dumbbells and doubled its goblet version. Now only the plain lift, and
  never where the owner's file has a rule for it.
### Three moves named from coaches' videos
- **Why:** "TRX bridge row" and "side plank pendulum with kickstand" read as "not sure".
  madCode: look them up as Orangetheory coaches show them.
- **Shipped:** "TRX bridge row" (a row on the straps with the hips held up: one move; a bare "bridge row" stays unread) and "side
  plank pendulum" (a side plank with the top leg swinging; "with kickstand" is the easier
  support). Both are done with nothing held, so the sheet says "no weight".
- **Also:** "cross carry to halo" reads as the coaches' "cross catch halo" (one weight brought
  up with one hand, caught at the chest with the other, circled round the head). No coach
  source uses the board's name; madCode recognised the move from its description. It is its
  own exercise and borrows nothing from a plain halo.
### A step earned long ago is held once first
- **Why:** two clean sessions at a weight, then months without the lift, and the sheet still
  said to go up. madCode: hold.
- **Shipped:** when the lift was last done more than eight weeks before the class, a step up
  isn't taken or offered as a test; the sheet holds the weight last done and says how long
  it has been. A typed-in row and the replay follow the same rule.
- **The review caught:** the gap was measured from the session the number rests on, so
  someone doing the lift weekly at other reps was held; the reason promised "then up" where
  a twinge, a big jump or other reps decide otherwise; a typed-in row didn't get the date.

### Lifts filed under different names lend each other a first number
- **Why:** a reverse lunge after months of forward lunges said "new: go light". madCode had
  agreed the set: forward and reverse the same, lateral at 0.8, a split squat at 0.6.
- **Shipped:** an estimate across those movements, by the same rules as a tweak's: what was
  held, never a step up, nothing from a twinge, and the lift's own other wording first.
- **On the yardstick:** 170 of 361 rows with a number (167 before).

### The dumbbell counts file is picked on the phone
- **Why:** the import read the counts file only in the replay tool; the phone's history still
  had rows with no count, which blocks the one-arm, alternating and goblet links.
- **Shipped:** "Add your dumbbell counts" on the home screen, beside the history. The file is
  kept with the history, the history is read with it, and the card says how many lifts it
  rules on and how many rows took their count from it.
- **Due by hand:** DEVICE.md, importing a history, step 8.
### Board lines the reader couldn't name

A row whose line reads as "not sure" never reaches the coach, on the sheet or from an imported
history. A batch of such lines from the boards now reads:

- "step-down squat" and "step-down reverse lunge" are movements of their own (a step off the
  bench into the squat or lunge), not two movements side by side.
- "low bench high plank" and "bench high plank" are positions, not a bench and a plank in
  conflict. "plank up-down" ("up and down", "up & down") and "frogger" are listed.
- "dumbbell goblet …" is a goblet hold: goblet says how the dumbbell is held.
- "clean to press" is the clean and press. "rear foot on bosu" is a stability.
- A small count right after "to" in a combo ("to 2 x calf raise") is passed over: it says how
  often, not what. Anywhere else a count still leaves the line unread.
- **Still a gap:** "dumbbell sumo squat to frogger" reads but gets no number from the squat,
  because the combo's dumbbell is put on the frogger too. Taking it off changed the key of
  combos already logged on the phone, so that needs its own change.
- **The review caught:** the first version dropped any count in the words, so "3 x 8 x squat"
  read as three reps and got a heavier number, and "squat and 8 x deadlift" read as one combo.
  Its second look caught the frogger fix orphaning logged combos, which was taken back out.

Left unread, because the boards don't make them plain: "cross carry", "pendulum with
kickstand", "TRX bridge row". On the whole corpus 46 strings that were "not sure" now read and
no reading that existed changed.

### A band doesn't decide whether a row takes a weight
- **Why:** "mid band upright row" showed "no weight", read as a band with nothing held. madCode
  sent a picture of a banded row: the band is round the wrists, pulled apart, across the lift.
- **Shipped:** a banded row takes a weight exactly when the same row without the band would,
  and the two lend each other their number as an estimate. A lateral walk in a band stays
  weightless (madCode doesn't recall holding anything for one).
- **On the yardstick:** 165 of 361 rows with a number (158 before).

### The owner's dumbbell counts file
- **Why:** the private grading found the rows still blank or low mostly carried "(count?)": the
  history says how many dumbbells only for some movements, and "alternating", one arm and the
  goblet link all need it. madCode ruled on their own lifts with the gym-coach session, in a
  private file beside the history.
- **Shipped:** the import and the replay tool read that file (`-Pcounts=…`), filling a count
  only where neither the line nor the row says one. Tested on invented values; the format is
  in COUNTS-FORMAT.md.
- **Next:** picking the file on the phone, beside the history.

### The Progress card names exercises in words
- **Why:** madCode's phone showed "chest fly [position=floor]" and "lateral shift
  [implement=one dumbbell, both hands]": the key an exercise is filed under, not its name.
- **Shipped:** "floor chest fly", "goblet lateral shift", "single arm split stance high row".
  The reasons under "Why this number" use the same names.
- **The review printed the name of every exercise in the corpus** and found the first try
  wanting: "hold squat", "stick bicep curl", "high plank plank", and a combo saying "single
  arm" before each of its parts. Rewritten in the order a person says it.

### Harder tweaks start lighter, and one dumbbell against two
- **Why:** madCode approved the rest of the tweaks after a research round on which of them
  "the same weight" would be clearly wrong for.
- **Shipped:** a first go on a bosu, with a slow lower, a pause or a pulse, or in a staggered
  stance starts at 0.8 to 0.9 of the plain lift, rounded down. A grip, a decline, kneeling or
  a kettlebell start at the same weight. One dumbbell in both hands starts at 1.5 of one of
  the pair on a squat, lunge or tricep extension, and at 2 on a deadlift.
- **On the yardstick:** 158 of 361 rows now have a number (140 before).
- **The review ran every corpus pair again** and found twice-the-pair applied to every hinge
  (a good morning, a swing), a goblet version in the log blocking a nearer plain one, a y-bell's
  26 lb landing on a dumbbell row, a hammer curl's weight on the plain curl, and kneeling
  carried onto a push press. All fixed; kettlebells and y-bells take no estimate for now.
- **Research:** TRAINING.md, 11 and 12. About half the factors have a study behind them; the
  rest are reasoned, and lean light.
- **Not built:** "reverse grip" on these boards means palms up, the ordinary curl, so the
  factor found for a palms-down curl has nothing to apply to.

### A tweaked lift takes an estimate
- **Why:** madCode, going through the blank rows one question at a time: "I'm very rarely going
  to want to say 'new: go light' if it's literally the same exercise with minor tweaks."
- **Shipped:** a lift never logged in these words takes the weight its nearest logged version
  last held, shown as "est.": one arm, "explosive", a band, sumo, incline. One leg's 0.6 shows
  "est." too. "Overhead tricep extension" shares a history with the bare one outright.
- **On the yardstick:** 140 of 361 rows now have a number (128 before).
- **The review ran every corpus pair through it** and found a weight held in both hands could
  land in one arm, one arm's weight doubled onto a lunge, the later of two near versions winning
  over the lighter, and a conversion lending more than was ever lifted. All fixed, at the cost
  of six rows.
- **Research:** sumo against a regular stance is in TRAINING.md, 10: the same weight, and no
  reason to try heavier.

## Monday 23 November 2026

### A heavier weight vouches for a lighter one for eight weeks
- **Why:** after 30 clean, a clean 25 went straight back to 30 however long ago the 30 was.
  madCode: add a limit, six to eight weeks.
- **Shipped:** eight weeks. After that the 25 goes up on two clean sessions of its own.
- **What the research says** (TRAINING.md, 9, new): strength holds three to four weeks with no
  training at all and then falls; nothing measures a lift left out while training goes on, so
  eight weeks is cautious. It counts in weeks, never sessions, so the limit is in weeks.

### A step earned at other reps is offered as a try
- **Why:** madCode: this is the point of the app over a notebook. Two clean sessions at 8 reps
  earn a step up, but a board asking 6 or 12 next got only the converted weight, and the step
  was dropped.
- **Shipped:** the sheet keeps the converted number and adds "try 30 if set 1 felt easy", the
  dumbbell above it. Not after a twinge, and not when no step was earned.

### Wordings that leave the weight alone share a history
- **Why:** madCode asked whether Spotter's silence could have been found here. It could: an
  invented lifter working through the public board corpus, same weight every time, got a
  number on 110 of 361 weighted rows. Most blanks were a movement lifted before in other words.
- **Shipped:** "alternating", "dumbbell", "floor" or "bench" on a lying lift, and "seated" or
  "standing" on an upper-body lift no longer make a new exercise for the coach: such wordings
  share the number, the two clean sessions and a twinge. It is on in the app.
- **The review ran every corpus line through it** and found grip words don't belong: boards
  set "close grip chest press" beside "chest press", and the lighter one would have pulled the
  other's number down. They are left out, for madCode to rule on again. It also found a
  triceps extension on a bench merged with the overhead one, a combo's "no number" bypassed
  through its other wording, and the Progress card listing one step twice. All fixed.
- **On the yardstick:** 128 of 361 now. What is left blank is mostly words that do change the
  weight (one arm, a tempo, one leg, sumo, a band): each needs a factor from madCode.
- **Replaces** the per-movement switch and its one link, which the grading on real history
  showed never fired. The replay tool's flag is now `-PnoShared`, to compare both ways.

### A range asks which count was done
- **Why:** "✓ as listed" on "8 - 10 x" saved 8 reps without saying so, and the coach only goes
  up from the top of a range.
- **Shipped:** a line with a range has no tick. It shows a button for each count (8, 9, 10),
  in the board's own numbers, and the one tapped is saved at the sheet's weight. A line with one
  count keeps its tick. madCode chose this from four mocked options.
- **Also:** madCode's answers to nine of the ten open questions are in BACKLOG.

### The run closes
- **Why:** you said to end cycles when they ran out of things to do. After cycle 32 every item
  left needs one of your answers, and a cycle doesn't guess one.
- **Done:** the 4-hourly task is switched off, not deleted, so it can be turned back on.
- **To start again:** answer on the morning page or in BACKLOG, then ask for cycles.

## Sunday 22 November 2026

### Cycle 32: tread and rower instructions aren't rows
- **Why:** the review list showed it on a real week of boards: "Clear screen", "Set monitor to
  14 minute count down", "30 sec recovery row" and the like sat on the sheet as "not sure"
  rows.
- **Shipped:** the reader takes them for what they are, instructions, so they are no longer
  rows. On the board corpus and the last four days of the feed (12,180 lines), 544 lines went
  from "not sure" to instruction, and 9 two-station block titles are read as headings.
- **Five wrong reads fixed:** "1 min row" and "row until time is called" are the rower; they
  were being read as the dumbbell row, which could have borrowed its number.
- **Nothing else changed its reading**, checked line by line before and after.
- **The review tried 145 made-up lines** and found the first rules too greedy: "30 sec row
  (single arm)" would have vanished from the sheet. Now only the rower's own notes (a tempo, a
  stroke rate) keep such a line an instruction, and a line that names an exercise after
  "When done" is still a row. The second look found a dumbbell note that mentions a tempo
  ("30 sec row (single arm, slow tempo)") still hidden: a note that names an arm, a dumbbell
  or a weight now keeps the row.
- **Also:** a row shows "&" where the feed wrote "&amp;".

### Cycle 31: the words Spotter doesn't know yet
- **Why:** the part of step 6's review screen that needs no ruling from you.
- **Shipped:** a card under Progress, "Words Spotter doesn't know yet". It reads the boards kept
  on the phone and lists the unknown words of each "not sure" line, the ones seen on the most
  days first, with a line they were on. Read only: nothing can be sorted yet.
- **On a real week of boards it found** mostly tread and rower instructions that the reader
  takes for exercises, and three real gaps in the lists. Both are in BACKLOG.
- **For you:** how sorting a word should work, and what sharing a board should send, are in
  BACKLOG, "Your call". Those two are what is left of step 6.

### Cycle 30: is the advice working
- **Why:** the handoff's "know whether the advice is working: are the suggested weights
  completed?", and it needs no ruling from you.
- **Shipped:** two lines at the foot of the Progress card, for the last four weeks: how many
  of the sheet's numbers you did as suggested, and how many you lifted heavier, or lighter or
  came up short on.
- **Counts, not a grade.** The scorecard still does the grading, on the export. Rows that
  can't fairly be compared (one you added, an easier version, a ladder logged by its heaviest
  round) are left out.
- **Dropped after review:** a "rows had no number" line. The classes you logged on 22 Nov,
  before the log kept the sheet's number, would have been counted there untruly.

### Cycle 29: progress, what went up and what is next
- **Why:** step 6's first slice, and the one that needs no ruling from you.
- **Shipped:** a "Progress" card under the sheet. "Went up in the last 4 weeks" lists what
  you lifted heavier than the clean time before, at the same reps. "Next" lists what goes up
  a dumbbell next time, and what is one clean session away. It appears once there is
  something to say.
- **It reads your log and the imported history by the sheet's own rules**, so it never
  promises what the next sheet won't show.
- **Found on the invented history before review:** "Next" had no time limit and filled with
  exercises last done months ago; it now keeps to the last four weeks.

### Cycle 28: the skills and instructions, checked against what cycles did
- **Why:** the review was overdue (due around cycle 12).
- **Done:** a reader with no stake compared the skills, the CLAUDE.md files and the practices
  with 27 cycles of notes. Rules that cycles had relearned were moved up or reworded so they
  cover the case (run a new command for real; a due review is the audit; falsify in a
  worktree). Six lessons that kept coming back are now in `spotter/CLAUDE.md`, and the cycle
  log is back to its last ten entries. Nothing about the app changed.
- **For you:** four places where two instructions disagree are in BACKLOG, "Your call".
- **Not built:** your own measured ratio in place of the 0.6 one-leg factor; BACKLOG says why.

### Cycle 27: strength per movement, the switch and its first link
- **Why:** step 5. The Log screen's list is done but for your calls.
- **Shipped, switched off:** a rule set the app doesn't use (`Rules`), and the replay tool's
  `-PperMovement` to grade it. Its first link: an exercise done one side at a time
  ("alternating bicep curl") starts from the dumbbells of the plain one, and the other way
  round, where the plain rules say "new: go light".
- **Nothing changes on your phone.** On the invented history, the switch numbers 431 of 880
  lifted rows where the plain rules number 420; whether those numbers are good is for the
  scorecard on your real history to say (REPLAY.md).
- **The review found two ways the link could say too much**, both fixed: a single dumbbell
  held in both hands was lent to each arm (double the load), and the "never a step up" cap
  compared against the last session at any reps. It now lends only between two-dumbbell
  versions, and at most what was held the last time these reps were asked.
- **Waiting on you for more links:** which variations share a weight (BACKLOG, Your call).

### Cycle 26: two classes in a day, and two Log-screen leftovers
- **Why:** from earlier reviews; small, and the first could put a wrong number on a sheet.
- **Fixed:** with a morning and an evening class of different formats, the morning's sheet read
  the evening's log as its history. Now a class reads a row of another class the same day only when
  that row was first logged before it started.
- **Also:** an added row not yet saved has "Remove". A logged row whose line Spotter can no
  longer read still shows what is logged, and can be cleared.
- **Left:** two classes of the *same* format in one day still share a log (BACKLOG).

### Cycle 25: add an exercise the board missed
- **Why:** next on the Log screen: the coach swaps one in, or the board leaves one out.
- **Shipped:** under the rows, "Did one the board missed?": type it as the board would say it
  ("8 x chest press"), tap "Add an exercise", and it is a row under "Added by you" with the
  same rack, reps and chips as the rest. It feeds the next sheet like any logged row.
- **A gap beats a guess here too:** a line Spotter can't name in full isn't added, and the
  screen says which words it didn't know. Nor is one with no weight, or with no reps or time.

### Cycle 24: a weight for each round of a ladder
- **Why:** next on the Log screen, and you asked what would make a ladder's number better.
- **Shipped:** on a ladder row, pick a weight under "Changed", then "+ Each round": each
  round gets its own weight on a − / + stepper. Every round starts as a copy of the one
  before, so "20 / 25 / 30" is two taps. "One weight" goes back.
- **What it changes on the sheet:** a ladder logged this way counts at round one's weight, so
  it can go up like any other row. A ladder logged with one weight still only holds.
- **The review ran real sequences and found the first version could make a number worse:**
  "20 / 25 / 30" dropped the sheet to 20 where "30, the heaviest" held 25, and "30 / 25 / 20"
  twice went up to 35. Now a ladder that started light is read by its heaviest round, as
  before; one that went lighter as it went on holds; a stop or a twinge is put on the heaviest
  round. Also fixed: rounds said on a log carried over from another board were dropped.
- **Not yet:** a later round's weight says nothing to a straight row of those reps (BACKLOG).
- **The log's table gained a column** (version 3); a log from the build on your phone is kept,
  and a test opens one to prove it.

### Cycle 23: a documentation pass
- **Why:** due, after eleven PRs in a day.
- **Done:** the docs checked against the code by a reader with no stake in them. DESIGN said
  nothing was logged yet, had two Screens sections, and described four things as working that
  aren't built (the review list, a second borrowing factor, the corpus grade, a miss warning);
  BACKLOG still listed two done items as open and step 3 as unticked. All corrected; "Your
  call" now holds the four questions that are open and what was settled.
- **One line on screen changed:** the calendar note said Spotter reads today's and tomorrow's
  entries; it also reads the two days before, for a class not yet logged, and now says so.

### Cycle 22: two clean sessions to go up
- **Why:** your answer on the morning page and in chat: up after two ticks, or one that felt
  easy, and any settled weight needs two ticks before the next step.
- **Shipped:** one clean session now holds the weight ("up after one more clean session, or
  one that felt easy"). Two clean sessions in a row at the same reps and weight go up a
  dumbbell; one that felt easy goes up at once. If you lift above the sheet's number and it
  is clean, that becomes the number and waits its own two. "Felt easy" in an imported
  history counts too.
- **This slows every number down**, by design. Grades from the replay tool before and after
  this change aren't comparable.

### Cycle 21: your history on the phone
- **Why:** step 4's last part: the sheet says "new: go light" on every row until it has
  history.
- **Shipped:** "Import a history file" at the bottom of the home screen. Pick your
  `history.jsonl` once and the sheet's numbers come from it and from what you log. The screen
  says how many rows were read and how many were left out, by reason. You can replace or
  remove it; your own log is never touched. A day that is in both your log and the file
  counts once, as you logged it here.
- **To check on the phone:** DEVICE.md, "History import".

### Cycle 20: the replay tool
- **Why:** the gym-coach session asked for a way to grade Spotter's coach on your real history
  without a phone, in private.
- **Shipped:** one command replays a history file: for each class, the sheet the coach would
  have made from the days before it, beside what you lifted, as a file the scorecard grades.
  It prints counts only and refuses to write inside a git checkout. `docs/REPLAY.md` has the
  command. No switch for strength per movement yet: that is step 5.
- Tested on invented histories only, including Carry's invented lifter (163 classes).

### Cycle 19: reading your history file
- **Why:** step 4. The sheet says "new: go light" on every row until it has history, and yours
  is in gym-coach's file.
- **Shipped:** the coach can read that file's format. Nothing on the phone yet: picking the
  file is next, then the replay tool. A row is taken only when its exercise, weight and reps
  can all be read; the rest are counted by reason (skipped, no weight, form broke down, not
  named, reps unclear). Tested on invented rows only, including the 1,326-row invented
  history in Carry's fixtures.
- **Order changed:** this went ahead of the rest of the Log screen (two classes a day, add an
  exercise, each round), which stay in Next.

### Cycle 18: a log follows the exercise when the board changes
- **Why:** your answer ("1 yes"): rows logged from a borrowed board should carry over to your
  own board's rows with the same exercise.
- **Shipped:** log from the other format's board, and when your class's own board is posted the
  log moves to the matching rows, with the weight and reps you logged. Anything with no
  matching row is listed at the bottom of the Log screen under "Logged, but not on this
  board", with a Clear button; it still counts until you clear it. The same happens when a
  board's poster revises a line after you've logged it.

### Cycle 17: the log goes to a folder you pick
- **Why:** step 3's backup: the log shouldn't live on one phone only.
- **Shipped:** the bottom of the home screen says where your log is. Choose a backup folder
  once, and Spotter copies the log there after each save and once a day: a file per class in
  the scorecard's shape, and one file with everything. If the copy fails, it says so and asks
  you to choose the folder again. Android's own backup now keeps the log too.
- **To check on the phone:** DEVICE.md, "Backup folder".

### Cycle 16: the export, from the log
- **Why:** step 3's export: the file the scorecard grades, and a copy of everything you log.
- **Shipped:** the coach can turn the log into files: one scorecard session per class you've
  logged, with the number the sheet showed you beside what you lifted, and one file holding
  every event. Nothing writes them to a folder yet; that, the schedule and Android backup are
  next.
- **Review caught:** two classes on a day whose names cut down alike would have overwritten
  each other's file; correcting a row replaced the number the sheet had shown with a later one;
  a ladder counted in totals went out with halved rounds; a damaged row could be graded.
  All fixed and tested, and the tests now check the cases the first ones let through.

### Cycle 15: which class, once you've logged
- **Why:** your answer ("tomorrow's sheet as soon as today's is logged"), and the review's
  finding that a class not logged within three hours could never be logged.
- **Shipped:** once today's class is logged, the sheet shows your next one, so the evening
  before a morning class you see that class. And a class you haven't logged stays at the top
  of the screen for two days ("… isn't logged yet · Log it"), whatever the sheet below shows;
  once logged it becomes a quiet "Change the log for …".
- **Review caught:** saving one row mid-class would have swapped the sheet for tomorrow's (now
  only once the class is over); after a save the old sheet could sit there without its log
  while the next board was fetched, as if the save were lost; a class in the calendar under two
  titles was offered beside itself; a theme entry could reopen a logged day after three hours;
  the banner nagged about a class with nothing to log. All fixed and tested.
- **Left, in BACKLOG:** a class logged from a board that has since changed reads as "isn't
  logged yet"; two classes in one day share or cross logs; the banner can't be dismissed for a
  class you chose not to log (it goes after two days).

### Cycle 14: pick the weight from the rack
- **Why:** the logging review found "changed" on a new row saved a made-up 2×10 before you'd
  picked anything, assumed two dumbbells, and took eight taps to reach 40.
- **Shipped:** "changed" opens the whole rack, thirteen weights, one tap each, with last time's
  weight shown above it. Nothing is logged until you pick. "One dumbbell / Two" starts from what
  is known (the board's words, the sheet's number, your last log) and otherwise from neither,
  with "One dumbbell or two?" asked on the row.
- **Review caught:** a mis-tapped "changed" couldn't be closed; "as listed" and "changed" could
  both look selected; nothing asked for a count that wasn't known; saying how many dumbbells
  on a listed ladder made it count as a changed one; the rack jumped down a line after the
  first pick; "last time" could show the earlier of two logs from one day. All fixed.

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
  weight to its one-leg version; and left ladders already on the phone able to step up. A
  second look at the fix caught more: a session from long ago could bring back a much heavier
  number; a twinge at 8 reps allowed the same weight for 12; an old twinge capped for ever; and
  the arrow could disagree with its reason. All fixed and tested.
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
