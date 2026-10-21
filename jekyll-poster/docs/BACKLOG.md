# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] Sign in with GitHub, a TalkBack listen, and updating itself on a phone, by a person, now and then
  ([DEVICE-CHECKS.md](DEVICE-CHECKS.md)).

## Waiting on madCode

- [ ] Per-blog templates for new posts: Jekyll's `defaults:` already covers front matter for every
  post. Build a small "New posts start with" (tags, categories, a body), or drop it?
  ([#182](https://github.com/madCode/claude-playground/issues/182))
- [ ] A "keep private" word list per blog, checked before every publish (obyde's find/replace,
  for posts not from Obsidian): should a listed word be replaced, or block the publish until
  it's taken out?
- [ ] Collections other than posts, and `collections_dir`: worth building?
  ([#179](https://github.com/madCode/claude-playground/issues/179))
- [ ] Hugo, or other static site generators on GitHub: keep, narrow or drop?
  ([#180](https://github.com/madCode/claude-playground/issues/180))

## Done

Cycles 40 on, newest first. Earlier cycles, and what madCode set up, are in [DEVLOG.md](DEVLOG.md).

- [x] Cycle 55: a post's notification names its blog when another is open; a tap opens the app on that blog (#137)
- [x] Cycle 54: a second documentation pass
- [x] Cycle 53: a documentation pass
- [x] Cycle 52: debug builds say when a newer one is out, and install it
- [x] Cycle 51: the debug release numbered, never cancelled mid-upload, its actions pinned; the README says how to update
- [x] Cycle 50: the update system, checked against Carry's and newspaperss's
- [x] Cycle 49: the device live check also publishes a photo, edits and deletes from the screens, and meets an edit conflict
- [x] Cycle 48: per-blog templates checked against Jekyll's `defaults:` before building (#182)
- [x] Cycle 47: Hide from the site (`published: false`) and Show on the site again, from the editor's menu
- [x] Cycle 46: backlog grooming
- [x] Cycle 45: on the emulator, New post from the launcher, and the app killed while a post waits
- [x] Cycle 44: on the emulator, front matter the app can't follow, and Sign in with GitHub twice
- [x] Cycle 43: Only connect through a VPN, checked nightly with a real VPN (WireGuard and Proton)
- [x] Cycle 42: the laptop's device checks on CI's emulator; the editor's picker and dialogs survive rotation
- [x] Cycle 41: by the day alone, the commit is dated by the day too (madCode chose it, 2026-10-20)
- [x] Cycle 40: the laptop checks written down, with `tools/emulator-check.sh`
