package spotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import spotter.app.export.ExportRun
import spotter.app.export.ExportStore
import spotter.app.export.ExportTarget
import spotter.coach.BoardLine
import spotter.coach.Export
import spotter.coach.Identity
import spotter.coach.Journal
import spotter.coach.JournalEntry
import spotter.coach.Load
import spotter.coach.Reading
import spotter.coach.Vocabulary
import java.io.IOException
import java.time.LocalDate

// An invented lifter's log exported to a stand-in folder, with the real store behind it.
@RunWith(AndroidJUnit4::class)
class ExportRunTest {
    private val identity = Identity(Vocabulary.starting())
    private fun exercise(line: String) = identity.read(BoardLine.read(line).words) as Reading.Known
    private val monday = LocalDate.of(2026, 3, 2)

    private class Folder : ExportTarget {
        val files = mutableMapOf<String, String>()
        val writes = mutableListOf<String>()
        var broken = setOf<String>()
        override fun write(name: String, text: String) {
            if (name in broken) throw IOException("no room")
            writes += name
            onWrite()
            files[name] = text
        }
        override fun delete(name: String) {
            if (name in broken) throw SecurityException("grant taken back")
            files.remove(name)
        }
        var unreadable = false
        var onWrite: () -> Unit = {}
        override fun names(): Set<String> = if (unreadable) throw IOException("offline") else files.keys.toSet()
    }

    private val folder = Folder()
    private val store = ExportStore(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("export-test", Context.MODE_PRIVATE))
    private val address = "content://picked/tree"
    private fun run(entries: List<JournalEntry>) = ExportRun(store.written(store.folder!!), folder).run(entries)

    @Before
    fun aFolderIsPicked() {
        store.folder = address
    }

    private fun logged(id: Long, day: LocalDate, cls: String, line: String, lb: Int) =
        Journal.logged(id, day, cls, line, line, exercise(line), Load(lb, 2), 8..8, null, listOf(8)).copy(id = id)

    @Test
    fun onlyWhatChangedIsWrittenAndAnEmptiedClassIsRemoved() {
        val squat = logged(1, monday, "2G", "8 x squat", 30)
        val press = logged(2, monday.plusDays(1), "3G", "8 x chest press", 25)
        assertTrue(run(listOf(squat, press)))
        assertEquals(setOf("session-2026-03-02-2G.json", "session-2026-03-03-3G.json", Export.LOG_FILE), folder.files.keys)
        assertEquals(Export.files(listOf(squat, press)), folder.files)

        // Nothing new: nothing written.
        folder.writes.clear()
        assertTrue(run(listOf(squat, press)))
        assertTrue(folder.writes.isEmpty())

        // Tuesday's row corrected: its session and the log, not Monday's.
        val fixed = press.copy(id = 3, kind = JournalEntry.Kind.CORRECTED, target = 2, lb = 30)
        assertTrue(run(listOf(squat, press, fixed)))
        assertEquals(listOf("session-2026-03-03-3G.json", Export.LOG_FILE), folder.writes)

        // Monday's only row removed: its session file goes, and the log keeps the removal.
        val gone = squat.copy(id = 4, kind = JournalEntry.Kind.REMOVED, target = 1)
        assertTrue(run(listOf(squat, press, fixed, gone)))
        assertEquals(setOf("session-2026-03-03-3G.json", Export.LOG_FILE), folder.files.keys)
        assertEquals(4, folder.files.getValue(Export.LOG_FILE).trimEnd().lines().size)
    }

    // The folder is another app's: a write that fails is said, and tried again next time.
    @Test
    fun aFailedWriteIsReportedAndRetried() {
        val squat = logged(1, monday, "2G", "8 x squat", 30)
        folder.broken = setOf("session-2026-03-02-2G.json")
        assertFalse(run(listOf(squat)))
        // The log still got there.
        assertEquals(setOf(Export.LOG_FILE), folder.files.keys)
        folder.broken = emptySet()
        assertTrue(run(listOf(squat)))
        assertEquals(setOf("session-2026-03-02-2G.json", Export.LOG_FILE), folder.files.keys)
        // A removal that fails keeps the file on the list for next time.
        folder.broken = setOf("session-2026-03-02-2G.json")
        val gone = squat.copy(id = 2, kind = JournalEntry.Kind.REMOVED, target = 1)
        assertFalse(run(listOf(squat, gone)))
        folder.broken = emptySet()
        assertTrue(run(listOf(squat, gone)))
        assertEquals(setOf(Export.LOG_FILE), folder.files.keys)
    }

    // A newly picked folder has none of the files: everything is written again.
    @Test
    fun aNewFolderStartsFromNothing() {
        val squat = logged(1, monday, "2G", "8 x squat", 30)
        assertTrue(run(listOf(squat)))
        store.finished(address, at = 5, ok = true)
        store.folder = "content://another/tree"
        assertEquals(0, store.lastAt)
        folder.writes.clear()
        assertTrue(run(listOf(squat)))
        assertEquals(2, folder.writes.size)
    }

    // Another folder picked while a run is writing to the first: that run's files are in the old
    // folder, so the new one mustn't be taken as filled, nor called copied.
    @Test
    fun aFolderPickedMidRunStillGetsEveryFile() {
        val squat = logged(1, monday, "2G", "8 x squat", 30)
        val running = ExportRun(store.written(address), folder)
        folder.onWrite = { store.folder = "content://another/tree" }
        running.run(listOf(squat))
        store.finished(address, at = 5, ok = true)
        assertEquals(0, store.lastAt)
        folder.onWrite = {}
        folder.writes.clear()
        assertTrue(run(listOf(squat)))
        assertEquals(2, folder.writes.size)
        // A run for the old folder starting now knows nothing of the new one's files.
        assertTrue(store.written(address).written.isEmpty())
        store.named(address, "Old")
        assertEquals(null, store.folderName)
    }

    // A file deleted from the folder by hand comes back, though the log hasn't changed.
    @Test
    fun aFileGoneFromTheFolderIsWrittenAgain() {
        val squat = logged(1, monday, "2G", "8 x squat", 30)
        assertTrue(run(listOf(squat)))
        folder.files.remove("session-2026-03-02-2G.json")
        folder.writes.clear()
        assertTrue(run(listOf(squat)))
        assertEquals(listOf("session-2026-03-02-2G.json"), folder.writes)
        // A folder that can't be read is a failed copy, and nothing is written blind.
        folder.unreadable = true
        folder.files.clear()
        folder.writes.clear()
        assertFalse(run(listOf(squat)))
        assertTrue(folder.writes.isEmpty())
    }
}
