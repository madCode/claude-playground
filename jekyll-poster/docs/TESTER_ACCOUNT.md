# The tester account

The live checks post to real blogs on GitHub. They use a GitHub account made only for testing,
so a leaked CI token can reach sample blogs and nothing else. It's the studio's, so other apps'
tests can use it later. Only madCode can make it. About 30 minutes, plus five for the VPN.

The tester never joins Seafloor-Studio, madCode's repositories or any real project: that's what
keeps a leaked token harmless.

## 1. The account

1. Sign out of GitHub (or use a private window) and sign up as `seafloor-studio-tester` (or a
   close name; tell Claude which). Use a `+` alias of the studio's inbox, and filter that alias
   out of the inbox: test posts and Pages builds send a lot of mail.
2. Turn on two-factor authentication, and keep the authenticator and recovery codes in the
   password manager. Without them, nobody can make new tokens when these run out.

## 2. Three test blogs

Each is a copy of [madCode/sample-blog](https://github.com/madCode/sample-blog). Signed in as the
tester:

1. [github.com/new/import](https://github.com/new/import), from
   `https://github.com/madCode/sample-blog`, as **public** `jekyll-test-blog-a`. Then the same
   again as `jekyll-test-blog-b`. If the importer refuses, madCode makes sample-blog a template
   repository (its Settings) and the tester uses **Use this template** instead.
2. Make an organisation (free plan), `seafloor-studio-test-org`, and import a third copy into
   it as `jekyll-test-blog-org`.
3. On each: Settings → Pages → Deploy from a branch, `main`, `/ (root)`. A minute later the site
   is at `https://<owner>.github.io/<repo>/`.
4. On the organisation: Settings → Personal access tokens → allow fine-grained tokens, without
   approval.

## 3. One token per blog

Signed in as the tester: Settings → Developer settings → Fine-grained tokens → Generate new
token, three times, one per blog:

- **Resource owner:** the tester for `jekyll-test-blog-a` and `jekyll-test-blog-b`, the
  organisation for `jekyll-test-blog-org`.
- **Repository access:** only that one blog.
- **Permissions:** Contents read and write; Actions read-only.
- **Expiration:** the longest offered, at most a year. Tell Claude the date: it schedules a
  reminder for the week before.

## 4. Into claude-playground

In madCode/claude-playground: Settings → Secrets and variables → Actions → **Secrets**:

| Secret | Value |
|---|---|
| `TEST_BLOG_A_TOKEN` | the `jekyll-test-blog-a` token |
| `TEST_BLOG_B_TOKEN` | the `jekyll-test-blog-b` token |
| `TEST_BLOG_ORG_TOKEN` | the `jekyll-test-blog-org` token |

And under **Variables**, so the workflows know where to post:

| Variable | Value |
|---|---|
| `TEST_BLOG_A` | `seafloor-studio-tester/jekyll-test-blog-a` |
| `TEST_BLOG_B` | `seafloor-studio-tester/jekyll-test-blog-b` |
| `TEST_BLOG_ORG` | `seafloor-studio-test-org/jekyll-test-blog-org` |

Never paste a token into a chat, an issue or a commit. They go into these secrets only.

## 5. Sign in with GitHub (for the laptop checks)

1. Install the Jekyll Poster GitHub App on the tester account and the organisation, each only on
   its blogs. The app's page: `github.com/apps/<slug>` (see [GITHUB_APP.md](GITHUB_APP.md)).
2. In the Claude desktop app's built-in browser, sign in to GitHub as the tester once. The
   sign-in stays there, so Claude can enter a device code on the emulator's behalf. Only
   there: kept out of your everyday browser, it can't get mixed up with your own GitHub.

## 6. A VPN (for the device live check)

The "only connect through a VPN" switch needs a real VPN to check: a post waits while it's off
and goes out once it's on. The device live check runs it with the WireGuard app and a config
from a free [Proton](https://proton.me/vpn) account for the tester (another `+` alias of the
studio's inbox).

1. At account.protonvpn.com, Downloads, WireGuard configuration: name it `jekyll-poster-ci`,
   platform Android, NetShield off, any free server. Download the file.
2. Paste the whole file into the secret `PROTON_WG_CONF`, as for the tokens above, then delete
   the download. Without it, the VPN test is skipped.

Then tell Claude it's done. The first cycle after that runs the live check against all three
blogs to show the tokens work.
