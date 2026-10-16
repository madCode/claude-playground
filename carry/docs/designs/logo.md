# A logo for Carry

Owner asked, 2026-10-12. Built 2026-10-16: the owner picked H3, *pulse on a dot*,
from the concepts on the private logo canvas.

## The constraint that decides it

It has to work as a **one-colour launcher icon at 48dp** as well as wherever
else it ends up. That rules out anything with fine detail, a gradient, or a
word in it.

## What is already settled

The app's visual identity exists and the logo should sit inside it rather
than invent a second one:

- **Barlow Condensed**, bundled, used for every name on every screen.
- **Chalk on slate** -- light type on a dark ground, which is what the whole
  app looks like.
- **Amber means exactly one thing**: the number you came here for. A logo
  that spends amber on decoration devalues it everywhere else.
- The muscle-group hues (legs, push, pull, core, combo) are a coding system,
  not a palette to draw with.

## Worth knowing before drawing

The name is about carrying numbers in your head from the lobby to the floor
and back -- not about lifting. A barbell or a dumbbell would be the obvious
drawing and probably the wrong one; the app never sees a gym.

## Deliverable

An adaptive icon (foreground + background layer), a monochrome variant for
Android 13+ themed icons, and a check at 48dp on both a light and a dark
launcher background before it is called done.

## What was built

An interval pulse drawn in the five heart-rate zones (grey, blue, green,
orange, red), the colours every class is coached in, with a chalk dot at
the peak. The owner asked for colour that says gym and HIIT without
borrowing anyone's logo, so this departs from *chalk on slate* in one
place, and keeps the rest:

- **Amber is untouched.** The zone orange (#FF8A2A) is one zone of five,
  never alone and never a ground, and is a different colour from the amber.
- **Slate stays the ground** (`ic_launcher_background`, the window colour).
- **One colour still works**: the themed icon and the status-bar icon are
  the same pulse without its zones.

The drawing is a 100-unit grid scaled into the 66dp circle every launcher
mask keeps; `IconTest` fails if ink lands outside it, or if the status-bar
icon gains a second colour (the status bar draws only alpha).
