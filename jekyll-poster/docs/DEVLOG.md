# Devlog

What each cycle shipped, what its review caught, and what got in the way. Newest first.
Times are when each cycle landed, Pacific.

**Latest debug build:** [jekyll-poster-debug.apk](https://github.com/madCode/claude-playground/releases/download/jekyll-poster-debug/jekyll-poster-debug.apk)

## Status

- **Last night:** night 1 built the app from nothing: sign-in two ways, the blog's own
  categories, writing with a toolbar, photos without their location, publishing exactly once,
  following the Pages build, safe edits, front matter, `_drafts`, and a zine look picked over
  four design rounds. Every PR had a fresh-eyes review; 65 findings were fixed.
- **Not yet tried on a real blog:** everything ran against a fake GitHub. The sample blog
  ([madCode/sample-blog](https://github.com/madCode/sample-blog), live on Pages) is ready for
  the first real post.
- **Waiting on you:** a token to try it; the GitHub App for Sign in with GitHub
  ([how](GITHUB_APP.md)); the Android SDK in the environment's setup script.

## Night 1 · Sun 4 Oct

### Cycle 11: New post from the app icon
- **Shipped:** long-press the icon for New post, a shortcut added when the app starts (a
  shortcuts.xml can't name the debug build's package).
- **Found on the way:** since cycle 6, Back from a post started by a share made another post
  from the same share and opened it again, so the editor couldn't be left. Home's effect ran each
  time Home came back; the share is now taken once. A flow test covers it.

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
