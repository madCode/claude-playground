# claude-playground

Projects built by Claude in cycles, each in its own folder with its own `CLAUDE.md` (what the
project is, its layout and commands). This file holds the rules every project shares; Claude Code
reads it in any project folder. Run commands from the project's folder.

Starting a project, releasing, recurring reviews and the other repos that share these rules:
see [practices/](practices/).

## Privacy

The repository is public. Never commit anyone's own data: no tokens, accounts, hosts, emails or
content from anyone's own setup. Tests, screenshots and sample data use made-up or well-known
public material only.

## Posting as madCode

Claude Code posts from madCode's account, so anything it writes says so **at the top**, where
it's read, not only in a footer a reader scrolls past. The first line of a pull request
description, an issue or PR comment, or a review:

    🤖 **Claude · <tag>**

Tags: `pull request`, `proposal`, `fix ready`, `needs info`, `update`, `review notes`,
`question for madCode`. `update` when none of the others fit. The first comment in an issue
thread also opens by saying who is writing and what to expect, which the `handle-issues` skill
spells out.

Nothing from the Claude GitHub App needs this: its author already says so.

## Comments

A comment is for what the code can't say itself: **why** it is written this way (a constraint, a
trade-off, what goes wrong with the obvious alternative, with a link to the upstream issue when
there is one), **the non-obvious** (a gotcha, an invariant, a surprising dependency), or **a
summary of complex code**. Not restating the code, and not history ("used to", "since the
rewrite"): that belongs in the PR or the commit message. A consequence is fine; a changelog is
not. One comment above a block that shares a reason, not one per line; usually a line or two.
Public API docs (KDoc, docstrings) stay accurate when signatures change.

## Tests

A test should be able to catch a plausible regression. Test behaviour, not structure, end to end
where you can. Don't feed code inputs it can never receive, or write a test only to lift
coverage. A test that waits for the screen or the database waits for the condition, not a fixed
time. When a change has no behaviour to test, say so in the PR instead of inventing a test.

## Documentation

Docs are for people: keep them readable, current and short.

- `docs/DESIGN.md` says how the app works now. A change that alters behaviour updates it, or the
  README, in the same PR.
- `docs/DEVLOG.md` is the history, newest first; `docs/BACKLOG.md` the plan, once there is one.
- Every few cycles, a documentation pass: check the docs against the code, fix what's stale, cut
  what's grown long or become history.
- Plain words over jargon, short sections, one idea per bullet.

## Pull requests

One project per PR, its title starting with the project's name ("BART widget: …"). Several
small PRs beat one large one. The description follows `.github/pull_request_template.md`: what
changed, how it was tested (the commands run and what they showed, not just "tests pass"), what
the review found. Merge only when asked, and only with CI green.

### Review

Before opening a PR that changes behaviour, have a fresh-eyes subagent review the diff. Point it
at the risky parts:

- How the change interacts with other features touching the same data, including whatever else
  deletes or replaces that data.
- Background work: cancellation, retries, removal and re-adding, two runs at once.
- Untrusted input: whatever comes from the network, files or other apps, including links that
  aren't to the web, huge or hostile documents, and slow regexes.
- Other locales: non-Latin digits, international domain names, right-to-left text, older text
  encodings.
- Accessibility: TalkBack labels and headings, large text, touch targets.

On Android, also:

- Rotation, and the app being killed and restored: what's on screen, dialogs, and actions that
  must happen once. `StateRestorationTester` tests the second in Compose.
- Devices that lack standard screens or behave differently (e-readers, other launchers, older
  Android versions).

Ask for concrete findings only: file:line and a failure scenario, most severe first, no edits.
Ask for bugs and gaps against what the change is for, not style or what might be nice: a reviewer
asked for gaps finds some, and chasing every one adds code nobody needed.
Verify each finding before acting on it, and say in the PR what the review found and what was
fixed or deliberately left. Docs-, comment- and config-only changes can skip this; after fixing
the findings, a short second look at just the new diff is enough.

### Screenshots

A PR that changes how a screen looks shows it: before and after images in the description, one
pair per screen or state that changes (only "after" for a new screen).

- Render both with the project's `ScreenshotTest`: "before" from `origin/main`, "after" from the
  branch. A changed screen the test doesn't shoot yet gets a shot added, which also guards it.
- Open the PR, then commit the PNGs to the `claude/screenshots` branch (never merged) under
  `<project>/pr-<N>/` as `<screen>-before.png` and `<screen>-after.png`, and edit them into the
  description with
  `![<screen>, before](https://github.com/madCode/claude-playground/blob/claude/screenshots/<project>/pr-<N>/<screen>-before.png?raw=true)`.
- If they can't be rendered (no Android SDK), say so in the PR.
