package spotter.coach

/** How many a board line asks for. */
sealed interface Scheme {
    /** "12 x", "6 - 10 each x", "8 total x". [low] and [high] are equal unless a range is given. */
    data class Reps(val low: Int, val high: Int, val count: Count) : Scheme {
        /**
         * Reps a side on a movement done one side at a time (trap 7): "6 each" is 6 a side, and
         * "12 total" is also 6 a side, so reps logged against either aren't counted twice.
         * Odd totals round down. On a movement done with both sides at once, sides don't apply.
         */
        fun perSide(): IntRange = when (count) {
            Count.TOTAL -> (low / 2)..(high / 2)
            Count.EACH, Count.PLAIN -> low..high
        }
    }

    /** "10 / 8 / 6 x": a weight is picked up for the first round (trap 8). */
    data class Ladder(val rounds: List<Int>, val count: Count) : Scheme {
        val firstRound: Int get() = rounds.first()
    }

    /** "30 sec of", "1 min of": a time, not a rep count. */
    data class Timed(val seconds: Int) : Scheme

    /** "AMRepsAP": as many reps as possible. */
    data object AsManyAsPossible : Scheme
}

/** Whether a rep count is for each side, the total across sides, or says neither. */
enum class Count { PLAIN, EACH, TOTAL }

/** What kind of line it is. Only an [EXERCISE] line goes on to be named and given a number. */
enum class LineKind { EXERCISE, INSTRUCTION, HEADING }

/**
 * A board line split into what it asks for and the words that name the exercise. The words go on
 * to identity; nothing here decides what the exercise is.
 */
data class BoardLine(
    val raw: String,
    val kind: LineKind,
    /** "finisher", "bonus", "buy-in" when the line is one; null for a plain block line. */
    val label: String?,
    val scheme: Scheme?,
    /** The exercise's words, lower case, with the scheme, label, side and trailing rest taken off. */
    val words: String,
    /** "L" or "R" when the line names one side, as in "(R)". */
    val side: String?,
    val rest: Boolean,
) {
    companion object {
        private val labels = listOf("finisher", "bonus", "buy-in", "buy in", "coach cue")
        private const val NUM = """(\d+)"""
        private val ladder = Regex("""^$NUM(?:\s*/\s*$NUM)+\s*(each|total)?\s*(?:x|×)?\s+""")
        // "N x", "N - N x", "N each x", "4 each side:", and "5 each arm", which has no "x".
        private val reps = Regex("""^~?$NUM(?:\s*-\s*$NUM)?\s*(?:(each|total)(?:\s+(?:side|arm|leg))?\s*(?:x|×|:)?|(?:x|×|:))\s*""")
        private val timed = Regex("""^~?$NUM\s*(sec|secs|second|seconds|s|min|mins|minute|minutes)\b\s*(?:of\s+)?""")
        private val amrap = Regex("""\bamrepsap\b|\bamrap\b""")
        private val sideMark = Regex("""\s*\((l|r)\)\s*$|\s+(l|r)$|\s+on (?:the )?(l)eft$|\s+on (?:the )?(r)ight$""")
        private val bareCount = Regex("""^$NUM\s+(?!(?:dumbbells?|db|degree|kg|lbs?|x)\b)(?=[a-z])""")
        private val trailingRest = Regex("""[,\s]*(?:-\s*work & )?\brest\b\s*$""")

        // A line that tells the class what to do rather than naming an exercise. Boards hold
        // these between exercises; reading one as an exercise is how "Good morning, all!" once
        // became the good-morning lift (trap 5).
        private val instructionStarts = listOf("repeat", "goal", "until", "match", "increase", "then", "rest ", "complete")

        fun read(raw: String): BoardLine {
            var text = Vocabulary.normaliseText(raw)
                .replace("**", "")
                .replace("\\", "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            var label: String? = null
            for (l in labels) {
                val m = Regex("""^(?:repeat until (?:time (?:is called )?)?(?:for )?)?$l\b\s*:?\s*""").find(text)
                if (m != null) {
                    label = l.replace("buy in", "buy-in")
                    text = text.substring(m.range.last + 1)
                    break
                }
            }
            if (label == null && instructionStarts.any { text.startsWith(it) } ) return other(raw, LineKind.INSTRUCTION, text)
            if (text.endsWith(":") || Regex("""^floor block|^block \d|^tread block|\bchallenge:?$""").containsMatchIn(text)) {
                return other(raw, LineKind.HEADING, text)
            }
            // Commentary: a sentence aimed at the reader, not a workout line.
            if (text.contains('!') || text.contains('?') || Regex("""\b(you|your|we|all)\b""").containsMatchIn(text) && scheme(text) == null) {
                return other(raw, LineKind.INSTRUCTION, text)
            }

            var rest = false
            trailingRest.find(text)?.let { rest = true; text = text.substring(0, it.range.first) }
            if (Regex("""\(rest as needed\)""").containsMatchIn(text)) {
                rest = true
                text = text.replace(Regex("""\s*\(rest as needed\)"""), "")
            }
            var side: String? = null
            sideMark.find(text)?.let { m ->
                side = (1..4).firstNotNullOf { m.groups[it] }.value.uppercase()
                text = text.substring(0, m.range.first)
            }

            val (scheme, after) = scheme(text) ?: (null to text)
            var words = after
            var finalScheme = scheme
            if (amrap.containsMatchIn(words)) {
                words = words.replace(amrap, " ")
                if (finalScheme == null) finalScheme = Scheme.AsManyAsPossible
            }
            words = words.replace(Regex("""\s*until time is called\s*"""), " ")
                .replace(Regex("""[(),]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            return BoardLine(raw, LineKind.EXERCISE, label, finalScheme, words, side, rest)
        }

        private fun other(raw: String, kind: LineKind, text: String) =
            BoardLine(raw, kind, null, null, text, null, false)

        /** The scheme at the start of [text] and the text after it, or null when there is none. */
        private fun scheme(text: String): Pair<Scheme, String>? {
            ladder.find(text)?.let { m ->
                val rounds = Regex("""\d+""").findAll(m.value.substringBefore("each").substringBefore("total"))
                    .map { it.value.toInt() }.toList()
                return Scheme.Ladder(rounds, count(m.groups[m.groups.size - 1]?.value)) to text.substring(m.range.last + 1)
            }
            timed.find(text)?.let { m ->
                val n = m.groups[1]!!.value.toInt()
                val seconds = if (m.groups[2]!!.value.startsWith("m")) n * 60 else n
                return Scheme.Timed(seconds) to text.substring(m.range.last + 1)
            }
            reps.find(text)?.let { m ->
                val low = m.groups[1]!!.value.toInt()
                val high = m.groups[2]?.value?.toInt() ?: low
                return Scheme.Reps(minOf(low, high), maxOf(low, high), count(m.groups[3]?.value)) to text.substring(m.range.last + 1)
            }
            // A bare count before the exercise ("8 lateral lunge"), unless the number is part of
            // the exercise ("2 dumbbell bench bridge", "90 degree pop squat").
            bareCount.find(text)?.let { m ->
                val n = m.groups[1]!!.value.toInt()
                return Scheme.Reps(n, n, Count.PLAIN) to text.substring(m.range.last + 1)
            }
            return null
        }

        private fun count(word: String?) = when (word) {
            "each" -> Count.EACH
            "total" -> Count.TOTAL
            else -> Count.PLAIN
        }
    }
}
