package spotter.coach

/**
 * The owner's own rulings on how many dumbbells a lift is done with where nobody says: a file
 * kept beside the history and picked with it, never in this repository (docs/COUNTS-FORMAT.md,
 * schema `dumbbell-counts/1`). It fills a count only where neither the board line nor the
 * history row gives one.
 */
class DumbbellCounts private constructor(private val rules: Map<String, Rule>) {
    private data class Rule(val count: Int, val whenLineHas: String?, val unlessLineHas: Map<String, Int>)

    /** How many movements the file rules on. */
    val size: Int get() = rules.size

    /**
     * The count for a row filed under [names] (one movement, or a combo's parts) whose board
     * line is [line]; null where the file doesn't rule, or a combo's parts don't agree.
     */
    fun count(names: List<String>, line: String?): Int? {
        val said = line.orEmpty().lowercase()
        val counts = names.map { name ->
            val rule = rules[name.trim().lowercase()] ?: return null
            if (rule.whenLineHas != null && !has(said, rule.whenLineHas)) return null
            rule.unlessLineHas.entries.firstOrNull { has(said, it.key) }?.value ?: rule.count
        }
        return counts.distinct().singleOrNull()
    }

    // At the start of a word: "arm" is in "single arm" and "arms", not in "farmer" or "warm up".
    private fun has(line: String, word: String): Boolean {
        var at = line.indexOf(word)
        while (at >= 0) {
            if (at == 0 || !line[at - 1].isLetterOrDigit()) return true
            at = line.indexOf(word, at + 1)
        }
        return false
    }

    companion object {
        val NONE = DumbbellCounts(emptyMap())

        /** Far more than a list of lifts needs; a file built to exhaust memory stops here. */
        const val MAX_CHARS = 100_000
        private const val MAX_DEPTH = 4

        /**
         * Reads the file, or says in a sentence why it isn't one. All or nothing: a ruling
         * half read would be a count nobody gave.
         */
        fun parse(text: String): DumbbellCounts {
            require(text.length <= MAX_CHARS) { "That file is too big to be a list of dumbbell counts." }
            val lines = text.removePrefix("\uFEFF").lines().map { uncommented(it).trimEnd() }.filter { it.isNotBlank() }
            require(lines.firstOrNull()?.matches(Regex("""schema:\s*(["']?)dumbbell-counts/1\1""")) == true) { "That isn't a dumbbell counts file: its first line should be \"schema: dumbbell-counts/1\"." }
            require(lines.getOrNull(1)?.trim() == "movements:") { "That file has no \"movements:\" list." }
            val rules = linkedMapOf<String, Rule>()
            val indent = lines.getOrNull(2)?.indexOfFirst { it != ' ' } ?: 0
            for (line in lines.drop(2)) {
                require(line.first() == ' ') { "A movement isn't indented under \"movements:\": ${shown(line)}" }
                val colon = colonAfterName(line)
                require(colon > 0) { "Not a movement and its count: ${shown(line)}" }
                val value = line.substring(colon + 1).trim()
                // A rule spread over lines, as some YAML writers lay it out, would be read as
                // movements called "count" and "goblet".
                require(line.indexOfFirst { it != ' ' } == indent && value.isNotEmpty()) { "A rule goes on one line, like \"squat: {count: 2, unless_line_has: {goblet: 1}}\": ${shown(line)}" }
                val name = name(line.substring(0, colon))
                require(name.isNotEmpty() && !name.startsWith("-") && name.all { it == ' ' || !unprintable(it) }) { "Not a movement's name: ${shown(line)}" }
                require(rules.put(name, rule(Flow(value, line).value(0), line)) == null) { "A movement is there twice: ${shown(line)}" }
            }
            return DumbbellCounts(rules)
        }

        // The colon that ends the name: a quoted name may hold one.
        private fun colonAfterName(line: String): Int {
            val start = line.indexOfFirst { it != ' ' }
            val quote = line.getOrNull(start)?.takeIf { it == '"' || it == '\'' } ?: return line.indexOf(':')
            var at = start + 1
            while (at < line.length && (line[at] != quote || (quote == '\'' && line.getOrNull(at + 1) == quote).also { if (it) at++ })) at++
            return line.indexOf(':', minOf(at, line.length))
        }

        // As it will be looked up: unquoted, lower case, one space between words.
        private fun name(raw: String): String {
            val t = raw.trim()
            val quote = t.firstOrNull()?.takeIf { (it == '"' || it == '\'') && t.length > 1 && t.last() == it }
            // An opening quote that doesn't close at the colon: not a name.
            if (quote == null && t.firstOrNull().let { it == '"' || it == '\'' }) return ""
            val bare = if (quote == null) t else t.substring(1, t.length - 1).let { if (quote == '\'') it.replace("''", "'") else it }
            return bare.replace(Regex("[\\s\u00a0]+"), " ").trim().lowercase()
        }

        private fun unprintable(c: Char) = c.isISOControl() || c.isSurrogate() || Character.getType(c).toByte() in setOf(
            Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.PRIVATE_USE, Character.UNASSIGNED, Character.SPACE_SEPARATOR,
        )

        private fun rule(value: Any, line: String): Rule = when (value) {
            is String -> Rule(count(value, line), null, emptyMap())
            is Map<*, *> -> {
                require(value.keys.all { it in setOf("count", "when_line_has", "unless_line_has") }) { "A word this file's format doesn't have: ${shown(line)}" }
                val unless = when (val u = value["unless_line_has"]) {
                    null -> emptyMap()
                    is Map<*, *> -> u.entries.associate { (word, n) -> word(word, line) to count(n, line) }.also {
                        require(it.size == u.size) { "A word is there twice: ${shown(line)}" }
                    }
                    else -> throw IllegalArgumentException("\"unless_line_has\" should list words and counts: ${shown(line)}")
                }
                Rule(count(value["count"], line), value["when_line_has"]?.let { word(it, line) }, unless)
            }
            else -> throw IllegalArgumentException("Not a count: ${shown(line)}")
        }

        private fun count(value: Any?, line: String): Int =
            (value as? String)?.takeIf { it == "1" || it == "2" }?.toInt() ?: throw IllegalArgumentException("A count should be 1 or 2: ${shown(line)}")

        private fun word(value: Any?, line: String): String =
            (value as? String)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Not a word to look for: ${shown(line)}")

        // A "#" starts a comment at the line's start or after a space, as in YAML.
        private fun uncommented(line: String): String {
            val at = line.indices.firstOrNull { line[it] == '#' && (it == 0 || line[it - 1].isWhitespace()) }
            return if (at == null) line else line.substring(0, at)
        }

        // Only the start of a line is quoted back, and only what prints: it may be anything.
        private fun shown(line: String) = "\"" + line.trim().filter { it == ' ' || !unprintable(it) }.take(60) + "\""

        /** YAML's one-line form, as far as this file uses it: a word, or `{key: value, …}`, nested. */
        private class Flow(private val text: String, private val line: String) {
            private var at = 0

            fun value(depth: Int): Any {
                val v = read(depth)
                skip()
                require(at == text.length) { "Something after the count: ${shown(line)}" }
                return v
            }

            private fun skip() { while (at < text.length && text[at] == ' ') at++ }

            private fun read(depth: Int): Any {
                skip()
                require(depth <= MAX_DEPTH) { "Too deeply nested: ${shown(line)}" }
                if (at < text.length && text[at] == '{') {
                    at++
                    val map = linkedMapOf<String, Any>()
                    while (true) {
                        skip()
                        if (at < text.length && text[at] == '}') { at++; return map }
                        val key = scalar(":")
                        require(at < text.length && text[at] == ':') { "A name with no value: ${shown(line)}" }
                        at++
                        require(map.put(key.lowercase(), read(depth + 1)) == null) { "A word is there twice: ${shown(line)}" }
                        skip()
                        if (at < text.length && text[at] == ',') at++ else require(at < text.length && text[at] == '}') { "A list that doesn't close: ${shown(line)}" }
                    }
                }
                return scalar(",}")
            }

            private fun scalar(stops: String): String {
                skip()
                val start = at
                while (at < text.length && text[at] !in stops) at++
                val word = text.substring(start, at).trim().trim('"', '\'')
                require(word.isNotEmpty()) { "Something missing: ${shown(line)}" }
                return word
            }
        }
    }
}
