# Design

Who it's for: someone with a Jekyll blog on GitHub Pages who can code, wants to blog more, and
doesn't want to open a laptop and make a commit for every post.

## The look

- A risograph zine, chosen over four design rounds: cream paper, fluoro pink and teal inks,
  Bricolage Grotesque, a ✶ and a squiggle under section headings, dotted rules between posts,
  and New post as a tilted stamp printed a little out of register.
- Each category and tag has its own colour, the same everywhere: rows, chips, the picker.
- Pills keep dark ink on light colours and light ink on dark, 4.5:1 or better. Pink text is a
  deeper pink than the stamp's fluoro, for contrast on the cream.
- The header shows the blog's own title from `_config.yml`.
- Dark mode is the same zine printed on brown-black paper.

## Signing in

- **Sign in with GitHub**, when the build names a GitHub App ([setup](GITHUB_APP.md)): the app
  shows a code, copies it, opens github.com and waits. The blogs it then lists are the
  repositories the app is installed on; with none, it links to installing it.
- Or a **fine-grained token**, pasted once. The app's link opens GitHub's form with Contents
  (read and write) and Actions (read) filled in; the writer picks the repository there.
- Then the app lists the repositories the sign-in can write to, Pages sites first.
- Tokens are sealed with an Android Keystore key and never backed up. Expiring tokens are
  renewed five minutes before they expire.

## Reading the blog

- Posts are files Jekyll would render: in any `_posts` folder (nested ones too, like
  `travel/_posts/` or `_posts/2025/`) named `YYYY-MM-DD-title.md`, and drafts in `_drafts`.
- Categories as Jekyll reads them: `category:` wins and is taken whole (`category: Web
  Development` is one); otherwise `categories:`, a YAML list or a space-separated string.
  Folders above `_posts` add theirs. Tags likewise.
- Duplicate keys read as Ruby reads them: the last one wins.
- Spellings that differ only in case are one category, shown as most posts spell it.
- Each post is fetched once; later reads fetch only posts whose content changed.
- A repository too big for GitHub to list in one go is refused rather than read in part.

## The list

- Posts on the phone first (drafts, waiting, failed, just published), then the blog's own.
- With more than one category, a row of pills filters the blog's posts. Tapping the chosen one,
  or All, shows them all again; so does the chosen category disappearing.

## Writing

- A post has a title, categories, tags and a Markdown body. It saves as you type.
- Categories and tags come from the blog's own, most used first with their counts, or are typed
  in as new ones.
- A **toolbar** above the keyboard, while the body has the focus: bold, italic, link (a
  selected URL becomes the address), heading, list, quote, code. Bold, italic, code and the line
  styles undo themselves when pressed again.
- **Preview** shows the post as a page: Markdown with tables and strikethrough, `relative_url`
  and `site.baseurl` resolved, site images from the live site, new photos from the phone. Other
  Liquid shows as written. No scripts run.
- Sharing text, a link or photos to the app starts a post with them.
- Long-pressing the app's icon offers **New post**, straight into the editor.
- A share starts one post: going back from it returns to the list.
- A new post with nothing in it is dropped when you leave it. One left when the app closed under
  it isn't listed, and is dropped a day later (it may still be open in another window).
- A draft belongs to the blog it was written for. Signed in to another blog, it waits, hidden.

## Photos

- From the photo picker, taken with the camera, or shared from the gallery (in the order shared). Each goes in at the
  cursor, after any selection, on a paragraph of its own, and asks for alt text (skippable).
- Each is turned upright (mirrored ones too), scaled to at most 2000 px and re-encoded, which
  drops its EXIF: no location, camera or time goes to a public blog. PNGs stay PNG.
- A camera photo is written to the app's cache, never backed up, and deleted once prepared: its
  original, location included, doesn't stay on the phone either.
- GIFs keep their frames and loop but lose comment and application blocks, where XMP (and a
  location) can hide. GIFs over 10 MB are refused.
- Named by when they were added, beside the blog's images:
  `/assets/images/2026/20261004-221500.jpg`. Linked with `relative_url`, so project sites work.
- Uploaded in the post's commit if the text still links to them; never over a file already on
  the blog (a taken name is renamed, in the text too). Deleted from the phone once uploaded, or
  with their draft.

## Front matter

- Keys beyond the editor's fields (`image:`, `excerpt:`, `comments: false`, …) are YAML in a
  folding section; folded, it names its keys.
- An edit shows the post's other keys as written, comments included. Unchanged, they're written
  back byte for byte and not checked; changed, comments stay.
- Changed text must be one `key: value` per line from the left edge. YAML the blog couldn't
  read, indented, `{…}`, `?` or `<<` keys, a `---` line, or a key the app sets (`title`,
  `date`, `categories`, `tags`, `layout`) stops Publish, opens the section and says why.
- An edit opened before the app kept front matter shows it read-only: it can't know the
  post's other keys.

## Publishing

- Publish queues the post. It goes when there's a connection, one post at a time, retrying if
  GitHub can't be reached or a connection drops mid-way.
- The first Publish asks to send notifications; publishing goes ahead either way.
- A new post becomes `_posts/<date>-<slug>.md`, its date with the phone's offset
  (`2026-10-04 22:15:00 -0700`) so the site builds it on the day the writer meant.
  `layout: post` is added only when `_config.yml` doesn't default posts to a layout.
- The name avoids files already on the branch (`-2`) and existing posts' addresses: under
  `/:title/`, two posts of the same title would overwrite each other.
- One commit per publish ("Add post: Title"), with its photos. It's checked against the branch
  where it lands, so it never replaces a file that appeared meanwhile.
- Never twice: every text sent for a post is remembered, so a commit that landed unheard is
  recognised on the next attempt. A failed post that never got as far as a commit gets a fresh
  name and date when sent again.
- The post's address, as Jekyll builds it: `permalink` from `_config.yml`; the date in the site's
  time zone (UTC on GitHub when unset, so an evening post can carry tomorrow's date in its URL);
  categories lowercased and escaped, front matter's first, then folders'. The site is the
  `CNAME` domain, else `url` (plus `/repo` for a github.io project site whose workflow sets
  `baseurl`), else `owner.github.io[/repo]`.
- Then the app follows the Pages deployment for that commit (an Actions run with Pages in its
  name or file; CI doesn't count) for about eleven minutes: "rebuilding", then "live" or
  "build failed", with a notification that opens the post. Without Actions access, it just says
  published.

## Editing a post on the blog

- Tapping a post opens the blog's copy as it is on GitHub now. Front matter that isn't valid
  YAML isn't opened: the app would write over what it couldn't read.
- Update rewrites title, categories, tags and body, and the other front matter only if it was
  changed. Every other key, comment and quote stays as written; a post that spells it
  `category:` keeps that key.
- If the post changed on GitHub since it was opened, even in the moment of committing, Update
  refuses and says to discard and start from the new version.

## Deleting a post from the blog

- **Delete from the blog** (the menu, while editing a post or Jekyll draft) asks first, then
  removes the file in one commit ("Delete post: Title"). The changes made on the phone go with it.
- Only the version that was opened is deleted: if it changed on GitHub since, nothing is deleted
  and the app says so. Already gone counts as done.
- Its photos stay: another post may use them. The repository's history keeps the text.

## The blog's _drafts

- **Save to the blog's _drafts** (the editor's menu) commits a new post to `_drafts/<slug>.md`,
  undated, to finish on a laptop. Jekyll doesn't publish drafts.
- Opening a Jekyll draft: **Update draft** keeps it in `_drafts`; **Publish to the site** moves
  it to `_posts/<date>-<slug>.md` with a date, in one commit.

## Settings

- The blog (repository and branch) and the site's address.
- **Switch blog** lists the repositories the current sign-in can write to.
- **Sign out** keeps drafts on the phone for when that blog is signed in again.

## Not yet

See the [backlog](BACKLOG.md).
