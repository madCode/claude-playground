package spotter.app.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import spotter.coach.HistoryImport
import spotter.coach.Identity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate

/**
 * The imported history: a copy of the picked file, kept in the app's own storage beside the log,
 * never in it. The log is what was logged here; the import is another app's record, read again
 * by the rules as they are now, so a better reader later reads more of the same file. It is left
 * out of Android backup: the file it came from is the copy to keep.
 *
 * One per process ([shared]): a pick outlives the screen that started it, so what it found, and
 * that it is still reading, are held here for whichever screen is showing when it ends.
 */
class HistoryStore(dir: File) {
    private val file = File(dir, "history.jsonl")
    private val tmp = File(dir, "history.jsonl.tmp")
    private var read: Pair<LocalDate, HistoryImport.Result>? = null

    /** Why the last pick wasn't taken; null once one is. Gone with the process, like any toast. */
    @Volatile var refused: String? = null

    /** True while a picked file is being read. */
    @Volatile var reading: Boolean = false
        private set

    private val changes = MutableStateFlow(0)

    /** Moves whenever the history, [refused] or [reading] changes. */
    val changed: StateFlow<Int> get() = changes

    /** Marks a pick under way, or says one already is: two at once would race to be the history. */
    @Synchronized
    fun begin(): Boolean {
        if (reading) return false
        reading = true
        changes.value++
        return true
    }

    fun end() {
        reading = false
        changes.value++
    }

    /**
     * Replaces the kept copy, whole and on disk before it takes the name: a half-written file
     * would read as a shorter history. [result] is [text] as read on [today], kept so the file
     * isn't parsed a second time.
     */
    @Synchronized
    fun save(text: String, result: HistoryImport.Result, today: LocalDate) {
        try {
            FileOutputStream(tmp).use {
                it.write(text.toByteArray())
                it.fd.sync()
            }
            if (!tmp.renameTo(file)) throw IOException("couldn't keep the history")
            read = today to result
        } finally {
            // Never a stray copy of someone's training log, whatever went wrong.
            tmp.delete()
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        tmp.delete()
        read = null
    }

    /** The history as read on [today], or null when none is kept. Parsed once a day. */
    @Synchronized
    fun result(identity: Identity, today: LocalDate): HistoryImport.Result? {
        // Left by a process killed mid-save.
        tmp.delete()
        if (!file.isFile) return null
        read?.takeIf { it.first == today }?.let { return it.second }
        return HistoryImport.read(file.readText(), identity, today, everyRow = false).also { read = today to it }
    }

    companion object {
        private var one: HistoryStore? = null

        /** Outlives any one screen: a pick started before a rotation finishes after it. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Synchronized
        fun shared(dir: File): HistoryStore = one ?: HistoryStore(dir).also { one = it }
    }
}
