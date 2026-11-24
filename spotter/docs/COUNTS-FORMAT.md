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

- **The first line** is the schema; the second is `movements:`; each movement is indented under
  it, all by the same amount.
- **A rule is one line.** A rule laid out over several lines (as some YAML writers do by
  default) is refused, not guessed at: write `{…}` as above.
- **A name** is the one gym-coach files the row under: the history row's `exercise`, and each
  name in `components` for a combo.
- **A count** is 1 (one dumbbell, in both hands or one hand) or 2 (one in each hand).
- **`when_line_has`**: the count applies only when the row's board line contains that word.
- **`unless_line_has`**: any of those words in the line gives its own count instead.
- **Several `unless_line_has` words in one line**: the first in the file's order decides.
- Words are matched in lower case at the start of a word: "arm" is found in "single arm" and
  "arms", not in "farmer". A `#` after a space starts a comment, in quotes too.
- A movement, or a word, given twice is refused.

## How Spotter reads it

- **A row's own words always win**: "single arm", "goblet", "two dumbbells", or a history row
  whose `variant.implement` already says. The file fills only a row with no count.
- **It counts dumbbells only.** A row whose words or `variant.implement` name something else
  held (a kettlebell, a Y-bell, a TRX, bodyweight) is left as it is. So is a combo whose own
  words give its parts different counts.
- **A movement the file leaves out stays unknown.** So does a combo whose parts get different
  counts.
- **All or nothing.** A file that can't be read in full is refused with a sentence saying why;
  none of it is used.
- It is read when a history is imported (`HistoryImport`) and by the replay tool
  ([REPLAY.md](REPLAY.md)). On the phone it is picked on the home screen, after the history. The log made on the phone doesn't need it: the Log screen asks.
