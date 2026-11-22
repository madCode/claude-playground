# Cycle log

For the next cycle: what to do differently. What was built is in DEVLOG.md. Newest first.

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
