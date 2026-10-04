# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.
Times are Pacific.

**Latest debug build:** [jekyll-poster-debug.apk](https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk)

## Night 1 · Sun 4 Oct

### Cycle 5: a Markdown toolbar, photos at the cursor, settings (04:15–05:00)
- **Shipped:** a toolbar above the keyboard for bold, italic, link, heading, list, quote and
  code, each undone by pressing it again; photos go in at the cursor on a paragraph of their
  own; Settings (the blog and its address, switch blog, sign out, version).
- **Cycles 3–4's review found** (all fixed, with tests): a post that failed at the commit and
  was then sent to the other destination used the old name, so a "draft" could go live; only
  the last text sent was remembered, so two lost commits in a row could still publish twice
  (every sent sha is kept now); a Jekyll draft whose new name was taken meanwhile retried
  forever; two renamed photos could get one name and one picture replace the other; a token
  renewal finishing after sign-out signed the writer back in; a draft published from a laptop
  under the same name was taken for the phone's own move and the phone's edits dropped; a
  `url` that already named the repo got it twice; one dropped poll ended Sign in with GitHub;
  "cancelled on GitHub" read as a bad token; expiry counted from picking the blog, not from the
  token; damaged or huge GIFs gave raw errors or could run out of memory.

### Cycle 4: the blog's _drafts (03:45–04:15)
- **Shipped:** save a new post to the blog's `_drafts` to finish on a laptop; update a Jekyll
  draft in place; publish one from the phone, dated and moved to `_posts` in one commit.
- **Cycle 2's review found** (all fixed, with tests): cycle 1's debug build shipped a database
  that this one changed without a migration, so it would crash on launch (now version 2 with a
  migration and `MigrationTest`); a post that landed unheard and then failed could be published
  twice when sent again (the sha last sent now identifies it); a captive portal's HTML left a
  post stuck "publishing"; a new post could take an older post's address under `/:title/`;
  categories with spaces and folder categories made the wrong address, as did dates without an
  offset; a duplicated key was read from the last line but written to the first; mirrored photos
  stayed mirrored; a photo picked just before Publish or Back was lost; prepared photos were
  never deleted, and the preview read them on the main thread; photos could overwrite a file on
  the blog; a project site's address lost its `/repo`.
- **Left:** a post with both `category:` and `categories:` shows only `category:` (as Jekyll's
  documented rule says; the review thought Jekyll combines them, unverified).

### Cycle 3: Sign in with GitHub (03:15–03:45)
- **From you:** let people sign in either way, token first.
- **Shipped:** GitHub's device flow for a GitHub App: a code to copy, github.com opened, the
  app waits; expiring tokens renewed before they expire, refresh tokens sealed like the token.
  The button appears once a build names the app: [setup](GITHUB_APP.md), yours to do since only
  you can register an app. CI picks it up from repository variables.

### Cycle 2: preview, photos, live (02:40–)
- **Shipped:** a preview of the post as a page; photos from the picker, turned upright, scaled
  and stripped of EXIF (location), uploaded in the post's own commit and linked with
  `relative_url`; each published post's address, worked out as Jekyll would (checked against
  what GitHub Pages built for the sample blog); a notification when the site is live, or why not.
- **Cycle 1's review found** (all fixed, with tests): a connection dropping mid-response escaped
  as a bare error and left the post "waiting" for good; editing from a stale list, or retrying
  an edit that had landed, looped on "changed on GitHub"; duplicate front matter keys (Jekyll
  allows them) read as no keys, so Update overwrote the title and dropped categories;
  `category: Web Development` was split in two, unlike Jekyll; keys like `og:image` were
  swallowed into the key above and deleted; two queued posts with one title could overwrite each
  other, and a commit rebuilt after a race could overwrite a concurrent edit (commits now check
  the files they rely on); drafts weren't tied to a blog; identical files at two paths were one
  post; more than 999 posts broke the cache on Android 8–11; a truncated tree was read as whole;
  any workflow failing on the commit read as "build failed", and the watcher ran 95 minutes, not
  ten; GraphQL rate limits weren't retried; a queued post could be deleted mid-commit.
- **Seen live:** the sample blog's Pages build runs as "pages build and deployment" on the
  commit's sha, which is what the build watcher follows; its post URLs match the app's.

### Cycle 1: post from the phone (00:35–02:40)
- **From you:** a phone app for a Jekyll blog on GitHub Pages; token sign-in to start, device
  flow later; pick categories from what the blog already uses, or add one.
- **Research:** [existing solutions](research/existing-solutions.md). Nothing on Android
  publishes through the API without cloning the repo; nothing on mobile reads a blog's own
  categories; nothing reports the Pages build. Those three became the app's core.
- **Shipped:** token sign-in and blog picker; the blog's posts, categories and tags; writing with
  autosave; category and tag pickers; publishing through WorkManager (one commit, retried
  offline, never twice); editing posts already on the blog without disturbing their other front
  matter; refusing an edit when the post changed on GitHub; following the Pages build.
- **Caught on the way:** a comment above a key was attached to the key before it, so editing
  `tags` deleted the comment about `image`; comments now go with the key below them.
- **Got in the way:** a Compose UI test of the edit flow hangs in a Room write once another
  Compose test has run in the same JVM (also with a file database and separate executors). The
  same steps pass through the ViewModels (`EditorViewModelTest`), so the UI test stops at
  opening the post. Open in the backlog.
