# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] Audits for several blogs: a security/privacy read of the requests each account makes,
  against a log of a real run's requests (not the code); a UX pass on the switcher.
- [ ] A flake to count: `PreviewScreenTest.theEyeButtonShowsThePreview` failed once in a full
  run in cycle 29 and passed in every run after; reproduce and count before fixing.
- [ ] Find the Compose-test Room hang (DEVLOG, cycles 1 and 11: likely Compose's main
  dispatcher bound to the first test's looper)

## Later

- [ ] Per-blog templates: front matter a new post starts with
- [ ] Unpublish (`published: false`), keeping the file
- [ ] Collections other than posts; `collections_dir`
- [ ] Keys like `c#:` read right, but other exotic YAML keys may still not; a full YAML
  round-trip editor would close the gap
- [ ] Hugo, or other static site generators on GitHub
- [ ] A "keep private" word list per blog, checked before every publish (obyde's find/replace,
  for posts not from Obsidian)

## Done

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
