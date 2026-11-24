# The dumbbell counts file

How many dumbbells a lift is done with, where neither the board nor the history says: the
owner's own rulings, in a file kept beside the history (`dumbbell_counts.yaml`). It is private,
like the history: never in this repository, and every value below is invented. The format came
from the gym-coach session, 2026-11-24, at madCode's ask.

## The file

```yaml
schema: dumbbell-counts/1
movements:
  shoulder press: 2                                     # a plain count
  halo: 1
  tricep extension: {count: 1, when_line_has: overhead} # only when the board line has the word
  squat: {count: 2, unless_line_has: {goblet: 1, sumo: 1}}
```

- **The first line** is the schema; the second is `movements:`; each movement is indented under it.
- **A name** is the one gym-coach files the row under: the history row's `exercise`, and each
  name in `components` for a combo.
- **A count** is 1 (one dumbbell, in both hands or one hand) or 2 (one in each hand).
- **`when_line_has`**: the count applies only when the row's board line contains that word.
- **`unless_line_has`**: any of those words in the line gives its own count instead.
- Words are matched in lower case, anywhere in the line. A `#` starts a comment.

## How Spotter reads it

- **A row's own words always win**: "single arm", "goblet", "two dumbbells", or a history row
  whose `variant.implement` already says. The file fills only a row with no count.
- **A movement the file leaves out stays unknown.** So does a combo whose parts get different
  counts.
- **All or nothing.** A file that can't be read in full is refused with a sentence saying why;
  none of it is used.
- It is read when a history is imported (`HistoryImport`) and by the replay tool
  ([REPLAY.md](REPLAY.md)). The log made on the phone doesn't need it: the Log screen asks.
