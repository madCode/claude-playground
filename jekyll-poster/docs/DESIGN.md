# Design

Who it's for: someone with a Jekyll blog on GitHub Pages who can code, wants to blog more, and
doesn't want to open a laptop and make a commit for every post.

## Signing in

- A fine-grained personal access token, pasted once. The app's link opens GitHub's new-token
  form with the name and permissions filled in: Contents read and write (to commit posts) and
  Actions read (to see the site build). The writer picks the repository on GitHub.
- The token is checked, then the app lists the repositories it can write to, Pages sites first.
  Picking one reads the blog.
- The token is stored sealed with an Android Keystore key that never leaves the phone. It isn't
  backed up; a restored phone asks to sign in again.

## Reading the blog

- Posts are files Jekyll would render: in any `_posts` folder (nested ones too, like
  `travel/_posts/` or `_posts/2025/`), named `YYYY-MM-DD-title.md`; and drafts in `_drafts`.
- Categories come from `categories:` or `category:`, as a YAML list or a space-separated string
  (as Jekyll reads them), plus folders above `_posts`. Tags likewise.
- Spellings that differ only in case are one category (Jekyll's URLs don't tell them apart),
  shown in the spelling most posts use.
- Each post is fetched once; later reads fetch only posts whose content changed.

## Writing

- A post has a title, categories, tags and a Markdown body. It saves as you type.
- Categories and tags are picked from the blog's own, most used first with their counts, or
  typed in as new ones.
- A new post with nothing written is dropped when you leave it.
- Sharing text or a link to the app starts a post with it.

## Publishing

- Publish queues the post; it goes when there's a connection, retrying if GitHub can't be
  reached.
- A new post becomes `_posts/<date>-<slug>.md`. Its date has the phone's offset
  (`2026-10-04 22:15:00 -0700`), so the site builds it on the day the writer meant. A name
  already taken gets `-2`.
- `layout: post` is written only when `_config.yml` doesn't already default posts to a layout.
- Path and date are fixed the first time a post is sent, so a retry after a crash finds the
  post already there and doesn't publish it twice.
- Each publish is one commit ("Add post: Title").
- After publishing, the app follows the Pages build for that commit (an Actions run) for about
  ten minutes: "rebuilding", then "live" or "build failed". With no Actions access, it just says
  published.

## Editing a post on the blog

- Tapping a post opens the blog's copy. Changes save on the phone until Update.
- Update rewrites only title, categories, tags and body; every other key, comment and quote
  stays as written. A post that spells it `category:` keeps that key.
- If the post changed on GitHub since it was opened, Update refuses and says so.

## Not yet

See the [backlog](BACKLOG.md): images, preview, Sign in with GitHub, drafts to `_drafts`.
