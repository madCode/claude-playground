package com.app.jekyllposter.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file in the Obsidian vault: its path from the vault's top, as Obsidian names it, and where to read it. */
data class VaultFile(val path: String, val uri: Uri)

/**
 * Every file in the vault folder the writer picked, for finding a note's `![[photo.jpg]]`. Walked
 * through the Storage Access Framework, the only way to read a folder on Android without asking
 * for all of the phone's files. Hidden folders (`.obsidian`, `.trash`) are skipped; so is anything
 * past [limit] files, so a huge vault can't stall the editor.
 */
suspend fun listVault(context: Context, tree: String, limit: Int = 20_000): List<VaultFile> = withContext(Dispatchers.IO) {
    val treeUri = Uri.parse(tree)
    val out = mutableListOf<VaultFile>()
    val folders = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(treeUri) to ""))
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    while (folders.isNotEmpty() && out.size < limit) {
        val (id, prefix) = folders.removeFirst()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val childId = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val path = prefix + name
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) folders += childId to "$path/"
                else out += VaultFile(path, DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
            }
        }
    }
    out
}
