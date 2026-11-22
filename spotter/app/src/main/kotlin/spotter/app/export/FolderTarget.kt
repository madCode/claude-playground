package spotter.app.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/**
 * The picked folder, through Android's document tree: a folder in Downloads, on a card, or a
 * synced one another app provides. Spotter holds a lasting grant to this one folder and no storage
 * permission. What the other app does with a file (sync it, index it) is its own business:
 * DEVICE.md says how to check the files arrive.
 *
 * Made for one run: the folder is read once, and files are found by the name shown, since a
 * provider may store a file under another id than its name.
 */
class FolderTarget(private val context: Context, private val tree: Uri) : ExportTarget {
    private val resolver = context.contentResolver
    private val folder: Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private val files: MutableMap<String, Uri> by lazy {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder))
        val found = mutableMapOf<String, Uri>()
        // No answer is not an empty folder: taking it for one would make a second copy of every
        // file ("name (1).json") in a provider that is only offline.
        val cursor = resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?: throw IOException("couldn't read the folder")
        cursor.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                found.getOrPut(name) { DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) }
            }
        }
        found
    }

    override fun names(): Set<String> = files.keys.toSet()

    override fun write(name: String, text: String) {
        val file = files[name] ?: create(name)
        // "wt" truncates: a shorter file mustn't keep the old one's tail.
        (resolver.openOutputStream(file, "wt") ?: throw IOException("couldn't open $name")).use { it.write(text.toByteArray()) }
        files[name] = file
    }

    override fun delete(name: String) {
        val file = files[name] ?: return
        if (!DocumentsContract.deleteDocument(resolver, file)) throw IOException("couldn't remove $name")
        files.remove(name)
    }

    private fun create(name: String): Uri {
        val made = DocumentsContract.createDocument(resolver, folder, mime(name), name) ?: throw IOException("couldn't create $name")
        // A provider may add an extension of its own for the type; the file is found by its name
        // next time, so it has to keep it.
        if (displayName(resolver, made) == name) return made
        return runCatching { DocumentsContract.renameDocument(resolver, made, name) }.getOrNull()
            ?: run {
                runCatching { DocumentsContract.deleteDocument(resolver, made) }
                throw IOException("couldn't name $name")
            }
    }

    companion object {
        // The type that matches the extension, where there is one, is the type a provider leaves
        // the name alone for; ".jsonl" has none, and plain bytes is the usual "as named".
        private fun mime(name: String) = if (name.endsWith(".json")) "application/json" else "application/octet-stream"

        private fun displayName(resolver: android.content.ContentResolver, document: Uri): String? = runCatching {
            resolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

        /**
         * The folder's name for the screen, as its own app shows it ("Spotter"). The address isn't
         * used: outside the phone's own storage it is an id nobody could read.
         */
        fun name(context: Context, tree: Uri): String =
            displayName(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
                ?.takeIf { it.isNotBlank() } ?: "the folder you chose"
    }
}
