package com.app.jekyllposter.ui.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A post that hasn't gone out yet can be taken back to a draft and changed. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class TakeBackTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @After fun close() = app.github.close()

    private fun open(id: Long) = EditorViewModel(c, id).also { e -> idleUntil { e.text != null && e.state.value.draft != null } }

    @Test fun aPostWaitingForItsRandomTimeCanBeEditedAndPublishedAgain() {
        app.publishNow = false
        val id = runBlocking {
            app.signIn()
            c.settings.setSendAtRandomTime(true)
            c.drafts.insert(Draft(title = "Quiet", body = "Hello.", state = PostState.Queued, sendAfter = System.currentTimeMillis() + 3_600_000))
        }
        val editor = open(id)
        assertFalse(editor.state.value.editable)

        editor.takeBack()
        idleUntil { editor.state.value.editable }
        assertEquals(listOf(id), app.cancelled.toList())
        assertNull(runBlocking { c.drafts.get(id)!! }.sendAfter)

        editor.setTitle("Quieter")
        editor.publish()
        idleUntil(10_000) { editor.state.value.closed }
        val queued = runBlocking { c.drafts.get(id)!! }
        assertEquals(PostState.Queued, queued.state)
        assertEquals("Quieter", queued.title)
        // A new random time, from this Publish.
        assertTrue(queued.sendAfter != null)
        assertEquals(listOf(id), app.published.toList())
    }

    @Test fun aPostThatWentOutMeanwhileStaysPublished() {
        val id = runBlocking { c.drafts.insert(Draft(title = "Gone", state = PostState.Queued)) }
        val taken = runBlocking {
            val stored = c.drafts.get(id)!!
            // A publish under way: taking back waits for it, then finds the post sent.
            c.publisher.betweenPublishes {
                val back = async(Dispatchers.Default) { c.takeBack(id) }
                delay(200)
                c.drafts.update(stored.copy(state = PostState.Published, commitSha = "abc"))
                back
            }.await()
        }
        assertFalse(taken)
        assertEquals(PostState.Published, runBlocking { c.drafts.get(id)!! }.state)
        assertTrue(app.cancelled.isEmpty())
    }

    @Test fun aDeleteIsNotTakenBack() {
        val id = runBlocking {
            c.drafts.insert(Draft(title = "Garden plans", editingPath = "_posts/2025-04-20-garden.md", state = PostState.Queued, destination = Destination.Delete))
        }
        assertFalse(runBlocking { c.takeBack(id) })
        assertEquals(PostState.Queued, runBlocking { c.drafts.get(id)!! }.state)
    }

    @Test fun aPostThatTriedToCommitKeepsItsNameWhenPublishedAgain() {
        // An attempt that may have landed unheard: publishing again must update that file, not add one.
        app.publishNow = false
        val id = runBlocking {
            app.signIn()
            c.drafts.insert(
                Draft(
                    title = "Quiet", body = "Hello.", state = PostState.Queued,
                    targetPath = "_posts/2026-10-04-quiet.md", publishDate = "2026-10-04T09:00:00-07:00", sentShas = listOf("abc"),
                ),
            )
        }
        val editor = open(id)
        editor.takeBack()
        idleUntil { editor.state.value.editable }
        editor.setBody(androidx.compose.ui.text.input.TextFieldValue("Hello again."))
        editor.publish()
        idleUntil(10_000) { editor.state.value.closed }
        val draft = runBlocking { c.drafts.get(id)!! }
        assertEquals(PostState.Queued, draft.state)
        assertEquals("_posts/2026-10-04-quiet.md", draft.targetPath)
        assertEquals("2026-10-04T09:00:00-07:00", draft.publishDate)
    }
}
