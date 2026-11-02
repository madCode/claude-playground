# Brain fixtures

What gym-coach's Python coach does today, written down so this repository's
Kotlin port (`../brain`) can be held to it. The plan is
[docs/designs/brain-move.md](../docs/designs/brain-move.md); these are its
phase 1.

**Generated, never edited by hand.** gym-coach's `fixtures.py` writes them:

    uv run fixtures.py write --corpus <gym-data>/wod_cache/wods.jsonl --out <this folder>
    uv run fixtures.py check <this folder>    # does the Python still produce them?

A change to what the coach parses or suggests regenerates them, and the port
follows in the same change, until the Python is retired.

| File | What it holds |
|---|---|
| `boards.jsonl` | Every board in the corpus, cut to the lines the coach reads, with the rows the Python matches (`expected`) and what each line matches on its own (`per_line`). |
| `sheets.jsonl` | An invented lifter walking the corpus, one class a day: the sheet the coach writes from their history so far, their answers, and the history rows those become. Replay it in order. |
| `exercises.json` | The knowledge base, exactly as the Python's YAML loader read it, in its order. |
| `manifest.json` | Counts, the corpus's hash, the knowledge base's hash, and the lifter's stated baselines. |

**Public material only.** The board lines are from public r/orangetheory
workout posts, cut to the prescription lines; the full comments stay in
gym-coach. The lifter is invented, from a hash of each movement's name. No
one's own history, baselines or calendar is read to write these.
