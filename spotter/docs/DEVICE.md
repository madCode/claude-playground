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

## Today's sheet

The tests prove the sheet from a board and a calendar. Reading the phone's own calendar, and the
permission prompt, are only visible on a phone.

1. Open Spotter after the update. The first time, Android asks to let Spotter read the calendar;
   allow it.
2. With a class booked today or tomorrow ("Orange 60 Min 2G"), the sheet shows that class, its
   date and start, and the board's rows. Without one, it says "No class booked today or
   tomorrow."
3. Turn on flight mode and reopen: the same board shows, from the copy kept on the phone. The
   evening before a class, before the next board is posted, it says "No board posted … yet".


## Backup folder

The tests prove which files are written, changed and removed. Android's folder picker, the
folder's own app (Files, a sync app) and the daily run are only visible on a phone.

1. At the bottom of Spotter it says "Your log is only on this phone." Tap **Choose a backup
   folder**, pick or make one (say `Download/Spotter`) and allow it.
2. Within a minute the line reads "Log copied to Spotter · <time>". With a class logged, the
   folder holds `spotter-log.jsonl` and a `session-<date>-<class>.json` per class.
3. Log or correct a row and save: the time moves on, and the class's file has the change.
4. Delete one session file in Files, then save a log again: the file is back. Then delete the
   folder and save again: the line reads "Couldn't copy your log to
   Spotter. Choose the folder again."
5. A day later, without opening Spotter: the files are still current (the daily run).

## History import

The tests prove what is read from a file and what the sheet makes of it, on invented rows.
Android's file picker and your real file are only visible on a phone.

1. At the bottom of Spotter it says "No history imported." Tap **Import a history file** and
   pick your `history.jsonl` (from Downloads, or wherever you put it).
2. The line reads "History imported: N rows read." with what was left out, by reason. Rows for
   exercises you've lifted before now show a number on the sheet, not "new: go light".
3. Pick a file that isn't a history (a photo): it says nothing could be read, and the history
   you had is unchanged.
4. **Replace the history** with the same file: the counts are the same. Turn the phone while
   it says "Reading the file…": the result still shows.
5. **Remove the history** asks first. Removed, the sheet goes back to what your own log says.
6. With the phone's font size at its largest, both buttons are whole and can be tapped.
7. With TalkBack on, a refused file and "Reading the file…" are spoken without touching them.
