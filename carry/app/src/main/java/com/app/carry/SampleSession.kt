package com.app.carry

import kotlinx.serialization.json.JsonNull

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
    ) = ExerciseSet(
        ref = ref, line = "${asked ?: seconds} x $name", canonical = name, variant = JsonNull,
        prescribedReps = reps, prescribedText = asked, prescribedDurationSec = seconds,
        family = family, suggestedWeight = weight,
        previousWeight = previous, streakDone = done, streakNeeded = needed,
        suggested = suggested,
    )

    val today = Session(
        sheetId = "2026-10-17:2G",
        date = "2026-10-17",
        classTag = "2G",
        sets = listOf(
            set(0, "bench press", "push", 45.0, previous = 40.0),
            set(1, "single-arm row · split stance", "pull", 40.0, done = 2, needed = 3),
            set(2, "torso rotation · seated", "core", 30.0, done = 1, needed = 3),
            set(3, "goblet squat · harder", "legs", 25.0),
            set(4, "deadlift", "legs", 25.0, done = 2, needed = 3, asked = "10 total", reps = 5),
            set(5, "bicep curl · seated", "combo", 15.0, asked = "6-12", reps = 6),
            set(6, "lateral raise", "push", null, asked = "8 each", reps = 8),
            set(7, "tricep extension · bench", "push", null),
            set(8, "squat", "legs", null, suggested = "no weight needed"),
            // The one timed row: a hold has no rep count, and how long is
            // the whole prescription.
            set(9, "sit-up", "core", null, suggested = "no weight needed",
                asked = null, reps = null, seconds = 45),
        ),
    )
}
