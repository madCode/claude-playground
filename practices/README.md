# Practices

How projects here are run, beyond the shared rules in the root [CLAUDE.md](../CLAUDE.md), which
every session reads. These files are read when they're needed.

- [The loop](lifecycle.md): how something becomes a shipped change, and who decides what.
- [Starting a project](#starting-a-project), below.
- [Handling issues](../.claude/skills/handle-issues/SKILL.md): the `handle-issues` skill, which
  sorts issues into bugs to fix and proposals that wait for a yes.
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
| [rss-to-e-reader](https://github.com/madCode/rss-to-e-reader) | Python | Comments, tests, review | Attribution; docs rules; a Python review checklist (config shapes, failure paths) |
| [dailylog](https://github.com/madCode/dailylog) | Kotlin, Android | Yes | The screenshots rule for a PR that changes a screen |

Which repos are behind on [the loop](lifecycle.md), as of 10 October 2026 — the port-next list,
by the step each thing serves:

| The loop's step | What it takes | Missing in |
|---|---|---|
| [2. Sorted before it is built](lifecycle.md#2-it-is-sorted-before-it-is-built) | the `handle-issues` skill, on a daily routine | rss-to-e-reader |
| [4. The author reviews their own work](lifecycle.md#4-it-is-built-and-the-author-reviews-their-own-work-first) | review rules in the project's CLAUDE.md | — |
| [5. CI says what a person shouldn't have to](lifecycle.md#5-ci-says-what-a-person-shouldnt-have-to) | coverage of the changed lines (diff-cover) | — |
| | a review by something with no memory of writing the diff | rss-to-e-reader |
| | the attribution check | rss-to-e-reader |
| | device tests | — (n/a for rss-to-e-reader) |
| | zizmor and Scorecard | rss-to-e-reader |
| [6. It merges itself when it's clean](lifecycle.md#6-it-merges-itself-when-its-clean) | a branch ruleset naming the required checks | rss-to-e-reader |
| [7. A failure that heals itself is written down](lifecycle.md#7-a-failure-that-heals-itself-still-gets-written-down) | the red-main watcher | rss-to-e-reader |
| [8. Whole-codebase passes on a clock](lifecycle.md#8-whole-codebase-passes-on-a-clock) | a scheduled recurring review | rss-to-e-reader |
| Supply chain, between changes | Dependabot with a cooldown | rss-to-e-reader |
| [Screenshots](../CLAUDE.md#screenshots) | before/after images on a PR that changes a screen | dailylog |

So: **newspaperss and dailylog run the whole loop**; dailylog's independent review is present but
not a required check, which is the one difference left between them. **rss-to-e-reader has none of
the loop beyond the rules themselves and changed-line coverage** — in port order, the attribution
check and an independent review first (they are two workflow files and cost nothing), then a branch
ruleset, then the scanners, the skill, the watcher and the clock.

Checked against each repo's workflows, Dependabot config, branch ruleset and CLAUDE.md rather than
from memory, because this table went stale within a day of being written the first time: dailylog
gained six of these on 7-8 October 2026, while the PR adding the table was open.

Worth copying back here from them:

- ~~**Coverage of the changed lines**~~: done in newspaperss and dailylog, with the same
  `diff-cover`. Two things learned doing it on Kotlin. Kover names packages rather than paths, so
  `--src-roots` is needed or it matches nothing and prints "No lines with coverage information"
  for every PR — a check that cannot fail, so dailylog's job fails loudly when changed Kotlin
  lines match none. And below about twenty changed lines the percentage describes the denominator
  rather than the tests, so it is worth reporting without enforcing.
- **The release process** (dailylog's signed release and F-Droid): in [releasing.md](releasing.md).
- ~~**The `handle-issues` skill**~~: now [here](../.claude/skills/handle-issues/SKILL.md), made
  general: it reads the repository's name, commands and privacy rules from the repository, so
  every copy is the same file. Copy it unchanged into a repo's `.claude/skills/`; a change goes
  here first, then into each copy.
- [version-page.md](version-page.md) — every app shows which build it is, copyable in one tap
