package com.app.jekyllposter.ui.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.RANDOM_WINDOW
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.home.status
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Send at a random time": Publish picks a moment in the next few hours, and nothing goes out before. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class SendLaterTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @After fun close() = app.github.close()

    private fun editor(draft: Draft): Pair<Long, EditorViewModel> {
        val id = runBlocking {
            c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
            c.settings.setSendAtRandomTime(true)
            c.drafts.insert(draft)
        }
        return id to EditorViewModel(c, id).also { e -> idleUntil { e.text != null && e.state.value.draft != null } }
    }

    @Test fun publishWaitsForARandomMomentInTheNextHours() {
        val (id, editor) = editor(Draft(title = "Quiet", body = "Hello."))
        val tapped = System.currentTimeMillis()
        val head = app.github.head
        editor.publish()
        idleUntil(10_000) { editor.state.value.closed }
        val draft = runBlocking { c.drafts.get(id)!! }
        assertEquals(PostState.Queued, draft.state)
        assertTrue(draft.sendAfter!! in tapped..tapped + RANDOM_WINDOW.inWholeMilliseconds)
        assertEquals(head, app.github.head)
        assertTrue(draft.status().first, draft.status().first.startsWith("Going out at "))

        runBlocking { c.sendNow(id) }
        assertNull(runBlocking { c.drafts.get(id)!! }.sendAfter)
        assertEquals(listOf(id), app.retried.toList())
    }

    @Test fun deletingIsNeverDelayed() {
        app.publishNow = false
        val (id, editor) = editor(Draft(title = "Garden plans", body = "x", editingPath = "_drafts/garden-plans.md", sendAfter = Long.MAX_VALUE))
        editor.deleteFromBlog()
        idleUntil(10_000) { editor.state.value.closed }
        val draft = runBlocking { c.drafts.get(id)!! }
        assertEquals(com.app.jekyllposter.data.Destination.Delete, draft.destination)
        assertNull(draft.sendAfter)
    }
}
