# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] More of the device live check (`LiveOnTheDeviceTest`): a photo, an edit and a delete
  from the screens, and an edit conflict made by the workflow committing while the post is open.
- [ ] Sign in with GitHub and a TalkBack listen, by a person, now and then
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

- [x] Cycle 47: Hide from the site (`published: false`) and Show on the site again, from the editor's menu
- [x] Cycle 45: on the emulator, New post from the launcher, and the app killed while a post waits
- [x] Cycle 44: on the emulator, front matter the app can't follow, and Sign in with GitHub twice
- [x] Cycle 43: Only connect through a VPN, checked nightly with a real VPN (WireGuard and Proton)
- [x] Cycle 42: the laptop's device checks on CI's emulator; the editor's picker and dialogs survive rotation
- [x] Cycle 41: by the day alone, the commit is dated by the day too (madCode chose it, 2026-10-20)
- [x] Cycle 40: the laptop checks written down, with `tools/emulator-check.sh`
