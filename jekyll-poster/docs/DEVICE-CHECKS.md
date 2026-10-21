# Device checks

Every check of the app on a device: what CI's emulator runs on every PR, what it runs nightly,
and on PRs that change the app's code, against real GitHub, and the two a person runs now and then. The JVM tests (`:core:test`,
`:app:testDebugUnitTest`) cover the rest, without a device.

## On CI's emulator, every PR

"Jekyll Poster device tests" runs these on every PR that touches the app, against a pretend
GitHub on the emulator serving the sample blog. `:sender` is a second app installed beside it, so
photos and notes arrive from another process with a real URI grant.

| Check | Test | It proves |
|---|---|---|
| Sign in, write, publish | `OnTheDeviceTest` | Signing in with a token, a post written in the editor goes out through the phone's own background work, and the "is live" notification shows |
| A link from another app | `OnTheDeviceTest` | A link shared from outside the app starts one post with it as a Markdown link |
| Front matter the app can't follow | `OnTheDeviceTest` | A post with a `?` key, pushed from elsewhere, isn't opened: Home says why, and nothing on GitHub changes |
| Sign in with GitHub twice | `OnTheDeviceTest` | Signing in with GitHub again to add a second blog keeps one sign-in holding both (the pretend GitHub approves the code; entering it in a browser is the person's check below) |
| New post from the launcher | `OnTheDeviceTest` | Long-pressing the app's icon on the home screen offers New post, which opens one empty post |
| A photo from another app | `FromOtherAppsTest` | It's added with its alt text, and the copy on GitHub has no location and doesn't say what made it |
| A note from Obsidian | `FromOtherAppsTest` | The post takes the note's name as its title, `[[links]]` to the blog's posts become `post_url` links, `%%comments%%` are gone |
| The camera | `FromOtherAppsTest` | A photo from the emulator's own camera app is added, and the original is deleted from the app's cache |
| Rotation | `ScreensOnTheDeviceTest` | Text typed, the tag picker and its search survive turning the phone, and the post isn't made twice |
| Large text and dark theme | `ScreensOnTheDeviceTest` | Home, the editor and Blog & privacy at twice the text size, in the dark, pass Android's accessibility checks (labels, touch targets, contrast). Their pictures are kept in the run's report, under `screens/` |

## On CI's emulator, against real GitHub

"Jekyll Poster device live check" (`LiveOnTheDeviceTest`) runs these every night, and on a PR that
changes it or the app's own code, on the tester's real blogs ([TESTER_ACCOUNT.md](TESTER_ACCOUNT.md)). Its posts are
deleted after, and its request record is kept for the privacy audit.

| Check | It proves |
|---|---|
| Two blogs, two sign-ins | Blog A signed in to and published from the screens, the organisation's blog added as a second sign-in; both posts go live on Pages and their notifications show |
| Privacy at its most | Every switch of Set up for writing anonymously but the VPN: the commit says "Update blog", names the no-reply address, and is dated by the day; tracking codes are gone; a random send time is set. What GitHub kept goes in `privacy-report.json` |
| The app killed while a post waits | A post queued for a minute ahead, then the app's process ended: Android starts its background work alone, and the post goes out |
| Only connect through a VPN | With the WireGuard app's Proton tunnel off, a post waits and no request reaches GitHub; with it on, the phone's public address changes and the post goes out |

"Jekyll Poster live check", a separate workflow with no emulator, publishes to and deletes from
all three of the tester's blogs nightly through the app's GitHub client.

## What CI can't show

- The camera's real lens, and a location in its photos. The shared photo's location is checked
  instead.
- A share from Obsidian itself. `:sender` shares a note the way Obsidian does.
- A vault folder for a note's embedded photos, chosen in Android's folder picker. The JVM tests
  cover the vault's photos.

## Now and then, by a person

On a phone or the laptop's emulator with the newest debug build (`tools/emulator-check.sh install`
puts it on an emulator). Post only to the tester's blogs, and delete what you post.

| Check | When | Do this | It worked if |
|---|---|---|---|
| **Sign in with GitHub** | A change to sign-in, or the GitHub App | Sign in with GitHub; enter the code at github.com/login/device signed in as the tester, and authorise | The tester's blogs are listed; pick `jekyll-test-blog-a` and its posts load |
| **A TalkBack listen** | A new screen, or a change to one's layout | Turn TalkBack on; go through New post and Publish | Every button says what it does; the blog switcher names the open blog |

The first needs a browser signed in as the tester, whose password and 2FA stay out of CI. The
second needs someone listening: CI's accessibility checks find missing labels, not labels that
make no sense.

## Last checked by a person

| Check | When |
|---|---|
| Sign in with GitHub | Not yet |
| A TalkBack listen | Not yet |
