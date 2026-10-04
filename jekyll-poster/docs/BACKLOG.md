# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] Try it on your own blog (the sample blog works from the phone)
- [ ] Register the GitHub App and set the repository variables *(you: [how](GITHUB_APP.md))*
- [ ] A first green run of the live check *(you: the `SAMPLE_BLOG_TOKEN` secret; it runs from
  main's Actions tab once merged)*
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
