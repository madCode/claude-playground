# Cycle log

For the next cycle: what to do differently. What was built is in DEVLOG.md. Newest first.

### Cycle 1 — build: the starting lists

- Run baseline (cloud container): disk 15% used, load 0.00, no emulator.
- The scheduled task must run in a session that can push to madCode/claude-playground. Three
  fresh-session runs before this one could clone but not push, and built nothing. If a run
  finds it can't push, it says so on the morning page and stops; it doesn't build locally.
- Measure the lists against the corpus with a longest-phrase-first read before and after a
  change to them: "words left over" is the number that tells whether a list change helped. Until
  the corpus grade is a Gradle test, a throwaway script in the scratchpad does it; say so.
