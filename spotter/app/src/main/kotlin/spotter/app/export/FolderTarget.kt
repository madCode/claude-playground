package spotter.app.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * The picked folder, through Android's document tree: a folder in Downloads, on a card, or a
 * synced one another app provides. Spotter holds a lasting grant to this one folder and no storage
 * permission. What the other app does with a file (sync it, index it) is its own business:
 * DEVICE.md says how to check the files arrive.
 */
class FolderTarget(private val context: Context, private val tree: Uri) : ExportTarget {
    private val folder: Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    override fun write(name: String, text: String) {
        val file = find(name) ?: DocumentsContract.createDocument(context.contentResolver, folder, MIME, name)
            ?: throw IOException("couldn't create $name")
        // "wt" truncates: a shorter file mustn't keep the old one's tail.
        (context.contentResolver.openOutputStream(file, "wt") ?: throw IOException("couldn't open $name")).use { it.write(text.toByteArray()) }
    }

    override fun delete(name: String) {
        find(name)?.let { DocumentsContract.deleteDocument(context.contentResolver, it) }
    }

    // By the name shown: a provider may store a file under another id than its name.
    private fun find(name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return null
    }

    companion object {
        // Plain bytes: with a JSON type some providers add a second extension to ".jsonl".
        private const val MIME = "application/octet-stream"

        /** The folder's name for the screen: "Download/Spotter" from "primary:Download/Spotter". */
        fun name(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifEmpty { "the folder you picked" }
    }
}
