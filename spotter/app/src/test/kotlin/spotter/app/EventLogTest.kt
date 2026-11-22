package spotter.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import spotter.app.log.EventLog
import spotter.app.log.LogDatabase
import spotter.coach.BoardLine
import spotter.coach.Identity
import spotter.coach.Journal
import spotter.coach.JournalEntry
import spotter.coach.Load
import spotter.coach.Reading
import spotter.coach.Vocabulary
import java.time.LocalDate

// An invented lifter's log of public board lines, through the real Room database.
@RunWith(AndroidJUnit4::class)
class EventLogTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LogDatabase::class.java)
        .allowMainThreadQueries().build()
    private val log = EventLog(db)
    private val vocabulary = Vocabulary.starting()
    private val identity = Identity(vocabulary)
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private val day = LocalDate.of(2026, 3, 1)

    @After fun close() = db.close()

    // A row that can't be read costs itself, not the log.
    @Test
    fun aDamagedRowIsSkipped() {
        val press = exercise("8 x chest press")
        log.write(Journal.logged(1, day, "2G", "row 0", "8 x chest press", press, Load(25, 2), 8..8, null, listOf(8, 8, 8)))
        db.events().insert(spotter.app.log.EventRow.of(Journal.logged(2, day, "2G", "row 1", "8 x chest press", press, Load(25, 2), 8..8, null, listOf(8))).copy(kind = "FROM_A_NEWER_BUILD"))
        db.events().insert(spotter.app.log.EventRow.of(Journal.logged(3, day, "2G", "row 2", "8 x chest press", press, Load(25, 2), 8..8, null, listOf(8))).copy(parts = "[{\"movement\":"))
        assertEquals(1, log.entries().size)
    }

    // Written, read back and folded: the coach sees what was logged, as last corrected.
    @Test
    fun whatIsWrittenComesBackAsHistory() {
        val ladder = exercise("10 / 8 / 6 x single arm low row")
        val first = log.write(Journal.logged(1, day, "2G", "row 0", "10 / 8 / 6 x single arm low row", ladder, Load(20, 1), null, listOf(10, 8, 6), listOf(10, 8, 6)))
        val press = exercise("8 x chest press")
        val second = log.write(Journal.logged(2, day, "2G", "row 1", "8 x chest press", press, Load(25, 2), 8..8, null, listOf(8, 8, 7), hurt = true))
        val entries = log.entries()
        assertEquals(listOf(first, second), entries.map { it.id })
        assertEquals(listOf(10, 8, 6), entries[0].ladder)
        val history = Journal.history(entries, vocabulary)
        assertEquals(listOf("low row [arms=single]", "chest press"), history.map { it.key })
        assertEquals(true, history[1].hurt)
        // A correction is a new row; the first stays as it was written.
        log.write(entries[1].copy(id = 0, at = 3, kind = JournalEntry.Kind.CORRECTED, target = second, lb = 20, hurt = false))
        assertEquals(3, log.entries().size)
        assertEquals(25, log.entries()[1].lb)
        assertEquals(listOf(20, 20), Journal.history(log.entries(), vocabulary).map { it.load.lb })
    }
}
