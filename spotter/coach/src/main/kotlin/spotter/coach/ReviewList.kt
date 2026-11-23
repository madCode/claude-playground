package spotter.coach

import java.time.LocalDate

/**
 * The words on a recent board line that no list has, so its row says "not sure" and gets no
 * number (DESIGN.md, "New words go to a review list"). [word] is every such word of the line,
 * in order ("front foot"): together they are one thing to settle, and apart "front" and "foot"
 * would each look like a find. [line] is the latest board line they were seen on, in the
 * board's own words; [days] is on how many of the kept days' boards they appeared.
 */
data class UnknownWord(val word: String, val line: String, val lastSeen: LocalDate, val days: Int)

/**
 * The words to review, read off the boards kept on the phone. Only words: a line that is unsure
 * for another reason (two movements side by side, a choice) has nothing a list could learn.
 *
 * The boards are strangers' text from the network, so what is read is bounded and what is
 * shown is cleaned: a day counts once for a set of words however many lines carry it, or one
 * board of junk would outrank a real word seen all week.
 */
object ReviewList {
    /** More than any class's board has; a board built to be slow to read stops here. */
    const val MAX_LINES = 150

    // Line breaks, tabs and other controls, and the marks that reorder text: none belongs in a
    // board line, and on a card they would stretch it or reverse what follows.
    private val unprintable = Regex("""[\p{Cc}\u200E\u200F\u202A-\u202E\u2066-\u2069]+""")

    fun of(boards: List<DayBoard>, identity: Identity): List<UnknownWord> {
        val seen = boards.flatMap { board ->
            // A line once, whichever class had it: the 2G and 3G boards often share a block.
            val lines = board.classes.values.flatMap { it.take(MAX_LINES) }.distinct()
            Sheet.make(lines, emptyList(), identity).rows
                .mapNotNull { row ->
                    (row.reading as? Reading.NotSure)?.takeIf { it.why == Why.UNKNOWN_WORDS }
                        ?.let { Triple(clean(it.words.joinToString(" ")), board.date, clean(row.shown)) }
                }
                .filter { it.first.isNotEmpty() }
                .distinctBy { it.first }
        }
        return seen.groupBy { it.first }
            .map { (words, at) ->
                val latest = at.maxBy { it.second }
                UnknownWord(words, latest.third, latest.second, at.map { it.second }.distinct().size)
            }
            // Seen on the most days first, then the most recent.
            .sortedWith(compareByDescending<UnknownWord> { it.days }.thenByDescending { it.lastSeen }.thenBy { it.word })
    }

    private fun clean(text: String) = text.replace(unprintable, " ").replace(Regex("""\s+"""), " ").trim()
}
