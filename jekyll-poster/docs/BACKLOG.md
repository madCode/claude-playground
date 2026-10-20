# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] More device tests on the suite cycle 36 started (`OnTheDeviceTest`): the New post
  shortcut from the launcher, the app killed while a post is queued, a photo shared from another
  app (a test-only sender, like newspaperss's `epubsink`).
- [ ] More of the device live check (`LiveOnTheDeviceTest`): a photo, an edit and a delete
  from the screens, and an edit conflict made by the workflow committing while the post is open.
- [ ] Audit for several blogs: a security/privacy read of the requests each account makes,
  against the request record from a device live check run (not the code). A cloud session can
  do it from that run's artifacts.
- [ ] The first run of the laptop checks ([DEVICE-CHECKS.md](DEVICE-CHECKS.md)): a session in the
  desktop app on madCode's laptop, since a cloud session has no emulator or browser sign-ins.


## Later

- [ ] Per-blog templates: front matter a new post starts with
- [ ] Unpublish (`published: false`), keeping the file
- [ ] Collections other than posts; `collections_dir`
- [ ] Hugo, or other static site generators on GitHub
- [ ] A "keep private" word list per blog, checked before every publish (obyde's find/replace,
  for posts not from Obsidian)

## Done

- [x] Cycle 40: the laptop checks written down, with `tools/emulator-check.sh`
- [x] Cycle 38: a nightly device live check on two of the tester's blogs, keeping the request record
- [x] The tester account, its three blogs and tokens, the GitHub App on them, and a Proton VPN
  account (madCode, 2026-10-20)
- [x] Cycle 37: the live check posts to the tester's three blogs, nightly and by hand
- [x] Cycle 36: device tests on an emulator, on every PR
- [x] Cycle 35: a request record in debug builds, for the several-blogs audit
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
