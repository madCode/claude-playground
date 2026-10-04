# Turning on "Sign in with GitHub"

The app can sign in with a pasted token (always) or with "Sign in with GitHub" (GitHub's device
flow). The second needs a GitHub App, which only its owner can register. About five minutes:

1. github.com → Settings → Developer settings → **GitHub Apps** → **New GitHub App**.
2. **Name**: e.g. "Jekyll Poster (yourname)". **Homepage URL**: this repository.
3. **Callback URL**: leave empty. Tick **Enable Device Flow**.
4. **Webhook**: untick Active.
5. **Repository permissions**: Contents → Read and write; Actions → Read-only; Metadata →
   Read-only (required anyway).
6. **Where can this app be installed?** Only on this account (or any account, to share it).
7. Create it. On its page, note the **Client ID** (`Iv23…`; not a secret) and the URL name
   in its public link (`github.com/apps/<slug>`).
8. Optional: under **Optional features**, opt out of **User-to-server token expiration** to get
   tokens that don't expire. With expiry on, the app renews tokens itself.
9. **Install App** → your account → only your blog's repository.

Then build with them, or set them as repository **variables** (Settings → Secrets and variables
→ Actions → Variables) named `JEKYLL_POSTER_CLIENT_ID` and `JEKYLL_POSTER_APP_SLUG`, and CI's
debug APK has the button:

    ./gradlew assembleDebug -PgithubClientId=Iv23… -PgithubAppSlug=jekyll-poster-yourname
