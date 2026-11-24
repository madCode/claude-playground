package spotter.coach

import java.time.LocalDate

/**
 * One exercise as logged in one class: what was held, what was asked, and how each set went.
 * [reps] are reps a side, one entry per set; "✓ as listed" logs the target for every set, and a range the count tapped.
 */
data class Logged(
    val date: LocalDate,
    val exercise: Reading.Known,
    val load: Load,
    /** Reps a side the line asked for (a range, or one number); null for a timed set. */
    val target: IntRange?,
    val reps: List<Int>,
    /** A twinge or pain. Such a set never raises a number (trap 3). */
    val hurt: Boolean = false,
    val formBroke: Boolean = false,
    val stopped: Boolean = false,
    /** A ladder's rounds ("8 / 6 / 4"), each set judged against its own; [target] is ignored. */
    val ladder: List<Int>? = null,
    val hard: Boolean = false,
    /** Said to have felt easy: reason enough to go up after one clean session. */
    val easy: Boolean = false,
    /**
     * A ladder logged with one weight, the heaviest of its rounds, with nobody having said what
     * each round held: it can hold the number but never raise it, since the number is round one's.
     */
    val heaviestOnly: Boolean = false,
    /**
     * A ladder whose weight came down in a later round of no more reps than the first: round
     * one's weight wasn't kept, which is as good as "felt hard".
     */
    val faded: Boolean = false,
) {
    val key: String get() = exercise.key

    /** Reps a side asked of the first set, for converting between rep counts; null when timed. */
    val firstTarget: Int? get() = ladder?.first() ?: target?.last

    /**
     * The reps the logged weight stands for, when telling one rep count's history from another's:
     * what was asked, or the fewest-rep round of a ladder logged with its heaviest weight.
     */
    val anchor: Int? get() = if (ladder != null && heaviestOnly) ladder.min() else firstTarget

    /**
     * The reps the weight is known to have been done for, for converting between rep counts: the
     * fewest of the sets logged, or what a ladder's first round asked. Null when timed.
     */
    val repsDone: Int?
        get() = when {
            // The heaviest round is the one with the fewest reps: that is what the weight was held for.
            ladder != null -> if (heaviestOnly) ladder.min() else ladder.first()
            // More reps than asked are no sounder a basis than what was asked.
            else -> reps.minOrNull()?.takeIf { target != null }?.let { minOf(it, target!!.last) }
        }

    /** Whether there is anything to judge: a rep target with no reps logged says nothing. */
    val judged: Boolean get() = reps.isNotEmpty() || (target == null && ladder == null)

    // The first-set rule needs sets to compare. One set logged is the heaviest, often from a
    // later, tired round (the Log screen's one number), so it holds unless three or more short:
    // madCode's call, 2026-11-22.
    private val missed: Boolean
        get() = stopped || formBroke || shortBy().let { short ->
            (short.size > 1 && short.first() > 0) || short.any { it >= 3 }
        }

    // How many reps short each set was of its own target's low end.
    private fun shortBy(): List<Int> = reps.mapIndexed { i, r ->
        val asked = ladder?.let { it.getOrElse(i) { _ -> it.last() } } ?: target?.first ?: return emptyList()
        maxOf(0, asked - r)
    }

    /** How the session went, by the rules in DESIGN.md ("Progression"). */
    val outcome: Outcome
        get() = when {
            hurt -> Outcome.HURT
            missed -> Outcome.MISS
            shortBy().any { it > 0 } -> Outcome.NEAR_MISS
            ladder != null || target == null -> Outcome.CLEAN
            reps.all { it >= target.last } -> Outcome.CLEAN
            // Every set in the range but not at its top: hold until the top is reached.
            else -> Outcome.IN_RANGE
        }

    /** A hurt set that was also short or stopped: what hurt was already too heavy. */
    val hurtAndMissed: Boolean get() = hurt && missed
}

enum class Outcome { CLEAN, IN_RANGE, NEAR_MISS, MISS, HURT }

/** Against the logged session the number rests on. */
/** [EST]: never logged in these words, estimated from a version of the lift that was. */
enum class Change { UP, SAME, DOWN, NEW, EST }

/** What the sheet shows for a row. */
sealed interface Suggestion {
    /**
     * A weight, why, and how it compares with last time. [test] is a heavier dumbbell to try
     * when the next step is a big jump ("12 · try 15 if set 1 felt easy").
     */
    data class Weight(
        val load: Load,
        val change: Change,
        val reasons: List<String>,
        val test: Load? = null,
        /** Said on the row before the number: pain last time. */
        val caution: String? = null,
        /** One more clean session at this weight and these reps goes up a dumbbell. */
        val oneMore: Boolean = false,
    ) : Suggestion

    /** No sound basis: [NEW], or why not. A gap beats a guess. */
    data class NoNumber(val reason: String, val new: Boolean = false) : Suggestion {
        companion object {
            /** Nothing logged for the exercise, and no sound link to one that has. */
            val NEW = NoNumber("new: go light", new = true)
        }
    }
}

/**
 * Suggests a weight for one row from the log: straight sets with 1–2 reps in reserve, up a
 * dumbbell after a clean session, hold after a near miss or a twinge, down a step after a miss.
 * See DESIGN.md, "Progression".
 */
/**
 * Rules the replay tool can switch, to grade the coach both ways on the scorecard (DESIGN.md,
 * "The scorecard decides"). The defaults are what the app runs.
 */
data class Rules(
    /** Wordings that leave the weight as it is share one history ([Reading.Known.weightKey]). */
    val shared: Boolean = true,
    /** A lift never logged in these words takes an estimate from a near version of it ([Estimated]). */
    val estimates: Boolean = true,
    /** The day the sheet is for, so a step earned long before it can wait; null where no day is known. */
    val today: java.time.LocalDate? = null,
)

object Progression {
    /**
     * Whether [basis] is the second clean session running at its weight: an earlier day's session
     * at the same reps and count was clean at this weight or more, with nothing but clean
     * sessions since, at any reps.
     *
     * An earlier day, because the same exercise twice in one class is one session's evidence.
     * This weight or more, because a weight done clean before isn't new when it is come back to;
     * one lifted above the sheet's number is. But a heavier weight only vouches for a lighter one
     * for [COMEBACK_WEEKS]: after that long it says little about today (TRAINING.md, 9), and the
     * lighter weight earns its step with two sessions of its own.
     */
    private fun settled(basis: Logged, own: List<Logged>): Boolean {
        val earlier = own.takeWhile { it !== basis }
        val before = earlier.lastOrNull { it.anchor == basis.anchor && it.date < basis.date } ?: return false
        val between = earlier.dropWhile { it !== before }.drop(1)
        val recent = before.load.lb == basis.load.lb || !before.date.isBefore(basis.date.minusWeeks(COMEBACK_WEEKS))
        return before.outcome == Outcome.CLEAN && before.load.count == basis.load.count && before.load.lb >= basis.load.lb && recent &&
            between.all { it.outcome == Outcome.CLEAN || it.outcome == Outcome.IN_RANGE }
    }

    /**
     * How long a heavier weight done clean still vouches for a lighter one (madCode, 2026-11-23),
     * and how long an earned step up stays good to take.
     */
    private const val COMEBACK_WEEKS = 8L

    /** A step more than this much heavier is a big jump: the sheet holds and offers a test. */
    private const val BIG_JUMP = 1.2

    /** One leg from two (trap 10): well under the two-legged weight. */
    private const val ONE_LEG = 0.6
    private const val STAGGERED = 0.8

    // Epley's estimate is only sound for sets of 3 to 12 reps.
    private val epleyReps = 3..12

    /**
     * The weight for a row of [exercise] asking [target] reps a side (the first round, for a
     * ladder; null when timed), from [log] in the order it was logged.
     */
    fun suggest(exercise: Reading.Known, target: IntRange?, log: List<Logged>, rules: Rules = Rules()): Suggestion {
        // Sessions on one date keep the order they were logged in.
        val own = own(exercise, log, rules)
        if (own.isEmpty()) return borrow(exercise, target, log, rules)

        val last = own.last()
        // madCode: a twinge holds the weight and notes it. With nothing but twinges logged, the
        // latest one is the basis, and the cap below holds it there or a step under.
        val sound = own.filter { it.outcome != Outcome.HURT }
        val to = target?.last
        // Each rep count keeps its own history (TRAINING.md, 5): the last time these reps were
        // asked is a better basis than a conversion from other reps, which is rounded down each
        // time and would walk the number down on a board that alternates 8s and 10s. But only
        // while the latest session bears it out: a set missed since, at any reps, puts the
        // latest session in charge (trap 4), and so does a latest weight lighter than this one
        // predicts for its reps, which is how a number from long ago would come back too heavy.
        val latest = sound.lastOrNull()
        val sameReps = sound.lastOrNull { it.anchor == to }
        val since = sound.dropWhile { it !== sameReps }.drop(1)
        // Null when the two can't be compared (timed, or reps Epley doesn't cover).
        val borneOut: Boolean? = if (sameReps == null || latest == null || latest === sameReps) true else {
            val a = sameReps.anchor
            val b = latest.anchor
            if (a == null || b == null || a !in epleyReps || b !in epleyReps) null
            else latest.load.lb >= (convert(sameReps.load.lb, a, b) ?: 0)
        }
        val basis = when {
            latest == null -> last
            sameReps != null && borneOut != false && since.none { it.outcome == Outcome.MISS } -> sameReps
            else -> latest
        }
        // No step up from an older session on the strength of one that can't be compared with
        // it, or that came up short.
        val noStep = basis !== latest && (borneOut == null || since.any { it.outcome == Outcome.NEAR_MISS })
        val reasons = mutableListOf<String>()
        var test: Load? = null
        var oneMore = false
        val held = basis.load.lb
        var lb = when {
            basis.outcome == Outcome.HURT -> held
            basis.outcome == Outcome.CLEAN && noStep ->
                held.also { reasons += "clean at ${basis.load.label} the last time these reps came up: same again" }
            basis.outcome == Outcome.CLEAN && basis.heaviestOnly ->
                held.also { reasons += "a ladder logged at ${basis.load.label}, its heaviest round: same again" }
            // Weak evidence against a step, so it delays one rather than cancels it (TRAINING.md, 3).
            basis.outcome == Outcome.CLEAN && basis.hard && basis.firstTarget != null ->
                held.also { reasons += "done at ${basis.load.label} but it felt hard: same again" }
            basis.outcome == Outcome.CLEAN && basis.faded ->
                held.also { reasons += "started at ${basis.load.label} and went lighter in a later round: same again" }
            // A timed set says nothing about reps in reserve.
            basis.outcome == Outcome.CLEAN && basis.firstTarget == null -> held.also { reasons += "a timed set at ${basis.load.label}: same again" }
            // One clean session isn't enough to go up on (madCode, 2026-11-22; TRAINING.md, 1): a
            // weight is settled by two clean sessions in a row at these reps, or by one that
            // felt easy. A weight lifted above the sheet's number is new, so it waits its two too.
            basis.outcome == Outcome.CLEAN && !basis.easy && !settled(basis, own) -> held.also {
                // Only promised where it is true: at these reps, with a step to take that isn't a
                // big jump, and nothing logged since (a twinge after it breaks the run).
                val up = Rack.up(held)
                reasons += if (basis.anchor == to && up != null && up <= held * BIG_JUMP && basis === own.last()) {
                    oneMore = true
                    "clean at ${basis.load.label} once: up after one more clean session, or one that felt easy"
                } else {
                    "clean at ${basis.load.label} once: same again"
                }
            }
            basis.outcome == Outcome.CLEAN -> {
                val up = Rack.up(held)
                when {
                    up == null -> held.also { reasons += "clean at ${basis.load.label}, the heaviest there is" }
                    up > held * BIG_JUMP -> held.also {
                        test = basis.load.copy(lb = up)
                        reasons += "clean at ${basis.load.label}; $up is a big jump, so try it only if set 1 feels easy"
                    }
                    else -> up.also { reasons += "clean at ${basis.load.label}: up a dumbbell" }
                }
            }
            basis.outcome == Outcome.IN_RANGE -> held.also { reasons += "${basis.load.label} in range, not yet at the top: same again" }
            basis.outcome == Outcome.NEAR_MISS -> held.also { reasons += "a near miss at ${basis.load.label}: same again" }
            else -> Rack.down(held)?.also { reasons += "missed at ${basis.load.label}: down a step" }
                ?: held.also { reasons += "missed at ${basis.load.label}, the lightest there is: same again" }
        }

        // A step earned long ago waits a session (madCode, 2026-11-24): after that long the
        // weight is held once first, and a clean session at it then takes the step.
        if (rules.today != null && basis.date.isBefore(rules.today.minusWeeks(COMEBACK_WEEKS)) && (lb > held || test != null)) {
            lb = held
            test = null
            val weeks = java.time.temporal.ChronoUnit.WEEKS.between(basis.date, rules.today)
            reasons.clear()
            reasons += "clean at ${basis.load.label}, but that was $weeks weeks ago: same again first, then up"
        }

        // ▲ ▼ = say what the rules decided, not what a change of reps converted to.
        var decided = lb
        // Said only if a twinge below doesn't take the offer away.
        var offer: String? = null
        // Different reps from last time: Epley, only where it holds, from the reps the weight
        // was done for. A timed set and a counted one don't convert either way. The estimate is
        // off by as much as a dumbbell step (TRAINING.md, 5), so it never rides on a step up:
        // it starts from the weight held, or the step down after a miss.
        // After a miss the step down is expected to reach what was asked, so it converts from that.
        val from = if (basis.outcome == Outcome.MISS) basis.anchor else basis.repsDone ?: basis.anchor
        if (basis.anchor != to) {
            // A step up earned at other reps isn't taken, but it isn't lost either: what that
            // dumbbell converts to is offered, to try if the first set feels easy (madCode,
            // 2026-11-23). A big jump is such a step too: its "try" was for those reps.
            val earned = if (lb > held) lb else test?.lb
            if (earned != null) {
                lb = held
                decided = held
                reasons.clear()
                reasons += "done at ${basis.load.label}"
            }
            if (from == null || to == null || from !in epleyReps || to !in epleyReps) {
                return Suggestion.NoNumber("last time was ${from?.let { "$it reps" } ?: "timed"} and this is ${to?.let { "$it reps" } ?: "timed"}: no sound conversion")
            }
            val converted = convert(lb, from, to)
                ?: return Suggestion.NoNumber("$to reps is lighter than the lightest dumbbell from last time's weight")
            if (converted != lb) reasons += "$from reps last time, $to now: $converted"
            lb = converted
            // Only when the earned dumbbell converts to more than the number: rounded down
            // like the number, it never offers more than was earned. And one dumbbell above
            // the number at most: to far fewer reps the estimate climbs faster than a first
            // set should.
            val next = Rack.up(converted)
            test = earned?.let { convert(it, from, to) }?.takeIf { it > converted && next != null }?.let { minOf(it, next!!) }?.let { up ->
                offer = "a step up was earned at $from reps: try $up if set 1 feels easy"
                basis.load.copy(lb = up)
            }
        }

        // The latest set that hurt, since the last sound one, caps the number at what hurt, or
        // under it if it was also a miss (trap 3). Last, so no step above can lift the number
        // past it. The latest, not the lowest: a twinge at 20 followed by a twinge at 25 done in
        // full holds 25. A sound session after a twinge clears it.
        var caution: String? = null
        var against = held
        own.lastOrNull { it.outcome == Outcome.HURT && (latest == null || !it.date.isBefore(latest.date)) }?.let { hurt ->
            var cap = if (hurt.hurtAndMissed) {
                Rack.down(hurt.load.lb) ?: return Suggestion.NoNumber("it hurt at the lightest dumbbell: no number")
            } else {
                hurt.load.lb
            }
            // What twinged at 8 reps is too much for 12: the cap converts down, never up.
            val hurtReps = hurt.anchor
            if (hurtReps != null && to != null && to > hurtReps && hurtReps in epleyReps && to in epleyReps) {
                cap = convert(cap, hurtReps, to) ?: return Suggestion.NoNumber("it hurt at the lightest dumbbell: no number")
            }
            decided = minOf(decided, cap)
            lb = minOf(lb, cap)
            // Said whether or not the cap bit: the person should know before picking it up.
            reasons += "it twinged at ${hurt.load.label}, so ${if (hurt.hurtAndMissed) "under that" else "no higher"}"
            caution = "it twinged last time at ${hurt.load.label}"
            against = hurt.load.lb
            // Never offer to try what hurt, or more.
            if ((test?.lb ?: 0) >= cap) test = null
        }

        // An offer made on a conversion goes with any twinge since, as "one more" does.
        if (offer != null && caution != null) test = null
        if (test != null) offer?.let { reasons += it }
        val load = basis.load.copy(lb = lb)
        // Against the session the number rests on, so the arrow agrees with the reason; after a
        // twinge, against what twinged, so holding there never reads as "up".
        val change = when {
            decided > against -> Change.UP
            decided < against -> Change.DOWN
            else -> Change.SAME
        }
        // Said whenever another wording had a say, whether as the basis or as one of the two
        // clean sessions, so the number can be checked against the log.
        if (own.any { it.key != exercise.key }) reasons += "counted with its other wordings, which take the same dumbbells"
        // A twinge since holds the weight whatever was promised.
        return Suggestion.Weight(load, change, reasons, test, caution, oneMore && caution == null)
    }

    /**
     * Whether [logged] is another wording of [exercise] at the same weight. Across "alternating"
     * only with a dumbbell in each hand, or where the words say how it is held: one dumbbell may
     * have been held in both hands, and that weight in one arm would be double; a count nobody
     * said could be either.
     */
    private fun shares(logged: Logged, exercise: Reading.Known): Boolean {
        if (logged.exercise.weightKey != exercise.weightKey) return false
        fun alternating(e: Reading.Known) = e.parts.map { it.variations["alternating"] }
        // Where the words fix the count ("single arm", "goblet"), both wordings have them.
        return alternating(logged.exercise) == alternating(exercise) || logged.load.count == 2 || Sheet.rowCount(logged.exercise) != null
    }

    /** The sessions [exercise]'s number is read from, oldest first: its own, and its shared wordings'. */
    fun own(exercise: Reading.Known, log: List<Logged>, rules: Rules = Rules()): List<Logged> =
        log.filter { it.judged && (it.key == exercise.key || (rules.shared && shares(it, exercise))) }.sortedBy { it.date }

    /** [lb] done for [from] reps as a weight for [to], rounded down to the rack; null where Epley doesn't hold. */
    internal fun converted(lb: Int, from: Int, to: Int): Int? = if (from in epleyReps && to in epleyReps) convert(lb, from, to) else null

    private fun convert(lb: Int, from: Int, to: Int): Int? = Rack.atMost(lb * (1 + from / 30.0) / (1 + to / 30.0))

    /**
     * A new exercise borrows only through a stated factor: a one-legged version of a two-legged
     * lift you've done, at 0.6 (trap 10). A loose relative is no basis (trap 11): no number.
     */
    private fun borrow(exercise: Reading.Known, target: IntRange?, log: List<Logged>, rules: Rules): Suggestion {
        val part = exercise.parts.singleOrNull() ?: return Suggestion.NoNumber.NEW
        val oneLeg = oneLeg(part, target, log, rules)
        if (oneLeg is Suggestion.Weight || !rules.estimates) return oneLeg
        return similar(exercise, part, target, log, rules) ?: oneLeg
    }

    /**
     * An estimate for a lift never logged in these words, from a version of it that differs
     * only by tweaks that leave the weight about as it is, or from a movement filed apart that
     * is the same lift done another way ([Estimated]): the weight that version last held,
     * never a step up it has earned, and nothing from one that twinged.
     */
    private fun similar(exercise: Reading.Known, part: Part, target: IntRange?, log: List<Logged>, rules: Rules): Suggestion? {
        // Logged in these words already, judged or not: what was held then stands, not an estimate.
        if (log.any { it.key == exercise.key || (rules.shared && it.exercise.weightKey == exercise.weightKey) }) return null
        val mine = weighing(part, rules).entries
        fun tweaks(p: Part) = weighing(p, rules).entries.let { theirs -> (mine - theirs) + (theirs - mine) }
        fun own(p: Part) = p.movement == part.movement
        val oneArm = part.variations["arms"] == "single"
        val near = log.filter { logged ->
            val p = logged.exercise.parts.singleOrNull() ?: return@filter false
            val tweaks = tweaks(p)
            // A row the sheet gives no weight (pulses with nothing held) has none to lend.
            logged.judged && Sheet.wantsWeight(logged.exercise) && Estimated.kin(part.movement, p.movement) != null && (tweaks.isNotEmpty() || !own(p)) &&
                tweaks.all { Estimated.factor(part.movement, it.key, it.value) != null } &&
                // A version held another way, with no stated multiple across, isn't near at all:
                // it mustn't stand in the way of one that is. Nor a stronger grip the row lacks.
                Estimated.holds(part, Estimated.hold(p)) &&
                (weighing(p, rules).entries - mine).none { Estimated.stronger(part.movement, it.key, it.value) } &&
                // Two arms from one only where the arm is the limit: on a lunge it would double
                // what the legs move, and a swing or pullover is one dumbbell in both hands.
                (oneArm || p.variations["arms"] != "single" || Estimated.armLimited(part.movement)) &&
                // As between shared wordings: a side at a time only where the count is known.
                (p.variations["alternating"] == part.variations["alternating"] || logged.load.count == 2 || Sheet.rowCount(logged.exercise) != null)
        }
        // The nearest versions; where two are as near, the lighter estimate. One of them with
        // nothing sound to lend (it twinged, or nobody said how it was held) leaves no number:
        // the other's would be the heavier of the two.
        // The lift's own versions first, however worded: another movement is looked at only
        // when none of its own is near, so a kin can neither outweigh nor blank what the
        // lifter's own log says.
        val candidates = near.filter { own(it.exercise.parts.single()) }.ifEmpty { near }
        val fewest = candidates.minOfOrNull { tweaks(it.exercise.parts.single()).size } ?: return null
        val nearest = candidates.filter { tweaks(it.exercise.parts.single()).size == fewest }.map { it.exercise }.distinctBy { it.key }
            .map { from(it, part, target, log, rules) ?: return null }
        return nearest.minByOrNull { it.load.lb }
    }

    private fun from(source: Reading.Known, part: Part, target: IntRange?, log: List<Logged>, rules: Rules): Suggestion.Weight? {
        val from = suggest(source, target, log, rules) as? Suggestion.Weight ?: return null
        if (from.caution != null) return null
        val theirs = source.parts.single()
        val sessions = own(source, log, rules).filter { it.outcome != Outcome.HURT }
        val oneArm = part.variations["arms"] == "single"
        val arms = oneArm != (theirs.variations["arms"] == "single")
        val (mine, held) = Estimated.hold(part) to Estimated.hold(theirs)
        // How the weight is held, and what that does to the number. Only ever from a session
        // that says how many dumbbells it was: a count nobody said could be either.
        val (count, hands) = when {
            // One arm from two, or two from one: the dumbbell in each hand is the same one.
            arms -> when {
                mine != held -> return null
                oneArm -> (if (from.load.count == 2) 1 else return null) to 1.0
                else -> (if (from.load.count == 1) 2 else return null) to 1.0
            }
            mine == held -> from.load.count to 1.0
            // One dumbbell in both hands from one in each, and back: a stated multiple.
            mine == 1 -> 1 to (Estimated.inBothHands(part)?.takeIf { from.load.count == 2 } ?: return null)
            // Only to a row that says two: a bare one could be done either way.
            held == 1 -> 2 to (Estimated.inBothHands(part)?.takeIf { mine == 2 && from.load.count == 1 }?.let { 1 / it } ?: return null)
            // "Two dumbbells" said of one side only: the same lift, if the other was two.
            else -> (if (from.load.count == 2) 2 else return null) to 1.0
        }
        // A session logged against its own words (a "two dumbbell" row done with one) lends nothing.
        if (Sheet.rowCount(Reading.Known(listOf(part), emptySet())).let { it != null && count != null && it != count }) return null
        // A tweak this version has and that one hasn't takes its factor. The other way round
        // the harder version's weight stands: dividing by a guess would make a heavier one.
        val added = (weighing(part, rules).entries - weighing(theirs, rules).entries)
            .fold(1.0) { f, (axis, value) -> f * (Estimated.factor(part.movement, axis, value) ?: return null) }
        // What was held at these reps, or the conversion from other reps: never a step up, and
        // never more than that version was ever lifted at.
        val last = sessions.lastOrNull { it.anchor == target?.last }?.load?.lb
        val most = sessions.maxOfOrNull { it.load.lb } ?: return null
        val stands = minOf(from.load.lb, last ?: from.load.lb, most)
        val factor = added * hands * (Estimated.kin(part.movement, theirs.movement) ?: return null)
        val lb = if (factor == 1.0) stands else Rack.atMost(stands * factor) ?: return null
        val said = "your ${Estimated.name(source)} stands at ${from.load.copy(lb = stands).label}"
        val why = if (factor == 1.0) "and this is a small change to it" else "and this starts at about ${"%.2f".format(java.util.Locale.ROOT, factor).trimEnd('0').trimEnd('.')} times that dumbbell"
        return Suggestion.Weight(Load(lb, count), Change.EST, listOf("an estimate: $said, $why"))
    }

    // The variations that bear on the weight: all of them, or without the shared wordings.
    private fun weighing(p: Part, rules: Rules) =
        if (rules.shared) p.variations.filterNot { SharedWeight.neutral(p.movement, it.key, it.value) } else p.variations

    private fun oneLeg(part: Part, target: IntRange?, log: List<Logged>, rules: Rules): Suggestion {
        // One leg is a factor on what the legs move; a curl on one leg is a balance, not a 0.6.
        if (!part.oneLeg || part.movement.pattern !in setOf("squat", "lunge", "hinge")) return Suggestion.NoNumber.NEW
        val twoLegged = log.filter { logged ->
            val p = logged.exercise.parts.singleOrNull() ?: return@filter false
            // The plain two-legged lift: a sumo or wide stance is a variation of its own.
            p.movement == part.movement && !p.oneLeg && p.variations["legs"] in setOf(null, "standing") &&
                weighing(p, rules) - "legs" == weighing(part, rules) - "legs"
        }
        // The plain lift before a "standing" one, then the most recent.
        val source = twoLegged.filter { it.judged }.sortedWith(compareBy({ it.exercise.parts.single().variations["legs"] == null }, { it.date })).lastOrNull()
            ?: return Suggestion.NoNumber.NEW
        val from = suggest(source.exercise, target, log, rules) as? Suggestion.Weight ?: return Suggestion.NoNumber.NEW
        // A two-legged lift that twinged is no basis for a first go on one leg.
        if (from.caution != null) return Suggestion.NoNumber.NEW
        // A staggered stance keeps the back foot down: more than one leg, less than two.
        val staggered = part.variations["legs"] == "staggered"
        val lb = Rack.atMost(from.load.lb * (if (staggered) STAGGERED else ONE_LEG)) ?: return Suggestion.NoNumber.NEW
        return Suggestion.Weight(
            from.load.copy(lb = lb),
            Change.EST,
            listOf("an estimate ${if (staggered) "in a staggered stance: about 0.8" else "on one leg: about 0.6"} of the ${from.load.label} your ${part.movement.name} would be next"),
        )
    }
}
