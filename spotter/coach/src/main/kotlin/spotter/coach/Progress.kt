package spotter.coach

import java.time.LocalDate

/**
 * What has moved, and what is about to (HANDOFF.md: "See progress: what has moved, and what is
 * close to a bump"). Read from the same history the sheet is, and by the same rules, so it never
 * says anything the next sheet won't bear out.
 */
data class Progress(val wentUp: List<WentUp> = emptyList(), val next: List<Next> = emptyList()) {
    /** Heavier than the clean session before it at the same reps, and clean again. */
    data class WentUp(val exercise: String, val from: Load, val to: Load, val reps: Int?, val on: LocalDate)

    /**
     * Where an exercise lifted lately stands for its next class at the reps it was last asked:
     * [ready] to go up to [load], or one clean session at [load] away from it.
     */
    data class Next(val exercise: String, val load: Load, val reps: Int?, val ready: Boolean)

    companion object {
        /** How far back "lately" goes. */
        const val WEEKS = 4L

        /** Enough for a glance; the rest is in the log. */
        const val MOST = 8

        fun of(log: List<Logged>, today: LocalDate): Progress {
            // Sessions on one date keep the order they were logged in.
            val byExercise = log.filter { it.judged }.sortedBy { it.date }.groupBy { it.key }
            val wentUp = byExercise.values.mapNotNull { sessions -> wentUp(sessions, today) }
                .sortedByDescending { it.on }.take(MOST)
            // Only what was lifted lately: an exercise last seen in the spring is ready too, and
            // a list of those would bury the ones coming up. The most recent first.
            val next = byExercise.values.map { it.last() }.filter { !it.date.isBefore(today.minusWeeks(WEEKS)) }.sortedByDescending { it.date }.mapNotNull { last ->
                val s = Progression.suggest(last.exercise, last.target, log) as? Suggestion.Weight
                    ?: return@mapNotNull null
                when {
                    s.change == Change.UP && s.caution == null -> Next(last.key, s.load, last.firstTarget, ready = true)
                    s.oneMore -> Next(last.key, s.load, last.firstTarget, ready = false)
                    else -> null
                }
            }.sortedBy { !it.ready }.take(MOST)
            return Progress(wentUp, next)
        }

        // The latest sound session against the one before it at the same reps and the same
        // number of dumbbells: other reps, or one dumbbell against two, aren't the same lift.
        private fun wentUp(sessions: List<Logged>, today: LocalDate): WentUp? {
            val latest = sessions.last()
            if (latest.date.isBefore(today.minusWeeks(WEEKS)) || !sound(latest)) return null
            val before = sessions.dropLast(1).lastOrNull {
                it.date < latest.date && sound(it) && it.anchor == latest.anchor && it.load.count == latest.load.count
            } ?: return null
            return if (latest.load.lb > before.load.lb) WentUp(latest.key, before.load, latest.load, latest.anchor, latest.date) else null
        }

        private fun sound(l: Logged) = l.outcome == Outcome.CLEAN || l.outcome == Outcome.IN_RANGE
    }
}
