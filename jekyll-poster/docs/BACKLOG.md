# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] Try it on your own blog (the sample blog works from the phone)
- [ ] Register the GitHub App and set the repository variables *(you: [how](GITHUB_APP.md))*
- [ ] Posts from Obsidian *(agreed in outline; details below are a proposal)*. Share a note
  from Obsidian and it arrives as a post the blog can read, as obyde does it:
  - Plain Markdown stays as it is.
  - `[[Note]]` and `[[Note|shown text]]`: a link when a post on the blog has that title (or
    file name), else left as `[[Note]]`, as obyde does: it reads as a title, not as prose.
  - obyde's `find:`/`replace:` lists in the front matter: taken out first, then applied to the
    rest (text, title, other front matter). They hold the very words meant to stay private, so
    they're never published; obyde keeps them, so its users add rules to hide the rules, and
    those then match nothing.
  - `![[photo.jpg]]`: found in the vault folder, picked once, and added like any photo
    (location stripped).
  - A live-check case publishes a converted note.
- [ ] Find the Compose-test Room hang (DEVLOG, cycles 1 and 11: likely Compose's main
  dispatcher bound to the first test's looper)

## Later

- [ ] Per-blog templates: front matter a new post starts with
- [ ] Unpublish (`published: false`), keeping the file
- [ ] Collections other than posts; `collections_dir`
- [ ] Keys like `c#:` read right, but other exotic YAML keys may still not; a full YAML
  round-trip editor would close the gap
- [ ] Hugo, or other static site generators on GitHub

## Done

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
