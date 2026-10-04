# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.
Times are Pacific.

**Latest debug build:** [jekyll-poster-debug.apk](https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk)

## Night 1 · Sun 4 Oct

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
