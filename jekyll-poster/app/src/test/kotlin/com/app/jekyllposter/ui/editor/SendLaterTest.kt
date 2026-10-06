package com.app.jekyllposter.ui.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Destination
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
            app.signIn()
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

        assertEquals(draft.sendAfter, app.sendAfters[id])

        runBlocking { c.sendNow(id) }
        assertNull(runBlocking { c.drafts.get(id)!! }.sendAfter)
        assertEquals(listOf(id), app.sentNow.toList())
    }

    @Test fun aSecondTapKeepsTheTimeAlreadyChosen() {
        app.publishNow = false
        val (id, editor) = editor(Draft(title = "Quiet", body = "Hello."))
        editor.publish()
        editor.publish()
        idleUntil(10_000) { editor.state.value.closed }
        val first = runBlocking { c.drafts.get(id)!! }.sendAfter
        // Opened again while it waits: Publish there doesn't pick again either.
        val again = EditorViewModel(c, id).also { e -> idleUntil { e.state.value.draft != null } }
        again.publish()
        idleUntil(10_000) { again.state.value.closed }
        assertEquals(listOf(id), app.published.toList())
        assertEquals(first, runBlocking { c.drafts.get(id)!! }.sendAfter)
    }

    @Test fun sendNowDoesNothingOnceThePostHasGone() {
        val id = runBlocking { c.drafts.insert(Draft(title = "Done", state = PostState.Published, sendAfter = Long.MAX_VALUE)) }
        runBlocking { c.sendNow(id) }
        assertTrue(app.sentNow.isEmpty())
        assertEquals(Long.MAX_VALUE, runBlocking { c.drafts.get(id)!! }.sendAfter)
    }

    @Test fun theVpnComingBackKeepsThePostsRandomTime() {
        val at = System.currentTimeMillis() + 3_600_000
        val id = runBlocking {
            c.settings.setOnlyThroughVpn(true)
            c.drafts.insert(Draft(title = "Quiet", body = "x", state = PostState.Queued, sendAfter = at))
        }
        app.vpn.up.value = false
        Thread.sleep(500)
        app.vpn.up.value = true
        val deadline = System.currentTimeMillis() + 5_000
        while (id !in app.retried && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(at, app.sendAfters[id])
    }

    @Test fun deletingIsNeverDelayed() {
        app.publishNow = false
        val (id, editor) = editor(Draft(title = "Garden plans", body = "x", editingPath = "_drafts/garden-plans.md", sendAfter = Long.MAX_VALUE))
        editor.deleteFromBlog()
        idleUntil(10_000) { editor.state.value.closed }
        val draft = runBlocking { c.drafts.get(id)!! }
        assertEquals(Destination.Delete, draft.destination)
        assertNull(draft.sendAfter)
    }
}
