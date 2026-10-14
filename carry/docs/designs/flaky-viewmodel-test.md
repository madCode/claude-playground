# CarryViewModelTest reddens CI at random

Built. What the fix actually took is at the bottom: the cancel this document
asked for was necessary and **not sufficient**.

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

## What it took, and the part this document had wrong

Twenty full-suite runs on `main` produced **no** failure. `forkEvery(1)` gives
the class its own JVM, so running only `CarryViewModelTest` is the same trial
at a third of the cost; thirty of those under CPU load reproduced it once, at
`resetMain()`, in one of the two tests that build two ViewModels. Load is the
lever: the race needs a resumption from an IO thread to be delayed past the
end of the test, which an idle machine rarely does.

**The registry and the cancel alone did not fix it** -- thirty runs of the
same probe failed once again, at the same line. Cancelled is not finished. The
last thing `refresh` does is resume from `withContext(Dispatchers.IO)`, and
that resumption *reads* `Dispatchers.Main` from the IO thread. The blocking
read it is parked in cannot be interrupted, so cancelling stops the write-back
but not the dispatch that carries it, and the dispatch still lands wherever it
lands -- including after `resetMain`.

So teardown cancels **and then waits**: it drives the test dispatcher until
every registered job reports `isCompleted`, which puts that last dispatch
before `resetMain` instead of racing it, and fails loudly after ten seconds if
a job cannot be drained at all.
