---
name: new-project
description: Start a new project with madCode - work out what it is together, set it up from practices/templates, then hand it to the cycles skill for a set time or a set amount of work. Use when madCode wants to start, set up or kick off a new project or app.
---

# Starting a project

Three phases, and only the last runs without madCode. The design is theirs to decide; the setup
and the cycles carry it out.

## 1. Work out what it is, together

A conversation, not a form: a question or two at a time, using what madCode has already said.
Choices with a few clear options go through `AskUserQuestion`. What has to be settled:

- **Who it's for and what they get** that they couldn't before.
- **The one rule worth keeping**, and why: "it never guesses a station", "a blank row is
  blank". The rule the rest of the design follows.
- **What it must never do**, and the worst wrong answer it could give.
- **Where it lives.** A folder in claude-playground, which is public, or its own repository.
  Anyone's own data, or facts about it, means a private repository for the data at least, as
  gym-coach keeps its record in gym-data, and a Privacy section saying what may be said in
  public.
- **What it runs on**: language, platform, and what a person does to use it.
- **The first slice**: the smallest thing madCode would actually use, and what comes after.
- **When the first run of cycles stops**: a date, a number of cycles, or outcomes ("Done
  when").

Read the existing projects for what can be reused (a CI workflow, the SDK install script, a
screenshot test) and say what you'd reuse.

Draft `docs/DESIGN.md` from [the template](../../../practices/templates/DESIGN.md) as you go
and show it. Building starts on madCode's clear yes to the design, given after they've seen it
([the loop, step 3](../../../practices/lifecycle.md#3-approval-is-explicit-or-it-hasnt-happened)).

## 2. Set it up

Follow [Starting a project](../../../practices/README.md#starting-a-project), the checklist this
skill carries out; don't keep a second copy of it here. Then:

- **CLAUDE.md** from the template: what it is, the one rule, commands, privacy. Add the
  personas a cycle's audit should take, and where its cycle log is if not `docs/CYCLES.md`.
- **BACKLOG.md**: *Done when* from the stop rule, *Next* in the order to build (the first slice
  first, each item small enough for one cycle), and the dates of the first recurring reviews.
- **DEVLOG.md**: the first entry, what was decided and why.
- The checklist is written for an Android app. For anything else, keep what each step is for
  (tests that use it the way a person would, a coverage floor, CI that runs only for the
  project's paths) and use the language's own tools.

One PR, `<Project>: start`, with the setup and the design. If it holds code, the fresh-eyes
review runs as for any change.

## 3. Hand it to the cycles

Propose the run and wait for a yes; a scheduled task works unattended in madCode's name:

- **Stop**: the stop rule from phase 1.
- **How often**: every few hours, so each cycle's CI has come back before the next.
- **Mix**: build and audit, about 60/40, or mostly build while the first slice is unbuilt.
- **Repositories** each run needs: the project's, claude-playground for the skills, and any
  data repository, read-only unless madCode says otherwise.

On a yes, create it with `create_trigger` (a fresh session on each run), with the short prompt
in the cycles skill's *Scheduling a run*, then edit the prompt to add the task's id. Or, if
madCode would rather watch the first one, run cycle 1 here with the cycles skill and schedule
the rest after.

Finish by telling madCode what was set up, when the first cycle runs, when the run stops, and
where to look: DEVLOG.md's status block, and the PRs.
