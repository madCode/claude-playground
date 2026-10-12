package com.app.carry

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a JVM can honestly say about the storage layer, and no more.
 *
 * Robolectric shadows `takePersistableUriPermission`, so none of this proves
 * a grant really persists on a device -- an earlier version of this file
 * claimed exactly that and would have passed with the call deleted. The
 * device-only checks are listed in docs/BACKLOG.md.
 */
@RunWith(AndroidJUnit4::class)
class SessionFolderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tree: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Agym")

    @Test
    fun `the chosen folder is remembered across instances`() {
        SessionFolder(context).remember(tree)
        assertEquals(tree, SessionFolder(context).uri)
    }

    @Test
    fun `chosen asks the system, not the preference`() {
        // The bug this replaces: the Uri was stored even when the take
        // failed, so the app believed it had a folder it could not read and
        // sent the owner round the picker on every cold start, forever.
        val folder = SessionFolder(context)
        folder.remember(tree)
        val systemSaysYes = context.contentResolver.persistedUriPermissions.any { it.uri == tree }
        assertEquals(systemSaysYes, folder.chosen)
        assertEquals(!systemSaysYes, folder.grantLost)
    }

    @Test
    fun `forgetting clears it`() {
        val folder = SessionFolder(context)
        folder.remember(tree)
        folder.forget()
        assertNull(SessionFolder(context).uri)
        assertFalse(SessionFolder(context).chosen)
        assertFalse(SessionFolder(context).grantLost)
    }

    @Test
    fun `listing and reading without a folder are empty rather than a crash`() {
        SessionFolder(context).forget()
        assertEquals(emptyList<Pair<String, String>>(), SessionFolder(context).list())
        assertNull(SessionFolder(context).read("anything"))
    }

    @Test
    fun `a document Uri where a tree Uri belongs does not crash onResume`() {
        // getTreeDocumentId throws IllegalArgumentException on anything whose
        // first segment is not "tree". It used to run before the guard, so a
        // malformed stored value crashed the app on resume rather than
        // falling back to the picker.
        val folder = SessionFolder(context)
        context.getSharedPreferences("carry", Context.MODE_PRIVATE).edit()
            .putString("session_folder", "content://authority/document/42").apply()
        assertEquals(emptyList<Pair<String, String>>(), folder.list())
        assertNull(folder.read("42"))
    }

    @Test
    fun `a write that cannot happen says so rather than pretending`() {
        // A save the owner believes happened and did not is how a session's
        // numbers disappear, so this returns false rather than Unit.
        SessionFolder(context).forget()
        assertEquals(false, SessionFolder(context).write("anything", "{}"))

        val folder = SessionFolder(context)
        folder.remember(Uri.parse("content://nothing.here/tree/gone"))
        assertEquals(false, folder.write("gone", "{}"))
    }

    @Test
    fun `an unknown provider lists nothing instead of throwing`() {
        val folder = SessionFolder(context)
        folder.remember(Uri.parse("content://nothing.here/tree/gone"))
        assertEquals(emptyList<Pair<String, String>>(), folder.list())
        assertNull(folder.read("gone"))
    }
}
