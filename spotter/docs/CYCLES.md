# Cycle log

For the next cycle: what to do differently. What was built is in DEVLOG.md. Newest first.

### Cycle 28 — audit: the skills and instructions

- A due review is the audit: this one sat "overdue" for sixteen cycles behind builds. Check
  BACKLOG's Recurring reviews when picking, before the next item.
- This log is kept to its last ten cycles; a lesson worth keeping longer goes to
  `spotter/CLAUDE.md` ("What cycles keep relearning"), and the rest is in git.
- Run falsification in a worktree of its own: in the main tree it leaves the tree dirty while
  a reviewer reads, and trips the "uncommitted changes" check for as long as it runs.

### Cycle 27 — build: the per-movement switch

- Run a new command-line flag for real before trusting it: the unit tests passed while the
  Gradle task dropped the flag (`hasProperty` inside a task is the task's, not the project's).
- A rule that waits on the person's ruling can still ship its mechanism with the one link
  nobody would dispute; say which links wait and on whom.

### Cycle 26 — fix: two classes in a day

- Before changing a key that the log, the export and the calendar all share, look for a fact
  already stored that answers the question: when an entry was written did, with no migration.
- Split a backlog item whose halves differ in cost: the common half shipped, the rare one stays.

### Cycle 25 — build: add an exercise

- A new kind of row meets every rule that matches rows to entries: ask of each one (carry-over,
  strays, clearing) what it does with the new kind, before the review runs it.
- The first text field needs what no other control did: the keyboard's Done, a short label
  that fits a phone, a large-text check, and a DEVICE.md section for the keyboard itself.

### Cycle 24 — build: a weight for each round

- When the screen lets the person say more, check that saying more never gives a worse number
  than saying less: compare the two on the same class before the review does.
- Store what was said and derive the reading when the coach reads it (`Journal.read`), so a
  better reading later needs no migration.
- The reviewer was right that a test fed rounds no screen could produce; the reachable case
  (a carried log with another number of rounds) was the one that was broken.

### Cycle 23 — audit: the docs against the code

- Have the audit done by a reader given only the docs and the code, with the day's PR list:
  it found claims of things working that were never built, which the author's eye skips.
- When a PR ships a piece, delete the sentence that said it was coming, in the same PR.

### Cycle 22 — build: two clean sessions to go up

- A rule change that most tests lean on: add the one input the old behaviour now needs
  ("felt easy") to fixtures whose point lies elsewhere, and rewrite only the tests about the
  rule itself. Check each expectation that moved the other way by hand.

### Cycle 21 — build: the history on the phone

- Check each Java call against the project's lowest Android version before the review does:
  `readNBytes` compiled, and would have crashed on Android 8 to 12.

### Cycle 20 — build: the replay tool

- Run a new command for real once, with the arguments a person would type, before writing its
  doc: the test called the function, and only the run showed how relative paths resolve.

### Cycle 19 — build: reading the history file

- Carry's fixtures hold an invented history in the real format: look at a sample of real-shaped
  rows before writing a reader from the format notes alone. The notes said reps on "12 total"
  are the person's own number; the rows showed reps and asked reps always share terms.

### Cycle 18 — build: a log follows the exercise

- A test board whose first line isn't a block header the reader knows becomes a row, and every
  index after it is off by one: check `sheet.rows` before indexing a new fixture.
