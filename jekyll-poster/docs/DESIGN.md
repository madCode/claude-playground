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

- **Search** (the top bar) matches the blog's posts by title, category or tag, as you type,
  within the chosen category. Posts on the phone stay listed, so a failed one is never hidden.
  Back or ✕ closes it.

## Writing

- A post has a title, categories, tags and a Markdown body. It saves as you type.
- Categories and tags come from the blog's own, most used first with their counts, or are typed
  in as new ones.
- The picker shows the post's own at the top, so one just added is seen landing there. The
  keyboard's Done adds what's typed and stays open for more; closing the sheet (Done, a swipe,
  Back) adds it too. A leading # is dropped, and the blog's spelling wins.
- A **toolbar** above the keyboard, while the body has the focus: bold, italic, link (a
  selected URL becomes the address), heading, list, quote, code. Bold, italic, code and the line
  styles undo themselves when pressed again.
- **Preview** shows the post as a page: Markdown with tables and strikethrough, `relative_url`
  and `site.baseurl` resolved, site images from the live site, new photos from the phone. Other
  Liquid shows as written. No scripts run. Images are fetched by the app, not the web view, so
  sites see no phone model or Android version.
- Sharing text, a link or photos to the app starts a post with them, as shared. With **Remove
  tracking codes** on (Blog & privacy), links lose `utm_…`, `fbclid`, YouTube's `si` and the
  like before the editor opens, so the writer sees them as they'll be published.
- **Links to posts:** typing `[[`, or the toolbar's **Link to a post** (which types it, with any
  selected text as the search), lists the blog's posts above the keyboard (three rows tall, scrolling for more),
  narrowing as you type, as Obsidian lists notes; picking one writes `[[Its title]]`, or
  `[[2025-01-12-welcome|Its title]]` when another post shares the title or it holds `#` or `|`.
  Titles with brackets can't be linked and aren't offered. Publish or Update turns each
  `[[Post title]]` into a `post_url` link against the posts as they are then, by the same rules
  as a shared note's, so every retry sends the same text; one that matches no post (a post
  queued at the same moment, say) stays as written. An edit's hand-written `[[Exact title]]`
  becomes a link too. The preview shows them as links.
- A post that others link to can't be deleted without failing the site's next build: Jekyll
  can't find the `post_url`. The app doesn't check for links to it yet.
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
- Named for the post when it's published, beside the blog's images:
  `/assets/images/2026/a-walk-to-the-lighthouse.jpg`, then `-2`. Never a time. Linked with
  `relative_url`, so project sites work.
- Uploaded in the post's commit if the text still links to them; never over a file already on
  the blog (a taken name is renamed, in the text too). Deleted from the phone once uploaded, or
  with their draft.

## From Obsidian

- Share a note from Obsidian, as text or as its `.md` file, and it becomes a post, the way obyde
  turns notes into Jekyll posts. Any shared text goes the same way; plain text comes out as it went in.
- Or open it with the app (Obsidian's first share tray, or a file manager): that imports it too.
  The note stays as it is, and opening it again starts another post.
- The title is the note's `title:`, else its file name, else a first `# heading`. A first
  heading that repeats the title is dropped.
- `tags:` and `categories:` fill the post's own, in the blog's spelling. Written as one line with
  commas (`tags: personal-philosophy, jumping`), they're split on the commas, as Obsidian does;
  Jekyll alone would split on spaces and keep the commas. A YAML list, or the singular
  `category:`, keeps a name with a comma whole. `layout:` goes, and so
  do Obsidian's `aliases:` and `cssclasses:`.
- A note's `date:` is the post's date, in its file name and front matter. It's kept as written
  and read when publishing, in the site's time zone as it is then: a bare day is midnight there,
  as Jekyll reads it. The editor shows it, and **Use the publish day** drops it (until a commit
  was tried: then the post keeps its name and date, so it can't go out twice). A date the app
  can't read is shown as such, and the post is dated when published.
- A future date is kept too: Jekyll won't show the post until then, though the app says live.
- Saved to the blog's `_drafts` first, a note loses its date: drafts are undated, and Publish to
  the site dates them then.
  Other keys are the post's "more front matter", to see before publishing.
- `[[Post title]]` and `[[Post title|shown text]]` become `{{ site.baseurl }}{% post_url … %}`
  links when a post the site builds has that title or file name (`[[2025-04-20-reading-list]]`):
  published, not a draft, not dated in the future. A link to a note that isn't a post yet stays
  as written, `[[Like this]]`: it reads as a title. Code is left alone.
- A site its own workflow builds with Jekyll 4 (its Gemfile says so, and a workflow runs
  `jekyll build`) gets a plain `post_url`: Jekyll 4 adds the baseurl itself.
- The blog's post list is refreshed first: a `post_url` to a post that's gone fails the site's
  build. So does deleting or renaming a post that others link to, later: Pages then keeps the
  last good build and the app says the build failed.
- obyde's `find:` and `replace:` lists (Python regular expressions, in pairs) are taken out of
  the note first, then applied to everything else: title, text, tags, other front matter, and
  the file name the title may come from. So the words they hide don't reach the phone's draft
  or the blog, as long as a rule matches them as written: a name broken across two lines isn't
  matched. A note whose rules can't be applied (unpaired, written twice, unreadable, a group
  that isn't there) isn't added, and the app says why without repeating the rule.
- `![[photo.jpg]]` is found in the vault folder, chosen once (in the editor when a note first
  needs it, or in Settings), and added like any photo, location stripped. Choosing another
  folder gives the old one's access back. Its `|alt text` is the
  alt text; a size (`|300`) isn't. One not in the vault stays as written, and the app says so, naming it as the text writes it.
  Embedded notes and PDFs stay as written.
- The preview shows a `post_url` link as a link that goes nowhere: only Jekyll knows where.

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
- A new post becomes `_posts/<date>-<slug>.md`, dated in the site's `timezone` when
  `_config.yml` sets one (`2026-10-04 22:15:00 -0700`): the same day Jekyll will give it, and no
  hint of where the writer is. Without one, the phone's offset, so the site builds it on the day
  the writer meant; or, dating by the day only, the day in UTC (see Blog & privacy). A shared
  note's own date is used instead of now (see From Obsidian).
  `layout: post` is added only when `_config.yml` doesn't default posts to a layout.
- The name avoids files already on the branch (`-2`) and existing posts' addresses: under
  `/:title/`, two posts of the same title would overwrite each other.
- One commit per publish ("Add post: Title"), with its photos. It's checked against the branch
  where it lands, so it never replaces a file that appeared meanwhile.
- Never twice: every text sent for a post is remembered, so a commit that landed unheard is
  recognised on the next attempt. A failed post that never got as far as a commit gets a fresh
  name and date when sent again; a note's date stays.
- The post's address, as Jekyll builds it: `permalink` from `_config.yml`; the date in the site's
  time zone (UTC on GitHub when unset, so an evening post can carry tomorrow's date in its URL);
  categories lowercased and escaped, front matter's first, then folders'. The site is the
  `CNAME` domain, else `url` (plus `/repo` for a github.io project site whose workflow sets
  `baseurl`), else `owner.github.io[/repo]`.
- Then the app follows the Pages deployment for that commit (an Actions run with Pages in its
  name or file; CI doesn't count) for about eleven minutes: "rebuilding", then "live" or
  "build failed", with a notification that opens the post. A locked phone shows only "A post is
  live", not the title. Without Actions access, it just says published.

## Editing a post on the blog

- Tapping a post opens the blog's copy as it is on GitHub now. Closed without a change, it's
  dropped: it isn't listed under On this phone as well as on the blog. Front matter that isn't valid
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
  undated, to finish on a laptop. Jekyll doesn't publish drafts, but a public repository is
  readable by anyone on GitHub, and the menu says so.
- Opening a Jekyll draft: **Update draft** keeps it in `_drafts`; **Publish to the site** moves
  it to `_posts/<date>-<slug>.md` with a date, in one commit.

## Blog & privacy

Every default is what GitHub and Jekyll do on their own; each switch is one step more private.

- **Only connect through a VPN**, off. On, everything the app sends to GitHub and to the blog's
  site (sign-in, reading, publishing, the build watch, the preview's photos) goes through the
  phone's VPN, so they see its address, not the writer's.
  - Each connection is bound to the VPN, and names are looked up through it: if the VPN drops
    mid-request, the request fails rather than going out over Wi-Fi. No SOCKS proxy is used
    (OkHttp would open it around the VPN); an HTTP proxy is reached through the VPN.
  - With no VPN, nothing is sent and screens say so. A queued post shows **Waiting for your
    VPN…**; it goes out as soon as the VPN is back while the app is running, or else at
    WorkManager's next retry. The "is it live" check keeps waiting too, rather than giving up.
  - Turning it on (or off) moves requests to new connections; ones made before are never used
    again. Requests already under way finish as they started.
  - Any VPN counts: Android doesn't say which app it is. It must be meant for all traffic, and
    is used only for the addresses it carries. One that leaves this app out (split tunneling),
    or covers only its own range (Tailscale without an exit node), counts as none.
  - The preview drops what the WebView would fetch by itself (`<link>`, audio, video), so all
    it shows comes through the app.
  - Links opened in the browser, and the sign-in page, go the browser's own way.
- **Commit with your no-reply email**, off: commits then name no author, and GitHub uses the
  account's own email setting. On, they're signed `<login> <<id>+<login>@users.noreply.github.com>`:
  the login, never the profile's name, which may be a real one.
- **Date posts by the day only**, off: new posts get the minute and an offset. On, they get
  `date: 2026-10-04`, so the post's date says neither the time it went out nor the writer's
  time zone. (The commit that adds it still carries the time on GitHub.)
  - The day is the site's (UTC without a site zone), where Jekyll builds, for a note's own date
    too: a post is never in its future, hidden until that day.
  - Fixed on the first try, like the file name: a retry writes the same date. A site zone set
    between two tries of the same post can still put that day ahead; rare, and the next push
    shows the post.
  - Same-day posts all sit at midnight, so the site lists them by file name, not by the order
    they went out.
- **The site's time zone**, from `_config.yml`, with **Use UTC** first ("dates won't say where
  you are") and **Use** the phone's zone second: an IANA name like `Europe/Lisbon` in a public
  `_config.yml` says more than a date's offset does.
  - One commit changing only the `timezone:` line, titled "Set the site's time zone" without
    naming it, after a warning that posts near midnight (and dated addresses) can move a day.
  - Refused if `_config.yml` changed meanwhile.
- **Remove tracking codes** from shared links, off.
- Whether the repository is public (so `_drafts` and earlier versions are readable), and that
  edits and deletes stay in its history, each linking to GitHub.

## Settings
- The blog (repository and branch) and the site's address.
- **Blog & privacy**, above.
- **Switch blog** lists the repositories the current sign-in can write to.
- **Sign out** keeps drafts on the phone for when that blog is signed in again.
- **Obsidian:** the vault folder photos in shared notes come from; choose another or forget it.
- The app's version, with the build's CI run and commit in a debug build. A tap or long press
  copies it, for a bug report.

## Not yet

See the [backlog](BACKLOG.md).
