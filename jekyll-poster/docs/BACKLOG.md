# Backlog

The running plan. Each cycle picks what matters most for the writer next, builds it with tests,
and moves it to Done.

## Next

- [ ] Preview: render the Markdown as the site would (commonmark, the blog's base URL, Liquid
  `relative_url` image links resolved)
- [ ] Photos: pick from the gallery, downscale, strip EXIF (location!), upload beside the blog's
  images in the same commit, insert the Markdown link with `relative_url`
- [ ] Notify when a post is live, with a link to it (permalink from `_config.yml`)
- [ ] Sign in with GitHub (device flow, GitHub App client ID at build time), beside the token
- [ ] Save to the blog's `_drafts` (unpublished, but on GitHub and on the laptop)
- [ ] Settings: switch blog, signed-in account, app version
- [ ] Find the Compose-test Room hang (DEVLOG, cycle 1)

## Later

- [ ] Front matter beyond the basics: an escape hatch for raw YAML, per-blog templates
- [ ] Delete a post; unpublish (`published: false`)
- [ ] Collections other than posts; `collections_dir`
- [ ] Hugo, or other static site generators on GitHub

## Done

- [x] Cycle 1: sign in with a token, pick the blog, read posts and categories, write, pick
  categories and tags (existing or new), publish once, edit safely, follow the Pages build
