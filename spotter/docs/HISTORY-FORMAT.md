# gym-coach's `history.jsonl`, for the history import (step 4)

From the gym-coach session, 2026-11-22, at madCode's ask: the field names, types and allowed
values. Every value in the examples is invented; the real file stays on madCode's side and is
picked on the phone. Don't ask for it in a cycle.

## File

UTF-8 JSON Lines: one object per line, one line per logged exercise per class. Blank lines can
appear; skip them. Rows are in the order appended, roughly by date but not guaranteed. A backup
is named `history.jsonl.bak-YYYY-MM-DD`, which may be picked by mistake.

## Fields on every row

- `date`: "YYYY-MM-DD".
- `exercise`: the canonical movement name in gym-coach's knowledge base (the names in
  `carry/brain-fixtures/exercises.json`), or null on a combo row.
- `components`: a list of canonical names for a combo ("deadlift to row" is
  `["deadlift", "bent over row"]`), else null. No row has both.
- `line`: the board line as written; null on old rows.
- `variant`: a dict (below) or null. Null is an old row: the knowledge base's defaults.
- `weight`: number or null. **The marked weight of one dumbbell, never a total.** Int or float,
  and a float can be off the rack (22.5). Null is no weight.
- `reps`: int or null: what madCode reported, unadjusted. On "12 total" or "6 each" lines it is
  their own number: don't halve it (trap 7).
- `prescribed_reps`: int or null, what the board asked.
- `class`: "2G", "3G" or null.
- `source`: "form" (a logged sheet), "survey" (an older text reply) or "recovered".

## Fields on some rows (absent is false or null)

- `easy`, `hard`, `easier`, `hurt`, `skipped`, `done` (a timed or bodyweight row finished),
  `incomplete`: bool. `hurt` can be set on a row that wasn't done.
- `breakdown`: "fail", "knees", "modified", "half" or null. When set, the reps aren't
  trustworthy; gym-coach never uses such a row as a basis.
- `note`: free text, verbatim. Don't parse numbers from it.
- `added_by_owner`: true for a row that wasn't on the board. `matched_kb`: whether its name was
  recognised.
- `suggested`: what the sheet showed, as display text.
- `row_id`, `form_id`: strings. `survey_ts`: epoch seconds, on survey rows.

## `variant`

- `implement`: "single_dumbbell", "double_dumbbell", "ybell", "bodyweight" or null. **The only
  hint of the count:** double_dumbbell is 2; single_dumbbell or ybell is 1 (in both hands, or
  one hand when `arms` is "single"); null is unknown. Keep unknown as unknown; don't guess 2.
- `arms`: "both" or "single".
- `stance`: "both", "single", "split", "staggered" or "seated". gym-coach re-derives it from the
  movement's pattern on each read: don't trust "both" on a lunge or step-up (trap 10).
- `stability` ("stable" or a bosu value), `tempo` ("normal" or a tempo word), `position` ("none",
  "bench", "high_plank", …), `difficulty` ("none", "harder"), `intensity` ("none", "light"),
  `mode` ("reps"), `grip` ("none", "neutral", …; may be absent). Each is an open set of strings.
- `reps`: int or null (the number the maths used). `reps_text`: as written ("6 total",
  "10 each", "8/6/4"). `duration_sec`: int or null.
- `unknown`: words the parser didn't understand. Old rows may lack `grip`, `duration_sec` or
  `unknown`.

## Invented example rows

```
{"date": "2026-03-03", "exercise": "squat", "components": null, "line": "8 x goblet squat", "variant": {"stability": "stable", "stance": "both", "arms": "both", "implement": "single_dumbbell", "mode": "reps", "tempo": "normal", "position": "none", "difficulty": "none", "intensity": "none", "reps": 8, "reps_text": "8", "duration_sec": null, "grip": "none", "unknown": []}, "weight": 40, "reps": 8, "prescribed_reps": 8, "class": "2G", "source": "form", "row_id": "r-0001", "form_id": "f-0001"}
{"date": "2026-03-03", "exercise": "chest press", "components": null, "line": "8 x DB chest press", "variant": {"stability": "stable", "stance": "both", "arms": "both", "implement": "double_dumbbell", "mode": "reps", "tempo": "normal", "position": "bench", "difficulty": "none", "intensity": "none", "reps": 8, "reps_text": "8", "duration_sec": null, "grip": "none", "unknown": []}, "weight": 25.0, "reps": 7, "prescribed_reps": 8, "class": "2G", "source": "form", "hard": true}
{"date": "2026-03-05", "exercise": null, "components": ["deadlift", "bent over row"], "line": "10 x deadlift to row", "variant": {"stability": "stable", "stance": "both", "arms": "both", "implement": null, "mode": "reps", "tempo": "normal", "position": "none", "difficulty": "none", "intensity": "none", "reps": 10, "reps_text": "10", "duration_sec": null, "grip": "none", "unknown": []}, "weight": 22.5, "reps": 10, "prescribed_reps": 10, "class": "3G", "source": "survey", "survey_ts": 1772700000}
{"date": "2026-03-05", "exercise": "reverse lunge", "components": null, "line": "12 total x reverse lunge", "variant": {"stability": "stable", "stance": "split", "arms": "both", "implement": "double_dumbbell", "mode": "reps", "tempo": "normal", "position": "none", "difficulty": "none", "intensity": "none", "reps": 6, "reps_text": "12 total", "duration_sec": null, "grip": "none", "unknown": []}, "weight": 20, "reps": 12, "prescribed_reps": 6, "class": "3G", "source": "form", "hurt": true, "note": "knee twinge"}
{"date": "2026-03-08", "exercise": "shoulder press", "components": null, "line": null, "variant": null, "weight": 15, "reps": 10, "prescribed_reps": null, "class": null, "source": "survey", "breakdown": "fail"}
{"date": "2026-03-08", "exercise": "high plank", "components": null, "line": "45 sec high plank", "variant": {"stability": "stable", "stance": "both", "arms": "both", "implement": "bodyweight", "mode": "reps", "tempo": "normal", "position": "high_plank", "difficulty": "none", "intensity": "none", "reps": null, "reps_text": null, "duration_sec": 45, "grip": "none", "unknown": []}, "weight": null, "reps": null, "prescribed_reps": null, "class": "2G", "source": "form", "done": true}
{"date": "2026-03-08", "exercise": "skull crusher", "components": null, "line": "skull crushers", "variant": null, "weight": 12, "reps": 10, "prescribed_reps": null, "class": "2G", "source": "form", "added_by_owner": true, "matched_kb": true}
```
