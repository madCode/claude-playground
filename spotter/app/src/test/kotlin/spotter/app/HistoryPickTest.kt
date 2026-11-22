package spotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import spotter.app.history.HistoryPick
import spotter.app.history.HistoryState
import spotter.app.history.HistoryStore
import spotter.app.today.Today
import spotter.coach.BoardLine
import spotter.coach.BookedClass
import spotter.coach.Identity
import spotter.coach.Journal
import spotter.coach.Load
import spotter.coach.Reading
import spotter.coach.TodaysBoard
import spotter.coach.Vocabulary
import java.time.LocalDate

// An invented lifter's history file, picked on the phone: nobody's training data.
class HistoryPickTest {
    @get:Rule val tmp = TemporaryFolder()
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private val today = LocalDate.of(2026, 11, 21)
    private val store by lazy { HistoryStore(tmp.root) }

    private fun row(date: String, line: String, weight: Int, extra: String = "") =
        """{"date": "$date", "class": "2G", "line": "$line", "variant": {"implement": "double_dumbbell"}, "weight": $weight, "reps": 8, "prescribed_reps": 8$extra}"""

    private val file = listOf(
        row("2026-11-10", "8 x chest press", 25),
        row("2026-11-10", "8 x zorbing", 20),
        row("2026-11-10", "8 x deadlift", 40, """, "skipped": true"""),
        row("2026-11-10", "8 x push-up", 0),
        row("2026-11-12", "8 x zorbing", 20),
    ).joinToString("\n").toByteArray()

    private fun take(bytes: ByteArray?, tooBig: Boolean = false) = HistoryPick.take(bytes, tooBig, store, identity, today)

    private fun sheet(vararg logged: spotter.coach.JournalEntry): Today.Showing {
        val entries = logged.toList()
        val beside = HistoryPick.beside(store.result(identity, today)?.logged.orEmpty(), entries)
        val board = TodaysBoard.Lines(BookedClass("2G", today, today.atTime(9, 50), "Orange 60 Min 2G"), listOf("* 8 x chest press"))
        return Today.of(board, entries, identity, vocabulary, beside) as Today.Showing
    }

    // The point of it: before the import the row is new; after, it has a number from the file.
    @Test
    fun aPickedHistoryPutsNumbersOnTheSheet() {
        assertEquals("new: go light", sheet().sheet.let { it.glance(it.rows.single()) })
        val state = take(file)
        assertEquals(HistoryState(read = 1, left = listOf("2 not named", "1 skipped", "1 no weight")), state)
        assertEquals("2×30 ▲", sheet().sheet.let { it.glance(it.rows.single()) })
        // Kept across a restart: another store on the same folder reads it.
        assertEquals(1, HistoryStore(tmp.root).result(identity, today)!!.rows.size)
        // Removed, the sheet is as it was.
        store.clear()
        assertEquals(HistoryState(), HistoryPick.state(store.result(identity, today)))
        assertEquals("new: go light", sheet().sheet.let { it.glance(it.rows.single()) })
    }

    // A day in both counts once, as it was logged here: the file's 25 on that day isn't read
    // beside the log's 35, and can't be taken for a later, lighter session.
    @Test
    fun aDayInTheLogIsntReadFromTheFileToo() {
        take(file)
        val press = identity.read(BoardLine.read("8 x chest press").words) as Reading.Known
        val logged = Journal.logged(1, LocalDate.of(2026, 11, 10), "2G", "row", "8 x chest press", press, Load(35, 2), 8..8, null, listOf(8)).copy(id = 1)
        assertEquals("2×40 ▲", sheet(logged).sheet.let { it.glance(it.rows.single()) })
        // Another day's log leaves the file's day in: the later log is the latest word.
        val later = logged.copy(date = LocalDate.of(2026, 11, 14), lb = 20)
        assertEquals(1, HistoryPick.beside(store.result(identity, today)!!.logged, listOf(later)).size)
    }

    // A wrong pick changes nothing and says why.
    @Test
    fun aFileThatIsntAHistoryIsRefused() {
        assertEquals("Nothing in that file could be read as a history.", take("holiday photos".toByteArray()).refused)
        assertEquals(false, store.present)
        take(file)
        val wrong = take(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))
        assertEquals(HistoryState(1, listOf("2 not named", "1 skipped", "1 no weight"), "Nothing in that file could be read as a history. The one you have is unchanged."), wrong)
        assertEquals("That file is too big to be a training history.", take(null, tooBig = true).refused)
        assertEquals("Couldn't open that file.", take(null).refused)
        assertEquals(1, store.result(identity, today)!!.rows.size)
    }

    // The read stops a byte past the limit, whatever the stream goes on to hold.
    @Test
    fun readingStopsPastTheLimit() {
        val endless = object : java.io.InputStream() {
            override fun read() = 'x'.code
        }
        assertEquals(HistoryPick.MAX_BYTES + 1, HistoryPick.read(endless).size)
        assertTrue(HistoryPick.read(file.inputStream()).contentEquals(file))
    }

    // A replaced file is read afresh, and a new day reads the same file again (rows dated today
    // are in tomorrow).
    @Test
    fun theKeptFileIsReadAgainWhenItOrTheDayChanges() {
        take(file)
        assertEquals(1, store.result(identity, today)!!.rows.size)
        take((String(file) + "\n" + row("2026-11-21", "8 x deadlift", 40)).toByteArray())
        assertEquals(2, store.result(identity, today)!!.rows.size)
        assertEquals(1, store.result(identity, today.minusDays(1))!!.rows.size)
    }
}
