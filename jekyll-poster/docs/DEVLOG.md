# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.
Times are when each cycle landed, Pacific.

**Latest debug build:** [jekyll-poster-debug.apk](https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk)

## Status

- **Last night:** night 1 built the app from nothing: sign-in two ways, the blog's own
  categories, writing with a toolbar, photos without their location (picked, shared or taken),
  publishing exactly once, following the Pages build, safe edits and deletes, front matter,
  `_drafts`, a New post shortcut, and a zine look picked over four design rounds. Every code PR
  had a fresh-eyes review; 90 findings, all but one fixed.
- **Tried on a real blog:** a post from the phone landed on the sample blog
  ([madCode/sample-blog](https://github.com/madCode/sample-blog), live on Pages). A live check
  workflow publishes and deletes one there through the app's own GitHub client.
- **Waiting on you:** a `SAMPLE_BLOG_TOKEN` Actions secret for the live check; the GitHub App for Sign in with GitHub
  ([how](GITHUB_APP.md)); the Android SDK in the environment's setup script.

## Day 1 · Sun 4 Oct

### Cycle 25: simpler code, 3 of 3 (VPN in core, cancellation, docs)
- **Changed:** the VPN gate, its renewable clients and the route maths moved to core, with
  their tests and `FakeVpn`; view models catch with `catching`, which lets a cancellation
  through (`runCatching` and `catch (e: Exception)` caught it, so a closed screen's coroutine
  could finish as if it hadn't been cancelled); tests sign in with `TestApp.signIn()`, fourteen copies before; ARCHITECTURE.md current.

### Cycle 24: simpler code, 2 of 3 (one rule each, smaller files)
- **Changed, barely behaviour:** one definition each for when a post can be changed
  (`Draft.editable`), the writer's text the editor owns (`Draft.withTextOf`, three copies
  before), which posts a `[[link]]` can reach (two before) and a Jekyll draft's path; the editor
  screen split from 733 lines into itself and four files; `BuildWatcher` in a file of its own;
  the share the home screen opens is read-only outside it.
- **Two labels now agree with the publisher**, which reads `_drafts` as a path segment: a
  Jekyll draft saved in a subfolder (`blog/_drafts/`) says "Saved to the blog's _drafts", and a
  post in a folder like `my_drafts/` is no longer labelled a Jekyll draft.

### Cycle 23: simpler code, 1 of 3 (wiring, core, imports)
- **Changed, not behaviour:** AppContainer takes one PublishQueue, builds the database through
  `PosterDatabase.create`, starts the VPN-back retry from `PosterApp` (a launch from its
  constructor could run before its properties were set, as one in the editor once did) and signs
  out in one call; date parsing, Git's blob id, the GIF cleaner and a bounded read moved to core;
  fully qualified names became imports.

### Cycle 22: privacy for anonymous writers, 3 of 3 (one setup, and around the app)
- **Shipped:** **Set up for writing anonymously**, one confirm for every private switch, and
  **Beyond the app**, what only the writer can do; nothing written goes to the cloud backup;
  more sites' share codes go; sign-in says to use a private tab.
- **Its review found** (fixed, with tests): a failed address lookup still turned the VPN switch
  on, after which every retry failed the same way (it now waits for the address, and says when
  the VPN is the reason); a lagging screen state could skip that lookup; the VPN step could be
  cut short by leaving the screen; Instagram's `img_index` is the photo, not a share code;
  `amazon.example.org` counted as Amazon; the private-tab hint came after the buttons that
  open GitHub, with no link to copy.

### Cycle 21: privacy for anonymous writers, 2 of 3 (what goes out, and when)
- **Shipped:** **Send at a random time** (a moment in the next three hours, kept with the post,
  with **Send now**); **Plain commit messages**; Obsidian comments and the note keys that say
  when and where it was written stay out of the post.
- **Its review found** (fixed, with tests): code inside a comment let the comment's words out
  and ate the text after it (comments now go first, over the whole note); Send now did nothing
  once the VPN-back restart had queued the post; a double tap on Publish picked a second time
  that was never scheduled; Send now could overwrite a publish landing that moment; `date
  created:` and `Created:` stayed; the "Going out at" label and Send now stayed after the time.

### Cycle 20: privacy for anonymous writers, 1 of 3 (who and when)
- **Asked:** make it easy to blog anonymously. An audit listed what still ties a post to its
  writer; this cycle takes who signs the commits and what the dates say.
- **Shipped:** no-reply commits signed with the login, never the profile's name; **Date posts
  by the day only**; **Use UTC** offered before the phone's zone, and the zone commit no longer
  names it; notifications show no title on a locked phone.

### Cycle 19: only through a VPN
- **Asked:** make sure the VPN is on before anything goes to GitHub, for privacy.
- **Shipped:** **Only connect through a VPN** in Blog & privacy. Connections are bound to the
  VPN as they're made, not checked first, so a dropped VPN fails a request instead of leaking
  it; with none, nothing is sent and queued posts say they're waiting for it.
- **Its review found** (fixed, with tests): a connection busy when the switch went on was used
  again afterwards, without the VPN (clients are now remade, and OkHttp never shares
  connections between them); a VPN routing only some addresses passed as one; a SOCKS proxy
  went around it; the preview's `<link rel=preconnect>` and media fetched past the app; queued
  posts could wait hours after the VPN came back; the build watch gave up while waiting for it.
- **The second look found** (fixed): before Android 13, the route Android adds to block IPv6
  made a partial VPN look like a full one; a VPN coming up read as none for a moment; the
  restart when the VPN is back could stop a publish mid-commit (it now runs beside it).
- **Not possible:** controlling Mullvad from the app. Its connect actions aren't open to other
  apps, and Android only lets the VPN app or the writer start a VPN.

### Links to posts with `[[`, and a crash opening a shared note
- **Shipped:** typing `[[` (or the toolbar's Link to a post) lists the blog's posts; picks are
  linked at Publish.
- **CI found:** the editor started loading before its own properties were set; with a shared
  note's photos it could crash. The load now starts last.

### Fix: the preview crashed on the phone
- **Found by the writer:** the eye button crashed the app on every post. Android compiles
  regular expressions with ICU, which refuses a `}` that doesn't close a repeat; the JVM the
  tests run on takes it. The preview's Liquid patterns had one since the preview was built, and
  so did the pattern that sets a photo's alt text.
- **Fixed:** every brace escaped, checked against ICU itself; `AndroidRegexTest` reads the
  app's patterns for a bare `}`, so a test fails here before the phone does. A test now taps
  the eye button too.
- **Also:** a tap or long press on the version in Settings copies it, for bug reports.

### Cycle 18: posts from Obsidian
- **Shipped:** share a note from Obsidian (text or its `.md` file) and it becomes a post, as
  obyde does it: front matter to the editor's fields, `[[links]]` to the blog's posts as
  `post_url`, links to unposted notes left as `[[written]]`, obyde's `find:`/`replace:` rules
  applied and then removed, and `![[photos]]` found in a vault folder chosen once.
- **Not obyde's code:** a Python command-line tool can't run on Android, and its core is a few
  hundred lines; about half of it the app already did. Unlike obyde, the rules never publish.
- **Its review found** (fixed, with tests): `post_url` leaves out a project site's baseurl on
  Pages' Jekyll 3; links to future-dated or since-deleted posts, or to posts under a category
  folder by the wrong name, would fail the site's build (refreshed first, those skipped, named
  `travel/…`; slug-only matches dropped); a replace group that isn't there crashed the app;
  Python group names with `_` were refused; a share was lost on rotation mid-way; photos waiting
  for the vault folder were lost if the picker pushed the app out of memory; a photo could land
  in a code example; choosing another folder kept the old one readable; conversion ran on the
  main thread; errors repeated the private word. Also: a second `find:` or a front matter
  without its closing line could have let words through; both now stop the note.
- **The second look found** (fixed): a photo whose name a rule changed wasn't found when the
  vault folder was chosen afterwards; Jekyll 4 builds would get the baseurl twice (detected
  now); the refresh's timeout couldn't stop a slow request, and Switch blog dropped the share;
  `.avif` and `.heic` could be missed; an escaped `\\` read as a group.
- **Left:** a phrase broken across lines isn't matched (as in obyde; DESIGN says so); deleting a
  post others link to fails the next build (Pages keeps the last good one); indented code blocks
  aren't told apart from nested lists.
- **Tests:** a test that called the share code inside `runBlocking` hung on Room, as in cycles 1
  and 11; it waits on the main looper instead.

### Cycle 17: Blog & privacy
- **Asked:** one page for the audit's settings, with defaults for the average blogger and easy
  switches for the privacy-minded; and don't change what was written unless asked.
- **Shipped:** the page (commit email, the site's time zone, tracking codes, visibility and
  history). Removing tracking codes is now opt-in: the post is never changed unless asked.
- **Its review found** (fixed, with tests): a time zone that landed was reported as failed if
  reading the blog again failed, inviting a second commit; publishing asked GitHub for the account
  on every post, and a failure there blamed the token's write access (the address is now looked
  up once, when the switch is turned on, and kept); an account without an id would have got an
  address that links to no one; visibility said "couldn't read it" while still loading; the zone
  change could race a publish (it waits for one now); Windows line endings and a quoted
  `"timezone":` key were mishandled; rows didn't say they're buttons.
- **The second look found** (fixed): an "on" still looking up the address could land after an
  "off", and a publish's lookup could turn the switch back on; an expired sign-in during the
  lookup said "no address" instead of "sign in again"; a storage hiccup failed the post instead
  of retrying.
- **The live check** now also commits as the no-reply address and asks GitHub whose commit it
  is, and whether it's Verified: the two things the fake GitHub can't answer.

### Cycle 16: a privacy audit
- **Asked:** what goes to GitHub besides what's in the post?
- **Found and fixed:** `_drafts` read as private but a public repository shows it to anyone (the
  menu says so now); dates carried the phone's offset, so a trip abroad showed (the site's time
  zone now, when it names one); photo names were the minute they were added (named for the post
  now); shared links kept tracking codes (stripped, unless a new setting keeps them); the
  preview's web view sent the phone's model and Android version with every image (the app
  fetches them now).
- **Found and left, for the writer:** commits carry the GitHub account's name and email (the
  account's private-email setting decides which); edits and deletes stay in the repository's
  history.
- **Checked:** photos lose EXIF; camera originals never leave the phone; no analytics; the token
  only goes to api.github.com.
- **Its review found** (fixed): Markdown right after a link (`**`, a backtick) was eaten with
  the last tracking code; `&amp;`-escaped links lost parameters; the preview read any linked
  file whole (a video could run the phone out of memory) and kept no cache; an edit's photo year
  used UTC. Left: a four-step, two-device way for a restored photo link to show another post's
  photo of the same name (nothing is ever overwritten).

### Cycle 15: the tag picker, from first use
- **Reported:** a tag added with the keyboard's checkmark vanished from the sheet (it was on the
  post, out of sight), and the sheet's Done dropped what was typed.
- **Shipped:** the sheet shows the post's own tags at the top; Done adds what's typed; typing one
  the post has says so instead of offering to add it again.
- **Its review found** (fixed): a typed "#Rain" beside the blog's "rain" made a second tag;
  swiping the sheet away still dropped what was typed (it now adds it, however the sheet closes:
  the sheet keeps the first dismiss callback it's given, so the callback reads the field when
  called); many tags could push the field off a short screen.

## Night 1 · Sun 4 Oct

### Cycle 14: search your posts
- **Shipped:** a search in Home's top bar: the blog's posts by title, category or tag, as you
  type, within the chosen category.
- **Its review found** (fixed): searching hid a failed post on the phone, with its error; the
  keyboard came back over the results after a rotation or a post; "No posts match" ignored the
  chosen category; the field had no name for TalkBack once typed in. (The keyboard's return is
  untested: Robolectric has no keyboard to see.)
- **The second look found** (fixed): the field's TalkBack name could hide what was typed;
  it's a label now.

### Cycle 13: a documentation pass
- Architecture, README and this status brought up to delete, the shortcut and the camera.

### Cycle 12: take a photo
- **Shipped:** the photo button offers Choose photos or Take a photo. The camera app writes to
  the cache through a FileProvider; the photo is prepared like any other and the original deleted.
- **Its review found** (fixed, with tests): the camera app can push this app out of memory, and
  the photo was then lost without a word while the original, location included, stayed in the
  cache; an editor closed mid-import left the original too; a photo arriving for a post being
  published vanished silently; a double tap orphaned a file; the cancel test couldn't fail. The
  path now lives in the screen's saved state, every way out deletes the original, and stale
  originals are swept on start (untested: it runs before a test can set one up).
- **The second look found** (fixed): after the app was pushed out of memory, the photo reached the
  new editor before its draft had loaded and was refused; it now waits for the draft.
- **Tests:** FileProvider caches its paths statically, against the first Robolectric test's data
  directory; the camera tests clear it.

### Cycle 11: New post from the app icon
- **Shipped:** long-press the icon for New post. The shortcut is added by the app (a
  shortcuts.xml can't name the debug build's package), once.
- **Found on the way:** since cycle 6, Back from a post started by a share made another post
  from the same share and opened it again, so the editor couldn't be left. Home's effect ran each
  time Home came back; the share is now taken once.
- **Its review found** (fixed, with tests): reopening from Recents replayed the shortcut or
  share; the shortcut restarting the app left an empty "Untitled" draft and could drop the last
  keystrokes; a rotation on the sign-in screen lost a share; the shortcut was pushed on every
  process start, background work included.
- **The second look found** (fixed): deleting those empty drafts at once could delete one still
  open in another window (a share opens a second one), or the row an editor closing under the
  shortcut was about to save its last keystrokes into. They're now hidden, and dropped after a day.
- **Tests:** an activity started with Robolectric's `buildActivity` stops composing after the
  first test in a JVM, with every thread idle: Compose's main dispatcher seems bound to the first
  test's looper. Likely the cause of the Compose-test hang (cycle 1), still open. Activity-level
  checks are now plain functions, and screens are tested through the Compose rule.

### Cycle 10: delete a post from the blog
- **Shipped:** Delete from the blog, in the editor's menu: asks first, one commit, only the
  version that was opened. Photos stay; the history keeps the text.
- **Its review found** (all fixed, with tests): an autosave mid-write could undo the queued
  delete; a post moved elsewhere (a draft published from the laptop) was taken as deleted; an
  earlier update's build watch could call the deleted post live; the refused-delete message sent
  the writer back to the same stale copy.
- **The second look found** (fixed): the cleanup matched the wrong column; sending a delete
  again dropped the marker that recognises a commit that landed unheard.

### Cycle 9: a documentation pass (landed 03:20)
- Design rewritten as the app is now (four design rounds, photos and front matter in their own
  sections, the never-twice rules in one place); backlog cut to what's actually next; this
  status block added.

### Cycle 8: front matter, and filtering by category (landed 03:19)
- **Shipped:** "more front matter" for posts, as YAML in a folding section, checked before
  publishing, untouched keys kept byte for byte (database version 3); the blog's posts filter by
  category from a row of pills.
- **Its review found** (all fixed, with tests): indented YAML passed the check but was read as
  nothing, so every other key on the post was deleted; `{…}` and `?` keys slipped past the
  check; comments in the field were dropped when written; a `---` line cut off what followed;
  an edit draft from before the upgrade could replace keys it never showed; a vanished category
  left the filter stuck on an empty list; pills were under 48dp to tap, and a category called
  "All" was taken for the All pill; a blocked Publish said nothing; front matter alone didn't
  keep a draft.
- **The second look found** (fixed): keys YAML reads as numbers or null (`2024:`) crashed the
  check as you typed; `... # comment` slipped past it; a `<<` merge key could override the title
  in Ruby's YAML; a post's own odd-but-working front matter blocked any edit (only changed text
  is checked now); keys like `c#:` or `'it''s':` were swallowed by the key above.
- **Cycle 7's review found** (fixed before merging): a category hashing to `Int.MIN_VALUE`
  crashed the colour lookup; switching blogs kept the old blog's title; TalkBack read the
  decorative ✶; small text used the font's 96pt optical size; disabled toolbar buttons were
  too faint; Publish could wrap at large text; the icon's shadow left the round mask's safe
  circle; the squiggle allocated on every draw.

### Cycle 7: a whimsical look, in four design rounds (landed 03:00)
- **From you:** "a few design rounds to pick a theme that's fun and maybe even whimsical".
- **Round 1:** five directions rendered on the real screens, light and dark (DesignRoundTest):
  classic, zine, garden, night sky, sticker. All too timid: a tint and a heading font each.
  Garden's and Sticker's variable fonts came out at their thinnest weight.
- **Round 2:** whimsy moved into the shapes: squiggles, dotted rules, colour per category, card
  rows, a tilted stamp of a button; garden dropped. An independent critique ranked zine first
  ("most personality for the least cost: the flavour is in the headers, rules and button, so the
  list and the writing area stay calm"), then sticker, then night.
- **Round 3:** zine refined from the critique: pill contrast (dark mode's were about 3:1), a
  themed category picker with coloured pills, deeper pink text, more line height in the editor,
  an inked outline on secondary buttons.
- **Round 4:** Publish as a fluoro pill, the riso app icon (teal under pink, out of register),
  zine as the app's look; the other styles' code and fonts taken out (they're in history).
  The rounds' contact sheets are on the `claude/screenshots` branch under `design/`.

### Cycle 6: photos shared from the gallery, alt text (landed 02:31)
- **Shipped:** share one photo or several from the gallery to start a post with them, in
  order; every added photo asks for its alt text (skippable).
- **Cycle 5's second look found** (all fixed): a cancelled or failed Switch blog could save the
  sign-in without its refresh token, and Back from it landed on a sign-in screen; the editor
  rebuilt the body without the keyboard's word in progress, which breaks predictive keyboards;
  the toolbar formatted the body while the title had the focus; a photo replaced selected text;
  a selection ending at a line break prefixed the next line too; switching blogs could write
  back a token renewed meanwhile. Found while testing: the editor reached its cursor before it
  was set up when a draft loaded fast.

### Cycle 5: a Markdown toolbar, photos at the cursor, settings (landed 02:20)
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

### Cycle 4: the blog's _drafts (landed 02:06)
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

### Cycle 3: Sign in with GitHub (landed 02:03)
- **From you:** let people sign in either way, token first.
- **Shipped:** GitHub's device flow for a GitHub App: a code to copy, github.com opened, the
  app waits; expiring tokens renewed before they expire, refresh tokens sealed like the token.
  The button appears once a build names the app: [setup](GITHUB_APP.md), yours to do since only
  you can register an app. CI picks it up from repository variables.

### Cycle 2: preview, photos, live (landed 01:59)
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

### Cycle 1: post from the phone (landed 01:48)
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
