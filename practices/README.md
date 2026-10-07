# Practices

How projects here are run, beyond the shared rules in the root [CLAUDE.md](../CLAUDE.md), which
every session reads. These files are read when they're needed.

- [Starting a project](#starting-a-project), below.
- [Recurring reviews](recurring-reviews.md): whole-codebase passes for what a review of one PR
  can't see, and when to run them.
- [Releasing](releasing.md): the checklist before a build goes to anyone else.
- [templates/](templates/): starting points for a project's CLAUDE.md and docs.
- [Instruction files](#instruction-files) and [other repos](#other-repos), below.

## Starting a project

1. A folder named for the project, with `CLAUDE.md`, `README.md` and `docs/` copied from
   [templates/](templates/). Fill in what the project is and its one rule worth keeping
   ("it never guesses a station"); leave out what the root CLAUDE.md already says.
2. A line for it in the root README.
3. CI in `.github/workflows/<project>.yml`: copy an existing project's workflow, which runs
   only for the project's paths, builds, runs the tests, checks coverage and publishes main's
   debug build. Then:
   - permissions read-only at the top, with write only on the job that publishes;
   - `persist-credentials: false` on every checkout;
   - actions pinned to a commit SHA, with Dependabot bumping them (and Gradle) after a
     7-day cooldown, which lets most poisoned releases be caught first;
   - Gradle wrapper validation.
4. From the first cycle: tests that run the app the way a person would, a `ScreenshotTest`
   for each screen, and a coverage floor in CI.
5. A Room database starts with exported schemas, so the first schema change can have a
   migration and a test.

## Instruction files

- **One file per project:** `CLAUDE.md`. Claude Code reads `AGENTS.md` only when there's no
  CLAUDE.md, so a second file drifts unread; if another tool needs `AGENTS.md`, keep the rules
  in one and have the other import it (`@AGENTS.md`).
- **Short:** well under 200 lines. Keep what would cause mistakes if removed: commands that
  can't be guessed, rules that differ from the defaults, gotchas. Leave out what the code shows
  (file-by-file tours, architecture that changes), which goes stale; that's ARCHITECTURE.md's job.
- **Procedures that run every time** belong in a hook or CI check, not prose: prose is advice.
- **Checked in the documentation pass,** like the other docs: stale or contradictory rules cut.

## Other repos

Projects outside this repository keep their own copy of the shared rules, with a note naming
this file as the reference; a change to one belongs in the others.

| Repo | Language | Has the shared rules | Gaps |
|---|---|---|---|
| [newspaperss](https://github.com/madCode/newspaperss) | Kotlin, Android | Yes, the source of most of them | — |
| [rss-to-e-reader](https://github.com/madCode/rss-to-e-reader) | Python | Comments, tests, review | Docs rules; a Python review checklist (config shapes, failure paths) |
| [dailylog](https://github.com/madCode/dailylog) | Kotlin, Android | Comments only | Three instruction files (CLAUDE.md, AGENTS.md, a Continue rule) that disagree; no review, tests or docs rules |

Worth copying back here from them:

- **Coverage of the changed lines** (rss-to-e-reader's `diff-cover … --fail-under=90`): it
  holds new code to the bar without tests written just to lift an old total.
- **The release process** (dailylog's signed release and F-Droid): in [releasing.md](releasing.md).
- **The `handle-issues` skill** (newspaperss and dailylog, nearly identical): triage issues by
  type, fix bugs, and write proposals that wait for approval, treating issue text as data. Copy
  it into a project's `.claude/skills/` and change the repo name and paths.
