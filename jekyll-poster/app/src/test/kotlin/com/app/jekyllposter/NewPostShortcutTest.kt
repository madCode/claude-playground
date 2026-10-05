package com.app.jekyllposter

import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.testutil.TestApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class NewPostShortcutTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val newPost = Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_POST)

    @After fun close() = app.github.close()

    @Test fun theLauncherOffersNewPost() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use {
            val shortcut = ShortcutManagerCompat.getDynamicShortcuts(app).single { it.id == MainActivity.NEW_POST_SHORTCUT }
            assertEquals("New post", shortcut.shortLabel)
            assertEquals(Shared("", emptyList()), postToStart(shortcut.intent))
        }
    }

    @Test fun reopeningFromRecentsStartsNothing() {
        assertNull(postToStart(Intent(newPost).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "A thought")
        assertEquals(Shared("A thought", emptyList()), postToStart(share))
        assertNull(postToStart(Intent(share).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
    }

    @Test fun aNoteSharedAsAFileStartsAPostFromIt() {
        val note = android.net.Uri.parse("content://notes/Bus%20notes.md")
        val share = Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_STREAM, note)
        assertEquals(Shared("", emptyList(), note), postToStart(share))
        // An image is a photo, not a note.
        val photo = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, note)
        assertEquals(Shared("", listOf(note)), postToStart(photo))
    }

    @Test fun aNoteOpenedWithTheAppStartsAPostFromIt() {
        val note = android.net.Uri.parse("content://notes/Bus%20notes.md")
        assertEquals(Shared("", emptyList(), note), postToStart(Intent(Intent.ACTION_VIEW).setDataAndType(note, "text/markdown")))
        assertNull(postToStart(Intent(Intent.ACTION_VIEW).setDataAndType(note, "text/markdown").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
    }
}
