# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] A request record in debug builds, for the audit below (madCode chose it, 2026-10-20): every
  request the app sends to GitHub, per sign-in, written where a session can collect it from the
  emulator. The `Authorization` header is always written as `(hidden)`, with a test that a
  token never reaches the record. Release builds have none of it.
- [ ] The tester account (madCode, agreed 2026-10-20): a GitHub account and organisation for
  testing, three copies of the sample blog, a token each in Actions secrets.
  [TESTER_ACCOUNT.md](TESTER_ACCOUNT.md) has the steps. The live checks below wait on it.
- [ ] A device suite on every PR: the real app on an emulator in Actions (newspaperss's
  `device-tests.yml`), against `FakeGitHub` served on the device. Shares from a test-only
  sender app, the New post shortcut, WorkManager for real, the app killed while a post is
  queued, the "it's live" notification read with UiAutomator.
- [ ] A device live check, nightly and by hand: the same emulator, signed in through the
  screens with the tester's tokens, against the real test blogs. Publish with a photo, wait for
  Pages and the notification, edit, delete; two blogs under two sign-ins; an edit conflict made
  by the workflow committing while the post is open. Uploads screenshots, logcat and the
  request record. A cloud session starts it and reads its artifacts through the GitHub tools.
- [ ] `:core:liveCheck` against all three test blogs, the organisation one included.
- [ ] Audit for several blogs: a security/privacy read of the requests each account makes,
  against the request record from a device live check run (not the code). A cloud session can
  do it from that run's artifacts.
- [ ] Laptop emulator checks, in `docs/DEVICE-CHECKS.md` and `tools/emulator-check.sh`: what
  Actions can't do. Sign in with GitHub (the device code entered in the desktop app's browser,
  signed in as the tester), sharing from the real Obsidian app, the gallery and the camera,
  rotation, TalkBack, large text and dark theme.


## Later

- [ ] Per-blog templates: front matter a new post starts with
- [ ] Unpublish (`published: false`), keeping the file
- [ ] Collections other than posts; `collections_dir`
- [ ] Hugo, or other static site generators on GitHub
- [ ] A "keep private" word list per blog, checked before every publish (obyde's find/replace,
  for posts not from Obsidian)

## Done

- [x] Cycle 34: removing the open blog's sign-in never reads as signed out
- [x] Cycle 33: exotic YAML keys, checked: an edit that would change keys the app doesn't edit
  isn't made
- [x] Cycle 32: the Compose-test hang found (the main looper, not Room), and the preview flake
  counted and fixed
- [x] Cycle 31: a UX pass on the blog switcher (nothing to change)
- [x] Cycle 30: the same account signed in twice is one sign-in
- [x] Several sign-ins and blogs, in three build cycles
  ([#137](https://github.com/madCode/claude-playground/issues/137)): cycles 27–29
- [x] The GitHub App registered, for Sign in with GitHub (madCode)
- [x] Cycle 29: privacy switches and the VPN per blog
- [x] Cycle 28: several sign-ins, the blog switcher, Remove
- [x] Cycle 27: a queued post goes to its own blog after switching; sign-ins and blogs in the database
- [x] Cycle 26: design for several sign-ins and blogs (#137)
- [x] Tried on madCode's own blog
- [x] Edit a post that hasn't gone out yet
- [x] Cycles 20–25: privacy for anonymous writers (3), simpler code (3)
- [x] Cycle 19: only connect through a VPN
- [x] Links to posts with `[[`
- [x] Cycle 18: posts from Obsidian
- [x] Cycle 17: Blog & privacy, one page for what the blog shows beyond its posts
- [x] A live check: publishes to and deletes from the sample blog from Actions
- [x] Cycle 16: a privacy audit, and its fixes
- [x] Cycle 15: the tag picker, from first use
- [x] Cycle 14: search your posts
- [x] Cycle 13: a documentation pass
- [x] Cycle 12: take a photo with the camera
- [x] Cycle 11: New post from the app icon; a share no longer starts a post again on Back
- [x] Cycle 10: delete a post from the blog
- [x] Cycle 9: a documentation pass
- [x] Cycle 8: more front matter (YAML), filter by category
- [x] Cycle 7: the zine look, over four design rounds
- [x] Cycle 6: photos shared from the gallery, alt text
- [x] Cycle 5: Markdown toolbar, photos at the cursor, settings
- [x] Cycle 4: the blog's `_drafts` (save, update, publish by moving)
- [x] Cycle 3: Sign in with GitHub (device flow), token renewal
- [x] Cycle 2: preview; photos (upright, scaled, EXIF stripped, same commit); the post's address
  and a notification when it's live
- [x] Cycle 1: sign in with a token, pick the blog, read posts and categories, write, pick
  categories and tags, publish once, edit safely, follow the Pages build
