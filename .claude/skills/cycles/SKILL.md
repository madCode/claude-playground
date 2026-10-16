---
name: cycles
description: Run work cycles on a project - each cycle picks one backlog item or one audit, does it to the project's rules, ships it, and writes down what the next cycle should do differently. Use when a scheduled task or madCode asks for a cycle, "run cycles" or "overnight cycles", or for work on a project for a set time or until a set amount is done.
---

# Cycles

A cycle is one piece of unattended work: pick one thing, do it properly, ship it, and leave the
repository ready for the next cycle, which starts with no memory of this one. Everything a cycle
knows comes from the repositories, so everything it learns goes back into them.

## What the asking prompt gives

A scheduled task's prompt, or madCode, says:

- **The project** and its repositories, and only what the project's CLAUDE.md doesn't already
  say (a read-only data repo, an environment variable to set first).
- **When to stop**: an end date, a last cycle number, or "when BACKLOG.md's *Done when* is
  ticked", with the scheduled task's id so the last cycle can turn it off. No stop rule means
  one cycle.
- Optionally, the mix of build and audit (about 60/40 if not), and items madCode has approved.

## 1. Read before choosing

1. The project's CLAUDE.md, and the root CLAUDE.md when the project lives in claude-playground.
2. The top of the cycle log (see [The two logs](#the-two-logs)). Apply its lessons rather than
   rediscovering them: the point is that each cycle is better than the last, not independently
   competent.
3. DEVLOG.md's status block, then BACKLOG.md.
4. The project's open PRs and CI on main. A red main, or a PR the last cycle left red, is this
   cycle's work before anything new.
5. The answers on the project's morning page (see [The morning page](#the-morning-page)): a
   decision madCode answered there is approval to build it, or a reason not to.
6. The project's domain notes, when its CLAUDE.md names a file of them (gym-coach's
   `TRAINING.md`), before an audit of the advice or a change to a rule that rests on one.

Then check the stop rule. If it is met, go to [Stopping](#stopping).

## 2. Pick one thing

Build or audit, at random by the asked mix.

**Build**: the top unfinished item in BACKLOG.md that isn't waiting on madCode (an unapproved
proposal, a "your call"), or what the last cycle left unfinished. Treat the item as a hypothesis
before building it:

- **Is it still real?** Build the input and run it the way production does, and print what
  happens. Reasoning from the code alone has been coherent and wrong.
- **Is it already done**, in whole or part? Grep for the idea, not the item's wording.
- **Is the cause the one it names?** An item names the cause its author could see.

An item that turns out to need a decision stops there. How it waits depends on its size:

- **A larger feature proposal** (a change to what people see or do, or to how the thing is
  built, too big for one cycle) becomes a GitHub issue, written as the `handle-issues`
  skill's *Proposals* section says: mockups or a diagram, alternatives, the approval asked
  for, labelled `waiting on madCode`. The BACKLOG.md item links the issue, and the issue check
  picks up the answer
  ([the loop, steps 2 and 3](https://github.com/madCode/claude-playground/blob/main/practices/lifecycle.md#2-it-is-sorted-before-it-is-built)).
  Search the open issues first, so it isn't filed twice.
- **A smaller call** (a wording, a threshold, which of two behaviours) stays in BACKLOG.md,
  under the project's "your call" heading.

**Audit**: one persona reading the real thing (the app as built, its real output, real data
where the project allows it), never fixtures. The project's CLAUDE.md or the prompt names the
personas; otherwise pick from someone the app is for, a UX designer, a security researcher, an
architect after simple code, test health, a documentation pass, backlog grooming, and an
instructions pass. When BACKLOG.md says a
[recurring review](https://github.com/madCode/claude-playground/blob/main/practices/recurring-reviews.md)
is due, that is the audit.

**Backlog grooming** is an audit of BACKLOG.md itself: drop or merge what is stale, done or
duplicated (checked against the code, not the wording), split items too big for one cycle, and
put *Next* in the order cycles should take it, the project's own ordering rule first, then what
readers would notice most. A larger feature idea sitting in the backlog becomes an issue, as
above. An item that doesn't serve what the app is for (DESIGN.md's *What it's for*, the one rule
in CLAUDE.md) moves to *Not doing* with a line on why, rather than being deleted, so it isn't
proposed again. A change of priority madCode set, or dropping an item they asked for, is a
question for them, not an edit.

**An instructions pass** reads the skills, the CLAUDE.md files and the scheduled task's prompt
the way the next session will, and asks whether they helped the sessions that used them:

- **Ignored or relearned**: a lesson in the cycle log that a rule already stated means the rule
  is buried, too long or unclear. Move it up, shorten it, or make it a check.
- **Never needed**: a rule no session's work has touched in many cycles is weight on every read.
- **Contradicted or duplicated** between files: one copy goes, and the rest points to it.
- **Stale**: a file, command, path or tool that no longer exists.
- **Skill descriptions** that wouldn't make a session reach for the skill when it should.

Base each finding on what the logs, PRs and summaries show sessions actually did, not on how
the text reads. Cutting what is stale or duplicated is fine; changing what a rule asks is
madCode's call. A change to a shared skill goes to claude-playground first, then its copies.

Fix small clear things now. The rest goes into BACKLOG.md, checked
against the code first: about a third of findings don't hold.

One thing per cycle. Time left over goes into the records, not a second feature.

## 3. Do it

All the project's rules apply. These are for unattended work, each learned by getting it wrong:

- **Falsify every new test.** Commit first (`git status --porcelain` empty), break the code the
  test covers, watch that test go red, restore with git. A break no test notices has falsified
  nothing; the break must change behaviour, and moving code breaks it harder than deleting it.
- **Measure the way it really runs**: through the real entry point, the command the owner
  runs, the way CI runs it. Before and after in separate processes and directories. A result
  that comes back empty or identical gets its harness checked before it is believed.
- **Don't state a number you haven't measured.**
- **Read back every scripted edit.** A replacement whose anchor drifted exits 0 and changes
  nothing.
- **List the callers before changing a shared value**, and say what each one should do.
- **A red test is a claim to check, not a fixture to update.**
- **Render a changed screen and look at it.** A test on the data doesn't show the screen.
- **Leave the tree alone while a review agent reads it**, and stop any background work in the
  step that reads its result.
- **A test that can't fail looks like one that passes.** Known ways: asserting on a `LazyColumn`
  row never scrolled into view (it isn't composed, so `assertDoesNotExist` passes); a
  `captureToImage` that came back flat; a fixture that names something real as "unknown"; a
  fixture with one of a thing, which can't tell a ratio from a count; inputs that route to the
  other branch. A falsification run where every break stays green is more likely a broken
  harness (grepping for a line the runner never prints) than useless tests.
- **Test the thing the person sees.** One assertion goes against the screen or the printed
  report, not only the model under it, and two halves that talk get one fixture made by the
  real producer and read by the real consumer.
- **A flake is fixed when it's counted**: reproduce it first, then at least twenty runs before
  and after, with the numbers in the PR.
- **Snapshots lie by omission**: `git archive` leaves out gitignored files, and after a CI
  re-run `gh run view --log` shows the latest attempt.

### Briefing an agent

When a cycle hands work to an agent:

- Have it check it is in the right repository first, and stop and say so if not. A worktree is
  cut from the session's current directory.
- Land any spec on the branch it starts from, as a document in the repository, and point the
  brief at it. Say what is already settled, with file and line, so it isn't rediscovered.
- State the gate ("raises X without raising Y"), and say that "built it, measured it, not
  shipping it" is a good outcome.
- Name what is out of bounds, keep commits to its own paths, and say who merges.
- Ask for a report: the PR, what was built, what the review found, and what could not be done.

## 4. Ship it

As the project's CLAUDE.md says: the fresh-eyes review, a PR with the template, merged only as
the project allows. Wait for CI by subscribing to the PR, not by sleeping. A cycle doesn't end
with its own PR red unless the PR says why.

## 5. Write it down

### The two logs

Two readers, so two files:

- **DEVLOG.md is for madCode**: what was asked, found, shipped and left, newest first, and its
  status block (*Lately*, *Watching*, *Waiting on you*) kept current.
- **The cycle log is for the next cycle**: `docs/CYCLES.md`, unless the project's CLAUDE.md
  names another. A `### Cycle N — build|audit: <what>` entry at the top, N one more than the
  last, then the one or two things the next cycle should do differently. What was built is in
  DEVLOG.md; link it rather than repeat it.

In BACKLOG.md, tick the item and move it to *Done* (if the file has no *Done* section, add
one), and add what the cycle found to *Next*.

### Research answers

A question answered by research (a cycle's, or an agent's for madCode) goes into the project's
domain notes as well as the morning page. The notes are the file its CLAUDE.md names; start one
if the project has none and the answer will matter again. The morning page gets the plain-words
version and is gone in a few days; the notes keep it for every later session. Each entry has:

- the finding in plain words, and how sure it is (several meta-analyses agree, consistent but
  thin, or reasoned from neighbouring evidence);
- what it means for the project: the rule it supports, or the gap it shows;
- its sources, linked to the paper itself (DOI or PubMed, not a blog's summary of it), each
  checked to resolve to the paper it is cited for, with the one finding it supports and whether
  only the abstract was read;
- leads worth a future search, kept apart from conclusions.

Newer evidence replaces an entry rather than stacking beside it. A rule that rests on an entry
names its section, so a changed entry sends the next cycle to the rules it touches.

### The morning page

BACKLOG.md and DEVLOG.md hold what waits on madCode. On top of them, each project keeps a
morning page madCode reads on their phone: what needs them, then a report of what the cycles
did. Every run keeps its project's page, in any repo, whether or not the prompt names it.
Without a link in the prompt, find it with `Artifact` (`action: "list"`) by its title,
**<Project> Morning Page** (Gym Morning Page, newspapeRSS Morning Page); if none exists,
create it, modelled on an existing one, and say so. Never start a second page for a project.

- Read the page (`Artifact`, `action: "read"`), change what this run touched, and publish to
  the same link. Another session may have published since; build on what the read returned.
- First, the decisions waiting on madCode, most blocking first, each with why in a sentence or
  two and an answer box: options where they're known, a free-text note always. Answers save to
  the page's `db` store under `decisions/<slug>`, so read them (`ArtifactData`) at the start of
  each run before choosing, act on them, and take answered items off. Then the things only they
  can do, as ticks the same way, linked to where they act (the PR, the issue, the BACKLOG.md
  line on GitHub).
- Then the cycles report, newest first: one card per cycle with its project and number, what it
  picked and why in a sentence, what changed for madCode in plain words (not the code), its PR,
  and anything that went wrong or was left, linked to the DEVLOG.md entry for the detail. Cards
  older than a few days come off; DEVLOG.md keeps them.
- Everything on it is also in BACKLOG.md or DEVLOG.md, so the page can always be rebuilt from
  them. An item answered or done comes off.
- It is private, but it follows the project's privacy rules anyway: no figures about anyone's
  own data.

### Lessons become rules

A lesson the log already holds, learned again, is a missing rule, not a lesson. Make it a check
(a test, a CI step, a hook) when it can be checked; otherwise a line in the project's CLAUDE.md,
or in this skill if it holds for any project. Say which in the entry.

Keep the log to its last ten or so cycles. An older entry is cut, since git keeps it, once
anything still true in it has become a rule.

## Stopping

When the stop rule is met, first check the machine against the run's baseline as well as this
cycle's start: small leftovers that each cycle let pass add up over a run. Then a closing
DEVLOG.md entry (what the run did, what waits on madCode,
where a next run should start), then turn the scheduled task off with `update_trigger`
(`enabled: false`; a session the task started can't delete it), and say so.

## Leave the machine as you found it

Every cycle, before it ends. In a cloud container this is the background-work rule above. On
madCode's own computer, also: nothing you started still running (`ps`), no temporary files or
checkouts left behind, and free disk and load what they were when the cycle began; note both at
the start so there is something to compare with. The first cycle of a run also writes them in
its cycle log entry, as the run's baseline. Anything you can't put back is the first line
of the summary.

**Emulators above all.** A cycle that starts an emulator shuts it down before it ends
(`adb emu kill`, then check `adb devices` and that no `qemu` or `emulator` process is left):
one left running has run for eight hours.

The one exception is a run whose cycles are at most 30 minutes apart, where restarting each time
would cost more than the cycle. Unless this is the run's last cycle, the emulator may stay up
for the next, but only behind a watchdog that shuts it down whatever happens to the run:

    W=${TMPDIR:-/tmp}/claude-emulator-watchdog.pid
    stop_watchdog() { [ -f "$W" ] && { p=$(cat "$W"); rm -f "$W"
      ps -p "$p" -o args= | grep -q 'emu kill' || return 0
      kids=$(pgrep -P "$p"); kill "$p"; [ -n "$kids" ] && kill $kids; }; return 0; }
    stop_watchdog
    nohup sh -c "sleep 2700; '$(command -v adb)' emu kill; rm -f '$W'" >/dev/null 2>&1 &
    echo $! > "$W"

That is 45 minutes from the end of this cycle. The next cycle runs the same lines to restart the
clock; if no next cycle comes, the emulator stops anyway. Two details matter: the shell is
stopped before its `sleep`, or it goes on to `adb emu kill`; and a process is killed only if it
is still the watchdog, since a stale PID file can name a process that has nothing to do with it.

The emulator and its watchdog are the only things allowed to outlive a cycle, and the cycle log
entry says they were left running. The last cycle of a run, and any cycle in a run spaced
further apart, shuts the emulator down and runs `stop_watchdog`.

At the start, an emulator already running is noted. If the last cycle left it behind a watchdog,
use it and renew the watchdog. If a cycle left it without one, stop it. Otherwise it may be
madCode's, so leave it, and put it first in the summary and on the morning page.

## Finish

A short summary: what was picked and why, what was measured, what changed (with PR links), what
waits on madCode, how many cycles are left, and the morning page's link.

## Scheduling a run

The scheduled task's prompt stays short, since this skill holds the procedure:

    <Project> cycles: use the cycles skill.
    Repositories: <which, and anything unusual about them>.
    Stop: after cycle <N> | on <date> | when BACKLOG.md's Done when is ticked.
    Scheduled task: <id>. Mix: <build/audit>. Approved: <items>.

The id exists only once the task does, so create it, then edit its prompt to add the id. The
session needs the repository holding this skill checked out alongside the project. Space the
runs so a cycle's CI has come back before the next starts.
