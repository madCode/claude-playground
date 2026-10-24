# Design

## The constraint everything follows from

No phone in the studio. So Carry is never open while you train — it is a
glance before and a form after, and anything that assumes otherwise is
wrong for this app. Two screens for the studio, plus a third read at home, no live timer, no during-workout logging.

Measured against the owner's real sheets: a typical one carries **a handful
of numbers worth remembering, and most often no weight changes at all**.
That is the whole information budget. A screen that gives every exercise
equal weight has failed, because most lines say "first time, go by feel" or
"no weight needed" — nothing to carry.

## What it is for

**Make sure the owner knows when they can push themselves.** Their words,
2026-10-14, and the line that settles arguments the other principles cannot.

The app informs; it instructs only where it has real evidence. Where the
evidence is thin — which is most lines, most days — it shows what was
actually done and leaves the judgement with the person lifting. That is not
a placeholder until the coaching gets better. It is the correct division of
labour, and it stays correct as the data thickens.

It was decided against a measurement rather than a preference. A progress
rule that *acted* on its own signal was built, measured ten ways, and
rejected every time: each formulation that raised how much of their ability
the lifter used also raised how many sets they failed to finish, roughly a
point for a point. The cause is physical, not a tuning problem — the
smallest step a gym stocks is a whole dumbbell, 17–20% at the low end of the
rack, against a rep-conversion signal worth about 5%. The instruction was
three to four times larger than the evidence that earned it.

So the same rule ships as information: the progress dots count, and nothing
acts on them. The weight stays whatever the estimate said.

Two consequences worth stating, because they look like missing features:

- **Silence is an answer.** A line that says "first time, go by feel" is the
  honest output when nothing comparable has been lifted, and filling it with
  a confident number would be the failure, not the fix.
- **A warning is worth more than a suggestion.** Telling someone they did
  not finish a weight last time needs no evidence about what they *should*
  lift, and it is the one thing they cannot reconstruct from memory in a
  car park.

## Screens

**Before** — what to carry in. Ordered by what you can't guess:

1. Weights that **changed** since last time, with what they were. A bump is
   the only number you'd get wrong from memory, because by feel you repeat
   last session's weight.
2. The other history-backed numbers, one line each.
3. Everything with no number — first-timers, bodyweight, untracked — folded
   into a tail of at most two lines: by feel, and no weight. Present, not
   prominent.

**After** — what you did (madCode approved the minimum page in #127,
mockup v4). One row per exercise, two lines tall: the name with *hurt* at
the end of it, then a weight box, a reps box (or on a timed row a *done*
tag), *easy*, and *mod* on a row the coach gave no weight. Plus **add a
row**, for anything the sheet didn't list. Visible labels sit above the boxes
("lb", "reps", "time"), stay put once something is typed, and are the
words TalkBack reads on each box; the chips sit level with the boxes.

**Each row says "I did it" with its own fields.** There is no separate
"did it" control, and there is no way to say "didn't do it":

| Row | Says it happened | Untouched |
|---|---|---|
| Weighted | a weight. Blank reps mean all of them. | not done |
| Bodyweight | reps. The empty box shows the runner's count faintly, and a tap fills it in, so "did them all" is one tap. *easy* or *mod* say a set happened too. | not done |
| Timed | *done*, a tag in the reps box's slot under "time" | not done |
| Added | its name | — |

The faint count is `prescribed_reps`, the coach's number, not the board's
words: "10 total" is 5 a side, and gym-coach's importer marks a set short
when its reps fall below that number.

- **A tap fills it, not focus.** Focus also arrives by Tab or D-pad, which
  would record sets nobody did. A scroll that starts on the box, or a
  long-press for Paste, doesn't fill either. TalkBack hears "empty, prescribed 10" and
  gets a "Fill in 10" action.
- **Only where the runner suggests no weight.** On a weighted row blank
  reps already mean "all of them", and a stray tap would record a set
  with no weight. The faint count still shows there.
- **Not on a range.** "6-12" has no single count; its floor would go into
  history as the reps done, so the box stays plainly empty. The tally reads "N of M noted · K
added · anything untouched counts as not done". *hurt* on its own is not
"noted": it is the commonest reason to leave a row.

**A row of several rounds says what to log.** A ladder (`8 / 6 / 4 x`) is
one row standing for three sets. Under its name the row says "3 rounds ·
log your heaviest set": one true weight and count is what the coach's
arithmetic uses, and saying so keeps a tired day from changing what the
record means. Only on a row that takes a weight: on a bodyweight ladder
every round has the same load, so "heaviest" picks nothing. The count
comes from the coach's `rounds` field, never from
the board's words, so a file without the field, or with a value of the
wrong shape, shows no hint and loses nothing else.

**Why a blank bodyweight row isn't "done".** A blank row can't be told
apart from one not done. If blank meant done, movements skipped or
forgotten would enter history as completed sets, and a wrong number in
history is the worst outcome here. One tap on the faint count is the
price.

**Checked against gym-coach's real importer** (`session_file.to_history`),
on a file written by Carry's own save: each of the rows above, plus *easy*
alone and *mod* alone, becomes a complete set. A row with only *hurt*,
and an untouched row, become nothing.

**What went, and why nothing the coach reads went with it:**

- ***skip*.** Every coach rule already leaves a skipped set out, exactly
  as it does an untouched row, so the control said nothing an untouched
  row doesn't. An older file's `skipped` still reads: the row looks
  untouched, its name faint, and a past session still shows the mark.
  Any edit to such a row clears the flag, so a row is never skipped and
  weighted at once.
- ***hard*.** The coach treats a completed set as completed however it
  felt (madCode's decision, 2026-10-16). An older file's `hard` still
  reads and is written back as it was. On this page a row only it
  answers reads as blank, and tapping *easy* clears it. gym-coach's
  importer still counts that row as a set, though, so a past session
  shows it with a *hard* mark and the Since tab counts its day.
- **A ✓ on every row** (mockups v2 and v3). Each row's own fields already
  say it, and tap-to-fill keeps a bodyweight row at one tap.
- ***done* as a chip.** It moved into the timed row's reps slot.

**Type is a step up on this page** (`Look.After`): names 20sp, chips
15sp, the tally 17sp, the post preview 14sp, buttons 17sp. Box contents
stay 15sp. It is filled in from memory with cold hands, and checked at
twice the font size, where the timed row's *done* tag widens rather than
wrapping.

**Add takes you to the row.** A tap on *add a row* puts the cursor and
the keyboard in the new row's name box, and scrolls the list to it if it
is off screen: below the button on a borrowed day, above it on a normal
one. While the last added row is still completely empty, Add makes no
second one and goes back to that row instead. The focus is spent by the
tap: a rotation or a restored draft brings the row back, not the cursor.
Every row on the page is keyed by the row itself, so an added row pushing
the sheet's rows down never hands one row's typing to the box of another.
An empty added row is harmless: the save leaves it out of the file and
never refuses over it.

**Where *mod* shows** is decided by the row, not by what is typed, so the
chip never vanishes mid-entry and the row never jumps:

- A sheet row whose variant's `implement` is **bodyweight, band or trx**
  (the coach's `NO_WEIGHT_IMPLEMENTS`): always.
- A sheet row on **any other implement** (a dumbbell, a kettlebell): never,
  even with no suggested weight, which is most weighted rows.
- A sheet row with **no readable implement**: when `suggested_weight` is
  null, the same test as tap-to-fill.
- Any sheet row with *mod* **already set** shows it, so it is never on
  where it can't be seen and turned off.
- An **added row**: while its weight box is empty, or while *mod* is on.
  The coach suggests nothing for it, so the box is the only sign.

*mod* is "I did the easier version" — knees, a box under the step-up. It
exists because **a large share of this owner's logged sets carry no weight**, and
a third of a typical sheet is bodyweight: for all of that the coach's
"what weight next" model has nothing to say. It is the only progression
axis those movements have, and three clean modified sets earn "try one as
prescribed". It also fixed a wrong message rather than only adding a
missing one — ten clean knee push-ups are *complete*, so three of them used
to earn "full form 3x in a row, push it", congratulating the owner on form
they had not used. The ladder itself stays in the owner's head: naming the
next rung for every movement is a knowledge base we do not have and they
do not need.

*done* is "I did it, and there is nothing to say about it", on a row the
board prescribed in seconds: no rep count and no weight, so a finished
45-second plank would otherwise reach history as nothing at all. Nothing
downstream reads the field by name; what it changes is that the row
reaches the importer at all, as a complete set.

The same gap existed one layer up: the *since you started* screen dropped a
whole session unless some row carried a number, so a class recorded only
with markers counted as never having happened. It now asks the same
question the row does — of the sheet's own rows. Whether a session logged
entirely by hand belongs on that screen is still open.

*easy* and *hurt* are the two things that cannot be derived:

- *easy* — finishing a set says nothing about what was left in reserve, and
  this is the only signal measured as able to raise the owner above their own
  habit, since every other number the coach has comes from their past
  timidity.
- *hurt* — "couldn't finish" is already derived from reps against prescribed
  reps, so a *fail* marker adds nothing; checked against the real history,
  every use of it sat on a set already flagged incomplete. Pain is different
  on two counts. Bodyweight and timed lines often carry no prescribed rep
  count at all, so nothing is derivable and the marker is the only signal,
  which is exactly the shape the real *knees* entry took. And a set can be
  *completed* and still hurt,
  which a reps comparison cannot see; the cost of missing it is that the
  coach counts a clean set toward a streak and suggests more weight next
  time.

The old breakdown vocabulary (*fail*, *knees*, *modified*, *half*) collapses
into this. `breakdown` stays in the schema so old history still reads, but
Carry never writes it; anything that needs words goes in the note.

### When there is no sheet to show

Nine states, and they are separate because each needs different words. A
wrong diagnosis sends someone to fix the wrong thing:

| State | What it means | What it offers |
|---|---|---|
| Reading | the first read, which happens off the main thread | nothing; it is brief |
| Pick your folder | nothing chosen yet | the picker |
| Lost the folder | chosen, but the grant is gone — asked of the system, never guessed from an empty folder | the picker |
| Nothing to carry | folder readable, no session written yet | the picker |
| Can't read that session / Carry is out of date | a file that is there but truncated mid-sync, or written by a newer coach | the picker |
| No format label / Unknown format label | a file that reads as JSON but has no `"schema"`, or one that isn't `gym-session/1` or a later `gym-session/N`; the message names the file and quotes an unknown label | the picker |
| Folder isn't answering | the read passed 15 seconds; a provider can block forever with no timeout to set | the picker |
| That's your whole phone | a whole volume picked rather than a folder; kept through the read when Carry comes back from the picker, and gone at the next return to the app | the picker |

**A past sheet is headed "No sheet for today"**, with "Showing the most
recent" under it: on a rest day that is the ordinary state, not a failed
sync. A sheet dated ahead of today, or a date the app cannot parse, gets a
warning above the numbers in the borrowed-block warning's voice, because
those look authoritative and are not about today.

**Known limit: two machines, two notions of "today".** The date in the file
is a calendar date written by the Mac; "today" is the phone's. They agree
while both are in the owner's own zone, which is the normal case, because
the date means "the day of the class" and the class is local to them.
Travelling far enough east or west to cross local midnight relative to the
Mac will mis-report by a day in whichever direction. Left alone
deliberately: carrying a zone in the schema is the companion script's
decision, not the app's, and a tolerance band would blunt the signal this
line exists to give. Worth revisiting if it ever actually happens.

**A sheet already on screen outlives all of them.** `keepLastGood` holds the
last good session through an empty or unreadable refresh, because a sync
replaces a file rather than editing it and there is a window where it is
absent or half written. Briefly stale beats vanishing while being read.

### What a person typed is the only irreplaceable thing here

Everything else on screen can be re-derived from the session file. The
numbers someone just entered cannot, so they get the protection:

- **Kept across process death**, in a draft rewritten on every keystroke.
- **Never wiped by a newer file arriving.** Tomorrow's sheet, or the day's
  second class, keeps what was typed and says the file moved.
- **Never written blind.** `ref` is only an index, so a runner that re-runs
  and renumbers its rows would turn an edit meant for a deadlift into one
  against a lateral raise. The draft keeps the bytes the numbers were typed
  against, and a save compares each edited row's identity between then and
  now, refusing if they differ.
- **Never written over a file that changed since it was read** (madCode,
  2026-10-17, the simple version). Just before writing, the save reads
  the file again and compares its content with what the page read, not
  its timestamp, since sync services touch those. A leading BOM, CRLF
  line endings and JSON formatting don't count as a change.
  - A changed, empty or unreadable file refuses the save: "This session
    file changed since you opened it, so nothing was saved." Nothing is
    written, the typing stays on screen, and under the message each typed
    row is listed as text ("10 x deadlift: 95 lb, 8 reps"), so the numbers
    can be entered again whatever loads next.
  - One button, *Drop my numbers, load the new file*: full size, well
    clear of *Try again*, labelled with what it costs. Only when the file
    on disk reads; for a missing, empty or broken one there is nothing to
    load, so the refusal and the list are all there is.
  - The baseline is what Carry itself last left on disk: after a good
    save, what it wrote. After a write that failed and left the file
    damaged, it is whatever that write left, so *Try again* repairs it
    unless something else has changed the file since. The "may be
    damaged" warning stays until a good save.
  - **Not handled:** the refresh on every resume can still put a different
    session on screen while typing is pending, as before. The session
    file is temporary, so that is left for when the brain moves to the
    phone.

### Numbers the importer would refuse

The importer refuses a **whole session file** over one bad number, so one
typo would lose every other number typed that day. The After screen checks
the same rules as `validate` in the companion's `session_file.py`:

- A weight is from 0 to 1000.
- Reps are a whole number from 0 to 1000. The box takes one `.`, so `8.5`
  can be typed; it is held as typed, not stored as blank.
- An added row holding anything needs a name.

A box that breaks one gets a brick border, a line under its row ("Weight
2500 is over 1000. Fix it to save.") and the same words as its TalkBack
error. Save is refused while any row is wrong, with one message naming
the rows ("2 rows need fixing: …. Nothing was saved."), announced as it
appears. Nothing is clamped or dropped: 2500 turned into 1000 or 250 is a
wrong number, and the owner fixes the box. The patch refuses the same
numbers again before writing, in case anything else reaches it.

## Rules worth keeping

- **A blank row is blank.** Never silently "skipped". The old survey logged
  unanswered items as non-answers, and a wrong number in history is worse
  than a missing one because nothing downstream can tell.
- **Never invent an exercise identity.** Rows come from the session file
  with their identity already attached; Carry fills in numbers and hands it
  back. Only an added row carries a name a person typed, and it is flagged
  as unmatched rather than guessed at.
- **Partial is fine.** The file round-trips, so a half-filled session can be
  finished later. Nothing is all-or-nothing.

## Visual direction

**Chalk on slate, colour-coded.** Chosen 2026-10-11 from three directions
(slate / logbook card / signage) and then from three amounts of colour; the
owner picked the middle one plus one piece of the loudest.

Graphite ground, chalk-white type, warm-grey neutrals so the dark surface
reads as a room rather than a dashboard. Dark first, because 6am in a lobby.

**Two colour systems, kept apart on purpose.**

- **Amber `#E0A33E` means one thing: this weight moved.** Among the numbers
  it appears only on a weight that moved; outside them it marks the
  selected tab, the save action and *easy*. If a figure is amber there is
  something to remember, and that is the whole reason the screen exists. A `+5` chip on
  that row names the change rather than leaving it to be subtracted.
- **Hue says what kind of movement it is**, on a 3px left edge and never on
  a number: legs `#8AD17F`, push `#5FA8E8`, pull `#C98BE0`, core `#4FC7C0`,
  combo `#F0866E`. This is free information — the knowledge base already
  carries muscle-group tags — and it turns "what sort of day is this?" from
  six lines of reading into one look.
- **Brick `#B4553F` marks *hurt*.** A warning, not an accent, and never
  styled to sit beside amber on the same row.

**Streak pips.** A held weight shows how close it is to going up: `●●○` for
two clean sets of the three a bump needs. It is the system's dullest string
("hold 40 lb, 1 more clean time before going up") turned into something
worth closing.

Known and accepted: **the pips will be empty for a while.** On the real
history the streak rule has never fired once — three of the four movements
logged more than once have their sets split across different variant keys,
so nothing reaches two in a row. The picture is honest; it is the rule
underneath that needs fixing, and an empty picture is the clearest possible
statement of that.

Type: **Barlow Condensed** for exercise names, which get long
("single-arm row · split stance") and must not wrap; **IBM Plex Mono** with
tabular lining figures for every weight, so columns align and a digit never
shifts width as it changes.

Rejected: a session hero header. It looked good and cost a third of the
first screen to restate what the rows already said, on a screen whose
measured job is three numbers in one glance.

## Looking back

A third screen, read at home and not in a hurry — the opposite of the other
two, which is why it may spend space on context they cannot.

**The folder is the history.** One file per session, written once by the
runner and completed once here, plus back-filled records for the days
logged before this app existed. Nothing keeps a rolling total, so there is
no second copy to drift from the first; the sum is computed from the files
every time it is asked for.

**It does not open with a chart.** Measured against the owner's real
history: 20 weighted lifts, 19 of them distinct movements, exactly one done
twice at the same variant. A weight-over-time line would be a row of
disconnected dots and would read as "you are not progressing", when the
truth is that the studio almost never repeats a movement. So the bands are:
what is nearly earned, the heaviest in each family (the axis the data is
dense on, because the same muscle groups come round constantly even when
the movements do not; a number only, because a family's sets are different
movements at different reps and bars of them read as a trend they are
not), every session newest first, and a plain statement of
why there is not more. That last one is load-bearing: without it, every
empty row on the other screens looks like a broken app.

**One session opens.** Tapping a row in *every session* shows that day row
by row: what the sheet asked for, what was entered against it, and the feel
markers — with the rows nobody filled in **shown blank**, because which
lines were left empty is half the answer to "what did I do that day". The
rows the owner added by hand come after the sheet's own, which on a borrowed
day is the whole session.

A row with nothing on it at all reads as blank; a row carrying only a feel
marker, or only *done*, does not, because for the movements that carry no
weight the marker is the whole record. An older file's *hard* is one of
those markers here, since gym-coach's importer counts it. The same test
decides whether the session appears on that screen at all.

It is a **record, not a form**, and says so. There is nothing on it to type
into, because two ways to write one file is how a wrong number gets in. A
day left unfinished has a *Finish this session* button instead, which puts
that sheet on the After tab, so it is filled in with the same boxes and
saved through the same checks as the newest one, the changed-file refusal
included. A back-filled day — a record of training from before this app —
is never offered, and says so.

**Finishing an older session.** The sheet the After tab holds is pinned by
its `sheet_id`, kept across a process death but not a fresh launch, so
opening Carry from the launcher is always the newest sheet. The id starts
with the sheet's date, so only the files named for that day are read. While
it is pinned, a strip under the tabs says *Finishing <day>, an older
session* on every tab, with a *Newest* button back: the Before tab is read
in one glance, and an old sheet there must not pass for today's.

- The After tab opens once the asked-for sheet has landed, not at the tap.
  Until then After still holds the newest sheet, and typing there would go
  into the newest file.
- Switching either way is refused while there is unsaved typing, since
  there is one draft and typing on another sheet would replace it. The page
  and the strip say to save or discard first. A partial save is fine: a
  session can be finished in two sittings.
- A pinned sheet that no file for its day lists has gone: the pin drops and
  the newest comes back. One that is listed but won't read is a sync part
  way through, so the screen keeps the sheet it had, pin and all.
- A back-filled day is never opened as a form, even if asked for.
- The coach re-reads every past file the day after and follows an edit, so
  a session finished late reaches its history.

The rows travel on the day rather than being re-read when the screen opens.
The file was already parsed, off the main thread, by the read that built
this screen; re-reading would add a loading state and a missing-file state
to a screen whose data is in hand. There is no nav library for this — the
app is three tabs — so the open session is one saved id inside the Since
screen, plus a `BackHandler`. The id is the session's own `sheet_id`, never
its place in the list: files arrive and the list reorders, and two classes
in one day share a date. A session that has left the folder closes rather
than being held, or it would spring open again over whatever is being read
when the file comes back.

**It derives, it never coaches.** Totals, maxima and counts are facts about
rows already in the files. A streak is not — it comes from a rule about
what earns a bump, that rule lives in the runner, and a second
implementation here would eventually disagree with it about someone's
training. So the progress band carries the runner's own numbers through,
and what counts as *the same exercise* is a key the runner writes.
A first version that worked that out locally, by hashing the whole variant,
made every lift on a sheet look distinct when one pair was the same
movement — it was including the fields the runner deliberately leaves out
of the key. Where a file carries no key, the count is withheld rather than
guessed.

**That band claims only what every row in it is: part way to a bump.** The
runner counts a streak from the first clean set, and this owner starts
nearly every movement fresh because the studio changes the setup most
classes — so most of a real sheet arrives on the same rung, one clean set
of the three a bump needs. A heading saying they were *close* to anything
was a claim the rows could not support, and *each one* was a claim about
scope: streaks strictly in progress, and five of those.

**It is a history, not the newest sheet.** The owner's call, 2026-10-16:
*"'Since' should be a history, it should include the stuff where I am
almost at a bump across. Anything where if I happened to do that exercise
today at that level, I would achieve a bump."* So the band covers every
movement across the sheets, each taken from the newest sheet that lists
it. Reading the newest sheet alone blanked the band whenever that sheet
had nothing part way, a plank-only day for instance.

- **The same movement** is the runner's `key`. A row with no key is only
  taken from the newest sheet, since nothing could tell whether a later
  sheet listed it.
- **The newest listing decides.** If it is out of the band (skipped,
  lighter, marked *hurt* with a weight, a streak finished or none
  written), the movement is out. *Hurt* ends the runner's streak at any
  weight; a hurt row with no weight isn't read by its streak rules, so
  that count stays. An older sheet's count never stands in for it, because that count
  is from before the newer set. A row on the newer sheet that didn't
  decode still counts as a listing, by the `key` in its raw JSON. On one
  sheet listing a movement twice, a skipped copy doesn't hide the other.
- **A sheet that won't read stops the band.** It may list any movement
  with a newer count, so rows from older days are left out and the band
  says "A sheet didn't read, so nothing older than it is shown here." The
  other class on that sheet's own day still counts, since both were
  written from the same history.
- **A row from an older sheet shows that sheet's date**, beside the pips
  under its name and in its spoken label ("as of Sun 4 Oct"), or "as of an
  older sheet" when that date won't read or isn't before the newest
  sheet's. The runner counted it from
  the history it had when it wrote that sheet. A later set that left no
  row with that key in the folder, such as a row typed by hand or a day
  with no sheet, is one Carry cannot see. So the row says how old its
  count is rather than claiming to be current. The runner's streak has no
  time limit, so neither does the band: the owner chose to keep old
  counts for now and revisit as data builds.
- **Order and cap are unchanged.** Fewest sets away leads; ties keep the
  sheets' order, newest sheet first and each in board order; five rows.

**The two numbers on a row are about different things**, and the row says
so. The pips count clean sets; the weight is what the runner says to use
today. On its converted-streak rule those differ — two sets held at 30 for
eight reps earn "2 of 3" beside an estimate of 25 for twelve, and nothing
was ever held at 25. The sighted row shows three things side by side and
claims no relation between them; the spoken label says *"2 of 3 clean
sets, next at 25 pounds"* rather than joining them into a sentence that
would.

A row leaves it the moment the owner's own answer contradicts it: logged
**below** the weight the count is about. That is the runner's own signal
that the next bump is blocked. An older file's *hard* is not: the runner
counts a completed set however it felt, so such a row stays. Logged at that weight
stays — the count is then stale by one and understates, which is the safe
direction — and logged heavier stays, because going up unprompted is the
thing this app exists to notice. The streak itself is never re-derived
here, only believed or withheld.

Fewest sets away leads, which is the question the row answers — another
clean set moves it — and not how far along, under which a movement two
sets from a bump outranks one that is a single set away. It is capped,
because a band of a dozen rows takes the screen from the three underneath;
what the cap leaves out is counted, not dropped, and on a sheet where
everything is on the same rung the order is the board's rather than a
ranking.

## Telling them a sheet arrived

The glance in the lobby used to depend on remembering to open the app. A
session file appears in the synced folder hours before the class and
nothing said so, which made the Before tab's whole purpose conditional on
the owner's memory.

A `WorkManager` check runs hourly — not the fifteen-minute floor, because
the file lands hours ahead and the extra precision would buy nothing but
battery — lists the folder, and notifies when a name appears that is new
AND dated today.

**When it notifies** is the class's business, not the clock's. The runner
writes `starts_at`, the class's local start, and a sheet that has one is
announced from 90 minutes before it, which with an hourly check means
within the hour after that. A sheet without one (a backfill, a hand-written
day, anything older) waits for 06:00, so one found overnight doesn't ring
in the small hours. Each sheet is held on its own, and a held sheet is not
recorded as seen, so the run that's due still announces it. A `starts_at`
that won't parse, or names another day, is treated as absent.

Tapping it brings the app forward, on whatever tab the owner left. It does
NOT force the Before tab, and that is a deliberate reversal: forcing it
needs `CLEAR_TOP`, which with a standard launch mode finishes the running
activity and rebuilds it, taking the ViewModel — typed weights, added
rows, an in-flight save — with it. Landing on a particular tab is not
worth losing numbers for, so the intent is `SINGLE_TOP` and the folder is
re-read on resume as it always was.
A device test checks it on an emulator on every PR (`NotificationTapTest`):
a weight typed and not saved is still in its box after a tap on the real
notification.

**A debug build holds six Android permissions, and this feature brought
four of them; a release build holds four.** `aapt2 dump permissions` on the built APK is the only honest
source for this, because a library brings its own. It also lists
`<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which
androidx.core defines for the app's own receivers; it grants nothing
outside the app.

Three are ours: `POST_NOTIFICATIONS`, required on API 33+ to post
anything, and, in a debug build only, `INTERNET` and
`REQUEST_INSTALL_PACKAGES`, for the update below. `check` fails if a
release build's merged manifest asks for either. Three more arrive with
`androidx.work`, and the app can't opt out of them while using it:

| permission | why WorkManager wants it |
|---|---|
| `WAKE_LOCK` | hold the device awake long enough to finish a check |
| `RECEIVE_BOOT_COMPLETED` | re-register the periodic work after a reboot |
| `ACCESS_NETWORK_STATE` | read connectivity, for network constraints: the debug build's update check and Wi-Fi download use them |

WorkManager also asks for `FOREGROUND_SERVICE`, for expedited and
foreground work. Carry has neither, so the manifest removes it and `check`
fails if it comes back. What it might have broken is the check coming back
after a reboot, so CI's emulator job reboots and checks
(`tools/reboot-check.sh`): WorkManager's jobs aren't kept across a reboot,
and its boot receiver schedules them again without Carry being opened.

`ACCESS_NETWORK_STATE` permits *reading whether a network exists*, not
using one. The notification text carries the class alone, because a lock
screen is not somewhere the owner's weights belong.

Until 2026-10-16 this app declared no permissions at all, and that was
checkable by opening one file. That is no longer true, and the honest
replacement is the table above rather than a smaller number.

Three rules sit in the decision rather than in the plumbing, and each
exists because its opposite is the failure the owner would feel:

- A file already seen is never announced twice.
- A file dated other than today is never announced, so backfilling an old
  day stays silent.
- When nothing has ever been recorded — a fresh install, or a reinstall
  that lost the set — nothing is announced at all. Re-announcing nothing
  beats announcing a backlog of sessions logged weeks ago.

Two further rules exist because an empty listing lies. `list()` answers an
empty list for a folder nobody picked, for a revoked grant, for a query
that threw, for a provider still restarting, and for a folder that is
genuinely empty — and none of those can be told apart from the answer.

So the watcher returns early when no folder is readable, and when one is,
it UNIONS what it saw into the record rather than replacing it, pruning by
date so the set cannot grow for ever. Replacing would erase the memory on
any momentary blip and announce today's sheet again on the next
successful run — the "announce everything" failure by a side door, and it
would repeat on every blip.

A name dated AFTER today is not recorded at all. The runner writes
tomorrow's sheet the evening before, which is the case this feature exists
for; storing it on sight meant that when its day arrived it was already
seen and was silently never announced.

## Asking whether a newer build is out

The app's only network use. The owner chose it on 2026-10-16
([#107](https://github.com/madCode/claude-playground/issues/107)) over
routing the build number through the synced folder, so a debug build
declares `INTERNET` for this alone, and a release build doesn't declare it.
`DebugUpdates` works like newspaperss's:

- **Only a CI debug build asks.** The installed build number comes from the
  version name (`0.1.0-debug.131+…`); a local or release build has none and
  never asks.
- **At most every three hours**, on a return to the app. Any answer counts
  as asked, refusals too, so a rate limit isn't spent again on every
  resume. No answer at all (offline) doesn't count, so it asks again next
  time.
- **It sends nothing of the owner's.** A GET of the `carry-debug` release's
  public JSON. The newest build is the highest `carry-debug-<run>.apk`
  among its files; a body over 256 KB is not read.
- **The Since tab says it**, at the top, even when nothing on it is
  countable (a folder this build can't read is when a newer build is most
  likely the fix), though only while a sheet is on screen; a newest file
  that can't be read at all shows its own message and no notice: "Build N is out — install it", in chalk rather than
  amber.
- **The whole request has 30 seconds**, not only each read, so a slow
  server can't hold the check open.
- **PR builds count too.** PR and main runs share one run counter, so an
  installed PR build is offered the next main build, which may not contain
  that PR.

A tap installs it from inside the app, in this order. The code
(`LiveUpdates`, `UpdateInstaller`) and the permission to install apps are in
the debug source set only; a release build has neither.

1. **Not over unsaved typing.** Installing replaces the running app, and
   nothing promises its saved state survives that, so the draft could be
   lost. With a draft for the session on screen, or a save in progress,
   the tap only says "Save the After tab first". The owner can type while
   the build downloads, so this is checked again just before the hand-off,
   and an install it stops says the same. A draft left from another
   session isn't on the form and doesn't count.
2. **Asks once for "install unknown apps".** Android's own switch, for
   Carry; the tap opens it, and the owner taps the notice again after.
3. **Downloads the fixed link** into the app's cache, following GitHub's
   redirect, with a 200 MB cap and five minutes in all. A partial file is
   never kept.
4. **Checks the file itself**: the same package, signed with the same key
   as the running app (every CI build shares the debug key), and a higher
   version code. Android would refuse another key too, but only after
   asking the owner. A file that fails the package or key check is given
   up on at once. One that is only not newer yet isn't: CI uploads the
   fixed-name file a moment after the numbered one, so a tap a few minutes
   later can find it.
5. **Hands it to Android's `PackageInstaller`.** The first in-app update
   always asks "Update this app?". On Android 12 and later, once Carry has
   installed itself, later ones may skip that, which is why it asks to. A
   session that fails before it's committed is abandoned, so nothing is
   left staged.
6. **Failures are said, and counted.** A download or hand-off that fails
   says why in a toast and opens the download link in a browser, so the
   old way still works. A failure Android reports after the hand-off is a
   toast only. Three failed installs of one build, or one Android says can
   never install (invalid, incompatible, a conflict), and Carry gives up on
   that build: the notice's tap then opens the download in a browser
   instead. A newer build is tried again. A failed download, the owner
   saying no to Android's question, or a phone too full to take the copy
   isn't counted. That last says to free some space and offers no link: a
   browser's download needs the same room. If Android itself answers that
   there isn't enough storage, a build the background downloaded is tried
   again 30 minutes later, once storage isn't low; one a tap downloaded,
   on the next tap or after the next check fetches it again.

The notice reads "Downloading build N…" and can't be tapped again until
Android's installer has answered, so a second tap can't start a second
download. If that answer is a confirmation while Carry is behind another
app, Android won't let it open there; it's shown when the owner comes
back, once. If there's unsaved typing by then, the waiting install is
dropped instead, and the notice can be tapped again. A question left with
Home sends no answer, so an install still unanswered five minutes after
the hand-off is dropped on the next return; asking again couldn't tell it
from an approved install still finishing. An answer about an older
session, from before a restart, doesn't end the attempt under way.
Failures count against the build in the file, which can lag behind the
newest one listed, so a newer build is never given up on untried, and a
file already given up on isn't tried again.

**In the background**, a debug build from CI also keeps itself current
without a tap (`LiveUpdates`, `LiveWorkers`):

- **A check every six hours**, on any connection, so a build arrives
  without Carry being opened. The three-hour limit above still applies,
  shared with the check on opening Carry.
- **The download waits for Wi-Fi** (an unmetered network) and enough free
  storage. It is checked like a tapped one, kept in the app's cache, and
  given up on after five failed tries. A tap on the notice then installs
  that file without downloading again.
- **The install waits for Carry to be closed with nothing unsaved.** The
  draft lives in saved instance state, which an install throws away, so
  the ViewModel keeps a mark on disk (`UnsavedMark`) from the first
  keystroke until a save or a discard, and while any save is writing. A fresh start with no draft
  restored clears it, since that typing is already gone. While Carry is
  on screen or the mark is set, the install is tried again later, 30
  minutes further apart each time. Both are checked again after the copy
  into Android's installer, just before the hand-off.
- **Nor while the folder picker is out.** The picker pauses Carry just as
  leaving it does, so an install then would end Carry under it and the
  folder picked would arrive nowhere. `MainActivity` notes each answer it
  sends another app for (`AwaitedAnswers`, on disk) and crosses it off
  when the answer comes, so a turn of the phone or Android dropping the
  process meanwhile doesn't let go of it. Only a fresh start of Carry, in
  a task of its own, clears one left behind: a start on top of an
  existing task (a launcher tap after the session notice opened Carry
  gets one) may have the picker below it.
- **And not while storage is low**, since Android's copy of the build
  needs room as the download did.
- **No question out of nowhere.** If Android wants the owner to confirm
  (the first in-app update, or below Android 12), the background attempt
  is dropped and the notice's tap installs it instead. A dialog appearing
  on its own, perhaps while the owner glances at the sheet in the lobby,
  would be worse than waiting. A failure in the background isn't toasted,
  but it is counted toward giving up, unless it's for want of room.
- Without "install unknown apps" allowed, nothing happens in the
  background; the tap asks for it as before.

The session files still leave only through the share sheet.

## Handing the block over

The preview is the whole of the egress control: the share sheet is the
only way anything of the owner's leaves, and the post is shown in full
before it goes. It carries no content description, because Compose offers
a text node line-by-line navigation only while its description is empty,
so a listener can step through the post a bullet at a time.

Intel for the owner's class is posted on a small minority of days, which is
why most of their history is borrowed from the other class's block. Nobody
posts theirs. So after the log is saved, Carry composes the post and shows
it; the owner sends it with their own app.

Three rules, and a review of the first version found it breaking all three.

- **It never republishes a block as another class's.** On a borrowed day
  the sheet *is* the other class's block — that is what borrowed means — so
  printing those lines under this class's heading fabricates intel, and the
  coach scrapes it back next week as genuine. The pollution this project
  exists to fix would become self-sustaining and public. On a borrowed day
  the post carries movements with **no rep counts**, and says in plain
  words that it is a report rather than the board.
- **It publishes nothing of the owner's.** No weights, and no performed
  reps either: an added row's reps are what they managed, not what the
  class was told to do, so printing them would leak a personal number *and*
  invent a prescription.
- **The rep count is the board's own wording, never the runner's working
  number.** `prescribed_reps` is what the weight math uses — an alternating
  "10 total" becomes 5 a side, a "6 - 10" range becomes its floor — and for
  most rows the two differ. Publishing it would tell the subreddit to do
  half the reps. The file carries `prescribed_text` beside it for exactly
  this, and a timed line carries `prescribed_duration_sec` instead ("45 sec
  of low plank", never "45 x").
- **The file is not trusted, even for a number.** `prescribed_text` arrives
  from the same writer as `suggested` ("try 45 lb (up from 40, held 3x)"),
  and it is both shown and published — so it is accepted only if it looks
  like a rep scheme, and a duration only if it is between one second and an
  hour. Anything else and the row simply carries no prescription. A field
  that cannot be validated is not published.
- **A typed name is shown back, not tested.** An added row's name is
  typed by hand, so a weight can end up in it. A digit test tried to
  catch that and failed both ways: it missed "deadlift twenty six" and
  "½ rack", and flagged real movements like "bicep curl 21s" and "90/90
  hip stretch". madCode's call, 2026-10-16: "show the typed name back, no
  digit check". So after a save, a line just above the Saved button reads
  *You typed: "deadlift twenty six". Is the weight in the weight box?*,
  naming every typed name. It is a line, not a dialog: a dialog on every
  save with an added row is one the owner learns to dismiss unread. It
  sits in the button's own list item, where the owner just tapped, and it
  shows with "Saved" and goes with it (the next edit, or a re-read on
  returning to the app). It is announced politely, so TalkBack reads it
  without moving focus. Each name is shown on one line, as a directional
  isolate, and cut with an ellipsis only past 200 characters. Carry matches nothing and the
  file is unchanged. The caption over the post says what is true: "The
  movements only. A name you typed goes out as you typed it."
- **It is built from fields this app owns.** `canonical` and `components`
  are parsed out by the runner; `line` is the sheet text verbatim and Carry
  does not own what goes in it. Its sibling `suggested` already holds "try
  45 lb (up from 40, held 3x)", so a runner revision folding an annotation
  into `line` would have published a weight with no change here and no test
  failing. A row Carry cannot name itself is left out rather than guessed.

- **A line that reads the same is posted once.** The owner's call,
  2026-10-16: "show it once". *The same* is the same text with case folded
  and the encoding normalised (NFC); spaces are already collapsed. Nothing
  looser: "push up" and "pushup", or "10 x squat" and "12 x squat", stay
  two bullets, because only identical words are certainly one movement.
  Where a typed line meets the sheet's, the sheet's copy is kept, in the
  typed one's place. That is the post only: both rows stay in the file.

Sheet rows count when answered with a number, or *done* on a timed row;
added rows count by name. The post is **shown in full
before it is sent**. That last one is the load-bearing guard: the text is
built from a file written on another machine, so no amount of care in this
file is worth as much as the owner reading the thing first.

## Writing it back

Carry **patches the original JSON**; it never re-serialises its own model.
Cycle 1 measured why: re-encoding drops a field a newer runner added, and
turns a weight of `45` into `45.0`. A model cannot faithfully reproduce a
document it only partly describes, and CLAUDE.md promises unfilled rows
come back untouched. So every key the runner wrote is copied through and
only the nine a person can edit are replaced.

Two consequences worth keeping:

- **Saving a session nobody edited returns the file unchanged** — byte for
  byte, for every file Carry itself has written. A sync watches this folder, and a file that rewrites itself on
  every save looks like a change that isn't one.
- **It refuses rather than guesses.** A file whose exercise list is not a
  list, two rows sharing an id, an edit that no longer matches any row — all
  leave the file untouched and say why. The first version silently replaced
  a malformed list with an empty one, wrote one weight against two
  exercises, and dropped unplaceable edits while reporting success. A
  training history survives a missing number; it cannot survive a wrong
  one, because nothing downstream can tell.

### The rows you added

Each added row carries an **id of its own**, minted when the row is added
and written into the file. Position was the identity before, and it was
wrong three ways at once: the text boxes were keyed by index, so a box kept
its text when the list shrank under it — the screen showing 30 next to a
row whose file entry held nothing; a row could be written twice when the
file was rewritten underneath and it changed category between reads; and
nothing could tell "the row the owner is editing" from "the row in that
slot".

A file written before ids gets a positional stand-in on read, and the
**ViewModel replaces it with a real one the moment the rows are seeded** —
every seeding path goes through one function, because the one that didn't
would be the one that let it back in. Without that promotion the fix would
only ever have applied to rows added on this phone, which is not where the
bug was: a stand-in is the index identity under a new name, so dropping a
blank row hands its id, its slot and its text box to the next row.

A stand-in is never written back. Persisted, it would collide with the
next read's stand-in for a different row, and two rows sharing an id is
worse than none having one — the screen keys boxes by it.

The ids make deduping a carried element against the row the owner is
writing *possible*, and that was tried and reverted: the owner's copy can
come from a draft pinned to older bytes, so dropping the file's copy
writes a stale `asRead` over it and destroys whatever was added to the row
since. A visible duplicate is the lesser bug.


`sets` comes from the runner; `added` is the owner's own, and Carry is the
only thing that writes it. That array is **replaced whole** on every save,
which makes it a different kind of risk from `sets`: a row Carry does not
hand back is not skipped, it is deleted.

So the array is split in two on every read — the rows the screen gets, and
everything else, **carried back out untouched**. Both halves come from one
function so they cannot drift, and the save writes shown + carried.
Refusing the save instead was tried first and is a trap worth recording:
the offending row is by definition one the owner cannot see, so there is
nothing they can do about it, and the day's real numbers could never be
written either.

One case still blocks, and it is not carrying's to fix: a row nested two
thousand deep overflows the stack inside `encodeToString`, which runs over
the whole document, so no version of this app can write that file. It
refuses and says the file is unchanged, which is true.

A shown row keeps the object the file held, verbatim, and an **untouched
row goes back as those same bytes**. That is what makes a save that
changed nothing write nothing — re-encoding would turn `45.0` into `45` —
and it is also what stops a value this build cannot parse, a `reps` of
`12.5`, from being flattened to null on a row nobody edited.

A row holding numbers but no name is **refused**, with the row on screen
and its name box asking in brick. That refusal is safe where the other one
was not, precisely because the owner can see the row.

Only two things are dropped rather than carried: a row holding nothing at
all, and a blank row the owner added and never filled. Neither loses
anything.

Rows are held in the draft, which is re-encoded into saved instance state
on **every keystroke** and crosses a Binder transaction to get there; the
draft once held the whole file text and died of exactly that, destroying
the typing it exists to protect. So a row too big or too deep to hold is
carried instead of shown. The depth cap is not decoration: `toString()` on
a two-thousand-deep object overflows the stack, and the read runs on every
resume, where a throw is a crash loop on a file the phone cannot edit.

`added` is deliberately **not** part of the `Session` model. Decoding it
there as well made one odd row cost the whole day: a row with no `name`
key threw during deserialization, the loader turned that into "Can't read
that session", and the sheet went with it.

## What counts as a session

A day the owner recorded doing something, **whichever box they typed it
into**. Their call, 2026-10-16: *"Yes if the 2G was incomplete/completely
off or I left a class early after only doing the row session, it should
still count."*

That used to mean the printed sheet's rows only, so a borrowed day whose
sheet was for the other block and whose real content was four rows typed
by hand reached the history as **nothing at all**. The rows the owner adds
now count the same as the sheet's: toward whether the day happened, toward
its lift count, and toward its heaviest.

Two things stay as they were, on purpose. An added row carries **no
family** — Carry never invents an exercise's identity — so it never reaches
*Heaviest by what it trains*, which is grouped by family. And the
"different movements" count still reads the sheet's rows only: it groups
by the runner's own key, which an added row has none of, so including them
would trip the withholding guard and hide the count entirely on any day
with one hand-typed weight.

A row's NAME counts as an answer only on an added row. On a sheet row the
name is the prescription rather than a reply, so counting it would make
every untouched sheet look filled in.

## The session file

Carry reads and writes one JSON per session, defined by the companion
script. Carry does not define the schema and must not extend it locally: the
runner writes the sets with their identities and `weight: null`, Carry fills
them in, the runner imports the result. A field Carry invents is a field the
importer drops.

**Reading it is lenient in two named ways and strict everywhere else.** An
unknown key is ignored, so a newer runner can add fields without breaking
an older build; and a `null` where a plain value is expected falls back to
that field's default, so one odd key cannot cost a whole day's numbers.

**`"schema": "gym-session/1"` is required.** It is checked on the raw JSON
before anything is decoded, so no default or coercion can vouch for a
file. A missing or `null` label, or any other value (a bare number
included), is refused with the file named and the label quoted, not as
damage: the file read fine, and "wait for the sync" would not help. A
later `gym-session/N` still says to update Carry. The coach writes the
label on every file and its importer refuses a file without one.

- **The save checks the file on disk**, just before writing: the form
  was filled against bytes read earlier, and a file replaced since by
  one whose label isn't ours is refused with its own message. Any other
  change is refused as above.
- **A sheet on screen is still held** over a refresh that finds a label
  problem, as over any unreadable refresh: the newest file may not be the
  one on screen, and dropping the sheet would hide the owner's typing.
  The save's own check is what stops a write.
- **The label is read at the top level.** The `gym-session/N` regex is
  only for a file cut off mid-sync, where there is no JSON to read.
- **An unknown label is quoted back safely**: without control, format or
  line-separator characters, and cut at 40 characters with an ellipsis.
- **Every file Carry writes keeps the label**, since a patch copies every
  top-level key.
- The Since tab counts unlabelled files in their own sentence. The
  new-sheet notification goes by file name and does not read the label.

**The rows are decoded one at a time**, so a row this build cannot read
costs that row rather than the day — a value of the wrong type, or a
missing `ref`. The head is decoded with the rows taken out, so a bad row
cannot take the date and the class tag with it.

**Where the line between "a bad row" and "a bad file" is drawn: wherever
the SAVE draws it**, and by calling the same function rather than keeping
two lists in step. `SessionPatch.unshaped` names the problems with the
file — a `sets` or an `added` that is not a list, an element of `sets`
that is not an object, two rows sharing a `ref` — and both the read and
the save refuse exactly those. (What depends on the owner's own input,
like an edit naming a row that has gone, stays in the save: it is
answerable from the screen.) On top of that the read refuses a file that
is not JSON, one whose `schema` is missing or not ours, and one whose `sheet_id` or
`date` will not read.

A reader that accepts what its writer rejects is worse than one that
refuses: it opens a form, takes a whole day's typing, and then refuses to
save it, with a message about a list the owner was shown as empty.

Only the rows the owner can SEE are seeded into the form. The raw JSON
holds every row, including the ones that did not decode, and treating
those as the owner's would both count them as filled and let a change to
an invisible row block a save. There are three seeds — the read, the
discard, and the re-seed after a save — and they share one function,
because fixing only the first left the other two showing the bug.

The Since tab is the exception to all of this: a day that read short is
left out of its history entirely. Its figures are claims rather than a
sheet, and "heaviest 50" over a day whose heaviest row did not decode is
a confident wrong number. The one figure taken from the unfiltered read
is "part way to a bump", because dropping a short day there would not
leave a gap — it would hand that day's movements to an older sheet's
counts.

A newest sheet that will not parse AT ALL is not in that read either, so
the band is **withheld** and says so, rather than quietly becoming the
previous session's. That needs the filenames, which `Growth` never sees:
it cannot tell a newest sheet that failed from a folder that ends earlier
— they are the same input — so the question is answered one layer up and
passed in. An older file failing cuts the band at that file's day
rather than withholding it, and says so under the rows.

The question asked there is about the newest **date**, not the newest
file: two classes on one day are two files, and if either read there is a
sheet for that day to take the band from. And a newer schema gets its own
wording, because the file is intact and the app is what is out of date —
which is what the Before tab says about the same file.

The sentence says *the newest sheet*, never *today's*. The newest file is
the newest by name, which on a rest day is last week's and can be dated
ahead; the Before tab is careful about exactly this. The band orders the
sheets by the same rule, so on a two-class day the Before tab's sheet
leads.

Left out, but not left out quietly: the tab says how many sessions are
not in its totals, above the figures they are missing from. This is the
screen that exists to explain why the numbers are thin, so a session
absent from it without a word is the same failure as a confident wrong
total, one step quieter. It is a different sentence from the sheet's,
because what is missing there is a row on a list the owner can still see,
and what is missing here is a whole session from totals they have no
other way to check.

Three words in that sentence are load-bearing. **Sessions**, not days:
two classes on one date are two files. **These totals**, not "here": the
bump band above reads every sheet whether it read short or not, so
"isn't counted here" would be contradicted by the first thing under it. And no **part of**: a file where every row failed still parses,
so "part" is false in exactly the worst case. It ends with the clause the
Before tab has and this one was missing — *"a sync part way through can
do that"* — because a file being replaced reads as zero bytes for a
moment and the history is re-read on every resume, so a red warning
asserting permanent loss can land on a file that will be fine by the next
glance.

It counts three shapes, because to the owner they are one thing: a file
that would not parse at all; one that read short **and** has evidence a
session happened; and one where **no** row read, which is neither of the
first two and fell through the gap between them. That middle test is the
same one the history itself uses, and it is the difference between
reporting a loss and inventing one — a blank sheet whose rows all read
fine would not have counted undamaged, so calling it a casualty asserts
what the data cannot support. Zero rows read is the opposite case: not
evidence of nothing, but the absence of evidence.

**A newer schema is counted apart and gets its own sentence**, because it
is not damage. The Before tab says *"Carry is out of date — update
Carry"* about the same file; one screen calling an intact file broken
while the other says to update the app is worse than either message
alone, and unlike the rest it does not settle on its own.

And the empty copy says "could not be counted" rather than "did not
read": a blank sheet reads perfectly and still leaves the list empty, so
the stronger claim is false exactly when the folder holds one.

Dropping a row is safe because the save path never sees the decode: it
patches the raw JSON and copies every row it did not edit through byte
for byte, so a row missing from the screen is still written back
untouched. It is not silent either — both the Before and the After tab
say how many rows did not read, in one shared wording, because a missing
line on the sheet otherwise reads as the coach not prescribing it and a
missing box otherwise reads as a row already filled in.
