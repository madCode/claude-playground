# Design

## The constraint everything follows from

No phone in the studio. So Carry is never open while you train — it is a
glance before and a form after, and anything that assumes otherwise is
wrong for this app. Two screens, no live timer, no during-workout logging.

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

**Today** — what to carry in. Ordered by what you can't guess:

1. Weights that **changed** since last time, with what they were. A bump is
   the only number you'd get wrong from memory, because by feel you repeat
   last session's weight.
2. The other history-backed numbers, one line each.
3. Everything with no number — first-timers, bodyweight, untracked — folded
   into a single tail line. Present, not prominent.

**Log** — what you did. One row per exercise, two lines tall: weight,
reps, and five chips (*easy*, *hard*, *mod*, *hurt*, *skip*) — six on a timed
row, which also gets *done*. Plus **add a row**, for anything the sheet didn't
list.

*skip* is a different kind of thing from the other two: it is not a quality
of a set, it is the statement that there wasn't one. It had been in the
schema, in the patch and in the importer — which logs skipped items so the
coach stops re-asking — since before this screen existed, with no way to
set it, so a movement deliberately skipped was indistinguishable from one
not got to. Skipping clears the row and takes its boxes away: there is no
number to put in them, and a weight against a set you did not do is the
wrong number this app exists to avoid. Un-skipping does not bring the old
number back, because nobody re-entered it.

*easy* and *hard* are one question with two answers, so they are mutually
exclusive. The coach reads them in opposite directions, and until
2026-10-12 it could only hear one of them: `easy` earned a bump a session
early while `hard` appeared nowhere in the engine at all, so every
qualitative signal pushed the weight up and none held it. Found by reading
the owner's own replies, where "ok hard" appears as readily as "ok, easy".

*mod* is "I did the easier version" — knees, a box under the step-up. It
exists because **a third of this owner's logged sets carry no weight**, and
a third of a typical sheet is bodyweight: for all of that the coach's
"what weight next" model has nothing to say. It is the only progression
axis those movements have, and three clean modified sets earn "try one as
prescribed". It also fixed a wrong message rather than only adding a
missing one — ten clean knee push-ups are *complete*, so three of them used
to earn "full form 3x in a row, push it", congratulating the owner on form
they had not used. The ladder itself stays in the owner's head: naming the
next rung for every movement is a knowledge base we do not have and they
do not need.

*done* is "I did it, and there is nothing to say about it", and it appears
only on a row the board prescribed in seconds. Those rows have no rep count
and take no weight, so both boxes are empty by definition and every other
chip claims something the owner may not mean — which left them nothing
honest to tap, and a blank row is written blank by design, so a finished
45-second plank reached history as nothing at all. It is also the only chip
that is the *primary* affordance of the row rather than a note on it, which
is why it is the brightest.

Nothing downstream reads the field by name. What it changes is that the row
reaches the importer at all, as a complete set — which is what the
bodyweight rule counts.

The same gap existed one layer up: the *since you started* screen dropped a
whole session unless some row carried a number, so a class recorded only
with markers counted as never having happened. It now asks the same
question the row does — of the sheet's own rows. Whether a session logged
entirely by hand belongs on that screen is still open.

The other two are the two things that cannot be derived:

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

Five states, and they are separate because each needs different words. A
wrong diagnosis sends someone to fix the wrong thing:

| State | What it means | What it offers |
|---|---|---|
| Reading | the first read, which happens off the main thread | nothing; it is brief |
| Pick your folder | nothing chosen yet | the picker |
| Lost the folder | chosen, but the grant is gone — asked of the system, never guessed from an empty folder | the picker |
| Nothing to carry | folder readable, no session written yet | the picker |
| Can't read that session / Carry is out of date | a file that is there but truncated mid-sync, or written by a newer coach | the picker |
| Folder isn't answering | the read passed 15 seconds; a provider can block forever with no timeout to set | the picker |

**A sheet that is not today's says so above the numbers**, in the same
place and the same voice as the borrowed-block warning, because it is the
same kind of mistake: a sheet that looks authoritative and is not about
today. The likeliest cause is simply that the sync has not run. A date the
app cannot parse is admitted rather than assumed to be today.

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

- **Amber `#E0A33E` means one thing: this weight moved.** It appears on the
  figure and nowhere else. If amber is on screen there is something to
  remember, and that is the whole reason the screen exists. A `+5` chip on
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
the movements do not), every session newest first, and a plain statement of
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
weight the marker is the whole record. The same test decides whether the
session appears on that screen at all.

It is a **record, not a form**, and says so. There is nothing on it to type
into: the one editable session is today's, on the Log screen, and two ways
to write one file is how a wrong number gets in. A back-filled day — a
record of training from before this app — says that instead.

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
scope: the band is the newest sheet only, streaks strictly in progress,
and five of those.

**The two numbers on a row are about different things**, and the row says
so. The pips count clean sets; the weight is what the runner says to use
today. On its converted-streak rule those differ — two sets held at 30 for
eight reps earn "2 of 3" beside an estimate of 25 for twelve, and nothing
was ever held at 25. The sighted row shows three things side by side and
claims no relation between them; the spoken label says *"2 of 3 clean
sets, next at 25 pounds"* rather than joining them into a sentence that
would.

A row leaves it the moment the owner's own answer contradicts it: marked
*hard*, or logged **below** the weight the count is about. Those are the
runner's own signals that the next bump is blocked. Logged at that weight
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

## Handing the block over

The preview is the whole of the egress control -- the app has no
`INTERNET` permission, so the share sheet is the only way anything
leaves -- and it marks a suspect line in the TEXT as well as in red.
Colour alone is unreadable in glare, on a greyscale display, or to
anyone with a colour vision deficiency, and it is silent to TalkBack.
The caption above the preview names that mark rather than the colour.
The mark is on the preview only: what goes to the share sheet is the
post text, handed over untouched.

What is said out loud is said on the CAPTION, and the preview itself
carries no content description at all. That is deliberate and it is the
opposite of the obvious move: Compose offers a text node LINE and PAGE
navigation only while its description is empty, so a label on the
preview costs a listener the ability to step through the post a bullet
at a time -- and whatever that label says about the flagged lines, it
must then read the whole post too, or the post becomes unreachable. The
caption is short, says how many lines are marked and which, and leaves
the post to be read as a post.

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
- **The promise it cannot keep, it checks instead.** The screen says "the
  movements only — never your weights", and an added row's name is typed by
  hand, so that is not something the app can guarantee. Stripping trailing
  digits would silently mangle `bicep curl 21s`, a real movement. So any
  bullet whose *movement name* carries a digit is shown in brick in the
  preview, with the line above changed to say so. The prescription's own
  digits are excluded from the test, or every line on an intel day would be
  flagged and the flag would mean nothing. Nor is a name the runner parsed:
  `bicep curl 21s` and `90/90 hip stretch` are real movements, and a
  warning that fires on a line nobody typed is one the owner learns to
  ignore.
- **It is built from fields this app owns.** `canonical` and `components`
  are parsed out by the runner; `line` is the sheet text verbatim and Carry
  does not own what goes in it. Its sibling `suggested` already holds "try
  45 lb (up from 40, held 3x)", so a runner revision folding an annotation
  into `line` would have published a weight with no change here and no test
  failing. A row Carry cannot name itself is left out rather than guessed.

Only rows answered with a number count, and the post is **shown in full
before it is sent**. That last one is the load-bearing guard: the text is
built from a file written on another machine, so no amount of care in this
file is worth as much as the owner reading the thing first.

## Writing it back

Carry **patches the original JSON**; it never re-serialises its own model.
Cycle 1 measured why: re-encoding drops a field a newer runner added, and
turns a weight of `45` into `45.0`. A model cannot faithfully reproduce a
document it only partly describes, and CLAUDE.md promises unfilled rows
come back untouched. So every key the runner wrote is copied through and
only the six a person can edit are replaced.

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
`schema` is the exception and is deliberately nullable: it is what decides
the file is ours at all, and a non-nullable `schema` would have coercion
hand the gate its own default back.

Anything else unreadable still costs the file, which the screen says
plainly rather than guessing: a value of the wrong type, or a missing
`ref`, takes the whole session to "Can't read that session". That is too
blunt and is on the backlog.
