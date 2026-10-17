# Cycle log

For the next cycle: what to do differently, newest first. What each cycle built is in
[DEVLOG.md](DEVLOG.md). Keep the last ten or so.

### Cycle 27 — build: a queued post goes to its own blog

- Commit before every falsification: a `git checkout` of a file to restore a break also
  reverted uncommitted review fixes in it, and they had to be re-applied by hand.
- `FakeGitHub.addRepository` serves a second blog; `TwoBlogsTest` shows how to drive two blogs
  through the real switch path. Cycle 2 builds on both.
- The review ran through the `code-review` skill (the build agent had no subagent tool); it found
  three real races and gaps. Keep pointing it at switch and sign-out during background work.

### Cycle 26 — design: several sign-ins and blogs

- The design waits on madCode in [#137](https://github.com/madCode/claude-playground/issues/137).
  Build only after a clear yes there, and build its three calls as answered, not as proposed.
- Start build cycle 1 from the issue's "How it's built": the code map in it (file:line) was
  checked on this cycle's main. `Account` is one DataStore record, `Settings` app-wide,
  `CachedPost` has no blog column; only `Draft.blog` is per blog.
- The SDK installs with `tools/install-android-sdk.sh`, and the screenshot test
  then renders all 20 screens. Run both in the background at the start of any cycle that
  touches a screen.
- Mockups for a proposal: HTML at 412×915 with the app's own `res/font/bricolage.ttf` and the
  Zine colours from `ui/theme/Theme.kt`, beside the screenshot test's "before" PNGs. Put the
  note boxes where they cover nothing: the first render put two over rows.
