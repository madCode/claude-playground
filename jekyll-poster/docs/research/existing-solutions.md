# Existing solutions and technical facts

Research pass, October 2026. Goal: an Android app that lets a coder write a
post on their phone and publish it to a Jekyll blog on GitHub Pages, with
the app making the commit through the GitHub API.

"?" means I could not confirm it. Maintenance status is from public pages
seen in October 2026; check again before relying on it.

## Comparison

| Tool | Platform | Alive in 2026? | Price | GitHub auth | Front matter | Reads existing categories/tags? | Images | Drafts | Preview / offline | Pages build status |
|---|---|---|---|---|---|---|---|---|---|---|
| **JekyllEx** | Android (F-Droid) | Yes, v0.2.5 Sep 2026 | Free, MIT | Token, via built-in git | Raw YAML in an editor | No | Files in the clone | Real `_drafts` | Runs real Jekyll on the phone; fully offline | No |
| **HugoNest** | Android, iOS, desktop | Made free Apr 2025; little activity since | Free | Git clone/push | Form plus raw | ? (AI suggests tags) | Media manager | ? | Live preview | No |
| **Git Blog** (Nimble Studio) | iOS, macOS | New in 2026 | Free, $2.99 unlock | Token in keychain | Reusable templates | No | Block editor with layouts | ? | ? | No |
| **Octopage** | iPhone | Old; unclear if still sold | $1 | ? | Prefilled Jekyll YAML | No | ? | ? | Markdown preview | No |
| **Working Copy** (+ Shortcuts, Ulysses, Editorial) | iOS | Yes | $14.99 to push | OAuth / SSH | By hand or by script | No | By hand | By hand | Offline git | No |
| **GitHub Mobile** file editor | Android, iOS | Yes | Free | GitHub sign-in | None (plain text) | No | No upload | No | No | Actions tab, by hand |
| **GitJournal** | Android, iOS (Flutter) | Yes, v1.89 (2026) | Free / pro | SSH deploy key | YAML header, notes-oriented | Tags within its own notes | Limited | No | Offline | No |
| **Obsidian + obsidian-git** | Android, iOS, desktop | Plugin alive; authors advise against mobile | Free | HTTPS + token (no SSH on mobile) | Templates / Properties | Obsidian's own tag index | Attachments folder | Folder-based | Offline | No |
| **Pages CMS** | Web | Yes, ~4k stars | Free, hosted or self-host | **GitHub App** | Form from `.pages.yml` | No; options listed in config | Media manager, drag and drop | ? | Field form, no site preview | No |
| **Decap CMS** (ex Netlify CMS) | Web | Slow; releases continue | Free | OAuth via a server proxy (Netlify or own) | Form from `config.yml` | Only via a relation widget to a collection | Media library | Editorial workflow (PRs) | Template preview | No |
| **Sveltia CMS** | Web, mobile-friendly | Yes, active | Free | **PAT "Sign in with token"** or OAuth via Cloudflare Worker | Decap-compatible config | Same as Decap | Asset library | Editorial workflow | Template preview | No |
| **Prose.io** | Web | Effectively abandoned; seeking maintainers | Free | OAuth (needs gatekeeper server) | Raw YAML plus `_prose.yml` | No | Upload to repo | ? | Markdown preview | No |
| **Siteleaf** | Web | Yes | Free for public repos, paid otherwise | OAuth sync | Form | Yes, it imports the site | Uploads | Yes | Cloud preview (paid) | Its own build |
| **CloudCannon** | Web | Yes | From $10/mo | OAuth sync | Form, visual editing | Yes, from its own build | Yes | Branch workflows | Full site build preview | Its own build |
| **Forestry → TinaCMS** | Web | Forestry closed Apr 2023; Tina is aimed at JS frameworks | Free tier, paid | Tina Cloud | Schema in code | No | Yes | Yes | Visual editing (React) | No |
| **Front Matter CMS** | VS Code (desktop only) | Yes | Free | Uses local git | Content types and forms | **Yes**: "export all tags & categories" from posts | Media dashboard | Yes | Local server | No |
| **Jekyll Admin** | Local web, plugin | Low activity | Free | None: needs `jekyll serve` | Form | Yes, it runs Jekyll | Static files | Yes | Real Jekyll | No |
| **Micro.blog** | Hosted, iOS/Android apps | Yes | $5+/mo | n/a | n/a | n/a | Yes | Yes | n/a | n/a |
| **Micropub bridges** (Indiekit, webpage-micropub-to-github, microglue, muan/micropub-endpoint) | Self-hosted server plus any Micropub client (Quill, iA Writer, Micro.blog) | Indiekit active; the rest mostly stale | Free, but you host it | Server holds a GitHub token; IndieAuth for the client | Server template | Indiekit can list categories from config | Media endpoint | ? | No | No |

Notes on the table:

- Micro.blog hosts its own Hugo blog; it cannot publish to a GitHub Pages
  repo except through a Micropub bridge you run.
- The iOS "Shortcuts + Working Copy" recipes (Ulysses, Editorial, Drafts)
  are the most written-about workflow, but they are glue scripts each
  person builds themselves. There is no Android equivalent.

## What we take from this

The market gap:

- **No Android app commits straight to GitHub without a full git clone.**
  JekyllEx, GitJournal, HugoNest and Obsidian all clone the repo. That
  costs storage and memory and fails on large repos (obsidian-git's own docs
  say mobile git crashes). An API-only app needs no clone and works on any
  repo size.
- **No mobile tool reads the blog's existing categories and tags.** Web
  CMSes make you list them in a config file. Front Matter CMS (desktop) and
  Siteleaf/CloudCannon (paid hosted builds) are the only ones that harvest
  them from posts. On a phone, picking from chips beats typing `categoreis`
  and creating a stray category.
- **No tool shows the GitHub Pages build result after publishing.** Every
  tool stops at "committed". People only find a broken build (bad YAML, a
  Liquid error) when they look at the site. A "Live / Building / Failed,
  here is why" status with a link to the post URL is a clear gap.
- **No zero-setup option.** Pages CMS needs a `.pages.yml`; Decap and
  Sveltia need an `admin/` folder plus an OAuth server; Micropub bridges
  need a hosted server. Our app should work on an unmodified Jekyll repo,
  learning conventions from what is already there.

Feature lessons:

- **Commit text and images together** (Git Blog, Pages CMS do this). One
  commit per post keeps history clean and avoids a half-published post with
  broken image links.
- **Learn from the repo, then ask.** Infer the posts folder, image folder
  (`assets/images`, `assets/img`, `images`), front matter keys and layout
  from recent posts; show the guess and let the user correct it once.
- **Front matter as a form with a raw-YAML escape hatch.** Coders want to
  add odd keys (`mathjax: true`, `redirect_from`); form-only editors
  frustrate them.
- **Templates** (Git Blog, Front Matter CMS): a reusable front matter
  skeleton per post type, defaulting to whatever recent posts use.
- **Drafts live on the phone first**, with optional "push as draft"
  (`_drafts/` or `published: false`). Offline writing must work; the
  network is only needed at publish time.
- **Preview honestly.** Only JekyllEx and the paid hosted CMSes render
  the real site. A Markdown preview is an approximation (no Liquid, no
  theme); say so, and link the live URL once the build passes.
- **Handle images like a phone app**: downscale, strip EXIF (GPS location
  is a privacy leak on a public blog), pick a sane filename, insert the
  Markdown with the right `baseurl`.
- **Editing existing posts** (Git Blog, Octopage, every CMS) is expected
  quickly after "new post"; plan the data model for it.
- **Avoid a server.** Decap/Prose died or decayed partly because GitHub
  OAuth needed a secret-holding proxy. Device flow (below) removes that.
- **PAT sign-in as a fallback** (Sveltia does this) helps people who
  don't want to install a GitHub App.

## Technical facts

### Committing: Contents API vs Git Data API

- **Contents API** (`PUT /repos/{o}/{r}/contents/{path}`): one file per
  commit; base64 body; an update needs the file's current `sha`; parallel
  calls conflict and must be serial. Fine for a text-only post, wrong for
  "post plus three images". [docs](https://docs.github.com/en/rest/repos/contents)
- **Git Data API**, one atomic commit for many files
  ([trees](https://docs.github.com/en/rest/git/trees)):
  1. `GET /git/ref/heads/{branch}` for the head commit sha.
  2. `GET /git/commits/{sha}` for its tree sha.
  3. `POST /git/blobs` per image (`encoding: base64`). Text can go inline.
  4. `POST /git/trees` with `base_tree` set (leaving it out deletes every
     other file) and entries `mode: 100644`, `type: blob`.
  5. `POST /git/commits` with `tree` and `parents: [head]`.
  6. `PATCH /git/refs/heads/{branch}` with `force: false`. If someone
     pushed in between, this fails with 422; rebuild on the new head.
- **GraphQL `createCommitOnBranch`** does the same in one call, takes
  `expectedHeadOid` for the race check, and the commit shows as verified.
  Worth considering. [docs](https://docs.github.com/en/graphql/reference/mutations#createcommitonbranch)
- File size: the Contents API serves up to 1 MB normally, up to 100 MB raw
  only. Downscale photos before upload anyway.
- **Rate limits**: 5,000 requests/hour per authenticated user; secondary
  limits of 80 content-creating requests per minute and 500 per hour, and
  100 concurrent. A post with 10 images is ~14 writes: fine, but upload
  blobs a few at a time, not all in parallel.
  [docs](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)

### Permissions (fine-grained PAT or GitHub App)

From [permissions for fine-grained tokens](https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens):

- **Contents: read and write**: blobs, trees, commits, refs, contents.
- **Metadata: read**: added automatically.
- **Actions: read**: `GET /actions/runs`, our build-status source.
- **Pages: read**: only `GET /pages` (site URL, `build_type`).
- Gotcha: `GET /pages/builds/latest` and `/pages/deployments` are listed as
  needing **Pages: write**. Avoid them so we never ask for write access we
  don't need.
- Classic PAT or OAuth App: needs `repo` (all private repos) or
  `public_repo`. Much broader than a GitHub App; a reason to prefer one.

### Sign-in: device flow

From [authorizing OAuth apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
and [GitHub App user tokens](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app):

- Works for both OAuth Apps and GitHub Apps. **Device flow must be enabled
  in the app's settings** or you get `device_flow_disabled`.
- **Client ID only, no secret**, so it is safe to ship in an APK.
- `POST https://github.com/login/device/code` returns `user_code`,
  `verification_uri`, `interval`. Then poll
  `POST https://github.com/login/oauth/access_token` with
  `grant_type=urn:ietf:params:oauth:grant-type:device_code`.
- Codes expire after 15 minutes. Errors: `authorization_pending`,
  `slow_down` (add 5 s to the interval), `expired_token`, `access_denied`.
- **GitHub App user tokens expire after 8 hours; refresh tokens last
  6 months.** Refresh needs the client secret **except** for tokens from the
  device flow, so a mobile app can refresh with no server.
  [docs](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/refreshing-user-access-tokens)
  If the refresh token lapses (6 months unused), send the user through
  sign-in again.
- A GitHub App user token only has permissions both the app and the user
  have, **on repos where the app is installed**. Onboarding must send the
  user to install the app on their blog repo (link to
  `https://github.com/apps/<slug>/installations/new`) and handle "no repos
  visible yet".
- Web flow with PKCE is also supported now (July 2025), but needs a
  redirect back into the app; device flow is simpler and needs no
  redirect handling.

### Pages build status

- `GET /repos/{o}/{r}/pages` gives `html_url` and `build_type`: `legacy`
  (built from a branch) or `workflow` (a custom Actions workflow).
  [docs](https://docs.github.com/en/rest/pages/pages)
- **Every Pages site now deploys through an Actions run**, even
  branch-built ones (the run is named "pages build and deployment").
  [docs](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)
- So the one method that covers both kinds is
  `GET /actions/runs?head_sha={our commit}` (Actions: read), then read
  `status` / `conclusion`, and job logs for the error.
- `pages/builds/latest` covers branch builds; whether it reflects custom
  workflows is not documented, and it needs Pages: write. Not worth it.
- Pages deployment statuses (`deployment_in_progress` … `succeed`,
  `deployment_failed`) exist on `GET /pages/deployments/{id}` but also
  need Pages: write.

### Discovering categories and tags

Approach: `GET /git/trees/{head}?recursive=1` once (limit 100,000 entries
or 7 MB, check `truncated`), then read the post files.

- Posts are any file under a `_posts` folder **at any depth**:
  `_posts/2024/x.md` is fine, and `movies/horror/_posts/x.md` gives the
  post the categories `movies` and `horror` from the folder path.
  [docs](https://jekyllrb.com/docs/posts/)
- If `_config.yml` sets `collections_dir`, posts live under
  `<collections_dir>/_posts`. Default is the repo root.
- Front matter keys: `category` (one value) and `categories`, `tags`
  (also `tag`). Each may be **a YAML list or a space-separated string**:
  `categories: web dev` means two categories, `web` and `dev`.
  [docs](https://jekyllrb.com/docs/front-matter/)
- `_config.yml` `defaults:` can set `category`/`categories`/`tags` by
  path and type, so posts inherit values that aren't in their own file.
  [docs](https://jekyllrb.com/docs/configuration/front-matter-defaults/)
- Treat values case-sensitively but warn on near-duplicates (`Android`
  vs `android`): categories go into URLs, tags don't.
- Fetching every post costs one request each. For a big blog, fetch only
  the newest N by filename, or use the tarball endpoint (one request),
  and cache by blob sha so later syncs only fetch changed files.
- Skip front matter that fails to parse rather than failing the sync.

### Jekyll post conventions and gotchas

- Filename: `YYYY-MM-DD-slug.md` in `_posts/`. A file without the date is
  skipped. [docs](https://jekyllrb.com/docs/posts/)
- Front matter must exist (even empty `---\n---`). Typical keys: `layout`,
  `title`, `date`, `categories`, `tags`. Quote titles: a colon in a title
  breaks YAML.
- `date` format: `YYYY-MM-DD HH:MM:SS +/-TTTT`; time and offset optional.
  The front matter date wins over the filename date for the URL.
- **GitHub Pages runs Jekyll 3.10** in safe mode with a fixed plugin
  allowlist. [versions](https://pages.github.com/versions/)
- **Timezone**: Jekyll uses the `timezone` from `_config.yml`, else the
  build machine's (UTC on GitHub). A post written at 23:30 in California
  is already "tomorrow" in UTC: the URL date can differ from the filename.
  Write the date with an explicit offset and name the file using the
  site's timezone.
  [docs](https://jekyllrb.com/docs/configuration/options/)
- **Future dates**: plain Jekyll defaults to `future: false` (skips posts
  dated later than build time), but the `github-pages` gem defaults to
  `future: true`. So branch-built sites publish future posts and
  custom-workflow sites silently skip them. Phone clocks running ahead
  can trip this. [pages-gem config](https://github.com/github/pages-gem/blob/master/lib/github-pages/configuration.rb)
- **Drafts**: files in `_drafts/` (no date in the name) are never built on
  Pages. `published: false` hides a post in `_posts/`. Either works as
  "push as draft".
- **Images**: Jekyll's docs suggest an `assets/` folder at the root;
  themes use `assets/images/` or `assets/img/`. Reference them with
  `{{ "/assets/images/x.jpg" | relative_url }}` or by prefixing
  `site.baseurl`: project sites (`user.github.io/repo`) break with a bare
  `/assets/...` path.
- **Permalinks**: default style `date` is
  `/:categories/:year/:month/:day/:title:output_ext`; read `permalink` in
  `_config.yml` to predict the live URL.
  [docs](https://jekyllrb.com/docs/permalinks/)

## Sources

- JekyllEx: https://f-droid.org/en/packages/xyz.jekyllex/ · Jekyll Talk, mobile thread (Apr 2025): https://talk.jekyllrb.com/t/how-to-update-my-jekyll-blog-from-my-phone/9908
- HugoNest made free: https://discourse.gohugo.io/t/before-i-walk-away-i-want-to-give-it-to-the-ones-who-truly-get-it/54500 · Git Blog: https://apps.apple.com/us/app/id6759486108
- Octopage: https://thecave.com/2017/04/21/how-i-post-to-my-jekyll-site-using-my-iphone · Ulysses + Working Copy: https://thesweetsetup.com/a-ulysses-shortcuts-and-working-copy-workflow-for-capturing-ideas-and-publishing-to-a-jekyll-based-blog/
- GitHub Mobile editing: https://github.blog/news-insights/product-news/file-editing-on-github-mobile-keeps-leveling-up/ · GitJournal: https://github.com/GitJournal/GitJournal
- obsidian-git mobile limits: https://publish.obsidian.md/git-doc/Getting%20Started · Pages CMS: https://pagescms.org and https://github.com/pagescms/pagescms
- Decap CMS: https://decapcms.org/docs/jekyll and https://github.com/decaporg/decap-cms · Sveltia CMS GitHub auth: https://sveltiacms.app/en/docs/backends/github
- Sveltia with Jekyll: https://dylanbeattie.net/2025/02/13/sveltiacms-jekyll-and-github-pages.html · Prose: https://github.com/prose/prose
- Siteleaf: https://www.siteleaf.com/blog/forestryio-cms-alternatives/ · CloudCannon pricing: https://cloudcannon.com/pricing/
- Front Matter CMS: https://github.com/estruyf/vscode-front-matter · Jekyll Admin: https://github.com/jekyll/jekyll-admin
- Micropub bridges: https://github.com/voxpelli/webpage-micropub-to-github, https://github.com/muan/micropub-endpoint, https://bmannconsulting.com/notes/micropub-to-github/
