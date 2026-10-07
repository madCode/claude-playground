# Practices

How projects here are run, beyond the shared rules in the root [CLAUDE.md](../CLAUDE.md), which
every session reads. These files are read when they're needed.

- [Starting a project](#starting-a-project), below.
- [Recurring reviews](recurring-reviews.md): whole-codebase passes for what a review of one PR
  can't see, and when to run them.
- [templates/](templates/): starting points for a project's CLAUDE.md and docs.

## Starting a project

1. A folder named for the project, with `CLAUDE.md`, `README.md` and `docs/` copied from
   [templates/](templates/). Fill in what the project is and its one rule worth keeping
   ("it never guesses a station"); leave out what the root CLAUDE.md already says.
2. A line for it in the root README.
3. CI in `.github/workflows/<project>.yml`: copy an existing project's workflow, which runs
   only for the project's paths, builds, runs the tests, checks coverage and publishes main's
   debug build. Keep its permissions read-only, and add `persist-credentials: false` to its
   checkouts.
4. From the first cycle: tests that run the app the way a person would, a `ScreenshotTest`
   for each screen, and a coverage floor in CI.
5. A Room database starts with exported schemas, so the first schema change can have a
   migration and a test.
