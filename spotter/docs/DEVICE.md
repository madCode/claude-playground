# Checking on the phone

What the tests can't see, because the last step happens in Android itself. Run through it when a
change touches the part named, and say in the PR that it's due.

## Updating itself

The tests prove Spotter picks the right build, checks its bytes and hands it to Android's
installer. Whether Android installs it without a tap is only visible on a phone.

1. First install, once: on the phone, open
   https://github.com/madCode/claude-playground/releases/download/spotter-debug/spotter-debug.apk
   and install it. Android asks to allow your browser to install apps.
2. Open Spotter. The version at the bottom names the build. If a newer one is out, the blue
   "A newer build is ready" box appears; tap **Install**. The first time, Android sends you to
   allow Spotter to install apps; allow it, go back, tap Install again, and confirm "Update".
3. From then on, after a merge to main: close Spotter and wait. Within about three hours (on
   any network) the version at the bottom is the newer build, with no tap. On Android 11 and
   older, Android always asks, so the box appears instead.

If it doesn't update: open Spotter. A box means Android wanted a tap; no box and an old version
means the check hasn't run yet or the phone was offline.
