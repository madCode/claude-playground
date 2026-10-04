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
- **Edits are safe.** If a post changed on GitHub since you opened it, the app won't overwrite it.
- **Photos without your location.** Picked photos are scaled down and stripped of EXIF before
  they go in the post's commit.
- **Preview, and a nudge when it's live.** See the post as a page before publishing; get a
  notification with its address once GitHub Pages has rebuilt.

**Install:** the newest build from main is
[jekyll-poster-debug.apk](https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk).

**Sign in:** make a fine-grained token for your blog's repository with Contents (read and
write) and Actions (read). The app links to GitHub's form with these filled in.

## Docs

- [Design](docs/DESIGN.md): how the app works.
- [Architecture](docs/ARCHITECTURE.md): how the code fits together.
- [Backlog](docs/BACKLOG.md): what's next.
- [Devlog](docs/DEVLOG.md): what changed each cycle, and why.
- [Existing solutions](docs/research/existing-solutions.md): what else is out there.
