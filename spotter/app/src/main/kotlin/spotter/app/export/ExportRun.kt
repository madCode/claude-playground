package spotter.app.export

import spotter.coach.Export
import spotter.coach.JournalEntry
import java.security.MessageDigest

/** Where the export's files go: the folder the person picked. */
interface ExportTarget {
    /** Writes [text] as the file [name], replacing one of that name. */
    fun write(name: String, text: String)

    /** Removes the file [name]; nothing to do when it isn't there. */
    fun delete(name: String)
}

/**
 * One export of the log to the picked folder (DESIGN.md, "Backup"). Only files whose text has
 * changed since the last run are written, and a session file whose class no longer has rows is
 * removed, so the folder holds what the log says and no more. Only files this app wrote are ever
 * touched: what was written is remembered in [Store], and the folder is never listed.
 */
class ExportRun(private val store: Store, private val target: ExportTarget) {
    interface Store {
        /** Each file written so far, with a digest of its text. */
        var written: Map<String, String>
    }

    /** True when the folder now matches the log; false when something couldn't be written. */
    fun run(entries: List<JournalEntry>, unreadable: Int = 0): Boolean {
        val files = Export.files(entries, unreadable)
        val before = store.written
        val now = before.toMutableMap()
        var ok = true
        // What a failed write leaves is retried next time: only what succeeded is remembered.
        for ((name, text) in files) {
            val digest = digest(text)
            if (before[name] == digest) continue
            if (attempt { target.write(name, text) }) now[name] = digest else ok = false
        }
        for (name in before.keys - files.keys) {
            if (attempt { target.delete(name) }) now.remove(name) else ok = false
        }
        store.written = now
        return ok
    }

    // The folder is another app's: it can be gone, full, or no longer ours to write to.
    private fun attempt(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (e: Exception) {
        false
    }

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
