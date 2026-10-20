package com.app.carry

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * [SessionFolder] against a provider that answers, which [SessionFolderTest]
 * has none of. The folder is written by a sync on another machine, so what
 * comes back is treated as untrusted: junk rows, a file far too big, a
 * stream that hands its bytes over a few at a time.
 */
@RunWith(AndroidJUnit4::class)
class SessionFolderProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val authority = "test.sessions"
    private val tree = Uri.parse("content://$authority/tree/primary%3Agym")
    private val today = "session-2026-10-20-3G.json"
    private val session = """
        {"schema":"gym-session/1","sheet_id":"2026-10-20:3G","date":"2026-10-20","class":"3G",
         "sets":[{"ref":0,"line":"10 x goblet squat","canonical":"goblet squat","weight":null}]}
    """.trimIndent()

    /** A provider whose listing is [rows], as (document id, display name). */
    private class Listing(private val rows: List<Array<String?>>) : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor =
            MatrixCursor(p ?: arrayOf("id", "name")).apply { rows.forEach { addRow(it) } }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    private fun folderListing(vararg rows: Array<String?>): SessionFolder {
        ShadowContentResolver.registerProviderInternal(authority, Listing(rows.toList()))
        return SessionFolder(context).apply { assertTrue(remember(tree)) }
    }

    private fun doc(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun serve(id: String, stream: InputStream) =
        shadowOf(context.contentResolver).registerInputStream(doc(id), stream)

    /** Hands over at most three bytes per read, as a pipe from another
     *  app's provider may. */
    private class Trickle(bytes: ByteArray) : InputStream() {
        private val inner = bytes.inputStream()
        override fun read() = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, minOf(len, 3))
    }

    @Test
    fun `rows a provider returns without an id or a name are left out of the listing`() {
        val folder = folderListing(
            arrayOf("doc-1", today),
            arrayOf(null, "session-2026-10-19-3G.json"),
            arrayOf("doc-3", null),
        )
        assertEquals(listOf("doc-1" to today), folder.list())
    }

    @Test
    fun `a session handed over a few bytes at a time reads whole, and is on screen`() {
        val folder = folderListing(arrayOf("doc-1", today))
        serve("doc-1", Trickle(session.encodeToByteArray()))
        val state = carryState(folder) as CarryState.Ready
        assertEquals("2026-10-20:3G", state.session.sheetId)
        assertEquals(session, state.raw)
    }

    @Test
    fun `a file over a megabyte is refused unread, and said to be unreadable`() {
        // Held three times over once read, where it becomes an out-of-memory
        // crash that takes the typed numbers with it.
        val folder = folderListing(arrayOf("doc-1", today))
        val huge = ByteArray(1_000_001) { ' '.code.toByte() }
        session.encodeToByteArray().copyInto(huge)
        serve("doc-1", huge.inputStream())
        assertNull(folder.read("doc-1"))
        assertEquals(CarryState.Unreadable(today, CarryState.Unreadable.Why.DAMAGED), carryState(folder))
    }

    @Test
    fun `a write that lands says so, in UTF-8`() {
        val folder = folderListing(arrayOf("doc-1", today))
        val landed = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(doc("doc-1"), landed)
        val text = """{"note":"Übung · 杠铃"}"""
        assertTrue(folder.write("doc-1", text))
        assertEquals(text, landed.toByteArray().decodeToString())
    }

    @Test
    fun `picking a second folder hands the first grant back`() {
        // Grants are capped per app; re-picking would otherwise pile them up.
        val other = Uri.parse("content://$authority/tree/primary%3Aelsewhere")
        val folder = SessionFolder(context)
        assertTrue(folder.remember(tree))
        assertTrue(folder.remember(other))
        assertEquals(listOf(other), context.contentResolver.persistedUriPermissions.map { it.uri })
    }

    @Test
    fun `a folder held for reading only is not a folder Carry can use`() {
        // Some cloud providers hand out read-only trees. Carry writes the
        // log back, so a read-only grant has to look lost now, not after
        // the workout when the save fails.
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.getSharedPreferences("carry", Context.MODE_PRIVATE).edit()
            .putString("session_folder", tree.toString()).apply()
        val folder = SessionFolder(context)
        assertFalse(folder.chosen)
        assertTrue(folder.grantLost)
        assertEquals(CarryState.NeedsFolder(lostGrant = true, holding = "gym"), carryState(folder))
    }
}
