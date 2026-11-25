package spotter.app.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import spotter.coach.DumbbellCounts
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
 * out of Android backup, as are the dumbbell counts kept with it: the files they came from are
 * the copies to keep.
 *
 * One per process ([shared]): a pick outlives the screen that started it, so what it found, and
 * that it is still reading, are held here for whichever screen is showing when it ends.
 */
class HistoryStore(dir: File) {
    private val file = File(dir, "history.jsonl")
    private val tmp = File(dir, "history.jsonl.tmp")
    // The owner's dumbbell counts (docs/COUNTS-FORMAT.md), kept the same way: the history is
    // read with them, so a count the rows don't give comes from the owner and not a guess.
    private val countsFile = File(dir, "dumbbell_counts.yaml")
    // The file as read on a day by one reader: another day, or lists that have changed since
    // (a word answered), reads it again.
    private var read: Triple<LocalDate, Identity, HistoryImport.Result>? = null

    /** Why the last pick wasn't taken; null once one is. Gone with the process, like any toast. */
    @Volatile var refused: String? = null

    /** True while a picked file is being read. */
    @Volatile var reading: Boolean = false
        private set

    private val changes = MutableStateFlow(0)

    /** Moves whenever the history, [refused] or [reading] changes. */
    val changed: StateFlow<Int> get() = changes

    // Which pick is under way: one stopped or overtaken finds its ticket no longer current,
    // and what it read is dropped.
    private var ticket = 0

    /**
     * Marks a pick under way and gives its ticket, or null when one already is: two at once would
     * race to be the history.
     */
    @Synchronized
    fun begin(): Int? {
        if (reading) return null
        reading = true
        changes.value++
        return ++ticket
    }

    /** Whether the pick holding [of] is still the one under way. */
    @Synchronized
    fun current(of: Int): Boolean = reading && of == ticket

    /** The pick holding [of] is over; nothing happens when it was stopped first. */
    @Synchronized
    fun end(of: Int) {
        if (!current(of)) return
        reading = false
        changes.value++
    }

    /**
     * Gives up on the pick under way: a file on a drive that never answers would otherwise keep
     * the screen waiting for as long as the app lives. Its read may go on, but is no longer wanted.
     */
    @Synchronized
    fun stop() {
        ticket++
        reading = false
        changes.value++
    }

    /** Says something changed that a screen should show. */
    @Synchronized
    fun touch() {
        changes.value++
    }

    /**
     * Replaces the kept copy, whole and on disk before it takes the name: a half-written file
     * would read as a shorter history. [result] is [text] as read on [today] by [identity], kept so the file
     * isn't parsed a second time.
     */
    @Synchronized
    fun save(text: String, result: HistoryImport.Result, today: LocalDate, identity: Identity) {
        try {
            FileOutputStream(tmp).use {
                it.write(text.toByteArray())
                it.fd.sync()
            }
            if (!tmp.renameTo(file)) throw IOException("couldn't keep the history")
            read = Triple(today, identity, result)
        } finally {
            // Never a stray copy of someone's training log, whatever went wrong.
            tmp.delete()
        }
    }

    /** Replaces the kept dumbbell counts with [text], which the caller has read in full. */
    @Synchronized
    fun saveCounts(text: String) {
        try {
            FileOutputStream(tmp).use {
                it.write(text.toByteArray())
                it.fd.sync()
            }
            if (!tmp.renameTo(countsFile)) throw IOException("couldn't keep the counts")
            // The history reads differently with them.
            read = null
        } finally {
            tmp.delete()
        }
    }

    /**
     * The kept counts, or null when none are. A file this version can no longer read in full
     * counts as none: half a ruling is a count nobody gave.
     */
    @Synchronized
    fun counts(): DumbbellCounts? =
        if (!countsFile.isFile) null else runCatching { DumbbellCounts.parse(countsFile.readText()) }.getOrNull()

    /** Whether a counts file is kept, readable or not. */
    @Synchronized
    fun hasCounts(): Boolean = countsFile.isFile

    /** The counts go with the history they were written for. */
    @Synchronized
    fun clear() {
        file.delete()
        countsFile.delete()
        tmp.delete()
        read = null
    }

    /** The history as read on [today], or null when none is kept. Parsed once a day. */
    @Synchronized
    fun result(identity: Identity, today: LocalDate): HistoryImport.Result? {
        // Left by a process killed mid-save.
        tmp.delete()
        if (!file.isFile) return null
        read?.takeIf { it.first == today && it.second === identity }?.let { return it.third }
        return HistoryImport.read(file.readText(), identity, today, everyRow = false, counts = counts() ?: DumbbellCounts.NONE).also { read = Triple(today, identity, it) }
    }

    companion object {
        private var one: HistoryStore? = null

        /** Outlives any one screen: a pick started before a rotation finishes after it. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Synchronized
        fun shared(dir: File): HistoryStore = one ?: HistoryStore(dir).also { one = it }
    }
}
