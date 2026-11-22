# Design

How Spotter works now, for someone about to change it. Plans go in BACKLOG.md; history in
DEVLOG.md and the PRs. Until the first code lands this describes what is being built; each
section is updated to "now" as its piece ships.

## What it's for

One person who takes Orange Theory classes (2G and 3G: a treadmill block and a floor block of
dumbbell, bench and bodyweight work). The phone can't go into the studio, so Spotter gives
them, from one glance beforehand, **which weight to grab for each exercise in today's class**,
with warnings first. In the car afterwards it logs what was actually done, in under a minute.

It replaces Carry (`../carry/`) only when it beats Carry on the scorecard (below). Until then
both are installed side by side.

## Rules

- **A gap beats a guess.** A wrong number is worse than none, because nothing later can tell
  them apart. Where there is no sound basis, the row says "new: go light", not a small number.
  But silence is a failure too: the scorecard counts unnumbered rows.
- **What's logged never changes meaning.** History is an append-only log of events. A
  correction is a new event; an exercise's identity is fixed when it is logged.
- **Weight is per hand plus a count:** `2×25`, `1×50`, never a bare total. Imported rows whose
  count is unknown say so and count as weaker evidence.
- **Every word on a board line is accounted for.** Each is part of the movement, a variation
  the coach knows, or known filler. A word left over makes the row "not sure what this is",
  with no number borrowed from a plainer exercise. The sheet shows the board's own words, with
  what Spotter understood beneath, so a misreading is visible at the gym.
- **One coach, in plain Kotlin.** The `coach` module has no Android code and runs in JVM tests.
  There is no second implementation anywhere.
- **The training data is madCode's.** It lives on the phone, in Android backup and in exports
  they choose. No GitHub token or Reddit key in the app; no real figures in this repository.

## Parts

- **`coach/`** (Kotlin/JVM). Pure functions from inputs to a sheet:
  - *Board parsing:* the day's board lines into exercise rows (movement, variation, reps,
    implement), leaving prose and greetings out.
  - *Identity:* see **Reading a board line**, below.
  - *Progression:* straight sets with 1–2 reps in reserve. Up a dumbbell after clean sessions;
    hold after a near miss or a twinge; down a step after a miss of 3+ reps, a miss on the
    first set, broken form or having to stop. A later miss caps an older success. Epley only
    within 3–12 reps. A test number ("12 · try 15 if set 1 felt easy") when the next dumbbell
    is a big jump.
    How a session went (`Progression`): *clean* when every set reaches the target's top;
    *in range* when every set is in the range but not at its top (hold); a *near miss* when a
    later set is one or two short (hold); a *miss* when the first set is short, any set is three
    or more short, form broke or the set stopped (down a step). A single set logged (the Log
    screen's heaviest, often from a tired later round) has no first set to miss: one or two short
    holds (madCode, 2026-11-22); *hurt* when it twinged.
    A twinge holds the weight and says so (madCode): the latest set that hurt caps the number at
    what hurt, or a step under it if it was also a miss; the cap comes last, so no other rule
    lifts a number past it, and no test weight is offered at or above it. The row carries a
    warning and the reason, whether or not the cap changed the number. What twinged at fewer
    reps caps lower for more reps. A sound session after a twinge clears it. With nothing but
    twinges logged, the latest one is the number. Nothing is borrowed from a lift that twinged.
    A clean set that "felt hard" waits a session before stepping up (TRAINING.md, 3).
    Each rep count keeps its own history: the last time these reps were asked is the basis,
    while the latest session bears it out. A set missed since, at any reps, puts the latest
    session in charge, and so does a latest weight lighter than the older one predicts for its
    reps (so a number from long ago doesn't come back). A near miss since, or a latest session
    that can't be compared, only stops a step up. Otherwise the weight converts by Epley from
    the reps done, rounded down, starting from the weight held and never from a step up
    (TRAINING.md, 5). ▲ ▼ = compare with the session the number rests on.
    A ladder's sets are each judged against their own round. A ladder logged as "changed" has
    one weight, its heaviest round's: it never raises a number, and stands for that round's
    reps, so round one gets what it converts to. A ladder ticked as listed was done at the
    sheet's number from round one. A timed set
    holds its weight, since it says nothing about reps in reserve, and a timed set and a counted
    one don't convert into each other. A rep target with no reps logged says nothing. A step more than 20% heavier (12 to 15, 20 to 25) is a
    big jump: the number holds and the heavier one is offered as a test. A new exercise borrows
    only through a stated factor: one leg from the plain two-legged lift at 0.6; anything else
    is "new: go light".
  - *Explanations:* every number carries the reasons behind it.
  - *The sheet* (`Sheet`): a class's board lines in order, under their block headings, each
    exercise row with the board's own words, what Spotter read, its reps a side (round 1 of a
    ladder) and its number. Warnings go first. A heading inside a block ("Back-to-back
    complex:") names part of that block. A combo with no history of its own takes the lightest
    of its weighted parts, but only when every one has a number of its own and all are held the
    same way; otherwise it has none, and says which part is missing. A row wants no weight when
    it says bodyweight or TRX; when it wears a band, holds or pulses with nothing held; or when
    it is a movement done without one (push-up, plank, burpee) and names nothing to hold. ▲ ▼ =
    say what the rules decided; a change of reps alone is not a drop. An imported
    load with no count takes the row's when the row's words fix it (goblet, two dumbbells,
    single arm).
  - *Scorecard rows:* per logged row, `date`, `suggested_weight` and `weight`, the shape
    gym-coach's `scorecard.py` grades.
- **`app/`** (Android, Compose). Fetches the board, reads the calendar, stores the log, shows
  the screens, runs the export. Package `com.app.spotter`, so it installs beside Carry.

## Reading a board line

Carry's reading of its 1,560-row board corpus shows why this gets its own section: about one
row in five drops words it didn't understand and still names a plainer exercise ("bench bird
dog low row" became *low row*), and some words it understood were lost on the way ("floor close
grip chest press" became *bench press*). The sheet then shows a number for something other than
what is on the board, and nothing says so.

- **An exercise is a movement plus its variations.** Movement: chest press, squat, row.
  Variations, each from a closed list the coach knows, by axis: implement and count (one or two
  dumbbells, Y-bell, TRX, band), arms (single, reciprocating), alternating, legs (single leg,
  split stance), position (floor, incline, bench, bridge hold, high plank), stability (bosu),
  grip (close, neutral, hammer), tempo and range (pulsing, half, full, explosive), band
  placement, direction, and hold; then combos ("A to B") and the rep scheme (reps, "6 each",
  "12 total", ladder, timed). Two rows are the same exercise only when movement and every
  variation match, one value per axis. That identity is fixed when a row is logged.
- **Intensity words are hints, not identity.** "Heavy" and "lighter" can shape the number on
  the sheet, but a heavy sumo deadlift is the same exercise as a sumo deadlift.
- **First the line is split** (`BoardLine`): what it asks for, and the words left for identity.
  - Rep schemes: a count ("12 x", "8 lateral lunge"), a range ("6 - 10"), "each" or
    "total", a ladder ("10 / 8 / 6"), a time ("30 sec of", "1 min of"), or AMRepsAP.
  - "6 each" and "12 total" are both 6 a side (trap 7). A ladder's weight is for its first
    round (trap 8).
  - A number that belongs to the exercise stays in it: "12 x 2 dumbbell bench bridge" is 12
    reps of a two-dumbbell bridge.
  - Labels ("Finisher:", "Bonus:", "Buy-in:"), a side ("(R)", "on left") and a trailing "rest"
    come off.
  - Lines that tell the class something ("Goal: match or beat…", "Repeat until time is
    called…", a cheer, a question, a sentence to the reader, a run recap), tread and rower
    lines ("600 m row", "1 min all out"), and block headings are never exercises (trap 5),
    even when they hold an exercise's name.
  - A line over 300 characters is commentary, unread: that also keeps a hostile line from
    stalling the reader.
- **Then the words are named** (`Identity`), longest phrase first, into one or more parts,
  each a movement with one value per axis, or "not sure" with the reason:
  - a word in no list (kept for the review list), a choice ("or"), two movements side by side,
    two values on one axis, or no movement at all;
  - combo connectors ("to", "into", "and") start a new part; "with" does only when a movement
    follows and one came before ("bridge hold with chest press" is a press in a bridge hold),
    and a "hold" before it is a stance, not an isometric ("single leg hold with … press");
  - what a combo holds, stands on or wears (implement, Bosu, band, single arm) is named once
    for every part: "TRX high row to bicep curl" is a TRX curl too, unless a part says otherwise;
  - a plank or bridge hold on its own is the exercise ("1 min of low plank");
  - the identity key is each movement with its variations in a fixed order, parts joined by
    " + ", without intensity hints.
- **A line is fully read, or it is "not sure".** Filler is a closed list too ("rest",
  "finisher", "count & remember reps"). A word that is none of these leaves the row unsure: it
  is shown, it can be logged, but it borrows no number.
- **A full read can still be wrong**, so the reader also refuses these, found on the corpus:
  - two movements side by side with no connector ("bench bird dog low row", "TRX bridge row");
  - a word that means one thing on one movement and another elsewhere ("wide" is a stance on
    a squat and a grip on a curl; so are "close" and "narrow"), so such words are only listed with the word that settles
    them ("wide stance", "wide grip");
  - "with" before only an implement or a modifier ("press with y-bell", "with power"): that is
    a variation of the movement before it, not a combo.
- **Related exercises are linked by stated factors**, shown in "Why this number" ("close grip,
  floor: about 0.85 of chest press"). Where no sound factor exists, the row says "new: go
  light". Until strength per movement is switched on (see the scorecard), only identical
  exercises share history.
- **New words go to a review list.** madCode sorts each new word or name once: which movement,
  which variation, harder or easier, or filler. The answer is an event in the phone's log, so it
  never relabels history already logged and is backed up with it. A useful answer can be
  proposed back to the starting list in a PR.
- **Where the lists live.** The starting lists are public knowledge, so they are JSON files in
  `coach/src/main/resources/spotter/`, shipped inside the app and changed by PR:
  `movements.json` (each movement, its true synonyms, pattern, muscles, and whether it is
  one-legged) and `words.json` (variations by axis, connectors, rep-scheme words, filler).
  madCode's own answers live only on the phone.
- **A movement is what is lifted; everything around it is a variation.** Carry's list baked
  variations into names ("goblet squat", "reverse grip low row") and filed different exercises
  as aliases ("hammer curl" under bicep curl, a bare "rotation" under torso rotation). Here an
  alias is only another spelling, a named variation is a word on its axis, and a combo ("lunge
  with torso rotation") is two movements and a connector, never a list entry.
- **A new name is added only when its meaning is plain from the boards**, and then as its own
  exercise ("tap squat", "tricep push-up"), so it borrows no number until it is logged. A setup
  phrase is added only when it settles one variation on any movement ("rear foot on bench" is
  rear foot elevated); one that could be two exercises ("feet on bench" on a climber,
  "shoulders on bench" on a press) stays unknown, and so on madCode's review list.
- **Some words mean something only on certain movements**, so they are listed with them
  (`scoped` in `words.json`): "wide" is a grip on a bicep curl, "feet on bench" is a decline on
  a mountain climber, "shoulders on bench" the bench on a bridge. On any other movement the word
  stays unknown. Two words take their meaning from the words before them: "rotation" after "with"
  is a torso rotation, unless the movement before is a core one ("high plank with rotation"),
  and a bare "press" after a squat or lunge is a shoulder press.
- **"Feet on bosu" and "hands on bosu" say where on the Bosu**, so they are kept apart, and a
  line that also says "bosu" is not two Bosus. In a combo each part keeps where it says.
- **Every phrase means one thing.** Loading the lists fails if a phrase is in two places, so
  which one a line meant never depends on file order. The longest phrase wins when reading:
  "high plank" is a position before "high pull" could be a movement.

## The log

- **Append-only** (`Journal` in the coach, `LogDatabase` in the app): each row logged is an event;
  a correction is a new event naming the one it replaces, and a removal one naming what it
  removes. The table is only ever inserted into. `Journal.history` folds the events, in the
  order written, into what the coach reads: each row as last corrected, removed ones left out.
  A correction or removal of an entry that isn't there is skipped, and so is a stored row that
  can't be read (damaged, impossible weight, a kind from a newer build): damage costs one entry,
  never the log. A ladder is stored in reps a side, as the reps are.
- **Identity fixed when logged**: an event keeps the movements and variations as they were read.
  A later change to the word lists never relabels a logged row; a movement the lists no longer
  hold keeps its logged name. The key is rebuilt from them, so a change to the key's format
  changes logged and new keys alike.
- **Each event names its sheet row by id**, not by place: the block's name (without its
  minutes) and the line as read, with its count, plus which repeat of that line in the block it
  is. The kept board and the fetched one give a row the same id, and so does a board fixed after
  posting (a capital, a quote, the block's minutes).
- **Each event keeps what the sheet showed** (weight and count, or nothing), so the scorecard
  grades the number the person saw, not one rebuilt later from a board that has changed.
- **The schema is kept in the repository** (`app/schemas`), one file per version. A phone has
  had version 1 since the Log screen shipped, so each change is a new version with a
  migration; `LogMigrationTest` builds a database from the old schema file and opens it.

## Loads

Dumbbells come in 3, 5, 8, 10, 12, 15, 20, 25, 30, 35, 40, 45 and 50 lb; "one step" means the
next one in that list. A Y-bell is another implement and never steps onto that list.

- A load is a count of dumbbells and the weight of each: `2×25`, `1×40`.
- The one conversion between counts: one dumbbell held in both hands (goblet) to two dumbbells
  whose total is no more than it, rounded down to the rack (1×40 → 2×20). Two dumbbells are
  never merged into one: a single-arm lift relates to a two-dumbbell one through a variation
  factor, not arithmetic.
- Imported history gives the weight of each dumbbell but often not how many. Such a load can
  step on the rack, but has no total and converts to no other count. It only feeds a row whose
  count it can't contradict: a row with no count of its own, or one whose name fixes the count
  (a goblet squat is one dumbbell). Otherwise an imported "squat 40" that was a goblet could
  become 2×45 on a two-dumbbell row. Imported decimals round down, never up.

## Where the inputs come from

- **The board:** `boards/YYYY-MM-DD.json` (schema `carry-board/1`) on this repository's
  `carry-boards` branch, which a scheduled job refreshes every half hour, fetched from
  `raw.githubusercontent.com` with no key (`app/today/Boards`). A file is kept only when it reads
  as that day's board; the phone keeps a week of them. The last good board is used when the
  day's can't be fetched, so a Reddit, GitHub or Wi-Fi outage shows an earlier day's sheet
  marked with its date, never a blank or wrong one, and never a later day's. A class with no
  board of its own borrows the other format's, labelled (`TodaysBoard` in the coach).
- **Which class:** the next booked class in the phone's calendar (`Booking`): today's, until
  it is logged and over (50 minutes after its start, so rows saved mid-class don't swap the
  sheet) or three hours after it starts (long enough to log it in the car), then tomorrow's. So the evening before a morning class shows that class once today's is logged.
  The latest class already started stays loggable for two days after the sheet has moved on:
  "… isn't logged yet · Log it" first on the screen, from that day's board as kept on the
  phone, or "Change the log for …" once it has rows. The format is in the event title ("Orange 60 Min 2G").
  Nobody picks the class by hand. When Spotter may not read the calendar, it says so rather
  than "no class booked".
- **The exercise list:** Spotter's own, in the coach's resources (see "Reading a board line").

## Screens

- **Sheet.** Today's (or tomorrow's) class and its board's age. One row per exercise in block
  order: name, the number, ▲ ▼ = against last time. Warnings at the top and on their rows
  (a miss, pain, a run of unfinished sets). A tap on a row opens why.
- **Log** ("Log this class" on the sheet). The same rows. Each weighted row is "✓ as listed"
  (the sheet's number, and the board's reps: a range's low end) or "changed": the heaviest
  dumbbell picked from the whole rack in one tap, one or two, and reps on a stepper. Opening
  "changed" logs nothing until a weight is picked, and the count is never guessed: it comes
  from the row's words, the sheet's number or the last log, or the row asks "One dumbbell or
  two?". Tapping "changed" again closes the rack. With "felt easy / hard", "twinge", "had to stop"
  and "easier version" one tap away. A row with no number yet has only "changed". Untouched rows
  stay unlogged, never "skipped". Save writes events (`Logging` in the coach, one transaction):
  a new row is logged, a changed one corrected, one cleared removed, an unchanged one left
  alone. Reopened, the screen shows what is logged. A class's own sheet keeps the numbers from
  before it; the next class reads what was done.
- Still to come on the Log screen: "+ Each round" (each new round a copy of the previous one,
  so only what changed needs a tap; the heaviest stays the row's one number), and "Add an
  exercise" for one the board missed.

Later: progress, review of new exercise names, sharing a board for others, the watch.

## Updating itself

madCode asked for an app that updates itself without them doing anything. A CI debug build of
Spotter does:

- **Where builds come from.** Each merge to main publishes its debug build to the
  `spotter-debug` release, beside `spotter-debug.json`: the build number, commit, the numbered
  APK's address, and its exact size and SHA-256 (`tools/debug-feed.sh` writes it).
- **When it checks.** On opening the app and every three hours in the background, on any
  network. A local build (no CI number) never checks: replacing it would throw away what is
  being tried.
- **What it installs.** Only a newer build, only from this repository's release, and only when
  the download matches the feed's size and hash. A build whose bytes are wrong, or that the
  installer refuses, three times is left alone until a newer one comes; a dropped connection
  doesn't count against it.
- **One run at a time.** The periodic check, the check on opening and a tap all queue on one
  WorkManager chain, so two never download into the same file or race to the installer.
- **When it installs.** Installing replaces the running app, so the background check installs
  only while Spotter isn't on screen, checked again after the download. Otherwise, or when
  Android wants a tap, a blue box offers the build (as soon as it's found, even with Spotter
  open) and its Install button does it. A build Android asked to confirm isn't downloaded again
  in the background: on Android 8 to 11 that would be every three hours, on mobile data too. On Android 12 and later, once Spotter has installed
  itself once, Android installs without asking (`UPDATE_PACKAGES_WITHOUT_USER_ACTION`).
- **Debug only.** The permissions to install apps are in the debug manifest; a check in the build
  fails if a release ever has them, or a debug build lacks them. Both builds reach the network,
  for the day's board, and the check also fails if either lacks that.
  How to check it on a phone: `docs/DEVICE.md`.

## Screens so far

- **Home**: the name, the update box when a build is waiting, today's sheet, and the version at
  the bottom, copied in one tap.
- **The sheet** (`SheetView`): the class, its date and start ("2G · Sat 21 Nov · 9:50"); notes
  when the board is borrowed or from an earlier day; the warnings; then each row in board order
  under its block, with the board's own words, what Spotter read beneath, and the number on the
  right. A tap on a row shows why, and stays open through a rotation. With no class booked,
  no board yet or no calendar, it says which in a sentence. The board kept on the phone shows
  at once and the fetched one replaces it, so weak signal never blanks the sheet. TalkBack
  hears the number in words ("two 30 pound dumbbells, up from last time"). Nothing is logged yet, so every
  weighted row says "new: go light" until step 3.
- **The calendar** is asked for once, on first open. Refused, the sheet explains what is read
  (titles and times of today's and tomorrow's entries) and offers the ask again, or Android's
  settings once Android stops asking.

## Backup

Android backup of the log, plus a scheduled export (the scorecard shape and the full log) to
a folder madCode picks, such as Downloads or a synced folder.

What is written (`Export` in the coach) comes from the event log alone, since each event keeps
the board's words and what the sheet showed:

- `session-<date>-<class>.json` for each class with rows logged: a `gym-session/1` file of its
  rows as last corrected, in the order logged. Rows left alone aren't in it.
- `spotter-log.jsonl`: every event, a line each, corrections and removals included, so the
  history can be rebuilt from the file.

## The watch (later)

madCode wears a Garmin. The watch view is a Connect IQ app (Monkey C) that shows the sheet's
names and numbers; the phone sends it the day's sheet through Garmin's Connect IQ companion
SDK. The coach stays on the phone; the watch only shows what it is sent.

## The scorecard decides

The export writes what gym-coach's `scorecard.py` grades: one file per class with a top-level
`date` (YYYY-MM-DD) and a row per exercise. Each row's `suggested_weight` and `weight` are the
weight of one dumbbell, never a sum (2×25 is 25, a goblet 1×40 is 40), with the count in a field
of its own. `suggested_weight` is null when the sheet showed no number. Rows with no weight, or
`skipped`, aren't graded.

The file (`ScorecardSession`) is a session file in Carry's format, `gym-session/1`, which the
same scorecard already grades: every field Carry writes, with Carry's types (weights as decimals,
`reps` as one number: the reps every set reached), and a `sheet_id` of date and class. Spotter's
own extras (`count`, `suggested_count`, `set_reps`) are fields Carry's reader ignores. A row left
blank has a null weight and is not skipped, and a set can only be logged against its own row.
[scorecard-example.json](scorecard-example.json) is an invented class, and a test keeps it equal
to what the code writes.

Over every logged weighted set: did the sheet give a number (*numbered*), was it within one
dumbbell of what was lifted (*close*), and how many were more than a step *high* or *low*.
Beside it, the board corpus is graded line by line: how many rows are read fully, unsure, or
wrong against hand-checked answers. Wrong stays at zero; unsure falls as the review list grows.
Spotter switches on for real when, over the same weeks of real classes, it numbers more rows
than Carry, gets more close, and is high no more often. Strength per movement (variations
reading one estimate through stated factors) ships behind a switch and is judged the same way
against plain per-exercise rules before it is turned on.
