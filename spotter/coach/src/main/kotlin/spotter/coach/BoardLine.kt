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

        /** Each round's reps a side: "6 / 8 / 10 total" is 3, 4, 5 a side, as "12 total" is 6 (trap 7). */
        fun perSide(): List<Int> = if (count == Count.TOTAL) rounds.map { it / 2 } else rounds
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
    /** "finisher", "bonus", "buy-in", "buy-out" when the line is one; null for a plain block line. */
    val label: String?,
    val scheme: Scheme?,
    /** The exercise's words, lower case, with the scheme, label, side and trailing rest taken off. */
    val words: String,
    /** "L" or "R" when the line names one side, as in "(R)". */
    val side: String?,
    val rest: Boolean,
) {
    companion object {
        /** Longer than any workout line: commentary, or input built to be slow to read. */
        const val MAX_LENGTH = 300

        private val labels = Regex("""^(?:repeat until (?:time (?:is called )?)?(?:for )?)?(finisher|bonus|buy-in|buy in|buy-out|buy out|coach cue)\b\s*:?\s*""")
        // Four digits at most: no board asks for more, and a longer number can't overflow Int.
        private const val NUM = """(\d{1,4})(?!\d)"""
        // A ladder has three rounds or more, or an "x": "1/2 kneeling" is a half-kneeling stance.
        private val ladder = Regex("""^$NUM\s*/\s*$NUM(?:\s*/\s*$NUM){1,8}\s*(each|total)?\s*(?:x|×)?\s+|^$NUM\s*/\s*$NUM\s*(each|total)?\s*(?:x|×)\s+""")
        // "N x", "N - N x", "N each x", "4 each side:", and "5 each arm", which has no "x".
        private val reps = Regex("""^~?$NUM(?:\s*-\s*$NUM)?\s*(?:(each|total)(?:\s+(?:side|arm|leg))?\s*(?:x|×|:)?|(?:x|×|:))\s*""")
        private val timed = Regex("""^~?$NUM\s*(sec|secs|second|seconds|s|min|mins|minute|minutes)\b\s*(?:of\s+)?""")
        private val clockTime = Regex("""^(\d{1,2}):(\d{2})\s+(?:of\s+)?""")
        // "45 sec to do 8 x split squat": the reps are the scheme; the time is how long to do them.
        private val timeToDo = Regex("""^~?\d{1,4}\s*(?:sec|secs|seconds|min|mins|minutes)\s+to\s+(?:do|complete)\s+""")
        private val amrap = Regex("""\bamrepsap\b|\bamrap\b""")
        private val trailingCount = Regex("""\s*\((total|each|ea)\)""")
        private val side = Regex("""\s*\((l|r|left|right)\)|\s+-\s*(l|r|left|right)\s*$|\s+(l|r)\s*$|\s+on (?:the )?(left|right)\s*$""")
        // A bare count before the exercise ("8 lateral lunge"), unless the number is part of the
        // exercise ("2 dumbbell bench bridge", "90 degree pop squat", "4 point row") or the line
        // is a block count ("2 blocks on the floor").
        private val bareCount = Regex("""^$NUM\s+(?!(?:dumbbells?|db|degree|kg|lbs?|x|point|way|arm|count|blocks?|rounds?|minutes?|min|sec|m)\b)(?=[a-z])""")
        private val trailingRest = Regex("""(?:[,\s]|\bor\b)*(?:-\s*work & )?\brest\b(?:[,\s]|\bor\b)*$""")
        private val trailingJunk = Regex("""(?:[,\s-]|\bor\b)+$""")

        // Lines that tell the class what to do, rather than naming an exercise. Reading one as an
        // exercise is how "Good morning, all!" once became the good-morning lift (trap 5).
        private val instructionStart = Regex("""^(repeat|goal|until|match|increase|then|complete|rest|member|check|wr)\b""")
        // What to do with the rower's or treadmill's screen, where to pick up, who is quoted,
        // and rowing until a signal ("row until time is called" is the rower, not a dumbbell
        // row): said to the class, and never the name of an exercise.
        private val housekeeping = Regex(
            """^(?:clear screen|set monitor\b|collapse\b|remember\b|begin at\b|count (?:&|and) remember\b|when done\b|(?:red|orange|green) goal\b|reposting\b|row (?:until|for)\b)""",
        )
        // A second sentence of five words or more; "alt." and a short cue ("count & check reps")
        // after a full stop are still part of a workout line.
        private val moreSentences = Regex("""\.\s+\S+(?:\s+\S+){4,}""")
        // One long sentence with no count in front: "Nice template - endurance and power … floor."
        private val sentence = Regex("""^[^\d~].*\s\S+(?:\s+\S+){8,}\.$""")
        private val talk = Regex("""\b(you|your|we|our|i|my)\b""")
        // Tread and rower lines name no dumbbell lift, though "row" is a movement's name.
        private val cardio = Regex("""\b\d+\s?m\b|%|\ball[- ]?outs?\b|\bao\b|\bpush pace\b|\bmph\b|\brower\b|\btread\b|\bjog\b|\brun\b|\bstrokes\b|\bdistance\b""")
        // Time between blocks: "90 sec recovery", "1 min to transition to the rower", "90 sec WR".
        // Only a whole line of it: "30 sec of active recovery: bird dog" is still a bird dog.
        private val between = Regex("""^~?\d{1,4}\s*(?:sec|secs|seconds|min|mins|minutes)\s+(?:of\s+)?(?:active\s+)?(?:recovery|rest|base|wr\b.*|to transition\b.*)$""")
        // A timed effort on the rower or the treadmill, the whole line: "1 min push row",
        // "30 sec recovery row", "1 min row (1:3 tempo)", "2.5 min base", "30 sec speed surge",
        // "2 min push (PW @ 5+)", "30 sec transition". A bare "row" after a time is the rower
        // (a dumbbell row says which: "low row"); "push" only when nothing follows it but the
        // rower or a note, so a push-up and a push press stay exercises. The note after it
        // must be the machine's (a tempo, a stroke rate, a wattage): "30 sec row (single arm)"
        // is a dumbbell in one hand.
        private val machine = Regex(
            """^~?\d{1,4}(?:\.\d{1,2})?\s*(?:sec|secs|second|seconds|s|min|mins|minute|minutes)\s+(?:of\s+)?""" +
                """(?:(?:(?:recovery|push|base)\s+)?row|(?:walking\s+)?recovery|base|push|(?:speed\s+|elevation\s+|incline\s+)?surge|transition)""" +
                """(?:\s*[(@,](?=.*(?:tempo|strokes|@|\bpw\b|\d:\d|intensity))(?!.*\b(?:arms?|dumbbells?|db|heavy|weight)\b).*)?(?:\s+or)?\.?$""",
        )

        // A count or a time "of" something inside a line: an exercise is being named, whatever
        // the line opens with ("When done - 8 x squat").
        private val namesAnExercise = Regex("""\d\s*(?:x|×)\s|\b(?:sec|secs|second|seconds|min|mins|minute|minutes)\s+(?:each\s+(?:side\s+)?)?of\b""")

        fun read(raw: String): BoardLine {
            if (raw.length > MAX_LENGTH) return other(raw, LineKind.INSTRUCTION, raw.take(80))
            var text = Vocabulary.normaliseText(raw)
                .replace("**", "")
                .replace("\\", "")
                .replace(Regex("""^[*•\-\s]+"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            var label: String? = null
            labels.find(text)?.let { m ->
                label = m.groups[1]!!.value.replace("buy in", "buy-in").replace("buy out", "buy-out")
                text = text.substring(m.range.last + 1)
            }
            // A line with no letters (a lone backslash between paragraphs) says nothing.
            if (text.none { it.isLetter() }) return other(raw, LineKind.INSTRUCTION, text)
            // "# 2G intel": the post's title.
            if (text.startsWith("#")) return other(raw, LineKind.HEADING, text.trimStart('#', ' '))
            if (instructionStart.containsMatchIn(text)) return other(raw, LineKind.INSTRUCTION, text)
            if (text.endsWith(":") || Regex("""^(?:floor|tread|row)(?:\s*/\s*(?:floor|tread|row))* block|^block \d|\bchallenge:?$""").containsMatchIn(text)) {
                return other(raw, LineKind.HEADING, text)
            }
            // After the headings: "When done:" names what follows, and rows are filed under it.
            if (housekeeping.containsMatchIn(text) && !namesAnExercise.containsMatchIn(text)) return other(raw, LineKind.INSTRUCTION, text)
            // Commentary: a cheer, a question, a sentence to the reader, or more than one sentence.
            if (text.contains('!') || text.contains('?') || talk.containsMatchIn(text) || moreSentences.containsMatchIn(text) || sentence.matches(text)) {
                return other(raw, LineKind.INSTRUCTION, text)
            }
            if (cardio.containsMatchIn(text) || between.containsMatchIn(text) || machine.matches(text)) return other(raw, LineKind.INSTRUCTION, text)

            var count: Count? = null
            trailingCount.find(text)?.let { m ->
                count = if (m.groups[1]!!.value == "total") Count.TOTAL else Count.EACH
                text = text.removeRange(m.range)
            }
            var foundSide: String? = null
            side.find(text)?.let { m ->
                foundSide = (1..4).firstNotNullOf { m.groups[it] }.value.take(1).uppercase()
                text = text.removeRange(m.range)
            }
            var rest = false
            if (text.contains("(rest as needed)")) {
                rest = true
                text = text.replace("(rest as needed)", " ")
            }
            text = text.replace(Regex(""",?\s*~?\d{1,4}\s*(?:sec|secs|seconds)\s+rest\b"""), ", rest")
            trailingRest.find(text)?.let { rest = true; text = text.substring(0, it.range.first) }
            text = text.replace(trailingJunk, "")
            timeToDo.find(text)?.let { text = text.substring(it.range.last + 1) }

            val (scheme, after) = scheme(text) ?: (null to text)
            var finalScheme = scheme
            if (count != null) finalScheme = when (scheme) {
                is Scheme.Reps -> scheme.copy(count = count!!)
                is Scheme.Ladder -> scheme.copy(count = count!!)
                else -> scheme
            }
            var words = after
            if (amrap.containsMatchIn(words)) {
                words = words.replace(amrap, " ")
                if (finalScheme == null) finalScheme = Scheme.AsManyAsPossible
            }
            words = words.replace(Regex("""\s*until time is called\s*"""), " ")
                .replace(Regex(""",?\s*recover when done(?: until next round begins)?\b"""), " ")
                .replace(Regex("""[(),]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            return BoardLine(raw, LineKind.EXERCISE, label, finalScheme, words, foundSide, rest)
        }

        private fun other(raw: String, kind: LineKind, text: String) =
            BoardLine(raw, kind, null, null, text, null, false)

        /** The scheme at the start of [text] and the text after it, or null when there is none. */
        private fun scheme(text: String): Pair<Scheme, String>? {
            ladder.find(text)?.let { m ->
                val numbers = m.value.substringBefore("each").substringBefore("total")
                val rounds = Regex("""\d+""").findAll(numbers).map { it.value.toInt() }.toList()
                val word = m.groupValues.drop(1).firstOrNull { it == "each" || it == "total" }
                return Scheme.Ladder(rounds, count(word)) to text.substring(m.range.last + 1)
            }
            clockTime.find(text)?.let { m ->
                return Scheme.Timed(m.groups[1]!!.value.toInt() * 60 + m.groups[2]!!.value.toInt()) to text.substring(m.range.last + 1)
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
