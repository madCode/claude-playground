package spotter.coach

/** One exercise row on the sheet: the board's own words, what Spotter read, and the number. */
data class SheetRow(
    val block: String?,
    val line: BoardLine,
    val reading: Reading,
    /** Reps a side the row asks for (the first round of a ladder); null when timed. */
    val target: IntRange?,
    /** Null when the row wants no weight: bodyweight, or banded with nothing held. */
    val suggestion: Suggestion?,
    /** Said before the number: "not sure what this is", a part with no number. */
    val warnings: List<String>,
) {
    /** The board's own words, as posted, without the bullet. */
    val shown: String get() = line.raw.trim().trimStart('*', '•', '-', ' ').replace("**", "")
}

/** Today's sheet for one class, rows in board order. */
data class Sheet(val rows: List<SheetRow>, val borrowedFrom: String? = null) {
    /** Every row's warnings, gathered for the top of the sheet. */
    val warnings: List<String> get() = rows.flatMap { row -> row.warnings.map { "${row.shown}: $it" } }

    /**
     * The sheet as text: what the glance shows, a row per line, warnings first. The board's own
     * words lead each row, so a misreading is visible at the gym.
     */
    fun text(): String = buildString {
        borrowedFrom?.let { appendLine("Borrowed from the $it board: leave rows that weren't in your class.") }
        warnings.forEach { appendLine("! $it") }
        var block: String? = null
        for (row in rows) {
            if (row.block != block) {
                block = row.block
                block?.let { appendLine("== $it") }
            }
            appendLine("${row.shown}  ${glance(row)}")
        }
    }.trimEnd()

    private fun glance(row: SheetRow): String = when (val s = row.suggestion) {
        null -> if (row.reading is Reading.NotSure) "?" else "no weight"
        is Suggestion.NoNumber -> s.reason
        is Suggestion.Weight -> buildString {
            append(s.load.label)
            append(
                when (s.change) {
                    Change.UP -> " ▲"
                    Change.DOWN -> " ▼"
                    Change.SAME -> " ="
                    Change.NEW -> " new"
                },
            )
            s.test?.let { append(" · try ${it.lb} if set 1 felt easy") }
        }
    }

    companion object {
        /**
         * The sheet for a class's board [lines] in order, from the [log]. [borrowedFrom] names the
         * other class whose board this is, when the class had none of its own.
         */
        private val topLevel = Regex("""\bblock\b|intel""")

        fun make(lines: List<String>, log: List<Logged>, identity: Identity, borrowedFrom: String? = null): Sheet {
            var block: String? = null
            val rows = mutableListOf<SheetRow>()
            for (raw in lines) {
                val line = BoardLine.read(raw)
                when (line.kind) {
                    LineKind.HEADING -> {
                        val name = line.words.trimEnd(':').trim()
                        // "Back-to-back complex:" inside a floor block names part of it, not a new block.
                        block = if (topLevel.containsMatchIn(name) || block == null) name else "${block.substringBefore(" · ")} · $name"
                    }
                    LineKind.INSTRUCTION -> Unit
                    LineKind.EXERCISE -> rows += row(block, line, log, identity)
                }
            }
            return Sheet(rows, borrowedFrom)
        }

        private fun row(block: String?, line: BoardLine, log: List<Logged>, identity: Identity): SheetRow {
            val reading = identity.read(line.words)
            val target = when (val s = line.scheme) {
                is Scheme.Reps -> s.perSide()
                is Scheme.Ladder -> s.firstRound..s.firstRound
                else -> null
            }
            if (reading !is Reading.Known) {
                val why = (reading as Reading.NotSure).why
                return SheetRow(block, line, reading, target, null, listOf("not sure what this is (${why.name.lowercase().replace('_', ' ')})"))
            }
            if (!wantsWeight(reading)) return SheetRow(block, line, reading, target, null, emptyList())
            val warnings = mutableListOf<String>()
            val suggestion = withRowCount(combo(reading, target, log, warnings), reading)
            return SheetRow(block, line, reading, target, suggestion, warnings)
        }

        // Trap 6: a bare squat wants a weight. Bodyweight and TRX don't, nor a band, hold or pulse
        // with nothing held, nor a bare push-up or plank, done without one unless the line names it.
        private fun wantsWeight(reading: Reading.Known): Boolean = reading.parts.any { wantsWeight(it) }

        private fun wantsWeight(p: Part): Boolean = when (p.implement) {
            "bodyweight", "trx" -> false
            null -> !p.movement.bodyweight && p.variations["mode"] != "hold" && p.variations["tempo"] != "pulse" &&
                // A band with nothing held, unless one arm says a dumbbell is in it.
                (p.variations["band"] == null || p.variations["arms"] == "single")
            else -> true
        }

        /**
         * A combo's number: its own history first. Without it, the lightest of its weighted parts,
         * since one dumbbell goes through every part, but only when every weighted part has a
         * number of its own and all are held the same way (trap 2). A part with no number, or one
         * that hurt, leaves the combo without one: a gap beats the heavier part's guess.
         */
        private fun combo(reading: Reading.Known, target: IntRange?, log: List<Logged>, warnings: MutableList<String>): Suggestion {
            val own = Progression.suggest(reading, target, log)
            if (reading.parts.size == 1 || own is Suggestion.Weight) return own
            if (log.any { it.key == reading.key }) return own
            val weighted = reading.parts.filter { wantsWeight(it) }
            if (weighted.map { it.implement }.toSet().size > 1) return Suggestion.NoNumber("new: its parts are held differently")
            val parts = weighted.map { part -> part to Progression.suggest(Reading.Known(listOf(part), emptySet()), target, log) }
            val missing = parts.filter { it.second !is Suggestion.Weight }
            if (missing.isNotEmpty()) {
                if (missing.size < parts.size) warnings += "no number for the ${missing.joinToString(" or ") { it.first.movement.name }} yet"
                return own
            }
            val (part, lightest) = parts.map { it.first to it.second as Suggestion.Weight }.minBy { it.second.load.lb }
            return Suggestion.Weight(
                lightest.load,
                Change.NEW,
                listOf("new combo: the lightest of its parts, the ${part.movement.name} (${lightest.load.label})"),
            )
        }

        // A load imported with no count takes the row's, when the row's words fix it.
        private fun withRowCount(s: Suggestion, reading: Reading.Known): Suggestion {
            if (s !is Suggestion.Weight || s.load.count != null) return s
            val count = rowCount(reading) ?: return s
            return s.copy(load = s.load.copy(count = count), test = s.test?.copy(count = count))
        }

        /** How many dumbbells the row's words say: null when they don't. */
        fun rowCount(reading: Reading.Known): Int? {
            val counts = reading.parts.map { p ->
                when {
                    p.implement == "two dumbbells" -> 2
                    p.implement == "one dumbbell, both hands" -> 1
                    p.variations["arms"] == "single" -> 1
                    else -> null
                }
            }.toSet()
            return counts.singleOrNull()
        }
    }
}
