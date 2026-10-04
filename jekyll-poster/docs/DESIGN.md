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
- Categories come as Jekyll reads them: `category:` wins and is taken whole
  (`category: Web Development` is one); otherwise `categories:`, a YAML list or a space-separated
  string. Folders above `_posts` add theirs. Tags likewise.
- Duplicate keys read as Ruby reads them, the last one winning.
- Spellings that differ only in case are one category (Jekyll's URLs don't tell them apart),
  shown in the spelling most posts use.
- Each post is fetched once; later reads fetch only posts whose content changed.
- A repository too big for GitHub to list in one go is refused rather than read in part.

## Writing

- A post has a title, categories, tags and a Markdown body. It saves as you type.
- Categories and tags are picked from the blog's own, most used first with their counts, or
  typed in as new ones.
- A new post with nothing written is dropped when you leave it.
- Sharing text or a link to the app starts a post with it.
- **Preview** shows the post as a page: Markdown (with tables and strikethrough), the
  `relative_url` and `site.baseurl` links resolved, site images loaded from the live site, and
  photos not yet uploaded shown from the phone. Other Liquid shows as written. No scripts run.
- **Photos** come from the photo picker. Each is turned upright, scaled to at most 2000 px and
  re-encoded, which drops its EXIF: no location, camera or time goes to a public blog. PNGs stay
  PNG, GIFs are copied as they are. It's named by when it was added
  (`/assets/images/2026/20261004-221500.jpg`, beside the blog's own images) and linked with
  `relative_url`, so it works on a project site.
- A draft belongs to the blog it was written for. Signed in to another blog, it waits, hidden.

## Publishing

- Publish queues the post; it goes when there's a connection, retrying if GitHub can't be
  reached or a connection drops mid-way. Posts go one at a time.
- The first Publish asks to send notifications; publishing goes ahead either way.
- A new post becomes `_posts/<date>-<slug>.md`. Its date has the phone's offset
  (`2026-10-04 22:15:00 -0700`), so the site builds it on the day the writer meant. A name
  already taken gets `-2`.
- `layout: post` is written only when `_config.yml` doesn't already default posts to a layout.
- Path and date are fixed the first time a post is sent, so a retry after a crash finds the
  post already there and doesn't publish it twice.
- Each publish is one commit ("Add post: Title"), with the photos the text still links to.
- The commit is checked against the branch as it is when it lands: if the post's name was taken
  meanwhile, it picks another; a failed post sent again gets a fresh name and date.
- The app works out the post's address as Jekyll would: `permalink` from `_config.yml`, the
  date in the site's time zone (UTC on GitHub when none is set, so an evening post west of
  Greenwich can carry tomorrow's date in its URL), lowercased categories. The site's address is
  the `CNAME` domain, else `url`, else `owner.github.io[/repo]`.
- After publishing, the app follows the Pages deployment for that commit (an Actions run with
  Pages in its name or file; CI and other workflows don't count) for about eleven minutes:
  "rebuilding", then "live" or "build failed", with a notification. Tapping "live" opens the
  post. With no Actions access, it just says published.

## Editing a post on the blog

- Tapping a post opens the blog's copy as it is on GitHub now. Changes save on the phone until
  Update. Front matter that isn't valid YAML isn't opened: the app would write over what it
  couldn't read.
- Update rewrites only title, categories, tags and body; every other key, comment and quote
  stays as written. A post that spells it `category:` keeps that key.
- If the post changed on GitHub since it was opened, even in the moment of committing, Update
  refuses and says to discard and start from the new version. An edit that had already landed
  before a crash is recognised as done.

## Not yet

See the [backlog](BACKLOG.md): Sign in with GitHub, drafts to `_drafts`, settings.
