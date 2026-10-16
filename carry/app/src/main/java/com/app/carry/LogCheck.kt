package com.app.carry

/**
 * What the companion importer would refuse, found on the phone, at the row.
 *
 * Mirrors `validate` and `_number_problems` in gym-coach's `session_file.py`:
 * a weight from 0 to 1000, reps a whole number from 0 to 1000, and an added
 * row holding anything needs a name. The importer refuses the WHOLE file
 * over one bad row, so a single typo like 2500 would cost every other number
 * typed that day, with nothing on the phone to say so.
 *
 * Nothing here clamps or drops a number. Turning 2500 into 1000 or into
 * blank is a different number from the one the owner typed; the row says
 * what is wrong and the save waits until they fix it.
 */
object LogCheck {
    const val MAX_WEIGHT = 1000.0
    const val MAX_REPS = 1000

    /** What is wrong with one row's numbers, as phrases ("weight 2500 is
     *  over 1000"); empty when the importer would take them. */
    fun row(weight: Double?, reps: Int?, fractionalReps: Double?): List<String> = listOfNotNull(
        when {
            // NaN is written as null, which the importer takes.
            weight == null || weight.isNaN() -> null
            weight > MAX_WEIGHT -> "weight ${weight.asWeight()} is over 1000"
            weight < 0 -> "weight ${weight.asWeight()} is below 0"
            else -> null
        },
        when {
            fractionalReps != null -> "reps ${fractionalReps.asWeight()} isn't a whole number"
            reps == null -> null
            reps > MAX_REPS -> "reps $reps is over 1000"
            reps < 0 -> "reps $reps is below 0"
            else -> null
        },
    )

    /** The line under a row, or null when the row is fine. */
    fun rowMessage(problems: List<String>): String? =
        problems.takeIf { it.isNotEmpty() }
            ?.joinToString(" and ")
            ?.replaceFirstChar { it.uppercase() }
            ?.let { "$it. Fix it to save." }

    /** Every row the save would be refused for, one entry per row, named. */
    fun problems(session: Session, entries: Map<Int, SessionPatch.Entry>, added: List<AddedSet>): List<String> {
        val sheet = session.sets.mapNotNull { set ->
            val e = entries[set.ref] ?: return@mapNotNull null
            row(e.weight, e.reps, e.fractionalReps).takeIf { it.isNotEmpty() }
                ?.let { "${set.displayName} ${it.joinToString(" and ")}" }
        }
        val mine = added.filterNot { it.blank }.mapNotNull { a ->
            val numbers = row(a.weight, a.reps, a.fractionalReps)
            when {
                a.name.isBlank() -> "an added row needs a name"
                numbers.isEmpty() -> null
                else -> "${a.name.trim().take(MAX_NAME)} ${numbers.joinToString(" and ")}"
            }
        }
        return sheet + mine
    }

    /** What the refused save says, or null when it may go ahead. Names the
     *  first few rows and counts the rest, so it stays one sentence. */
    fun summary(problems: List<String>): String? {
        if (problems.isEmpty()) return null
        val named = problems.take(MAX_NAMED).joinToString("; ")
        val rest = problems.size - MAX_NAMED
        val head = if (problems.size == 1) "1 row needs fixing" else "${problems.size} rows need fixing"
        return "$head: $named${if (rest > 0) "; and $rest more" else ""}. Nothing was saved."
    }

    private const val MAX_NAMED = 3
    private const val MAX_NAME = 40
}
