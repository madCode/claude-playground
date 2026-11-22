# Cycle log

For the next cycle: what to do differently. What was built is in DEVLOG.md. Newest first.

### Cycle 12 — build: the Log screen, first half

- A screen that writes to the log needs its review pointed at *when* it is used (the car after
  class), not only at what it writes: the class had already moved on by then.
- A test helper that copies the activity's save tests the copy: move the save into the store
  (`EventLog.save`) and call that from both.

### Cycle 11 — build: a stable row id, a typed "new", tests for why and understood

- An id made from a line's text should come from what the reader made of it, not the raw post:
  boards are refreshed after posting, and raw text changes when the meaning doesn't.

### Cycle 10 — build and audit: the event log; architecture and test health

- A read-only audit can run in a worktree of origin/main while the cycle builds in the main
  checkout: it cost no extra time, and found a bug the cycle then fixed.

### Cycle 9 — build: the sheet on the phone

- Render the screen from a real feed board and look at the PNG before review: it shows layout
  problems no assertion names.
- A clickable row merges its texts into one semantics node: measure a child's bounds with
  `useUnmergedTree = true`, or the test measures the whole row and can't fail.

### Cycle 8 — build: today's board on the phone

- An app falsification run takes minutes: run it in the background and leave the tree alone,
  even when a stop hook asks for a commit; the breaks restore themselves.

### Cycle 7 — build: the scorecard export

- When the consumer is out of reach (a private repository), match a file it already reads
  (Carry's sessions), ship an example, and put the one check on madCode's list.

### Cycle 6 — build: the sheet

- Run a real day's board (the `carry-boards` branch) through the new piece before review: a
  made-up board passed, the real one showed four kinds of noise rows at once.

### Cycle 5 — build: the suggested weight

- A break that stays green can be masked by a second rule giving the same answer (a timed 20
  holds anyway because 20 to 25 is a big jump); pick inputs where only the rule under test
  decides.
- Cycles now run 30 minutes apart at madCode's ask: take a whole step-sized slice (progression
  with all its traps) rather than one rule.

### Cycle 4 — build: words the boards use

- Adding a new movement name is low-risk, since it borrows nothing; a variation word can
  merge two exercises, so diff every new reading against the old ones and read each, not only
  the count.
- The corpus peek reads `expected.matched[].line` (1,546 exercise rows), the same set every
  cycle's numbers use; grepping every `"line"` field counts other fields too.

### Cycle 3 — build: naming the exercise

- Read refusals as hard as successes: four wrong refusals ("pulsing half squat", "mini band
  bridge") pointed to list axes, not to the reader; fixing the lists was the right size.
- When a check runs before another (unknown words before "or"), a test with both shows the
  order matters; the corpus line "V hold or 30 sec high plank" did.
- Ask the reviewer for wrong *known* readings, not only refusals: the combo implement bug read
  in full and looked right, which is the kind a gap-beats-a-guess app can't afford.

### Cycle 2 — build: reading what a line asks for

- Peek at the corpus through the real Kotlin code, not a Python copy: a throwaway JUnit test
  that writes every line's reading to a file (deleted before commit) found shapes a regex
  survey missed ("\~10 sec", "on left", counts with no "x").
- Two of three falsifications first stayed green: a rule with two guards ("!" and "all") needs
  a test that only one of them catches.
- `local.properties` is gitignored: a fresh checkout needs `sdk.dir=/opt/android-sdk` before
  the full build, or it fails in one second with "SDK location not found".

### Cycle 1 — build: the starting lists

- Run baseline (cloud container): disk 15% used, load 0.00, no emulator.
- The scheduled task must run in a session that can push to madCode/claude-playground. Three
  fresh-session runs before this one could clone but not push, and built nothing. If a run
  finds it can't push, it says so on the morning page and stops; it doesn't build locally.
- Measure the lists against the corpus with a longest-phrase-first read before and after a
  change to them: "words left over" is the number that tells whether a list change helped. Until
  the corpus grade is a Gradle test, a throwaway script in the scratchpad does it; say so.
