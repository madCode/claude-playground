# The tester account

The live checks post to real blogs on GitHub. They use a GitHub account made only for testing,
so a leaked CI token can reach sample blogs and nothing else. Only madCode can make it. About 30
minutes.

## 1. The account

1. Sign out of GitHub (or use a private window) and sign up as `jekyll-poster-tester` (or a
   close name; tell Claude which). Use a `+` alias of your own email.
2. Turn on two-factor authentication.

## 2. Three test blogs

Each is a copy of [madCode/sample-blog](https://github.com/madCode/sample-blog). Signed in as the
tester:

1. [github.com/new/import](https://github.com/new/import), from
   `https://github.com/madCode/sample-blog`, as **public** `test-blog-a`. Then the same again
   as `test-blog-b`.
2. Make an organisation (free plan), e.g. `jekyll-poster-test-org`, and import a third copy into
   it as `test-blog-org`.
3. On each: Settings → Pages → Deploy from a branch, `main`, `/ (root)`. A minute later the site
   is at `https://<owner>.github.io/<repo>/`.
4. On the organisation: Settings → Personal access tokens → allow fine-grained tokens, without
   approval.

## 3. One token per blog

Signed in as the tester: Settings → Developer settings → Fine-grained tokens → Generate new
token, three times, one per blog:

- **Resource owner:** the tester for `test-blog-a` and `test-blog-b`, the organisation for
  `test-blog-org`.
- **Repository access:** only that one blog.
- **Permissions:** Contents read and write; Actions read-only.
- **Expiration:** the longest offered. Put a reminder in your calendar a week before.

## 4. Into claude-playground

In madCode/claude-playground: Settings → Secrets and variables → Actions → **Secrets**:

| Secret | Value |
|---|---|
| `TEST_BLOG_A_TOKEN` | the `test-blog-a` token |
| `TEST_BLOG_B_TOKEN` | the `test-blog-b` token |
| `TEST_BLOG_ORG_TOKEN` | the `test-blog-org` token |

And under **Variables**, so the workflows know where to post:

| Variable | Value |
|---|---|
| `TEST_BLOG_A` | `jekyll-poster-tester/test-blog-a` |
| `TEST_BLOG_B` | `jekyll-poster-tester/test-blog-b` |
| `TEST_BLOG_ORG` | `jekyll-poster-test-org/test-blog-org` |

Never paste a token into a chat, an issue or a commit. They go into these secrets only.

## 5. Sign in with GitHub (for the laptop checks)

1. Install the Jekyll Poster GitHub App on the tester account and the organisation, each only on
   its blogs. The app's page: `github.com/apps/<slug>` (see [GITHUB_APP.md](GITHUB_APP.md)).
2. In the Claude desktop app's built-in browser, sign in to GitHub as the tester once. The
   sign-in stays there, so Claude can enter a device code on the emulator's behalf.

Then tell Claude it's done. The first cycle after that runs the live check against all three
blogs to show the tokens work.
