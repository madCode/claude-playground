# Every app shows which build it is

An app here must have somewhere the owner can **see and copy its version**.

## Why

The owner reports what they see on their phone. Without a version, neither
of us knows which build that was — and "this text feels small" means
something different depending on whether the build predates the change that
enlarged it. Guessing wrong costs a round trip every time, and guessing
*right* is luck.

This came up on 2026-10-12: the type scale was raised, a screenshot came
back still looking small, and there was no way to tell whether the
screenshot was of the new build or the old one.

## What it needs

- The **version name and build number**, from `BuildConfig` or equivalent —
  never a string typed into the UI, which goes stale silently.
- **Copyable in one tap.** The owner is on a phone; retyping
  `0.1.0-debug.66+c0f0d17` is not a thing anyone will do.
- Somewhere reachable without being in the way. Prefer the screen that is
  read unhurried over a settings menu nobody opens; avoid the screens with
  a job to do.

In Carry it sits at the bottom of the *Since* tab, which is the one read at
home rather than in a lobby or a car.

## What it is not

Not a changelog, not an update check, not a diagnostics panel. One line.
