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

Cancel the model in `@After`, before `resetMain()`, so the teardown can reach
it.

**A single field is not enough.** Two tests construct *two* ViewModels each --
`a half-filled form survives the process dying` (lines 119 and 127) and
`another day's numbers are never shown against this session` (lines 156 and
161). One field catches whichever was assigned last and leaves the other's
scope alive into the next test, which is the race you were trying to close.
Use a registry: a list, a helper that constructs and registers, and an
`@After` that cancels every one of them.

The likeliest culprits are the two tests that park a job for ever --
`model.read = { awaitCancellation() }` at lines 65 and 96. Those jobs end only
when the scope is cancelled.

**One honest caveat for the PR:** `withContext(Dispatchers.IO) { readFolder() }`
wraps blocking, non-cooperative work, so cancelling will not interrupt it
mid-call -- it only stops the resumption. That is harmless for this race,
because the part that touches `Dispatchers.Main` is the state write-back
inside the scope, and cancellation does stop that.

## How to know it worked

A flake cannot be proved fixed by one green run. Run the full suite in a loop
-- twenty times is enough to make a random failure likely -- before and after.
If it cannot be reproduced before the fix, do not claim the fix.
