# CarryViewModelTest reddens CI at random

Not built. Small, and worth doing before it wastes a review cycle.

## The symptom

In a full-suite run, `CarryViewModelTest` occasionally fails with:

    Dispatchers.Main is used concurrently with setting it

It passes reliably when run alone. It is on `main` already and is unrelated
to whatever PR it fails on, which is the expensive part: it reddens a PR for
a reason that has nothing to do with the diff.

## The cause

`setMain` / `resetMain` are correctly paired in `@Before` / `@After`. The race
is a `viewModelScope` job outliving its test: the ViewModel is never cleared,
so work launched in one test can still be touching `Dispatchers.Main` while
the next test sets it.

## The fix

Cancel the model in `@After`, before `resetMain()`. Hold the ViewModel in a
field so the teardown can reach it.

## How to know it worked

A flake cannot be proved fixed by one green run. Run the full suite in a loop
-- twenty times is enough to make a random failure likely -- before and after.
If it cannot be reproduced before the fix, do not claim the fix.
