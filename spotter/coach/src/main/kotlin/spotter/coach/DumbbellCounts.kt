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
            if (rule.whenLineHas != null && rule.whenLineHas !in said) return null
            rule.unlessLineHas.entries.firstOrNull { it.key in said }?.value ?: rule.count
        }
        return counts.distinct().singleOrNull()
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
            val lines = text.removePrefix("﻿").lines().map { uncommented(it).trimEnd() }.filter { it.isNotBlank() }
            require(lines.firstOrNull()?.replace(" ", "") == "schema:dumbbell-counts/1") { "That isn't a dumbbell counts file: its first line should be \"schema: dumbbell-counts/1\"." }
            require(lines.getOrNull(1)?.trim() == "movements:") { "That file has no \"movements:\" list." }
            val rules = lines.drop(2).associate { line ->
                require(line.first() == ' ') { "A movement isn't indented under \"movements:\": ${shown(line)}" }
                val colon = line.indexOf(':')
                require(colon > 0) { "Not a movement and its count: ${shown(line)}" }
                val name = line.substring(0, colon).trim().trim('"', '\'').lowercase()
                require(name.isNotEmpty()) { "A count with no movement: ${shown(line)}" }
                name to rule(Flow(line.substring(colon + 1).trim()).value(0), line)
            }
            return DumbbellCounts(rules)
        }

        private fun rule(value: Any, line: String): Rule = when (value) {
            is String -> Rule(count(value, line), null, emptyMap())
            is Map<*, *> -> {
                require(value.keys.all { it in setOf("count", "when_line_has", "unless_line_has") }) { "A word this file's format doesn't have: ${shown(line)}" }
                val unless = when (val u = value["unless_line_has"]) {
                    null -> emptyMap()
                    is Map<*, *> -> u.entries.associate { (word, n) -> word(word, line) to count(n, line) }
                    else -> throw IllegalArgumentException("\"unless_line_has\" should list words and counts: ${shown(line)}")
                }
                Rule(count(value["count"], line), value["when_line_has"]?.let { word(it, line) }, unless)
            }
            else -> throw IllegalArgumentException("Not a count: ${shown(line)}")
        }

        private fun count(value: Any?, line: String): Int =
            (value as? String)?.toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: throw IllegalArgumentException("A count should be 1 or 2: ${shown(line)}")

        private fun word(value: Any?, line: String): String =
            (value as? String)?.lowercase()?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Not a word to look for: ${shown(line)}")

        // A "#" starts a comment at the line's start or after a space, as in YAML.
        private fun uncommented(line: String): String {
            val at = line.indices.firstOrNull { line[it] == '#' && (it == 0 || line[it - 1].isWhitespace()) }
            return if (at == null) line else line.substring(0, at)
        }

        // Only the start of a line is quoted back: it may be anything.
        private fun shown(line: String) = "\"" + line.trim().take(60).filter { !it.isISOControl() } + "\""

        /** YAML's one-line form, as far as this file uses it: a word, or `{key: value, …}`, nested. */
        private class Flow(private val text: String) {
            private var at = 0

            fun value(depth: Int): Any {
                val v = read(depth)
                skip()
                require(at == text.length) { "Something after the count: ${shown(text)}" }
                return v
            }

            private fun skip() { while (at < text.length && text[at] == ' ') at++ }

            private fun read(depth: Int): Any {
                skip()
                require(depth <= MAX_DEPTH) { "Too deeply nested: ${shown(text)}" }
                if (at < text.length && text[at] == '{') {
                    at++
                    val map = linkedMapOf<String, Any>()
                    while (true) {
                        skip()
                        if (at < text.length && text[at] == '}') { at++; return map }
                        val key = scalar(":")
                        require(at < text.length && text[at] == ':') { "A name with no value: ${shown(text)}" }
                        at++
                        map[key.lowercase()] = read(depth + 1)
                        skip()
                        if (at < text.length && text[at] == ',') at++ else require(at < text.length && text[at] == '}') { "A list that doesn't close: ${shown(text)}" }
                    }
                }
                return scalar(",}")
            }

            private fun scalar(stops: String): String {
                skip()
                val start = at
                while (at < text.length && text[at] !in stops) at++
                val word = text.substring(start, at).trim().trim('"', '\'')
                require(word.isNotEmpty()) { "Something missing: ${shown(text)}" }
                return word
            }
        }
    }
}
