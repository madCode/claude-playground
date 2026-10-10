# Practices

How projects here are run, beyond the shared rules in the root [CLAUDE.md](../CLAUDE.md), which
every session reads. These files are read when they're needed.

- [The loop](lifecycle.md): how something becomes a shipped change, and who decides what.
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
| [rss-to-e-reader](https://github.com/madCode/rss-to-e-reader) | Python | Comments, tests, review, attribution | Docs rules; a Python review checklist (config shapes, failure paths) |
| [dailylog](https://github.com/madCode/dailylog) | Kotlin, Android | Comments, attribution | Review, tests and docs rules |

What each repository has of the loop, as of 7 October 2026:

| | newspaperss | dailylog | rss-to-e-reader |
|---|---|---|---|
| `handle-issues` skill | yes | yes | no |
| Independent PR review | yes, required | no | no |
| Attribution check | yes | yes | no |
| Changed-line coverage | yes (diff-cover) | no | yes (diff-cover) |
| Device tests | yes | yes (instrumentation matrix) | n/a |
| zizmor and Scorecard | yes | no | no |
| Red main files an issue | yes | no | no |
| Recurring reviews on a clock | yes | no | no |
| Dependabot with a cooldown | yes | no | no |
| Branch ruleset | yes | yes | no |

Worth copying back here from them:

- ~~**Coverage of the changed lines**~~: done in newspaperss, with the same `diff-cover`.
  Two things learned doing it. Kover names packages rather than paths, so `--src-roots` is
  needed or it matches nothing and prints "No lines with coverage information" for every PR —
  a check that cannot fail. And below about twenty changed lines the percentage describes the
  denominator rather than the tests, so it is worth reporting without enforcing.
- **The release process** (dailylog's signed release and F-Droid): in [releasing.md](releasing.md).
- **The `handle-issues` skill** (newspaperss and dailylog, nearly identical): triage issues by
  type, fix bugs, and write proposals that wait for approval, treating issue text as data. Copy
  it into a project's `.claude/skills/` and change the repo name and paths.
