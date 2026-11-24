package spotter.coach

/** One movement in an exercise, with its variations: one value per axis. */
data class Part(val movement: Movement, val variations: Map<String, String>) {
    /** Done on one leg: the movement always is (trap 10), or a stance word says so. */
    val oneLeg: Boolean
        get() = movement.oneLeg || variations["legs"] in setOf("single", "split", "staggered", "rear foot elevated")

    /** What is held, or null when the line doesn't say. A bare line isn't bodyweight (trap 6). */
    val implement: String? get() = variations["implement"]
}

/** What a line's words were read as. */
sealed interface Reading {
    /** Every word accounted for: one exercise, of one part or a combo of several. */
    data class Known(val parts: List<Part>, val hints: Set<String>) : Reading {
        /**
         * The exercise's identity, fixed when a row is logged: each movement with its variations
         * in a set order, parts joined by " + ". Intensity hints ("heavy") are not part of it.
         */
        val key: String get() = keyOf { true }

        /**
         * The history this exercise shares with its other wordings: [key] without the words that
         * leave the weight as it is (DESIGN.md, "Words that share a weight"). "Alt chest press",
         * "chest press" and "floor close grip chest press" are one history.
         */
        val weightKey: String get() = keyOf { (p, axis, value) -> !SharedWeight.neutral(p.movement, axis, value) }

        private fun keyOf(keep: (Triple<Part, String, String>) -> Boolean) = parts.joinToString(" + ") { p ->
            val vs = p.variations.toSortedMap().entries.filter { keep(Triple(p, it.key, it.value)) }.joinToString(", ") { "${it.key}=${it.value}" }
            if (vs.isEmpty()) p.movement.name else "${p.movement.name} [$vs]"
        }
    }

    /** Not read with confidence. It is shown and can be logged, but it borrows no number. */
    data class NotSure(val why: Why, val words: List<String>) : Reading
}

/**
 * The words that leave the dumbbells as they are, as madCode ruled on 2026-11-23. Each needs its
 * reason: a word that changes leverage or balance (one arm, one leg, a bosu, a tempo, an incline)
 * is not here, and keeps a history of its own. Nor is a grip: a board sets "close grip chest
 * press" beside "chest press", and the two are lifted at different weights.
 */
internal object SharedWeight {
    // Lifts done lying down, where the floor only shortens the range a bench allows. Not a
    // triceps extension: on a bench it is two dumbbells lying back, bare it is one overhead.
    private val lying = setOf("horizontal_push", "fly", "pullover")

    // Lifts the legs don't move: sitting or standing changes the balance, not the load.
    val upper = lying + setOf("vertical_push", "horizontal_pull", "vertical_pull", "curl", "raise", "triceps")

    // Presses the legs drive, whatever their pattern says.
    val legDriven = setOf("thruster", "push press", "clean and press")

    fun neutral(movement: Movement, axis: String, value: String): Boolean = when (axis) {
        // One side at a time, with the same dumbbell in each hand.
        "alternating" -> true
        // "Dumbbell" on a lift already done with dumbbells says nothing new.
        "implement" -> value == "dumbbell"
        // A bare "tricep extension" on these boards is the overhead one (madCode, 2026-11-24).
        "position" -> (value in setOf("floor", "bench") && movement.pattern in lying) ||
            (value == "overhead" && movement.name == "tricep extension")
        "legs" -> value in setOf("seated", "standing") && movement.pattern in upper && movement.name !in legDriven
        else -> false
    }
}

/**
 * Tweaks to a lift that leave the weight about as it is, as madCode ruled on 2026-11-24: "if
 * it's the same exercise with minor tweaks", the sheet gives a number. Unlike [SharedWeight]
 * these keep a history of their own once logged; before that, the nearest logged version lends
 * its weight as an estimate.
 */
internal object Estimated {
    private val pressed = setOf("horizontal_push", "fly")
    private val legs = setOf("squat", "hinge")

    private val arms = setOf("horizontal_push", "vertical_push", "horizontal_pull", "curl", "fly", "raise")

    /** A lift an arm limits, so two arms take the dumbbell one arm held; not a press the legs drive. */
    fun armLimited(movement: Movement) = movement.pattern in arms && movement.name !in SharedWeight.legDriven

    private val lying = setOf("horizontal_push", "fly", "pullover")

    /**
     * What a tweak does to the weight of a lift that has it, against the same lift without:
     * 1.0 is the same dumbbell. Null is a word that makes it another lift, with no estimate
     * across. The reasons and what stands behind each number are in TRAINING.md, 10 to 12; a
     * tweak that makes the lift harder with nothing measured takes the lighter guess.
     */
    fun factor(movement: Movement, axis: String, value: String): Double? = when (axis) {
        // The same dumbbell in the one hand as in each of two.
        "arms" -> 1.0.takeIf { value == "single" }
        "tempo" -> when (value) {
            // Faster, not heavier.
            "explosive" -> 1.0
            "slow", "eccentric", "pulse" -> 0.85
            "pause", "stick" -> 0.9
            else -> null
        }
        // The band pulls somewhere the dumbbell doesn't: on a squat it works the hips sideways.
        "band" -> 1.0
        "legs" -> when (value) {
            // Measured with a barbell, a wide stance lifts the same or a little less.
            "sumo" -> 1.0.takeIf { movement.pattern in legs }
            "b-stance" -> 0.8.takeIf { movement.pattern in legs }
            // Off the feet, an arm lift is the same lift; a squat or a push press isn't.
            "kneeling", "half kneeling" -> 1.0.takeIf { movement.pattern in SharedWeight.upper && movement.name !in SharedWeight.legDriven }
            else -> null
        }
        "position" -> 1.0.takeIf { value in setOf("incline", "decline") && movement.pattern in pressed }
        "stability" -> when (value) {
            // Lying on it costs a press less than standing on it costs anything.
            "bosu" -> if (movement.pattern in lying) 0.9 else 0.8
            "feet on bosu" -> 1.0
            else -> null
        }
        // How the handle is turned. Not a shared history: a board sets a grip beside the plain
        // lift at its own weight. But the plain lift's weight is where a first go starts. Not
        // "reverse": nothing was found on it, and on a press it is another lift.
        "grip" -> 1.0.takeIf { value in setOf("close", "neutral", "hammer", "wide") }
        // How many dumbbells, and in how many hands, is [hold]. A kettlebell or y-bell isn't
        // here: they come in other weights than the dumbbells do, usually one in both hands.
        "implement" -> 1.0.takeIf { value in setOf("one dumbbell, both hands", "two dumbbells") }
        else -> null
    }

    /** One dumbbell held in both hands, or one in each, where the words say: "goblet", "two dumbbells". */
    fun hold(part: Part): Int? = when (part.implement) {
        "one dumbbell, both hands" -> 1
        "two dumbbells" -> 2
        else -> null
    }

    /**
     * How many of one of a pair the single dumbbell held in both hands weighs: 1.5 where it is
     * held up at the chest or overhead, which the arms tire of first; 2 on a deadlift, where it
     * hangs from straight arms as the pair does (madCode, 2026-11-24; TRAINING.md, 12). Null on
     * any other lift.
     */
    fun inBothHands(part: Part): Double? = when {
        part.movement.pattern in setOf("squat", "lunge") || part.movement.name == "tricep extension" -> 1.5
        // Not every hinge: a good morning holds it at the chest, and a swing is one by nature.
        // Nor on one leg, where "single dumbbell" is as often one hand's.
        part.movement.name in setOf("deadlift", "romanian deadlift") -> 2.0.takeIf { !part.oneLeg }
        else -> null
    }

    /** Whether a lift held as [mine] can take an estimate from one held as [theirs] at all. */
    fun holds(part: Part, theirs: Int?): Boolean = hold(part).let { mine ->
        when {
            mine == theirs -> true
            mine == 1 || theirs == 1 -> mine != null && inBothHands(part) != null
            else -> true
        }
    }

    /** The stronger grip on a curl: its weight says too much for the plain one (TRAINING.md, 11). */
    fun stronger(movement: Movement, axis: String, value: String) = axis == "grip" && movement.pattern == "curl" && value in setOf("hammer", "neutral")

    // The order a person says them in: how the sides take turns, what is held and worn, how
    // one stands or lies, then how it is moved, nearest the movement's name.
    private val order = listOf("alternating", "arms", "band", "implement", "legs", "lying", "torso", "position", "grip", "tempo", "range", "direction")

    /**
     * How the card and a reason name an exercise: its movement with every word that tells it
     * apart, as a person would say it, not the key it is filed under. What a combo's parts all
     * share is said once, in front.
     */
    fun name(exercise: Reading.Known): String {
        val shared = if (exercise.parts.size == 1) emptySet() else exercise.parts.map { it.variations.entries }.reduce { a, b -> a intersect b }
        val parts = exercise.parts.joinToString(" to ") { p -> words(p, p.variations.entries - shared) }
        return (said(exercise.parts.first(), shared).first + parts).joinToString(" ")
    }

    private fun words(p: Part, entries: Set<Map.Entry<String, String>>): String {
        val (before, after) = said(p, entries)
        return (before + p.movement.name + after).joinToString(" ")
    }

    // The words that go before the movement's name, and the ones that follow it.
    private fun said(p: Part, entries: Set<Map.Entry<String, String>>): Pair<List<String>, List<String>> {
        val sorted = entries.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) order.size else i } }
        val before = sorted.mapNotNull { (axis, value) ->
            when (axis) {
                "alternating" -> "alternating"
                "arms" -> if (value == "single") "single arm" else value
                "legs" -> when (value) {
                    "single" -> "single leg"
                    "split", "wide", "staggered" -> "$value stance"
                    else -> value
                }
                "band" -> if (value == "on") "banded" else "$value band"
                "grip" -> "$value grip"
                "implement" -> when (value) {
                    "one dumbbell, both hands" -> if (p.movement.pattern in setOf("squat", "lunge")) "goblet" else "one-dumbbell"
                    "two dumbbells" -> "two-dumbbell"
                    else -> value
                }
                "tempo" -> when (value) {
                    "pulse" -> "pulsing"
                    "stick" -> null
                    else -> value
                }
                // Said after the name, below; and a position the movement is named for ("high
                // plank" on a plank) isn't said twice.
                "mode", "stability" -> null
                "position" -> value.takeUnless { p.movement.name in it }
                // A yes-or-no axis is said by its name: "lying".
                else -> if (value == "yes") axis else value
            }
        }
        val after = sorted.mapNotNull { (axis, value) ->
            when {
                axis == "mode" -> value
                axis == "tempo" && value == "stick" -> "(stick it)"
                axis == "stability" -> if (value == "bosu") "on a bosu" else "($value)"
                else -> null
            }
        }
        return before to after
    }
}

/** Why a line is "not sure". */
enum class Why {
    /** A word in no list. [Reading.NotSure.words] holds them, for madCode's review list. */
    UNKNOWN_WORDS,
    /** Two movements side by side with nothing joining them ("bench bird dog low row"). */
    TWO_MOVEMENTS,
    /** Words that change an exercise, but no exercise ("high plank alt"). */
    NO_MOVEMENT,
    /** "squat or lunge": one number can't be right for both. */
    A_CHOICE,
    /** Two values on one axis ("single arm" and "two dumbbells"). */
    CONFLICT,
    /** Nothing left to read. */
    EMPTY,
}

/**
 * Reads the words [BoardLine] leaves into an exercise, or "not sure" (see DESIGN.md, "Reading a
 * board line"). Every word must be accounted for, longest phrase first, and a line that reads in
 * full is still refused when its shape is one a full read gets wrong.
 */
class Identity(private val vocabulary: Vocabulary) {
    // Positions that are an exercise in themselves when a line names nothing else.
    private val heldAs = mapOf(
        "high plank" to "plank", "low plank" to "plank", "bear plank" to "plank", "bridge hold" to "glute bridge",
    )

    // A value that says more about another on the same axis.
    // A plank with the hands on a bench is one position, not a bench and a plank in conflict.
    private val together = mapOf(
        setOf("bench", "high plank") to "bench high plank",
        setOf("low bench", "high plank") to "low bench high plank",
    )

    // "dumbbell goblet …" is one dumbbell in both hands: goblet says how the dumbbell is held.
    private val refines = mapOf("hands on bosu" to "bosu", "feet on bosu" to "bosu", "one dumbbell, both hands" to "dumbbell")

    // Words that name a movement only next to certain others, as madCode read the boards
    // (2026-11-22): "with rotation" is a torso rotation, while "trx rotation chest press" isn't;
    // a bare "press" after a squat or lunge is a shoulder press, while "deadbug with press" lies
    // on its back. Anywhere else the word stays unknown.
    private fun fromNeighbours(token: String, before: List<Pair<String, Meaning>>): Meaning? {
        val (lastPhrase, last) = before.lastOrNull() ?: return null
        if (last !is Meaning.Connector || last.kind != "combo") return null
        fun movement(name: String) = vocabulary.movements.firstOrNull { it.name == name }?.let { Meaning.OfMovement(it) }
        return when {
            // A plank, side plank or deadbug "with rotation" is a rotation of its own, not this one.
            token == "rotation" && lastPhrase == "with" ->
                if (previous(before)?.pattern.let { it != null && it != "core" }) movement("torso rotation") else null
            // The reach at the top of a chest press (madCode, 2026-11-22). Elsewhere ("high plank
            // lateral reach") a reach is some other move, so it stays unknown.
            token == "reach" && lastPhrase in setOf("to", "into") ->
                if (previous(before)?.name == "chest press") movement("press reach") else null
            token == "press" && lastPhrase in setOf("to", "into") ->
                if (previous(before)?.name?.let { it.endsWith("squat") || it.endsWith("lunge") } == true) movement("shoulder press") else null
            else -> null
        }
    }

    // "deadlift to 2 x calf raise": a count after "to" says how often the second part is done,
    // not what it is, so it is passed over (how many tokens, or 0). Only there: "3 x 8 x squat"
    // is sets and reps misread, "squat and 8 x deadlift" is two exercises, and "2 x dumbbell" is
    // two dumbbells, so each of those stays a word nobody listed.
    private fun innerCount(tokens: List<String>, at: Int, before: List<Pair<String, Meaning>>): Int {
        val (phrase, last) = before.lastOrNull() ?: return 0
        if (last !is Meaning.Connector || phrase !in setOf("to", "into", "with")) return 0
        val length = when {
            tokens[at].matches(Regex("""\d{1,2}x""")) -> 1
            tokens[at].matches(Regex("""\d{1,2}""")) && tokens.getOrNull(at + 1) == "x" -> 2
            else -> return 0
        }
        val next = tokens.getOrNull(at + length) ?: return 0
        return if ((vocabulary.lookup(next) as? Meaning.Variation)?.axis == "implement") 0 else length
    }

    private fun previous(before: List<Pair<String, Meaning>>): Movement? =
        (before.lastOrNull { it.second is Meaning.OfMovement }?.second as? Meaning.OfMovement)?.movement

    fun read(words: String): Reading {
        val tokens = Vocabulary.normalise(words).replace(Regex("""[:;.]"""), " ").split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Reading.NotSure(Why.EMPTY, emptyList())

        // Longest phrase first, so "high plank" is a position before "high pull" can be tried.
        val meanings = mutableListOf<Pair<String, Meaning>>()
        val unknown = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            var matched = false
            for (n in minOf(vocabulary.longestPhrase, tokens.size - i) downTo 1) {
                val phrase = tokens.subList(i, i + n).joinToString(" ")
                val meaning = vocabulary.lookup(phrase) ?: continue
                meanings += phrase to meaning
                i += n
                matched = true
                break
            }
            if (!matched) {
                val counted = innerCount(tokens, i, meanings)
                if (counted > 0) {
                    i += counted
                    continue
                }
                val guessed = fromNeighbours(tokens[i], meanings)
                if (guessed != null) meanings += tokens[i] to guessed else unknown += tokens[i]
                i++
            }
        }
        // A choice first: whatever its other words, one number can't be right for both sides.
        if (meanings.any { it.second == Meaning.Connector("choice") }) {
            return Reading.NotSure(Why.A_CHOICE, tokens)
        }
        if (unknown.isNotEmpty()) return Reading.NotSure(Why.UNKNOWN_WORDS, unknown)

        // Split into parts at combo connectors. "with" joins a second movement only when one
        // follows; "press with y-bell" is a press done with a Y-bell.
        val segments = mutableListOf(mutableListOf<Pair<String, Meaning>>())
        for ((index, m) in meanings.withIndex()) {
            val (phrase, meaning) = m
            when {
                meaning is Meaning.Connector && phrase == "with" -> {
                    val rest = meanings.drop(index + 1).takeWhile { it.second !is Meaning.Connector }
                    // "bridge hold with chest press": nothing but a position before "with", so
                    // the position is how the press is done, not a part of its own.
                    val before = segments.last().any { it.second is Meaning.OfMovement }
                    if (before && rest.any { it.second is Meaning.OfMovement }) {
                        segments.add(mutableListOf())
                    } else if (!before) {
                        // "single leg hold with shoulder press": the hold is the stance, and the
                        // press isn't an isometric "shoulder press hold".
                        segments.last().removeAll { (it.second as? Meaning.Variation)?.axis == "mode" }
                    }
                }
                meaning is Meaning.Connector -> segments.add(mutableListOf())
                meaning == Meaning.Filler || meaning == Meaning.Scheme -> Unit
                else -> segments.last() += m
            }
        }

        val hints = mutableSetOf<String>()
        val parts = mutableListOf<Part>()
        for (segment in segments.filter { it.isNotEmpty() }) {
            var movements = segment.mapNotNull { (it.second as? Meaning.OfMovement)?.movement }
            if (movements.isEmpty()) {
                // "1 min of low plank", "bench bridge hold": the position is the exercise.
                val held = segment.firstNotNullOfOrNull { (_, m) ->
                    (m as? Meaning.Variation)?.takeIf { it.axis == "position" }?.let { heldAs[it.value] }
                }?.let { name -> vocabulary.movements.firstOrNull { it.name == name } }
                    ?: return Reading.NotSure(Why.NO_MOVEMENT, segment.map { it.first })
                movements = listOf(held)
            }
            if (movements.size > 1) return Reading.NotSure(Why.TWO_MOVEMENTS, segment.map { it.first })
            val variations = mutableMapOf<String, String>()
            for ((phrase, m) in segment) {
                val meaning = when (m) {
                    is Meaning.Scoped -> m.byMovement[movements.single().name]
                        ?: return Reading.NotSure(Why.UNKNOWN_WORDS, listOf(phrase))
                    else -> m
                }
                if (meaning !is Meaning.Variation) continue
                if (meaning.axis == "intensity") {
                    hints += meaning.value
                    continue
                }
                val earlier = variations.put(meaning.axis, meaning.value)
                if (earlier != null && earlier != meaning.value) {
                    // "bosu … (feet on bosu)": the second says where on the Bosu, not a second one.
                    when {
                        refines[meaning.value] == earlier -> Unit
                        refines[earlier] == meaning.value -> variations[meaning.axis] = earlier
                        setOf(earlier, meaning.value) in together -> variations[meaning.axis] = together.getValue(setOf(earlier, meaning.value))
                        else -> return Reading.NotSure(Why.CONFLICT, listOf(phrase))
                    }
                }
            }
            parts += Part(movements.single(), variations)
        }
        if (parts.isEmpty()) return Reading.NotSure(Why.EMPTY, meanings.map { it.first })
        return Reading.Known(shareAcross(parts), hints)
    }

    // A combo names what is held, stood on or worn once, for every part: "TRX high row to bicep
    // curl" is a TRX curl too, "single arm squat to press" a single-arm press, and "cross-back lunge to knee raise (bodyweight)" a bodyweight
    // lunge. A part that says otherwise keeps its own, and two parts that disagree share nothing.
    private fun shareAcross(parts: List<Part>): List<Part> {
        if (parts.size < 2) return parts
        var shared = parts
        for (axis in listOf("implement", "stability", "band", "arms")) {
            val values = shared.mapNotNull { it.variations[axis] }.toSet()
            if (values.size != 1) continue
            // Nothing is held for a part done with nothing ("dumbbell squat to frogger"): the
            // dumbbell is the squat's, and the combo takes the squat's number.
            val held = axis == "implement" && values.single() !in setOf("bodyweight", "trx")
            shared = shared.map { p -> if (axis in p.variations || (held && p.movement.bodyweight)) p else p.copy(variations = p.variations + (axis to values.single())) }
        }
        return shared
    }
}
