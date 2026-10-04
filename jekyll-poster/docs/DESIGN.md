# Design

Who it's for: someone with a Jekyll blog on GitHub Pages who can code, wants to blog more, and
doesn't want to open a laptop and make a commit for every post.

## Signing in

- **Sign in with GitHub**, when the build names a GitHub App ([setup](GITHUB_APP.md)): the app
  shows a code, copies it and opens github.com, and waits while the writer enters it. Then the
  blogs it lists are the repositories the app is installed on; with none, it links to installing
  it. Tokens that expire are renewed five minutes before they do.
- Or a fine-grained personal access token, pasted once. The app's link opens GitHub's new-token
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
- A **toolbar** above the keyboard: bold, italic, link (a selected URL becomes the address),
  heading, list, quote and code. Pressing a button again undoes it.
- **Preview** shows the post as a page: Markdown (with tables and strikethrough), the
  `relative_url` and `site.baseurl` links resolved, site images loaded from the live site, and
  photos not yet uploaded shown from the phone. Other Liquid shows as written. No scripts run.
- **Photos** come from the photo picker and go in at the cursor, on a paragraph of their own. Each is turned upright, scaled to at most 2000 px and
  re-encoded, which drops its EXIF: no location, camera or time goes to a public blog. PNGs stay
  PNG, GIFs are copied as they are. It's named by when it was added
  (`/assets/images/2026/20261004-221500.jpg`, beside the blog's own images) and linked with
  `relative_url`, so it works on a project site.
- Photos are prepared in the app's storage and deleted once uploaded, or with their draft. GIFs
  keep their frames and loop but lose comment and application blocks (where XMP, and a location,
  can hide); GIFs over 10 MB are refused. Mirrored orientations are undone too.
- A draft belongs to the blog it was written for. Signed in to another blog, it waits, hidden.

## Settings

- The blog (repository and branch) and the site's address; **Switch blog** lists the
  repositories the current sign-in can write to; **Sign out** keeps drafts on the phone for
  when that blog is signed in again.

## The blog's _drafts

- **Save to the blog's _drafts** (the editor's menu) commits a new post to `_drafts/<slug>.md`,
  undated. Jekyll doesn't publish drafts, so it's there to finish on a laptop.
- Opening a Jekyll draft from the list: **Update draft** keeps it in `_drafts`; **Publish to the
  site** moves it to `_posts/<date>-<slug>.md` with a date, in one commit.

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
  meanwhile, it picks another. A name is also passed over when the post's address would be an
  existing post's (under `/:title/`, two posts of the same title would overwrite each other).
- A photo whose name was taken on the blog meanwhile is renamed, in the text too; photos never
  replace files already on the blog.
- If a commit landed but the app didn't hear back, the next attempt recognises the file as this
  post's own (by the sha it last sent) and updates it, rather than publishing a second copy.
- A failed post that never got as far as a commit gets a fresh name and date when sent again.
- The app works out the post's address as Jekyll would: `permalink` from `_config.yml`, the
  date in the site's time zone (UTC on GitHub when none is set, so an evening post west of
  Greenwich can carry tomorrow's date in its URL; a front matter date without an offset is read in
  the site's zone), lowercased and escaped categories, front matter's first, then folders'. The site's address is
  the `CNAME` domain, else `url` (plus `/repo` for a github.io project site that leaves `baseurl`
  to its workflow), else `owner.github.io[/repo]`.
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

See the [backlog](BACKLOG.md).
