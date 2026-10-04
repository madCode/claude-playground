package com.app.jekyllposter.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.app.jekyllposter.core.obsidian.ObsidianNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file in the Obsidian vault: its path from the vault's top, as Obsidian names it, and where to read it. */
data class VaultFile(val path: String, val uri: Uri)

/** The vault's images; [complete] is false when the walk stopped early, in a huge vault. */
data class VaultImages(val files: List<VaultFile>, val complete: Boolean = true)

/**
 * The images in the vault folder the writer picked, for finding a note's `![[photo.jpg]]`. Walked
 * through the Storage Access Framework, the only way to read a folder on Android without asking
 * for all of the phone's files. Hidden folders (`.obsidian`, `.trash`) are skipped, and the walk
 * stops after [limit] entries, so a huge vault can't stall the editor.
 */
suspend fun listVault(context: Context, tree: String, limit: Int = 50_000): VaultImages = withContext(Dispatchers.IO) {
    val treeUri = Uri.parse(tree)
    val out = mutableListOf<VaultFile>()
    var seen = 0
    val folders = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(treeUri) to ""))
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    while (folders.isNotEmpty() && seen < limit) {
        val (id, prefix) = folders.removeFirst()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext() && seen++ < limit) {
                val childId = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val path = prefix + name
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) folders += childId to "$path/"
                // By extension, as embeds are: providers report some images (.avif, .heic) as octet-stream.
                else if (ObsidianNote.isImage(name)) out += VaultFile(path, DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
            }
        }
    }
    VaultImages(out, complete = folders.isEmpty() && seen <= limit)
}

/**
 * Keeps [tree] as the vault folder: read access that lasts past this run, and the old folder's
 * access given back, so the app can't still read a folder the writer has moved away from.
 */
suspend fun chooseVault(context: Context, settings: Settings, tree: Uri?) {
    val resolver = context.contentResolver
    val old = settings.obsidianVault()
    if (old != null && old != tree?.toString()) {
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    if (tree != null) runCatching { resolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    settings.setObsidianVault(tree?.toString())
}
