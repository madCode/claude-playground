package com.app.carry

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** What [carryState] needs, so the decision can be tested without Android's
 *  storage framework behind it. */
interface Folder {
    /** A folder has been chosen AND the grant to read it still exists. */
    val chosen: Boolean

    /** True when we hold a folder whose grant is gone, which is a different
     *  thing from never having picked one and needs different words. */
    val grantLost: Boolean

    fun list(): List<Pair<String, String>>
    fun read(documentId: String): String?
}

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

    /** Does the system still consider this ours to read? */
    private fun held(tree: Uri): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission
        }
    }.getOrDefault(false)

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
    fun remember(tree: Uri): Boolean {
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

    override fun read(documentId: String): String? = runCatching {
        val tree = uri ?: return null
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
        context.contentResolver.openInputStream(doc)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    private companion object {
        const val KEY = "session_folder"
        const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
