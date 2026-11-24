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
- Add `-PnoShared` (after both paths are given) to grade the coach without shared weights
  (DESIGN.md, "Words that share a weight"). The first line of the report then ends "(shared
  weights off)". Replay the same file both ways, into two folders, and compare the grades.
- Add `-Pcounts=/path/to/dumbbell_counts.yaml` to give the owner's dumbbell counts
  ([COUNTS-FORMAT.md](COUNTS-FORMAT.md)): a row that doesn't say how many dumbbells takes its
  count from them. The report's second line then says how many movements the file rules on
  and how many rows it filled. Without the flag the run is exactly as it was. The file is only
  read; a file that can't be read in full stops the run with the reason, which quotes the
  start of the line at fault (up to 60 characters of the counts file, on your own terminal).
- `out`: a folder for the session files, made if missing. **It must be outside any git
  checkout**: the tool refuses a folder git would offer to commit, since the sessions are the
  owner's training data. It must also be new or empty, so no session from an earlier run is
  graded with this one's.

It prints three lines of counts (four with `-Pcounts`) and nothing from the history file: classes and rows written, how many
lifted rows had a number on the sheet, and how many rows were read into history or left out, by
reason.

## What a session holds

`session-<date>-<class>.json` (`gym-session/1`), one per class in the file:

- **The sheet's side** of each row is what the coach would have shown that day, made from the
  history of the days before only: never the class itself, nor another class of the same day.
- **The lifted side** is the file's own word: the weight as written (22.5 stays 22.5) and the
  done, hurt, easy, hard and easier marks. A skipped row has no weight.
- **Reps** are there only on rows the coach read, a side. On the others the file's number may
  count both sides, so it is left out rather than set beside the line's reps a side.
- **Every row of the file is there**, also the ones the coach couldn't read (an exercise its
  lists don't name, reps it couldn't set against the line). Those have no suggestion, and the
  scorecard counts a blank row against the coach.
- **Rows where form broke down are in as plain lifts**: the session format has no mark for
  them. The coach never builds on such a row, but it is graded on it. Compare like with like.
- A line the coach takes for a heading or an instruction, logged with a weight, is in as a row
  with no suggestion.
- A class's board is the history's own lines for it, a line a row; an old row with no line goes
  by the name it was filed under, and rows with no class are filed under "class".

## Not yet

- A corpus of boards isn't read: the history's own lines are the rows that were lifted, which is
  what the scorecard grades.
