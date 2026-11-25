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
        row("2026-11-10", "8 x chest press", 25, """, "easy": true"""),
        row("2026-11-10", "8 x zorbing", 20),
        row("2026-11-10", "8 x deadlift", 40, """, "skipped": true"""),
        row("2026-11-10", "8 x push-up", 0),
        row("2026-11-12", "8 x zorbing", 20),
    ).joinToString("\n").toByteArray()

    private fun take(bytes: ByteArray?, tooBig: Boolean = false): HistoryState {
        HistoryPick.take(bytes, tooBig, store, identity, today)
        return HistoryPick.state(store, identity, today)
    }

    private fun sheet(vararg logged: spotter.coach.JournalEntry): Today.Showing {
        val entries = logged.toList()
        val beside = HistoryPick.beside(store.result(identity, today)?.logged.orEmpty(), entries)
        val board = TodaysBoard.Lines(BookedClass("2G", today, today.atTime(9, 50), "Orange 60 Min 2G"), listOf("* 8 x chest press"))
        return Today.of(board, entries, identity, vocabulary, beside) as Today.Showing
    }

    // The owner's counts, picked after the history: a row that didn't say how many dumbbells
    // takes its count from them, the card says so, and both go when the history is removed.
    @Test
    fun pickedCountsFillWhatTheHistoryDidNotSay() {
        val unsaid = """{"date": "2026-11-10", "class": "2G", "exercise": "squat", "line": "8 x squat", "variant": {"implement": null}, "weight": 40, "reps": 8, "prescribed_reps": 8}"""
        take(unsaid.toByteArray())
        assertEquals(listOf<Int?>(null), store.result(identity, today)!!.logged.map { it.load.count })
        fun counts(text: String?, tooBig: Boolean = false): HistoryState {
            HistoryPick.takeCounts(text?.toByteArray(), tooBig, store, identity, today)
            return HistoryPick.state(store, identity, today)
        }
        val good = "schema: dumbbell-counts/1\nmovements:\n  squat: 2\n  halo: 1\n"
        assertEquals(HistoryState(read = 1, counts = 2, ruled = 1), counts(good))
        assertEquals(listOf<Int?>(2), store.result(identity, today)!!.logged.map { it.load.count })
        // Kept across a restart, and a history picked afterwards is read with them too.
        assertEquals(2, HistoryStore(tmp.root).counts()?.size)
        assertEquals(1, take(unsaid.toByteArray()).ruled)
        // A file that isn't one says why and changes nothing.
        val refused = counts("schema: dumbbell-counts/1\nmovements:\n  squat: 3\n")
        assertEquals("A count should be 1 or 2: \"squat: 3\" The counts you have are unchanged.", refused.refused)
        assertEquals(2 to 1, refused.counts to refused.ruled)
        assertEquals("That file is too big to be a list of dumbbell counts.", counts(null, tooBig = true).refused)
        assertEquals("Couldn't open that file.", counts(null).refused)
        // The history file picked by mistake.
        assertTrue(counts(unsaid).refused!!.startsWith("That isn't a dumbbell counts file"))
        // Not UTF-8: a name with a mark in it would be a rule that never applies.
        HistoryPick.takeCounts("schema: dumbbell-counts/1\nmovements:\n  d\u00e9adlift: 2\n".toByteArray(Charsets.ISO_8859_1), false, store, identity, today)
        assertEquals("That file isn't saved as UTF-8 text. The counts you have are unchanged.", store.refused)
        // A kept file this version can't read is said, not silently dropped.
        java.io.File(tmp.root, "dumbbell_counts.yaml").writeText("schema: dumbbell-counts/9\n")
        assertEquals(Triple(null, 0, true), HistoryPick.state(HistoryStore(tmp.root), identity, today).let { Triple(it.counts, it.ruled, it.countsUnread) })
        store.clear()
        assertEquals(null, store.counts())
        // With no history there is nothing for the counts to be for.
        assertEquals("Import a history first: the counts are for its rows.", counts(good).refused)
        assertEquals(false, store.hasCounts())
        take(unsaid.toByteArray())
        assertEquals(listOf<Int?>(null), store.result(identity, today)!!.logged.map { it.load.count })
    }

    // A word answered since the file was read: the kept history is read again by the new
    // lists, the same day, and by the old ones again when the answer is undone.
    @Test
    fun theKeptHistoryIsReadAgainWhenTheListsChange() {
        assertEquals(1, take(file).read)
        val answered = Identity(vocabulary.with(listOf(spotter.coach.WordAnswer("zorbing", "squat"))))
        assertEquals(3, store.result(answered, today)!!.rows.size)
        assertEquals(1, store.result(identity, today)!!.rows.size)
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
        val logged = Journal.logged(1, LocalDate.of(2026, 11, 10), "2G", "row", "8 x chest press", press, Load(35, 2), 8..8, null, listOf(8), easy = true).copy(id = 1)
        assertEquals("2×40 ▲", sheet(logged).sheet.let { it.glance(it.rows.single()) })
        assertTrue(HistoryPick.beside(store.result(identity, today)!!.logged, listOf(logged)).isEmpty())
        // Another day's log leaves the file's day in: the later log is the latest word.
        val later = logged.copy(date = LocalDate.of(2026, 11, 14), lb = 20)
        assertEquals(1, HistoryPick.beside(store.result(identity, today)!!.logged, listOf(later)).size)
    }

    // A wrong pick changes nothing and says why.
    @Test
    fun aFileThatIsntAHistoryIsRefused() {
        assertEquals("Nothing in that file could be read as a history.", take("holiday photos".toByteArray()).refused)
        assertEquals(null, store.result(identity, today))
        take(file)
        val wrong = take(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))
        assertEquals(HistoryState(1, listOf("2 not named", "1 skipped", "1 no weight"), "Nothing in that file could be read as a history. The one you have is unchanged."), wrong)
        assertEquals("That file is too big to be a training history.", take(null, tooBig = true).refused)
        assertEquals("Couldn't open that file.", take(null).refused)
        assertEquals(1, store.result(identity, today)!!.rows.size)
        // A pick that is taken clears what the last one said.
        assertEquals(null, take(file).refused)
    }

    // One pick at a time, said while it lasts; and every change is announced to the screen.
    @Test
    fun aPickUnderWayIsSaidAndASecondIsTurnedAway() {
        val before = store.changed.value
        val ticket = store.begin()!!
        assertEquals(null, store.begin())
        assertEquals(true, HistoryPick.state(store, identity, today).reading)
        store.end(ticket)
        assertEquals(false, HistoryPick.state(store, identity, today).reading)
        assertEquals(before + 2, store.changed.value)
        assertTrue(store.begin() != null)
    }

    // A pick that never ends (a drive that doesn't answer) can be stopped: the screen is free
    // again, and whatever that pick reads afterwards is dropped, as is its ending.
    @Test
    fun aStoppedPickIsDropped() {
        val stalled = store.begin()!!
        store.stop()
        assertEquals(false, HistoryPick.state(store, identity, today).reading)
        val next = store.begin()!!
        // The stalled read comes back at last: not kept, not said, and it doesn't end the new pick.
        HistoryPick.take(file, false, store, identity, today, wanted = { store.current(stalled) })
        HistoryPick.take(null, false, store, identity, today, wanted = { store.current(stalled) })
        store.end(stalled)
        assertEquals(HistoryState(reading = true), HistoryPick.state(store, identity, today))
        // Stopped while the file was being read through: still not kept.
        var asked = 0
        HistoryPick.take(file, false, store, identity, today, wanted = { asked++ == 0 })
        assertEquals(listOf(2, null), listOf(asked, HistoryPick.state(store, identity, today).read))
        HistoryPick.take(file, false, store, identity, today, wanted = { store.current(next) })
        store.end(next)
        assertEquals(1, HistoryPick.state(store, identity, today).read)
    }

    // No stray copy is left by a process killed mid-save, and Remove takes one too.
    @Test
    fun noPartialCopyOutlivesAKillOrARemoval() {
        val stray = java.io.File(tmp.root, "history.jsonl.tmp").apply { writeText("left by a killed process") }
        assertEquals(null, store.result(identity, today))
        assertEquals(false, stray.exists())
        take(file)
        assertEquals(listOf("history.jsonl"), tmp.root.list()!!.toList())
        stray.writeText("again")
        store.clear()
        assertTrue(tmp.root.list()!!.isEmpty())
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
