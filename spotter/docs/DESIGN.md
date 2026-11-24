# Design

How Spotter works now, for someone about to change it. Plans go in BACKLOG.md; history in
DEVLOG.md and the PRs. The few parts that aren't built yet say so.

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
  count is unknown say so, and feed only rows whose count they can't contradict.
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
  - *Progression:* straight sets with 1–2 reps in reserve. Up a dumbbell after two clean
    sessions in a row at a weight, or one that felt easy; hold after one clean session, a near
    miss or a twinge; down a step after a miss of 3+ reps, a miss on the
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
    One clean session holds: a weight goes up after two clean sessions in a row at the same
    reps and weight, or after one that "felt easy" (madCode, 2026-11-22; TRAINING.md, 1). Two
    sessions are two days: the same exercise twice in a class is one. Anything between them
    that wasn't clean (a twinge, a miss, a near miss, at any reps) breaks the run, and one
    dumbbell and two aren't the same weight. Coming back to a weight done clean before isn't
    new: a clean session at the same weight counts however old, and one at a heavier weight if
    it was within the eight weeks before (TRAINING.md, 9). After longer the lighter weight
    earns its step with two sessions of its own. A weight lifted above the sheet's number becomes the number and waits its own two;
    one that twinged isn't taken up. A big jump's test weight is offered once the weight has settled.
    A clean set that "felt hard" waits a session before stepping up (TRAINING.md, 3).
    Each rep count keeps its own history: the last time these reps were asked is the basis,
    while the latest session bears it out. A set missed since, at any reps, puts the latest
    session in charge, and so does a latest weight lighter than the older one predicts for its
    reps (so a number from long ago doesn't come back). A near miss since, or a latest session
    that can't be compared, only stops a step up. Otherwise the weight converts by Epley from
    the reps done, rounded down, starting from the weight held and never from a step up
    (TRAINING.md, 5). A step up that was earned at the other reps isn't lost: what that
    dumbbell converts to, when it is more than the number, is offered as "try 30 if set 1 felt
    easy": one dumbbell above the number at most, and not if it twinged since. ▲ ▼ = compare with the session the number rests on.
    A ladder's sets are each judged against their own round. A ladder logged as "changed" has
    one weight, its heaviest round's: it never raises a number, and stands for that round's
    reps, so round one gets what it converts to. A ladder ticked as listed was done at the
    sheet's number from round one. A ladder logged with a weight for each round counts at
    round one's, like a listed one, so it can raise the number. Two exceptions, so that saying
    more never gives a worse number than saying only the heaviest: a ladder that climbed to
    more than round one's weight is worth (started light), or one with a twinge or a stop, is
    read by its heaviest round as a one-weight ladder is; and one that went lighter in a later
    round of no more reps holds, as if it felt hard. A timed set
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
    it says bodyweight or TRX; when it holds or pulses with nothing held; or when it is a
    movement done without one (push-up, plank, burpee, a walk in a band) and names nothing to
    hold. A band doesn't decide it: it pulls across the lift, and a banded row takes a weight
    exactly when the same row without the band would. But for a combo: a banded part with
    nothing held is the band's own exercise there ("low band squat to lateral leg lift"). ▲ ▼ =
    say what the rules decided; a change of reps alone is not a drop. An imported
    load with no count takes the row's when the row's words fix it (goblet, two dumbbells,
    single arm).
  - *Scorecard rows:* per logged row, `date`, `suggested_weight` and `weight`, the shape
    gym-coach's `scorecard.py` grades.
  - *The log and its files:* the append-only events and the history folded from them
    (`Journal`), what a save of the Log screen writes (`Logging`), the export's files
    (`Export`), reading another app's history (`HistoryImport`) and replaying one for grading
    (`Replay`).
- **`app/`** (Android, Compose). Fetches the board, reads the calendar, stores the log, shows
  the screens, runs the export, keeps an imported history. Package `com.app.spotter`, so it installs beside Carry.

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
- **Intensity words are hints, not identity.** A heavy sumo deadlift is the same exercise as a
  sumo deadlift. The hint is read and kept; it doesn't change the number yet.
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
    lines ("600 m row", "1 min all out", "1 min push row", "30 sec speed surge", "2.5 min
    base", "Clear screen", "Set monitor to…", "When done, WR until…"), and block headings are
    never exercises (trap 5), even when they hold an exercise's name. A bare "row" after a
    time, or "row until…", is the rower; a dumbbell row says which ("low row").
  - A line over 300 characters is commentary, unread: that also keeps a hostile line from
    stalling the reader.
- **Then the words are named** (`Identity`), longest phrase first, into one or more parts,
  each a movement with one value per axis, or "not sure" with the reason:
  - a word in no list, a choice ("or"), two movements side by side,
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
- **Related exercises are linked by stated factors**, shown in "Why this number". One exists so
  far: one leg from the two-legged lift, at about 0.6. Where no sound factor exists, the row
  says "new: go light".
- **Words that share a weight.** Some wordings leave the dumbbells as they are, and those share
  one history: the number, the two clean sessions and a twinge all carry across, and "Why this
  number" says other wordings were counted. The Progress card lists such wordings once. madCode
  ruled on the first set (2026-11-23):
  - "alternating": one side at a time, the same dumbbell in each hand. Only between sessions
    logged with two dumbbells, or where the words say how it is held ("goblet", "single
    arm"): one dumbbell held in both hands would be double in one arm.
  - "dumbbell": it names what the lift is already done with.
  - "floor" or "bench" on a press, fly or pullover: the floor only shortens the range. Not a
    triceps extension, which on a bench is another lift than one overhead.
  - "seated" or "standing" on a lift the legs don't move (not a thruster or push press).
  - "overhead" on a tricep extension: on these boards the bare one is the overhead one.
  - "(heavy)" and "(light)" were never part of an exercise's name.
  Everything else keeps a history of its own until a factor is agreed: one arm, one leg, a
  split or sumo stance, a bosu, a tempo, an incline, one dumbbell against two. So does a grip
  word, though madCode's first set had it: boards set "close grip chest press" beside "chest
  press", lifted at different weights, and the latest session would speak for both. The replay tool
  can switch sharing off (`-PnoShared`) to grade the coach both ways.
- **Tweaks that take an estimate.** madCode (2026-11-24): "if it's literally the same
  exercise with minor tweaks", the sheet gives a number, not "new: go light". A lift never
  logged in these words takes the weight its nearest logged version last held, shown as
  "est.", with that version named in "Why this number". Never a step up that version has
  earned, never more than it was ever lifted at, and nothing from one that twinged. Where two
  versions are as near, the lighter estimate, and none if either has nothing sound to lend. Logged once in these words, judged or not, the
  tweaked lift has a history of its own. The tweaks, each with its reason:
  - one arm from two: the same dumbbell in the one hand as in each of two, when the session
    it rests on was logged with two dumbbells. Two arms from one only on a lift the arm limits
    (a press, row, curl, fly or raise, but not a push press or thruster): on a lunge it would
    double what the legs move, and a swing or pullover is one dumbbell in both hands.
  - "explosive": faster, not heavier.
  - a band worn while holding a dumbbell: it pulls where the dumbbell doesn't (on a squat, the
    hips sideways; on a row, the wrists apart).
  - sumo stance, on a squat or deadlift: with a barbell it lifts the same or a little less
    (TRAINING.md, 10).
  - incline or decline, on a press or fly; kneeling or half kneeling, on a lift the legs don't
    move; a close, neutral, hammer or wide grip; feet on a bosu: the same weight (TRAINING.md,
    11). A hammer curl lends nothing to the plain curl: it is the stronger of the two. A
    kettlebell or y-bell takes no estimate either way: they come in other weights.
  - one leg from two is the stated factor above, 0.6, and a staggered stance 0.8; both show
    "est." too.
  A tweak that makes the lift harder starts it lighter, by a factor rounded down to the rack
  (TRAINING.md, 11): standing on a bosu 0.8, lying on one for a press 0.9; a slow or
  "eccentric" lower, or a pulse, 0.85; a pause or "stick it" 0.9; a b-stance 0.8. Two such
  tweaks multiply. The other way round, the harder version's weight stands for the plain lift:
  dividing by a guess would make a heavier number.
  One dumbbell held in both hands, against one in each (TRAINING.md, 12): 1.5 of one of the
  pair on a squat, a lunge or a tricep extension, where it is held up; 2 on a deadlift or a
  Romanian deadlift on two legs, where it hangs; no estimate on any other lift. Only between rows whose words say how the weight is held ("goblet", "two
  dumbbells") or a session logged with two, never to a bare row from a goblet one.
  The replay tool can't yet switch estimates off on their own; `Rules.estimates` is there for it.
- **New words go to a review list.** The list is built, read only (`ReviewList`): a card
  under Progress, "Words Spotter doesn't know yet", reads the boards kept on the phone (the
  last 7 fetched) and lists each line's unknown words together ("front foot"), the ones seen
  on the most days first, with the latest line they were on. The boards are strangers' text,
  so line breaks and text-reordering marks are taken out, a day counts once for a set of
  words, at most 300 lines of a day's board are read (none longer than 160 characters), and
  each entry has two lines of the card at most. Sorting them isn't built yet: madCode sorts each new word or name once: which movement,
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
  never the log. A ladder is stored in reps a side, as the reps are; with a weight for each
  round, the event's own weight is round one's and the rounds' weights sit beside it.
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
- The one conversion between counts (written and tested, not used by the sheet yet): one
  dumbbell held in both hands (goblet) to two dumbbells whose total is no more than it, rounded
  down to the rack (1×40 → 2×20). Two dumbbells are
  never merged into one: a single-arm lift relates to a two-dumbbell one through a variation
  factor, not arithmetic.
- Imported history gives the weight of each dumbbell but often not how many. Such a load can
  step on the rack, but has no total and converts to no other count. It only feeds a row whose
  count it can't contradict: a row with no count of its own, or one whose name fixes the count
  (a goblet squat is one dumbbell). Otherwise an imported "squat 40" that was a goblet could
  become 2×45 on a two-dumbbell row. Imported decimals round down, never up.

## History import

`HistoryImport` in the coach reads gym-coach's `history.jsonl`
([HISTORY-FORMAT.md](HISTORY-FORMAT.md)) into the coach's own history.

On the phone, "Import a history file" on the home screen opens Android's file picker. Spotter
reads the one file picked, once, and keeps its own copy in the app's storage, beside the log
and never in it: the log is what was logged here, the import is another app's record. The copy
is read again by the rules as they are now, so a better reader later reads more of the same
file. It isn't in Android backup; the file it came from is the copy to keep.

- The home screen says how many rows were read and how many were left out, by reason, and
  offers "Replace the history" and "Remove the history" (which asks first; the log stays).
- The file is read apart from the screen, so turning the phone doesn't lose the pick. While it
  is read the screen says so and offers Stop, for a file on a drive that never answers.
- A file that is too big (5 MB), can't be opened, or has nothing that reads as a history is
  refused with the reason, and the history already kept is unchanged.
- **A day in both counts once**, as it was logged here: imported rows from a day the log has
  are not read. Within a day order isn't known, so the two are never mixed.

A row becomes history only when all of it can be read. Otherwise it is left out and counted by
reason, so the import can say what it didn't take:

- **The exercise** is named by Spotter's own lists from the line as written, or from the name
  the row was filed under when an old row has no line. Not named in full: left out.
- **The weight** is one dumbbell's, brought down to a dumbbell on the rack (22.5 is read as 20).
  Heavier or lighter than the rack: left out, something else was held.
- **The count** is fixed by the name where it can be (a goblet squat, a single-arm press: one),
  then by the row's own hint (two dumbbells, one, a Ybell), then by the number madCode usually
  does the lift with, where they have said one (`usual` in the movement list: a deadlift two,
  a tricep extension one in both hands), else unknown: a lunge varies. The import card says how
  many rows took the usual number.
- **The reps** are a side. The row doesn't say whether its numbers count a side or both, so the
  asked number is matched to the line: against "12 total", asked 12 is both sides (8 done is 4
  a side) and asked 6 is a side. Where it can't be told (10 against "12 total", 8 against
  "4 each" with nothing asked), the row is left out.
- **A ladder** has one weight, its heaviest round's, so it can hold a number but not raise it.
- **Not finished** (`incomplete`) counts as "felt hard", which delays a step.
- Left out too: rows marked skipped, with no weight, where form broke down, where an easier
  version was done, and rows dated after today.
- A file too long to be a history keeps its newest rows and says how many it didn't take.

The same reading drives the replay tool ([REPLAY.md](REPLAY.md)): each class in a history file is
given the sheet the coach would have made from the days before it, beside what was lifted, as a
session file for the scorecard. It runs on a laptop with no phone, for grading in private.

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

- **Home**: the name, the update box when a build is waiting, today's sheet, then the imported
  history and the backup folder (each with what to do about it), and the version at the bottom,
  copied in one tap.
- **The sheet** (`SheetView`): the class, its date and start ("2G · Sat 21 Nov · 9:50"); notes
  when the board is borrowed or from an earlier day; the warnings; then each row in board order
  under its block, with the board's own words, what Spotter read beneath, and the number on the
  right. A tap on a row shows why, and stays open through a rotation. With no class booked,
  no board yet or no calendar, it says which in a sentence. The board kept on the phone shows
  at once and the fetched one replaces it, so weak signal never blanks the sheet. TalkBack
  hears the number in words ("two 30 pound dumbbells, up from last time"). Warnings
  are a twinge last time, a line Spotter couldn't read, and a combo part with no number.
- **The calendar** is asked for once, on first open. Refused, the sheet explains what is read
  (titles and times of entries from two days ago to tomorrow; the earlier days are for a class
  not yet logged) and offers the ask again, or Android's settings once Android stops asking.
- **Log** ("Log this class" on the sheet). The same rows. Each weighted row is "✓ as listed"
  (the sheet's number and the board's reps) or "changed". A line with a range ("8 - 10 x") has
  no tick: it shows a button for each count, in the board's own numbers, and the one tapped is
  saved at the sheet's number, so no reps are logged that weren't seen (a range wider than seven
  counts shows its two ends; an odd total keeps the tick). On a row being changed, a count sets
  that set's reps and leaves its weight alone. "Changed": the heaviest
  dumbbell picked from the whole rack in one tap, one or two, and reps on a stepper. Opening
  "changed" logs nothing until a weight is picked, and the count is never guessed: it comes
  from the row's words, the sheet's number or the last log, or the row asks "One dumbbell or
  two?". Tapping "changed" again closes the rack. With "felt easy / hard", "twinge", "had to stop"
  and "easier version" one tap away. A row with no number yet has only "changed". Untouched rows
  stay unlogged, never "skipped". A row ticked as listed shows "Felt easy" beside the tick, one
  tap, since it is what lets a weight go up after one session. Save writes events (`Logging` in the coach, one transaction):
  a new row is logged, a changed one corrected, one cleared removed, an unchanged one left
  alone. Reopened, the screen shows what is logged. A class's own sheet keeps the numbers from
  before it; the next class reads what was done. With two classes in a day, a class reads a row of
  the other's log only when that row was first logged before it started, so the morning's
  sheet never rests on the evening's lifting.
- When the board changes under a log (the class's own board replaces a borrowed one, or its
  poster revises a line), what was logged follows the exercise: an entry whose row is gone
  carries over to the first free row with the same exercise and the same kind of count, keeping
  its own words, target, weight and reps, also when corrected there: 8 done of 8 asked stays
  that, whatever the new row asks. The next save settles it on that row. Entries with no such row are listed under "Logged, but not on this
  board", each with Clear; they still count until cleared.
- A ladder that changed weight between rounds: once a weight is picked, "+ Each round" gives
  each round its own, on a stepper along the rack. Each round starts as a copy of the one
  before, and stepping a round takes the later ones that matched it along, so a ladder that
  climbs is one tap a round. "One weight" goes back to the rack, at the heaviest.
- An exercise the board missed: typed under the rows as the board would say it ("8 x chest
  press") and read by the same reader, it becomes a row under "Added by you" and is logged
  like any other. A line Spotter can't name in full, one with no weight, one with no reps
  or time, or one asking more than 99 reps isn't added, and says why. A log from a row since
  gone is never carried onto an added row: that one is a set of its own. An added row not yet
  saved has "Remove".
- A logged row whose line no longer reads in full (the word lists changed) still shows what is
  logged, with Clear; nothing new can be said of it until it reads again. Only a logged one is kept: reopened, the class has the
  row again, built from its own entry. The scorecard is told the sheet showed no number for
  it, since it was never on the sheet before class.

- **Progress**, a card under the sheet once there is something to say (`Progress` in the
  coach). "Went up in the last 4 weeks": an exercise whose latest session was clean (every set
  at the top of what was asked) and heavier than every clean one before it at the same reps
  and the same number of dumbbells, so a new best, not a return to old ground. "Next", for
  exercises lifted in the last 4 weeks: up a dumbbell next time, or one more clean session
  from it. Both are read by the sheet's own rules from the same history, so the card never
  says what the next sheet won't. Eight lines of each at most, the most recent first.
  Below them, "The sheet's numbers, last 4 weeks": of the rows logged on the phone that the
  sheet had a number for, how many were done as suggested (at the number, every set reaching
  what was asked), lifted heavier, or lighter or short. Counts, not a grade: the scorecard
  grades, on the export. Left out, since the two can't fairly be compared: a row the person
  added, an easier version, a ladder logged by its heaviest round, a lift with another number
  of dumbbells than the sheet said, and a row since corrected onto another exercise or other reps
  than its number was shown for, or carried to another board's row and given another weight there. Rows the sheet had no number for aren't counted either: an entry
  from before the log kept the sheet's number looks the same as one.

Later: sorting the review list's words, sharing a board for others, the watch.

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

## Backup

The log is kept three ways: on the phone, in Android's own backup (the database only), and as
files in a folder madCode picks on the home screen, such as Downloads or a synced folder.

The files are written after each save, once a day, and when a folder is picked. Only a file
whose text changed is written, and a class whose rows were all removed loses its file. Spotter
touches only files it wrote, by name, and writes one again if it has gone from the folder. The
home screen says which of four states holds: the log is only on this phone, it will be copied to
the folder, it was copied (and when), or the copy failed and the folder needs choosing again. A
new folder gets every file afresh; the old folder keeps what it had.

What is written (`Export` in the coach) comes from the event log alone, since each event keeps
the board's words and what the sheet showed:

- `session-<date>-<class>.json` for each class with rows logged: a `gym-session/1` file of its
  rows as last corrected, in the order logged. Rows left alone aren't in it, nor a row with a
  weight no load can have. `suggested_weight` is what the sheet showed when the row was first
  logged; a correction keeps it. A class name that isn't plain, or that would match another's
  that day, carries a mark so no file overwrites another.
- `spotter-log.jsonl`: every event, a line each, corrections and removals included
  (`round_lb` is a ladder's weight for each round, when said; `lb` is then round one's). When the
  store couldn't read some events, the first line says how many.

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
Beside it, the board corpus is to be graded line by line (not built yet: BACKLOG, "the corpus
grade"): how many rows are read fully, unsure, or wrong against hand-checked answers. Wrong
stays at zero; unsure falls as the review list grows.
Spotter switches on for real when, over the same weeks of real classes, it numbers more rows
than Carry, gets more close, and is high no more often. Shared weights are on, at madCode's word
(2026-11-23), and the replay tool can switch them off to judge them the same way. Factors
between variations that do change the weight are judged there before they are turned on.
