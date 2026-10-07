# Recurring reviews

The review of each PR sees one diff. These passes look at the whole project, for what no single
diff shows. Each goes the same way:

1. Two or three fresh-eyes subagents, each with one area, asked for concrete findings only:
   file:line and a failure scenario, most severe first, no edits.
2. Check each finding against the code before acting on it; some won't hold.
3. A PR per area, saying what was found, what was fixed and what was left and why. What needs
   the owner's decision is listed for them, not done.
4. A line in DEVLOG.md, and the next pass's date or cycle in BACKLOG.md.

## Security and supply chain

Before a project's first release, then before each release or every 20–30 cycles. Use OWASP's
mobile checklist ([MASVS](https://mas.owasp.org/MASVS/)) as the list of areas, so nothing is
skipped: storage, crypto, network, platform, code, privacy. Two tools do part of it on every
push and are worth adding to CI: [OpenSSF Scorecard](https://github.com/ossf/scorecard) (its
code review and branch protection checks will score low for a solo owner; that's expected) and
[zizmor](https://github.com/zizmorcore/zizmor) for the workflows.

- Hostile input: documents that expand (XML entities), regexes that slow down badly, huge
  files and images, links and intents that leave the app.
- Secrets and accounts: where tokens are kept, what's logged, what a backup carries.
- CI: what each job can write, whether checkouts keep a token, actions pinned to a commit.
- Signing: keys in the repository, and who can sign a build that installs over a tester's.
- Dependencies: unused ones, and how updates arrive.
- Android: cleartext traffic off, components not exported unless they must be, `content://`
  not `file://`, as few permissions as will do.

## Architecture and test health

Every 30–40 cycles, or before a release. The first one in newspaperss found real bugs, not only
tidying ([#163–#166](https://github.com/madCode/newspaperss/pulls?q=is%3Apr+audit)).

- Duplication, and code that has grown complicated or passes many values around.
- How features fit together: what one deletes or changes that another still uses, work that
  can be cut short halfway, two runs at once.
- Platform lifecycle: rotation, the app killed and restored, work that outlives a screen.
- Tests that can never fail, waits that flake under load, copied test helpers, coverage that
  counts generated code.
- Docs and the CLAUDE.md files checked against the code: stale, contradictory or long rules cut.
