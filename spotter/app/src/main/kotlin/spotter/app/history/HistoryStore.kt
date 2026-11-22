package spotter.app.history

import spotter.coach.HistoryImport
import spotter.coach.Identity
import java.io.File
import java.time.LocalDate

/**
 * The imported history: a copy of the picked file, kept in the app's own storage beside the log,
 * never in it. The log is what was logged here; the import is another app's record, read again
 * each time by the rules as they are now, so a better reader later reads more of the same file.
 * It is left out of Android backup: the file it came from is the copy to keep.
 */
class HistoryStore(dir: File) {
    private val file = File(dir, "history.jsonl")
    private var read: Pair<Long, HistoryImport.Result>? = null

    val present: Boolean get() = file.isFile

    /** Replaces the kept copy, whole: a half-written file would read as a shorter history. */
    @Synchronized
    fun save(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw java.io.IOException("couldn't keep the history")
        }
        read = null
    }

    @Synchronized
    fun clear() {
        file.delete()
        read = null
    }

    /** The history as read today, or null when none is kept. Read once per file and day. */
    @Synchronized
    fun result(identity: Identity, today: LocalDate): HistoryImport.Result? {
        if (!file.isFile) return null
        val stamp = file.lastModified() * 31 + today.toEpochDay()
        read?.takeIf { it.first == stamp }?.let { return it.second }
        return HistoryImport.read(file.readText(), identity, today).also { read = stamp to it }
    }
}
