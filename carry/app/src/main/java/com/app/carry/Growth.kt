package com.app.carry

/**
 * What the folder adds up to.
 *
 * The folder is the history: one file per session, written once by the
 * runner and completed once here, plus back-filled records for the days
 * logged before this app existed. Nothing keeps a separate rolling total,
 * so there is no second copy to drift from the first — the sum is computed
 * from the files every time it is asked for.
 *
 * **It derives, it never coaches.** Totals, maxima and counts are facts
 * about rows that are already in the files. A streak is not: it comes from
 * a rule about what earns a bump, that rule lives in the runner, and a
 * second implementation here would eventually disagree with it about
 * someone's training. So [Since.nearBump] carries the runner's own
 * `streak_done`/`streak_needed` through and computes nothing.
 */
object Growth {

    /** One weighted set that actually happened. */
    data class Lift(
        val date: String,
        val name: String,
        val family: String?,
        val weight: Double,
        val reps: Int?,
    )

    /** The heaviest thing in a family, and the weights leading to it. */
    data class Peak(val family: String, val best: Lift, val trail: List<Double>)

    /** One day, as it would be read back months later. */
    data class Day(
        val date: String,
        val classTag: String?,
        val lifts: Int,
        /** The heaviest thing done that day, which is what a person
         *  remembers a session by. Null on a day of only bodyweight. */
        val best: Lift?,
    )

    /** A row the runner says is close to earning more weight. */
    data class Bump(val name: String, val family: String?, val weight: Double, val done: Int, val needed: Int)

    data class Since(
        val sessions: Int,
        val sets: Int,
        val from: String?,
        val to: String?,
        val peaks: List<Peak>,
        val nearBump: List<Bump>,
        /** Newest first: "what did I do on the 27th" is a question about a
         *  day, and the screen has no answer for it without this. */
        val days: List<Day>,
        /**
         * Distinct movements, and how many have been done more than once
         * at the same variant. The second is why the coach is so often
         * quiet, and showing the first without it would read as an
         * accusation.
         *
         * Null when any lifted set came from a file with no `key` on it.
         * The runner decides what counts as the same exercise; a count
         * assembled where that is unknown would be a confident wrong
         * number on the one screen whose job is to explain why the
         * numbers are thin.
         */
        val movements: Int?,
        val repeated: Int?,
    ) {
        val empty: Boolean get() = sets == 0
    }

    /**
     * @param files the raw text of every session file in the folder, in any
     *   order. Unreadable ones are skipped rather than failing the screen:
     *   a half-synced file is normal in a shared folder, and one bad file
     *   must not cost the owner the whole picture.
     */
    fun of(files: List<String>): Since {
        val sessions = files.mapNotNull { SessionFiles.parse(it) }
        // A set counts only if a weight was actually recorded. A blank row
        // is not a zero, and `weight: 0` in the oldest records meant both
        // "bodyweight" and "skipped" — neither is a lift.
        val lifts = sessions.flatMap { session ->
            session.sets.filter { !it.skipped && (it.weight ?: 0.0) > 0.0 }
                .map { Lift(session.date, it.displayName, it.family, it.weight!!, it.reps) }
        }
        // A sheet nobody filled in is not a session they did. One of those
        // was sitting in the real folder — eleven blank rows from an early
        // test — and it counted as a session AND contributed eleven
        // movements to the very figure that explains why the coach is
        // quiet. A stat that counts what was never done is worse than none.
        val logged = sessions.filter { s -> s.sets.any { it.weight != null || it.reps != null || it.skipped } }
        val dates = logged.map { it.date }.filter { it.isNotBlank() }.sorted()

        val peaks = lifts.filter { it.family != null }
            .groupBy { it.family!! }
            .map { (family, inFamily) ->
                Peak(
                    family = family,
                    best = inFamily.maxBy { it.weight },
                    // Oldest to newest, so the bar on the right is now.
                    trail = inFamily.sortedBy { it.date }.map { it.weight }.takeLast(MAX_TRAIL),
                )
            }
            .sortedByDescending { it.best.weight }

        // Only from the newest sheet: a streak is a statement about where
        // the owner stands TODAY, and an older file's is a fact about a
        // day that has already been and gone.
        val newest = sessions.maxByOrNull { it.date }
        val nearBump = newest?.sets.orEmpty().mapNotNull { set ->
            val needed = set.streakNeeded ?: return@mapNotNull null
            val done = set.streakDone ?: 0
            val weight = set.suggestedWeight ?: return@mapNotNull null
            if (done <= 0 || done >= needed) null
            else Bump(set.displayName, set.family, weight, done, needed)
        }.sortedByDescending { it.done.toDouble() / it.needed }

        // Over what was actually LIFTED, not over every row on every sheet.
        // Grouped by the runner's own key, or not at all: all of them carry
        // one or the count is withheld.
        val lifted = logged.flatMap { s -> s.sets.filter { !it.skipped && (it.weight ?: 0.0) > 0.0 } }
        val counts = lifted.mapNotNull { it.key }
            .takeIf { it.size == lifted.size }
            ?.groupingBy { it }?.eachCount()

        val days = logged.map { session ->
            val mine = lifts.filter { it.date == session.date }
            Day(
                date = session.date,
                classTag = session.classTag,
                lifts = mine.size,
                best = mine.maxByOrNull { it.weight },
            )
        }.sortedByDescending { it.date }

        return Since(
            sessions = logged.size,
            sets = lifts.size,
            from = dates.firstOrNull(),
            to = dates.lastOrNull(),
            peaks = peaks,
            nearBump = nearBump,
            days = days,
            movements = counts?.size,
            repeated = counts?.count { it.value > 1 },
        )
    }


    /** Enough bars to show a direction; more is a chart, and six sessions
     *  do not support one. */
    private const val MAX_TRAIL = 8
}
