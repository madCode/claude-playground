package com.app.jekyllposter.ui.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** An edit of a blog post that's opened and left alone isn't kept: it would list the post twice. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class EditLeftUnchangedTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @Before fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
        c.blogs.refresh()
        Unit
    }

    @After fun close() = app.github.close()

    private fun openReadingList(): EditorViewModel {
        val post = runBlocking { c.database.posts().snapshot() }.first { it.path == "_posts/2025-04-20-reading-list.md" }
        var id: Long? = null
        HomeViewModel(c).edit(post) { id = it }
        idleUntil(10_000) { id != null }
        return EditorViewModel(c, id!!).also { editor -> idleUntil { editor.text != null } }
    }

    private fun leave(editor: EditorViewModel) {
        editor.close()
        idleUntil { editor.state.value.closed }
    }

    @Test fun anEditOpenedAndLeftAloneIsDropped() {
        leave(openReadingList())
        assertTrue(runBlocking { c.drafts.list() }.isEmpty())
    }

    @Test fun anEditThatChangedIsKept() {
        val editor = openReadingList()
        editor.setTitle("What I read in April, and May")
        leave(editor)
        assertEquals("What I read in April, and May", runBlocking { c.drafts.list() }.single().title)
    }

    @Test fun anUnchangedEditLeftBehindIsntReopenedWithItsOldText() {
        // Left when the app closed under the editor: no Back, so nothing dropped it.
        val stale = openReadingList().text!!
        runBlocking { c.drafts.update(stale.copy(body = "An older copy.", baseSha = "0000")) }
        val fresh = openReadingList().text!!
        assertTrue(fresh.id != stale.id)
        assertTrue(fresh.body.contains("A few books, a few essays."))
        assertEquals(listOf(fresh.id), runBlocking { c.drafts.list() }.map { it.id })
    }
}
