# Notify when a new session file lands

On the backlog since the folder picker shipped. Not built.

## Why

The whole point of the Before tab is a glance in the lobby, and that glance
still depends on the owner remembering to open the app. A session file
appears in the synced folder hours before the class; nothing tells them.

## The decision this forces

**The app currently declares no Android permissions at all.** `docs/DESIGN.md`
and the design doc both state that as a property: it cannot reach the network
or anything else even in principle, and that is easy to verify by reading one
manifest.

Posting a notification needs `POST_NOTIFICATIONS` on API 33+. That is a real
change to a stated property of the app, and it should be a deliberate choice
written into DESIGN.md rather than a side effect of a feature. It is still a
*local* permission -- nothing leaves the device -- but the sentence "declares
no permissions at all" stops being true and must be rewritten honestly.

## The mechanism

A `WorkManager` periodic check is the realistic option. Minimum interval is 15
minutes and Doze will stretch it, which is fine: the file lands hours ahead,
so this is not a timing-critical notification.

- Keep the set of session file names last seen; notify when a new one appears
  whose date is today.
- The tree permission is persisted, so the worker can list the folder without
  the owner present. Verify that holds after a reboot -- a revoked grant must
  fail quietly, not crash a background worker.
- Tapping the notification opens the Before tab for that session.

## What it must not do

- No notification for a file that is not today's.
- No repeat for a file already seen, including across a reinstall if the
  stored set is lost -- prefer re-notifying nothing to notifying everything.
- Nothing in the notification text beyond the class and the date. The owner's
  numbers do not belong on a lock screen.

## Tests

Robolectric around the worker's decision logic -- new file, already-seen file,
a file for another day, an unreadable folder. The decision is a pure function
of (names seen, names now, today); keep it that way so it can be tested
without Android.
