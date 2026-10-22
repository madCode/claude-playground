package com.app.carry.device

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File

/**
 * One folder of session files, served the way a phone's storage serves the folder the owner
 * picks. It lives in the test APK, a package of its own, so it can do what the system picker
 * does: grant Carry a persistable read and write grant on the tree. Carry then holds it exactly
 * as it holds a picked folder, through `takePersistableUriPermission`.
 *
 * Driven through [Control], since Carry's process can't reach this provider before it holds the
 * grant. Nothing outside a device test reaches either.
 */
class TestFolder : DocumentsProvider() {
    private val dir get() = folderIn(context!!)

    private fun file(documentId: String): File {
        require(documentId == ROOT || documentId.startsWith("$ROOT/")) { "Not in this folder: $documentId" }
        return if (documentId == ROOT) dir else File(dir, documentId.removePrefix("$ROOT/"))
    }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf())

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: COLUMNS).apply { row(this, documentId) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: COLUMNS).apply {
            file(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { row(this, "$ROOT/${it.name}") }
        }

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        documentId.startsWith("$parentDocumentId/")

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    private fun row(cursor: MatrixCursor, documentId: String) {
        val f = file(documentId)
        val values = mapOf<String, Any>(
            Document.COLUMN_DOCUMENT_ID to documentId,
            Document.COLUMN_DISPLAY_NAME to f.name,
            Document.COLUMN_MIME_TYPE to if (f.isDirectory) Document.MIME_TYPE_DIR else "application/json",
            Document.COLUMN_FLAGS to if (f.isDirectory) 0 else Document.FLAG_SUPPORTS_WRITE,
            Document.COLUMN_SIZE to f.length(),
        )
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray<Any?>())
    }

    /** `grant` (to the package in `arg`), `put` (the file named `arg`, with the text in extras), `clear`. */
    class Control : ContentProvider() {
        override fun onCreate() = true

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            val context = context!!
            val dir = folderIn(context)
            when (method) {
                GRANT -> context.grantUriPermission(
                    arg!!, tree(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                )
                PUT -> File(dir, arg!!).writeText(extras!!.getString(TEXT)!!)
                CLEAR -> dir.listFiles()?.forEach { it.delete() }
                else -> error("No such call: $method")
            }
            return Bundle()
        }

        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    companion object {
        const val AUTHORITY = "com.app.carry.device.folder"
        private const val CONTROL = "com.app.carry.device.control"
        private const val ROOT = "gym"
        private const val GRANT = "grant"
        private const val PUT = "put"
        private const val CLEAR = "clear"
        private const val TEXT = "text"
        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
        )

        private fun folderIn(context: Context) = File(context.filesDir, ROOT).apply { mkdirs() }

        fun tree(): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT)

        private fun Context.folder(method: String, arg: String, extras: Bundle? = null) {
            checkNotNull(contentResolver.call(Uri.parse("content://$CONTROL"), method, arg, extras)) {
                "The test folder didn't answer $method"
            }
        }

        /** Empties the folder and grants it to [packageName], as picking it in the system picker would. */
        fun grantTo(context: Context, packageName: String) {
            context.folder(CLEAR, "")
            context.folder(GRANT, packageName)
        }

        fun put(context: Context, name: String, text: String) =
            context.folder(PUT, name, Bundle().apply { putString(TEXT, text) })
    }
}
