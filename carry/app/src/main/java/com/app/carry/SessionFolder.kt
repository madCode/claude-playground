package com.app.carry

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** What [carryState] needs, so the decision can be tested without Android's
 *  storage framework behind it. */
interface Folder {
    /** What the owner picked, in words, so the app can say what it holds.
     *  A grant nobody can see is a grant nobody can audit. */
    val label: String?

    /** A folder has been chosen AND the grant to read it still exists. */
    val chosen: Boolean

    /** True when we hold a folder whose grant is gone, which is a different
     *  thing from never having picked one and needs different words. */
    val grantLost: Boolean

    fun list(): List<Pair<String, String>>
    fun read(documentId: String): String?

    /**
     * The file's bytes, up to [max] + 1 so a caller can tell "too large" from "this size", or
     * null when it can't be read. For the history import, which decodes as strictly as the
     * laptop does; [read] decodes leniently, for the screen.
     */
    fun readBytes(documentId: String, max: Int): ByteArray? = read(documentId)?.encodeToByteArray()

    /** Raw write. Prefer [save], which checks the result. */
    fun write(documentId: String, text: String): Boolean

    /**
     * Writes, reads back, and puts the old content back if what landed is
     * not what was sent.
     *
     * Opening a document for writing TRUNCATES it, so a provider error, a
     * sync replacing the file, or a full disk leaves a zero-length or half
     * written document -- destroying the owner's numbers AND the runner's
     * intel for that day. The first version of this returned `false` for
     * that, which the interface defined as "the write did not happen", and
     * it was the most dangerous thing in the app.
     *
     * SAF offers no atomic replace, so this is verify-and-restore rather
     * than a guarantee: a process killed mid-write still leaves a damaged
     * file, and nothing here can prevent that. What it does give is an
     * honest answer about which of the three things happened.
     */
    fun save(documentId: String, text: String): Saved {
        val before = read(documentId)
        val wrote = write(documentId, text)
        if (wrote && read(documentId) == text) return Saved.YES
        // Something went wrong. Is the file still what it was?
        if (read(documentId) == before) return Saved.NO_FILE_INTACT
        if (before != null && write(documentId, before) && read(documentId) == before) {
            return Saved.NO_FILE_INTACT
        }
        return Saved.NO_FILE_DAMAGED
    }
}

/** Three outcomes, because the owner needs to be told apart "try again"
 *  from "your file needs rescuing". */
enum class Saved { YES, NO_FILE_INTACT, NO_FILE_DAMAGED }

/**
 * The folder the owner picked, and the files in it.
 *
 * Whether we still hold the folder is answered by
 * `contentResolver.persistedUriPermissions`, never by guessing from an
 * empty listing. Those are genuinely different situations -- a grant that
 * died with a reinstall, versus a folder the coach has not written to yet --
 * and telling someone they lost a folder they just correctly picked sends
 * them round the picker again for nothing.
 */
class SessionFolder(private val context: Context) : Folder {

    private val prefs = context.getSharedPreferences("carry", Context.MODE_PRIVATE)

    var uri: Uri?
        get() = prefs.getString(KEY, null)?.let(Uri::parse)
        private set(value) = prefs.edit().apply {
            if (value == null) remove(KEY) else putString(KEY, value.toString())
        }.apply()

    /**
     * Does the system still consider this ours to read AND write?
     *
     * Both, because Carry writes the log back. Checking only read made a
     * read-only tree -- what some cloud providers hand out -- look
     * perfectly usable right up until the save failed, after the workout,
     * with the numbers already typed.
     */
    private fun held(tree: Uri): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
    }.getOrDefault(false)

    /** "primary:Sync/gym" reads as "Sync/gym"; a volume root as "/". */
    override val label: String? get() = uri?.let { tree ->
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':') }
            .getOrNull()?.ifEmpty { "/" }
    }

    override val chosen: Boolean get() = uri?.let(::held) == true
    override val grantLost: Boolean get() = uri?.let { !held(it) } == true

    /**
     * Returns false when the grant could not be persisted. The take throws
     * when the picked tree did not actually grant what is being asked for --
     * a read-only tree from a cloud provider is the realistic case -- and
     * the old code swallowed that and stored the Uri anyway. Everything then
     * worked until the process died, and the next cold start showed the
     * picker again, forever, with nothing to explain it.
     */
    /**
     * Refuses a whole volume.
     *
     * The picker opens with "Internal storage" a row above wherever the
     * owner is going, and a tree grant is RECURSIVE and persistent: one
     * mis-tap would hand this app read and write over every file on the
     * phone, surviving reboots until it is uninstalled. This app exists
     * because its predecessor held a handle wider than its job, so the
     * scope is checked rather than trusted.
     */
    fun isWholeVolume(tree: Uri): Boolean = runCatching {
        DocumentsContract.getTreeDocumentId(tree).substringAfter(':').isEmpty()
    }.getOrDefault(false)

    fun remember(tree: Uri): Boolean {
        if (isWholeVolume(tree)) return false
        // Release the previous one first: re-picking otherwise accumulates
        // grants against the per-package cap.
        uri?.takeIf { it != tree }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, FLAGS) }
        }
        val took = runCatching {
            context.contentResolver.takePersistableUriPermission(tree, FLAGS)
        }.isSuccess
        if (took) uri = tree else forget()
        return took
    }

    fun forget() {
        uri?.let { runCatching { context.contentResolver.releasePersistableUriPermission(it, FLAGS) } }
        uri = null
    }

    /** (document id, display name) for everything in the folder. */
    override fun list(): List<Pair<String, String>> = runCatching {
        val tree = uri ?: return emptyList()
        // getTreeDocumentId throws on anything that is not a tree Uri, and
        // it runs before any query -- so it belongs inside the guard, not
        // before it. A document Uri in the prefs used to crash onResume.
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1)
                    // A provider is allowed to return junk; a null name would
                    // otherwise blow up outside any guard.
                    if (id != null && name != null) add(id to name)
                }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /**
     * "wt" truncates before writing, so a shorter document cannot leave the
     * tail of a longer one behind. The write is still not atomic -- SAF
     * offers nothing that is -- so a sync watching this folder can in
     * principle copy a half-written file. The reader is built for that:
     * an unparseable session is reported as unreadable rather than as an
     * empty day, and keepLastGood holds the last good sheet through it.
     */
    override fun write(documentId: String, text: String): Boolean = runCatching {
        val tree = uri ?: return false
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        context.contentResolver.openOutputStream(doc, "wt")?.use {
            it.write(text.encodeToByteArray())
            true
        } ?: false
    }.getOrDefault(false)

    /**
     * Refuses anything implausibly large rather than reading it.
     *
     * A session is a few KB. The folder is written by another machine and
     * replicated by a sync service, so a runner bug or any oversized file
     * whose name happens to match can arrive — and this text is held three
     * times over (the string, its parse tree, and the draft in saved
     * instance state), where it becomes an OOM or a
     * TransactionTooLargeException that takes the owner's typed numbers
     * with it.
     */
    override fun read(documentId: String): String? = uri?.let { readCapped(it, documentId, MAX_SESSION_BYTES) }

    override fun readBytes(documentId: String, max: Int): ByteArray? =
        uri?.let { tree -> runCatching { readUpTo(tree, documentId, max) }.getOrNull() }

    private fun readCapped(tree: Uri, documentId: String, max: Int): String? = runCatching {
        readUpTo(tree, documentId, max)?.let { if (it.size > max) null else it.decodeToString() }
    }.getOrNull()

    /** At most [max] + 1 bytes, so one more than allowed shows the file is too large. */
    private fun readUpTo(tree: Uri, documentId: String, max: Int): ByteArray? {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        return context.contentResolver.openInputStream(doc)?.use { stream ->
            // Bounded by hand: readNBytes is API 33 and minSdk is 26. Grown as it fills,
            // so a large cap doesn't mean that much held for a small file.
            var buffer = ByteArray(minOf(max + 1, 64 * 1024))
            var filled = 0
            while (filled <= max) {
                if (filled == buffer.size) buffer = buffer.copyOf(minOf(max + 1, buffer.size * 2))
                val n = stream.read(buffer, filled, buffer.size - filled)
                if (n < 0) break
                filled += n
            }
            buffer.copyOf(filled)
        }
    }

    /** The `brain/` subfolder gym-coach writes each sheet's inputs into, or null when there is none yet. */
    fun brain(): BrainFolder? = runCatching {
        val tree = uri ?: return null
        val id = list().firstOrNull { it.second == BRAIN }?.first ?: return null
        SafBrainFolder(tree, id)
    }.getOrNull()

    private inner class SafBrainFolder(private val tree: Uri, private val folderId: String) : BrainFolder {
        override fun list(): List<Pair<String, String>> = runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val name = c.getString(1)
                        if (id != null && name != null) add(id to name)
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())

        // An input carries the owner's whole history, so it is allowed far more than a sheet.
        override fun read(documentId: String): String? = readCapped(tree, documentId, MAX_BRAIN_INPUT_BYTES)

        override fun write(documentId: String, text: String): Boolean = this@SessionFolder.write(documentId, text)

        override fun create(name: String, text: String): Boolean = runCatching {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, folderId)
            val doc = DocumentsContract.createDocument(context.contentResolver, parent, "application/json", name)
                ?: return false
            context.contentResolver.openOutputStream(doc, "wt")?.use {
                it.write(text.encodeToByteArray())
                true
            } ?: false
        }.getOrDefault(false)
    }

    private companion object {
        const val KEY = "session_folder"
        const val BRAIN = "brain"

        /** An input holds the history, at about 200 bytes a set: 4 MB is twenty thousand sets,
         *  far past any real one, and keeps a background read's memory modest. */
        const val MAX_BRAIN_INPUT_BYTES = 4_000_000
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** A real session is a few KB; a megabyte is already absurd. */
        const val MAX_SESSION_BYTES = 1_000_000
    }
}
