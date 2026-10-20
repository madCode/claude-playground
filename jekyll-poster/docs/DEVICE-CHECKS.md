# Device checks

Most of what used to need the laptop's emulator runs on CI's, on every PR and on main. Two checks
still need a person: run them now and then, when a change touches their path. Neither is on a
schedule.

## On CI's emulator

"Jekyll Poster device tests" runs these on every PR that touches the app. `:sender` is a second
app installed beside it, so photos and notes arrive from another process with a real URI grant.

| Check | Test | It proves |
|---|---|---|
| A photo from another app | `FromOtherAppsTest` | It's added with its alt text, and the copy on GitHub has no location and doesn't say what made it |
| A note from Obsidian | `FromOtherAppsTest` | The post takes the note's name as its title, `[[links]]` to the blog's posts become `post_url` links, `%%comments%%` are gone |
| The camera | `FromOtherAppsTest` | A photo from the emulator's own camera app is added, and the original is deleted from the app's cache |
| Rotation | `ScreensOnTheDeviceTest` | Text typed, the tag picker and its search survive turning the phone, and the post isn't made twice |
| Large text and dark theme | `ScreensOnTheDeviceTest` | Home, the editor and Blog & privacy at twice the text size, in the dark, pass Android's accessibility checks (labels, touch targets, contrast). Their pictures are kept in the run's report, under `screens/` |

"Jekyll Poster device live check" runs nightly against the tester's real blogs
([TESTER_ACCOUNT.md](TESTER_ACCOUNT.md)).

What CI can't show:

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
