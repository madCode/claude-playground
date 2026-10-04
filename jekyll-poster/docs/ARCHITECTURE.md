# Architecture

## Modules

- **`:core`** (JVM): no Android.
  - `frontmatter/`: `FrontMatterDocument` splits a file into YAML and body, edits single keys
    and keeps the rest byte for byte. SnakeYAML Engine reads values; the app writes its few value
    shapes itself, quoting only what YAML would misread.
  - `jekyll/`: Jekyll's conventions. `PostPath` (what's a post, its date, slug and folder
    categories), `Slug`, `SiteConfig` (from `_config.yml`), `PostSummary`, `Taxonomy`,
    `PostWriter` (new posts and edits), `Permalink` (a post's address, the site's address),
    `Images` (where a new photo goes and its Markdown) and `Preview` (commonmark, Liquid links).
  - `github/`: `GitHubClient`, a thin OkHttp client. Commits go through the Git Data API
    (blobs, tree on `base_tree`, commit, fast-forward ref update, rebuilt on a conflict), so a
    post and its images land in one commit. `expect` names files that must be unchanged at the
    head the commit lands on, checked on every rebuild. Post contents are fetched in batches through
    GraphQL by blob sha.
  - `blog/`: `Blog` ties one repository and branch together: `index()` reads the posts,
    taxonomy, config and image folder, reusing summaries for unchanged blobs.
  - Test fixtures: `FakeGitHub`, a MockWebServer that serves one repository with real commits.

- **`:app`** (Android):
  - `data/`: `AccountStore` (DataStore, token sealed by `SecretCipher`), Room
    (`Draft` for posts written on the phone, `CachedPost` for the blog's posts as last read),
    `BlogRepository` (refreshing that cache, the taxonomy flow).
  - `data/ImageImporter`: picked photo → upright, scaled, re-encoded file in app storage.
  - `publish/`: `Publisher` (the rules for sending a queued post, one at a time), `PublishWorker`
    (WorkManager, needs a network, exponential backoff, unique per post), `BuildWatcher` and
    `BuildWatchWorker` (polls the Pages run for the post's commit), `Notifier`.
  - `ui/`: Compose screens with a ViewModel each: `connect`, `home`, `editor`, `settings`.
    `ui/theme`: the look as a `PosterStyle` (colours, type, shapes) and its `Whimsy` (marks,
    squiggles, row style, category colours, the stamp), read by small shared composables
    (`SectionHeading`, `TermPill`, `PosterFab`, `InkButton`). `DesignRoundTest` renders styles
    side by side with `-Pdesign`.
    `PosterNavHost` picks the first screen once the stored account has loaded.
  - `AppContainer`: manual DI. Tests swap the GitHub address, cipher, database, DataStore and
    the publish scheduler (`TestApp`).

## A post's life

1. `Draft` row, state `Draft`, saved as the writer types.
2. Publish: state `Queued`, `PublishWorker` enqueued.
3. `Publisher` fixes path and date, commits, marks `Published` with the commit sha and
   `BuildState.Building`, refreshes the post cache.
4. `BuildWatchWorker` turns `Building` into `Live`, `Failed` or `Unknown`.
5. A failure the writer must fix (sign-in, access, an edit conflict) is state `Failed` with an
   `error`; they edit and publish again.
