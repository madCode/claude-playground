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
 * This app exists because its predecessor held a handle wider than its
 * job. A SAF tree grant is recursive and survives reboots, so its scope
 * is checked rather than trusted, named rather than hidden, and droppable
 * rather than permanent.
 */
@RunWith(AndroidJUnit4::class)
class GrantScopeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val folder = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ASync%2Fgym")
    private val wholePhone = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")

    @Test
    fun `a whole volume is refused`() {
        // "Internal storage" sits a row above the folder in the picker, and
        // one mis-tap would hand over read and write on every file on the
        // phone until the app is uninstalled.
        val store = SessionFolder(context)
        assertTrue(store.isWholeVolume(wholePhone))
        assertFalse(store.isWholeVolume(folder))
        assertFalse("a refused pick must not be stored", store.remember(wholePhone))
        assertNull(store.uri)
    }

    @Test
    fun `the folder being held can be named`() {
        // A grant nobody can see is a grant nobody can audit.
        val store = SessionFolder(context)
        store.remember(folder)
        assertEquals("Sync/gym", store.label)
    }

    @Test
    fun `the grant can be handed back`() {
        val store = SessionFolder(context)
        store.remember(folder)
        store.forget()
        assertNull(SessionFolder(context).uri)
        assertNull(SessionFolder(context).label)
    }
}
