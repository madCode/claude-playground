package com.app.carry

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Invented exercises and weights, for previews and tests. The repository is
 * public: nothing here comes from anyone's real training.
 */
object SampleSession {
    private fun set(
        ref: Int, name: String, family: String?, weight: Double?,
        previous: Double? = null, done: Int? = null, needed: Int? = null,
        suggested: String? = null,
        // What the board said, which is what a person reads. A real file
        // always carries it, so the sample does too -- a screenshot taken
        // without it shows a screen nobody will ever see. `prescribedReps`
        // stays the runner's math number: "10 total" is 5 a side.
        asked: String? = "10", seconds: Int? = null, reps: Int? = 10,
        related: String? = null, variant: JsonElement = JsonNull,
    ) = ExerciseSet(
        ref = ref, line = "${asked ?: seconds} x $name", canonical = name, variant = variant,
        prescribedReps = reps, prescribedText = asked, prescribedDurationSec = seconds,
        family = family, suggestedWeight = weight,
        previousWeight = previous, streakDone = done, streakNeeded = needed,
        suggested = suggested, related = related,
    )

    val today = Session(
        sheetId = "2026-10-17:2G",
        date = "2026-10-17",
        classTag = "2G",
        sets = listOf(
            set(0, "bench press", "push", 45.0, previous = 40.0),
            set(1, "single-arm row · split stance", "pull", 40.0, done = 2, needed = 3),
            set(2, "torso rotation · seated", "core", 30.0, done = 1, needed = 3),
            // The coach's variant, so the sheet shows what makes this one
            // harder than the plain move; "harder" is in the name already.
            set(3, "goblet squat · harder", "legs", 25.0, variant = buildJsonObject {
                put("implement", "single_dumbbell"); put("stance", "single")
                put("stability", "bosu"); put("difficulty", "harder")
            }),
            set(4, "deadlift", "legs", 25.0, done = 2, needed = 3, asked = "10 total", reps = 5),
            set(5, "bicep curl · seated", "combo", 15.0, asked = "6-12", reps = 6),
            set(6, "lateral raise", "push", null, asked = "8 each", reps = 8,
                related = "shoulder press 20 lb, 10/10"),
            set(7, "tricep extension · bench", "push", null),
            set(8, "squat", "legs", null, suggested = "no weight needed"),
            // The one timed row: a hold has no rep count, and how long is
            // the whole prescription.
            set(9, "sit-up", "core", null, suggested = "no weight needed",
                asked = null, reps = null, seconds = 45),
        ),
    )

    /**
     * A session as it reads back months later: some rows answered, one
     * skipped, two left blank, one note, and one row the owner added by
     * hand because the sheet never listed it.
     *
     * Kept as file TEXT rather than as a [Growth.Day], so anything reading
     * it goes through the same parse the folder does -- including `added`,
     * which is deliberately not part of [Session] and is read out of the
     * raw JSON. A fixture that skipped that would have proved the screen
     * works on rows the real path never produces.
     *
     * Invented exercises and invented weights; this repository is public.
     */
    val pastFile: String = """
        {
          "schema": "gym-session/1",
          "sheet_id": "2026-09-27:3G",
          "date": "2026-09-27",
          "class": "3G",
          "sets": [
            {"ref": 0, "line": "10 x bench press", "canonical": "bench press",
             "key": "bench-2db", "family": "push", "prescribed_text": "10",
             "prescribed_reps": 10, "variant": null,
             "weight": 45, "reps": 10, "easy": true},
            {"ref": 1, "line": "10 x single-arm row", "canonical": "single-arm row · split stance",
             "key": "row-1db", "family": "pull", "prescribed_text": "10",
             "prescribed_reps": 10, "variant": null,
             "weight": 40, "reps": 10},
            {"ref": 2, "line": "10 x torso rotation", "canonical": "torso rotation · seated",
             "key": "rotation-1db", "family": "core", "prescribed_text": "10",
             "prescribed_reps": 10, "variant": null},
            {"ref": 3, "line": "8 x goblet squat", "canonical": "goblet squat · harder",
             "key": "goblet-1db", "family": "legs", "prescribed_text": "8",
             "prescribed_reps": 8, "variant": null,
             "weight": 25, "reps": 8, "hard": true, "note": "last two were ugly"},
            {"ref": 4, "line": "10 total x deadlift", "canonical": "deadlift",
             "key": "deadlift-2db", "family": "legs", "prescribed_text": "10 total",
             "prescribed_reps": 5, "variant": null,
             "weight": 50, "reps": 5},
            {"ref": 5, "line": "8 each x lateral raise", "canonical": "lateral raise",
             "key": "lateral-2db", "family": "push", "prescribed_text": "8 each",
             "prescribed_reps": 8, "variant": null,
             "skipped": true},
            {"ref": 6, "line": "45 sec of low plank", "canonical": "low plank",
             "key": "plank-bw", "family": "core", "prescribed_duration_sec": 45,
             "variant": null, "easier": true},
            {"ref": 7, "line": "10 x tricep extension", "canonical": "tricep extension · bench",
             "key": "tricep-1db", "family": "push", "prescribed_text": "10",
             "prescribed_reps": 10, "variant": null}
          ],
          "added": [
            {"id": "c0ffee01", "name": "farmer carry", "weight": 35, "reps": 20, "hurt": true}
          ]
        }
    """.trimIndent()

    /** [pastFile] as the Since screen hands it to the detail view. */
    val past: Growth.Day get() = Growth.of(listOf(pastFile)).days.first()
}
