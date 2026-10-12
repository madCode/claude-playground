# Design

## The constraint everything follows from

No phone in the studio. So Carry is never open while you train — it is a
glance before and a form after, and anything that assumes otherwise is
wrong for this app. Two screens, no live timer, no during-workout logging.

Measured on real workouts at ~1.4 classes a week: a sheet carries a median
of **three numbers worth remembering, and zero weight changes**. That is the
whole information budget. A screen that shows eleven exercises equally has
failed, because four of them say "first time, go by feel" and three say
"no weight needed" — nothing to carry.

## Screens

**Today** — what to carry in. Ordered by what you can't guess:

1. Weights that **changed** since last time, with what they were. A bump is
   the only number you'd get wrong from memory, because by feel you repeat
   last session's weight.
2. The other history-backed numbers, one line each.
3. Everything with no number — first-timers, bodyweight, untracked — folded
   into a single tail line. Present, not prominent.

**Log** — what you did. One row per exercise: weight, reps, and two chips
(*easy*, *hurt*). Plus **add a row**, for anything the sheet didn't list.

Four fields, and the two chips are the two things that cannot be derived:

- *easy* — finishing a set says nothing about what was left in reserve, and
  this is the only signal measured as able to raise the owner above their own
  habit, since every other number the coach has comes from their past
  timidity.
- *hurt* — "couldn't finish" is already derived from reps against prescribed
  reps, so a *fail* marker adds nothing (checked: three uses in the real
  history, all three on sets already flagged incomplete). Pain is different
  on two counts. Bodyweight and timed lines often carry no prescribed rep
  count at all, so nothing is derivable and the marker is the only signal —
  the one real *knees* entry in the history is exactly this, a push-up with
  no reps and no prescription. And a set can be *completed* and still hurt,
  which a reps comparison cannot see; the cost of missing it is that the
  coach counts a clean set toward a streak and suggests more weight next
  time.

The old breakdown vocabulary (*fail*, *knees*, *modified*, *half*) collapses
into this. `breakdown` stays in the schema so old history still reads, but
Carry never writes it; anything that needs words goes in the note.

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

## The session file

Carry reads and writes one JSON per session, defined by the companion
script. Carry does not define the schema and must not extend it locally: the
runner writes the sets with their identities and `weight: null`, Carry fills
them in, the runner imports the result. A field Carry invents is a field the
importer drops.
