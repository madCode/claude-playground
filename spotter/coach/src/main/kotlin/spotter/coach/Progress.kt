package spotter.coach

import java.time.LocalDate

/**
 * What has moved, and what is about to (HANDOFF.md: "See progress: what has moved, and what is
 * close to a bump"). Read from the same history the sheet is, and by the same rules, so it never
 * says anything the next sheet won't bear out.
 */
data class Progress(val wentUp: List<WentUp> = emptyList(), val next: List<Next> = emptyList(), val advice: Advice = Advice()) {
    /**
     * Whether the sheet's numbers are working (HANDOFF.md: "are the suggested weights
     * completed?"), over the rows logged lately that the sheet had a number for. A count of
     * what happened, with no score: the scorecard does the grading, on the export.
     *
     * Rows the sheet had no number for aren't counted: an entry from before the log kept the
     * sheet's number looks the same as one, and would be called "no number" untruly.
     */
    data class Advice(
        /** Lifted at the sheet's number, every set reaching what was asked. */
        val asSuggested: Int = 0,
        /** Lifted heavier than the sheet's number (its "try" weight included: the log doesn't keep that). */
        val heavier: Int = 0,
        /** Lifted lighter than the sheet said, or at its number and short, stopped or with a twinge. */
        val lighterOrShort: Int = 0,
    ) {
        val numbered: Int get() = asSuggested + heavier + lighterOrShort
    }

    /** A new best: heavier than every clean session before it at the same reps, and clean. */
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
            // Sessions on one date keep the order they were logged in. An exercise the sheet
            // gives no weight (a banded or bodyweight one an import still has a number for)
            // has no number to move. Wordings that share a weight are one exercise here, as
            // they are to the coach, or one step would be listed once for each.
            val weighted = log.filter { it.judged && Sheet.wantsWeight(it.exercise) }
            // Each wording's history as the coach reads it; wordings that end on the same
            // session are one exercise. Not by [Reading.Known.weightKey] alone: the coach keeps
            // "alternating" apart from a session with one dumbbell, and so must the card.
            val byExercise = weighted.map { it.exercise }.distinctBy { it.key }
                .map { exercise -> Progression.own(exercise, weighted) }.filter { it.isNotEmpty() }.distinctBy { it.last() }
            val wentUp = byExercise.mapNotNull { sessions -> wentUp(sessions, today) }
                .sortedByDescending { it.on }.take(MOST)
            // Only what was lifted lately: an exercise last seen in the spring is ready too, and
            // a list of those would bury the ones coming up. The most recent first.
            val next = byExercise.map { it.last() }.filter { !it.date.isBefore(today.minusWeeks(WEEKS)) }.sortedByDescending { it.date }.mapNotNull { last ->
                val s = Progression.suggest(last.exercise, last.target, log) as? Suggestion.Weight
                    ?: return@mapNotNull null
                when {
                    s.change == Change.UP && s.caution == null -> Next(last.key, s.load, last.firstTarget, ready = true)
                    // Promised only where the count is known: a session logged next with its
                    // count said starts the two afresh against one whose count nobody said.
                    s.oneMore && last.load.count != null -> Next(last.key, s.load, last.firstTarget, ready = false)
                    else -> null
                }
            }.sortedBy { !it.ready }.take(MOST)
            return Progress(wentUp, next)
        }

        /**
         * What became of the sheet's numbers for the rows logged on the phone in the last
         * [WEEKS] weeks. Left out, because the two can't fairly be set side by side: a row the
         * person added (the sheet never showed it), an easier version (not that exercise's
         * set), a ladder logged by its heaviest round against a number for its first, a lift
         * with another number of dumbbells than the sheet said, and a row since corrected onto
         * other words or reps than the ones its number was shown for.
         */
        fun advice(entries: List<JournalEntry>, vocabulary: Vocabulary, today: LocalDate): Advice {
            var asSuggested = 0
            var heavier = 0
            var lighterOrShort = 0
            val byId = entries.associateBy { it.id }
            // The entry a row was first logged as: a correction keeps the number shown then.
            fun first(e: JournalEntry) = generateSequence(e) { c -> c.target?.takeIf { c.kind == JournalEntry.Kind.CORRECTED }?.let(byId::get) }.take(entries.size + 1).last()
            for ((e, logged) in Journal.read(entries, vocabulary)) {
                if (e.date.isBefore(today.minusWeeks(WEEKS)) || e.date > today || Sheet.isAdded(e.row) || e.easier || !Sheet.wantsWeight(logged.exercise)) continue
                val shown = e.shownLb ?: continue
                // Still the exercise and reps its number was shown for (a poster's change of a
                // capital isn't another row); and a log carried to another board's row and
                // given another weight there answers that row's number, which isn't kept.
                val asFirst = first(e).let {
                    it.key == e.key && it.targetLow == e.targetLow && it.targetHigh == e.targetHigh && (it.row == e.row || it.lb == e.lb)
                }
                when {
                    !asFirst || logged.heaviestOnly || (e.shownCount != null && logged.load.count != null && e.shownCount != logged.load.count) -> Unit
                    logged.load.lb > shown -> heavier++
                    logged.load.lb < shown || !done(logged) -> lighterOrShort++
                    else -> asSuggested++
                }
            }
            return Advice(asSuggested, heavier, lighterOrShort)
        }

        // Every set reached what was asked (the low end of a range counts: it was asked for).
        private fun done(l: Logged) = (l.outcome == Outcome.CLEAN || l.outcome == Outcome.IN_RANGE) && !l.faded

        // A new best: the latest session, clean, heavier than every clean session before it at
        // the same reps and the same number of dumbbells. Other reps, or one dumbbell against
        // two, aren't the same lift, and a count nobody said could be either; coming back to a
        // weight held before isn't going up; and a timed set has no reps to say it was "for".
        private fun wentUp(sessions: List<Logged>, today: LocalDate): WentUp? {
            val latest = sessions.last()
            if (latest.date.isBefore(today.minusWeeks(WEEKS)) || !sound(latest) || latest.anchor == null || latest.load.count == null) return null
            val before = sessions.filter { it.date < latest.date && sound(it) && it.anchor == latest.anchor && it.load.count == latest.load.count }
            val best = before.maxByOrNull { it.load.lb } ?: return null
            return if (latest.load.lb > best.load.lb) WentUp(latest.key, best.load, latest.load, latest.anchor, latest.date) else null
        }

        // Every set at the top of what was asked, and a ladder's first weight kept: "in range"
        // would say "for 10" of a set done for 8.
        private fun sound(l: Logged) = l.outcome == Outcome.CLEAN && !l.faded
    }
}
