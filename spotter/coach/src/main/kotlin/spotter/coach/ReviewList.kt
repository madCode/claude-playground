package spotter.coach

import java.time.LocalDate

/**
 * The words on a recent board line that no list has, so its row says "not sure" and gets no
 * number (DESIGN.md, "New words go to a review list"). [word] is every such word of the line,
 * in order ("front foot"): together they are one thing to settle, and apart "front" and "foot"
 * would each look like a find. [line] is the latest board line they were seen on, in the
 * board's own words; [rows] is how many lines had them, a line counted once a day however many
 * classes shared it.
 */
data class UnknownWord(val word: String, val line: String, val lastSeen: LocalDate, val rows: Int)

/**
 * The words to review, read off the boards kept on the phone. Only words: a line that is unsure
 * for another reason (two movements side by side, a choice) has nothing a list could learn.
 */
object ReviewList {
    fun of(boards: List<DayBoard>, identity: Identity): List<UnknownWord> {
        // Each (day, line) once: the 2G and 3G boards often share a block.
        val seen = boards.flatMap { board ->
            board.classes.values.flatMap { lines -> Sheet.make(lines, emptyList(), identity).rows }
                .mapNotNull { row ->
                    (row.reading as? Reading.NotSure)?.takeIf { it.why == Why.UNKNOWN_WORDS && it.words.isNotEmpty() }
                        ?.let { Triple(it.words.joinToString(" "), board.date, row.shown) }
                }
                .distinctBy { it.third }
        }
        return seen.groupBy { it.first }
            .map { (words, at) ->
                val latest = at.maxBy { it.second }
                UnknownWord(words, latest.third, latest.second, at.size)
            }
            // The words that cost the most rows first.
            .sortedWith(compareByDescending<UnknownWord> { it.rows }.thenBy { it.word })
    }
}
