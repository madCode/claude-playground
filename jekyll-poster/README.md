# Jekyll Poster

Write a blog post on your phone and publish it to your Jekyll blog on GitHub Pages, without
touching git. The app commits each post to your blog's repository through the GitHub API, then
tells you when GitHub Pages has rebuilt the site.

- **Works on your repository as it is.** No config file, admin folder or server to add.
- **Your categories and tags.** The app reads the ones your posts already use, most used first,
  and you can add new ones.
- **Front matter the way Jekyll reads it.** Dates carry your time zone, so an evening post isn't
  dated tomorrow. Editing a post keeps every key, comment and quote the app doesn't touch.
- **Writing is on the phone; publishing waits for a connection.** Drafts stay on the phone.
  A post queued offline goes out when you're back online, exactly once.
- **Edits are safe.** If a post changed on GitHub since you opened it, the app won't overwrite
  or delete it.
- **Front matter when you want it.** `image:`, `excerpt:` and the rest as YAML, checked before
  it's published.
- **Private by design.** Photos (picked, shared or taken) are stripped of EXIF and named for
  the post, dates use the site's time zone, and shared links lose tracking codes. No analytics:
  the app talks to GitHub, and the preview to the sites a post's images come from.
- **A bit of fun.** It looks like a risograph zine: cream paper, fluoro inks, a stamp of a
  New post button, and every category in its own colour.
- **Preview, and a nudge when it's live.** See the post as a page before publishing; get a
  notification with its address once GitHub Pages has rebuilt.
- **Quick to start.** Long-press the app's icon for New post, or share text, a link or photos
  to it.

**Install:** the newest build from main is
[jekyll-poster-debug.apk](https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk).

**Sign in:** make a fine-grained token for your blog's repository with Contents (read and
write) and Actions (read); the app links to GitHub's form with these filled in. Or, once a
GitHub App is set up ([how](docs/GITHUB_APP.md)), "Sign in with GitHub".

## Docs

- [Design](docs/DESIGN.md): how the app works.
- [Architecture](docs/ARCHITECTURE.md): how the code fits together.
- [Backlog](docs/BACKLOG.md): what's next.
- [Devlog](docs/DEVLOG.md): what changed each cycle, and why.
- [Existing solutions](docs/research/existing-solutions.md): what else is out there.
