package spotter.app.history

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
)

/** Taking a picked file as the history, and joining it to the log. Plain, so it runs in JVM tests. */
object HistoryPick {
    /** Years of classes fit in a tenth of this; a file beyond it isn't a history. */
    const val MAX_BYTES = 5_000_000

    /**
     * Keeps [bytes] as the history when something in it reads as one, and says what happened.
     * [bytes] is null when the file couldn't be opened; [tooBig] when it ran past [MAX_BYTES].
     * A file that isn't taken leaves the history already kept as it was.
     */
    fun take(bytes: ByteArray?, tooBig: Boolean, store: HistoryStore, identity: Identity, today: LocalDate): HistoryState {
        val before = state(store.result(identity, today))
        if (tooBig) return before.copy(refused = "That file is too big to be a training history.")
        if (bytes == null) return before.copy(refused = "Couldn't open that file.")
        // Malformed bytes become marks, which no row survives: the row is left out, not misread.
        val text = bytes.toString(Charsets.UTF_8)
        if (HistoryImport.read(text, identity, today).rows.isEmpty()) {
            return before.copy(refused = "Nothing in that file could be read as a history." + if (before.read != null) " The one you have is unchanged." else "")
        }
        return try {
            store.save(text)
            state(store.result(identity, today))
        } catch (e: java.io.IOException) {
            before.copy(refused = "Couldn't keep that file on this phone.")
        }
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
