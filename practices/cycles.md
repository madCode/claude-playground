# How to do a cycle well

A cycle is one unit of work: pick something, build it, prove it, write down
what happened. Most of what follows was learned by getting it wrong first,
and each rule names the mistake that bought it.

The loop a change travels is [lifecycle.md](lifecycle.md). This is about the
hour inside it.

## Starting one off

Most of a cycle's outcome is decided before any code is written. A cycle that
starts badly does not fail loudly — it produces confident, wrong work.

**Check the ground before the brief.** Two agents were once launched into
isolated worktrees and both got a worktree of the *wrong repository*, because
a worktree is cut from the session's current directory and the session was
somewhere else. Both correctly refused to work around the guard and stopped,
costing the run twice. Verify where you are first; have the agent verify too,
with a one-line check and an instruction to stop and say so if it fails.

**Land the spec before you point anyone at it.** The same run wrote five
design docs and then briefed agents to branch from `main` — where the docs
did not yet exist. Whatever the work is specified in has to be reachable from
the branch point.

**Write the spec as a document, not as the prompt.** A design doc in the
repository says what the thing is, why it exists, *what makes it non-trivial*,
and how to know it worked. Then the brief is three lines pointing at it, the
next session can pick it up cold, and the reasoning outlives the cycle.

**Say what is already known.** When an earlier attempt produced a diagnosis,
put it in the brief as settled — "this has been traced, do not redo it" —
with the specific file and line. One relaunched agent skipped twenty minutes
of rediscovery that way.

**State the gate, and let it overrule the spec.** "The full build and coverage
check must pass" is a gate. So is "this should raise X without raising Y".
Give explicit permission to come back with *"I built it, measured it, and it
is not an improvement, so I did not ship it"* — that is a good outcome, and
without permission it gets rationalised into a ship.

A spec can be wrong, and the cycle should be allowed to prove it. One brief
prescribed a fix confidently; the agent implemented exactly that, measured it,
and showed it did not close the race at all. The doc was rewritten to record
the wrong-but-reasonable diagnosis alongside what it actually took.

**Name what is out of bounds, and scope the commits.** If other work is
in flight in the same tree, say which paths are untouchable and have every
commit scoped to its own project rather than `git add -A`. Otherwise a cycle
sweeps up someone else's half-finished change.

**Decide who merges.** Unattended cycles that merge to a repository which
publishes a build are putting that build on someone's device. Either require
the full gate and green CI, or end the brief with *do not merge, leave it
open, report back*.

**Ask for a report, not a result.** The PR URL, what was built, what the
review found, and — the one that matters — *what could not be done*. An agent
told to report blockers will; one told only to finish will paper over them.

## A backlog item is a hypothesis

Three times in six cycles, the item at the top of the list named a cause
that was not the cause:

- "Nothing calls this" — something did, two layers away.
- "Nothing writes these fields" — the writer had been there all along; the
  value it wrote was null for a different reason entirely.
- "Four of five patterns have a heavier lift that is not the latest" — true
  of the patterns, but a gate downstream absorbed it and the real effect was
  two rows.

Each was written in good faith by an earlier cycle that had just found
something real nearby. **Before building what an item says, spend ten
minutes proving the thing it claims is broken is actually broken**, and
re-derive the size of any number it carries. Correcting the item is a
finished cycle; building on it is not.

## Pick one thing, and finish it

A cycle that half-builds three things leaves three things to re-read later.
One finished item — tested, measured, written up — beats three in flight.

Take the top unfinished item, or finish what the last cycle left. Do not
shop the backlog for something congenial.

## The test has to be able to fail

**Break the code the test covers, watch that exact test go red, restore it.**
Every time. State in the PR that you did.

This is not ceremony. Tests that could never fail have shipped in these
repositories repeatedly:

- Two tests asserted an output "did not contain a weight", but the code
  never read a weight field on any path — so they would have stayed green
  while it started publishing private notes.
- Three Compose tests asserted something about a row the `LazyColumn` had
  never scrolled to. A `LazyColumn` does not compose off-screen items, so
  the node did not exist, and `assertDoesNotExist` passed for the wrong
  reason. Scroll with `performScrollToNode` on `hasScrollAction()`.
- A screenshot test captured a flat blank and passed. `captureToImage` can
  return an empty bitmap silently; assert `!isFlat()` or the suite uploads
  nothing as evidence.
- A fixture named a real thing as "not yet supported", and went red the day
  the code learned it — which was the behaviour it was asserting. **Fixtures
  that must stay unknown should name something invented.**

And a subtler one: **choose inputs that cannot reach the other branch.** A
test for one code path used a value that routed to a different path, and the
resulting failure read like a bug in the feature rather than in the test.
Twenty minutes to find.

**A fixture with one of a thing cannot tell a ratio from a constant.** "1
recorded, went up 1/1" passes whether the code counts the ones that went up,
the total, or everything. Three mutations survived a suite that way in a
single cycle. Where a test asserts a proportion, the fixture needs one of
each outcome and one of neither.

**The falsification harness can lie the way a test can.** One reported all
seven mutations green and nearly convinced me the tests were worthless; it
was grepping for a `FAIL` line that runner never prints, because an
`AssertionError` just aborts the run. Check the exit code. And **distrust a
uniformly negative falsification result** — seven useless tests in a row is
far less likely than one broken harness. In the same pass, once it was
fixed, exactly one test really was useless.

## A measurement you have not tried to break is a guess

Four consecutive published figures about one matcher were wrong, each for a
different reason: counting the wrong denominator; comparing a raw string to
a normalised one; counting correct behaviour as failure; and a regex that
quietly rejected the rows it was meant to count. All four were caught, none
on the first pass.

- **State the denominator and what it excludes.** "13% matched" was counting
  lines the system ignores on purpose.
- **Sanity-check by hand against two or three real rows** before believing a
  number.
- **Run before/after in separate processes _and_ separate directories.** If
  module A imports module B by name, loading an old copy of B beside the new
  one makes the old code run the new functions, and the diff shows nothing.
  This has now happened twice.
- **`git archive` omits gitignored files**, so a baseline built that way can
  measure an empty corpus and print nothing. A measurement that prints
  nothing is not a measurement that found no change.
- **A flake cannot be proved fixed by one green run.** Reproduce it first and
  count: "1 of 30 before, 0 of 90 after" is evidence; "the suite passed" is
  not. If the obvious probe cannot reproduce it, build a denser one — and if
  you still cannot, say the fix is unverified rather than claiming it works.
  **Twenty runs is the floor, and say the number.** Twelve came back clean
  on a test a reviewer then measured failing four times in twenty; twelve is
  simply too few for a one-in-five rate to be unlikely to hide.

## Let the measurement overrule you

The most valuable cycles are the ones that end with "built it, measured it,
not shipping it". A change that was agreed, specced, and implemented was
rejected because ten formulations all traded the headline number against a
number that mattered more. The patch and the table were kept; the code was
not.

Write the gate down **before** building, in the design doc, in the form "this
should raise X without raising Y". Then it is a measurement, not an
argument.

## Reviews find real things, and about a third do not hold

Always review a behaviour change with fresh eyes before the PR, and **verify
each finding against the code before acting on it.** Ask for `file:line` plus
a concrete failure scenario, most severe first, no edits.

Budget for the review finding a *design* error rather than a typo — in one
project three separate cycles did. And review your own fix: one second look
found the fix was worse than the bug it closed.

## Green tests do not prove integration

Two halves of one product had 151 passing tests between them and could not
talk to each other: one wrote `<date>-<class>.json`, the other looked for
`session-<date>-<class>.json`. Every test on both sides built its own
fixture, so the names agreed by construction.

**Generate one fixture with the real producer and assert the consumer reads
it.** Name the keys one by one — a renamed field decodes as null and every
behavioural assertion still passes.

## A test that stops one layer short

Both of the worst gaps found in one audit had the same shape: the logic was
tested and **the thing that says it** was not.

- A replay carried the flags correctly, and nothing checked that the report
  printed them — the 72-line function the owner actually reads had never
  executed. One of the tests even claimed, in its docstring, that the report
  "would have caught" the bug. Nothing checked that it did.
- A save helper returned three outcomes, thoroughly tested, and nothing
  checked what the caller did with them. The caller's handling of all three
  was at zero, and it held a bug that put one session's numbers on another
  session's rows.

Both read as covered from the test names alone. **Coverage by function, not
by percentage, is what finds these** — a per-function report names the thing
nobody ran; a total percentage stays comfortable while a whole function
sits at zero.

## Green tests do not prove anyone can see it

A feature was added so the owner could record something the app had no way
to record. Every test passed. The screen it fed returned early — *"Nothing
logged yet"* — for exactly the sessions the feature existed for, because the
emptiness check counted a different thing. The new test passed throughout,
because it asserted on the data structure the screen reads rather than on
the screen.

**When a change exists so someone can see something, one assertion has to be
against the thing they look at.** A test over the model proves the model.

**And render it and look at it.** The same cycle added a sixth chip to a row
that fitted five. What slid off the right edge was the chip that says a set
did not happen — invisible to a green suite, obvious in the PNG in about two
seconds. Screenshot tests are cheap; the looking is the part that finds this.

**When a change makes one filter wrong, grep for the same wrong question
elsewhere.** It was being asked one layer up too, and the correction was the
same sentence both times.

## Write it down where it will be read

- The backlog is what is next. An item that ships moves to **Done** — it does
  not vanish. A careless edit once deleted a shipped item outright, and the
  work survived but the record did not.
- The devlog is newest-first and opens with a **status block**: what landed
  lately, what is being watched, what is waiting on the owner. Put the debug
  build link there too. "Which build am I on?" should never need asking.
- Anything specified but unbuilt goes in `designs/`, carrying what makes it
  non-trivial and how to know it worked — so the next session starts without
  re-deriving the analysis.
- **Verify every scripted documentation edit by reading the file back.** A
  string replace whose anchor has drifted changes nothing and exits 0.

## Recurring cycles should get better, not just repeat

When cycles run unattended, each one ends by recording what it would do
differently, and the next one **reads that first**. Otherwise every cycle
rediscovers the same traps at full price.

## Know what each tool is honest about

- An emulator is honest for storage access, file I/O, permissions, and
  `adb install -r`. It is **not** honest for UI: it fails by producing
  convincing false results.
- After a CI re-run, `gh run view --log` serves the latest attempt — reading
  it as the original once produced a confidently wrong published diagnosis.
- A subagent in an isolated worktree gets it from the session's *current*
  directory. Launching from the wrong one gives it a worktree of the wrong
  repository, and it will correctly refuse to work around the guard.
