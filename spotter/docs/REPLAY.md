# Replaying a history through the coach

For grading Spotter's coach on a real training history, in private. The tool reads a
`history.jsonl` in gym-coach's format ([HISTORY-FORMAT.md](HISTORY-FORMAT.md)) and writes one
session file per class in the format gym-coach's `scorecard.py` grades.

It needs JDK 21 and this repository; no Android SDK, no phone, no network once Gradle has its
dependencies.

## Run it

From `spotter/`:

    ./gradlew -q :coach:replay -Phistory=/path/to/history.jsonl -Pout=/path/to/sessions

- `history`: the file to replay. It is only read.
- `out`: a folder for the session files, made if missing. **It must be outside any git
  checkout**: the tool refuses a folder git would offer to commit, since the sessions are the
  owner's training data.

It prints three lines of counts and nothing from the file: classes and rows written, how many
lifted rows had a number on the sheet, and how many rows were read into history or left out, by
reason.

## What a session holds

`session-<date>-<class>.json` (`gym-session/1`), one per class in the file:

- **The sheet's side** of each row is what the coach would have shown that day, made from the
  history of the days before only: never the class itself, nor another class of the same day.
- **The lifted side** is the file's own word: the weight as written (22.5 stays 22.5), the reps
  reported, and the hurt, easy, hard and easier marks. A skipped row has no weight.
- **Every row of the file is there**, also the ones the coach couldn't read (an exercise its
  lists don't name, reps it couldn't set against the line). Those have no suggestion, and the
  scorecard counts a blank row against the coach.
- A class's board is the history's own lines for it, a line a row; an old row with no line goes
  by the name it was filed under, and rows with no class are filed under "class".

## Not yet

- Strength per movement (step 5) isn't built, so there is no switch for it. When it is, the
  switch goes here.
- A corpus of boards isn't read: the history's own lines are the rows that were lifted, which is
  what the scorecard grades.
