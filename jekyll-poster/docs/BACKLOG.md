# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] The nightly live check through the screens, where it now sets things in code or skips them:
  Hide and Show again, and Jekyll drafts; blog B's sign-in added from Settings and a switch from
  Home's title, and Set up for writing anonymously tapped; the random time with Send now, Stop
  and edit, offline then back, a bad token, a delete that meets a change, and a category, the
  toolbar, a `[[` link and Preview.
- [ ] On every PR's emulator: delete a local draft, Discard changes, Remove from this list; a
  note's Use the publish day; the first Publish asking for notifications, and its tap; Remove a
  sign-in and Send them now; search and category pills; Read the blog again; Forget the vault
  folder; several photos at once, a note as text, Open with.
- [ ] Sign in with GitHub, a TalkBack listen, and updating itself on a phone, by a person, now and then
  ([DEVICE-CHECKS.md](DEVICE-CHECKS.md)).

## Not doing

madCode decided these on the morning page, 21 Oct. Each comes back if a writer asks for it.

- Per-blog templates for new posts
  ([#182](https://github.com/madCode/claude-playground/issues/182)): Jekyll's `defaults:` already
  sets front matter for every post, and the app follows it.
- A "keep private" word list per blog.
- Collections other than posts, and `collections_dir`
  ([#179](https://github.com/madCode/claude-playground/issues/179)).
- Hugo, or other static site generators
  ([#180](https://github.com/madCode/claude-playground/issues/180)): it stays a Jekyll app.

## Done

Cycles 40 on, newest first. Earlier cycles, and what madCode set up, are in [DEVLOG.md](DEVLOG.md).

- [x] Cycle 57: scheduled posts (Schedule… in the editor), woken by an alarm at their time
- [x] Cycle 56: audit: Home's "double reads" in the request record are the publish's own reads, kept
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
