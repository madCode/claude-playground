# Releasing

Before a build goes to anyone beyond the owner: a store, F-Droid, a tester.

## Before

- The security and supply chain review ([recurring-reviews.md](recurring-reviews.md)) has run
  since the last release.
- Android's [core app quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)
  list, on a real device: going to the background and back, sleep and wake, Recents, battery,
  permissions asked for only when used.
- TalkBack, 200% font size and dark theme on every screen.
- A Room schema change has its migration and test, tried by upgrading over the last release.

## Signing

- The release key never enters the repository or an agent session. It lives in CI secrets, used
  only by the release job, on tags from `main`. Google Play's app signing (an upload key) means a
  leaked key can be replaced.
- The debug key isn't a release key. If debug builds go to testers, they're signed in CI with a
  key kept in secrets, so no one else can sign a build that installs over theirs.

## Steps

dailylog's process, which ships signed APKs to GitHub and F-Droid:

1. Bump `versionCode` and `versionName`.
2. A `CHANGELOG.md` entry for people using the app: what's new for them, not the dev log.
3. Commit, tag `v<version>`, push the tag. The release workflow builds the signed APK and
   attaches it to the GitHub release.
4. Check the store or F-Droid picked it up (F-Droid within a day).
