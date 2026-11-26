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
   "A newer build, N, is ready." box appears; tap **Install**. The first time, Android sends you to
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

## The tabs

The tests prove what is on each tab and that each keeps its place. The system bars and the
keyboard are only real on a phone.

1. Today, History and Settings sit above the system's navigation bar or gesture line, whole.
2. On the Log screen tap "Exercise, as the board says it": the tabs make way for the keyboard
   and come back when it closes. The same under Settings while searching for an exercise.
3. Leave the app on Settings, come back after a quarter of an hour: it opens on Today.

## Past classes

The tests prove the list, a class, a lift and the way back. Only a phone has a real history
and the system's back gesture.

1. History, under the Progress card: your classes, newest first, yours and the imported ones.
2. Tap a class, then one of its rows: the lift's sessions, newest first. Do the weights match
   what you remember?
3. The system's back gesture goes lift, class, list, and only then leaves the app.
4. On Today, tap a row with a number, then "Past sessions": the same lift, under History.

## Dark theme

The tests prove no screen is left white. The system bars and the moment before the first
frame are only real on a phone.

1. Set the phone to dark and open Spotter: no white flash, and the clock and battery at the
   top are readable over the page.
2. Each tab, and the Log screen: nothing glares, nothing is too faint to read in the car.

## Sorting a word

The tests prove the taps, what an answer does to a line, and that answers are kept. The
keyboard, and a real board's word, are only visible on a phone.

1. Under Progress, "Words Spotter doesn't know yet" lists words from recent boards. Tap one.
2. **It's another name for…**: type part of an exercise's name. The keyboard doesn't cover the
   names offered; tap one, read what it says back, then **Yes, read it that way**.
3. The word leaves the list and appears under "Your answers". If today's board has it, that
   row now has the exercise's number.
4. **Undo** on the answer: the word is back on the list and the row is "not sure" again.
5. Turn the phone while the search is open: the word, and what you typed, are still there.

## Adding an exercise

The tests prove what a typed line becomes and that it is saved. The keyboard, and what
TalkBack says, are only on a phone.

1. Log a class, scroll to "Did one the board missed?", and tap the field: the keyboard comes
   up and the field, with the button under it, is still in view above it.
2. Type `8 x chest press` and press the keyboard's Done: a row appears under "Added by you".
3. Type `8 x zorbing` and tap **Add an exercise**: it says Spotter isn't sure what that is.
   With TalkBack on, that sentence is spoken without touching it.
4. Turn the phone with a line half typed: the line is still there.
