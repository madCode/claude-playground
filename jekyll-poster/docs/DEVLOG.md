# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.
Times are Pacific.

**Latest debug build:** [jekyll-poster-debug.apk](https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk)

## Night 1 · Sun 4 Oct

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
