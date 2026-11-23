package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Boards made of public board lines, with invented words for the ones no list has.
class ReviewListTest {
    private val identity = Identity(Vocabulary.starting())
    private fun day(n: Int) = LocalDate.of(2026, 11, n)
    private fun board(n: Int, vararg classes: Pair<String, List<String>>) = DayBoard(day(n), classes.toMap())

    @Test
    fun unknownWordsAreListedWithWhereTheyWereSeen() {
        val boards = listOf(
            board(
                20,
                "2G" to listOf("**Floor Block 1 - 12 minutes**", "* 8 x chest press", "* 8 x zorbing", "* 10 x frobnicated squat (wibble side)"),
                // The other format's board: the words count once for the day, on however many lines.
                "3G" to listOf("**Floor Block 1 - 12 minutes**", "* 8 x Zorbing", "* 12 x zorbing"),
            ),
            board(21, "2G" to listOf("* 8 x deadlift", "* 8 x plonk press")),
            board(22, "2G" to listOf("* 6 x zorbing", "* 8 x deadlift")),
        )
        // Seen on the most days first, then the most recent; a line's unknown words are one
        // thing to settle, kept together and in order, with the latest line they were on.
        assertEquals(
            listOf(
                UnknownWord("zorbing", "6 x zorbing", day(22), 2),
                UnknownWord("plonk press", "8 x plonk press", day(21), 1),
                UnknownWord("frobnicated wibble side", "10 x frobnicated squat (wibble side)", day(20), 1),
            ),
            ReviewList.of(boards, identity),
        )
        // Lines Spotter reads, headings, and lines unsure for a reason no word list could
        // settle (a choice between two exercises) add nothing.
        assertTrue(ReviewList.of(listOf(board(20, "2G" to listOf("**Floor Block 1 - 12 minutes**", "* 8 x chest press", "* 8 x squat or lunge"))), identity).isEmpty())
        assertEquals(Why.A_CHOICE, (identity.read(BoardLine.read("8 x squat or lunge").words) as Reading.NotSure).why)
        assertTrue(ReviewList.of(emptyList(), identity).isEmpty())
    }

    // The boards are strangers' text: a line break or a mark that reverses text is taken out
    // before it reaches the screen, one board of junk doesn't outrank a real word, and a board
    // built to be slow to read is only read so far.
    @Test
    fun aHostileBoardIsBoundedAndCleaned() {
        val broken = ReviewList.of(listOf(board(20, "2G" to listOf("* 8 x zorb\n\n\n\tflib \u202Esquat\u0007"))), identity).single()
        assertEquals(listOf("zorb flib squat", "8 x zorb flib squat"), listOf(broken.word, broken.line))
        // A mark inside a word doesn't make it another word.
        assertEquals(listOf(2), ReviewList.of(listOf(board(20, "2G" to listOf("* 8 x \u200Fzorbing")), board(21, "2G" to listOf("* 8 x zorbing"))), identity).map { it.days })
        val junk = board(22, "2G" to (1..400).map { "* $it x aaa press" })
        val real = (20..21).map { board(it, "2G" to listOf("* 8 x zorbing")) }
        assertEquals(listOf("zorbing" to 2, "aaa press" to 1), ReviewList.of(real + junk, identity).map { it.word to it.days })
        val many = board(22, "2G" to (1..400).map { "* 8 x junk$it press" })
        assertEquals(ReviewList.MAX_LINES, ReviewList.of(listOf(many), identity).size)
    }
}
