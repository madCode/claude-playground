# Read the class from the phone's own calendar

On the backlog as the thing that frees the companion script from needing a
calendar config. Not built, and bigger than its one-line backlog entry.

## What it would replace

The coach decides there is a class today by reading ICS URLs from a config
file on the Mac. That config is one of four things tying the coach to the
assistant it grew up in; the other three die when Carry shows the cheat
sheet. If the phone supplies the class instead, that one goes too.

## The part that is not obvious

**The coach needs the class before it writes the session file, and the coach
runs on the Mac.** So the app reading the calendar does not by itself help:
the answer has to travel back across the folder, and it has to arrive first.

That means a new file in the shared folder -- something like "the classes I
can see for the next few days" -- written by the app and read by the coach at
trigger time. That is a **protocol addition**, and the companion script owns
the schema (see CLAUDE.md). It should be agreed on that side before any app
code is written, or the app will produce a file the importer ignores.

Sequencing matters too: the file must be synced and present before the
coach's trigger fires, not after. Syncthing is quick but not instant.

## The permission

`READ_CALENDAR` is a runtime permission, and the app currently declares none
at all -- a property stated in DESIGN.md and verifiable by reading one
manifest. Adding it is a deliberate change to that property and belongs in
DESIGN.md, with the reason, rather than arriving as a side effect.

It is also the most invasive permission the app would ever hold: a calendar
is a log of someone's life. Read the narrowest thing that works -- today and
the next few days, matching on the class tag -- and never copy event titles
into the shared folder, which is synced and read by another machine.

## A caution from the existing code

The coach's own calendar lookup shipped with `.between(d, d)` -- a zero-length
interval at midnight -- so it matched **only all-day events** and the gym
trigger never fired once before it was found. A calendar query is exactly the
kind of code that looks right and returns nothing.

Whatever queries the phone's calendar gets a test with a timed event and an
all-day event in the fixture, and the test must fail if the query stops
finding the timed one.

## Smaller alternative worth considering first

If the goal is only to stop maintaining ICS URLs, letting the owner type the
class into the app for today is a fraction of the work and needs no
permission. Worth asking whether the calendar read is the goal or just the
means.
