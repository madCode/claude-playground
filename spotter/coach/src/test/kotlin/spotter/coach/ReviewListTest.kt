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
                // The same block in the other format: its lines count once for the day.
                "3G" to listOf("**Floor Block 1 - 12 minutes**", "* 8 x zorbing", "* 12 x zorbing lunge"),
            ),
            board(22, "2G" to listOf("* 6 x zorbing", "* 8 x deadlift")),
        )
        val words = ReviewList.of(boards, identity)
        // The word that cost the most rows first, with the latest line it was on.
        // A line's unknown words are one thing to settle, kept together and in order.
        assertEquals(
            listOf(UnknownWord("zorbing", "6 x zorbing", day(22), 3), UnknownWord("frobnicated wibble side", "10 x frobnicated squat (wibble side)", day(20), 1)),
            words,
        )
        // Lines Spotter reads, headings, and lines unsure for a reason no word list could
        // settle (a choice between two exercises) add nothing.
        assertTrue(ReviewList.of(listOf(board(20, "2G" to listOf("**Floor Block 1 - 12 minutes**", "* 8 x chest press", "* 8 x squat or lunge"))), identity).isEmpty())
        assertEquals(Why.A_CHOICE, (identity.read(BoardLine.read("8 x squat or lunge").words) as Reading.NotSure).why)
        assertTrue(ReviewList.of(emptyList(), identity).isEmpty())
    }
}
