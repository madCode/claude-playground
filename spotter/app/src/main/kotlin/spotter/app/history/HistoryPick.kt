package spotter.app.history

import spotter.coach.DumbbellCounts
import spotter.coach.HistoryImport
import spotter.coach.Identity
import spotter.coach.Journal
import spotter.coach.JournalEntry
import spotter.coach.Logged
import java.time.LocalDate

/** What the home screen says about the imported history. */
data class HistoryState(
    /** Rows read into history; null when none is imported. */
    val read: Int? = null,
    /** Rows left out, by reason, most first: "409 no weight". */
    val left: List<String> = emptyList(),
    /** Why the last pick wasn't taken, in a sentence. */
    val refused: String? = null,
    /** A picked file is being read. */
    val reading: Boolean = false,
    /** Lifts the owner's dumbbell counts rule on; null when no counts file is kept. */
    val counts: Int? = null,
    /** Rows that took their count from it. */
    val ruled: Int = 0,
    /** Rows whose count nobody said, read at the lift's usual number. */
    val countAssumed: Int = 0,
    /** A counts file is kept that this version can't read in full, so none of it is used. */
    val countsUnread: Boolean = false,
)

/** Taking a picked file as the history, and joining it to the log. Plain, so it runs in JVM tests. */
object HistoryPick {
    /** Years of classes fit in a tenth of this; a file beyond it isn't a history. */
    const val MAX_BYTES = 5_000_000

    /**
     * Keeps [bytes] as the history when something in it reads as one; otherwise leaves the
     * history already kept as it was and says why in [HistoryStore.refused]. [bytes] is null when
     * the file couldn't be opened; [tooBig] when it ran past [MAX_BYTES].
     */
    fun take(
        bytes: ByteArray?, tooBig: Boolean, store: HistoryStore, identity: Identity, today: LocalDate,
        /** False once the pick was stopped: what it read is then dropped, unsaid. */
        wanted: () -> Boolean = { true },
    ) {
        if (!wanted()) return
        store.refused = when {
            tooBig -> "That file is too big to be a training history."
            bytes == null -> "Couldn't open that file."
            else -> {
                // Malformed bytes become marks, which no row survives: left out, not misread.
                val text = bytes.toString(Charsets.UTF_8)
                val result = HistoryImport.read(text, identity, today, everyRow = false, counts = store.counts() ?: DumbbellCounts.NONE)
                if (result.rows.isEmpty()) {
                    "Nothing in that file could be read as a history." + if (store.result(identity, today) != null) " The one you have is unchanged." else ""
                } else {
                    try {
                        if (!wanted()) return
                        store.save(text, result, today)
                        null
                    } catch (e: java.io.IOException) {
                        "Couldn't keep that file on this phone."
                    }
                }
            }
        }
    }

    /**
     * Keeps [bytes] as the dumbbell counts when all of it reads as one (docs/COUNTS-FORMAT.md);
     * otherwise leaves what is kept and says why. The sentence may quote the start of the line
     * at fault: the owner's own file, on the owner's own screen.
     */
    fun takeCounts(bytes: ByteArray?, tooBig: Boolean, store: HistoryStore, identity: Identity, today: LocalDate, wanted: () -> Boolean = { true }) {
        if (!wanted()) return
        store.refused = when {
            // The history was removed while the picker was open: counts kept alone could be
            // neither seen nor removed.
            store.result(identity, today) == null -> "Import a history first: the counts are for its rows."
            tooBig -> "That file is too big to be a list of dumbbell counts."
            bytes == null -> "Couldn't open that file."
            else -> {
                val text = bytes.toString(Charsets.UTF_8)
                try {
                    // Bytes that aren't UTF-8 become marks: a name with one would never match a row.
                    require('\uFFFD' !in text) { "That file isn't saved as UTF-8 text." }
                    DumbbellCounts.parse(text)
                    if (!wanted()) return
                    store.saveCounts(text)
                    null
                } catch (e: IllegalArgumentException) {
                    (e.message ?: "That isn't a dumbbell counts file.") + if (store.counts() != null) " The counts you have are unchanged." else ""
                } catch (e: java.io.IOException) {
                    "Couldn't keep that file on this phone."
                }
            }
        }
    }

    /** What the screen says: the history as kept, the last refusal, and a pick under way. */
    fun state(store: HistoryStore, identity: Identity, today: LocalDate): HistoryState {
        val result = store.result(identity, today)
        return state(result).copy(
            refused = store.refused, reading = store.reading,
            counts = store.counts()?.size, ruled = result?.rows.orEmpty().count { it.countRuled },
        ).let { it.copy(countsUnread = it.counts == null && store.hasCounts()) }
    }

    /** Up to one byte past [MAX_BYTES]: enough to know a file is too big without holding all of it. */
    fun read(stream: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() <= MAX_BYTES) {
            val n = stream.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun state(result: HistoryImport.Result?): HistoryState = if (result == null) HistoryState() else HistoryState(
        read = result.rows.size,
        left = result.left.entries.sortedByDescending { it.value }.map { "${it.value} ${words(it.key)}" },
        countAssumed = result.rows.count { it.countAssumed },
    )

    private fun words(left: HistoryImport.Left) = when (left) {
        HistoryImport.Left.UNREADABLE -> "unreadable"
        HistoryImport.Left.TOO_MANY -> "past the limit"
        HistoryImport.Left.SKIPPED -> "skipped"
        HistoryImport.Left.NO_WEIGHT -> "no weight"
        HistoryImport.Left.OFF_RACK -> "off the rack"
        HistoryImport.Left.EASIER -> "easier version"
        HistoryImport.Left.BROKE_DOWN -> "form broke down"
        HistoryImport.Left.NOT_NAMED -> "not named"
        HistoryImport.Left.REPS_UNCLEAR -> "reps unclear"
    }

    /**
     * The imported rows the coach may read beside the log: none from a day the log has, so a class
     * logged here and also in the imported file counts once, as it was logged here.
     */
    fun beside(imported: List<Logged>, entries: List<JournalEntry>): List<Logged> {
        val logged = Journal.live(entries).map { it.date }.toSet()
        return imported.filter { it.date !in logged }
    }
}
