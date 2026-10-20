# Checks on the laptop's emulator

What CI's emulator can't do: real other apps, the device-flow sign-in in a browser, a real VPN,
and looking at the screens. A session in the Claude desktop app on madCode's laptop runs these,
with `tools/emulator-check.sh` for the adb steps. Run them when a change touches the path, and
every few weeks otherwise. About 40 minutes.

The tester account's sign-ins live in the desktop app's built-in browser (GitHub and Proton, see
[TESTER_ACCOUNT.md](TESTER_ACCOUNT.md)). Post only to the tester's blogs, and delete what you post.

## Before

1. Note free disk and load. An emulator already running may be madCode's: leave it and say so.
2. An emulator with Google Play, API 33 or newer, built for the laptop's processor (arm64).
   `tools/emulator-check.sh start <avd>` boots it behind a 45-minute watchdog.
3. `tools/emulator-check.sh install`: the newest CI build from main. It has Sign in with GitHub.
4. `tools/emulator-check.sh record on`, so the run leaves a request record.

## The checks

| Check | Do this | It worked if |
|---|---|---|
| **Sign in with GitHub** | In the app, Sign in with GitHub. Enter the code at github.com/login/device in the built-in browser (signed in as the tester), and authorise | The tester's blogs are listed; pick `jekyll-test-blog-a` and its posts load |
| **A note from Obsidian** | Install Obsidian from Play. Make a vault with a note holding a `[[link]]` to another note, a `%%comment%%` and an embedded photo. Share the note to Jekyll Poster | A post opens with the note's title and body, the comment gone, the link as a `post_url`, and the photo in place |
| **A photo from the gallery** | `adb push` a JPEG with a GPS location to `/sdcard/Pictures`, open it in Photos and share it | The editor has the photo; once published, the file on GitHub has no location (`exiftool` on the downloaded file) |
| **The camera** | In the editor, take a photo with the emulator's camera | It's in the post, and nothing is left in the app's `cache/camera` |
| **Only through a VPN** | Install Proton VPN from Play and sign in as the tester. Blog & privacy: turn on Only connect through a VPN, with Proton off. Publish | The post shows "Waiting for your VPN…" and isn't on GitHub. Turn Proton on: it goes out within a minute |
| **Rotation** | Rotate (`tools/emulator-check.sh rotate on`) with text typed, a dialog open, and the term picker open | Nothing typed is lost; no dialog or post appears twice |
| **Large text and dark theme** | `big-text on`, then `dark on`; look at Home, the editor and Blog & privacy | Nothing clipped or overlapping; text readable on the zine colours |
| **TalkBack** | Turn TalkBack on; go through New post and Publish | Every button says what it does; the blog switcher names the open blog |

## After

1. `tools/emulator-check.sh record pull`: `requests.jsonl` holds every request the run made. It
   should hold no token: `grep -E 'gh[pousr]_|github_pat_' requests.jsonl` finds nothing. Give it
   to the several-blogs audit if one is due.
2. Delete the posts the run made, from the app or on GitHub.
3. `big-text off`, `dark off`, `rotate off`, then `tools/emulator-check.sh stop`. It fails if an
   emulator is left.
4. Free disk and load as they were. Write what passed, what didn't and the date in the cycle's
   DEVLOG.md entry, and update "Last checked" below.

## Last checked

| Check | When |
|---|---|
| Sign in with GitHub | Not yet |
| A note from Obsidian | Not yet |
| A photo from the gallery | Not yet |
| The camera | Not yet |
| Only through a VPN | Not yet |
| Rotation | Not yet |
| Large text and dark theme | Not yet |
| TalkBack | Not yet |
