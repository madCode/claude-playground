# Architecture

## Modules

- **`:core`** (JVM): no Android.
  - `frontmatter/`: `FrontMatterDocument` splits a file into YAML and body, edits single keys
    and keeps the rest byte for byte. SnakeYAML Engine reads values; the app writes its few value
    shapes itself, quoting only what YAML would misread.
  - `jekyll/`: Jekyll's conventions. `PostPath` (what's a post, its date, slug and folder
    categories, whether it's a draft), `Slug`, `SiteConfig` (from `_config.yml`), `ConfigEdit`
    (changes one `_config.yml` line), `PostSummary`, `Taxonomy`, `PostWriter` (new posts and
    edits, the date as a timestamp or a day), `parseJekyllDate`, `Permalink` (a post's address,
    the site's address), `Images` (where a new photo goes and its Markdown), `MarkdownEdits`
    (the editor's toolbar) and `Preview` (commonmark, Liquid links, nothing the WebView would
    fetch past the app).
  - `github/`: `GitHubClient`, a thin client over any OkHttp `Call.Factory`. Commits go through
    the Git Data API (blobs, tree on `base_tree`, commit, fast-forward ref update, rebuilt on a
    conflict), so a post and its images land in one commit. `expect` names files that must be
    unchanged at the head the commit lands on, checked on every rebuild. Post contents are
    fetched in batches through GraphQL by blob sha. `DeviceFlow` is Sign in with GitHub: code,
    polling, refresh. `gitBlobSha` is Git's id for a text, to recognise a commit that landed.
  - `net/`: the VPN switch's machinery, below Android. `Vpn` and `Route` (the network, as the
    app sees it), `VpnGate` (an OkHttp socket factory, DNS and proxy selector that bind to the
    VPN or refuse with `NoVpnException`), `GatedCalls` (a `Call.Factory` remade on each switch
    change, so old connections are never reused) and the route maths (`carries`).
  - `obsidian/`: `ObsidianNote` turns a note into a post's fields: comments and private keys
    out, front matter mapped, find/replace rules applied and removed, `[[links]]` to
    `post_url`, image embeds listed and resolved against the vault's files as Obsidian does. The
    editor's `[[` completion uses it too.
  - `text/`: `Tracking` takes tracking and share codes out of links.
  - `images/` and `io/`: `Gif` (a GIF without its metadata blocks), `readAtMost`.
  - `blog/`: `Blog` ties one repository and branch together: `index()` reads the posts,
    taxonomy, config and image folder, reusing summaries for unchanged blobs.
  - Test fixtures: `FakeGitHub`, a MockWebServer that serves a repository with real commits
    (`addRepository` serves another beside it), and `FakeVpn`, a VPN tests turn on and off.

- **`:app`** (Android):
  - `data/`: Room, created by `PosterDatabase.create` with its migrations:
    - `Draft`: posts written on the phone, each with its blog's key (`owner/repo@branch`).
    - `SignIn`: a token or a GitHub App sign-in, its tokens sealed by `SecretCipher`.
    - `SavedBlog`: a blog, keyed `owner/repo@branch`, the sign-in that posts to it, and its title
      as last read.
    - `CachedPost`: each blog's posts as last read, keyed by blog and path.
  - `AccountStore`: reads a blog with its sign-in as an `Account`. Which blog is open is a
    DataStore value; `current()` is that blog's, `forBlog(key)` any blog's. Either renews its
    sign-in's token on use, under one lock with saving and removing. `blogs` lists every blog with
    its sign-in; `open`, `addBlog` (under any sign-in) and `remove` (refused, in one transaction,
    while a post is queued for one of its blogs) change them. On first use it moves the account an older
    version kept in DataStore into the tables, once, and gives drafts with no blog its key.
  - `BlogRepository`: refreshes a blog's cached posts, and holds the open blog's taxonomy, config,
    address and paths. `refresh(blog)` reads any blog, for a post going out to it, but changes
    those only when it's the open one.
  - `Privacy`: each blog's switches, a `BlogPrivacy` row per blog key (none means all off). On
    first use it gives every blog the switches an older version kept for the whole app.
    `Settings` keeps only the Obsidian vault folder. `AndroidVpn` is
    `Vpn` from the phone's default network.
  - `data/ObsidianVault`: lists the vault folder the writer picked, through the Storage Access
    Framework, for a note's `![[photo]]`. The editor imports what it finds like any photo.
  - `data/ImageImporter`: a picked, shared or camera photo → upright, scaled, re-encoded file in
    app storage. Camera photos arrive in `cache/camera/` through a FileProvider and are deleted
    once imported.
  - `publish/`: `Publisher` (the rules for sending a queued post, one at a time, to the post's
    own blog with its sign-in: it plans a new post, an edit, a move out of `_drafts` or a
    delete, then commits the plan),
    `PublishQueue` (where queued posts are handed over; `WorkManagerQueue` in the app),
    `PublishWorker` (WorkManager: needs a network, waits for a post's random time and the VPN,
    exponential backoff), `BuildWatcher` and `BuildWatchWorker` (polls the post's blog's Pages
    run for its commit), `Notifier`.
  - `ui/`: Compose screens with a ViewModel each: `connect`, `home`, `editor` (the screen, its
    fields, toolbar, preview with `PreviewFetcher`, and term picker in their own files),
    `settings` (with `BlogPrivacy`), `blogs` (the switcher's list, Your blogs and Remove, shared by
    Home's sheet and Settings). `catching` is `runCatching` that lets cancellation through.
    `ui/theme`: the look as a `PosterStyle` (colours, type, shapes) and its `Whimsy` (marks,
    squiggles, row style, category colours, the stamp), read by small shared composables
    (`SectionHeading`, `TermPill`, `PosterFab`, `InkButton`). `DesignRoundTest` renders styles
    side by side with `-Pdesign`.
    `PosterNavHost` picks the first screen once the stored account has loaded, and starts a
    shared (`HomeViewModel.startShared`, through `ObsidianNote`) or New post once (a saved flag:
    Home's effects run again each time it returns).
  - `MainActivity`: `postToStart` turns its intent (a share, a Markdown file opened, the
    launcher's New post shortcut) into the post to start; the shortcut is a dynamic one it adds.
  - `AppContainer`: manual DI. Every web client goes through a `VpnGate`: one per blog, gated by its switch, and one for signing in, gated if any blog asks. A share's photos and
    embeds wait in `pendingShares` for the editor. `PosterApp` starts the VPN-back retry once
    the container is built: nothing launches from a constructor, where it could run before the
    properties it uses are set. Tests (`TestApp`) swap the GitHub address, cipher, database,
    DataStores, vault, VPN and publish queue (publishing at once), and sign in with `signIn()`.

## A post's life

1. `Draft` row, state `Draft`, saved as the writer types.
2. Publish: `[[titles]]` linked, state `Queued` (with a random `sendAfter` if the writer asked),
   handed to the `PublishQueue`.
3. `PublishWorker` waits for that time and, if asked, a VPN; then `Publisher` fixes path and
   date, commits, marks `Published` with the commit sha and `BuildState.Building`, refreshes
   the post cache. The VPN coming back, or Send now, starts a second worker beside a waiting
   one; the publisher's lock and the post's state keep it to one commit.
4. `BuildWatchWorker` turns `Building` into `Live`, `Failed` or `Unknown`.
5. A failure the writer must fix (sign-in, access, an edit conflict) is state `Failed` with an
   `error`; they edit and publish again.
6. Delete from the blog is the same queue with `Destination.Delete`: the path is recorded in
   `targetPath` before the commit, and the row is deleted once the file is gone.
