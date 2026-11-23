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
    /** More lines than a day's classes have between them; a board built to be slow to read stops here. */
    const val MAX_LINES = 300

    /** Longer than any exercise line; reading a line costs by its length. */
    const val MAX_CHARS = 160

    // Line breaks, tabs and other controls: none belongs in a board line, and on a card they
    // would stretch it. Each stands where a space might have.
    private val breaks = Regex("""[\p{Cc}\u2028\u2029]+""")

    // Marks that reorder text or hide inside a word: taken out with nothing left behind, so
    // the word is the word it reads as.
    private val marks = Regex("""[\u00AD\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]+""")

    fun of(boards: List<DayBoard>, identity: Identity): List<UnknownWord> {
        val seen = boards.flatMap { board ->
            // A line once, whichever class had it: the 2G and 3G boards often share a block.
            // Bounded over the whole board: a file may name any number of classes.
            val lines = board.classes.values.asSequence().flatten().filter { it.length <= MAX_CHARS }.distinct().take(MAX_LINES).toList()
            Sheet.make(lines, emptyList(), identity).rows
                .mapNotNull { row ->
                    (row.reading as? Reading.NotSure)?.takeIf { it.why == Why.UNKNOWN_WORDS }
                        ?.let { Triple(clean(it.words.joinToString(" ")), board.date, clean(row.shown)) }
                }
                .filter { it.first.isNotEmpty() }
        }
        return seen.groupBy { it.first }
            .map { (words, at) ->
                val latest = at.maxBy { it.second }
                UnknownWord(words, latest.third, latest.second, at.map { it.second }.distinct().size)
            }
            // Seen on the most days first, then the most recent.
            .sortedWith(compareByDescending<UnknownWord> { it.days }.thenByDescending { it.lastSeen }.thenBy { it.word })
    }

    private fun clean(text: String) = text.replace(marks, "").replace(breaks, " ").replace(Regex("""\s+"""), " ").trim()
}
